# V2 실기기 화면 재캡처 (2026-11-15, C-V2)


## F-V3 수정 완료 (2026-11-15)

최초 캡처 68장에 **수정 재캡처 19장(`_fix`)을 추가해 총 87장**이다. 아래 결과를 최신으로 본다. `01_first_onboarding_fix.png`를 직접 열어 앱 정체·두 카드를 확인했고, 실행 Activity가 `com.namunkiro/.app.MainActivity`임을 확인했다. 최초 01의 앱 실행 여부 지적은 새 파일로 대체한다.

- 최종 명령: `android/`에서 `./gradlew :app:assembleDebug :app:testDebugUnitTest --max-workers=1` → **BUILD SUCCESSFUL, 71개 / 실패 0 / 오류 0**(표시 모델 회귀 테스트 4개 추가).
- APK SHA-256: `4787553F4AEA4BD91C57219E4383ADD17F4B15244C81E6EF4CA0BAE249CB2C09`.
- 상태 계약·domain/core·CostModel 변경 없음. 대표 금액은 원래 모델을 유지하고, 산식 말풍선만 ‘거리 반올림’ 조건을 명시해 정수 표시 거리와 일치시켰다.
- Gradle 데몬 종료 후 AVD를 `-no-snapshot -memory 2048`로 실행해 설치·신규 상태부터 확인했다. 촬영 종료 후 `adb emu kill` 성공. 커밋 없음.

| 발견/추가 지적 | 상태 | 수정 및 실측 증거 |
|---|---|---|
| F1 개인 홈 시연 재진입 | **수정됨** | 개인 계약이 있으면 헤더에 76dp ‘예시 [모의]’ 버튼. 개인 생성→예시→개인 홈 복귀 실측(10/11/52 fix) |
| F2 범위·가정 라벨 | **수정됨** | 계약 평균은 엔진 `endKmPoint`(계획 포함), 다른 끝은 최근4주 가정으로 매핑. 20만=62,048 / 27만=62,741km와 동일한 두 끝을 표시(20 fix) |
| F3 미확인 축 0~1원 | **수정됨** | 알려진 비용이 없으면 숫자 축 대신 ‘조건 확인 후 비교’. 알려진 값이 전부0일 때도 1원 축을 만들지 않음(24 fix) |
| F4 정수 km 산식 | **수정됨** | 말풍선 `2,353km × 100원 = 235,300원 · 거리 반올림`; 표시 거리로 CostModel을 재사용. 대표 금액·스크럽 원래 계산은 유지(11 fix) |
| F5 IME 이전 버튼 | **수정됨** | 진행 중 두 진입 카드를 접고 이전/다음을 같은 행에 배치. 키보드가 열린 안정 프레임에서 두 버튼 전체 보임(06/09 fix) |
| F6 작은 날짜 선택기 | **수정됨** | 앱 테마의 큰 날짜 제목·연/월 이동·76dp 날짜 버튼. 온보딩/계약/보험/예정 이동이 공통 선택기를 사용(03 fix) |
| F7 설정 띠·개발 표기 | **수정됨** | MainActivity 검은 띠 제거, 헤더 안 76dp 톱니 버튼으로 이동. 설정 패널은 이름·한국어 날짜/시각·정차/주행·켜짐/꺼짐 등으로 번역하고 24sp/76dp 적용(11/28 fix) |
| F8 예측 라벨·선 겹침 | **수정됨** | 오늘 값 라벨을 관측점 왼쪽 위에 놓아 예측선과 분리(20 fix). 일별 누적 원자료가 없는 현재 상태에서는 과거 선을 임의 생성하지 않음 |
| (a) Gangneung | **수정됨** | 예시 이동 입력의 이름을 기존 공유 상수 ‘강릉 경포대’로 미리 채우고 토요일/왕복420km를 제공. Preview 예시 라벨도 통일; 실제 제목/긴 이동 줄에 한국어 표시(18/19 fix). 이전 영문은 작업자 입력이었으며 실제 시드에 영문 상수는 없었음 |
| (b) 검은 설정 띠 | **수정됨** | 위 F7과 동일, 앱 안 톱니 버튼·설정 패널로 대체 |
| (c) 홈 핵심 카드 첫 화면 | **수정됨** | 거리 차트330→220dp, 홈 카드 간격/패딩과 링210→160dp. 전체1682×1210 앱 영역에서 금액/막대/링/페이스 카드 전체가 스크롤 없이 표시(11 fix) |
| (d) 최초 앱 실행 캡처 | **수정됨** | 앱 Activity와 실제 사진을 확인한 새01 fix를 최신 증거로 사용 |
| S1 시스템 IME 사각형 | 환경 항목 | 제안/도구 줄 사각형은 Android IME 영역으로 남아 있음. 앱 텍스트 깨짐이 아니며 앱 코드에서 시스템 키보드 글리프를 바꾸지 않음(06/09 fix) |

