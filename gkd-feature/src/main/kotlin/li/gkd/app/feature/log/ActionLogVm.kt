package li.gkd.app.feature.log

import li.gkd.app.data.ruleconfig.RuleGroupConfigService
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import li.gkd.app.data.ExcludeData
import li.gkd.app.data.RawSubscription
import li.gkd.app.domain.rule.RuleGroupPolicy
import li.gkd.app.domain.rule.RuleGroupTarget
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.data.subscription.SubscriptionState
import li.gkd.app.data.appinfo.AppInfoRepository
import li.gkd.app.a11y.launcherAppId
import li.gkd.db.ActionLog
import li.gkd.db.Db
import li.gkd.db.SubsGroupConfig
import li.gkd.db.RuleGroupType

data class ActionLogListItem(
    val actionLog: ActionLog,
    val group: RawSubscription.RawGroupProps?,
    val rule: RawSubscription.RawRuleProps?,
    val subscription: RawSubscription?,
)

data class ActionLogDialogState(
    val actionLog: ActionLog,
    val subsConfig: SubsGroupConfig?,
    val globalAppChecked: Boolean?,
    val activityDisabled: Boolean,
)

class ActionLogVm(
    val route: ActionLogRoute,
) : BaseViewModel() {

    val pagingDataFlow = Pager(PagingConfig(pageSize = 100)) {
        when {
            route.subsId != null -> Db.actionLogDao.pagingSubsSource(route.subsId)
            route.appId != null -> Db.actionLogDao.pagingAppSource(route.appId)
            else -> Db.actionLogDao.pagingSource()
        }
    }
        .flow
        .cachedIn(scope)
        .combine(SubscriptionState.subsMapFlow) { pagingData, subsMap ->
            pagingData.map { actionLog ->
                val subscription = subsMap[actionLog.subsId]
                val group = if (actionLog.groupType == RuleGroupType.App) {
                    subscription?.apps
                        ?.find { app -> app.id == actionLog.appId }
                        ?.groups
                        ?.find { group -> group.key == actionLog.groupKey }
                } else {
                    subscription?.globalGroups?.find { group -> group.key == actionLog.groupKey }
                }
                val rule = group?.rules?.run {
                    if (actionLog.ruleKey != null) {
                        find { rule -> rule.key == actionLog.ruleKey }
                    } else {
                        getOrNull(actionLog.ruleIndex)
                    }
                }
                ActionLogListItem(actionLog, group, rule, subscription)
            }
        }

    private val selectedActionLogFlow = MutableStateFlow<ActionLog?>(null)

    val dialogStateFlow = selectedActionLogFlow.flatMapLatest { actionLog ->
        if (actionLog == null) {
            flowOf(null)
        } else {
            val configFlow = if (actionLog.groupType == RuleGroupType.App) {
                Db.subsAppGroupConfigDao.queryConfig(
                    actionLog.subsId,
                    actionLog.appId,
                    actionLog.groupKey,
                )
            } else {
                Db.subsGlobalGroupConfigDao.queryConfig(
                    actionLog.subsId,
                    actionLog.groupKey,
                )
            }
            combine(configFlow, SubscriptionState.subsMapFlow) { subsConfig, subsMap ->
                val subscription = subsMap[actionLog.subsId]
                val exclude = ExcludeData.parse(subsConfig?.exclude)
                val globalAppChecked = if (actionLog.groupType == RuleGroupType.Global) {
                    subscription?.globalGroups
                        ?.find { group -> group.key == actionLog.groupKey }
                        ?.let { group ->
                            RuleGroupPolicy.getGlobalGroupChecked(
                                subscription,
                                exclude,
                                group,
                                actionLog.appId,
                                launcherAppId,
                                AppInfoRepository.systemAppsFlow.value,
                            )
                        }
                } else {
                    null
                }
                ActionLogDialogState(
                    actionLog = actionLog,
                    subsConfig = subsConfig,
                    globalAppChecked = globalAppChecked,
                    activityDisabled = actionLog.activityId?.let { activityId ->
                        exclude.activityIds.contains(actionLog.appId to activityId)
                    } ?: false,
                )
            }
        }
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        null,
    )

    fun showActionLog(actionLog: ActionLog) {
        selectedActionLogFlow.value = actionLog
    }

    fun dismissActionLog() {
        selectedActionLogFlow.value = null
    }

    suspend fun deleteLogs() {
        when {
            route.subsId != null -> Db.actionLogDao.deleteSubsAll(route.subsId)
            route.appId != null -> Db.actionLogDao.deleteAppAll(route.appId)
            else -> Db.actionLogDao.deleteAll()
        }
    }

    suspend fun toggleGlobalAppExclusion() {
        val state = dialogStateFlow.value ?: return
        val checked = state.globalAppChecked ?: return
        val actionLog = state.actionLog
        RuleGroupConfigService.updateGroupEnabled(
            RuleGroupTarget.Global(actionLog.subsId, actionLog.groupKey, actionLog.appId),
            !checked,
        )
    }

    suspend fun toggleActivityExclusion() {
        val actionLog = dialogStateFlow.value?.actionLog ?: return
        val activityId = actionLog.activityId ?: return
        val target = if (actionLog.groupType == RuleGroupType.App) {
            RuleGroupTarget.App(actionLog.subsId, actionLog.appId, actionLog.groupKey)
        } else {
            RuleGroupTarget.Global(actionLog.subsId, actionLog.groupKey)
        }
        RuleGroupConfigService.toggleActivityExclusion(target, actionLog.appId, activityId)
    }
}
