# 작업 현황 (WIP)

마지막 갱신: 2026-10-04. 다음 세션은 이 파일과 [CLAUDE.md](../CLAUDE.md)를 먼저 읽는다.
원래 지시서는 [ktxhybridapp.txt](../ktxhybridapp.txt)(0절 세션 규칙, 5절 작업 순서). iOS 판 지시서는 [ktxiosapp.txt](../ktxiosapp.txt).

## 1. 한 줄 요약

- **Android 앱은 기능상 완성**: 지시서 1)~9)단계와 그 뒤 추가 요청이 모두 `main`에 push되어 있다(마지막 코드 커밋 `a340d96`).
- **1.0.0이 운영 서버에 배포되어 있다.** `https://www.withktx.com/app/ktx-driver-1.0.0.apk`는 2026-10-04 로컬 direct release와 SHA-256이 같다.
- **남은 것은 현장 검증과 운영 준비다**(12절).
  - 드라이버 2~3명 시범 운영
  - 실기기 미확인 항목
  - 다른 기종 시험
  - Play 등록 서류
- **TMS 쪽 결정 대기**(13절): 픽업 없이 누른 Start tracking이 꺼지지 않는 문제, 위치 수집 시작 시점.
- **iOS 앱은 별도 저장소에서 맥북으로 시작했다**(14절). 이 저장소에는 지시서만 있다.

## 2. 세션 규칙 (지시서 0절)

