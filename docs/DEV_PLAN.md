# 남은키로 개발 계획 (v3 명세 기준, 시연 제약 반영)

작성: 2026-11-15. 기준 명세: 신청서 v3(`design/spec_v3_content.py`). 이전 PoC(`AndroidStudioProjects/hackathon`)에서 실측한 사실을 근거로 한다.

목표 일정
- 11/15 23:59: 1차 접수(기획서 필수, APK·영상은 가산점). 오늘은 골격과 엔진, 가능하면 동작 APK까지.
- 12/3~4: 2차 PT. **현장에서 Pleos 에뮬레이터로 직접 시연.** 실차 없음. 이 날까지 앱 완성.

원칙: 시연에서 **실제로 동작하는 것과 모의 데이터인 것을 화면에서 구분**한다(운영측 답변). 구현하지 않은 기능을 구현한 것처럼 보이게 하지 않는다.

---

## 1. 플랫폼 사실과 시연 제약

| 항목 | 확인 상태 | 앱 설계 영향 | 시연 대응 |
|---|---|---|---|
| 누적 주행거리 | SDK 소스 확인: `vehicle.getOdometer()` → `checkCurrentDistanceDrivenCapabilities`, `getCurrentDistanceDriven(onComplete: (Float?) -> Unit)` km 단위, `registerDistanceDriven(DistanceDrivenListener)` → `onDistanceDrivenUpdated(Float?)` km. 권한 `pleos.car.permission.CAR_DRIVE` | 핵심 입력. capability 확인 후 구독 | Virtual Console(VC)에서 주행거리 값을 올려 실시간 반영. **VC는 기존보다 큰 값만 받는다** → 역행 시연 불가 |
| 기어 | SDK 소스 확인: `vehicle.getGear()` → `getCurrentGearSelectionState`, `registerGearSelection(GearSelectionListener)`, `VehicleGear.GEAR_PARK/DRIVE…`, 권한 CAR_DRIVE | 상세 화면 게이트, 주행 단위(P→D→P) 구분 | VC 기어 P/D 전환 |
| 주행 중 화면 제한 | CarUxRestrictionsManager. 차속은 VC 재현 불가 | 제한 시 잠금 화면 | `adb shell cmd hmg_car_service --service drivingstate driving_regulation on/off` + VC 기어 D |
| 시동 ON/OFF | **VC 재현 불가**, IGNITION_STATE 실차 미확인, 시동 OFF 후 앱 동작 보장 없음 | 시동 이벤트에 의존하지 않는다. 앱이 화면에 올라올 때마다 재조회하고, 마지막 승인값과의 차이로 공백 구간 거리를 회수 | "시동 끔 → 다른 날 시동 켬"은 **앱 강제 종료 → VC로 거리 증가 → 앱 재실행**으로 재현. 자막: "앱 재시작으로 시동 재시작을 대신 표현" |
| 시간 경과 | 주 경계, 90일, 1년 4개월을 실제 시간으로 재현 불가 | 모든 계산은 `AppClock` 인터페이스로 현재 시각을 받는다 | 데모 모드의 `DemoClock`으로 날짜를 옮기고, 페르소나별 일별 주행 이력을 시드로 넣는다. 시드 구간은 [모의] 배지 |
| 등록 목적지 | NaviHelper 콜백 이벤트 실수신 미확인, 에뮬레이터 경로 계산 실패 보고 | 예정 이동은 **수동 입력이 기본**, NaviHelper 조회는 성공 시 후보로 덧붙임 | 시연은 수동 입력 또는 시드 목적지. NaviHelper는 Phase 2에서 실측 후 연결 |
| 위치 | GPS 이동 VC 불가 | MVP 제외 | — |
| 앱 표시 영역 | 2560x1440 중 오른쪽 앱 영역 약 1682x1210(실측). 반쪽 창 가능 | 반응형: 넓은 창 2열, 좁은 창 1열 | 기본 가로 창으로 시연 |
| 백그라운드 | 앱이 화면에서 사라져도 남을 수 있으나 보장 없음. 앱이 스스로 앞으로 나오는 방법 미확인 | 알림·자동 기동에 의존하지 않음. "정차 중 한 번 알림"은 **앱을 열었을 때 상단 배너**로 구현 | — |
| 오류 처리 | `VehicleException` 난독화로 errorCode 접근 불가 | 메시지 문자열로만 분기, 실패 시 0으로 대체하지 않음 | — |
| 개발 PC | RAM 16GB, 내장 그래픽. 에뮬레이터 + Gradle + 브라우저로 RAM 고갈 사례 | 에뮬레이터는 `-no-snapshot` cold boot, 동시에 Gradle 빌드 1개 | 현장 시연 장비 별도 확보, 영상 백업 |

