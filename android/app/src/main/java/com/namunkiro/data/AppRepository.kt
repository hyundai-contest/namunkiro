package com.namunkiro.data

import android.content.Context
import com.namunkiro.domain.model.Contract
import com.namunkiro.domain.model.DailyDistance
import com.namunkiro.domain.model.ExitQuotes
import com.namunkiro.domain.model.InsurancePlan
import com.namunkiro.domain.model.LifeScenario
import com.namunkiro.domain.model.PlannedTrip
import com.namunkiro.domain.model.SplitPlan
import com.namunkiro.domain.odometer.OdometerLedger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/**
 * 앱 전체 저장 상태의 한 장 스냅샷. 화면·엔진은 이것만 읽는다.
 * answeredQuestions: 응답한(또는 '나중에'를 고른) 점진 질문 이름(ui.state.DeferredQuestionKind.name).
 *   data 패키지가 ui에 의존하지 않도록 문자열로 저장한다.
 */
@Serializable
data class AppData(
    val contract: Contract? = null,
    val history: List<DailyDistance> = emptyList(),
    val ledger: OdometerLedger = OdometerLedger(),
    val plannedTrips: List<PlannedTrip> = emptyList(),
    val splitPlans: List<SplitPlan> = emptyList(),
    val scenarios: List<LifeScenario> = emptyList(),
    val exitQuotes: ExitQuotes = ExitQuotes(),
    val insurancePlan: InsurancePlan? = null,
    val answeredQuestions: Set<String> = emptySet(),
    /** 시연 시드가 적용돼 있으면 페르소나 id(화면의 [모의] 표시용). 실사용은 null. */
    val demoPersonaId: String? = null,
)

@Serializable
private data class DemoMeta(val personaId: String? = null)

/**
 * JSON 파일 기반 저장소. 항목별 파일로 나눠 쓰기 때문에 주행 중 잦은 ledger·history 갱신이
 * 계약·견적 파일을 다시 쓰지 않는다. 모든 쓰기는 원자적([JsonFileStore]).
 */
class AppRepository(private val store: JsonFileStore) {

    private val _data = MutableStateFlow(loadAll())
    val data: StateFlow<AppData> = _data.asStateFlow()
    val current: AppData get() = _data.value

    /** 시연 전에 개인 데이터를 별도로 보관한다. */
    fun savePersonalSnapshot() = store.write("personal_backup", AppData.serializer(), current)
    fun restorePersonalSnapshot(): AppData = store.read("personal_backup", AppData.serializer()) ?: AppData()
    fun saveDemoSnapshot() = store.write("demo_backup", AppData.serializer(), current)
    fun restoreDemoSnapshot(): AppData? = store.read("demo_backup", AppData.serializer())

    private fun loadAll(): AppData = AppData(
        contract = store.read(K_CONTRACT, Contract.serializer()),
        history = store.read(K_HISTORY, ListSerializer(DailyDistance.serializer())).orEmpty(),
        ledger = store.read(K_LEDGER, OdometerLedger.serializer()) ?: OdometerLedger(),
        plannedTrips = store.read(K_TRIPS, ListSerializer(PlannedTrip.serializer())).orEmpty(),
        splitPlans = store.read(K_SPLITS, ListSerializer(SplitPlan.serializer())).orEmpty(),
        scenarios = store.read(K_SCENARIOS, ListSerializer(LifeScenario.serializer())).orEmpty(),
        exitQuotes = store.read(K_QUOTES, ExitQuotes.serializer()) ?: ExitQuotes(),
        insurancePlan = store.read(K_INSURANCE, InsurancePlan.serializer()),
        answeredQuestions = store.read(K_ANSWERED, SetSerializer(String.serializer())).orEmpty(),
        demoPersonaId = store.read(K_DEMO, DemoMeta.serializer())?.personaId,
    )

    /** 디스크에서 다시 읽는다(외부에서 파일을 바꾼 경우). */
    fun reload() { _data.value = loadAll() }

    fun saveContract(contract: Contract?) {
        if (contract == null) store.delete(K_CONTRACT) else store.write(K_CONTRACT, Contract.serializer(), contract)
        _data.update { it.copy(contract = contract) }
    }

    /** 수집기 상태와 일별 이력을 함께 저장(수신값 승인 때마다). */
    fun saveOdometer(ledger: OdometerLedger, history: List<DailyDistance>) {
        store.write(K_HISTORY, ListSerializer(DailyDistance.serializer()), history)
        store.write(K_LEDGER, OdometerLedger.serializer(), ledger)
        _data.update { it.copy(ledger = ledger, history = history) }
    }