### 수정 재캡처 파일별 표

| 파일 | 화면·페르소나 | 핵심 숫자·확인 |
|---|---|---|
| [01_first_onboarding_fix.png](01_first_onboarding_fix.png) | 신규 첫 실행 | 남은키로 정체, 예시 약24만/내계약30초 카드; 순정 내비 아님 |
| [02_personal_step1_fix.png](02_personal_step1_fix.png) | 개인 입력1/3 | 입력에 집중한 레이아웃·이전/다음 같은 행 |
| [03_personal_datepicker_fix.png](03_personal_datepicker_fix.png) | 만기 날짜 | 2029.11.15, 큰 제목과 76dp 연·월·일 버튼 |
| [05_personal_step2_fix.png](05_personal_step2_fix.png) | 개인 입력2/3 | 거리1~1,000,000km 안내 |
| [06_personal_step2_keyboard_fix.png](06_personal_step2_keyboard_fix.png) | 개인 거리·키보드 | 키보드 위 이전/다음 모두 온전히 보임 |
| [08_personal_step3_fix.png](08_personal_step3_fix.png) | 개인 입력3/3 | 단가1~100,000원/km, 이전/시작 같은 행 |
| [09_personal_step3_keyboard_fix.png](09_personal_step3_keyboard_fix.png) | 개인 단가·키보드 | 이전/시작 버튼 전체 보임 |
| [10_personal_first_home_fix.png](10_personal_first_home_fix.png) | 생성 직후 개인 홈 | 7일 뒤 예측 안내·차 누적0km, 헤더 예시 재진입 버튼 |
| [11_kim_home_fix.png](11_kim_home_fix.png) | 김 대리 홈 | 24만 원, 주281 목표·남음95km·현재299; 핵심4요소 전체·2,353×100=235,300 표시 |
| [12_kim_week_fix.png](12_kim_week_fix.png) | 김 대리 이번 주 | 목표281·예상296·추가0원, 검은 설정 띠 없음 |
| [18_kim_trip_ready_fix.png](18_kim_trip_ready_fix.png) | 김 대리 예시 이동 | 강릉 경포대·왕복420·2028.03.18 기본 입력 |
| [19_kim_week_gangneung_fix.png](19_kim_week_gangneung_fix.png) | 강릉 계획 반영 | 제목·긴 이동 줄 한국어, 예상684·목표281→278·+3.9만 |
| [20_kim_forecast_fix.png](20_kim_forecast_fix.png) | 계획 포함 예측 | 20만~27만, 최근4주62,048·20만/계약평균62,741·27만; 관측 라벨/선 분리 |
| [23_kim_exit_first_fix.png](23_kim_exit_first_fix.png) | 출구 질문 | 인수가·예상 시세 질문 시트 |
| [24_kim_exit_fix.png](24_kim_exit_fix.png) | 미확인 출구 | 24만 거리 근거, 미확인 묶음에 1원 축 없음 |
| [28_kim_demo_panel_fix.png](28_kim_demo_panel_fix.png) | 톱니→시연 설정 | 김 대리·2028.03.16 오후6:30·모의 차량·정차·주행 제한 꺼짐, 큰 버튼 |
| [49_kim_half_home_fix.png](49_kim_half_home_fix.png) | 반쪽 홈 | 톱니·모의 칩 유지, 24만·95km 링·1열 배치(페이스는 아래로 이어짐) |
| [52_personal_return_fix.png](52_personal_return_fix.png) | 개인 데이터 복귀 | 생성한 개인 계약의 첫날 홈으로 복귀; 초기화 없이 왕복한 이번 흐름 |
| [53_kim_demo_resumed_fix.png](53_kim_demo_resumed_fix.png) | 시연 재개 | 개인 헤더에서 시연 재진입, 김 대리24만 홈 |