결정 사항
- **distractionOptimized(DO) 선언**: 선언한다. 주행 중에는 앱이 직접 최소 잠금 화면만 보여 준다(숫자·조작 없음). 선언하지 않으면 플랫폼이 앱을 가려 버려 "주행 중에는 쉬어 갑니다" 경험을 보여 줄 수 없다. 심사 지침상 주행 중 화면은 운전 관련 최소 정보만 → 잠금 문구 한 줄과 아이콘만 둔다.
- **기어는 Vehicle SDK 우선**, CarPropertyManager는 Vehicle SDK 실패 시 대체.
- **시동 대신 "세션"**: 앱 포그라운드 진입 = 세션 시작. 세션 시작 때마다 Odometer 재조회 → 공백 거리 회수 → 이상값 검사.

---

## 2. 아키텍처

단일 앱 모듈, 패키지로 경계를 나눈다. 도메인은 Android 의존 없는 순수 Kotlin(단위 테스트 대상).

```
android/app/src/main/java/com/namunkiro/
├── core/
│   ├── time/        AppClock(interface), SystemClock, DemoClock
│   └── result/      VehicleReading(값+출처+시각), Source(OBSERVED/INPUT/ESTIMATED/UNCONFIRMED/SIMULATED)
├── vehicle/         VehicleGateway(interface), PleosVehicleGateway(Odometer·Gear), UxRestrictionGateway, FakeVehicleGateway
├── data/            Room DB: ContractEntity, OdometerSampleEntity, WeekLedgerEntity, PlannedTripEntity, ExitQuoteEntity / Repository
├── domain/
│   ├── odometer/    OdometerIngestor: raw→accepted, 이상값·역행 보류, 공백 회수
│   ├── budget/      WeeklyBudgetEngine: 기본 예산·이월·분할·누적 잔고·회복 판정
│   ├── forecast/    ForecastEngine: 평일/주말 기준선, 범위, 예정 이동·시나리오
│   ├── exit/        ExitCalculator: 같은 종료일 기준 반납/인수 보유/인수 매각/승계/해지, ExitCalendar
│   └── insurance/   InsuranceTierPlanner: 보험 기간·구간 상한 (budget 엔진 재사용)
├── ui/
│   ├── theme/       design-tokens.md를 옮긴 Color/Type/Dimens
│   ├── components/  BudgetRing, WeekBars, RangeBand, Badge, Card, BigNumber
│   └── screens/     Onboarding, Home, Week, Forecast, Exit, Contract, Insurance, DrivingLock, AnomalyHold, DeferredQuestion
├── app/             MainActivity, AppViewModel(화면 상태 조립), Navigation
└── demo/            DemoController, PersonaSeeds(김 대리/이 과장/박 과장), DemoPanel(데모 빌드에서만)
```

데이터 흐름
```
VehicleGateway(odometer km, gear, uxRestricted)
   → OdometerIngestor(검사·회수) → OdometerSample(승인값, 출처)
   → WeeklyBudgetEngine / ForecastEngine / ExitCalculator (AppClock 기준)
   → AppViewModel(StateFlow<UiState>) → Compose 화면
Contract(입력·점진 질문) ─────────────┘
```

빌드 변형
- `debug`: 데모 패널 포함, FakeVehicleGateway 전환 가능.
- `demo`(release 서명): PT·영상용. 데모 패널은 앱 제목 길게 누르기로 열림, 시드 데이터는 [모의] 배지.
- `release`: 데모 패널·시드 없음(제출 APK는 demo와 release 둘 다 첨부할지 결정).

