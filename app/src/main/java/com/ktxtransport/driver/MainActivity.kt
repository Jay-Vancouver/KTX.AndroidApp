package com.ktxtransport.driver

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.MotionEvent
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
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: View

    private var mainFrameError = false

    // Site address the page was loaded from; a change in SettingsActivity reloads the new one.
    private var loadedStartUrl = ""
    private var clearHistoryAfterLoad = false

    // Leftâ†’right swipe across the top strip opens SettingsActivity.
    private var swipeTracking = false
    private var swipeTriggered = false
    private var swipeStartX = 0f
    private var swipeStartY = 0f

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
        SystemBars.apply(this, lightBackground = false) // KTX blue behind the bars, white icons

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

        // The settings swipe starts at the left edge, where gesture navigation's "back" would take it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            webView.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                v.systemGestureExclusionRects = listOf(Rect(0, 0, dp(SWIPE_EDGE_DP), dp(SWIPE_ZONE_DP)))
            }
        }

        loadedStartUrl = WebHosts.startUrl
        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(appLinkUrl(intent) ?: loadedStartUrl)
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
        if (loadedStartUrl != WebHosts.startUrl) {
            // The administrator changed the server address in SettingsActivity.
            loadedStartUrl = WebHosts.startUrl
            clearHistoryAfterLoad = true
            webView.loadUrl(loadedStartUrl)
        }
        FixUploader.flush(this) // positions left over from a stopped or killed service
        if (!LocationService.isRunning) LocationService.resumeIfTracking(this)
        dispatchStatus() // the driver may be back from a settings screen
        updates.checkIfDue()
    }

    /**
     * A leftâ†’right drag that starts in the top strip opens SettingsActivity. Taps and other
     * gestures there still reach the page; once the swipe is recognised the page gets a cancel.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val location = IntArray(2).also { webView.getLocationOnScreen(it) }
                swipeTracking = ev.rawY - location[1] in 0f..dp(SWIPE_ZONE_DP).toFloat()
                swipeTriggered = false
                swipeStartX = ev.rawX
                swipeStartY = ev.rawY
            }
            MotionEvent.ACTION_MOVE -> if (swipeTracking && !swipeTriggered) {
                val dx = ev.rawX - swipeStartX
                val dy = abs(ev.rawY - swipeStartY)
                if (dx > webView.width / 4f && dx > dy * 2) {
                    swipeTriggered = true
                    val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    startActivity(Intent(this, SettingsActivity::class.java))
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val consumed = swipeTriggered
                swipeTracking = false
                swipeTriggered = false
                if (consumed) return true
            }
        }
        if (swipeTriggered) return true
        return super.dispatchTouchEvent(ev)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private val updates = UpdateUi(this)

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
                // http only for a debug build's local test site (WebHosts.isAppUrl allows it there)
                "http" -> if (WebHosts.isAppUrl(uri)) false else { openExternal(uri); true }
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
            if (clearHistoryAfterLoad) {
                // Back must not return to pages of the previous server.
                clearHistoryAfterLoad = false
                view.clearHistory()
            }
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
        private const val SWIPE_ZONE_DP = 64 // height of the top strip that starts the settings swipe
        private const val SWIPE_EDGE_DP = 48 // left-edge part of it taken back from the system back gesture
    }
}
