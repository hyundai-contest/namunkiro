# 남은키로 화면 명세 · 신청서 v3

산출물: `mockup.html`, `design-tokens.md`, 이 문서. 기준 원문은 `spec_v2_content.py`와 이를 덮어쓰는 `spec_v3_content.py`; v3의 F1–F7와 시나리오 1–6을 최종값으로 삼는다. 기존 Main.dc.html은 구조 참고만 했다. coordinator가 전달한 실측 앱 창은 1682×1210dp, 반쪽 창은 약 840×1210dp이다. 전체 AVD 2560×1440을 앱이 그리지 않는다. 시스템 영역 회색 도식은 프레임 밖에 있으며 앱 상세에는 로고/제조사 자산이 없다.

## 실행과 공통 상태

HTML 직접 열기. 상단 프레임 탭 또는 좌우 방향키로 12개 프레임 전환. 기어 P/D, UX 제한, +10km, 이상값 수신, 다크/라이트, 초기화는 프레임 밖 시연 제어다. 앱의 홈/이번 주/예측/출구/계약 탭은 프레임 안 76dp 이상 타깃이다. 반쪽 홈은 1열 스크롤로 접힌다. 글자와 데이터는 기본 시스템 폰트를 사용하며 외부 의존성 없다.

모든 프레임의 수치는 시나리오 fixture이며 한 사람의 연속 타임라인이 아니다. 홈·이번 주·예측은 김 대리, 회복 어려움은 이 과장 인도90일, 출구는 이 과장 1년4개월, 보험은 박 과장 10월 초이다. 브라우저 내 상태는 탭 이동·P/D 잠금 중 유지하고 새로고침하면 초기화한다. 실제 앱은 Room/DataStore 및 SavedStateHandle로 입력 초안·선택 화면·마지막 승인값을 복원해야 한다. HTML 계산은 UX 검토용이며 생산용 예측/계약 계산 엔진이 아니다.

안전 우선순위: `gear == P && uxRestrictions == unrestricted`일 때만 상세 표시. D/R/N, 기어 불명, UX 제한 중 또는 확인 불가이면 잠금. 입력 이벤트마다 초안 보관 후 잠금. 주행 중 숫자·비용·예측·팝업·음성·잔여거리 알림 숨김. 데이터 수신은 가능하지만 이상값 팝업도 정차까지 보류. 정차 후 이전 화면 복원; 실제 앱은 변경된 UX 상태를 재확인한다.

공통 데이터: `gear`, `gearReceivedAt`, `uxRestrictions`, `uxReceivedAt`, `currentRoute`, `drafts`, `theme`, `windowWidthDp`, `windowHeightDp`, `insets`, `dataSource`, `lastReceivedAt`, `simulationMode`. nullable 상태는 unknown/unsupported/error/stale을 보존한다. `value=0`으로 대체하지 않는다. 출처 배지는 값 또는 의미상 같은 숫자 그룹에 부착한다.

## 01 · 30초 시작 / F1

3단계 진행선과 현재 입력 카드, 차량이 채운 시작 계기판, 첫 기본 예산 미리보기. 단계1 만기일 2030.10.31 → 단계2 총약정60,000km → 단계3 초과단가100원/km. 시작 계기판12km [관측], 첫 예산287km [추정]. 다음·이전으로 이동, 3단계 완료는 홈으로 간다. 유효한 날짜/양수 거리·단가 필수. 공제·인수가·해지율은 여기서 묻지 않는다.

데이터: `contractEndDate`, `contractStartDate`(실제 앱은 인도일/시작일 확인 필요), `totalAllowanceKm`, `excessRateWonPerKm`, `startingOdometerKm`, `startingOdometerTimestamp`, `odometerCapability`, `setupStep`, `setupDraft`, `contractWeeks`, `baseWeeklyBudgetKm`. 시작값 미수신이면 로딩/연결 확인 표시, 0km 금지. 명세의287은 약209주 fixture이며 실제 구현은 정확한 기간·부분 주 처리 필요. HTML에서 입력을 바꿔도 완료 후 홈은 고정 시나리오 fixture로 이동한다.

## 02 · 홈 / F2·F3 / 시나리오2

주간 예산 링: 이번 주244km, 사용186km [관측], 남은58km. 지난주330−기본287=43km초과 → 이번 주287−43=244km. 누적 잔고−807km [추정]. 만기 거리 정산 약24만 원, 모의 범위200,000–290,000원. 손상·감가는 미확인 별도. 지난주 같은 시점보다18% 많음. ‘만기 예측’·‘이번 주 계획’ 연결.

