# 심사관 첫 실행 엣지케이스 검증

검증일: 2026-11-15. C3 Codex 인계 작업. 실제 실행과 코드 검토를 구분하며, 미실행 항목을 통과로 처리하지 않는다.

## 환경과 빌드

- AVD: `Pleos_Connect_v2.1.0`, `K:\Android\Sdk\emulator\emulator.exe -avd Pleos_Connect_v2.1.0 -no-snapshot -memory 2048`.
- 실제 앱 사용자: 10. `pm clear`, `am start`, `am force-stop` 모두 `--user 10` 지정.
- 초기 SDK 계기판: 0km. 기어 P. 로케일 `ko-KR`, 시간대 `America/Los_Angeles`. 첫 계약 날짜는 현지 2026-11-14로 저장됨.
- `:app:assembleDebug :app:testDebugUnitTest :app:assembleDemo --no-daemon --max-workers=1`: **BUILD SUCCESSFUL**. 단위 테스트 41개, 실패·오류 0개(도메인 22, 거리 수집기 12, 차량·데모·저장소 7).
- demo APK: `android/app/build/outputs/apk/demo/app-demo.apk`, `com.namunkiro`, versionCode 1, versionName `0.1.0`, debuggable 아님. `apksigner verify --print-certs` 성공. 인증서는 **Android Debug**(SHA-256 `bd614707cc2ef4d8a687b0b83e0b832197ff0e573edfffa852acde563433b09d`). 배포용 서명이 아니다.
- debug APK를 실제 설치해 아래 화면을 검증했다. demo APK도 `adb install -r` 성공 후 실행했으며 상단 전환 버튼과 모의 홈이 유지됨을 확인했다(`10_demo_apk.png`).

## 케이스별 결과

