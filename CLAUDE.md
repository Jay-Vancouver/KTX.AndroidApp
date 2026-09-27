# KTX Driver (Android hybrid app)

Full spec: [ktxhybridapp.txt](ktxhybridapp.txt). This file is a summary of sections 1–5.

## Session rules
- Answers, questions and explanations in Korean. Code, identifiers and commit messages in English.
- Commit only when the user says "커밋해줘"; after committing, always push to origin.
- This repo is separate from the TMS repo.
- Before reporting a task as done, run `./gradlew assembleDebug` and report the result.
- After each step in "Work order", confirm the build passes and ask whether to commit.

## Why (background)
- Drivers use the TMS web page https://driver.withktx.com (pickup scan, delivery signature, logs, inspections).
- Truck location currently comes from a separate app (Traccar Client) that drivers must configure; the web page cannot start/stop it, and browsers cannot send location with the screen off.
- Solution: a WebView wrapper around the driver page plus a native location service the page controls ("start tracking at pickup, stop at delivery"). No Traccar source is included.
- Distribution: APK on the company website (not Google Play). iPhone drivers keep web + Traccar Client.

## App structure
Kotlin, minSdk 26, targetSdk 34, package `com.ktxtransport.driver`, app name "KTX Driver".
Built with command-line tools only (JDK 17, Android SDK cmdline-tools, Gradle wrapper, adb) — see [docs/SETUP.md](docs/SETUP.md).

   **Host note (verified on device 2026-09-27):** driver.withktx.com redirects to `https://www.withktx.com/driver/`, and the server builds SMS login links from the request host (`/driver/s/<token>`). So App Links cover both `driver.withktx.com` and `www.withktx.com/driver/`, and the bridge restriction (item 3) must allow `www.withktx.com` pages under `/driver/` as well as `driver.withktx.com`.
1. **WebView screen** — loads https://driver.withktx.com; persistent cookies (6-month login), camera permission delegation, file chooser, back = WebView history, external links open in browser, User-Agent suffix `KTXDriverApp/<version>`, App Links (autoVerify) for driver.withktx.com.
2. **Location foreground service** (`foregroundServiceType="location"`) — FusedLocationProviderClient, fallback LocationManager; 60 s high-accuracy while tracking, 5-min heartbeat when stationary.
   - Sends OsmAnd format: `POST <url>` form fields `id` (10-digit phone), `lat`, `lon`, `timestamp` (UTC epoch s), `speed` (knots), `bearing`, `altitude`, `accuracy`, `batt` (%). Empty 200 response.
   - Failed sends go to a local queue, resent in order (duplicates are harmless).
   - Persistent notification "KTX: 위치 전송 중" that opens the app; restart on BOOT_COMPLETED if tracking was on.
   - State (on/off, phone, server URL, last sent) in SharedPreferences. The URL comes from `startTracking()` — never hard-code it.
3. **JS bridge** `KtxAndroidApp` (only exposed to pages on driver.withktx.com):
   - `startTracking(phone, url)`, `stopTracking()`
   - `status()` → JSON `{tracking, lastSentAt, permission:"always|whileInUse|denied", battery:"unrestricted|restricted"}`
   - `requestPermissions()` — location "always", then battery-optimization exemption
   - `version()`
   - Bridge names are shared with the TMS server; change both sides together.
4. **Update check** — on start read https://driver.withktx.com/app/version.json (`{"version","apk","notes"}`); if newer, prompt and open the download link (not forced).
5. **First-run guide** — location "always", battery-optimization exemption, "install unknown apps"; do not ask again once granted.

## Scenario
- Pickup-complete page calls `startTracking(phone, "https://www.withktx.com/gps")` → immediate first fix → every 60 s.
- Delivery-complete page calls `stopTracking()` when no loads remain.
- Server-side work (UA detection, calling the bridge, version.json, APK hosting, guide) is done in the TMS session, not here.

## Work order
1. Tools: JDK 17, cmdline-tools (platform-tools, platforms;android-34, build-tools;34.0.0), Gradle wrapper → docs/SETUP.md
2. Project skeleton, icon from TMS `app/static/img/PartnerLogo.png`
3. WebView + camera/file permissions + User-Agent + App Links
4. Location foreground service + OsmAnd send + offline queue + heartbeat + boot restart
5. `KtxAndroidApp` bridge + domain restriction
6. First-run permission guide
7. Update check
8. Release signing (keystore outside repo; path/passwords in local gradle.properties or env vars) + backup notes
9. Device test checklist docs/TEST.md

## References
- TMS repo: WSL `/home/tms_user/ktx/tms` (`\\wsl.localhost\Ubuntu-24.04\home\tms_user\ktx\tms`): `app/gps/service.py`, `app/gps/ingest.py`, `app/routers/driver_pages.py`, `app/templates/driver/`, `docs/TRACKING_PLAN.md`, `docs/manual/`.
- Identifier = driver phone, 10 digits (leading 1 removed).
- Test server: test.ktxtransport.com; production: tms.ktxtransport.com. POD upload: https://pod.withktx.com.