위 재캡처 이후 IME 안정 프레임을 다시 찍기 위해 별도 pm clear를 한 번 더 수행했다. 10→11→52→53 왕복 캡처는 그 초기화 전의 연속 흐름이며 혼동하지 않는다. 변경 화면은 조정자가 UX 게이트로 재검토해야 한다.

- 최초 캡처 **68장**. 원본 `adb exec-out screencap -p` PNG이며 자르기·합성·보정하지 않았다. 전환 프레임 45·64도 구분해서 보존했다.
- 환경: `Pleos_Connect_v2.1.0`, 2560×1440, 앱 전체 창 1682dp / 반쪽 창 약 840dp, Android 사용자 10, 한국어 UI, `America/Los_Angeles`. 캡처 시점 Windows 시각은 17:28~17:42, 에뮬레이터 시스템 시각은 화면 오른쪽 위 12:29~12:42 오전으로 보인다. 앱 데모 시각은 이와 별도다.
- 기존 실행 중이던 동일 AVD가 `-no-snapshot -memory 2048`로 실행된 것을 확인하고 재사용했다. 먼저 `gradlew --stop`으로 데몬 1개를 종료했으며 추가 빌드는 실행하지 않았다.
- debug APK 설치 성공. SHA-256: `1FB9F8FDB9811DAC154F85B2A5CA2D8913DDE3809C5EBCBAF57AFB948146C0A4`.
- `pm clear --user 10 com.namunkiro` 후 신규 상태에서 시작했다. 처음 사용자 직접 조작과 겹친 시도는 조정자 확인 후 초기화·재촬영했으며 최종 01~10은 독점 조작 상태다.
- 개인 입력 완료 후 홈에서 시연 재진입을 찾지 못해 10 이후 한 번 더 `pm clear`했다. 따라서 52는 **빈 개인 상태(온보딩)로 복귀**한 캡처이며, 생성한 개인 계약을 왕복 보존한 증거로 취급하지 않는다.
- 강릉 이동은 UI에서 토요일 2028.03.18, 왕복 420km를 입력했다. 이동 이름 `Gangneung`은 작업자가 직접 입력한 영문이며 앱의 번역 누락이 아니다.
- 01~58은 시드·가짜 게이트웨이 기반이다. 59부터 실제 SDK로 전환했다. VC HTTP 쓰기 정책 차단은 없었고 기어 D/P·+12km·+580km를 모두 실제 SDK에서 확인했다. 데모 모드의 급증 허용량 약 100km를 사용하므로 실사용 기본 규칙의 허용량 실측은 아니다.
- 완료 후 `adb emu kill` 성공 및 `adb devices` 빈 목록, emulator/qemu 프로세스 없음 확인. 마지막 VC는 P, 계기판 22,566km; 앱은 두 번째 +580km 보류 상태다. UI 코드 수정·커밋 없음.

## 파일별 색인