데이터: `weekStart`, `weekEnd`, `baseBudgetKm=287`, `carryoverKm=-43`, `effectiveBudgetKm=244`, `observedWeekKm=186`, `remainingKm=58`, `previousWeekKm=330`, `samePointChangePct=18`, `cumulativeBalanceKm=-807`, `settlementLowWon`, `settlementHighWon`, `settlementCentralWon=240000`, `otherSettlementStatus`. +10km는 사용/잔여/누적 잔고·모의 거리비용을 갱신한다. 예상비용은 누적 잔고×단가가 아니라 미래 페이스 기반이다. 회복 화면과 출구 화면의471만/314만은 다른 시점 fixture.

## 03 · 이번 주 / F2·F4 / 시나리오3

요일별 막대는 관측/평소 예상/등록 목적지를 중립 채움/테두리/사선 청색으로 구분. km 세로축0·250·500, 요일 가로축, 점선은244÷7≈35km 일평균 예산. 모의 요일값 월42/화48/수44/목52=관측186, 금36/일42=예상78, 토 강릉왕복420으로 대체하여 합계684km. 평소 토요일 기준선에420을 중복 가산하지 않는다.

등록 목적지 토글 ON:684−244=440km초과. 다음 주 반영 시287−440=−153km, 4주이면177km/주, 8주이면440÷8=55씩 →232km/주. 선택 즉시 향후 예산과 설명 변경. ‘분할 계획 저장’은 피드백 표시; 실제 주 마감 전에는 예정 계획이다. OFF는264km, 초과20km로 재계산한다. 소수 내부 유지·표시 반올림, 마지막 분할 주에 오차 보정 필요.

데이터: `dailyObservedKm[7]`, `dailyBaselineKm[7]`, `destinationId`, `destinationDate`, `destinationRoundTripKm=420`, `destinationSource`, `destinationEnabled`, `tripOverlapKey`, `forecastWeekKm=684`, `expectedOverrunKm=440`, `splitWeeks=1|4|8`, `futureBudgetSchedule[]`, `planSavedAt`, `weekCloseStatus`. 앱 꺼짐 동안 회수된 총거리는 요일별 학습값으로 임의 분배하지 않는다.

## 04 · 예측 / F4

만기 예상 띠 그래프(하한–상한), 약정60,000km 기준선, 시간축 계약시작→현재→2030.10만기, 누적km축 표시. 모의 초기 범위62,000–62,900km는 홈20만–29만원과 일치한다. 평일 반복210/주말77km는 모의 기준선으로 표시. 입력: 통근증가km/일, +20 프리셋, 여행은 다른 차로. 입력 변경 시 모의 범위·만기 원단위 비용 즉시 재계산. 현재 HTML 띠 모양은 고정 개념도이고 숫자가 민감도에 반응한다; 실제 구현은 전체 시계열을 재계산해 띠를 이동한다.

데이터: `forecastSeries[{date,lowKm,centralKm,highKm}]`, `allowanceKm`, `forecastMethod`, `sampleDays`, `confidenceReason`, `weekdayRecurringKm`, `weekendKm`, `scenarioStartDate`, `scenarioEndDate`, `commuteExtraKmPerDay`, `commuteDaysPerWeek`, `otherCarTripIds`, `deductionKm`, `deductionMethod`. 초기 데이터 부족은 ‘계획값 기반’, 수신 실패는 마지막 계산시각, 예측 오차 누적은 별도 신뢰도. HTML의 +20은 모의 하한+500/상한+1300km, 다른 차 여행은420km 제외이며 실제 기간 계산이 아니다.

## 05 · 페이스 회복 어려움 / F4 / 시나리오4

이 과장 90일 관측6,600km / 예산3,696km / 잔고−2,904km. 필요273km/주와 최근513km/주를 0에서 시작하는 동일 축 가로 막대로 비교. 현재 대비약47% 감축 필요. 만기 거리 정산 약471만원, 모의430만–520만원. 판정 사유와 계약변경·인수면제 미확인 표시. ‘계약 조건’·‘출구 계획’ 버튼.

데이터: `elapsedDays=90`, `observedTotalKm=6600`, `accruedBudgetKm=3696`, `cumulativeBalanceKm=-2904`, `requiredWeeklyKm=273`, `recentWeeklyKm=513`, `recoveryStatus`, `recoveryReason`, `decisionThresholdConfig`, `forecastSettlementWon=4710000`, `amendmentAllowed`, `buyoutDistanceWaiver`. 회복 판정 임계값은 명세에 수치가 없어 구현 시 합의/검증해야 하며 mockup은 해당 시나리오만 표시한다. 판정을 확정 청구·무조건 인수 추천으로 표현하지 않는다.

## 06 · 출구 달력 + 결정 / F6 / 시나리오5