- 답변·질문·설명은 한글로 쓴다. 코드·식별자·커밋 메시지는 영문으로 쓴다.
- 커밋은 사용자가 "커밋해줘"라고 할 때만 한다. 커밋하면 항상 `origin`에 push한다.
- 작업 완료를 보고하기 전에 `./gradlew assembleDebug` 통과를 확인하고 결과를 말한다.
- 단계가 끝날 때마다 빌드를 확인하고 커밋할지 묻는다.
- 앱 코드를 바꾸면 배포용도 다시 빌드한다(`assembleDirectRelease`, `bundlePlayRelease`). 결과 경로와 시각을 보고한다.
- 커밋 메시지 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`를 붙인다.
- TMS 파일은 이 세션에서 고치지 않는다. 읽기만 하고, 요청 문서나 문구로 정리해 사용자가 TMS 세션에 전달한다.
- 개인정보(사용자 전화번호 등)는 문서와 스크린샷에 넣지 않는다.

## 3. 저장소와 환경

| 항목 | 값 |
|---|---|
| 저장소 | `C:\ktxandroidapp` (지시서에 적힌 `C:\ktx-apk`가 아님) |
| origin | https://github.com/Jay-Vancouver/KTX.AndroidApp.git, 브랜치 `main`(공개 — raw 파일을 내려받을 수 있음) |
| 커밋 작성자 | `jay` / `system@ktxtransport.com` (저장소 로컬 설정) |
| push 인증 | Git Credential Manager에 저장됨 — 셸에서 `git push` 가능 |
| JDK | Temurin 17, `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot` (사용자 환경변수 `JAVA_HOME`) |
| Android SDK | `C:\Users\Admin\AppData\Local\Android\Sdk` (`ANDROID_HOME`): platform-tools, platforms;android-36(34도 있음), build-tools;36.0.0·35.0.0 |
| 빌드 | Gradle wrapper 8.11.1, AGP 8.10.1, Kotlin 1.9.24, compileSdk/targetSdk 36, minSdk 26, flavor `direct`/`play` |
| 버전 | `versionCode 1`, `versionName "1.0.0"` (배포됨 — 다음 배포 때 반드시 올린다) |
| TMS 저장소 | `\\wsl.localhost\Ubuntu-24.04\home\tms_user\ktx\tms` (읽기만) |

**PowerShell에서 빌드할 때**: 도구가 여는 셸에는 새 환경변수가 반영되지 않을 수 있으므로 매번 앞에 붙인다.

```powershell
Set-Location C:\ktxandroidapp
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','User')
$env:ANDROID_HOME = [Environment]::GetEnvironmentVariable('ANDROID_HOME','User')
.\gradlew.bat assembleDebug --console=plain -q
.\gradlew.bat assembleDirectRelease bundlePlayRelease --console=plain -q   # 배포용
```

Bash 도구도 쓸 수 있다(Git Bash). 설치 절차 전체는 [SETUP.md](SETUP.md)에 있다.

## 4. 커밋 기록과 실기기 확인

| 내용 | 커밋 | 실기기 확인 |
|---|---|---|
| 1) 도구 설치, SETUP.md | `25a803b` | — |
| 2) 프로젝트 골격, 아이콘 | `3692baa` | 아이콘 |
| 3) WebView, 카메라/파일, UA, App Links(www 포함) | `87aaef0`, `5588b2f` | UA, 카메라, 로그인 유지, PICK UP 카메라 |
| 4) 위치 서비스, OsmAnd 전송, 오프라인 큐, 하트비트, 부팅 재시작 | `c32e1e7` | 60초 주기(화면 꺼짐), 큐 순서, 업데이트 후 재시작, 중지 |
| 5) `KtxAndroidApp` 다리, 도메인 제한 | `c35ba89` | 모든 함수, 도메인 제한, 권한 없이 startTracking |
| 6) 첫 실행 권한 안내 | `96ad756` | 전체 흐름, "나중에", 완료 후 재표시 없음 |
| 7) 업데이트 확인 | `acb78c6` | 새 버전 안내, 같은 버전이면 표시 안 함, 12시간 제한 |
| 8) release 서명, SIGNING.md, assetlinks.json | `e26b25b` | release 빌드·서명 검증 |
| 9) TEST.md | `c9adeb7` | — |
| WIP 문서, `tools/cdp.ps1` | `443488f` | — |
| 업데이트를 앱 안에서 설치 | `258d0cf` | debug 1.0.0 → 9.9.9 성공(첫 시도는 Play 프로텍트 거부, 재시도 성공) |
| 전송 간격·하트비트를 `startTracking` options로, 브라우저에서 앱 설치 판별(`asset_statements`) | `bcd7f40` | `interval: 15`로 15초 전송, `getInstalledRelatedApps()`가 앱을 돌려줌 |
| 설치 매뉴얼 PDF(한글·영문) | `9bdddfd` | — (이 커밋에 파일 이름 변경 2건도 섞여 들어감) |
| Play/홈페이지 빌드 분리, targetSdk 36, 위치 고지 창, 설정 화면(상단 스와이프, 관리자 PIN으로 서버 주소 변경) | `144bfa4` | 2026-09-30 S25+ release: 상태 표시줄, 키보드, 고지 창, Play용 배터리 안내, 설정 화면 열기, PIN 거부/통과. **주소 실제 변경·저장은 미시험** |
| Play 스토어 그래픽·문구, TMS 개인정보처리방침 요청서 | `d71e0da` | — |
| 폰 언어가 바뀌면 알림 문구도 다시 게시 | `a1b0f97` | **미확인** |
| 전송 스레드 감시(watchdog), 전송 실패 로그·설정 화면 표시, `status()`에 `queued`/`lastError` | `a340d96` | 데이터를 끄면 실패 원인이 기록되고, 다시 켜면 1초 안에 전송됨. **멈춘 전송을 교체하는 상황 자체는 재현 못 함** |
| iOS 앱 지시서 | `b0bfbe4` | — |

**한 번도 확인하지 못한 것**([TEST.md](TEST.md)에 항목 있음):
- 5분 하트비트(4.3)
- 실제 재부팅 후 재시작(6.1)
- 점검 사진 업로드(3.5)
- release APK끼리 앱 내 업데이트(7절)
- 서버 주소 변경·저장(8.5~8.8)
- 알림 언어 전환, watchdog 교체
- 실제 운행으로 픽업 → 장거리 → 배송 → 자동 중지 전체, 8~10시간 배터리 사용량
- S25+(Android 16) 말고 다른 폰(Android 8~12, 삼성 외)
- Play로 설치한 빌드

## 5. 코드 지도

`app/src/main/java/com/ktxtransport/driver/`

| 파일 | 역할 |
|---|---|
| `MainActivity.kt` | WebView(쿠키, UA, 카메라 위임, 파일 선택, 뒤로 가기, 오류 화면), 다리 등록, `bridgeAllowed`, 상단 스와이프로 설정 열기(`dispatchTouchEvent`, 왼쪽 48dp 제스처 제외), 서버 주소가 바뀌면 다시 로드, debug adb 훅 |
| `WebHosts.kt` | 앱 안에서 여는 호스트, 다리 허용 URL(`isBridgeUrl`), 위치 전송 허용 호스트(`isTrackingHost`: withktx.com 또는 관리자 지정 주소의 도메인) |
| `KtxBridge.kt` | `window.KtxAndroidApp` 함수, `statusJson`, 이벤트 이름 |
| `LocationService.kt` | 위치 Foreground Service, interval/heartbeat(`applyCadence`), WakeLock, 알림(`onConfigurationChanged`에서 언어 반영), `start/stop/resumeIfTracking` |
| `FixQueue.kt` | SQLite 큐(`fixes.db`, 최대 1만 건) |
| `FixUploader.kt` | 큐를 순서대로 POST. 2xx는 삭제, 408·429·3xx·5xx·네트워크 오류는 재시도, 그 밖의 4xx와 잘못된 URL은 버림. 한 건이 60초를 넘으면 연결을 끊고 실행기를 교체(watchdog). 실패는 `Log.w`와 `TrackingState.recordSendError` |
| `TrackingState.kt` | SharedPreferences `tracking`: tracking, phone, url, lastSentAt, interval, heartbeat, lastSendError. `parseCadence`, `normalizePhone`, `isUsableUrl` |
| `BootReceiver.kt` | `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED` → `resumeIfTracking` |
| `AppPermissions.kt`, `PermissionFlow.kt` | 권한 상태와 요청 흐름: 고지 창 → 위치 → 항상 → 알림 → 배터리 |
| `SetupActivity.kt`, `SetupState.kt` | 첫 실행 안내(4번 "알 수 없는 앱 설치"는 direct에만) |
| `SettingsActivity.kt` | 상태, 대기 건수, 마지막 오류, 권한 다시 요청, 서버 주소 변경(PIN, 연결 확인, 기본값으로) |
| `ServerConfig.kt`, `KtxApp.kt` | 시작 주소(기본 또는 관리자 지정), 업데이트 주소 = 그 호스트의 `/app/version.json` |
| `UpdateChecker.kt` | `version.json` 확인, 버전 비교 |
| `SystemBars.kt` | targetSdk 35+ edge-to-edge 대응(상태 표시줄 파란 배경) |
| `Browser.kt` | 기본 브라우저를 패키지로 지정해 열기 |
| `src/direct/` | `UpdateUi.kt`, `UpdateInstaller.kt`, `InstallResultReceiver.kt`, 매니페스트(설치·배터리 예외 권한) |
| `src/play/` | 빈 `UpdateUi.kt`(`NEEDS_INSTALL_PERMISSION = false`) |

설정은 `app/build.gradle.kts`에 있다.
- buildConfigField: `START_URL`, `VERSION_URL`, `ADMIN_PIN_SHA256`, flavor별 `DIRECT_BATTERY_REQUEST`
- release 서명은 `KTX_*`
- APK 이름 `ktx-driver-<v>.apk` / `-play.apk`
- 맨 위에 `import java.security.MessageDigest`가 있다. 전체 이름으로 쓰면 Gradle의 `java` 확장과 충돌한다.

## 6. 다리 명세 (TMS와 공유 — 바꾸면 양쪽을 같이 고친다)

`window.KtxAndroidApp`은 메인 프레임이 `https://driver.withktx.com/...`, `https://www.withktx.com/driver...`, 또는 관리자가 지정한 주소 아래일 때만 동작한다. 그 밖의 페이지에서는 `false` / `""` / `"{}"`를 돌려준다.

