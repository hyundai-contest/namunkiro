# Pleos 공모전 개발 컨텍스트 (에이전트용)

> 이 파일은 Android Studio 터미널 에이전트가 이 프로젝트를 이어서 개발할 때 읽는 기본 컨텍스트다.
> 프로젝트 루트에 `CLAUDE.md` / `AGENTS.md` / `GEMINI.md` 등 사용하는 에이전트가 읽는 이름으로 두고 사용한다.
> 작성 기준일: 2026-11-07. 출처: Pleos 개발자 가이드(https://document.pleos.ai, 약 90페이지 확인), 2026-10-28 공모전 온라인 설명회.

---

## 0. 에이전트 작업 규칙

- 응답과 커밋 메시지, 주석 설명은 **한국어**로 한다. 코드 식별자는 영어.
- 이 문서의 SDK 메서드 이름 중 **"(slug 기준 추정)"** 표시가 있는 것은 문서 URL 이름에서 유추한 것이다. 사용 전에 IDE 자동완성이나 API Reference(https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/)로 정확한 시그니처를 확인하고, 다르면 이 문서를 고친다.
- 차량 SDK 호출은 전부 **비동기 콜백**이다. 결과를 동기 값처럼 쓰지 말 것.
- 확인되지 않은 플랫폼 동작(아래 9절)을 전제로 핵심 기능을 짜지 말고, 폴백을 함께 만든다.
- 새로 확인된 사실(에뮬레이터에서 실측한 결과 등)은 이 문서 9절에 날짜와 함께 기록한다.
- 운전자 감시·평가 기능, 주행 중 경고 남발, 주행 중 복잡한 UI는 만들지 않는다(10절 원칙).

---

## 1. 공모전 개요

- 대회: 현대자동차그룹 차량용 앱 개발 공모전. 공식: https://appdevchallenge.auto-hmg.io/ , 문의 appdevchallenge@hyundai-partners.com
- 주제: 운전자·탑승자를 위한 Pleos Connect 차량용 앱. 분야 제한 없음.
- 일정
    - **11/15(일)** 1차 접수 마감: 기획서(접수 양식) + 참가자 전원 서명 동의서 **필수**. 개발 산출물(시연 영상, GitHub 저장소 + APK)은 **선택이지만 가산점**.
    - 11/23 서류 결과 → **12/3~4 2차 PT(현대차 강남대로사옥 1층 UX 스튜디오 서울)**: PT 전까지 앱 개발 완료, **현장에서 에뮬레이터로 직접 시연**(실차 시연 없음).
    - 12/18 수상팀 발표 → 12/19~1월 중순 전시 준비(새 기능 개발 기간 아님) → 2027-02 현대차그룹 행사 전시·시상.
- 심사 기준: **차량 특화성**(모바일과 다른 가치, 주행·정차 맥락과 차량 데이터·하드웨어를 UX에 연결) / **창의성**(문제·니즈 정의, 해결 방식) / **기술 활용성**(Pleos SDK·API를 핵심 기능에 적절히) / **사업성**(타깃, 사용자 확보, 지속 이용, 확대).
- 42dot 조언: "SDK를 많이 쓰는 게 목적이 아니다. 차량 환경에서만 줄 수 있는 핵심 가치 한 가지를 **실제 동작하는 기능**으로 보여 줘라."
- 생성형 AI 사용 가능. 단 PT 기술 Q&A에서 **전체 구조와 로직을 팀이 직접 설명**할 수 있어야 한다 → 코드는 설명 가능한 단순한 구조로.
- 공모전 개발 단계에서는 SDK 권한 승인 절차 없이 **Manifest에 권한 선언만 하면** 에뮬레이터에서 5개 SDK를 쓸 수 있다(설명회 발언). 외부 API·퍼블릭 네트워크 사용 가능.
    - 단, 문서(프로젝트 생성 페이지)에는 Vehicle 제어·TTS·STT·FusedLocation이 "준비 중"으로 표기돼 있어 충돌한다. 막히면 주최측 메일로 확인.

### GitHub 제출 요건 (가산점)
- APK는 이메일 첨부가 아니라 **저장소의 `apk/` 폴더 또는 GitHub Releases**에 올린다.
- README 필수 항목: 앱 이름, 한 줄 소개, **실행 방법**, **테스트 환경**, 필요한 설정, APK 위치 또는 릴리즈 링크.
- 프라이빗 저장소면 공지된 심사용 계정을 Collaborator로 초대하고 마감 전 접근 확인.

---

## 2. 팀·장비

- 개발자 2명(프론트·백엔드 출신). 레포는 **모노레포** 하나, 빌드는 분리.
  ```
  repo/
  ├── android/   # Android Studio 프로젝트 (이 앱)
  ├── server/    # (선택) 서버가 필요해질 때만. MVP는 서버 없음
  ├── api/       # (선택) OpenAPI 스펙
  ├── apk/       # 제출용 APK
  └── README.md
  ```
  android와 server를 하나의 Gradle 멀티모듈로 묶지 않는다(AGP·JDK 충돌 방지).
- 개발 PC: 삼성 갤럭시북3 Pro(i5-1340P, RAM 16GB, **Intel Iris Xe 내장 그래픽**). Pleos 공식 최소 사양(RTX 3060 Laptop 6GB급 외장 GPU)에 **미달** → 에뮬레이터가 느리거나 그래픽 오류가 날 수 있다. 안 뜨면 AVD Graphics를 Software로. 12/3 현장 시연용 장비는 별도 확보 + 시연 영상 백업 녹화.

---

## 3. 개발 환경

| 항목 | 값 |
|-|-|
| IDE | Android Studio 최신 |
| 언어 | Kotlin (Jetpack Compose) |
| minSdk | **28 이상**(차량 보안 요건: 서명 키 로테이션) |
| compileSdk / targetSdk | 35 권장(Vehicle 샘플 앱 기준), 34 플랫폼도 설치 |
| Gradle | 8.0 이상 |
| 에뮬레이터 | **Pleos Connect Emulator v2.1.0**, Android 14(API 34) |

### 프로젝트 생성 방식
- New Project에서 Car 분류에는 No Activity / Media Service / Empty Car App Library App만 있다. 이 앱은 일반 화면 앱이므로 **Phone and Tablet > Empty Activity(Compose)**로 만들고 Manifest에 차량용 선언을 추가했다.
- Car App Library(템플릿 앱), Media Service 템플릿은 쓰지 않는다.

### 에뮬레이터 설치
1. SDK Manager > SDK Update Sites > + :
    - Name: Pleos Connect System Image
    - Windows/Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
    - Mac: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
2. SDK Platforms > Show Package Details > API 34 > Pleos Connect system image 설치 → Android Studio 재시작.
3. Device Manager > Create device > **Automotive > Pleos Connect v2.1.0** > Advanced: **Boot = Cold**(quick boot면 VHAL 사용 불가), **RAM 4GB** > API 34 Pleos Connect v2.1.0 APIs.
4. 에뮬레이터 기본 언어 한국어. STT 테스트 시 Extended Controls > Microphone에서 호스트 오디오 입력 허용. 블루투스 동글 미지원.

### Gradle 설정
`settings.gradle.kts`
```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }
    }
}
```
`app/build.gradle.kts` (필요한 것만)
```kotlin
dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    // implementation("ai.pleos.playground:TextToSpeech:3.3.1")  // 아티팩트 ID는 문서 Importing 페이지로 확인
    // implementation("ai.pleos.playground:SpeechToText:3.3.1")
    // implementation("ai.pleos.playground:FusedLocation:1.1.3")
}
```
- Getting Started 예제는 Vehicle `2.3.1`, Importing 문서·릴리즈 노트는 `2.3.2`. **2.3.2 사용.**
- AAOS 표준 API(CarPropertyManager, CarUxRestrictionsManager)를 쓰려면 `android { useLibrary("android.car") }`가 필요할 수 있다(에뮬레이터에서 확인).

### Manifest 기본형
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-feature android:name="android.hardware.type.automotive" android:required="true" />

    <!-- 실제로 쓰는 API의 권한만 선언 (전체 목록 복사 금지) -->
    <uses-permission android:name="pleos.car.permission.CAR_DOORS" />

    <application ...>
        <activity android:name=".MainActivity" android:exported="true">
            <!-- 주행 중 사용이 필요한 화면만. 선언 시 주행 중 최소 UI 원칙 준수 -->
            <meta-data android:name="distractionOptimized" android:value="true" />
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

---

## 4. Pleos Connect 플랫폼 핵심

- AAOS(Android Automotive OS) 기반 인포테인먼트. 국내 탑재: 그랜저(2026-05), 아반떼 CN8, 투싼 NX5, 싼타페 MX5 PE(내연·HEV). **국내 Pleos EV 없음**(유럽 아이오닉 3).
- 화면: 운전자 쪽 고정 **Driving view** + 앱 영역(최대 2개 window). 앱은 portrait(1 window) 또는 landscape(2 window)로 표시되며 크기·방향이 바뀔 수 있다 → 반응형 레이아웃, 두 방향 모두 설계. 화면에서 사라진 앱은 백그라운드에 남을 수 있음.
- 입력: **공용 센터 화면 터치뿐**(전용 후석 화면 API 없음). 음성은 Gleo AI(순정 어시스턴트) 경유 가능(8절).
- **주행 규제**: DO(distractionOptimized) 미선언 앱은 주행 중 사용 불가(Parking에서만). DO 선언 앱도 `CarUxRestrictionsManager.registerListener()` → `onUxRestrictionsChanged()`로 받은 ActiveRestrictions에 맞춰 기능을 줄여야 한다. 한국("그 외 지역") 주행 중 금지: 미디어 로그인·설정, 차량 설정(디지털키·도어·시트 등 `NO_SETUP`), 동영상(`NO_VIDEO`), 대용량 텍스트(`LIMIT_CONTENT`). 키보드·검색은 허용.
- 하드웨어 직접 접근 금지, 플랫폼 API로만. 카메라 API 없음, CAN raw 없음, 알림(NotificationListener) 경로 없음.
- 네트워크 불안정 대비: 오프라인에서도 핵심 기능 동작, 로컬 캐시.
- 기어·차속·시동은 Pleos SDK에 없음 → **AAOS 표준 `CarPropertyManager`**로 읽는다(`GEAR_SELECTION`, `PERF_VEHICLE_SPEED`). 클러스터와 같은 속도를 원하면 PERF_VEHICLE_SPEED.
- `Build.DEVICE`(CONNECT_S/L/W)는 실차에서만 유효, 에뮬레이터에선 플랫폼 구분 불가. 지역 코드는 `product.sw.target_region`(KOR 등).

---

## 5. Pleos Connect SDK (2026-10-19 릴리즈 기준)

| SDK | 버전 | 용도 |
|-|-|-|
| Vehicle | 2.3.2 | 차량 상태 조회·구독·제어 |
| NaviHelper | 3.0.5 | 순정 내비 경로 요청·내비 이벤트 |
| TextToSpeech | 3.3.1 | 음성 합성 |
| SpeechToText | 3.3.1 | 음성 인식(텍스트만, 명령 실행은 안 함) |
| FusedLocation | 1.1.3 | 차량 위치·지오펜스 |

LLM SDK는 2026-10-19 **제공 종료**(Gleo 연동은 Gleo Interface로).

### 5.1 Vehicle SDK 사용 패턴
```kotlin
val vehicle = Vehicle(context)
vehicle.initialize()                    // 차량 서비스 연결 (IPC)
val door = vehicle.getDoor()            // 도메인 객체는 getXxx()로만 획득

// Capability: 지원 여부·허용 범위
door.checkDoorPositionCapabilities(
    onComplete = { config, areaMap ->   // config.accessType: NONE/READ/WRITE/READ_WRITE
        val bounds = areaMap[DoorArea(DoorValue.ROW_1_LEFT)]   // bounds?.minValue / maxValue
    },
    onFailed = { e -> }
)
// Get
door.getCurrentDoorPosition(
    area = DoorArea(DoorValue.ROW_1_LEFT),
    onComplete = { pos: Int? -> },
    onFailed = { e: Exception -> }
)
// Set (실패 시에만 콜백)
door.setDoorPosition(area = DoorArea(DoorValue.ROW_1_LEFT), value = 1, onFailed = { e -> })
// Callback 구독/해제 (같은 리스너 객체·같은 area로 해제)
val listener = object : DoorPositionListener {
    override fun onPositionUpdated(pos: Int?) {}
    override fun onFailed(e: Exception) {}
}
door.registerDoorPosition(DoorArea(DoorValue.ROW_1_LEFT), listener)    // area 필수 (2.3.2 소스 확인)
door.unregisterDoorPosition(DoorArea(DoorValue.ROW_1_LEFT), listener)

vehicle.release()                       // onDestroy 등. 콜백 완료 전 release 금지
```
- **set 성공 콜백 ≠ 실제 값 변경**. 반드시 get 재조회나 listener 값으로 확인.
- Capability 먼저 확인 → 미지원 영역/기능은 UI 비활성. 차종마다 지원이 다르므로 차종명 분기 금지.
- 오류: `onFailed`의 Exception이 `VehicleException`이면 `errorCode` 확인.
    - `ERROR_NOT_SUPPORTED_PROPERTY` / `ERROR_NOT_SUPPORTED_AREA` → UI 비활성·영역 변경
    - `ERROR_SECURITY_PERMISSION` → Manifest 권한 확인
    - `ERROR_SERVICE_CONNECTION_FAILURE` / `ERROR_INTERNAL_SERVICE` → initialize·에뮬레이터 상태 확인, 재시도
    - `ERROR_DRIVING_RESTRICTED` → 주행 제한 안내
    - `ERROR_EMPTY_RESULT`, `ERROR_NOT_SUPPORTED_VALUE_TYPE`, `ERROR_UNDEFINED`
- v2.3.2 Breaking: CruiseControl 상태 상수 변경(OFF/READY/ACTIVE 제거 → OTHER, ENABLED, ACTIVATED, USER_OVERRIDE, SUSPENDED, FORCED_DEACTIVATION_WARNING), `checkHvacFanSpeedCapabilities` 반환 타입 변경, FanDirectionType 확장, Area 클래스 equals/hashCode 변경. `VehicleSpeedListener` 삭제(속도는 CarPropertyManager).

### 5.2 Vehicle 도메인과 권한
권한 형식: 조회 `pleos.car.permission.CAR_*`, 제어 `pleos.car.permission.CONTROL_CAR_*`. 세부 API마다 `@RequiresPermission`이 다를 수 있으니 확인.

| 도메인 | 조회 권한 | 주요 API (slug 기준 추정, 확인 필요) |
|-|-|-|
| Door | CAR_DOORS | getCurrentDoorPosition, registerDoorPosition, getDoorLockState, registerDoorLockState, setDoorLock |
| Seat | CAR_SEATS | getCurrentSeatOccupancy, registerSeatOccupancy, getCurrentSeatBeltBuckledState, registerSeatBeltBuckledState, getCurrentSeatForeAftPosition, getCurrentSeatBackrestAnglePosition, getCurrentSeatHeightPosition, selectSeatMemory, move* |
| Light | CAR_LIGHTS | 전조·미등·안개·실내·독서등 state/switch get·register·set, setAmbientLightBrightness/BrightnessColor/Switch |
| HVAC | CAR_CLIMATE | getHvacTemperature, getHvacTemperatureCurrent(실내 실측인지 미확인), getHvacFanSpeed, getHvacPowerState, register*, set* |
| Brake / DrivingMode / Odometer | CAR_DRIVE | getCurrentBrakePedalStatus, getCurrentDrivingModeState, getCruiseControlState, getCurrentDistanceDriven, getTripCurrentDrivingDistance/Time 등 |
| EvBattery / Fuel | CAR_ENERGY | getEvBatteryStateOfCharge, getFuelPercent, 연비·전비 |
| Safety / Tire | CAR_SAFETY | getTelltalesInfo, registerTelltales, hasSeatBeltNotBuckledWarning, 오일·브레이크액·워셔액 상태 |
| CarInfo / Display | CAR_INFO | getVINInfo, 온도 단위 |
| Window / Wiper | CAR_WINDOWS | getCurrentWindowPosition, setWindowPosition, 와이퍼 상태 |
| TurnSignal | (확인 필요) | getCurrentTurnSignalState, registerTurnSignalState |
| SteeringWheel | (READ_CAR_STEERING 추정) | getCurrentSteeringWheelAngle, registerSteeringWheelAngle, 핸들 열선 |
| SideMirror / Toll | CAR_MIRRORS / CAR_TOLL | 미러 열선 / 하이패스 볼륨 |

샘플 앱 Manifest에는 이 외에 `CAR_MILEAGE`, `CAR_POWERTRAIN`, `CAR_SPEED`, `CAR_TIRES`, `READ_CAR_DISPLAY_UNITS`, `READ_CAR_STEERING`, `READ_ADAS_STATES`, `READ_WINDSHIELD_WIPERS`, `CONTROL_WINDSHIELD_WIPERS`도 있다. 제품 앱은 **쓰는 API의 최소 권한만** 선언.

### 5.3 NaviHelper 3.0.5
```kotlin
val naviHelper = NaviHelper(applicationContext)
naviHelper.initialize(onConnected = { /* 리스너 등록, API 호출 */ }, onFailed = { t -> })
naviHelper.addListener(listener)        // NaviHelperEventListener: onError(NaviErrorCode), onDestinationArrived 등
// ... requestRoute(좌표), requestRouteWithWaypoints, requestTrip, requestNavigationScreen, 즐겨찾기·최근 목적지·현재 위치·TBT 조회
naviHelper.removeListener(listener); naviHelper.release()
```
- 권한: `NAVI_ROUTE`(get*Info, requestTrip), `NAVI_ROUTE_SEARCH`(requestRoute 등 경로 변경), `NAVI_CUSTOM_MAP`(requestNavigationScreen).
- **좌표 필수, POI 이름 검색 API 없음.** 3.0.5부터 데이터 모델 생성자 좌표 순서가 **(latitude, longitude)**.
- "실제 전달되는 이벤트는 내비 앱 구현과 상태에 따라 다를 수 있음" → 이벤트 수신은 에뮬레이터에서 실측.
- 오류: CAUSE_LOW_BATTERY, CAUSE_SEARCH_ROUTE_OVER_DISTANCE, CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE, CAUSE_SEARCH_ROUTE.

### 5.4 TTS / STT 3.3.1
- 모드: `HYBRID`(기본, 서버 우선·실패 시 온디바이스), `SERVER`, `ON_DEVICE`. **ON_DEVICE는 앱 등록·네트워크 불필요** → MVP는 ON_DEVICE 우선.
- 순서(TTS): `TextToSpeech(context, Mode.ON_DEVICE)` → `initialize(OnInitializeListener)` → `addEventListener` → `setLocale` → (서버 모드면 `registerApp(clientID, clientSecret, OnServerConnectionListener)` 후 onConnected 대기) → `speak` / `stop` → `removeEventListener` → `release`.
- STT: `SpeechToText(context, mode)` → initialize → `addListener(ResultListener)` → setLocale → `request()` / `stop()`. `sendAudio(ByteArray)` + `completeAudioSend()`는 ON_DEVICE 전용(16kHz 16bit mono PCM, 100ms=3,200byte).
- 권한: `pleos.car.permission.TTS_SERVICE`, `pleos.car.permission.STT_SERVICE`. Client ID/Secret은 Pleos Playground > Workspace > My Project에서 발급.
- 오류 콜백 인자는 3.3.1부터 `ErrorType` enum.

### 5.5 FusedLocation 1.1.3
- `FusedLocation(context)` → **`isSupported()` 확인** → `initialize()` → `getLastKnownLocation()` 또는 `registerFusedLocationCallback(listener)` → `unregisterFusedLocationCallback` → `release()`.
- `FusedLocationListener.onChanged(LocationData?)` — **null 가능**, `onError(Exception)` 필수 구현.
- 지오펜스: `addProximityAlert` / `removeProximityAlert`(ENTER/EXIT).
- 권한 `pleos.car.permission.FUSED_LOCATION`은 **dangerous** → 런타임 권한 요청 구현. 위치 접근 차단 시 `showLocationAccessBlockedPopup()`.
- 위치를 기기 안에서만 쓰면 위치기반서비스사업 신고 대상에서 빠지는 쪽이 유리 → 서버 전송 최소화.

---

## 6. 테스트·디버깅

### Virtual Console (차량 상태 시뮬레이터)
```
adb forward tcp:8088 tcp:8088
# PC 브라우저: http://127.0.0.1:8088/
```
- **재현 가능**: 기어 P/R/N/D, 브레이크 페달 %, 주행거리, 조향각(-540~540°), 핸들 열선, 크루즈 상태·목표 속도, 도어 위치·잠금, 트렁크, **좌석별 착석(Seat Occupancy)**, 실내등·독서등·앰비언트(밝기·Cyan/Blue/Purple/White)·외부등 모드(Off/Auto/Tail/Low)·상향등·비상등·방향지시등, 창문 위치·잠금, 와이퍼 스위치, 글러브박스, ETCS 시나리오, 경고등(telltales: 주차 브레이크·벨트·ABS·TPMS 등 Off/On/Blink), EV(주행가능거리·SOC·에너지·용량·충전 한도·스위치·포트), 공조 전반(전원·AC·풍량·방향·온도·내외기·제상·시트 열선/통풍·공기청정), 차량 알림.
- **재현 불가**: **좌석별 벨트 체결**, **차속**, 실내·외기 온도, 연료 %, 시동 OFF/ACC, GPS 이동(에뮬레이터 Extended Controls의 위치/경로 기능으로 대체 시도).
- 접속 안 될 때: 에뮬레이터 실행·v2.1.0 이미지 확인 → `adb devices` → forward 재실행 → 8088 포트 점유 확인.

### Vehicle SDK Sample App (동작 기준선)
- https://github.com/Pleos-Playground/pleos-sample-vehicle-app (NaviHelper·FusedLocation 샘플도 같은 조직에 있음)
- `./gradlew installDebug` → Launcher "Vehicle SDK Sample". 내 앱이 안 되면 샘플에서 같은 API가 되는지 먼저 확인.
- 샘플 앱 기준: Vehicle SDK 2.3.2, Service 2.3.22, JDK 17, Gradle wrapper 8.10.2, AGP 8.8.2, Kotlin 2.0.0, compile/target 35.

### 주요 adb 명령
```
adb install -d -r app-debug.apk
adb shell monkey -p <패키지> -c android.intent.category.LAUNCHER 1
adb shell am force-stop <패키지>
adb shell pm clear <패키지>
# 주행 규제 ON/OFF (DO·CarUxRestrictions 테스트)
adb shell cmd hmg_car_service --service drivingstate driving_regulation on
adb shell cmd hmg_car_service --service drivingstate driving_regulation off
# 시연 영상 녹화 (Windows, 최대 3분)
adb shell screenrecord /sdcard/demo.mp4
adb pull /sdcard/demo.mp4
```

### 폼 팩터 테스트(선택)
Go 1.22+, host-display-bridge 실행(`go run . --avd "<AVD 이름>"`), 에뮬레이터를 **별도 창**으로 실행, FormFactorConsole 앱에서 12.9"/14.6" Portrait/Landscape 가상 디스플레이로 레이아웃 확인. 종료 시 `adb forward --remove tcp:8095`.

---

## 7. 개발 중인 앱: 대리 바통 (메인 출품작)

> **2026-11-15 메모**: 이 절은 초기 아이디어(대리 바통) 기록이다. 최종 출품작은 **남은키로**이며, 현재 구현 명세는 `docs/FINAL_SPEC.md`, 시연 대본은 `docs/DEV_PLAN.md` 5절을 따른다.

### 한 줄
저녁에 차를 세우는 맑은 순간에 "오늘 대리로 귀가"를 미리 맡겨 두면, 밤에 대리기사가 운전석에 앉는 순간 차가 **이 차의 지금 상태와 차주 요청을 한 장의 카드로** 건네고, 주차(P) 때 층을 1탭으로 받아 두었다가, 다음 날 아침 **밤사이 바뀐 것만 보여 주고 1탭으로 되돌리는** 대리운전 인계 앱.

### 문제(원인 한 줄)
대리운전에서는 주인이 아닌 사람이 운전석에 앉는데, 그 순간 주인은 취했거나 잠들어 있어서 설명·확인·기억을 하나도 할 수 없다.
- 근거: "어제 대리기사가 사이드랑 시트 만져놨네요 / 맡기기 전 게스트모드로 바꿔놓습니다"(MLBPARK 2025-12), 대리기사 지하 주차 거부 갈등(머니투데이 2025-01), 낯선 조작으로 인한 대리 사고(테슬라 기피 80% 이상), 카카오T '차량 정보 등록'·티맵 대리 기사 체크리스트(플랫폼이 이미 돈 들여 만든 우회책). 시장 약 2.78조 원(2024), 하루 약 38만 건 추정.

### 사용 흐름
1. **저녁 주차(P, 차주가 맑을 때)**: 처음 3회는 차주가 위젯 [오늘 대리 귀가] 1탭. 이후 학습: 과거 맡김 장소 반경 150m, 17~22시, P + 운전석 도어 열림 → 카드 "여기서 대리로 귀가하실 거면 지금 준비해 둘까요? [준비][아니요]". [준비] → 집 목적지·도착 주차 요청 칩(예: B2 이하 OK, 기계식 X)·차 메모 칩(예: 트렁크 골프백)이 지난 값으로 채워짐, 확인 1탭. 같은 장소 [아니요] 2회면 그 장소는 다시 묻지 않음. 앱에서 "술"이라는 단어를 쓰지 않는다.
2. **밤, 기사 탑승(P, 출발 전)**: 맡김 준비 상태 + 조수석/뒷좌석 착석 + 운전석 도어 열림 → 운전석 착석 ON → **기사용 조작 카드** + 20초 음성. 차주 입력 0. 내비에 집 경로 미리 설정(NaviHelper). 브레이크 + D 진입 시 카드 사라짐, 주행 중엔 앱이 아무것도 하지 않음.
    - 미리 준비하지 않은 날 폴백: 운전석이 비고 다른 좌석 착석 유지 중 30분 안에 운전석 재착석 + 시트 위치 ±3cm 변화 → 화면 구석에 "처음 타셨나요? [이 차 조작 보기]".
3. **도착(D→P 진입 순간, 시동 끄기 전)**: 큰 버튼 "몇 층에 세우셨나요? B1·B2·B3·B4·B5·지상·기계식 / 건너뛰기"(기사 1탭, 선택). 음성 "도착했어요. B3로 기록했어요."
4. **다음 날 아침(운전석 착석, 정차)**: **아침 인계장** + [모두 내 설정으로] 1탭.

### Output (결과물)
1. **기사용 조작 카드**(큰 글씨 1장, 3칸)
    - 출발법: VIN → 차종별 조작 템플릿(앱 내장, 현대 매뉴얼 기준). 예: "브레이크 밟고 → 오른쪽 칼럼 다이얼 앞으로 = D / P = 다이얼 끝 버튼"
    - 지금 상태(실시간): 주차 브레이크(경고등), 라이트 모드, 주행모드, 크루즈 설정
    - 차주 요청: 도착지, 주차 요청 칩, 메모 칩, "시트는 편하게 바꾸셔도 됩니다. 아침에 차가 되돌립니다"
2. **도착 층 기록**: 시각·층·마지막 좌표(지하 진입 직전 GPS)
3. **아침 인계장**: "어젯밤 22:47 ○○ 앞에서 맡김 → 23:31 귀가 · B3" / 바뀐 것(맡김 직전 기준값 대비): 시트, 주행모드, 라이트 모드, 설정 온도 → [모두 내 설정으로]. 화면에 "기사님의 속도·경로·운전은 기록하지 않습니다" 명시.
4. **내 대리 귀가 기록**(차 안에만 저장): 회차별 맡김 장소·시각·귀가 시각·층·요청 칩.

### 마이데이터 사다리
- 1단계: 대리 인계(카드·층·아침 인계장)
- 쌓이는 데이터(전부 차주 본인 것, 기기 내 저장): 맡김 장소·요일·시각 / 귀가 시각·집 주차 층 / 맡기기 직전 내 차 기준값(시트·라이트·공조·주행모드) / 요청·메모 칩
- 확장(사용자 가치)
    - E-1 **맑을 때 맡겨 두기**: 같은 장소 3회 → 저녁 주차 시 차가 먼저 준비 제안. 음주운전 이유 1·2위('대리·대중교통이 없어서' 20.7%, '술이 깼다고 판단' 20.4%, 도로교통공단 2025)는 둘 다 취한 뒤 밤의 결정 → 결정을 맑은 저녁으로 옮기는 사전 약속 장치. 잠금·경고·보고 없음.
    - E-2 **집 주차 요청 자동화**: 집 도착 층 분포 학습 → 다음 기사 카드에 "보통 B3" 자동 기입.
    - E-3 **내 기준값 원복**: 맡김 직전 값 = 내 값, 아침 1탭 원복.
- 확장(생태계): 카카오T 예약 대리 연동(제휴 선택, 없어도 본체 동작), "대리기사도 헤매지 않는 차", 음주운전 예방 공익 서사, VIN별 조작 카드의 공식 콘텐츠화.
- **하지 않는 것**: 대리기사 평가·운전 점수, 기사 속도·경로·페달·신원 수집, 인계 기록의 보험·분쟁 증거화, 숙취 판정(안전 착각 유발).

### 사용하는 차량 신호 (VC 재현 여부)
| 목적 | API | VC |
|-|-|-|
| 착석 감지(운전석·조수석·뒷좌석) | Seat registerSeatOccupancy | 가능 |
| 운전석 도어 열림 | Door registerDoorPosition | 가능 |
| 기어 P/D | CarPropertyManager GEAR_SELECTION | 가능 |
| 출발 판정 | Brake registerBrakePedalStatus | 가능 |
| 주차 브레이크 상태 | Safety getTelltalesInfo / registerTelltales | 가능 |
| 라이트 모드 | Light 전조/미등 switch get/set | 가능 |
| 공조 설정 온도·풍량 | HVAC get/set | 가능 |
| 시트 위치(기준값·변화) | Seat getCurrentSeatForeAftPosition 등 | 미확인 |
| 시트 원복 | Seat selectSeatMemory / move* (CONTROL) | 미확인, 폴백: "IMS 1번 버튼" 안내 |
| 주행모드·크루즈 | DrivingMode get | 크루즈 가능, 주행모드 확인 필요 |
| 차종 템플릿 | CarInfo getVINInfo | - |
| 집 경로 설정 | NaviHelper 즐겨찾기 조회 + requestRoute | 실측 필요 |
| 학습 장소 | FusedLocation addProximityAlert | GPS 이동 불가 → 시드 데이터 |
| 음성 | TTS ON_DEVICE speak | - |

### MVP 범위 (12/3 PT까지)
- 필수: 위젯 [오늘 대리 귀가] → 기사 탑승 감지 → 기사 카드(지금 상태 실시간) → D 진입 시 해제 → P 진입 시 층 입력 → 아침 인계장 diff + 원복(가능한 항목만) → 내 대리 귀가 기록 목록.
- 다음: E-1 학습 제안(시드 데이터 3회로 시연), E-2 층 자동 기입.
- 선택: Gleo 음성("대리 준비해 줘"), 카카오T 연동 목업.
- 서버 없음. 저장은 Room 또는 DataStore(기기 내).
- 모든 차량 의존 기능은 **capability 확인 → 미지원 시 UI 숨김** 구조.

### 60초 시연 시나리오(에뮬레이터 + Virtual Console)
1. (0~10s) '이 장소 맡김 3회' 시드 → VC 기어 P·운전석 도어 열림 → 준비 제안 카드 → [준비] → 칩 확인
2. (12~26s) VC 조수석 착석 ON → 운전석 도어 열림·착석 ON → 기사 카드 + 음성. VC 주차 브레이크 경고등 ON/OFF에 카드가 실시간 반영. 외부등 AUTO→OFF, 공조 22→26℃(기사가 바꾼 상황)
3. (26~32s) 브레이크 + D → 카드 사라짐
4. (32~42s) D→P → 층 버튼 B3 → "도착했어요, B3로 기록했어요"
5. (46~60s) 다음 날: 운전석 착석 → 아침 인계장 → [모두 내 설정으로] → VC에서 외부등 AUTO·22℃ 복귀 확인 → 귀가 기록 3회 목록

---

## 8. Gleo Interface (선택 기능)
- `app/src/main/assets/schema.json`에 최대 3개 function 등록(이름 대문자+밑줄, 설명, string/enum 파라미터, `component`: "activity" 또는 화면 없는 "service"), `invokeWord`로 호출 이름 지정. Gleo AI(LLM)가 발화에서 파라미터를 추출해 Intent로 호출.
- Manifest에 function 이름과 같은 action의 intent-filter 정의.
- 앱은 Callback(functionName, status success/asking/fail, message(영어 문장), mode voice/beep/silent, data)으로 응답. **7초 타임아웃.**
- 스키마는 **검토·승인 후 활성화** → 공모전 기간 사용 가능 여부와 에뮬레이터 내 Gleo 존재는 미확인. 메인 입력으로 쓰지 말 것.
- 호출 방향은 **Gleo → 앱 단방향**(function calling). 앱이 Gleo에게 프롬프트를 보내고 답을 받는 API는 없다(2026-11-09 문서 확인).
    - 수신: Intent action = function 이름, 파라미터는 extra `"ACTION_PARAM"`(JSON 문자열). `component`가 activity면 `startActivity`, service면 `sendBroadcast` → BroadcastReceiver로 받음.
    - 응답: action `ai.pleos.playground.intent.action.APP_ACTION_RESPONSE` 브로드캐스트, extra `packageName`(내 패키지) + `response`(Callback JSON 문자열).
    - 파라미터 타입은 string/number/boolean/array, enum은 문자열만. 설명은 영어 1~2문장.
- 범용 LLM이 필요하면 외부 LLM API를 HTTPS로 직접 호출한다(아래 9절 주최측 답변).

---

## 9. 미확인 사항 (실측하면 날짜와 결과를 여기 기록)

### 확인된 사실
- 2026-11-07 개발환경 세팅·빌드 확인 (`./gradlew :app:assembleDebug` 성공)
    - 실제 프로젝트 툴체인: AGP 9.5.1(Kotlin 내장, `kotlin-android` 플러그인 불필요), Gradle 9.6.0, Kotlin 2.2.10, JDK 17 타깃. 설치된 플랫폼이 android-37뿐이라 **compileSdk 37 / targetSdk 35**로 둠.
    - 아티팩트 ID 확정: `ai.pleos.playground:{Vehicle:2.3.2, NaviHelper:3.0.5, TextToSpeech:3.3.1, SpeechToText:3.3.1, FusedLocation:1.1.3}` (버전 카탈로그 `libs.pleos.*`).
    - 패키지: `ai.pleos.playground.vehicle.Vehicle`, `ai.pleos.playground.vehicle.api.model.area.DoorArea`, `ai.pleos.playground.navi.helper.NaviHelper`.
    - `useLibrary("android.car")`로 `android.car.Car` import 컴파일 확인.
    - 공식 샘플과 같이 `kotlin-reflect` 명시 추가. 릴리즈 R8용 keep 규칙 `ai.pleos.**` 추가(`app/src/main/keepRules/rules.keep`).
- 2026-11-07 Pleos 에뮬레이터(AVD `Pleos_Connect_v2.1.0`, 2560x1440, RAM 4GB, cold boot) 설치·부팅 확인. 이 PC(Iris Xe, GPU auto)에서 부팅됨.
    - 앱 실행 확인: `checkDoorPositionCapabilities` → `getCurrentDoorPosition(ROW_1_LEFT)` 성공, 값 0(닫힘). **Virtual Console에서 운전석 도어를 열고 닫으면 `registerDoorPosition` 리스너로 화면이 실시간 갱신됨(사용자 확인).** 앱은 Driving view 오른쪽 앱 영역(853,90~2535,1300)에 표시됨.
    - **SDK 소스(sources.jar)로 확인한 실제 시그니처**: `registerDoorPosition(area: DoorArea, listener)` / `unregisterDoorPosition(area, listener)` — 5.1 예시와 달리 **area 인자 필요**. 리스너는 `onPositionUpdated(pos: Int?)` + `onFailed(e)`.
    - `Vehicle.initialize()`는 콜백 없이 비동기 연결. 직후 호출한 get/check는 SDK 내부에서 연결을 기다렸다 실행됨. 콜백은 백그라운드 스레드 → StateFlow로 전달.
    - **`VehicleException`은 배포 AAR에서 난독화**(클래스명 `a`)돼 import·`errorCode` 접근 불가. 오류는 `Exception.message`로만 구분.
    - **메모리 부족으로 에뮬레이터 멈춤 발생**: 남은 RAM 1GB 이하일 때 qemu가 디스크로 밀려나 응답 없음(adb shell도 멈춤). 복구 = qemu 강제 종료 → `gradlew --stop` → cold boot(약 45초). 에뮬레이터를 켠 상태에서 Android Studio + Gradle 데몬 + Chrome만으로도 남은 RAM이 0.5GB 수준까지 떨어짐 → 브라우저 탭과 다른 앱을 최소화할 것.
    - **디스크 부족으로 에뮬레이터 시작 실패**: `FATAL | Your device does not have enough disk space` (C: 남은 1.5GB). Pleos 시스템 이미지 17GB, AVD 약 4.5GB(스냅샷 제외), 에뮬레이터 실행 중 추가로 약 4GB를 더 씀. cold boot만 쓰므로 스냅샷(4GB) 삭제 후 **`emulator -avd Pleos_Connect_v2.1.0 -no-snapshot`**(로드·저장 모두 안 함)으로 실행. 부팅 약 100초. 에뮬레이터 시작 전 C: 여유 6GB 이상 필요.
    - **기어는 Vehicle SDK에도 있음**: `vehicle.getGear()` → `getCurrentGearSelectionState`, `registerGearSelection(GearSelectionListener)`, 권한 `CAR_DRIVE`, 값 `VehicleGear.GEAR_PARK/DRIVE/...`. CarPropertyManager 대신 이것을 우선 사용 검토.
- 2026-11-09 NaviHelper 3.0.5 소스(sources.jar + navi-data 3.0.4 javap) 확인
    - `NaviHelperEventListener`: `onDestinationArrived(DestinationArrivedInfo: name/address/distance/duration)`, `onDrivingInfo(DrivingInfo)` → `destination: DrivingState`의 `distance`·`duration`(남은 거리·시간), `onNavigationStatus`, `onTBTInfo`, `onRouteStateInfo`, `onCurrentRoadSpeedLimit`(1초 주기) 등. 남은 거리로 "목적지 근처" 판정 가능(이벤트 실수신은 미확인).
    - `requestNavigationScreen(NavigationScreenRequest)`은 **순정 내비 화면**을 여는 것(Foreground, SearchPlace, OverviewRoute, FavoriteMain, AddHomeFavorite 등)이고 내 앱을 띄우는 기능은 아님.
    - 화면 모델 문서: 앱이 스스로 앞으로 나오는 방법·팝업·오버레이는 언급 없음. Driving view는 앱이 가릴 수 없음.
- 2026-11-09 주최측 답변(LLM): 외부 API로 LLM을 쓰는 것에 현재 제한 없음. **비식별 짧은 텍스트를 HTTPS로 외부 LLM에 보내는 방식은 심사에서 허용**. 차량 정보 수집 동의·활용 책임은 개발사에 있음. Pleos LLM SDK는 미지원, Gleo Interface 연동은 가능.
- 2026-11-15 V1 차량 연동 실측(AVD `Pleos_Connect_v2.1.0`, `-no-snapshot`, `PleosVehicleGateway` + `OdometerIngestor` + JSON 저장소, 임시 확인 화면)
    - **Odometer 수신 확인**: `checkCurrentDistanceDrivenCapabilities` → READ 계열(SUPPORTED) → `getCurrentDistanceDriven` → `registerDistanceDriven` 정상. 콜드 부팅 직후 VC 기본값 **0.0km**. 앱 onStart부터 첫 값까지 약 1~3초(Vehicle 연결 대기). 값이 그대로여도 리스너가 약 12초마다 같은 값을 다시 보낸다 → `lastOdometer`가 바뀔 때만 수집기에 넣을 것(distinctUntilChanged).
    - **VC 주행거리 변경 반영**: 21,406 → 21,418km가 **0.1~1초 안에** 리스너로 들어옴. 데모 규칙(여유분 100km)에서 +12km는 ACCEPTED, +580km(21,468→22,048)는 HELD_SPIKE. 0km 기준에서 21,394km로 올리면 급증 보류 → 시연 전 "VC 값과 동기화"(DemoController)로 기준을 맞춰야 한다.
    - **VC는 HTTP API로도 조작 가능(브라우저 없이)**: `adb forward tcp:8088 tcp:8088` 후
        - 브라우저 세션 하트비트가 있어야 쓰기가 받아들여짐(없으면 `{"error":"emulator_inactive"}`): `curl -X POST "http://127.0.0.1:8088/browser/session?client=<아무 id>"`를 1초마다 반복.
        - 주행거리: `curl -X PUT "http://127.0.0.1:8088/vehicle/odometer?traveled_distance_km=21418"` (VHAL `PERF_ODOMETER`, 0~1,000,000). 줄이면 `{"error":"traveled_distance_must_not_decrease"}` → **역행 시연 불가 재확인**.
        - 기어: `curl -X PUT "http://127.0.0.1:8088/vehicle/gear?gear=Park|Reverse|Neutral|Drive"` (`VENDOR_GEAR_SELECTION`).
        - 항목 목록 `GET /vehicle/catalog`, 현재 상태 `GET /vehicle/state`. `/vehicle/controls?speed_kph=`도 있으나 P에서 40을 넣어도 UXR 변화 없음(차속 재현 여부 미확인).
    - **기어 P/D 반영**: `getCurrentGearSelectionState` 초기값 PARK, `registerGearSelection`으로 D/R/N/P 변경이 0.1~0.2초 안에 반영. Gear API는 SDK에서 deprecated(WARNING, GEAR_SELECTION 속성 권장)지만 동작함.
    - **UX 제한(CarUxRestrictionsManager)**: `Car.createCar(context)` + `CAR_UX_RESTRICTION_SERVICE` 정상. **이 이미지에는 `hmg_car_service`가 없어** `adb shell cmd hmg_car_service --service drivingstate driving_regulation on/off`는 `Can't find service`로 실패. `cmd car_service`는 user 빌드라 막힘(`requires non-user build`), root 불가. 대신 **VC 기어만으로 UXR이 바뀐다**: D·R → `isRequiresDistractionOptimization=true`(activeRestrictions=80), P → false(0). N은 false로 보였으나 반영이 늦어 재확인 필요. 시연의 "기어 D + driving_regulation on"은 **기어 D만으로 재현됨**.
    - **앱 강제 종료 후 재실행 공백 회수**: `am force-stop --user 10` → VC 21,418→21,468km → 재실행 → 새 세션 첫 값이 `ACCEPTED_GAP`, 증가 50km, 당일 DailyDistance km 62(주행 12 + 공백 50)·gapKm 50으로 저장되고 재실행 후에도 복원됨.
    - **백그라운드에서는 콜백이 오지 않는다**: 앱이 화면 뒤로 가면(cached app freezer) 리스너가 멈추고, 앞으로 가져오면 밀린 값이 한꺼번에 들어옴. 백그라운드 수집에 의존하지 말 것(세션 시작 재조회 + 공백 회수 설계가 맞음).
    - **앱은 사용자 10으로 실행**: `pm clear`·`am start`·`am force-stop`에 `--user 10`을 붙여야 한다(안 붙이면 사용자 0에 적용돼 데이터가 남음).
    - 에뮬레이터 시계는 PST(로그 11-14 19:xx = KST 11-15 12:xx). 날짜 경계 테스트 시 주의(앱 계산은 AppClock/DemoClock 기준).
- 2026-11-15 N1 NaviHelper·주행 단위 실측(AVD `Pleos_Connect_v2.1.0`, 앱 `PleosNaviGateway`, NaviHelper 3.0.5 / 내비 서비스 `ai.pleos.playground.service.navi` 3.0.4, 순정 내비 `ai.umos.maps.android.navigation.app`)
    - **연결**: `initialize(onConnected, onFailed)` → 약 0.24초 만에 `onServiceConnected`, `getServiceVersion = 3.0.4`, `registerCallbackV3`. 권한 `pleos.car.permission.NAVI_ROUTE`(Normal) 선언만으로 `getDestinationInfo`·`getRouteStateInfo` 동작. `<queries>`는 AAR 매니페스트가 넣어 줌.
    - **목적지 없음 형식**: `DestinationInfo(latitude=0.0, longitude=0.0, name="", address="", remainDistance=0, remainTime=0, poiId="")`, `RouteStateInfo(naviMode=SAFE_GUIDING, isRouting=false)`. → 빈 이름+0m를 '목적지 없음'으로 처리.
    - **목적지 설정 방법(에뮬레이터)**: `geo:` NAVIGATE 인텐트는 무시됨. 순정 내비 검색창에 `adb shell input text "seoul"`(영문) 입력 → 결과의 경로 화살표 탭 → 경로 미리보기 후 **자동으로 안내 시작**. 지도 타일은 비어 있지만 검색·경로 계산은 동작(현재 위치 '수정구 시흥동').
    - **안내 시작 시 오는 이벤트(순서)**: `onRouteStarted(RouteStartInfo(duration=3840, distance=23000, tollFare=700, name=더현대서울))` → (deprecated) `onRouteEnded`가 **곧바로 함께 옴**(종료 의미 아님, 무시해야 함) → `onNavigationStatus(ROUTE_DRIVE)` → `onDrivingInfo(destination=DrivingState(name=더현대서울, duration=3840, distance=23000))` → `onRouteStateInfo(naviMode=ROUTE_GUIDING, isRouting=false)` → `getDestinationInfo` 응답 `DestinationInfo(latitude=37.526…, longitude=126.927…, name=더현대서울, address=영등포구 여의도동, remainDistance=23000, remainTime=3840, poiId=…)`. 단위는 문서대로 m·초.
    - **주의: 안내 중에도 `RouteStateInfo.isRouting=false`**. 안내 여부는 `naviMode`(ROUTE_GUIDING / SAFE_GUIDING)나 `onNavigationStatus`(ROUTE_DRIVE / FREE_DRIVE)로 판단할 것.
    - **안내 종료(‘안내 종료’ 버튼)**: `onDrivingInfo(distance=0, duration=0)` 1회 → `onRouteCancelled` → `onNavigationStatus(FREE_DRIVE)` → 빈 `DestinationInfo` → `RouteStateInfo(naviMode=SAFE_GUIDING)`. `onRoutingCompleted`·`onDestinationArrived`는 이번에 관측 안 됨(실제 이동 없음).
    - **백그라운드 동안의 이벤트**: 내비가 앱 영역을 차지해 우리 앱이 뒤로 가면 프로세스가 얼어 콜백이 오지 않는다. **앞으로 가져오면 밀린 이벤트가 순서대로 한꺼번에 들어온다**(22:55:21 시작 → 앱 복귀 22:55:37에 수신). 따라서 "목적지 설정 → 앱 열기"로 제안이 뜨는 흐름은 성립. 반쪽 창(내비와 나란히)이면 실시간 수신 가능할 것으로 보이나 이번에는 미측정.
    - 차량 이동 시뮬레이션(위치 이동)으로 `onDrivingInfo` 남은 거리 감소·`onDestinationArrived` 수신은 **미확인**(VC로 GPS 이동 불가).
    - **기어 기반 주행 단위(TripSegmenter)**: 실제 SDK 모드에서 VC 기어 P→D, 계기판 20,012.1→20,013.6km(1.2초마다 +0.06km, 250km/h 규칙 안), 기어 P → `trip_segments.json`에 `{start 23:01:23, end 23:02:02, km 1.5, source OBSERVED}` 저장 확인. VC로 +12km를 한 번에 올리면 실사용 규칙(데모 여유분 없음)에서는 급증 보류라 그 주행은 0km로 버려진다 → 실 SDK 시연은 작은 단계로 올리거나 데모 규칙을 쓸 것.
    - VC 계기판 API 응답에 VHAL 속성 `TRIP_DRIVING_DISTANCE`(0x21610451)·`TRIP_DRIVING_TIME`도 함께 표시됨(앱에서는 미사용).
- 2026-11-15 R-FINAL 재실측(수정 후 APK): **반쪽 창(앱 오른쪽 핸들을 왼쪽으로 끌어 내비와 나란히)에서는 앱이 보이는 상태라 NaviHelper 이벤트가 실시간으로 온다**(onRouteStarted→제안 6ms). 내비 검색은 화면 한글 자모 키를 좌표로 눌러 '강릉' 입력 가능. 경로 선택 후 '안내 시작' 5초 카운트다운 뒤 자동 시작. 부팅 직후·`am force-stop` 직후 첫 `am start`는 순정 내비가 앞에 남는 경우가 있어 한 번 더 실행해야 함. 자세한 결과: `docs/rehearsal/final/REPORT.md`.

### 미확인 → 체크리스트 (2026-11-15 개발 완료 시점 갱신)

범례: [x] 확인 · [~] 일부 확인 · [ ] 미확인 · [-] 남은키로에서 사용하지 않아 확인 대상 아님

- [x] 이 PC(Iris Xe)에서 Pleos 에뮬레이터 부팅·성능 → 11-07 부팅 확인. `-no-snapshot` cold boot 약 45~100초, RAM 부족 시 멈춤 주의(위 기록)
- [x] Vehicle SDK Odometer capability/get/register, VC 주행거리 변경 반영(0.1~1초) → 11-15 V1
- [x] Vehicle SDK 기어 get/register, VC 기어 P/D/R/N 반영(0.1~0.2초) → 11-15 V1
- [x] CarUxRestrictions: VC 기어 D·R만으로 주행 최적화 요구, P에서 해제. `hmg_car_service` 없음 → 11-15 V1, R-FINAL 잠금 1.2초·복원 1.5초
- [x] 앱 강제 종료 → VC 증가 → 재실행 공백 회수 → 11-15 V1, R-FINAL(50.0 km 배너)
- [x] 기어 기반 주행 구간(P→D→P) 저장 → 11-15 N1
- [x] NaviHelper 이벤트 수신·목적지 조회(`onRouteStarted`, `getDestinationInfo`, 안내 종료 순서), 반쪽 창 실시간 수신 → 11-15 N1, R-FINAL
- [~] NaviHelper 즐겨찾기 조회·`requestRoute` → 미확인(남은키로 미사용)
- [ ] `onDestinationArrived`·남은 거리 감소 → VC로 위치 이동 불가해 미확인
- [ ] 시동 직후 앱 자동 기동 여부, 시동 OFF/ACC 이후 앱 동작 → VC 재현 불가. 앱은 시동 이벤트에 의존하지 않음(세션 재조회 + 공백 회수)
- [ ] 실차에서 Odometer 갱신 주기·정밀도 → 실차 없음
- [-] `CarPropertyManager` GEAR_SELECTION / PERF_VEHICLE_SPEED → Vehicle SDK Gear로 대체, 차속은 VC 재현 불가
- [-] Seat 위치·Seat Occupancy, DrivingMode, 외부등 Light API → 이전 아이디어용 항목
- [-] TTS ON_DEVICE 한국어·오디오 존, FusedLocation → 남은키로 미사용
- [~] 문서 충돌: Vehicle 제어·TTS·STT·FusedLocation "준비 중" 표기 vs 설명회 "권한 선언만으로 사용 가능" → 남은키로가 쓰는 Vehicle 읽기(CAR_DRIVE)·NaviHelper(NAVI_ROUTE)는 권한 선언만으로 동작 확인. 제어(set) API는 사용하지 않음

---

## 10. 설계 원칙 (심사·플랫폼 정책)
- 주행 중: 화면 조작 요구 0, 큰 터치 영역, 텍스트 최소, 동영상·애니메이션 최소. 주행 중 출력은 꼭 필요한 짧은 음성만.
- 정차(P) 중: 카드·목록·입력 자유.
- 패널티 금지: 점수·감점·감시·지목·잠금·출발 지연·경고 남발 없음.
- 개인정보: 기기 내 저장 우선, 기록하지 않는 항목을 화면에 명시, 1탭 삭제 제공. 디버그 로그에 개인정보 금지, 배포 빌드에서 로그 비활성.
- 보안: 배포용 서명 키 사용, HTTPS 인증서 검증 우회 금지, 외부 URI 검증.
- 포그라운드 복귀 시 상태 복원, 백그라운드에서 폴링·타이머 최소화(CPU·메모리 절약).
- 오디오를 재생하면 오디오 포커스 정책 준수(내비 안내 시 duck, 통화·긴급 중 요청 거절 처리).

---

## 11. 참고 링크
- 개발자 가이드 시작: https://document.pleos.ai/docs/pleos-only/getting-started
- SDK 개요·API Reference: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/
- 에뮬레이터 설치: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
- Virtual Console 사용: https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide
- 주행 규제 개발: https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide
- 앱 구조·화면 모델: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model
- ADB 명령: https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/adb-command-reference-guide
- Gleo Interface: https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/core-concepts
- 샘플: https://github.com/Pleos-Playground/pleos-sample-vehicle-app , pleos-sample-navi-app , pleos-sample-fusedlocation-app
- 공모전: https://appdevchallenge.auto-hmg.io/

---

## 12. 첫 작업 순서 (에이전트에게)
1. 현재 프로젝트 구조 확인(Phone and Tablet > Empty Activity, Compose로 생성됨). minSdk 28, compileSdk 35로 맞추기.
2. `settings.gradle.kts`에 Pleos Maven 저장소, `app/build.gradle.kts`에 Vehicle 2.3.2 추가 → Sync.
3. Manifest에 `android.hardware.type.automotive` uses-feature와 `CAR_DOORS` 권한 추가.
4. `VehicleRepository`(Vehicle 초기화·해제, 도메인 객체 제공) 작성 → MainActivity에서 운전석 도어 위치 get + register 결과를 화면에 표시.
5. Pleos 에뮬레이터에서 실행, Virtual Console로 도어 열고 닫아 화면 갱신 확인 → 9절에 결과 기록.
6. 이후 7절 MVP 순서대로: 착석·기어 감지 상태 머신 → 기사 카드 UI → 층 입력 → 기준값 저장·diff·원복 → 아침 인계장 → 귀가 기록.