---

## 3. 핵심 로직 명세

### 3.1 거리 수집 (OdometerIngestor)
- 저장: 원시값(raw km, 수신 시각, 경로: SDK/Fake)과 승인값을 분리.
- 승인 규칙: `NaN`·무한·음수·null 거부. 직전 승인값 대비
  - `Δ < -ε`(ε=0.1km): 역행 → 보류.
  - `Δ > 250 × Δt(h) + ε`(같은 세션, 단조 시간 기준): 급증 → 보류(E 화면).
  - 세션 사이 공백(앱이 꺼져 있던 동안)의 증가는 **승인하되 "공백 구간"으로 표시**하고 요일별 학습에서는 제외. 단 공백 시간 대비 250km/h 상한을 넘으면 보류.
- 보류 해제: [다시 조회]로 재수신해 규칙을 통과할 때만. 사용자 확인 한 번으로 급증을 승인하지 않는다.
- 일별 집계: 승인값의 날짜별 첫·마지막 차이. 공백 구간이 걸친 날은 "미분류" 거리로 따로 둔다.

### 3.2 초과 0원 목표 주간 페이스 (WeeklyBudgetEngine)

- 사용자 화면은 이월·분할·조정 예산 대신 **초과0원 목표 = (총약정거리 + 공제 − 실제사용) ÷ 남은 주수**를 중심으로 구성한다. 값은 `BudgetSnapshot.zeroFeeWeeklyKm`를 사용하며 공제0이면 `requiredWeeklyKm`과 같다.
- 김 대리:2028-03-16, 계약501일 경과,1,461일 중960일 남음,21,382km 사용. `(60,000−21,382)÷(960÷7)=281.589583km/주` → 표시 약282km. 이번 주186km 사용이므로 목표 대비95.589583km → 표시 약96km 남음. 반올림282를 매주 정확히 지키면0원이 보장된다는 표현은 쓰지 않는다.
- 현재 페이스 비용: 계약 시작 이후 평균은 `21,382÷501×7=298.75km/주`이고 예상만기62,353.50km, 초과2,353.50km×100원 =235,350원. 최근4주294km/주와 전체평균299km/주를 구분한다. 공제 미확인이면0가정 잠정값과 미확인을 함께 표시한다.
- 예정 이동: 강릉420km가 토요일 평소32km를 대체하므로 추가388km, `TripImpact.extraFee=38,800원`이다. 문구는 **“이 차로 가면 +3.9만 원 · 목표 279km”**. 같은 잔여기간에서 추가388km를 반영한 계획 미리보기는278.760417km/주이며, 실제 일요일3/19 주간684km 주행 완료 후 누적21,880km·잔여957일이면278.829676km/주다. 둘 다 표시 약279km다.
- 계획 입력만으로 실제 사용을 바꾸지 않는다. 기록 기반 `zeroFeeWeeklyKm`는 실주행 전281.59km이며 계획 반영279km는 별도 미리보기임을 구분한다. 다른 차 전환은420→0km이므로 계획 유지 대비42,000원 감소이고, 평소32km 대비 여행 증가38,800원과 혼용하지 않는다.
- 감축 탄력: `주10km × 남은 주수 × 단가`, 단 기존 초과정산액을 한도로 감소하고0원 아래로 내리지 않는다. 김 대리960일이면 약137,143원(13.7만 원), 이 과장90일 시점 잔여1,371일이면 약195,857원(19.6만 원) 감소다. 조건을 밝힌 가정 계산이며 확정 할인이나 신규 엔진 필드가 아니다.
- 회복 판정은 기존 엔진 유지: 필요 주간 페이스가 최근4주 평균의60% 미만 또는0이하이면 HARD_TO_RECOVER. 이 과장 필요273km/주·최근513km/주는 약53%다. 화면은 무리한 감축을 요구하지 않고 비용 변화량·출구 비교를 우선 안내한다.60%는 검증 전 제품 가설이다.
- 목표는 사용·시간에 따라 재계산한다. 덜 타는 사용자는 목표를 채우도록 유도하지 않고 미사용 약정·다음 계약 약정 낮추기를 안내한다. 공제 포함 잔여약정이 음수면 감축만으로0원이 불가능하고, 남은 기간이 없으면 목표값은 없다.
- **내부 계산만 유지, 화면 미노출:** 월요일00:00 경계, 기본 `B=round(K÷(계약일수÷7))`, 직전 주 기본−실제의 한 주 이월, 조정 예산, 분할·시작 주 이월0, 누적 잔고 계산은 코드·데이터·기존 단위 테스트에 남긴다.244/58/232km와 분할 선택 버튼은 새 화면·시연에서 사용하지 않는다. 자세한 기존 규칙은 FEATURE_GUIDE의 내부 계산 부록을 따른다.
- 기본287km를 유지하는 내부 지표 `onBudgetEndFee=74,200원`은0원 목표281.59km와 다르다. ‘목표대로 타도8만 원이 남는다’고 설명하지 않는다.