표의 ‘없음’은 해당 정지 화면에서 발견하지 않았다는 의미이며 기능 전체의 통과 판정이 아니다. 스크롤 아래로 이어지는 내용은 가려짐 결함과 구분했다.

| 파일 | 페르소나 | 화면 | 보인 핵심 숫자·상태 | 이상한 점 |
|---|---|---|---|---|
| [01_first_onboarding.png](01_first_onboarding.png) | 신규 | 첫 실행 두 갈래 | 예시 김 대리 약 24만 원 / 3가지·30초 | 앱 정체와 두 큰 카드 보임 |
| [02_personal_step1.png](02_personal_step1.png) | 개인 | 입력 1/3 | 만기일 미선택, 다음 비활성 | 없음 |
| [03_personal_datepicker.png](03_personal_datepicker.png) | 개인 | 만기 날짜 선택기 | 2029년 11월 15일 | F6: 작은 플랫폼 달력, 날짜 제목 줄바꿈 |
| [04_personal_date_selected.png](04_personal_date_selected.png) | 개인 | 만기 선택 완료 | 2029.11.15, 다음 활성 | 없음 |
| [05_personal_step2.png](05_personal_step2.png) | 개인 | 입력 2/3 | 총 약정거리, 1~1,000,000km | 없음 |
| [06_personal_step2_keyboard.png](06_personal_step2_keyboard.png) | 개인 | 약정 입력·IME | 빈 거리 필드, 다음 비활성 | F5: 이전 버튼 하단 일부 잘림; S1 |
| [07_personal_step2_entered_keyboard.png](07_personal_step2_entered_keyboard.png) | 개인 | 약정 입력·IME | 60000, 다음 활성 | 다음은 보임, 이전 일부 잘림; S1 |
| [08_personal_step3.png](08_personal_step3.png) | 개인 | 입력 3/3 | 초과 단가 1~100,000원/km | 없음 |
| [09_personal_step3_keyboard.png](09_personal_step3_keyboard.png) | 개인 | 단가 입력·IME | 단가 필드, 내 계약 시작 | F5·S1 |
| [10_personal_first_home.png](10_personal_first_home.png) | 개인 | 계약 생성 직후 홈 | 주행 7일 뒤 예측, 차 누적 20,014km | F1: 홈·헤더에서 예시 재진입 없음 |
| [11_kim_home.png](11_kim_home.png) | 김 대리 | 초과 홈 | 약 24만 원, 사용 21,382km, 만기 62,353km, 목표 주 281 / 현재 299, 주 18km 감축 | F4: 표시 산식의 정수 km와 원 결과 불일치; F7 |
| [12_kim_week.png](12_kim_week.png) | 김 대리 | 이번 주 | 목표 281, 예상 296, 평소 대비 0원; 월~일 48/52/41/45/46/32/32 | 기본 주행과 추가 이동 비교로 읽을 필요 |
| [13_kim_trip_sheet.png](13_kim_trip_sheet.png) | 김 대리 | 예정 이동 입력 | 빈 이름·왕복 km, 2028.03.16 | 없음 |
| [14_kim_trip_keyboard.png](14_kim_trip_keyboard.png) | 김 대리 | 이름 입력·IME | 날짜 2028.03.16, 계획 추가 비활성 | 시트 취소/추가 보임; S1 |
| [15_kim_trip_distance_keyboard.png](15_kim_trip_distance_keyboard.png) | 김 대리 | 거리 입력·IME | 입력 이름 Gangneung, 왕복 km | 영문 이름은 직접 입력; S1 |
| [16_kim_trip_date.png](16_kim_trip_date.png) | 김 대리 | 이동일 선택기 | 2028.03.16 | 없음 |
| [17_kim_trip_saturday.png](17_kim_trip_saturday.png) | 김 대리 | 토요일 선택 | 2028.03.18 | 없음 |
| [18_kim_trip_ready.png](18_kim_trip_ready.png) | 김 대리 | 계획 저장 전 | Gangneung, 420km, 2028.03.18 | 없음 |
| [19_kim_week_gangneung.png](19_kim_week_gangneung.png) | 김 대리 | 강릉 계획 후 | 예상 684, 목표 281→278, +3.9만 원, 평소보다 +388km | 이전 시안의 282→279와 수치가 다름(같은 모델의 내림 적용 여부 확인 대상) |
| [20_kim_forecast.png](20_kim_forecast.png) | 김 대리 | 계획 포함 예측 | 범위 20만~27만 원, 최근 습관 62,741km·27만 / 계약 평균 62,353km·24만 | F2: 범위와 하단 가정 값 불일치; F8 |
| [21_kim_why_forecast.png](21_kim_why_forecast.png) | 김 대리 | 예측 왜 시트 | 실제 범위 거리 62,048~62,741km, [모의] 고지 | F2: 본문의 62,048과 화면 하단 가정값 연결 안 됨 |
| [22_kim_forecast_return.png](22_kim_forecast_return.png) | 김 대리 | 왜 닫은 뒤 예측 | 20만~27만, 관측 주행 154회·루틴 80% | F2·F8 |
| [23_kim_exit_first.png](23_kim_exit_first.png) | 김 대리 | 출구 첫 질문 | 인수가·예상 시세 미입력 | 시트 한 개 표시, 나중에 선택 가능 |
| [24_kim_exit.png](24_kim_exit.png) | 김 대리 | 출구 비교 | 거리 정산 24만 원, 모든 선택지 미확인 | F3: 각 묶음 0원—1원 축; 반납 최소값 미표시 |
| [25_kim_exit_calendar.png](25_kim_exit_calendar.png) | 김 대리 | 출구 하단 달력 | 종료 2030.10.31, 통지 2030.09.30 | 연도 확인됨; F3 유지 |
| [26_kim_contract.png](26_kim_contract.png) | 김 대리 | 계약 상단 | 만기 2030.10.31, 60,000km, 100원/km, 공제0, 인수1,800만 | 면제/환급/시세/해지 요율 미확인 명시 |
| [27_kim_contract_lower.png](27_kim_contract_lower.png) | 김 대리 | 계약 하단 | 보험 꺼짐, 조건 입력 버튼, 환급 미확인 | 없음 |
| [28_kim_demo_panel.png](28_kim_demo_panel.png) | 김 대리 | 시연 패널 | KIM, 501일, 계기판 21,394, 기어 PARK | F7: 디버그 패널 ISO·영문 필드 |
| [29_lee499_selected.png](29_lee499_selected.png) | 이 과장 499일 | 시드 선택 패널 | LEE_499, 2028.03.14, 31,240km | F7 |
| [30_lee499_home.png](30_lee499_home.png) | 이 과장 499일 | 회복 어려움 홈 | 314만 원, 현재 주 438, 0원 주 209 | 회복 어려움 제목 확인 |
| [31_lee499_exit.png](31_lee499_exit.png) | 이 과장 499일 | 출구·복원 스크롤 | 반납 최소314만+, 보유/매각176만, 승계300만+, 해지779만+, 남은 월료1,920만 | 이전 김 대리 출구의 스크롤 위치가 유지되어 상단 히어로는 화면 밖 |
| [32_lee499_exit_top.png](32_lee499_exit_top.png) | 이 과장 499일 | 출구 위로 스크롤 | 히어로176만, 사용31,228→만기91,431km, 초과31,431 | 빗금·독립 축 확인; 위 라벨 일부는 아직 화면 밖 |
| [33_lee499_panel.png](33_lee499_panel.png) | 이 과장 499일 | 시연 패널 | 31,240km, 실제 시드 날짜 2028.03.14 | F7 |
| [34_lee90_selected.png](34_lee90_selected.png) | 이 과장 90일 | 시드 선택 패널 | 2027.01.30, 6,612km | F7 |
| [35_lee90_home.png](35_lee90_home.png) | 이 과장 90일 | 회복 어려움 홈 | 471만 원, 현재 주513, 0원 주272 | 제목·금액 확인 |
| [36_lee90_panel.png](36_lee90_panel.png) | 이 과장 90일 | 시연 패널 | LEE_90, 6,612km | F7 |
| [37_jung_selected.png](37_jung_selected.png) | 정 주임 | 시드 선택 패널 | 2028.03.16, 12,012km | F7 |
| [38_jung_home.png](38_jung_home.png) | 정 주임 | 덜 탐 홈 | 25,006km 남음, 만기34,994km, 사용12,000km, 정산0원, 연8,743→1만km 검토 | 남는 거리 히어로 확인 |
| [39_jung_contract.png](39_jung_contract.png) | 정 주임 | 계약·복원 스크롤 | 단가100, 공제0, 인수1,800만, 환급 미확인, 보험꺼짐 | 이전 계약 스크롤 유지로 상단 필수조건은 화면 밖 |
| [40_jung_panel.png](40_jung_panel.png) | 정 주임 | 시연 패널 | JUNG, 12,012km | F7 |
| [41_park_selected.png](41_park_selected.png) | 박 과장 | 시드 선택 패널 | 2027.10.07, 8,830km | F7 |
| [42_park_insurance.png](42_park_insurance.png) | 박 과장 | 보험 모드·계약 하단 | 120원/km·운용리스·보험켜짐, 1만km 구간 주91 | 1.2만km 구간은 스크롤 아래 |
| [43_park_contract_top.png](43_park_contract_top.png) | 박 과장 | 계약 상단 | 만기2030.01.05, 약정45,000, 단가120, 운용리스 | 미확인 조건 명시 |
| [44_park_insurance_targets.png](44_park_insurance_targets.png) | 박 과장 | 보험 구간 목표 | 보험켜짐, 10,000km 주91 / 12,000km 주245 | 두 구간 유지 목표 확인 |
| [45_park_half_transition.png](45_park_half_transition.png) | 박 과장 | 반쪽 전환 중 | 앱·내비 아이콘 전환 프레임 | 안정된 화면 아님; 반쪽 증거는49·50 |
| [46_park_panel.png](46_park_panel.png) | 박 과장 | 전체 창 복귀·패널 | PARK, 8,830km | F7 |
| [47_kim_selected.png](47_kim_selected.png) | 김 대리 | 시드 재적용 | KIM, 21,394km | 강릉 계획은 초기화됨; F7 |
| [48_kim_home_reset.png](48_kim_home_reset.png) | 김 대리 | 초기 시드 홈 | 24만 원, 주18km 감축, 주281 목표 | F4 |
| [49_kim_half_home.png](49_kim_half_home.png) | 김 대리 | 반쪽 홈·내비 병렬 | 24만 원·주18, 사용21,382→만기62,353 | 시연 칩 한 줄·1열 정상; F4 |
| [50_kim_half_week.png](50_kim_half_week.png) | 김 대리 | 반쪽 이번 주 | 목표281/예상296/추가0원 세로배치 | 차트 숫자는 화면 아래로 이어짐; 겹침 없음 |
| [51_kim_full_home.png](51_kim_full_home.png) | 김 대리 | 전체 창 복귀 홈 | 24만 원, 주281 | 반쪽 전환 후 홈 정상 |
| [52_personal_return.png](52_personal_return.png) | 개인 빈 상태 | 내 데이터로 복귀 | 신규 온보딩 두 카드 | 개인 계약 복원 실측 아님(앞서 pm clear했음) |
| [53_kim_demo_resumed.png](53_kim_demo_resumed.png) | 김 대리 | 예시 재개 | 24만 원, 시연 칩 | 예시 재개 정상 |
| [54_kim_clock_before.png](54_kim_clock_before.png) | 김 대리 | 데모 시계 이동 전 | 2028.03.16, +7일 버튼 | F7 |
| [55_kim_clock_after.png](55_kim_clock_after.png) | 김 대리 | 데모 시계 +980일 | 2030.11.21, 계기판21,394 유지 | +7일 140회 UI 조작, 날짜 이동 확인 |
| [56_kim_expired_home.png](56_kim_expired_home.png) | 김 대리 | 만기 경과 홈 | 계약 기간 사용21,382km, 참고 정산0원 | 종료 제목·계약 확인·최종 청구 확인 안내 정상 |
| [57_kim_expired_panel.png](57_kim_expired_panel.png) | 김 대리 | 만기 경과 패널 | 2030.11.21 | F7 |
| [58_kim_reset_for_vc.png](58_kim_reset_for_vc.png) | 김 대리 | VC 준비·시드 초기화 | 2028.03.16, 가짜21,394km | F7 |
| [59_kim_real_sdk.png](59_kim_real_sdk.png) | 김 대리 | 실제 SDK 전환 패널 | 실제20,013.6 / 승인21,394km | 이전 VC 값과 시드 불일치 상태; 이후 정렬·동기화 |
| [60_kim_vc_sync.png](60_kim_vc_sync.png) | 김 대리 | 실제 SDK 동기화 | 차량=승인21,394km | VC를21,394로 맞춘 뒤 시드 이동0 확인 |
| [61_kim_vc_home.png](61_kim_vc_home.png) | 김 대리 | 실제 SDK·모의 이력 홈 | 24만 원·시연 칩 | 실제 SDK와 시드 이력이 혼합된 시연 |
| [62_kim_driving_lock.png](62_kim_driving_lock.png) | 김 대리 | 실제 VC 기어D 잠금 | 잠금 두 줄, 앱 숫자·버튼 없음 | 요구 상태 확인 |
| [63_kim_park_return.png](63_kim_park_return.png) | 김 대리 | 실제 VC P 복귀 | 24만 원·주18·현재주299 | 홈 복원 정상 |
| [64_kim_plus12_pending.png](64_kim_plus12_pending.png) | 김 대리 | +12 쓰기 직후·수신 전 | 아직 사용21,382·만기62,353 | 전파 지연 프레임; 결과 증거는65 |
| [65_kim_plus12_settled.png](65_kim_plus12_settled.png) | 김 대리 | 실제 +12 수신 완료 | 사용21,394·만기62,388, +12km→+1,200원·관측 | 한계 비용 정상; F4의 표시 산식 반올림 문제 유지 |
| [66_kim_rebase_confirm.png](66_kim_rebase_confirm.png) | 김 대리 | +580 보류 재기준 확인 | 21,986km를 주행으로 더하지 않음·기록 유지 | 명시적 확인 문구 보임 |
| [67_kim_rebased_home.png](67_kim_rebased_home.png) | 김 대리 | 재기준 후 홈 | 24만 원, +12km 관측 칩 유지 | 재기준 자체를 +580 주행으로 추가하지 않음(저장 ledger 확인) |
| [68_kim_anomaly580_settled.png](68_kim_anomaly580_settled.png) | 김 대리 | 두 번째 +580 안정 보류 | 4초 21,986→22,566km, 허용100.4km, 오후6:32 | 다시 확인·22,566km 새 기준 버튼·기록 유지 안내 정상 |

