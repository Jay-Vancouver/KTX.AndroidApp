package com.ktxtransport.driver

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.MimeTypeMap
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: View

    private var mainFrameError = false

    /** Whether the page in the main frame may use the KtxAndroidApp bridge (read on the JavaBridge thread). */
    @Volatile
    var bridgeAllowed = false
        private set

    private val permissionFlow = PermissionFlow(this) {
        LocationService.resumeIfTracking(this)
        dispatchStatus()
    }

    // Pending WebView callbacks waiting on a runtime permission or picker result.
    private var pendingPermissionRequest: PermissionRequest? = null
    private var pendingGeolocation: Pair<String, GeolocationPermissions.Callback>? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingFileChooserParams: WebChromeClient.FileChooserParams? = null
    private var cameraPhotoFile: File? = null

    private val cameraPermissionForPage =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val request = pendingPermissionRequest ?: return@registerForActivityResult
            pendingPermissionRequest = null
            if (granted) request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) else request.deny()
        }

    private val cameraPermissionForChooser =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val params = pendingFileChooserParams ?: return@registerForActivityResult
            pendingFileChooserParams = null
            launchFileChooser(params, cameraAllowed = granted)
        }

    private val locationPermissionForPage =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val (origin, callback) = pendingGeolocation ?: return@registerForActivityResult
            pendingGeolocation = null
            callback.invoke(origin, result.values.any { it }, false)
        }

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = filePathCallback ?: return@registerForActivityResult
            filePathCallback = null
            callback.onReceiveValue(parseFileChooserResult(result.resultCode, result.data))
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        errorView = findViewById(R.id.errorView)
        findViewById<Button>(R.id.retryButton).setOnClickListener { retry() }

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        clearOldCameraFiles()

        CookieManager.getInstance().setAcceptCookie(true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setGeolocationEnabled(true)
            userAgentString = "$userAgentString KTXDriverApp/${BuildConfig.VERSION_NAME}"
        }
        webView.webViewClient = AppWebViewClient()
        webView.webChromeClient = AppWebChromeClient()
        webView.setDownloadListener { url, _, _, _, _ -> Browser.open(this, Uri.parse(url)) }
        webView.addJavascriptInterface(KtxBridge(this), KtxBridge.NAME)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // At the first page, keep the app alive in the background so the page state survives.
                if (webView.canGoBack()) webView.goBack() else moveTaskToBack(true)
            }
        })

        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(appLinkUrl(intent) ?: WebHosts.startUrl)
        }
        // First-run guide on top of the page (which keeps loading behind it) until it is completed.
        if (savedInstanceState == null && !SetupState(this).completed) {
            startActivity(Intent(this, SetupActivity::class.java))
        }
        handleDebugIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        appLinkUrl(intent)?.let { webView.loadUrl(it) }
        handleDebugIntent(intent)
    }

    /**
     * Debug builds only, for testing without the server:
     * adb shell am start -n com.ktxtransport.driver/.MainActivity --es debug_tracking start
     *     --es phone 6045551234 --es url http://127.0.0.1:8099/gps
     * adb shell am start -n com.ktxtransport.driver/.MainActivity
     *     --es debug_version_url http://127.0.0.1:8099/app/version.json
     */
    private fun handleDebugIntent(intent: Intent?) {
        if (!BuildConfig.DEBUG || intent == null) return
        when (intent.getStringExtra("debug_tracking")) {
            "start" -> LocationService.start(
                this, intent.getStringExtra("phone"), intent.getStringExtra("url"),
                TrackingState.parseCadence(intent.getStringExtra("options")) ?: TrackingState.Cadence.DEFAULT,
            )
            "stop" -> LocationService.stop(this)
        }
        intent.getStringExtra("debug_version_url")?.let { UpdateChecker.urlOverride = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        FixUploader.flush(this) // positions left over from a stopped or killed service
        if (!LocationService.isRunning) LocationService.resumeIfTracking(this)
        dispatchStatus() // the driver may be back from a settings screen
        UpdateChecker.checkIfDue(::showUpdate)
    }

    // --- Update (UpdateChecker finds it, UpdateInstaller downloads and installs it) ---

    private var updateDialog: AlertDialog? = null
    private var pendingUpdate: UpdateChecker.Update? = null

    /** Not forced: "Later" closes it until the next check. */
    private fun showUpdate(update: UpdateChecker.Update) {
        if (isFinishing || isDestroyed || updateDialog?.isShowing == true) return
        val message = update.notes.ifEmpty { getString(R.string.update_message) }
        updateDialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_title, update.version))
            .setMessage(message)
            .setPositiveButton(R.string.update_now) { _, _ -> startUpdate(update) }
            .setNegativeButton(R.string.update_later, null)
            .show()
    }

    /** "Install unknown apps" for this app itself, asked the first time an update is installed. */
    private val installPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val update = pendingUpdate ?: return@registerForActivityResult
            pendingUpdate = null
            if (packageManager.canRequestPackageInstalls()) downloadUpdate(update) else showNotice(R.string.update_need_permission)
        }

    private fun startUpdate(update: UpdateChecker.Update) {
        if (packageManager.canRequestPackageInstalls()) {
            downloadUpdate(update)
            return
        }
        pendingUpdate = update
        try {
            installPermissionLauncher.launch(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
            )
        } catch (_: ActivityNotFoundException) {
            pendingUpdate = null
            showNotice(R.string.update_need_permission)
        }
    }

    private fun downloadUpdate(update: UpdateChecker.Update) {
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = true
        }
        val padding = (24 * resources.displayMetrics.density).toInt()
        val content = FrameLayout(this).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(bar)
        }
        var cancelled = false
        var cancel: (() -> Unit)? = null
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_downloading, update.version))
            .setView(content)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                cancelled = true
                cancel?.invoke()
            }
            .show()

        cancel = UpdateInstaller.download(this, update.apkUrl,
            onProgress = { percent ->
                if (percent >= 0) {
                    bar.isIndeterminate = false
                    bar.progress = percent
                }
            },
            onDone = { file ->
                if (isDestroyed) return@download
                dialog.dismiss()
                when {
                    cancelled -> file?.delete()
                    file == null -> showNotice(R.string.update_failed)
                    !UpdateInstaller.isValidUpdate(this, file) -> {
                        file.delete()
                        showNotice(R.string.update_invalid)
                    }
                    !UpdateInstaller.install(this, file) -> showNotice(R.string.update_install_failed)
                    // else: Android's install confirmation opens via InstallResultReceiver
                }
            },
        )
    }

    private fun showNotice(messageRes: Int) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setMessage(messageRes)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onPause() {
        webView.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    /** URL from an App Link (tapped SMS login link), if it points at our site. */
    private fun appLinkUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data
        return if (WebHosts.isAppUrl(uri)) uri.toString() else null
    }

    /** Called by KtxAndroidApp.requestPermissions() and startTracking() without location. */
    fun startPermissionFlow() = permissionFlow.start()

    /** Tells the page that permissions or tracking may have changed: window event "ktxappstatus". */
    private fun dispatchStatus() {
        if (!bridgeAllowed) return
        val json = KtxBridge.statusJson(this)
        webView.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('${KtxBridge.STATUS_EVENT}', {detail: $json}));", null
        )
    }

    private fun retry() {
        errorView.visibility = View.GONE
        if (webView.url.isNullOrEmpty()) webView.loadUrl(WebHosts.startUrl) else webView.reload()
    }

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_: ActivityNotFoundException) {
        }
    }

    /** Opens intent: URLs from pages, falling back to browser_fallback_url. */
    private fun openIntentUrl(url: String) {
        val intent = try {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } catch (_: Exception) {
            return
        }
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.component = null
        intent.selector = null
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            intent.getStringExtra("browser_fallback_url")?.let { openExternal(Uri.parse(it)) }
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    // --- File chooser (photo upload) ---

    private fun acceptsImages(params: WebChromeClient.FileChooserParams): Boolean {
        val types = params.acceptTypes.filter { it.isNotBlank() }
        return types.isEmpty() || types.any { it.startsWith("image/") || it == "*/*" }
    }

    private fun mimeTypes(params: WebChromeClient.FileChooserParams): Array<String> =
        params.acceptTypes
            .flatMap { it.split(',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { type ->
                if (type.startsWith(".")) {
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(type.substring(1))
                } else {
                    type
                }
            }
            .distinct()
            .toTypedArray()

    private fun launchFileChooser(params: WebChromeClient.FileChooserParams, cameraAllowed: Boolean) {
        val cameraIntent = if (cameraAllowed && acceptsImages(params)) createCameraIntent() else null

        val intent = if (params.isCaptureEnabled && cameraIntent != null) {
            cameraIntent
        } else {
            val types = mimeTypes(params)
            val contentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (types.size == 1) types[0] else "*/*"
                if (types.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, types)
                if (params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
            }
            Intent.createChooser(contentIntent, null).apply {
                if (cameraIntent != null) putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
            }
        }

        try {
            fileChooserLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
        }
    }

    private fun createCameraIntent(): Intent? {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) == null) return null
        val dir = File(cacheDir, CAMERA_DIR).apply { mkdirs() }
        val file = File.createTempFile("photo_", ".jpg", dir)
        cameraPhotoFile = file
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        return intent
            .putExtra(MediaStore.EXTRA_OUTPUT, uri)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun parseFileChooserResult(resultCode: Int, data: Intent?): Array<Uri>? {
        val photo = cameraPhotoFile
        cameraPhotoFile = null
        if (resultCode != Activity.RESULT_OK) return null

        data?.clipData?.let { clip ->
            return Array(clip.itemCount) { clip.getItemAt(it).uri }
        }
        data?.data?.let { return arrayOf(it) }
        if (photo != null && photo.length() > 0) {
            return arrayOf(FileProvider.getUriForFile(this, "$packageName.fileprovider", photo))
        }
        return null
    }

    private fun clearOldCameraFiles() {
        File(cacheDir, CAMERA_DIR).listFiles()?.forEach { it.delete() }
    }

    // --- WebView clients ---

    private inner class AppWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            return when (uri.scheme) {
                "https" -> if (WebHosts.isAppHost(uri.host)) false else { openExternal(uri); true }
                "http" -> { openExternal(uri); true }
                "intent" -> { openIntentUrl(uri.toString()); true }
                else -> { openExternal(uri); true } // tel:, sms:, mailto:, geo:, market:
            }
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            bridgeAllowed = WebHosts.isBridgeUrl(url)
            mainFrameError = false
            errorView.visibility = View.GONE
        }

        // Also covers history.pushState and back/forward within the page.
        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            bridgeAllowed = WebHosts.isBridgeUrl(url)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            if (mainFrameError) errorView.visibility = View.VISIBLE
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) {
                mainFrameError = true
                errorView.visibility = View.VISIBLE
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            // The renderer crashed or was killed for memory; restart the screen instead of crashing the app.
            recreate()
            return true
        }
    }

    private inner class AppWebChromeClient : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progressBar.progress = newProgress
            progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
        }

        /** Camera for pickup scan / inspection photos (getUserMedia). Audio is never granted. */
        override fun onPermissionRequest(request: PermissionRequest) {
            val wantsVideo = PermissionRequest.RESOURCE_VIDEO_CAPTURE in request.resources
            if (!wantsVideo || !WebHosts.isAppHost(request.origin.host)) {
                request.deny()
                return
            }
            if (hasPermission(Manifest.permission.CAMERA)) {
                request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
            } else {
                pendingPermissionRequest?.deny()
                pendingPermissionRequest = request
                cameraPermissionForPage.launch(Manifest.permission.CAMERA)
            }
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest) {
            if (pendingPermissionRequest == request) pendingPermissionRequest = null
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            if (!WebHosts.isAppHost(Uri.parse(origin).host)) {
                callback.invoke(origin, false, false)
                return
            }
            if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            ) {
                callback.invoke(origin, true, false)
            } else {
                pendingGeolocation = origin to callback
                locationPermissionForPage.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }
        }

        override fun onShowFileChooser(
            webView: WebView,
            callback: ValueCallback<Array<Uri>>,
            params: FileChooserParams,
        ): Boolean {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = callback

            if (acceptsImages(params) && !hasPermission(Manifest.permission.CAMERA)) {
                pendingFileChooserParams = params
                cameraPermissionForChooser.launch(Manifest.permission.CAMERA)
            } else {
                launchFileChooser(params, cameraAllowed = hasPermission(Manifest.permission.CAMERA))
            }
            return true
        }
    }

    companion object {
        private const val CAMERA_DIR = "camera"
    }
}
