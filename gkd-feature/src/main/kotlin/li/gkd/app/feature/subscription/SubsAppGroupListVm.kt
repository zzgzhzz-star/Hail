package li.gkd.app.feature.subscription

import li.gkd.app.domain.rule.RuleGroupTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import li.gkd.app.a11y.launcherAppId
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.ruleconfig.RuleGroupConfigService
import li.gkd.app.data.edit
import li.gkd.app.domain.rule.toRuleGroupTarget
import li.gkd.app.util.MutexState
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.core.state.Loadable
import li.gkd.app.util.toJson5String
import li.gkd.app.data.appinfo.AppInfoRepository
import li.gkd.db.SubsAppGroupConfig
import li.gkd.db.SubsCategoryConfig
import li.gkd.db.Db

data class SubsAppGroupConfigs(
    val subsConfigs: List<SubsAppGroupConfig>,
    val categoryConfigs: List<SubsCategoryConfig>,
)

data class SubsAppGroupListUiState(
    val subscription: RawSubscription,
    val app: RawSubscription.RawApp,
    val configs: Loadable<SubsAppGroupConfigs>,
)

class SubsAppGroupListVm(
    val route: SubsAppGroupListRoute,
) : BaseViewModel() {
    private val batchMutex = MutexState()
    val batchBusyFlow: StateFlow<Boolean> get() = batchMutex.state

    suspend fun runBatchAction(action: suspend () -> Unit) {
        batchMutex.tryWithStateLock(action)
    }


    private val subscription = requiredSubscription(route.subsItemId)

    private val subsConfigsFlow =
        Db.subsAppGroupConfigDao.queryByAppId(route.subsItemId, route.appId)

    private val categoryConfigsFlow = Db.subsCategoryConfigDao.queryConfig(route.subsItemId)

    val uiState = subscription.buildUiState(
        initialValue = { rawSubscription ->
            buildUiState(rawSubscription, Loadable.Loading)
        },
    ) { rawSubscription ->
        combine(subsConfigsFlow, categoryConfigsFlow) { configs, categoryConfigs ->
            buildUiState(
                rawSubscription = rawSubscription,
                configs = Loadable.Ready(
                    SubsAppGroupConfigs(
                        subsConfigs = configs,
                        categoryConfigs = categoryConfigs,
                    ),
                ),
            )
        }
    }

    private fun buildUiState(
        rawSubscription: RawSubscription,
        configs: Loadable<SubsAppGroupConfigs>,
    ) = SubsAppGroupListUiState(
        subscription = rawSubscription,
        app = rawSubscription.apps.find { it.id == route.appId }
            ?: error("订阅应用不存在: ${route.appId}"),
        configs = configs,
    )

    val focusGroupFlow: StateFlow<Triple<Long, String?, Int>?>?
        field = route.focusGroupKey?.let {
            MutableStateFlow<Triple<Long, String?, Int>?>(
                Triple(
                    route.subsItemId,
                    route.appId,
                    route.focusGroupKey,
                )
            )
        }

    fun consumeFocusGroup() {
        focusGroupFlow?.value = null
    }

    suspend fun buildSelectedGroupsText(selectedKeys: Set<Int>): String =
        withContext(Dispatchers.Default) {
            val app = uiState.value.value?.app ?: error("订阅应用尚未加载")
            val groups = app.groups.filter { it.key in selectedKeys }
            check(groups.isNotEmpty()) { "所选规则已变化，无可复制规则" }
            toJson5String(
                app.copy(
                    groups = groups,
                ),
            )
        }

    suspend fun updateSelectedEnabled(selectedKeys: Set<Int>, enabled: Boolean?): Int {
        val app = uiState.value.value?.app ?: error("订阅应用尚未加载")
        val selectedGroups = app.groups
            .filter { it.key in selectedKeys }
            .map { it.toRuleGroupTarget(route.subsItemId, route.appId) }
            .toSet()
        return RuleGroupConfigService.batchUpdateGroupEnabled(
            selectedGroups,
            enabled,
            launcherAppId,
            AppInfoRepository.systemAppsFlow.value,
        ).size
    }

    suspend fun setGroupEnabled(
        group: RawSubscription.RawAppGroup,
        enabled: Boolean,
    ) {
        RuleGroupConfigService.updateGroupEnabled(
            RuleGroupTarget.App(route.subsItemId, route.appId, group.key),
            enabled,
        )
    }

    suspend fun deleteSelectedGroups(selectedKeys: Set<Int>): Int {
        check(route.subsItemId < 0) { "远程订阅规则不可删除" }
        var deletedSize = 0
        subscription.update { current ->
            current.edit {
                deletedSize = removeAppGroups(
                    appId = route.appId,
                    removeAppIfEmpty = true,
                ) { it.key in selectedKeys }.size
            }
        }
        return deletedSize
    }
}
