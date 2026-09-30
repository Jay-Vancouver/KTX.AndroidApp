# TMS 작업 요청: 개인정보처리방침에 KTX Driver 앱 반영

- 요청일: 2026-09-30
- 요청처: KTX Driver Android 앱 저장소 (`C:\ktxandroidapp`, GitHub `Jay-Vancouver/KTX.AndroidApp`)
- 대상 파일: TMS `app/templates/public/privacy.html` (공개 주소 `https://withktx.com/privacy`)
- 우선순위: **높음** — Google Play 등록 신청 전에 배포돼야 한다.

## 1. 왜 고쳐야 하나

KTX Driver 앱을 Google Play에 등록한다. Play 심사는 **개인정보처리방침이 앱의 실제 동작과 맞는지**,
특히 **백그라운드 위치 수집**을 방침에 명시했는지 확인한다(맞지 않으면 등록 거절 또는 앱 삭제 사유).

지금 방침은 위치를 이렇게 설명한다.
- 2절: "Location coordinates are collected **solely through the Traccar Client** application ... only while the
  **tracking switch** is active. Drivers have full physical control to toggle location tracking off."
- 7절: "Drivers may manage location collection at any time using the **toggle in the Traccar app**"

KTX Driver 앱(Android)은 다르게 동작한다.
- 픽업 완료 화면이 앱에 추적 시작을 지시하면 **자동으로** 위치를 보내기 시작하고, 마지막 배송 뒤 **자동으로** 멈춘다(드라이버가 켜는 스위치 없음).
- **화면이 꺼져 있거나 앱이 닫혀 있어도(백그라운드)** 보낸다. 보내는 동안 상시 알림 "KTX: Sending location"이 뜬다.
- iPhone 드라이버는 계속 웹 + Traccar Client를 쓴다 → 방침은 **두 방식을 모두** 설명해야 한다.

## 2. 앱이 실제로 수집·전송하는 것 (방침 작성 근거)

| 항목 | 내용 |
|---|---|
| 위치 | 위도, 경도, 속도, 방향, 고도, 정확도, 측정 시각 — `POST https://www.withktx.com/gps` (OsmAnd 형식), 식별자 = 드라이버 전화번호 |
| 배터리 | 전송 시점의 배터리 잔량(%) |
| 주기 | 기본 60초. 새 위치가 없어도 5분마다 한 번(서버가 `startTracking` 옵션으로 바꿀 수 있음, 10초~10분 / 1분~1시간) |
| 시작·중지 | 픽업 완료 페이지의 `KtxAndroidApp.startTracking()`로 시작, 실린 로드가 없을 때 `stopTracking()`으로 중지 |
| 백그라운드 | 위치 권한 "항상 허용" + Foreground Service. 화면 꺼짐·앱 닫힘 상태에서도 전송, 상시 알림 표시, 재부팅 뒤 자동 재개 |
| 오프라인 | 전송 실패 시 폰 안에 쌓아 두었다가(최대 약 1만 건) 연결되면 순서대로 보내고, 서버가 받으면 폰에서 지운다 |
| 카메라 | PICK UP 스캔 화면(웹 페이지)이 카메라로 QR을 읽는다. 사진은 드라이버가 업로드할 때만 전송 |
| 앱 안 고지 | 위치 권한을 묻기 **전에** "위치 정보 수집 안내" 창(백그라운드 수집·용도 설명, 거부 가능) |
| 드라이버가 멈추는 방법 | 폰 설정 → 애플리케이션 → KTX Driver → 권한 → 위치 "허용 안 함"(또는 앱 삭제). 설정 → 앱 → 강제 중지도 다음 실행 전까지 전송을 멈춘다 |

## 3. 바꿀 문구 (영문 그대로 반영)

### 3.1 1절 표 — "Drivers" 행의 "Information Collected" 칸
기존 `... and mobile device GPS coordinates (only when tracking is enabled).` 를 아래로 바꾼다.
```
... photos of bills of lading/signed documents; and, while a load is being carried, the mobile device's
location (GPS coordinates, speed, heading, altitude and accuracy) and battery level, collected by the
KTX Driver app for Android or the Traccar Client app (see Section 2).
```

