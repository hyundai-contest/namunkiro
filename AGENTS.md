# 남은키로 — 에이전트 작업 규칙

- 응답·주석·문서는 한국어, 코드 식별자는 영어.
- 기준 문서: `docs/DEV_PLAN.md`(개발 계획·로직 명세), `docs/PLATFORM_NOTES.md`(Pleos 플랫폼 실측 사실), `design/`(디자인 토큰·화면 명세), `design/spec_v3_content.py`(기획 명세).
- Pleos Vehicle SDK 소스: `docs/sdk-src/vehicle`(Vehicle-2.3.2-sources). 메서드 시그니처는 여기서 확인.
- 공유 계약 파일은 조정자(Claude) 소유. 수정이 필요하면 조정자에게 요청: `core/**`, `vehicle/VehicleGateway.kt`, `domain/model/**`, `domain/EngineContracts.kt`, `domain/odometer/OdometerIngestorContract.kt`, `ui/state/AppUiState.kt`.
- 자기 담당 패키지 밖 파일은 수정하지 않는다. 커밋하지 않는다(조정자가 통합 시 처리).
- 빌드: `android/`에서 `./gradlew :app:assembleDebug`, 단위 테스트 `./gradlew :app:testDebugUnitTest`. Gradle 빌드는 동시에 하나만.
- 에뮬레이터(AVD `Pleos_Connect_v2.1.0`)는 차량 연동·통합 담당만 사용. RAM이 부족하면 멈추니 다른 빌드와 겹치지 않게 한다.
- 시연 원칙: 실제 SDK 값과 모의(시드·가짜) 값을 화면에서 구분한다. 시동 OFF 이후 동작·차량 제어(set) API에 의존하지 않는다.
- **UX 게이트**: UI·디자인·문구를 바꾼 작업은 완료 보고 전에 변경 화면 목록을 남기고, 조정자가 UX 담당 에이전트(상주 워커 또는 `.claude/agents/ux-guardian.md`)에게 검토를 요청한다. 반려(P0)면 수정 후 재검토. 원칙은 `docs/ux/UX_PRINCIPLES.md`를 따른다.
