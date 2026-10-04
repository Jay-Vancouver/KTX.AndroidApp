# 작업 현황 (WIP)

마지막 갱신: 2026-09-28. 다음 세션은 이 파일과 [CLAUDE.md](../CLAUDE.md)를 먼저 읽는다.
원래 지시서는 [ktxhybridapp.txt](../ktxhybridapp.txt)(0절 세션 규칙, 5절 작업 순서).

## 1. 한 줄 요약

지시서 5절의 1)~9) 단계가 **모두 끝나 GitHub `main`에 push되어 있다**. 그 뒤 사용자 요청으로 업데이트를
브라우저 대신 **앱 안에서 설치**하도록 바꿨고(`258d0cf`), 실기기에서 확인했다.
앱 쪽 남은 일은 없다. 이제 **TMS 서버 작업**(4절)이 끝나야 [TEST.md](TEST.md) 전체를 실기기로 시험할 수 있다.

## 2. 세션 규칙 (지시서 0절)

- 답변·질문·설명은 한글, 코드·식별자·커밋 메시지는 영문.
- 커밋은 사용자가 "커밋해줘"라고 할 때만, 커밋하면 항상 `origin`에 push.
- 작업 완료 보고 전에 `./gradlew assembleDebug` 통과를 확인하고 결과를 말한다.
- 단계가 끝날 때마다 빌드를 확인하고 커밋할지 묻는다.
- 커밋 메시지 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## 3. 저장소와 환경

| 항목 | 값 |
|---|---|
| 저장소 | `C:\ktxandroidapp` (지시서의 `C:\ktx-apk`가 아니라 이 폴더) |
| origin | https://github.com/Jay-Vancouver/KTX.AndroidApp.git, 브랜치 `main` |
| 커밋 작성자 | `jay` / `system@ktxtransport.com` (저장소 로컬 설정) |
| push 인증 | Git Credential Manager에 저장됨 — 셸에서 `git push` 가능 |
| JDK | Temurin 17, `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot` (사용자 환경변수 `JAVA_HOME`) |
| Android SDK | `C:\Users\Admin\AppData\Local\Android\Sdk` (`ANDROID_HOME`), platform-tools, platforms;android-36 (34도 있음), build-tools;36.0.0·35.0.0 |
| 빌드 | AGP 8.10.1, Gradle 8.11.1, compileSdk/targetSdk 36 (2026-09-30 상향), flavor `direct`/`play` |
| Gradle | wrapper 8.7, AGP 8.5.2, Kotlin 1.9.24 |
| git | `C:\Program Files\Git\cmd\git.exe` |
| TMS 저장소 | `\\wsl.localhost\Ubuntu-24.04\home\tms_user\ktx\tms` (배포판 이름이 `Ubuntu`가 아니라 `Ubuntu-24.04`) |

**PowerShell 셸에서 빌드할 때**: 도구가 여는 셸에는 새 환경변수가 반영되지 않을 수 있으므로 매번 앞에 붙인다.

```powershell
Set-Location C:\ktxandroidapp
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','User')
$env:ANDROID_HOME = [Environment]::GetEnvironmentVariable('ANDROID_HOME','User')
.\gradlew.bat assembleDebug --console=plain -q
```

설치 절차 전체: [SETUP.md](SETUP.md).

## 4. 단계별 결과