### 3.3 예측 (ForecastEngine)
- 기준선: 최근 4주(데이터 부족 시 가용 기간)의 평일 평균·주말 평균. 공백 구간 거리는 제외.
- 범위: 장기 추세(계약 시작 이후 평균)와 최근 추세(최근 4주)로 각각 만기 거리를 계산해 하한·상한. 데이터 14일 미만이면 "계획값 기반" 표시.
- 예정 이동: 해당 날짜의 기준선을 예정 거리로 **대체**(이중 계산 금지).
- 시나리오: 일일 거리 가감(예: 통근 +20km/일), 기간 지정 부재, 특정 이동 다른 차 처리.
- 분해: 초과분을 평일 반복 / 주말 / 미분류 비중으로 나눔.

### 3.4 출구 (ExitCalculator, ExitCalendar)
- 모든 선택지를 **같은 비교 종료일**(기본: 만기일)로 맞춘다.
- 만기 반납 = 남은 월료 + 예상 거리 정산 + (손상·감가: 미확인) − 보증금 환급.
- 인수 후 보유 = 남은 월료 + 인수가 + 취등록세(7%, 추정) − 보증금 충당. 순비용 = 위 − 예상 시세. **필요 현금과 순비용을 따로 표시.**
- 지금 승계 = 수수료 + 지원금(입력 견적) ± 보증금·선납 정산 + 종료일까지 대체 이동비(입력, 미입력 시 미확인).
- 지금 해지 = 해지 수수료(잔여 대여료 × 요율 구간, 계약서 입력) + 거리 정산(일할 약정) − 환급 + 대체 이동비.
- 과거 납입액은 매몰비용이라 비교에서 제외. 문구는 "입력 조건에서 예상 순비용이 낮음, 실행 가능 여부 확인".
- 금융리스·무제한 계약: 거리 정산 0, 반납 선택지 비활성(계약 조건 카드에서 유형 선택).
- 출구 달력: 해지 요율 구간 변경일, 자유반납 가능일, 만기 통지 기한, 견적 만료일. 앱을 연 날이 이벤트 14일 전 이내면 상단 배너 1회.

### 3.5 보험 특약 모드
- 같은 예산 엔진에 (보험 기간, 구간 상한 목록)을 넣는다. 예: 1만 km/1년 → 주 192km.
- 구간별 "지킬 수 있는 남은 주간 페이스"와 현재 페이스 기준 경계 예상일.

### 3.6 점진 온보딩
- 필수 3개: 만기일, 총 약정거리, 초과 단가. 시작 계기판은 Odometer 현재값을 [관측]으로 제안(신차 인도 직후 가정). 계약 시작일은 기본 "오늘", 수정 가능.
- 지연 질문(필요한 화면 첫 진입 때 1개씩): 공제 거리 → 만기 정산 화면 / 인수가·시세 → 출구 화면 / 해지 요율·자유반납일 → 출구 달력 / 계약 유형(장기렌트·운용·금융·무제한) → 계약 조건 카드.

---

## 4. 데이터 모델 (로컬 JSON 저장)

