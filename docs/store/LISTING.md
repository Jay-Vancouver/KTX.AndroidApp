# Google Play 등록 정보 — KTX Driver

등록 대행업체에 전달하는 자료다. 스토어 문구는 영어(기본)와 한국어 두 가지.
빈칸(`____`)은 회사가 채운다.

## 1. 기본 정보

| 항목 | 값 |
|---|---|
| 앱 이름 | KTX Driver |
| 패키지명 | `com.ktxtransport.driver` |
| 버전 | 1.0.0 (versionCode 1) |
| 최소 Android | 8.0 (API 26), targetSdk 36 |
| 업로드 파일 | `app-play-release.aab` (Play용 빌드. 홈페이지용 APK는 보내지 않는다) |
| 업로드 키 인증서 SHA-256 | `95:D1:72:23:A3:1F:A7:7B:03:05:DB:9D:BD:77:84:50:FD:D2:43:B0:2C:87:AD:9B:C1:7F:CF:14:DA:B2:51:67` |
| 카테고리 | 비즈니스 (Business) |
| 가격 | 무료, 광고 없음, 인앱 구매 없음 |
| 대상 연령 | 18세 이상 (업무용) |
| 개인정보처리방침 | `https://withktx.com/privacy` (위치 수집 내용 보강 필요 — 5절) |
| 지원 이메일 / 웹사이트 | `____` / `https://withktx.com` |

## 2. 스토어 문구 — English (default)

**App name** (30자 이내)
```
KTX Driver
```

**Short description** (80자 이내)
```
Scan pickups, sign deliveries and share truck location — for KTX drivers.
```

**Full description** (4000자 이내)
```
KTX Driver is the work app for drivers hauling freight for KTX Transport.

PICK UP
• Scan pallet tags with the phone camera, or type the order number when a load has no tag.
• Your pickups go straight to KTX dispatch — no paperwork to hand in.

DELIVERY
• Mark loads delivered and collect the receiver's name and signature on the phone.
• Proof of delivery is saved with the order right away.

AUTOMATIC LOCATION SHARING
• After pickup, the app shares your truck's location with KTX Transport so dispatch and the
  customer know where their freight is.
• It keeps working with the screen off and stops by itself after your last delivery.
• Location is used only to track the loads you are carrying.

DAILY LOG AND INSPECTION
• Fill in your driving log and pre-trip vehicle inspection, with photos of any defects.

SIMPLE SIGN-IN
• Enter your phone number and tap the link we text you. You stay signed in for months.

KTX Driver is for drivers registered with KTX Transport. A registered phone number is needed to sign in.
```

## 3. 스토어 문구 — 한국어

**앱 이름**
```
KTX Driver
```

**간단한 설명** (80자 이내)
```
KTX 드라이버용 픽업 스캔, 배송 확인, 트럭 위치 자동 전송 앱
```

**자세한 설명**
```
KTX Driver는 KTX Transport의 화물을 운송하는 드라이버를 위한 업무용 앱입니다.

픽업
• 휴대폰 카메라로 팔레트 태그를 스캔합니다. 태그가 없는 화물은 오더 번호를 입력합니다.
• 픽업 내용은 바로 KTX 배차 담당자에게 전달됩니다.

배송
• 배송 완료를 표시하고, 받는 분의 이름과 서명을 휴대폰으로 받습니다.
• 배송 증빙이 오더에 바로 저장됩니다.

위치 자동 공유
• 픽업을 마치면 트럭 위치를 KTX Transport와 공유해, 배차 담당자와 고객이 화물 위치를 알 수 있습니다.
• 화면이 꺼져도 동작하며, 마지막 배송을 마치면 자동으로 멈춥니다.
• 위치는 싣고 있는 화물을 추적하는 데만 사용합니다.

운행 일지와 차량 점검
• 운행 일지와 운행 전 차량 점검을 작성하고, 결함이 있으면 사진을 첨부합니다.

간편한 로그인
• 전화번호를 입력하고 문자로 받은 링크를 누르면 됩니다. 한 번 로그인하면 몇 달 동안 유지됩니다.

KTX Driver는 KTX Transport에 등록된 드라이버용 앱입니다. 로그인하려면 등록된 전화번호가 필요합니다.
```

## 4. 이미지 (이 폴더)

| 요청 | 파일 |
|---|---|
| 앱 아이콘 512×512 | `KTX_Driver_icon_512.png` |
| 그래픽 이미지 1024×500 | `KTX_Driver_feature_graphic_en.png` (한국어 페이지용 `_ko`) |
| 휴대전화 스크린샷 1080×1920 | `KTX_Driver_screenshot_1_en.png` ~ `_3_en.png` (한국어 `_ko`) |

스크린샷은 실제 앱 화면(Galaxy S25+, 2026-09-30)이다. PICK UP 화면의 카메라 영상은 사무실이 찍혀 흐리게 처리했다.
더 필요하면(최대 8장) 배송·서명·운행 일지·점검 화면을 폰에서 새로 캡처해 추가한다.

## 5. Play Console 심사 항목 (업체가 입력)

**앱 액세스 (심사자 로그인)** — 로그인이 SMS 링크 방식이라 심사자가 들어갈 방법이 필요하다: `____` (TMS에서 심사용 전화번호·고정 로그인 링크 마련)

**데이터 보안 (Data safety)**
| 데이터 | 수집 | 공유 | 목적 | 필수 |
|---|---|---|---|---|
| 정확한 위치(백그라운드 포함) | 예 | 예 — 운송 중인 화물의 고객(해당 오더 위치만) | 앱 기능(화물 추적) | 예 |
| 전화번호 | 예 | 아니요 | 계정 관리(로그인) | 예 |
| 사진 | 예 | 예 — 해당 오더의 고객(배송 증빙) | 앱 기능 | 아니요 |
| 이름·서명(받는 분) | 예 | 예 — 해당 오더의 고객 | 앱 기능 | 아니요 |
- 전송 중 암호화: 예 (https). 삭제 요청: 가능(회사 연락처 `____`).

**백그라운드 위치 권한 신고**
- 기능 설명: "After a driver picks up freight, the app sends the truck's location to KTX Transport every minute, including while the screen is off or the app is in the background, so dispatch and the customer can follow the load. Sending stops automatically after the driver's last delivery."
- 앱 안 고지: 위치 권한을 묻기 전에 "위치 정보 수집 안내" 창을 띄운다(거부 가능).
- 시연 영상: `____` (고지 창 → 항상 허용 → 화면 끄고 전송되는 모습)

**Foreground Service (location) 신고**
- 사용 이유: 운송 중 트럭 위치를 계속 보내기 위해. 상시 알림 "KTX: Sending location"이 표시된다.
- 시연 영상: `____` (픽업 → 알림 표시 → 배송 후 알림 사라짐)

## 6. 개인정보처리방침 보강 필요 (TMS)

현재 `https://withktx.com/privacy`는 위치를 **"Traccar Client 앱으로만, 드라이버가 스위치로 켜고 끈다"**고 설명한다.
Play 심사는 방침과 앱 동작이 맞는지 본다. 등록 전에 아래를 추가해야 한다.
- 수집 앱: **KTX Driver 앱**(Android) — 픽업 후 **백그라운드에서 자동으로** 위치 수집, 마지막 배송 후 자동 중지
- 수집 항목: 위치(위도·경도·속도·방향·고도·정확도), 배터리 잔량, 전화번호, 사진, 서명
- 드라이버가 멈추는 방법(앱 설정에서 위치 권한 해제 등), 보관 기간, 삭제 요청 방법