| 단계 | 커밋 | 실기기 확인 |
|---|---|---|
| 1) 도구 설치, SETUP.md | `25a803b` | — |
| 2) 프로젝트 골격, 아이콘(PartnerLogo.png → 적응형 아이콘) | `3692baa` | 아이콘 표시 |
| 3) WebView, 카메라/파일, User-Agent, App Links | `87aaef0`, `5588b2f` | UA, 카메라, 로그인 유지, PICK UP 카메라 |
| 4) 위치 Foreground Service, OsmAnd 전송, 오프라인 큐, 하트비트, 부팅 재시작 | `c32e1e7` | 60초 주기(화면 꺼짐), 오프라인 큐 순서, 업데이트 후 재시작, 중지 |
| 5) `KtxAndroidApp` 다리, 도메인 제한 | `c35ba89` | 모든 함수, 도메인 제한, 권한 흐름, 권한 없이 startTracking |
| 6) 첫 실행 권한 안내 | `96ad756` | 전체 흐름, "나중에", 완료 후 재표시 없음 |
| 7) 업데이트 확인 | `acb78c6` | 새 버전 안내, Chrome 다운로드, 같은 버전이면 무표시, 12시간 제한 |
| 8) release 서명, SIGNING.md, assetlinks.json | `e26b25b` | release 빌드·서명 검증(폰 설치는 안 함) |
| 9) TEST.md | `c9adeb7` | — |
| 추가) 전송 스레드 감시(watchdog) + 전송 오류 표시 | (이 커밋) | 2026-10-03 알버타 시험: 앱이 40시간 실행 중 17:18부터 2시간 전송이 멈춤(폰 위치·네트워크·서버는 정상, 앱 재시작으로 즉시 회복) → 전송 1건이 60초 넘으면 연결을 끊고 새 스레드로 교체. 실패 원인 로그, 설정 화면에 대기 건수·마지막 오류, `status()`에 `queued`/`lastError`. 데이터 끔 시험: 실패 기록 → 켜자 1초 안에 전송 |
| 추가) 알림 문구가 폰 언어 변경을 따라감 | `a1b0f97` | 언어를 바꿔도 알림이 시작 때 언어로 남던 문제(onConfigurationChanged에서 다시 게시). 실기기 미확인 |
| 추가) Play용/홈페이지용 빌드 분리(flavor), targetSdk 36, 위치 고지 창, Play용 배터리 설정 안내 | (이 커밋) | 2026-09-30 S25+(Android 16)에서 release 서명 빌드로 확인: 상태 표시줄(흰 아이콘이 안 보이던 문제 → 파란 배경으로 수정), 웹·네이티브 입력칸 키보드, 뒤로 가기, 고지 창, Play용 배터리 안내→앱 정보→제한 없음, Play용에 설치·배터리 권한 없음. PIN 입력칸 가림 버그 수정 |
| 추가) 설정 화면(상단 좌→우 스와이프) + 관리자 PIN으로 서버 주소 변경 | (이 커밋) | 2026-09-30 확인: 스와이프로 열림, 상태 값, 틀린 PIN 거부, 맞는 PIN → 주소 입력 창. 실제 주소 변경·저장은 미시험(TEST.md 8.5~8.8) |
| 추가) 브라우저에서 앱 설치 여부 판별(`asset_statements`, site = `https://www.withktx.com`) | (이 커밋) | 폰 Chrome에서 manifest 링크를 임시로 넣자 `getInstalledRelatedApps()` → `[{"id":"com.ktxtransport.driver","platform":"play","version":"1.0.0"}]`. 운영 페이지에는 아직 manifest 링크가 없음(TMS 배포 필요) |
| 추가) 전송 간격·하트비트를 startTracking options로 | (이 커밋) | 옵션 해석·clamp·잘못된 JSON 거부·status 값·추적 중 재등록 확인, `interval: 15`로 실제 15초 간격 전송 확인 |
| 추가) 업데이트를 앱 안에서 설치 | `258d0cf` | 2026-09-28 확인: 1.0.0 → 9.9.9(debug) 앱 내 업데이트 성공, 설치 주체가 `com.ktxtransport.driver`로 바뀜, 설정·권한 유지. 첫 시도는 Play 프로텍트 검증에서 거부(아래 7절) |

**아직 한 번도 확인하지 못한 것** (TEST.md에 항목 있음): release APK끼리의 앱 내 업데이트(7절, debug로만 확인), 5분 하트비트(4.3), 실제 재부팅(6.1), 점검 사진 업로드(3.5),
release APK 설치·업데이트(1절, 7절), App Links 도메인 인증(2.2), 서버 지도에 점 찍힘(3.4 — 시험은 PC 수신기로만 함).

## 5. 코드 지도 (`app/src/main/java/com/ktxtransport/driver/`)