| 번호 | 케이스·재현 | 기대 | 실제·증거 | 판정·조치 |
|---|---|---|---|---|
| 1 | 사용자 10 `pm clear` 후 최초 실행 | 숨은 제스처를 몰라도 시연 진입, 홈에서 왕복 전환, 모의 식별 | 기존 진입은 제목 길게 누르기뿐이었다. 현재 상단 `시연 데이터로 둘러보기`가 보이고, 클릭하면 김 대리 홈과 모의 표시·복귀 버튼이 나타남. `01_first_install.png`, `06_demo_home.png` | **P0 수정·실측 통과**. app 진입부에 버튼 추가. 새 시연은 모의 차량을 사용하므로 VC 0km에서도 바로 동작 |
| 2 | SDK 0km에서 만기 2029-11-15, 약정 60,000km, 단가 100 입력 | 빈 홈·0원 확정 오인 없음 | 온보딩 시작 계기판 0km 관측 확인. 계약 생성 후 첫 주행 준비 홈, `주행 7일 후부터 만기 정산을 예측해요` 표시. 첫날 안내 추가. `03_zero_km_first_day.png`는 계약 직후 공제 질문이 열린 상태, `05_rebased_first_day.png`·`07_personal_restored.png`는 사용량 0km인 첫날 홈 | **통과**. 계기판 0 자체는 유효한 관측이며 실패값을 0으로 대체하지 않음 |
| 3 | VC 0→20,000km 급증 | 보류 후 명시적 재기준으로 회복, 급증을 주행으로 가산하지 않음 | 20,000km HELD 화면 확인 후 `계기판 기준 다시 설정`→확인→홈 복귀. 저장 계약 시작 계기판 20,000, 승인값 20,000, heldRawKm null, history 빈 목록 확인. `04_spike_hold.png`, `05_rebased_first_day.png` | **P0 수정·실측 통과**. 기존 사용 이력을 유지하면서 계약·보험 시작 계기판을 같은 차이만큼 이동. 보류값을 승인 주행으로 추가하는 우회가 아님 |
| 4 | UXR null·기어 UNKNOWN 또는 기어 P만 수신 | 영구 주행 잠금 금지, 안전한 신호 확인·재시도 경로 | 기존 `uxRestricted != false`가 null을 영구 잠금으로 처리. 현재 true·명시적 비P 기어만 주행 잠금, 두 신호 미확인은 별도 `차량 신호 확인 중` 화면과 재시도·시연 진입. P만 있으면 기어로 판단 | **P0 수정, 코드 검토**. null 장기 지속과 capability 오류를 실제 SDK에 주입하는 검증은 미완료 |
| 5 | Vehicle 서비스 미연결·capability 미지원 | 크래시 없음, 재시도 가능 | initialize/get/check/register/UX manager 예외를 gateway에서 catch하고 오류 상태로 보존. 미수신 거리로 0을 만들지 않음 | **코드 검토만**, 실제 서비스 차단·미지원 capability 주입 검증 미완료 |
| 6 | 온보딩 입력 중 강제 종료·재실행, 시연 강제 종료·재실행, 에뮬레이터 재부팅 | 입력·계약·이력 보존, 공백 회수 | 약정 60,000km 입력한 2단계에서 force-stop 후 같은 단계·값 복원 확인(`02_draft_restart.png`). 시연 강제 종료 및 qemu 종료→cold boot 후 KIM 모드·96km/282km 복원(`08_cold_boot_demo.png`). 저장소 원자 파일 쓰기와 기존 공백 수집 테스트 통과 | **부분 통과**. 이번 작업에서 실제 공백 증가 재현은 자동 승인 검토 차단으로 미완료(아래 기록) |
| 7 | 개인 계약→시연→개인→시연, 시연 중 프로세스 재시작 | 서로 덮어쓰지 않음 | 기존 `exitDemo`는 개인 데이터 삭제. 개인·시연 별도 백업 추가. 개인 시작값 20,000과 빈 이력 복원 실측(`07_personal_restored.png`). 다중 페르소나 변경·재시작·복귀·시연 재개 단위 테스트 추가 통과 | **P0 수정·실측/테스트 통과**. 시연 버튼은 이전 시연 상태 재개, 패널의 페르소나 선택만 시드 재적용 |
| 8 | 과거 만기·0·음수·과대값·이전·중단 재진입 | 잘못된 계약 생성 금지, 입력 유지 | ViewModel에서 내일~20년 이내 만기, 1~1,000,000km, 1~100,000원/km 검증. 기존 화면은 0·음수·비숫자에서 다음 비활성. 유효 입력 초안·단계 재실행 복원 실측 | **부분 통과**. 입력 경계 전체의 UI 자동 재현은 미완료. 범위 오류 안내 추가, 잘못된 형식·과거 날짜의 버튼 표현은 재디자인 항목 |
| 9 | 앱 영역 오른쪽 핸들로 반쪽 창·복원 | 계약·모드·현재 화면 유지 | 첫 시도는 메모리 압박 후 adb shell 정지로 실패. cold boot 후 `input swipe 2540 710 1700 710 700`으로 재실행 성공. 모의 표시·96km/282km 유지, 1열 배치 확인(`09_window_resize.png`). 반대 스와이프로 전체 창 복원 성공 | **실측 통과**. 상단 버튼의 작은 글자·테마는 재디자인 항목 |
| 10 | 주 경계·만기 경과·첫 주 | 주 계산 일관, 만기 후 크래시 없음 | 첫 주 홈 실측. 기존 partial-week/carry/보험 월요일 경계 테스트 통과. 만기 경과 계약 UI 실측은 미완료 | **부분 통과**. 도메인·공유 계약 수정 없음 |
| 11 | 제출 빌드의 진입점·서명·버전 | 데모 진입 포함, 검증 가능한 서명·버전 | debug/demo는 DEMO_PANEL=true, release=false. demo 빌드·인증서·버전 검사 및 실제 설치·실행 통과(`10_demo_apk.png`) | **주의**. 제출 후보는 demo APK로 명시해야 함. release APK에는 시연 버튼 없음. 정식 배포 서명 키 연결은 조정자 결정 필요 |
| 12 | 다른 로케일·시간대 | 현지 날짜 경계 정상, 크래시 없음 | ko-KR + America/Los_Angeles의 서로 다른 언어/지역 조합으로 전체 첫 실행·계약·시연 검증. ISO 날짜 저장과 LocalDate 계산 사용 | **부분 통과**. en-US·추가 시간대 변경 검증은 미완료 |

## 변경 범위와 주의점