### 3.2 2절 전체 교체 — "2. Driver Location Tracking (GPS)"
```html
<h2>2. Driver Location Tracking (GPS)</h2>
<p>Driver location is collected in one of two ways, depending on the driver's phone.</p>
<ul>
  <li><b>KTX Driver app (Android):</b> When a driver records a pickup, the KTX Driver app starts sending the
      phone's location to KTX automatically, and it stops automatically after the driver's last delivery of the
      day. <b>The app collects location in the background &mdash; including while the screen is off or the app is
      closed or not in use</b> &mdash; so that loads can be tracked for the whole trip. While location is being
      sent, a persistent notification ("KTX: Sending location") is shown on the phone. Before asking for location
      permission, the app explains this background collection and the driver may decline.</li>
  <li><b>What the app sends:</b> latitude and longitude, speed, heading, altitude, location accuracy, the time of
      the reading, the phone's battery level, and the driver's registered phone number as the identifier. A
      position is sent about once a minute, and at least every five minutes while stopped. If the phone has no
      connection, positions are kept on the phone until they can be sent and are then removed from the phone.</li>
  <li><b>Traccar Client app (iPhone and other devices):</b> Drivers who do not use the KTX Driver app send their
      location with the Traccar Client application, only while its tracking switch is on. Traccar Client is
      developed by Traccar Ltd. and is governed by its own licensing and privacy policies; KTX only receives the
      coordinates sent to KTX-controlled servers.</li>
  <li><b>Purpose &amp; Proportionality:</b> Location is used only to track the loads a driver is carrying (from
      pickup to final delivery): to show dispatch and the customer where their freight is, to plan dispatch, and
      to keep an operational record. It is not used for advertising, is not sold, and is not collected when the
      driver has no loads.</li>
  <li><b>How drivers can stop it:</b> In the KTX Driver app, collection stops by itself after the last delivery.
      A driver can also stop it at any time in the phone's settings (Settings &rarr; Apps &rarr; KTX Driver &rarr;
      Permissions &rarr; Location &rarr; Don't allow) or by uninstalling the app; in Traccar Client, by turning off
      its tracking switch. Stopping location sharing may require manual status updates to dispatch.</li>
  <li><b>Access Limitations:</b> KTX dispatchers can view truck locations and their associated loads. A portal
      customer can see truck location only while the truck is actively in transit with that specific customer's
      freight, and only associated with their own order reference numbers. Location data is never shared with
      unrelated third parties.</li>
  <li><b>Retention:</b> GPS position data is retained for {{ retention_days }} days for route audit and dispute
      purposes, after which it is permanently deleted or anonymized.</li>
</ul>
```

### 3.3 1절 표 — "Drivers" 행 아래에 카메라 설명 한 줄(선택, 권장)
"Purpose" 칸 끝에 추가:
```
The phone camera is used to scan pallet-tag QR codes at pickup (scanned on the phone, not stored) and to take
photos the driver chooses to upload.
```

### 3.4 5절 표 — 행 추가
```html
<tr><td>Location Positions Waiting on the Driver's Phone</td>
    <td>Kept on the phone only until they are sent to KTX (for example after a period without signal), then
        deleted from the phone.</td></tr>
```

### 3.5 7절 "Exercising Your Rights" 첫 항목 교체
```
Drivers using the KTX Driver app can stop location sharing at any time by turning off the app's location
permission in the phone's settings or by uninstalling the app; drivers using Traccar Client can use its
tracking switch. Drivers may stop SMS delivery by texting STOP.
```

### 3.6 시행일
`effective_long` / `effective` 값을 반영 배포일로 갱신한다(9절: 변경 시 시행일을 바꾼다고 명시돼 있음).

## 4. 함께 확인할 것

1. **공개 접근**: `https://withktx.com/privacy`가 로그인 없이, **리다이렉트 끝에 200**으로, HTML로 열려야 한다(PDF·다운로드 불가).
   사무실 Wi-Fi에서는 사이트가 안 열리므로(헤어핀 NAT) **모바일 데이터**로 확인할 것.
2. **Play 데이터 보안 양식과 일치**: 앱 저장소 `docs/store/LISTING.md` 5절의 표(위치·전화번호·사진·이름/서명, 고객과 공유 범위)와 방침 내용이 서로 맞아야 한다.
3. **계정 삭제 요청 경로**(Play 요구 사항 점검): 드라이버 계정은 회사가 등록하고 앱 안에서 가입하는 구조가 아니지만,
   Play Console "데이터 삭제" 항목에 적을 수 있도록 **삭제 요청 방법**(10절 Privacy Officer 연락처로 요청)을 7절에 한 줄 명시하는 것을 권장.
   예: `To request deletion of your driver account and associated data, contact our Privacy Officer (Section 10).`
4. 10절의 `contact_email` / `contact_address_lines`가 실제 값으로 채워져 있는지(Play 스토어 연락처와 같은 주소 권장).

## 5. 완료 기준

- [ ] 3.1 ~ 3.6 반영, 시행일 갱신
- [ ] 운영(`withktx.com`)에 배포, 모바일 데이터로 `https://withktx.com/privacy` 열어 "KTX Driver"와 "background" 문구 확인
- [ ] 4절 3번(삭제 요청 문구) 반영 여부 결정
- [ ] 앱 저장소 쪽에 완료 알림 → 대행업체에 URL 전달