| 파일 | 역할 |
|---|---|
| `MainActivity.kt` | WebView(쿠키, UA, 카메라 위임, 파일 선택, 뒤로 가기, 오류 화면), 다리 등록, `bridgeAllowed`, 첫 실행 안내 띄우기, 업데이트 안내 창, debug adb 훅 |
| `WebHosts.kt` | 앱 안에서 여는 호스트(`*.withktx.com`), 다리 허용 URL(`isBridgeUrl`) |
| `KtxBridge.kt` | `window.KtxAndroidApp` 함수들, `statusJson`, 이벤트 이름 |
| `LocationService.kt` | 위치 Foreground Service, 60초 요청, 5분 하트비트, WakeLock, 알림, `start/stop/resumeIfTracking` |
| `FixQueue.kt` | SQLite 큐(`fixes.db`, 최대 1만 건) |
| `FixUploader.kt` | 큐를 순서대로 POST, 2xx 삭제 / 408·429·5xx·네트워크 오류는 재시도 / 그 밖의 4xx는 버림 |
| `TrackingState.kt` | SharedPreferences `tracking`: tracking, phone, url, lastSentAt; 전화번호 정규화, URL 검증 |
| `BootReceiver.kt` | `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED` → `resumeIfTracking` |
| `AppPermissions.kt` | 권한 상태(`always/whileInUse/denied`, 배터리) |
| `PermissionFlow.kt` | 위치 → 항상 → 알림 → 배터리 예외 순서 요청(단계 부분 선택 가능) |
| `SetupActivity.kt`, `SetupState.kt` | 첫 실행 안내 화면, 완료 기록(SharedPreferences `setup`) |
| `UpdateChecker.kt` | `version.json` 확인, 버전 비교 |
| `UpdateInstaller.kt`, `InstallResultReceiver.kt` | 앱 내 업데이트: 다운로드, APK 검증(패키지·서명·versionCode), PackageInstaller 설치, 확인 화면/결과 |
| `SettingsActivity.kt` | 설정 화면: 상태 표시, 권한 다시 요청, 서버 주소 변경(관리자 PIN, 연결 확인 후 저장) |
| `ServerConfig.kt`, `KtxApp.kt` | 서버 주소(기본 START_URL 또는 관리자 지정), 업데이트 주소는 그 호스트의 `/app/version.json`. Application에서 초기화 |
| `Browser.kt` | 기본 브라우저(없으면 Chrome)를 **패키지로 지정**해 열기 |

설정: `app/build.gradle.kts`의 `START_URL`, `VERSION_URL`(buildConfigField), release 서명(`KTX_*`), release APK 이름.
debug 전용: `app/src/debug/res/xml/network_security_config.xml`(localhost http 허용).

## 6. 다리 명세 (TMS와 공유 — 바꾸면 양쪽을 같이 고친다)

`window.KtxAndroidApp`, 메인 프레임이 `https://driver.withktx.com/...` 또는 `https://www.withktx.com/driver...`일 때만 동작.
다른 페이지에서는 `false` / `""` / `"{}"`를 돌려주고 아무것도 하지 않는다.

- `startTracking(phone, url[, options])` → `true|false`. phone은 숫자 10자리(앞의 1, 기호는 앱이 정리). url은 `withktx.com` 도메인 https만(포트는 URL에 포함 가능).
  위치 권한이 없으면 추적-켜짐을 저장하고 권한 흐름을 띄운 뒤, 허용되면 바로 시작.
  `options`(2026-09-28 추가)는 **JSON 문자열**: `JSON.stringify({interval: 30, heartbeat: 300})`(초). JS 객체를 그대로 넘기면
  다리가 받지 못해 조용히 기본값이 된다. interval 10..600(기본 60), heartbeat 60..3600(기본 300, interval보다 짧아지지 않음),
  범위 밖은 끝값으로 맞춤, JSON이 깨지면 `false`. 호출마다 전체 값을 정하고(생략 = 기본값), 추적 중 다시 부르면 즉시 적용.
- `stopTracking()`
- `status()` → JSON 문자열 `{"tracking":bool,"lastSentAt":epoch ms|null,"permission":"always|whileInUse|denied","battery":"unrestricted|restricted","interval":초,"heartbeat":초}`.
  `tracking`은 서비스가 실제로 돌고 있는지, `lastSentAt`은 서버가 마지막으로 받은(2xx) 시각.