## 최초 발견 이력 — F1~F8은 위 F-V3에서 수정됨

| ID | 발견 | 근거 |
|---|---|---|
| F1 | 개인 계약 생성 후 홈·헤더에 예시로 진입하는 경로가 보이지 않음. 최초 온보딩에서만 예시 카드가 있어 실제 개인 계약을 만든 뒤 시연을 보여 주려면 경로 검토 필요 | 10, 이후 캡처를 위해 pm clear 사용 |
| F2 | 강릉 계획 후 범위는 20만~27만/62,048~62,741km인데 화면의 가정 라벨은 최근4주62,741·27만 / 계약평균62,353·24만. ‘범위 양 끝은 서로 다른 가정’이라는 왜 설명과 하단 두 값이 맞지 않음 | 20~22 |
| F3 | 모든 선택지가 미확인인 출구에서 0원—1원 축이 나타남. 거리 정산24만은 알면서 반납 막대는 미확인뿐이라 비용 근거 연결도 약함 | 24~25 |
| F4 | 홈 말풍선은 `초과 2,353km × 100원 = 235,349원`으로 보여 정수 표시만 계산하면 235,300원과 맞지 않음. 수신 후도 `2,388 × 100 = 238,849원`. 거리 소수 반올림/금액 계산 정밀도가 서로 다름 | 11,49,65 |
| F5 | 개인 입력 IME에서 다음/시작 주 버튼은 보이지만 이전 버튼 아래 부분이 가려짐. 기존 ‘키보드가 주 버튼을 가림’ 문제는 이 캡처에서 재현되지 않음 | 06~07,09 |
| F6 | 온보딩의 플랫폼 날짜 선택기는 앱 폰트·터치 크기와 달리 작은 회색 달력이고 큰 날짜 제목이 `11월 15 / 일 (목)`으로 꺾임 | 03 |
| F7 | 앱 밖 상단 ‘시연 설정’ 띠는 여전히 매우 작은 저대비 글자. 패널 자체에는 ISO 시각·KIM/LEE_499/PARK·false·PARK·SDK 등 개발용 표기가 노출됨(일반 화면과 구분). 헤더 시연 칩은 크게 잘 보임 | 11,28 등 패널 캡처 |
| F8 | 예측의 오늘 관측 라벨이 우상향 예측선과 겹치며, 오늘 이전 관측 누적은 실선 없이 점으로만 보임 | 20,22 |
| S1 | IME 상단 제안/도구 줄에 작은 사각형 세 개가 보임. Android 시스템 키보드 영역이며 앱 본문 한글 깨짐으로 단정하지 않음 | 06~07,09,14~15 |

