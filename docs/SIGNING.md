# Release 서명과 배포

KTX Driver 앱은 Google Play가 아니라 사내 홈페이지에서 APK로 배포한다. Android는 **처음 설치한 APK와 같은 키로
서명된 APK만** 업데이트로 받아 준다. 따라서 이 문서의 keystore는 앱의 신분증이다.

> **keystore나 비밀번호를 잃어버리면 이미 설치된 모든 드라이버 폰에 더 이상 업데이트를 설치할 수 없다.**
> 드라이버가 앱을 지우고 새 키의 앱을 다시 설치해야 하며, 그러면 로그인과 권한 설정도 처음부터 다시 해야 한다.

## 1. 현재 키

| 항목 | 값 |
|---|---|
| keystore 파일 | `C:\Users\Admin\.ktx-keys\ktx-driver.jks` (저장소 밖) |
| 형식 / 알고리즘 | PKCS12, RSA 4096 |
| alias | `ktx-driver` |
| 유효 기간 | 2026-09-27 ~ 2126-09-03 |
| 인증서 DN | `CN=KTX Transport, O=KTX Transport, L=Vancouver, ST=BC, C=CA` |
| 인증서 SHA-256 | `95:D1:72:23:A3:1F:A7:7B:03:05:DB:9D:BD:77:84:50:FD:D2:43:B0:2C:87:AD:9B:C1:7F:CF:14:DA:B2:51:67` |

비밀번호는 무작위 32자로 만들었고, 빌드 PC의 `C:\Users\Admin\.gradle\gradle.properties`에만 있다.
keystore 비밀번호와 key 비밀번호는 같다.

## 2. Gradle이 서명 정보를 읽는 방법

[app/build.gradle.kts](../app/build.gradle.kts)는 아래 네 값을 **사용자 홈의 `~/.gradle/gradle.properties`** 또는
**같은 이름의 환경변수**에서 읽는다. 저장소의 `gradle.properties`에는 절대 넣지 않는다.

```properties
KTX_KEYSTORE_FILE=C:/Users/Admin/.ktx-keys/ktx-driver.jks
KTX_KEYSTORE_PASSWORD=<비밀번호>
KTX_KEY_ALIAS=ktx-driver
KTX_KEY_PASSWORD=<비밀번호>
```

값이 없으면 `assembleRelease`는 **서명되지 않은 APK**를 만든다(설치 불가). `.gitignore`가 `*.jks`, `*.keystore`,
`keystore.properties`를 막고 있지만, 커밋 전에 `git status`로 한 번 더 확인한다.

## 3. 백업 (반드시)

백업할 것은 두 가지다: **`ktx-driver.jks` 파일**과 **`gradle.properties`의 KTX_ 네 줄**(비밀번호).

1. 회사 비밀번호 관리자(1Password, Bitwarden 등)에 항목을 하나 만들고 `.jks` 파일을 첨부, 비밀번호와 alias를 적는다.
2. 오프라인 사본: USB 드라이브에 `.jks`와 비밀번호를 적은 파일을 넣어 금고 등 PC와 다른 곳에 보관한다.
3. 두 곳 모두 **복원 시험**을 한 번 한다: 다른 폴더에 복사한 `.jks`로
   `keytool -list -v -keystore <복사본> -alias ktx-driver`를 실행해 SHA-256이 위 표와 같은지 확인.
4. 이메일, 메신저, 공유 드라이브에 비밀번호를 평문으로 올리지 않는다. 저장소에는 절대 커밋하지 않는다.

담당자가 바뀌면 이 두 곳의 접근 권한을 넘긴다.

## 4. 다른 PC에서 release 빌드

1. [SETUP.md](SETUP.md)대로 개발 환경을 설치한다.
2. 백업에서 `.jks`를 저장소 밖(예: `C:\Users\<사용자>\.ktx-keys\`)에 복사한다.
3. `C:\Users\<사용자>\.gradle\gradle.properties`에 2절의 네 줄을 넣는다(경로는 `/`로).
   CI처럼 파일을 둘 수 없으면 같은 이름의 환경변수로 준다.

## 5. 새 버전 배포 절차

1. [app/build.gradle.kts](../app/build.gradle.kts)의 `versionCode`를 1 올리고(정수, 항상 증가), `versionName`을 올린다(예: `1.0.1`).
   `versionCode`가 설치된 것보다 크지 않으면 Android가 업데이트를 거부한다.
2. 빌드:
   ```powershell
   .\gradlew.bat assembleRelease
   ```
   결과: `app\build\outputs\apk\release\ktx-driver-<versionName>.apk`
3. 서명 확인:
   ```powershell
   & "$env:ANDROID_HOME\build-tools\34.0.0\apksigner.bat" verify --print-certs app\build\outputs\apk\release\ktx-driver-1.0.1.apk
   ```
   `certificate SHA-256 digest`가 1절의 지문(콜론 없이 소문자)과 같아야 한다.
4. APK를 서버(`driver.withktx.com/app/` 또는 사내 홈페이지)에 올리고, `https://driver.withktx.com/app/version.json`을 고친다.
   ```json
   {"version": "1.0.1", "apk": "https://driver.withktx.com/app/ktx-driver-1.0.1.apk", "notes": "What changed"}
   ```
   앱은 화면에 나올 때(프로세스당 12시간에 한 번) 이 파일을 읽고, `version`이 더 높으면 업데이트 안내를 띄운다.
5. `git tag v1.0.1` 후 push.

## 6. App Links 인증 (assetlinks.json)

SMS 로그인 링크를 누르면 브라우저가 아니라 앱이 바로 열리게 하려면, 서버가 아래 파일을 제공해야 한다.
내용은 [docs/assetlinks.json](assetlinks.json)에 있다.

- `https://driver.withktx.com/.well-known/assetlinks.json`
- `https://www.withktx.com/.well-known/assetlinks.json`

조건 (Android가 엄격하게 확인함):
- **리다이렉트 없이** 200으로 응답해야 한다. 지금 `driver.withktx.com`은 모든 경로를 `www.withktx.com/driver/`로
  보내므로, nginx에서 `/.well-known/assetlinks.json`만 예외로 직접 응답하게 해야 한다.
- `Content-Type: application/json`, https(유효한 인증서).

확인 (release APK를 설치한 폰에서):
```powershell
adb shell pm verify-app-links --re-verify com.ktxtransport.driver
adb shell pm get-app-links com.ktxtransport.driver   # 두 도메인 모두 verified 여야 함
```

## 7. debug와 release

- debug APK는 이 PC의 자동 생성 debug 키로 서명되므로 **release와 서로 덮어 설치할 수 없다**.
  바꾸려면 앱을 지워야 하고, 그러면 폰의 로그인 쿠키와 권한도 지워진다.
- 드라이버에게는 항상 release APK만 배포한다. debug 빌드에는 시험용 기능(adb로 추적 시작, localhost http 허용)이 들어 있다.
- `assetlinks.json`에는 release 지문만 넣는다.