- `requestPermissions()` — 비동기. 끝나면 이벤트로 알림.
- `version()` → `"1.0.0"`
- window 이벤트 `ktxappstatus` (`event.detail` = status 객체): 권한 흐름이 끝났을 때, 앱이 다시 화면에 나올 때.
- User-Agent 끝에 `KTXDriverApp/<versionName>`.

위치 전송: `POST <url>` form `id, lat, lon, timestamp(UTC 초), speed(knots), bearing, altitude, accuracy, batt`.

## 7. 확인된 사실과 결정 (이유 포함)

- **호스트**: `driver.withktx.com`은 모든 경로를 `www.withktx.com/driver/...`로 리다이렉트한다. 서버는 SMS 링크를
  요청 호스트로 만들므로 실제 링크는 `https://www.withktx.com/driver/s/<token>`. 그래서 App Links와 다리 허용 범위가
  두 호스트를 모두 포함한다(지시서는 driver.withktx.com만 언급).
- **간격과 하트비트**: 기본값은 Traccar 설정(`interval=60, heartbeat=300`)과 같게 — 60초마다 보내고, 5분간 새 보고가 없으면
  마지막 위치를 현재 시각으로 보낸다. 서버 stale 기준은 10분(`TMS_TRACKING_STALE_MINUTES`). 2026-09-28 사용자 요청으로
  서버가 `startTracking` options로 바꿀 수 있게 함(앱 업데이트 없이 조정). 서버 URL·포트·ID도 원래부터 웹이 넘기는 값이다.
  (고정: URL은 withktx.com https만, ID는 전화번호 10자리만, 시작 페이지와 version.json 주소는 빌드 설정.)
- **삼성: 최근 앱에서 밀어 닫기 — 배터리 예외가 있으면 괜찮고, 없으면 추적이 멈춘다** (2026-09-28 Galaxy S25+ 확인).
  예외 없음: `removeTask` 직후 프로세스가 서비스째 종료되고 START_STICKY로도 다시 살아나지 않음.
  예외 있음(`battery: unrestricted`): 밀어 닫아도 프로세스·서비스 유지, 전송 계속.
  → 첫 실행 안내 3번(배터리 제한 없음)이 필수. 서버 카드도 `status().battery`가 `restricted`면 경고하는 것이 좋다(TMS 작업).
- **WakeLock**: 추적 중에만 PARTIAL_WAKE_LOCK — 화면이 꺼져도 60초 주기 유지(Traccar Client 기본값과 같음).
- **업데이트는 앱 안에서 설치**(2026-09-28 사용자 요청으로 브라우저 방식에서 변경). `UpdateInstaller`가 내려받고,
  같은 패키지·같은 서명 키·더 높은 versionCode인지 확인한 뒤 PackageInstaller 세션으로 설치한다. Android 설치 확인 화면은
  `InstallResultReceiver`가 띄운다. Play 밖 앱이라 첫 업데이트는 드라이버가 한 번 눌러야 한다(Android 12+에서는 그 뒤
  이 앱이 설치 주체가 되어 확인이 생략될 수 있음 — `USER_ACTION_NOT_REQUIRED`). 첫 실행 안내 4번 항목은 **KTX Driver 자신의**
  "알 수 없는 앱 설치" 허용이며 실제 허용 여부(`canRequestPackageInstalls`)로 ✓ 표시.
- **Browser.open은 패키지를 지정**: 삼성 폰에서 selector(CATEGORY_APP_BROWSER) 방식은 관계없는 앱까지 나오는 선택 창을
  띄웠다. 그리고 plain ACTION_VIEW는 driver.withktx.com URL이면 App Links로 우리 앱에 돌아온다.
- **화면 문구**: 기본 영어, 폰 언어가 한국어면 `values-ko`.
- **라이브러리 버전 고정**: core-ktx 1.13.1, appcompat 1.7.0 (compileSdk 36으로 올린 뒤에도 그대로 둠; 올리면 다시 시험).
- **SDK 34 → 36** (2026-09-30, 사용자 요청으로 Google Play 등록 준비): 지시서의 targetSdk 34보다 우선. 두 flavor에 공통 적용.
- **flavor 분리**: Play 정책상 Play 앱은 스스로 업데이트할 수 없고 `REQUEST_INSTALL_PACKAGES`를 쓸 수 없다 → 앱 내 업데이트
  (`UpdateInstaller`, `InstallResultReceiver`, `UpdateUi`)는 `src/direct`, Play용은 빈 `UpdateUi`. 배터리 예외 직접 요청
  (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)도 Play 제한 권한이라 direct에만 두고, Play용은 안내 창 → 앱 설정(배터리 → 제한 없음).