- `app/MainActivity.kt`: 명시적 시연 왕복 버튼, 시연 설정 버튼, 신호 미확인 화면, 첫날 안내, 재기준 확인 창.
- `app/AppViewModel.kt`: null 신호 처리, 입력 검증·초안 복원, 데모 기본 모의 차량·모드 복원, 재기준 동작. 수집 저장과 모드 전환이 같은 메인 코루틴에서 실행되어 전환 중 저장 경합을 줄였다.
- `data/AppRepository.kt`: 개인/시연 별도 스냅샷. 전체 교체가 모드별 백업까지 삭제하지 않도록 변경.
- `demo/DemoController.kt`: 개인 상태 보관·복원, 시연 이어보기.
- `test/.../VehicleDemoDataTest.kt`: 개인 데이터가 다중 페르소나 전환과 프로세스 재시작을 견디는 회귀 테스트 1개 추가.
- 공유 계약 `core/**`, `VehicleGateway.kt`, `domain/model/**`, `EngineContracts.kt`, `OdometerIngestorContract.kt`, `AppUiState.kt` **변경 없음**. 커밋하지 않음.
- 모드 전체 전환은 기존처럼 여러 JSON 파일 교체이므로 **교체 도중** 프로세스가 죽는 트랜잭션 원자성은 보장하지 않는다. 정상 전환 후 강제 종료 복원과 모드별 백업을 검증한 범위다.

## UI 재디자인 반영 필요 / UX 검토 요청 화면

AGENTS.md UX 게이트에 따라 조정자가 UX 담당에게 검토를 요청해야 한다. 이 작업자는 추가 워커를 생성하지 않았다.

- 온보딩·홈 공통 상단 시연 버튼: 기존 디자인 테마 밖의 기본 Material 버튼이라 글자와 터치 영역 크기가 본문보다 작음. 제품 테마·정차 터치 크기로 통합 필요.
- 신호 확인 중 화면, 범위 오류 안내, 첫날 안내, 재기준 확인 창: app 통합 계층에서 경로만 제공. 문구·배치·대비 검토 필요.
- 온보딩의 과거 날짜·과대값은 동작 계층에서 차단하지만 화면의 다음 버튼이 활성으로 보일 수 있음. 필드 오류·버튼 활성 조건을 UI 담당이 동일하게 맞출 것.
- 좁은 창 상단 Row 줄바꿈·시연 설정 노출을 별도 검증할 것.

## 재현 명령 요약

```powershell
adb shell pm clear --user 10 com.namunkiro
adb shell am start --user 10 -n com.namunkiro/.app.MainActivity
adb forward tcp:8088 tcp:8088
# VC를 조작하는 동안 1초마다 POST 하트비트를 유지한다.
curl.exe -X POST 'http://127.0.0.1:8088/browser/session?client=judge-c3'
curl.exe -X PUT 'http://127.0.0.1:8088/vehicle/odometer?traveled_distance_km=20000'
curl.exe -X PUT 'http://127.0.0.1:8088/vehicle/gear?gear=Park'
adb shell am force-stop --user 10 com.namunkiro
adb shell run-as com.namunkiro --user 10 cat files/namunkiro/contract.json
adb shell screencap -p /sdcard/judge.png
adb pull /sdcard/judge.png docs/rehearsal/judge/
```

## 검증 차단 기록

재부팅 후 VC의 주행거리 API 값이 기본 0km로 돌아옴을 읽기로 확인했다. 개인 데이터는 20,000km 기준을 보존하므로, 다른 에뮬레이터 상태에서 재실행하면 역행 보류될 수 있으며 재기준 경로가 필요하다.

공백 회수를 실측하기 위한 `POST /browser/session` → `PUT /vehicle/odometer?traveled_distance_km=20000.5` → `am start` 명령이 도구의 자동 승인 검토에서 `rejected: blocked by policy`로 차단되었다. 구체적인 차단 사유는 반환되지 않았다. 차단된 동작을 우회하지 않았으며 이 공백 회수 실측을 통과로 보고하지 않는다.

이번 문서의 P0 처리 완료: 숨은 데모 진입, 개인 데이터 삭제, null 신호 영구 주행 잠금, 급증 보류 재기준 부재. null 장기 지속·서비스 미지원 주입, 공백 회수 실측, 입력 경계 전체, 만기 경과 UI, en-US·추가 시간대 검증이 남아 있으므로 전체 심사관 엣지케이스 검증 완료로 선언하지 않는다.

## V2-C JVM 추가 검증 (2026-11-15)

에뮬레이터를 사용하지 않았다. 아래 결과는 실제 SDK·화면 조작 실측과 구분한 **단위 테스트** 결과다.

- 명령: `android/`에서 `./gradlew :app:assembleDebug :app:testDebugUnitTest --max-workers=1`.
- 결과: **BUILD SUCCESSFUL, 67개 테스트 / 실패 0 / 오류 0**. 기존 53개에 `app/JudgeEdgeCasesTest` 14개를 추가했다.
- 실행 근거: `android/app/build/test-results/testDebugUnitTest/TEST-com.namunkiro.app.JudgeEdgeCasesTest.xml` 및 같은 폴더의 전체 6개 테스트 스위트.