- `startTracking(phone, url[, options])` → `true|false`.
  - phone은 숫자 10자리다(앞의 1과 기호는 앱이 정리).
  - url은 https이고 withktx.com 또는 지정 주소의 도메인이어야 한다.
  - `options`는 **JSON 문자열**이다. 예: `JSON.stringify({interval, heartbeat})`(초). interval 10..600(기본 60), heartbeat 60..3600(기본 300). 범위 밖이면 끝값으로 맞춘다. JSON이 깨지면 `false`. 추적 중 다시 부르면 즉시 적용된다.
  - 위치 권한이 없으면 추적-켜짐을 저장하고 권한 흐름을 띄운다. 허용되면 바로 시작한다.
- `stopTracking()`
- `status()` → JSON 문자열 `{tracking, lastSentAt, permission:"always|whileInUse|denied", battery:"unrestricted|restricted", interval, heartbeat, queued, lastError}`.
- `requestPermissions()`, `version()` → `"1.0.0"`.
- 이벤트 `ktxappstatus`(detail = status 객체): 권한 흐름이 끝났을 때, 앱이 다시 화면에 나올 때.
- UA 끝에 `KTXDriverApp/<versionName>`이 붙는다.
- 위치 전송: `POST <url>` form `id, lat, lon, timestamp(UTC 초), speed(knots), bearing, altitude, accuracy, batt`.