- **백그라운드 위치 고지**(Play 요구, 두 빌드 공통): 위치 권한 대화상자 전에 `PermissionFlow`가 고지 창을 띄우고, 거부하면 위치 단계를 건너뛴다.
- **release minify 끔**: 켜면 `@JavascriptInterface` keep 규칙 필요(`proguard-rules.pro`에 주석으로 준비).
- **Chrome은 모든 APK 다운로드에 "유해한 파일일 수도 있음" 경고**를 띄운다 → 첫 설치(브라우저 다운로드) 안내에 "무시하고 다운로드".
  업데이트는 앱 안에서 하므로 이 경고가 없다.
- **재부팅 후에는 잠금을 한 번 풀어야** 추적 재시작(앱이 directBootAware가 아님).
- **Play 프로텍트**: 처음 보는 APK로 앱 내 업데이트를 하면 설치 확인 뒤 Play 프로텍트 창이 잠깐 뜨고
  `INSTALL_FAILED_VERIFICATION_FAILURE: Install not allowed`로 거부될 수 있다(2026-09-28 1회 발생, 창 문구는 못 봄).
  같은 APK로 다시 시도하자 검증 `ALLOW`로 설치됨. 앱은 실패 시 "업데이트가 설치되지 않았습니다" 안내 → 드라이버가 다시 시도.
  release APK로 재현되는지, 창에 "설치" 버튼이 있는지 TEST.md 7.3에서 확인할 것.
- **사무실 Wi-Fi에서는 withktx.com에 접속이 안 된다**: 폰이 Wi-Fi일 때 앱이 "연결할 수 없습니다", 이 PC에서도
  driver.withktx.com 연결 실패. 5G에서는 정상. 서버(24.65.144.62)가 같은 사무실 네트워크에 있어 생기는 헤어핀 NAT 문제로 추정
  — 앱이 아니라 네트워크 설정 문제. 시험은 모바일 데이터로 하거나, 사무실 라우터의 NAT loopback/내부 DNS를 설정한다.

## 8. 서명 (자세히: [SIGNING.md](SIGNING.md))

- keystore `C:\Users\Admin\.ktx-keys\ktx-driver.jks`, alias `ktx-driver`, 비밀번호는 `C:\Users\Admin\.gradle\gradle.properties`의
  `KTX_KEYSTORE_PASSWORD` / `KTX_KEY_PASSWORD`에만 있다. **대화·저장소에 출력하거나 넣지 않는다.**
- release 인증서 SHA-256 `95:D1:72:23:A3:1F:A7:7B:03:05:DB:9D:BD:77:84:50:FD:D2:43:B0:2C:87:AD:9B:C1:7F:CF:14:DA:B2:51:67`
  → [assetlinks.json](assetlinks.json).
- **사용자에게 keystore 백업을 요청해 둠**(완료 여부 미확인). 다음 세션에서 한 번 확인할 것.
- 빌드 결과(git 제외): 홈페이지 APK `app\build\outputs\apk\direct\release\ktx-driver-1.0.0.apk`(`assembleDirectRelease`),
  Google Play AAB `app\build\outputs\bundle\playRelease\app-play-release.aab`(`bundlePlayRelease`).
  대행업체용 업로드 인증서 `C:\Users\Admin\.ktx-keys\upload_certificate.pem`(공개 정보).

## 9. 시험 도구와 방법

| 도구 | 용도 |
|---|---|
| [tools/gps_receiver.ps1](../tools/gps_receiver.ps1) | `http://127.0.0.1:8099/`에서 OsmAnd POST를 받아 `tools/gps_received.log`에 기록 |
| [tools/version_server.ps1](../tools/version_server.ps1) `-Version 9.9.9` | 같은 포트에서 `/app/version.json`과 가짜 APK 제공 |
| [tools/cdp.ps1](../tools/cdp.ps1) `-Expr "<JS>"` | debug 앱 WebView에서 JS 실행(DevTools). 예: `-Expr "KtxAndroidApp.status()"` |