왼쪽 달력:2028.11.01 해지율35→30%, 자유반납일·만기통지기한·견적만료일은 미확인,2030.10.31만기. 정차 중 날짜 이벤트당1회 안내를 실제 앱에서 저장. 오른쪽 비교 표: 같은 종료일2030.10.31 기준 지금 승계/지금 해지/만기 반납/인수후보유/인수후매각 다섯 선택지. 비교 계산 시점은 이 과장1년4개월,31,240km이며90일 진단과 혼합하지 않는다.

반납 약314만원은 거리 정산만; 손상·감가 미확인. 보유 순비용176만원·필요현금1,926만원. 승계300만원 [입력]. 해지·매각 순비용은 미확인. 매각은 입금 전1,926만원 현금이 필요할 수 있음. 현금은 지급시점과 자금조달 조건에 따라 달라지므로 확정치로 취급하지 않는다. 표 아래 미확인 비용 때문에 결론 변경 가능 표시. 종료일자산가치1,750만원·승계견적300만원 수정 시 `1926−자산가치`와 승계견적을 비교해 조건부 최저값 강조. 자산가치를1,500만원으로 내리면 보유426만원 → 승계300만원이 낮아져 시연 가능.

데이터: `comparisonAsOfDate`, `commonEndDate`, `commonCostScope`, `odometerKm=31240`, `exitEvents[{type,date,source,notifiedAt}]`, `terminationRates[]`, `returnRightDate`, `noticeDeadline`, `quoteExpiresAt`, `buyoutPriceWon=18000000`, `buyoutAncillaryCostWon=1260000`, `terminalAssetValueWon=17500000`, `transferQuoteWon=3000000`, `optionCosts[{netCost,requiredCash,cashDate,unknownItems,assumptions}]`, `damageCost?`, `depreciationCost?`, `salesFees?`, `replacementMobilityCost?`, `remainingRent?`, `quoteUpdatedAt`.

명세 금액 재현을 위한 단순 모의: 보유1,926−잔존1,750=176만원. 공통 월납입/대체 이동비 제외, 실제 엔진은 옵션별 잔여월납입·대체차량·할인율·세금·잔존자산·면제조항을 같은 종료일 범위로 정규화해야 한다. 미확인 항목은 null이며0합산 금지. 모든 비용을 아는 상태의 전역 최저라고 단정하지 않고 ‘입력 조건에서’라는 한정 문구 사용.

## 07 · 계약 조건 카드 / F5

기본 입력 만기일/총약정/단가, 시작값관측, 거리 제한방식 select. 점진 항목 공제/반납권/인수면제/해지율은 미확인. 인수가1,800만원은 입력 모의. 공제 버튼은 지연 질문으로 이동. 무제한/거리 제한 없는 금융리스 선택 시 초과료 비적용 문구. 현재 HTML 홈은 고정 fixture이므로 제한방식 선택 후 홈까지 전파하지 않는다; 실제 구현은 모든 거리초과료 카드에서 비적용 처리해야 한다.

데이터: `contractType`, `distanceLimitType`, `endDate`, `allowanceKm?`, `rateWonPerKm?`, `returnRight`, `deductionMethod`, `deductionKm?`, `buyoutPriceWon?`, `buyoutWaiver?`, `terminationRateSchedule?`, `fieldVerification[{field,source,verifiedAt}]`. null과false/0 구분. 면제 미확인은 인수 비교 경고, 무제한은 초과료 산식 자체 제외.

## 08 · 보험 특약 / F7 / 시나리오6

박 과장 보험1년·1만km 이하·기본주192km,10월초누적8,820km. 링 주사용150km는 모의 추가값. 1만구간잔여1,180km, 현재225km/주면11월첫주초과 예상. 1.2만구간은남은13주동안245km/주이하로가능. 구간 버튼으로 잔여와 설명 변경. 환급액·인정 조건 미확인; 렌트 초과료를 보험 계산에 사용하지 않는다.

데이터: `policyStartDate`, `policyEndDate`, `policyStartOdometerKm`, `policyObservedKm=8820`, `selectedBandUpperKm=10000|12000`, `baseWeeklyBudgetKm=192`, `recentWeeklyKm=225`, `weeksRemaining=13`, `crossingDateRange`, `bandRefundTable?`, `insurerRulesStatus`, `weeklyUsageKm=150`. 목업의 문구는 원문 시나리오값이며245×13은 표시 반올림값이다.

## 09 · 주행 중 잠금 / 공통 안전

