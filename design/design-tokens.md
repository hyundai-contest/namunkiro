# 남은키로 차량 UI 토큰 · v3

기준: spec_v2_content.py 및 spec_v3_content.py의 최종 F1–F7. v2의 주 209km·11월 29일 예시는 사용하지 않는다. 브랜드 로고·차량 제조사 그래픽 없음. 외부 네트워크·이미지·라이브러리 없이 mockup.html을 직접 연다.

## 창과 안전 영역

AVD 2560×1440px, density 160: 1dp=1px, 기본 fontScale=1에서 1sp=1px. coordinator 실측: 일반 앱은 대략 (853,90)–(2535,1300)의 오른쪽 창 **1682×1210dp**를 사용한다. 전체 화면을 앱 캔버스로 가정하지 않는다. 왼쪽 Driving view, 상단 상태바, 하단/측면 런처는 시스템이 그린다. HTML 바깥 회색 도식은 자리표시이며 실제 시스템 UI를 재현하지 않는다.

| 항목 | 가로 창 | 반쪽 세로 창 |
|---|---:|---:|
| 앱 프레임 | 1682×1210dp | 840×1210dp |
| 콘텐츠 좌우 여백 | 48dp | 32dp |
| 콘텐츠 상하 여백 | 36dp | 28dp |
| 헤더 | 86dp | 80dp |
| 내비게이션 버튼 | 최소 150×76dp | 각 가용 너비/5, 높이 76dp |
| 주요 열 | 2열, 24dp 간격 | 1열, 18dp 간격 |
| 스크롤 | 콘텐츠 오버플로 시 세로 | 홈 콘텐츠 세로 |

실기에서는 현재 WindowMetrics와 WindowInsets로 측정한다. 이미 앱 창 바깥에 제외된 시스템 영역을 중복으로 패딩하지 않는다. 내부 여백은 추가적인 콘텐츠 여백이다. 반쪽 창 임계값은 maxWidth < 1000dp로 제안하며 방향 이름보다 실제 창 너비를 따른다. 글자 확대 시 높이를 고정하지 말고 세로 스크롤과 최소 터치 크기를 유지한다. HTML 축소는 리뷰 편의 기능이며 실제 dp 터치 크기를 표현하는 물리적 시연이 아니다.

## 색

단일 강조색 계열은 청색. 상태는 색만으로 구분하지 않고 문구·선·패턴을 병용한다.

| Compose 명칭 / CSS | Dark | Light | 용도 |
|---|---|---|---|
| background / --bg | #101214 | #F4F5F7 | 창 배경 |
| surface / --surface | #191C20 | #FFFFFF | 카드 |
| surfaceContainer / --raised | #22262B | #E9EDF2 | 보조 버튼·선택 배경·링 트랙 |
| outline / --line | #393E45 | #BDC5CF | 1dp 경계·구분선 |
| onBackground / --text | #F1F3F5 | #171A20 | 주요 텍스트 |
| onSurfaceVariant / --muted | #AFB6BF | #555E69 | 단위·축·설명 |
| primary / --accent | #81B5FF | #235DA8 | 핵심 수치·선택·주요 버튼 |
| onPrimary / --ink | #102039 | #FFFFFF | 강조 버튼 텍스트 |

포커스: #62B6FF 4dp 외곽선, offset 4dp. 선택은 강조 테두리 3dp와 텍스트로 표시. 비정상/음수도 별도 빨강 없이 부호·‘보류’·설명으로 의미를 전달한다.

## 타이포

플랫폼 기본 한글 sans-serif, HTML system-ui → Malgun Gothic 폴백. Compose FontFamily.SansSerif, 숫자 `fontFeatureSettings="tnum"`. 숫자를 탭형으로 고정하고 천 단위 구분, U+2212 마이너스, 소수는 필요할 때만 쓴다.

| 스타일 | 크기 / 권장 행간 | 굵기 | 적용 |
|---|---|---|---|
| displayMetric | 78sp / 90sp | 650 또는 SemiBold | 링 잔여·핵심 금액 |
| headline | 48sp / 59sp | Bold | 화면 제목 |
| metricMedium | 50sp / 62sp | SemiBold | 보조 숫자 |
| brand | 34sp / 46sp | Bold | 텍스트 앱명 |
| title | 30sp / 42sp | SemiBold | 카드 제목 |
| body / button | 28sp / 41sp | Normal / Medium | 본문·입력·행동 |
| secondary / axis | 24sp / 35sp | Normal | 부가 설명·차트 축 |
| badge / fine | 22sp / 32sp | Medium / Normal | 출처 배지 |
| debug | 20sp / 29sp | Normal | 시연 하단 상태만 |