- 폰에서 PC 서버로: `adb reverse tcp:8099 tcp:8099` (끝나면 `adb reverse --remove tcp:8099`). 두 서버는 같은 포트라 하나씩.
- debug 빌드 adb 훅(다리 없이):
  `adb shell am start -n com.ktxtransport.driver/.MainActivity --es debug_tracking start --es phone 6045550100 --es url http://127.0.0.1:8099/gps`
  (`stop`), `--es debug_version_url http://127.0.0.1:8099/app/version.json`.
- 시험용 전화번호는 운영 DB를 건드리지 않게 **PC 수신기로만** 보냈다(`6045550100`). 운영 `/gps`로 시험 전송은 하지 않았다.
- 첫 실행 상태 재현(로그인 유지): `adb shell run-as com.ktxtransport.driver rm -f shared_prefs/setup.xml`,
  `pm revoke`로 권한 회수, `adb shell dumpsys deviceidle whitelist -com.ktxtransport.driver`로 배터리 예외 해제.
  **`pm clear`는 로그인 쿠키까지 지우므로 쓰지 않는다.**

## 10. 시험 폰 (사용자 개인 폰)

- Galaxy S25+ (SM-S936W), Android 16, adb serial `R3CY40PJVQW`. Secure Folder(user 150)가 있어 `pm list packages`는 `--user 0`을 붙인다.
- 2026-09-30 기준: 사용자가 9/29에 **release APK를 직접 설치**해 두었고(시험 계정 (123) 456-7890로 로그인, 위치 전송 중),
  그 위에 새 **direct release**(targetSdk 36)를 덮어 설치했다. 위치 항상 허용, 알림, 배터리 제한 없음, 알 수 없는 앱 설치 허용.
  release 서명이라 **debug APK는 덮어 설치할 수 없다**(지우면 로그인이 사라짐) → 시험은 release 빌드로 한다(DevTools/`cdp.ps1`은 debug 전용이라 못 씀).
- release 앱 기준 `www.withktx.com` App Link가 **verified**(서버 assetlinks.json 정상).
- 사무실 Wi-Fi에서는 사이트가 안 열린다 → 시험 때 사용자가 Wi-Fi를 끔(adb `svc wifi disable`은 Android 16에서 안 먹음).
  화면 꺼짐 방지는 `adb shell svc power stayon usb`로 켜고, 끝나면 `svc power stayon false`로 되돌린다.
- Chrome의 "알 수 없는 앱 설치" 스위치는 **켜지 않았다**(사용자 보안 설정이라 건드리지 않음).
- release로 바꾸려면 debug 앱을 지워야 하고, 그러면 로그인도 지워진다 — 사용자에게 먼저 묻는다.

## 11. 주의할 점 (이번 세션에서 겪은 것)

- adb 연결이 가끔 끊긴다. `adb devices`가 비고 장치 관리자에 MTP만 보이면 USB 디버깅이 꺼진 것 —
  사용자에게 개발자 옵션의 USB 디버깅, 삼성 "자동 차단(Auto Blocker)" 확인을 부탁한다.
- 시험 중 폰이 잠기면 DevTools 연결과 화면 탭이 실패한다. 사용자에게 잠금 해제를 부탁한다.
- `pm revoke`는 앱 프로세스를 죽인다 → DevTools 소켓 번호(`webview_devtools_remote_<pid>`)가 바뀐다. `cdp.ps1`은 `pidof`로 매번 찾는다.
- Windows PowerShell 5.1: `Invoke-RestMethod`/`ConvertFrom-Json`이 JSON 배열을 한 덩어리로 준다 → `| ForEach-Object { $_ }`로 펼친다.
- sdkmanager 라이선스는 PowerShell 파이프로 안 된다 → `cmd /c "sdkmanager --licenses < yes.txt"`.
- 리소스 폴더 이름을 바꾸면(예: `mipmap-anydpi-v26` → `mipmap-anydpi`) 증분 빌드가 깨질 수 있다 → `gradlew clean`.
- 화면 좌표 탭: 스크린샷은 1080×2340을 923×2000으로 줄여 보여 주므로 좌표에 1.17을 곱한다.