별도 프레임과 전역 잠금 상태. 문구 ‘주행 중에는 화면을 쉬어 갑니다’, ‘정차하면 보던 화면에서 이어집니다’, 입력 보관. 숫자·버튼·비용·팝업 없음. 프레임 밖 기어 P 복원 또는 UX 제한 해제로 복귀. 명시 잠금 프레임09는 P이어도 예시 잠금을 유지하므로 다른 탭을 선택하면 해제. 실제 전역 D/제한 상태는 프레임 탭 이동으로 우회 불가.

데이터: `lockReason`, `previousRoute`, `savedDraft`, `gear`, `uxRestrictions`. HTML의 UX 토글은 해제/확인불가 두 상태 시연; 실제 앱은 제한됨/불명도 동일 잠금. 앱 메뉴는 잠금 동안 제거한다.

## 10 · 이상값 보류 / 공통 데이터 품질

정상31,240km08:12:00 → 원시32,240km08:13:00,1분+1,000km는250km/h×시간(약4.17km) 상한을 초과해보류. 마지막 정상값 유지, 원시/승인 구분. 정상값 재수신 버튼은31,250km를 가정하여+10km만 홈 사용량에 추가, 중복1,000km는미가산. 기본 샘플과 홈 fixture는 서로 다른 시점의 품질 설명용 예시다. 주행 중 이상값 버튼은 상세를 열지 않는다.

데이터: `rawOdometerValue`, `rawUnit`, `rawReceivedAt`, `approvedOdometerKm`, `approvedAt`, `deltaKm`, `elapsedSeconds`, `validationState`, `holdReason`, `maxPlausibleSpeedKmh=250`, `subscriptionState`, `capabilityState`, `dedupeKey`. 역행·단위 불일치·미지원·수신실패도 같은 패턴. 재조회 실패는 보류 유지·오류시각 표시; 강제승인 버튼 없음. API 재구독 후 동일값 중복합산 금지.

## 11 · 지연 질문 / F1·F5

‘공제 거리를 알면 더 정확해져요’. 총거리공제km 입력,0은공제없음,빈값은미확인. 적용은 계약카드로 이동하며 입력배지 표시. 나중에는 홈으로 복귀. HTML에선 독립 프레임으로 반복 방문 가능; 실제 앱은 첫 만기정산 진입에서 1회 질문 후dismissed/answered상태저장, 주행 중 노출 금지. 단일 항목만 물어 온보딩을 늘리지 않는다.

데이터: `deductionKm?`, `deductionMethod`, `questionEligible`, `questionShownAt`, `questionDismissedAt`, `questionAnsweredAt`. 실제 엔진은 공제방식/적용주기를 확인 후 비용 재계산. HTML 적용은 계약카드에 값을 반영하며 예측 fixture에는 전파하지 않는다.

## 12 · 반쪽 창 홈

840×1210dp. 헤더와5개탭유지, 홈링295dp, 주요수치64sp, 카드1열. 첫 카드에링·이번주244·사용186·지난주이월을 표시하고 그 아래 만기비용·누적잔고로 스크롤. 가로 홈의 비교18% 부가행은 생략하되 핵심데이터는 모두유지. 일반 홈과 동일상태를공유하므로+km동시반영. 데이터는02와동일. 실제 Compose는 BoxWithConstraints로열수변경, LazyColumn/verticalScroll로작은창접근성확보.

## SDK 연결·검증 체크

Odometer capability/read/subscribe/unsubscribe와 CAR_DRIVE; GEAR_SELECTION과 CAR_POWERTRAIN; CarUxRestrictions listener; NaviHelper destination callback을 ViewModel 상태로 합성한다. 서버·시동OFF작업·차량set API 없이 동작. 원시값/승인값·단위·시각은 Room, 시연설정·질문상태는 DataStore. 원본 위치 경로는 저장하지 않는다.

시연 확인 순서: 홈58km →+10수신48km →이번주강릉684·8주232 →예측생활변화 →출구자산1750→1500으로추천변경 →입력중D잠금/P복원 →UX확인불가잠금 →이상값보류/정상재수신 →라이트/반쪽홈. 실제 출시 전 실기 인셋·fontScale·터치영역·UX불명·수신실패·월요일0시이월·분할반올림·재시작중복방지·중복목적지·계약무제한 전파 검증이 필요하다.

## ?? ??

Aside ?????? 12? ??? ??? ?? ?? ?? ??. +10km ? ?58?48km, P?D?? ?? ???P???48km ??, UX???? ??, ??684km?8?232km?????153km, ??? ?? ????+10? ??, ????1750?1500 ??? ??176?426?? ? ??300?? ?? ??? ????. ?? ?? ? ?? ?? ?? ???? ?? ??? ????. ?? HTTP ??? QA?? ???? HTML ???? ?? ??.