**TMS가 다리를 호출하는 곳**(2026-10-03 읽어서 확인): `app/templates/driver/`
- `scan_result.html`: 픽업 후 `startTracking`
- `delivery_result.html`: `{% if remaining == 0 %}`일 때 `stopTracking` — 앱을 끄는 유일한 곳
- `status.html`: 앱 카드. 버튼 `#drv-app-start`(Start tracking), `#drv-app-perm`. 함수 `drvAppRender()`, `drvAppStart()`
- 간격 옵션은 `app/config.py`에서 온다.

## 7. 확인된 사실과 결정 (이유 포함)

- **호스트**: `driver.withktx.com`은 `www.withktx.com/driver/...`로 리다이렉트한다. SMS 링크는 `https://www.withktx.com/driver/s/<token>`이다. 그래서 App Links와 다리가 두 호스트를 모두 포함한다. release 앱 기준 www App Link가 verified.
- **간격·하트비트**: 기본은 60초/5분으로 Traccar와 같다. 서버 stale 기준은 10분이다. 서버가 options로 바꿀 수 있다.
- **삼성에서 최근 앱 목록에서 밀어 닫기**: 배터리 예외가 있으면 추적이 유지된다. 없으면 프로세스가 죽고 다시 살아나지 않는다. 그래서 배터리 "제한 없음"이 필수다.
- **WakeLock**: 추적 중에만 PARTIAL_WAKE_LOCK.
- **업데이트는 앱 안에서 설치**(direct만): 같은 패키지·같은 서명·더 높은 versionCode인지 검증한 뒤 PackageInstaller로 설치한다. Play 프로텍트가 처음 보는 APK를 한 번 거부할 수 있다. 다시 시도하면 된다.
- **flavor 분리**: Play 앱은 스스로 업데이트할 수 없고, `REQUEST_INSTALL_PACKAGES`와 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`를 쓸 수 없다. 그래서 이 기능들은 direct에만 있다. Play용 배터리 단계는 안내 창을 띄운 뒤 앱 설정을 연다.
- **백그라운드 위치 고지**(두 빌드 공통): 권한 대화상자 전에 띄운다. 문구는 "픽업부터 그날의 마지막 배송까지만 전송"이다. 수집 시점 정책이 바뀌면 `values/strings.xml`과 `values-ko/strings.xml`의 `disclosure_message`, 그리고 TMS 개인정보처리방침을 같이 바꾼다.
- **targetSdk 36**: edge-to-edge가 강제된다. 흰 상태 표시줄 아이콘이 안 보이는 문제가 생겨 루트 배경을 `ktx_blue`로 했다.
- **관리자 PIN**: `C:\Users\Admin\.gradle\gradle.properties`의 `KTX_ADMIN_PIN`(10자리)에 둔다. 저장소의 `gradle.properties`가 아니다. APK에는 SHA-256만 들어간다. PIN을 바꾸면 다시 빌드해야 한다. 값은 출력하지 않는다.
- **전송 멈춤 사고(2026-10-03)**:
  - 알버타 시험에서 앱이 40시간 실행되던 중 17:18부터 2시간 동안 전송이 멈췄다.
  - 폰 위치, 네트워크, 서버(수동 POST 200)는 모두 정상이었다. 강제 종료 후 다시 열자 즉시 회복됐다.
  - 원인은 전송 스레드 하나가 막힌 것으로 판단했다. 그래서 watchdog을 추가했다(`a340d96`).
- **알림 언어**: 서비스 시작 때 언어로 고정되던 문제를 고쳤다. 언어가 바뀌면 `createConfigurationContext`로 다시 게시한다.
- **Chrome은 모든 APK 다운로드에 경고**를 띄운다. 매뉴얼에 "무시하고 다운로드"로 안내되어 있다.
- **재부팅 후에는 잠금을 한 번 풀어야** 추적이 다시 시작된다.
- **사무실 Wi-Fi에서는 withktx.com이 공인 IP(24.65.144.62)로 안 열린다.** 헤어핀 NAT 때문이다. PC에서는 www.withktx.com이 내부 192.168.0.56으로 해석된다. 폰 시험은 모바일 데이터로 한다.
- **Start tracking 버튼은 픽업이 없어도 전송을 시작한다**(TMS 설계, 안내문 "To start it now, tap Start tracking"). 앱은 화물 상태를 모르고 지시만 따른다. 문제점과 처리 방향은 13절.
- **시험 서버** `https://test.ktxtransport.com/driver/`와 `/app/version.json`이 200으로 응답한다(2026-10-03). 앱 설정 화면에서 서버 주소를 이 주소로 바꾸면 운영 데이터 없이 픽업/배송 흐름을 시험할 수 있다. 다리와 위치 전송 주소(ktxtransport.com 도메인)도 따라간다.