## 12. 다음 할 일

**사용자**
1. keystore 백업(SIGNING.md 3절) — 했는지 확인.

**TMS 세션** (이 저장소가 아니라 TMS 저장소에서, [TEST.md](TEST.md) 0절의 S1~S4)
1. (S1) 픽업 완료 화면: `if (window.KtxAndroidApp) KtxAndroidApp.startTracking(phone10, "https://www.withktx.com/gps")`.
2. (S2) 배송 완료 화면: 실린 로드가 없으면 `KtxAndroidApp.stopTracking()`.
2a. 드라이버 카드의 "No position yet today"가 UTC 기준 날짜로 보임(2026-10-03 알버타 17:18 MDT 위치가 있는데 19:2x에 "없음") — 표시는 현지(America/Vancouver 등) 날짜로 해야 함.
2b. 카드에서 `status().queued`/`lastError`로 "폰에 N건 대기 중 / 마지막 오류" 표시 가능(선택).
3. UA `KTXDriverApp/`이면 Traccar 안내 카드 대신 앱용 문구, `status()`/`ktxappstatus`로 카드 보강
   (`battery: "restricted"`나 `permission`이 `always`가 아니면 경고 + `requestPermissions()` 버튼).
   간격을 바꾸려면 `startTracking`의 세 번째 인자 `JSON.stringify({interval, heartbeat})`.
4. (S3) `assetlinks.json`을 `driver.withktx.com`과 `www.withktx.com`의 `/.well-known/`에 **리다이렉트 없이** 200 + `application/json`.
   지금 driver.withktx.com은 전부 리다이렉트하므로 nginx 예외 필요.
5. (S4) `/app/version.json`과 APK 호스팅. 아직 없음(`www.withktx.com/driver/app/version.json`은 404).
   driver.withktx.com 리다이렉트를 거쳐도 앱은 https 리다이렉트를 따라가지만, `/app/`도 예외로 직접 응답하는 편이 확실.
   다른 주소로 정하면 앱의 `VERSION_URL`만 바꾼다.
5a. 앱 설치 판별: `/app/manifest.webmanifest`와 `/.well-known/assetlinks.json`은 www.withktx.com에 이미 있으나(2026-09-28 확인),
   **운영 로그인 페이지(`/driver/`)에 `<link rel="manifest" href="/app/manifest.webmanifest">`가 없다** — 템플릿 배포 필요.
   배포되면 Chrome에서 `navigator.getInstalledRelatedApps()`가 앱을 돌려준다(앱 쪽은 확인 완료).
6. 드라이버 가이드 Android 판: 3.1·3.3 대신 "APK 설치 + 권한 허용", 첫 설치의 Chrome 경고 "무시하고 다운로드",
   업데이트는 앱의 "업데이트" → Android 확인 화면 "업데이트".

**앱 내 업데이트를 다시 시험할 때 (debug, 2026-09-28에 한 절차)**
versionCode 2 / versionName 9.9.9로 잠깐 바꿔 debug APK를 빌드해 복사하고 되돌린 뒤,
1.0.0 debug 설치 → `tools/version_server.ps1 -Version 9.9.9 -ApkPath <9.9.9 apk>` + `adb reverse tcp:8099 tcp:8099`
→ `--es debug_version_url http://127.0.0.1:8099/app/version.json`로 실행 → 업데이트 → 권한 화면 → 다운로드 → 설치 확인
→ `dumpsys package com.ktxtransport.driver | grep versionName`이 9.9.9 → `adb install -r -d`로 1.0.0 복구.

**서버 작업 후 (이 저장소)**
1. 사용자 동의 후 debug 앱 삭제 → release APK 설치 → [TEST.md](TEST.md) 전 항목 시험.
2. 문제가 나오면 고치고, `versionCode`/`versionName`을 올려 재배포(SIGNING.md 5절).
3. 선택: `lastSentAt`을 알림 문구에 표시, release minify(keep 규칙 필요), 모노크롬 아이콘(lint 경고).