세로 홈: headline 38sp, displayMetric 64sp, metricMedium 42sp. 축소된 브라우저 보기에서도 원본 프레임 수치는 동일하다. 긴 계약 문구는 줄바꿈하고 숫자+단위는 한 묶음으로 배치한다. 화면당 핵심 수치 1개를 가장 크게 둔다.

## 치수와 컴포넌트

간격 스케일: 4, 8, 12, 16, 20, 24, 30, 32, 36, 48dp. 모서리: 배지 7dp, 버튼/입력 12dp, 카드 20dp. 그림자·그라디언트·장식 아이콘을 사용하지 않는다.

| 컴포넌트 | 규격 |
|---|---|
| Card | surface, outline 1dp, radius 20dp, padding 30dp (세로 24dp), 카드 간 24dp |
| PrimaryButton | minHeight 76dp, padding H24/V12dp, radius 12dp, primary/onPrimary, 28sp |
| SecondaryButton | 동일 크기, surfaceContainer/outline, 선택 시 3dp primary 테두리 |
| AppTab | minHeight 76dp, 가로 minWidth 150dp, 세로 균등 분배, 12dp 간격(세로 8dp) |
| Input/Select | minHeight 76dp, H24/V12dp, 28sp, 항상 보이는 label, 단위 명시 |
| Badge | 22sp, H10/V3dp, radius 7dp, 1dp outline, 비인터랙티브 |
| DataRow | V16dp, bottom divider 1dp, 마지막 행은 선 없음 |
| Notice | surfaceContainer, leading primary 4dp, H24/V16dp, 25sp |
| BudgetRing | 390×390dp (세로 295), stroke 18dp, round cap, 중앙 수치·단위·라벨 |
| DailyBar | 55dp 폭, radius top 5dp, 0–500km 공통 축, 260dp 플롯 높이 |
| ForecastBand | 720×340dp viewBox, axis 2dp, trend 5dp, primary 24% fill, 기준선 dash 10/8dp |
| Timeline | outline 2dp 세로선, 16dp primary 점, 이벤트 간 22dp |
| ComparisonTable | 25sp 셀, 22sp 헤더, V16/H12dp, 1dp 행 구분, 선택 배경 surfaceContainer |

링의 진행률은 사용/예산이며 0–1로 clamp. 잔여는 음수도 표시 가능, 초과 문구를 반드시 추가. 홈 244예산·186사용·58잔여, 주 기본287은 링 아래 이월 산식으로 구분. 막대: 달린 거리는 중립색 채움, 평소 예상은 빈 테두리, 등록 목적지는 청색 사선 패턴으로 구분한다. 요일·거리·0축·250/500km·예산 일평균 기준선을 명시한다. 띠 그래프는 신뢰구간이라는 통계적 주장 대신 ‘예상 하한–상한’으로 표기한다.

## 배지 의미와 금액 규칙

- 관측: 차량에서 받은 값. 합성된 예산과 비용에는 사용하지 않는다.
- 입력: 사용자 확인값·내비 등록 예정값. 내비 수신의 실제 출처 필드도 저장한다.
- 추정: 예산·미래 거리·환산 비용·판정. 기초 관측과 구분한다.
- 미확인: null. 0과 다르며 합산·최저비용 판정에서 누락 여부를 노출한다.
- 모의: 이 HTML의 전체 데이터와 민감도 계산. 실제 차량/API 연결이 아님을 프레임에도 표시한다.

금액은 홈에서 ‘약 24만 원’, 바로 아래 200,000–290,000원 범위를 병기. 원본 명세에 없는 범위·분해 숫자는 모의 예시. 누적 −807km×100원을 만기 비용으로 오인하지 않도록 누적 잔고와 미래 페이스 추정값을 별도 카드에 둔다. 출구는 같은 종료일·동일 비용 포함 범위로 순비용/필요 현금/미확인 항목을 동시에 보여 준다.