빌드 위험을 줄이려고 Room(KSP) 대신 **kotlinx.serialization JSON 파일 저장**(원자적 쓰기)으로 시작한다. 모델은 `domain/model/Models.kt`. 아래 구조는 저장 단위의 개념도다.
- `contract`(id, type, startDate, endDate, totalKm, deductKm?, unitPrice, buyoutPrice?, deposit?, monthlyFee?, terminationTiers JSON?, freeReturnDate?, noticeDeadline?, startOdometer, startOdometerSource, confirmedFields JSON)
- `odometer_sample`(id, rawKm, acceptedKm?, receivedAt, monoMs, sessionId, status: ACCEPTED/HELD/GAP, source)
- `week_ledger`(weekStart, budgetKm, actualKm, carryInKm, splitDeductKm, closed)
- `split_plan`(id, totalKm, weeks, startWeek)
- `planned_trip`(id, date, km, label, source: MANUAL/NAVI/SEED, otherCar)
- `exit_quote`(id, kind, amount, validUntil, source)
- `insurance_plan`(id, periodStart, periodEnd, tiers JSON)
- 모든 숫자 필드는 출처(Source)를 함께 저장해 배지를 일관되게 그린다.

---

## 5. 시연 설계 (PT 90초, 새 주간 목표 화면)

사전 준비: 김 대리 시드(2026-11-01~2030-10-31), DemoClock=2028-03-16 목요일, 경과501일, 사용21,382km·계기판21,394km로 맞춘다. 홈의 목표는 약282km/주, 이번 주186km 사용·약96km 남음이며 현재평균 만기 정산235,350원이다. 강릉 장면은 SDK 증가값을 받기 전에 촬영하여 수치를 고정한다. 이월·분할 버튼과244/58/232km 장면은 노출하지 않는다.

| 시간 | 조작 | 새 화면 반응·발표 문장 | 실제/모의 |
|---|---|---|---|
| 0–10s | 온보딩3개 입력, 시작 계기판 자동 제안 | “계약 거리와 실제 주행으로 앞으로의 목표와 비용을 계산합니다.” 첫날은 기록 부족 안내, 확정0원처럼 설명하지 않음 | Odometer 실제, 계약 입력 |
| 10–25s | 김 대리 홈 확인 | “초과0원 목표 주282km · 이번 주186km 탐 · 약96km 남음. 지금 평균 주299km가 이어지면 만기 약24만 원.” km→원 산식 확인 | 이력 모의, 계산 실제 |
| 25–40s | 이번 주에 강릉 왕복420km 계획 추가 | “이 차로 가면 +3.9만 원 · 목표279km.” 주간 예상684km, 추가388km×100원=38,800원.279km는 계획 반영 미리보기라고 설명 | 입력·계산 실제, 이력 모의 |
| 40–55s | VC 계기판 +12km | 목표·만기 비용 갱신과 관측 증가 확인. “줄일 수 있는 이동을 주10km 줄이면 만기 약13.7만 원 감소”는 증가 전960일 기준 가정이라고 구분 | 실제 SDK 수신 |
| 55–65s | VC 기어 D | 주행 중 최소 잠금 화면. 이 에뮬레이터는 hmg_car_service가 없어 기어 D/R 기반 UXR 제한 확인 | 실제 기어·UXR |
| 65–72s | VC 기어 P | 보던 화면 복원 | 실제 |
| 72–82s | 이 과장499일 출구 화면 | “거리 초과 약31,431km×100원 → 반납 최소314만 원, 인수 순비용176만 원·준비할 돈1,926만 원.” 두 묶음 비교·월료와 대체 이동비 미확인 확인. 필요 시90일 장면에서 “주10km 감축이면 약19.6만 원 감소” 보조 설명 | 이력·견적 모의, 계산 실제 |
| 82–90s | 앱 강제 종료, VC +50km, 앱 재실행 | 공백 구간50km 회수와 사용·목표·비용 재계산 | 실제 재시작 회수 |