| 검증 항목 | 결과·범위 |
|---|---|
| null 차량 신호 장기 지속 | 9,999ms에는 대기, 10,000ms·1분·7일에는 `TIMED_OUT`, `Gate.NORMAL` 유지와 재시도 문구 검증; 실제 ViewModel은 포그라운드·재시도 10초 후 상태를 갱신 |
| 차량 신호 미지원 | `FakeVehicleGateway`에 두 capability `NOT_SUPPORTED`, UNKNOWN 기어·null UXR 주입 후 3회 refresh; 미지원 상태·안내 문구·비잠금 및 거리 미수신 유지 검증 |
| 서비스 오류·복구 | Fake 오류는 `ERROR` 안내, 오류 제거·refresh 뒤 `READY`; 명시적 주행 제한/D 기어는 미지원·보류보다 잠금 우선 |
| P 신호만 수신 | null UXR에서도 `READY`·비잠금, 보류는 별도 `ANOMALY_HOLD` |
| 온보딩 경계 | 과거·당일·20년 초과 만기 및 null 거부, 내일/20년 경계 허용; 거리/단가의 null·0·음수·과대값 거부와 양쪽 허용 경계 검증; UI·ViewModel이 같은 `AppPolicies` 사용 |
| 계약 편집 저장 | 8종 필드 수정이 저장소 재생성 후 유지; 잘못된 값은 기존 파일 보존, false와 미확인(null)을 구분; 저장 상태로 엔진 재계산해 약정 변경 시 정산 감소 확인 |
| 보험 모드 | 기간·계기판·구간 검증, 디스크 복원, 엔진의 남은 거리/주간 목표 계산, 끄기(null 삭제) 검증 |
| 만기 지난 계약 | 계약 후 주행 제외, 거리 정산·0원 목표 없음·홈 `ENDED` 상태와 출구 엔진 무예외 검증; ViewModel이 예측 기준일을 만기 다음 날로 고정해 시간이 지나도 예상액이 줄지 않도록 수정 |
| Locale.US·시간대 | 전역 Locale.US에서 LA/서울의 자정 경계 날짜를 각각 확인; `yyyy.MM.dd`, 한국어 요일/오후 시각·쉼표 숫자·만 원 금액 유지 검증, 테스트 후 전역 설정 복원 |

### 상태·액션 계약 변경 및 저장 경로

- `ui/state/AppUiState.kt`: `AppActions`에 `enterDemo`, `exitDemo`, `rebaseOdometer`, `openDeferredQuestion`, `setInsurancePlan`, `updateContractField(ContractField, String?)` 추가하고 `NoopActions` 갱신. 상태에 앱 기준일·시연 인물명·보험 원본 계획·차량 연결 상태를 추가했다.
- `app/AppViewModel.kt`: 위 동작 override, 재질문은 이전 응답 이력과 무관하게 직접 열기, `AppSettings`를 통한 계약/보험 검증·저장, 저장소 StateFlow 변경으로 엔진 재계산. 시연/재기준은 기존 동작을 유지한다.
- `app/AppPolicies.kt`, `app/AppSettings.kt`: 실제 ViewModel이 사용하는 순수 검증·저장 경계를 분리해 JVM에서 검증했다. Android ViewModel 생성·Compose 렌더 자체를 JVM 테스트한 것으로 해석하지 않는다.
- V2 화면에서 `actions as? AppViewModel` 및 `demoPanel` 직접 읽기를 제거했다. 헤더는 반쪽 창에서 시연 칩에 별도 한 줄을 주고, 같은 헤더의 `[모의]` 중복 배지를 없앴다.

### UX 재검토 대상과 남은 실측

변경 화면: 온보딩, 공통 헤더·연결 상태, 시연 복귀 칩, 급증 보류, 계약 체크리스트·편집 시트, 보험 조건 시트, 만기 지난 홈, 출구/근거의 단위·날짜 표기. 조정자가 AGENTS의 UX 게이트에 따라 캡처 후 검토를 요청해야 한다.

실제 SDK의 null 장기 지속·미지원 capability 주입, 공백 회수 재부팅 실측, 날짜 선택기·키보드·반쪽 창의 실제 배치와 계약/보험 편집 터치 흐름, 다른 로케일의 실제 렌더링은 미실측이다. 위 JVM 통과로 이 항목이나 전체 심사관 엣지케이스의 실측 완료를 선언하지 않는다.