주 281km·현재299km·주18km 감축은 이전 시안의 최근 습관294/목표282와 다르지만 이번 앱은 계약 평균 기준이라고 명시한다. 이것만으로 불일치로 판정하지 않았다. 이 과장499일은 패널 날짜가 2028.03.14이며, 김 대리·정 주임은 2028.03.16이다.

## 확인된 개선·한계

- 첫 화면의 앱 정체와 두 큰 카드, 헤더 내 시연 인물/복귀 칩, 반쪽 창 한 줄 칩·1열 배치, 독립 출구 축·빗금·남은 월료 연결, 연도 포함 달력, 보험 구간 유지 목표, 종료 홈, 주행 잠금 두 줄을 실제 캡처했다.
- 실제 SDK +12km에서 한계 비용 +1,200원, +580km 보류, 소수 허용량·한국어 시각·재기준 확인을 캡처했다. 첫 보류의 안정 화면 촬영보다 상태 전환이 늦어 65는 +12 결과 프레임이며, 두 번째 +580의 안정 보류는68이다.
- 첫 보류를 재기준 처리한 후 저장 ledger는 승인21,986·heldRawKm=null, 계약 시작 계기판592였다(기존12에서 +580 이동). 사용 이력은21,394km로 유지되어 +580을 주행에 가산하지 않음을 확인했다. 두 번째 보류는 미해결 상태로 종료했다.
- 개인 계약 데이터 왕복 보존, 보험 켜기/끄기 저장 조작, 데이터가 없는 차량의 신호 타임아웃, reduce-motion, 애니메이션 중간 타이밍은 이번 정지 캡처의 검증 대상에서 제외했다. UI 코드는 변경하지 않았으므로 조정자가 발견을 분류하고 UX 게이트 재검토를 진행해야 한다.