다른 차 버튼을 보여 줄 경우 “평소 주행 대비 추가3.9만 원”과 “이번420km 전체를 다른 차로 전환하면4.2만 원 감소”를 구분한다. 두 값이 같다고 말하지 않는다. 감축 탄력은 같은 기간·단가·감축 유지 가정이며 기존 초과액이0원이 되면 감소도 멈춘다. 통근·업무·돌봄을 반드시 줄여야 한다는 메시지는 쓰지 않는다.

예비 컷: 이 과장90일은 필요273/최근513km 목표 강요 대신 출구 비교와 감축 탄력, 정 주임은 정산0원·추천 연1만km, 보험은 구간 유지 페이스를 보여 준다. 이상값+580km 보류도 예비 컷으로 둔다. 최신 UI·시드 통합 상태에서 재촬영하며, 문서 변경 자체를 화면 구현·새 리허설 완료로 간주하지 않는다.

---

## 6. 작업 분해와 병렬 계획

| ID | 작업 | 담당 | 선행 | 완료 기준 |
|---|---|---|---|---|
| P0 | 프로젝트 골격: Gradle(hackathon 설정 재사용), 패키지, 인터페이스·도메인 모델 계약 파일, 빈 화면 빌드 | Claude(조정자) | — | `assembleDebug` 성공 |
| D1 | 디자인 목업·토큰·화면 명세 | Codex | — | design/ 3개 산출물 |
| E1 | 도메인 엔진: budget·forecast·exit·insurance + 단위 테스트(명세 예시 숫자 전부) | Codex | P0 | `testDebugUnitTest` 통과, 3절 예시 숫자 일치 |
| V1 | VehicleGateway(Odometer·Gear·UXR) + OdometerIngestor + Room + AppClock/DemoClock + 페르소나 시드 + 데모 패널 + 에뮬레이터 실측 | Claude | P0 | 에뮬레이터에서 VC 주행거리·기어 반영 확인, 재시작 공백 회수 확인 |
| U1 | Compose 화면·컴포넌트(design-tokens 기반), 가짜 UiState로 미리보기 | Codex | P0, D1 | 모든 화면 Preview 빌드, 넓은/좁은 창 |
| I1 | 통합: AppViewModel로 엔진·게이트웨이·화면 연결, 시연 시나리오 리허설, CLAUDE.md 9절 실측 기록 | Claude | E1, V1, U1 | 5절 90초 시나리오 에뮬레이터에서 통과 |
| R1 | README·APK·영상 | Claude | I1 | 제출 요건 충족 |

병렬 운영 규칙
- 같은 작업 폴더를 쓰되 **담당 패키지 밖 파일은 수정 금지**(공유 계약 파일 변경은 조정자에게 요청).
- 커밋은 조정자가 통합 시점에 한다(동시 커밋 충돌 방지).
- Gradle 빌드는 한 번에 하나만 돌도록 순서를 지킨다. **에뮬레이터는 V1·I1 담당만 사용.**

---

## 7. 테스트
- 단위 테스트(E1): 287 / 244 / −807 / 약 24만 원 / 684 / 8주 232 / 3,696·−2,904·273·513·회복 어려움·471만 원 / 314만·176만(현금 1,926만)·300만 / 보험 192·1,180·245.
- 수집 테스트(V1): 정상 증가, 역행 보류, 급증 보류, 공백 회수, 재구독 중복 방지, capability 미지원 시 상태 표시.
- 에뮬레이터 체크리스트(I1): VC 주행거리 반영 지연, 기어 P/D, driving_regulation on/off 시 잠금·복원, 강제 종료 후 재실행 회수, 반쪽 창 레이아웃.

## 8. 리스크
- 에뮬레이터 자원 부족으로 멈춤 → 영상 백업, 시연 장비 별도.
- NaviHelper 실수신 실패 → 수동 예정 이동으로 시연(이미 기본 경로).
- 시드·DemoClock이 "조작"으로 보일 위험 → [모의] 배지와 시연 중 구두 설명, 실제 SDK 수신 장면(+12km, 기어, 재시작 회수)을 반드시 포함.
- 설치율·MAU 등 사업 수치는 추정 → PT에서 산정 방식 함께 제시.

