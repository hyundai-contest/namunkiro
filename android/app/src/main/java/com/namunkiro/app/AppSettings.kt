package com.namunkiro.app

import com.namunkiro.core.time.AppClock
import com.namunkiro.data.AppRepository
import com.namunkiro.domain.model.InsurancePlan
import com.namunkiro.ui.state.ContractField

/** 계약·보험 편집의 저장 경계. 저장소 StateFlow 변경이 ViewModel 엔진 재계산을 유발한다. */
class AppSettings(private val repo: AppRepository, private val clock: AppClock) {
    fun updateContractField(field: ContractField, value: String?): Boolean {
        val current = repo.current.contract ?: return false
        val next = AppPolicies.editContract(current, field, value, clock.today()) ?: return false
        repo.saveContract(next)
        return true
    }
    fun setInsurancePlan(plan: InsurancePlan?): Boolean {
        if (plan != null && !AppPolicies.validInsurancePlan(plan)) return false
        repo.saveInsurancePlan(plan)
        return true
    }
}