## 8. 서명과 배포물 (자세히: [SIGNING.md](SIGNING.md))

- keystore는 `C:\Users\Admin\.ktx-keys\ktx-driver.jks`, alias는 `ktx-driver`다. 비밀번호는 `C:\Users\Admin\.gradle\gradle.properties`의 `KTX_*`에만 있다. **출력하거나 저장소에 넣지 않는다. .jks는 대행업체에 보내지 않는다.**
- SHA-256: `95:D1:72:23:A3:1F:A7:7B:03:05:DB:9D:BD:77:84:50:FD:D2:43:B0:2C:87:AD:9B:C1:7F:CF:14:DA:B2:51:67` → [assetlinks.json](assetlinks.json).
- **keystore와 PIN 백업 여부는 아직 사용자에게 확인하지 못했다.**
- 홈페이지 APK: `app\build\outputs\apk\direct\release\ktx-driver-1.0.0.apk`. 2026-10-04 01:39:29 UTC 빌드, 3,152,772 bytes, 서버 파일과 같다.
- Play AAB: `app\build\outputs\bundle\playRelease\app-play-release.aab`. 대행업체에는 이 파일과 업로드 인증서 `C:\Users\Admin\.ktx-keys\upload_certificate.pem`만 보낸다.
- 스토어 자료는 [docs/store/](store/)에 있다: 아이콘 512, 그래픽 1024×500(한·영), 스크린샷 3장(한·영), [LISTING.md](store/LISTING.md). 다시 만들 때는 `build_graphics.ps1`을 쓴다. 이 스크립트는 UTF-8 BOM으로 저장해야 한다.
- 설치 매뉴얼은 [docs/manual/](manual/)에 있다: `KTX_Driver_Install_Guide_KO.pdf`/`_EN.pdf`. 다시 만들 때는 `build_pdf.ps1`(Edge headless, `Start-Process -Wait`)을 쓴다.
- **다음 배포**:
  1. `versionCode`와 `versionName`을 올린다(예: 2 / 1.0.1).
  2. `assembleDirectRelease`로 빌드한다.
  3. TMS 서버 `data/images/android`에 APK를 올리고 `version.json`을 갱신한다(TMS 세션).
  4. Play는 `bundlePlayRelease`로 만든 AAB를 대행업체에 보낸다.

## 9. 시험 도구와 방법

| 도구 | 용도 |
|---|---|
| [tools/gps_receiver.ps1](../tools/gps_receiver.ps1) | `http://127.0.0.1:8099/`에서 OsmAnd POST를 받아 `tools/gps_received.log`에 기록 |
| [tools/version_server.ps1](../tools/version_server.ps1) `-Version 9.9.9 [-ApkPath <apk>]` | 같은 포트에서 `/app/version.json`과 APK 제공 |
| [tools/cdp.ps1](../tools/cdp.ps1) `-Expr "<JS>"` | debug 앱 WebView에서 JS 실행 |

- 폰에서 PC로 연결: `adb reverse tcp:8099 tcp:8099`. 두 서버는 같은 포트를 쓰므로 하나씩 띄운다.
- debug adb 훅: `--es debug_tracking start --es phone 6045550100 --es url http://127.0.0.1:8099/gps`(`stop`), `--es debug_version_url ...`.
- 운영 `/gps`로는 시험 전송을 하지 않았다. 시험 번호는 `6045550100`이고 PC 수신기로만 보냈다.
- 첫 실행 상태 재현: `run-as ... rm -f shared_prefs/setup.xml`, `pm revoke`, `dumpsys deviceidle whitelist -com.ktxtransport.driver`. **`pm clear`는 쓰지 않는다**(로그인이 지워짐).
- release 앱 진단: `adb logcat -s FixUploader LocationService`, 설정 화면의 "대기 중인 위치"와 "마지막 전송 오류".