    fun saveLedger(ledger: OdometerLedger) {
        store.write(K_LEDGER, OdometerLedger.serializer(), ledger)
        _data.update { it.copy(ledger = ledger) }
    }

    fun savePlannedTrips(trips: List<PlannedTrip>) {
        store.write(K_TRIPS, ListSerializer(PlannedTrip.serializer()), trips)
        _data.update { it.copy(plannedTrips = trips) }
    }

    fun upsertPlannedTrip(trip: PlannedTrip) =
        savePlannedTrips(current.plannedTrips.filterNot { it.id == trip.id } + trip)

    fun removePlannedTrip(id: String) = savePlannedTrips(current.plannedTrips.filterNot { it.id == id })

    fun saveSplitPlans(plans: List<SplitPlan>) {
        store.write(K_SPLITS, ListSerializer(SplitPlan.serializer()), plans)
        _data.update { it.copy(splitPlans = plans) }
    }

    fun addSplitPlan(plan: SplitPlan) = saveSplitPlans(current.splitPlans + plan)

    fun saveScenarios(scenarios: List<LifeScenario>) {
        store.write(K_SCENARIOS, ListSerializer(LifeScenario.serializer()), scenarios)
        _data.update { it.copy(scenarios = scenarios) }
    }

    fun saveExitQuotes(quotes: ExitQuotes) {
        store.write(K_QUOTES, ExitQuotes.serializer(), quotes)
        _data.update { it.copy(exitQuotes = quotes) }
    }

    fun saveInsurancePlan(plan: InsurancePlan?) {
        if (plan == null) store.delete(K_INSURANCE) else store.write(K_INSURANCE, InsurancePlan.serializer(), plan)
        _data.update { it.copy(insurancePlan = plan) }
    }

    fun markQuestionAnswered(kindName: String) = saveAnsweredQuestions(current.answeredQuestions + kindName)

    fun saveAnsweredQuestions(names: Set<String>) {
        store.write(K_ANSWERED, SetSerializer(String.serializer()), names)
        _data.update { it.copy(answeredQuestions = names) }
    }

    /** 전체 교체(데모 시드 적용). */
    fun replaceAll(data: AppData) {
        // 전환 중 강제 종료에도 모드별 백업은 지우지 않는다.
        listOf(K_CONTRACT, K_HISTORY, K_LEDGER, K_TRIPS, K_SPLITS, K_SCENARIOS, K_QUOTES, K_INSURANCE, K_ANSWERED, K_DEMO).forEach(store::delete)
        data.contract?.let { store.write(K_CONTRACT, Contract.serializer(), it) }
        store.write(K_HISTORY, ListSerializer(DailyDistance.serializer()), data.history)
        store.write(K_LEDGER, OdometerLedger.serializer(), data.ledger)
        store.write(K_TRIPS, ListSerializer(PlannedTrip.serializer()), data.plannedTrips)
        store.write(K_SPLITS, ListSerializer(SplitPlan.serializer()), data.splitPlans)
        store.write(K_SCENARIOS, ListSerializer(LifeScenario.serializer()), data.scenarios)
        store.write(K_QUOTES, ExitQuotes.serializer(), data.exitQuotes)
        data.insurancePlan?.let { store.write(K_INSURANCE, InsurancePlan.serializer(), it) }
        store.write(K_ANSWERED, SetSerializer(String.serializer()), data.answeredQuestions)
        store.write(K_DEMO, DemoMeta.serializer(), DemoMeta(data.demoPersonaId))
        _data.value = data
    }

    /** 모든 저장 데이터 삭제(초기화). */
    fun clear() = replaceAll(AppData())

    companion object {
        private const val K_CONTRACT = "contract"
        private const val K_HISTORY = "daily_history"
        private const val K_LEDGER = "odometer_ledger"
        private const val K_TRIPS = "planned_trips"
        private const val K_SPLITS = "split_plans"
        private const val K_SCENARIOS = "life_scenarios"
        private const val K_QUOTES = "exit_quotes"
        private const val K_INSURANCE = "insurance_plan"
        private const val K_ANSWERED = "answered_questions"
        private const val K_DEMO = "demo_meta"

        /** 앱 내부 저장소(`files/namunkiro/`)에 둔다. */
        fun create(context: Context): AppRepository =
            AppRepository(JsonFileStore(File(context.applicationContext.filesDir, "namunkiro")))
    }
}
