# 개발 환경 설치 (Windows, Android Studio 없이)

KTX Driver 앱은 VS Code + 명령줄 도구만으로 빌드한다. 아래는 Windows 11에서 실제로 설치한 절차다.
모든 명령은 PowerShell 기준이다.

## 설치되는 것

| 도구 | 버전 | 위치 |
|---|---|---|
| Git for Windows | 2.55 | `C:\Program Files\Git` |
| JDK (Eclipse Temurin) | 17 | `C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot` |
| Android SDK cmdline-tools | latest (13114758) | `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest` |
| platform-tools (adb) | 최신 | `%LOCALAPPDATA%\Android\Sdk\platform-tools` |
| platforms;android-34 | 34 | `%LOCALAPPDATA%\Android\Sdk\platforms\android-34` |
| build-tools | 34.0.0 | `%LOCALAPPDATA%\Android\Sdk\build-tools\34.0.0` |
| Gradle | 8.7 (wrapper) | 저장소의 `gradlew.bat`이 처음 실행 시 자동으로 내려받음 |

## 1. Git, JDK 17

```powershell
winget install --id Git.Git -e --silent --accept-package-agreements --accept-source-agreements
winget install --id EclipseAdoptium.Temurin.17.JDK -e --silent --accept-package-agreements --accept-source-agreements
```

## 2. Android cmdline-tools

sdkmanager는 반드시 `cmdline-tools\latest\bin` 구조 안에 있어야 동작한다.

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
New-Item -ItemType Directory -Force "$sdk\cmdline-tools" | Out-Null
Invoke-WebRequest "https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip" -OutFile "$env:TEMP\cmdline-tools.zip"
Expand-Archive "$env:TEMP\cmdline-tools.zip" -DestinationPath "$sdk\cmdline-tools\_tmp"
Move-Item "$sdk\cmdline-tools\_tmp\cmdline-tools" "$sdk\cmdline-tools\latest"
Remove-Item -Recurse "$sdk\cmdline-tools\_tmp", "$env:TEMP\cmdline-tools.zip"
```

최신 zip 파일 이름은 https://developer.android.com/studio#command-line-tools-only 에서 확인한다.

## 3. 환경변수 (사용자 범위)

```powershell
$jdk = (Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory | Where-Object Name -like 'jdk-17*' | Select-Object -First 1).FullName
[Environment]::SetEnvironmentVariable('JAVA_HOME', $jdk, 'User')
[Environment]::SetEnvironmentVariable('ANDROID_HOME', "$env:LOCALAPPDATA\Android\Sdk", 'User')
$p = [Environment]::GetEnvironmentVariable('Path', 'User')
[Environment]::SetEnvironmentVariable('Path', "$p;$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin;$env:LOCALAPPDATA\Android\Sdk\platform-tools", 'User')
```

설정 후 VS Code와 터미널을 **모두 닫았다가 다시 열어야** 새 환경변수가 적용된다.

## 4. SDK 패키지와 라이선스

PowerShell 파이프(`'y' | sdkmanager ...`)로는 라이선스 동의가 전달되지 않는다. `y`를 적은 파일을 cmd 리다이렉션으로 넘긴다.

```powershell
1..30 | ForEach-Object { 'y' } | Set-Content -Encoding ascii "$env:TEMP\yes.txt"
cmd /c "sdkmanager.bat --licenses < %TEMP%\yes.txt"
cmd /c "sdkmanager.bat platform-tools platforms;android-34 build-tools;34.0.0 < %TEMP%\yes.txt"
```

## 5. Gradle wrapper

저장소에 이미 `gradlew`, `gradlew.bat`, `gradle/wrapper/`가 들어 있으므로 **새 PC에서는 할 일이 없다.**
처음 한 번만 만들 때 사용한 방법은 다음과 같다(Gradle 배포판을 임시로 받아 wrapper만 생성).

```powershell
Invoke-WebRequest "https://services.gradle.org/distributions/gradle-8.7-bin.zip" -OutFile "$env:TEMP\gradle.zip"
Expand-Archive "$env:TEMP\gradle.zip" -DestinationPath "$env:TEMP"
& "$env:TEMP\gradle-8.7\bin\gradle.bat" wrapper --gradle-version 8.7 --distribution-type bin
```

## 6. 확인

```powershell
java -version            # 17.x
adb version
sdkmanager --list_installed
.\gradlew.bat --version  # Gradle 8.7, JVM 17
```

## 7. 빌드와 설치

```powershell
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

폰은 설정 → 휴대전화 정보 → 빌드 번호 7번 탭 → 개발자 옵션 → USB 디버깅을 켠 뒤 USB로 연결한다.