## 10. 시험 폰 (사용자 개인 폰)

- Galaxy S25+ (SM-S936W), Android 16, adb serial `R3CY40PJVQW`. Secure Folder(user 150)가 있어서 `pm` 명령에 `--user 0`을 붙인다.
- **direct release**가 설치되어 있다(release 서명이라 debug를 덮어 설치할 수 없다. 지우면 로그인이 사라진다). 시험 계정으로 로그인되어 있고, 권한은 모두 허용되어 있다.
- 화면 켜 두기: `adb shell svc power stayon usb`. 끝나면 `false`로 되돌린다.
- Chrome의 "알 수 없는 앱 설치" 스위치와 같은 보안 설정은 건드리지 않는다.

## 11. 주의할 점

- adb가 끊기면 USB 디버깅과 삼성 "자동 차단(Auto Blocker)"을 확인해 달라고 사용자에게 부탁한다. 폰이 잠기면 탭과 DevTools가 실패한다.
- PowerShell 5.1:
  - BOM 없는 스크립트의 한글이 깨진다.
  - `ConvertFrom-Json` 배열은 `| ForEach-Object { $_ }`로 펼쳐야 한다.
  - sdkmanager 라이선스 동의는 `cmd /c "... < yes.txt"`로 한다.
- 리소스 폴더 이름을 바꾸면 `gradlew clean`이 필요하다. lint MissingTranslation은 `translatable="false"`로 처리한다.
- 스크린샷 좌표: 1080×2340이 923×2000으로 줄어 보이므로 1.17을 곱한다.

## 12. 운영 서버 상태 (2026-10-04 확인)

| 항목 | 상태 |
|---|---|
| `https://www.withktx.com/app/version.json` | `{"version": "1.0.0", "apk": "https://www.withktx.com/app/ktx-driver-1.0.0.apk"}` |
| APK | 최신 로컬 빌드와 같음(SHA-256 일치) |
| `www.withktx.com/.well-known/assetlinks.json` | 200 |
| `driver.withktx.com/.well-known/assetlinks.json` | PC에서 연결 안 됨(사무실 네트워크 문제일 수 있음, 폰으로는 미확인) |
| `/app/manifest.webmanifest` | 200. 로그인 전 `/driver/` 페이지에는 `<link rel="manifest">`가 없음(로그인 후 페이지는 미확인) |
| 개인정보처리방침 | KTX Driver 앱과 백그라운드 위치 내용 반영됨 |
| `/.well-known/apple-app-site-association` | 없음(로그인 페이지로 302) — iOS Universal Links용으로 TMS 작업 필요 |

## 13. 결정 대기 / TMS 요청 (2026-10-03 대화)

**A. 픽업 없이 Start tracking → 꺼지지 않는 전송**
- 끄는 곳이 `delivery_result.html`(남은 0건)뿐이다. 그래서 픽업 없이 누르거나 배차 담당자가 화물을 닫으면 위치가 계속 전송된다. 드라이버 화면에는 Stop 버튼도 없다.
- 이는 고지·방침 문구("픽업부터 마지막 배송까지만")와 맞지 않는다.
- 사용자에게 TMS 세션용 요청 문구를 대화로 전달했다(파일로는 저장하지 않음). 요지는 다음과 같다.
  1. 시험 모드(`tracking_test_mode`: 시험 서버이거나 시험용 드라이버 계정)를 판단하고, 진행 중 화물 수(`active_loads`)를 템플릿에 넘긴다.
  2. 일반 드라이버: 화물이 0건이면 Start 버튼을 숨긴다. 상태 페이지에서 `tracking===true`이고 화물이 0건이면 자동으로 `stopTracking()`을 호출한다.
  3. 시험 모드: Start와 Stop 버튼을 항상 보이고, 자동 정리는 하지 않으며, "TEST"를 표시한다.
  4. 시험 서버 확인: 코드가 최신인지, 시험 서버 페이지가 넘기는 위치 전송 주소(시험 서버로 가야 함), 시험 드라이버·화물을 만드는 방법, 지도 표시.
  5. 드라이버 카드의 "No position yet today"가 UTC 날짜 기준이다. 현지 시간대(America/Edmonton)로 바꿔야 한다.
- 앱 변경은 없다. TMS 세션이 답하면 시험 서버의 위치 전송 주소가 앱의 `isUsableUrl`을 통과하는지 확인한다.
- 그때까지 드라이버에게 "픽업 전에는 Start tracking을 누르지 말 것"을 안내한다.

**B. 위치 수집 시작 시점**(사용자 결정 대기)
- 선택지:
  - A 현재: 픽업 → 마지막 배송
  - **B 배차 배정/확인 → 마지막 배송 또는 취소(권장)**
  - C 근무 시작/종료 버튼
  - D 1회 조회(앱 기능 추가 필요)
- A~C는 TMS가 호출 시점만 바꾸면 된다. 다만 앱의 `disclosure_message`(한·영), TMS 개인정보처리방침, 드라이버 안내를 함께 바꿔야 한다. 알버타 PIPA 기준 업무 범위 안에서 사전 고지가 필요하다.
- 결정되면 이 저장소에서 할 일: 고지 문구를 수정하고, 버전을 올려 재빌드한 뒤 배포한다.

**C. Google Play 등록**(대행업체 이용)
- 남은 것: 회사 조직 개발자 계정, 심사자 로그인 방법(SMS 로그인이라 TMS가 심사용 번호나 고정 링크를 마련), 시연 영상 2개, 지원 이메일. [LISTING.md](store/LISTING.md) 5절의 `____` 칸.
- 2027년경부터는 Play 밖 배포(APK)에도 Android 개발자 인증 등록이 필요해질 예정이다.

## 14. iOS 앱 (별도 저장소, 맥북)

- 지시서는 [ktxiosapp.txt](../ktxiosapp.txt)(`b0bfbe4`)다. 맥은 raw URL로 받는다: `https://raw.githubusercontent.com/Jay-Vancouver/KTX.AndroidApp/main/ktxiosapp.txt`.
- 핵심 결정:
  - Swift + WKWebView + CoreLocation, XcodeGen, iOS 16 이상.
  - 다리 이름은 **`KtxAndroidApp` 그대로**이고 동기 호출을 흉내 낸다(`prompt` 방식). 그래서 TMS 코드를 고치지 않아도 된다.
  - `battery`는 항상 `"unrestricted"`로 보고한다.
  - 앱 내 업데이트는 없다.
  - 배포는 App Store Unlisted를 검토하고, 시범 운영은 TestFlight로 한다.
- 진행 상태(2026-10-04): 맥북 에어에 `~/ktxiosapp` 폴더를 만들었다. Claude Code를 설치했고(`~/.local/bin`을 `~/.zshrc`의 PATH에 추가) 로그인했다. **Xcode, Homebrew 등 다른 도구는 아직 설치하지 않았다.** 첫 지시는 "ktxiosapp.txt를 읽고 0절의 세션 규칙을 기억해 줘. 5절의 작업 순서대로 1)부터 시작해."로 안내했다.
- 사용자가 할 일:
  - App Store에서 Xcode 설치
  - Homebrew 설치(맥 비밀번호 필요, 별도 터미널에서)
  - GitHub에 `KTX.iOSApp` 저장소 만들기
  - Apple Developer Program 회사 계정 신청(D-U-N-S 필요)
- 맥 세션은 TMS 저장소에 접근할 수 없다. TMS 쪽 확인이 필요하면 사용자가 이 PC 쪽으로 가져온다.
- iOS용 TMS 작업(맥 세션이 `docs/TMS_REQUEST_ios.md`로 정리 예정): apple-app-site-association, 아이폰 앱 안 문구(APK·배터리 안내 숨김), App Store 심사 계정.

## 15. 다음 할 일 (이 저장소)

1. 사용자에게 keystore와 PIN 백업 여부를 확인한다(SIGNING.md 3절).
2. 13절 A에 대한 TMS 응답이 오면 시험 서버로 전체 흐름을 시험한다.
   - 앱 설정 → 서버 주소 `https://test.ktxtransport.com/driver/` → 시험 화물로 픽업 → 배송 → 자동 중지 → "기본값으로".
   - TEST.md 8.5~8.8도 이때 함께 확인한다.
3. 13절 B가 결정되면 고지 문구를 수정하고 1.0.1로 빌드한다.
4. 드라이버 2~3명 시범 운영(1~2주): TEST.md 전체, 실제 운행, 배터리, 다른 기종. 문제가 나오면 수정하고 버전을 올려 재배포한다. 이때 release끼리 앱 내 업데이트도 검증한다.
5. 선택: 알림에 `lastSentAt` 표시, release minify(keep 규칙 필요), 모노크롬 아이콘.
