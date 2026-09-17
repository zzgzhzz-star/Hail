package li.gkd.app.feature.subscription

import androidx.activity.compose.BackHandler
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import li.gkd.app.MainViewModel
import li.gkd.app.data.ExcludeData
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.ruleconfig.RuleGroupConfigService
import li.gkd.app.domain.rule.RuleGroupTarget
import li.gkd.app.data.edit
import li.gkd.app.ui.component.FullscreenDialog
import li.gkd.app.ui.component.MultiTextField
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.component.TowLineText
import li.gkd.app.ui.component.useSubs
import li.gkd.app.ui.component.useSubsGroup
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.data.subscription.SubscriptionRepository
import li.gkd.app.ui.share.launchUiAction
import li.gkd.app.ui.share.launchUi
import li.gkd.app.util.throttle
import li.gkd.app.util.ToastUtils.toast
import li.gkd.db.SubsGroupConfig

private data class ExcludeEditSession(
    val groupState: RuleGroupTarget,
    val originalConfig: SubsGroupConfig?,
)

class RuleGroupState(
    private val mainVm: MainViewModel,
) {
    private val showGroupFlow = MutableStateFlow<RuleGroupTarget?>(null)
    private val dismissGroupShow = { showGroupFlow.value = null }

    fun showGroup(state: RuleGroupTarget) {
        showGroupFlow.value = state
    }

    private val excludeEditSessionFlow = MutableStateFlow<ExcludeEditSession?>(null)
    private val excludeTextFlow = MutableStateFlow("")
    private val dismissExcludeGroupShow = {
        excludeEditSessionFlow.value = null
        excludeTextFlow.value = ""
    }
    private fun getChangedExcludeData(session: ExcludeEditSession): ExcludeData? {
        val oldValue = ExcludeData.parse(session.originalConfig?.exclude)
        val newValue = ExcludeData.parse(
            excludeTextFlow.value,
            requireNotNull(session.groupState.appId),
        )
        return newValue.takeIf { it != oldValue }
    }

    private fun openExcludeEditor(state: RuleGroupTarget) {
        dismissGroupShow()
        if (state.appId == null) {
            mainVm.navigatePage(
                SubsGlobalGroupExcludeRoute(
                    state.subsId,
                    requireNotNull(state.groupKey),
                ),
            )
            return
        }
        mainVm.scope.launchUi {
            val originalConfig = RuleGroupConfigService.queryGroupConfig(state)
            excludeTextFlow.value = ExcludeData.parse(originalConfig?.exclude)
                .stringify(state.appId)
            excludeEditSessionFlow.value = ExcludeEditSession(state, originalConfig)
        }
    }

    private suspend fun resetGroupSwitch(state: RuleGroupTarget): String {
        RuleGroupConfigService.updateGroupEnabled(state, null)
        return if (state is RuleGroupTarget.Global && state.pageAppId != null) {
            "已重置局部开关至默认值"
        } else {
            "已重置开关至默认值"
        }
    }

    private suspend fun deleteGroup(
        state: RuleGroupTarget,
    ) {
        SubscriptionRepository.update(state.subsId) { subscription ->
            when (state) {
                is RuleGroupTarget.Global -> subscription.edit {
                    if (removeGlobalGroups { it.key == state.groupKey }.isEmpty()) {
                        error("规则已不存在")
                    }
                }

                is RuleGroupTarget.App -> subscription.edit {
                    if (subscription.apps.none { it.id == state.appId }) {
                        error("应用规则已不存在")
                    }
                    if (removeAppGroups(state.appId) { it.key == state.groupKey }.isEmpty()) {
                        error("规则已不存在")
                    }
                }
            }
        }
    }

    private suspend fun saveChangedExclude(
        session: ExcludeEditSession,
        excludeData: ExcludeData,
    ) {
        RuleGroupConfigService.replaceExclude(
            target = session.groupState,
            expected = ExcludeData.parse(session.originalConfig?.exclude),
            value = excludeData,
        )
    }

    @Composable
    fun Render() {
        val showGroupState = showGroupFlow.collectAsStateWithLifecycle().value
        val showSubs = useSubs(showGroupState?.subsId)
        val showGroup = useSubsGroup(showSubs, showGroupState?.groupKey, showGroupState?.appId)
        if (showGroupState?.groupKey != null && showSubs != null && showGroup != null) {
            val subsConfigFlow = remember(showGroupState) {
                RuleGroupConfigService.groupConfig(showGroupState)
            }
            val subsConfig = subsConfigFlow.collectAsStateWithLifecycle(null).value
            val excludeData = remember(subsConfig?.exclude) {
                ExcludeData.parse(subsConfig?.exclude)
            }
            RuleGroupDialog(
                subs = showSubs,
                group = showGroup,
                appId = showGroupState.appId,
                onDismissRequest = dismissGroupShow,
                onClickEdit = {
                    dismissGroupShow()
                    mainVm.navigatePage(
                        UpsertRuleGroupRoute(
                            subsId = showGroupState.subsId,
                            groupKey = showGroupState.groupKey,
                            appId = showGroupState.appId,
                        )
                    )
                },
                onClickEditExclude = {
                    openExcludeEditor(showGroupState)
                },
                onClickResetSwitch = subsConfig?.let {
                    if (showGroup is RawSubscription.RawGlobalGroup) {
                        if (showGroupState.pageAppId != null) {
                            if (excludeData.appIds.contains(showGroupState.pageAppId)) {
                                mainVm.scope.launchUiAction {
                                    toast(resetGroupSwitch(showGroupState))
                                }
                            } else {
                                null
                            }
                        } else {
                            subsConfig.enable?.let {
                                mainVm.scope.launchUiAction {
                                    toast(resetGroupSwitch(showGroupState))
                                }
                            }
                        }
                    } else {
                        subsConfig.enable?.let {
                            mainVm.scope.launchUiAction {
                                toast(resetGroupSwitch(showGroupState))
                            }
                        }
                    }
                },
                onClickDelete = mainVm.scope.launchUiAction {
                    val r = mainVm.dialogRequests.confirm(
                        title = "删除规则",
                        text = "确定删除 ${showGroup.name} ?",
                        error = true,
                    )
                    if (!r) {
                        return@launchUiAction
                    }
                    deleteGroup(showGroupState)
                    dismissGroupShow()
                    toast("删除成功")
                }
            )
        }

        val excludeEditSession = excludeEditSessionFlow.collectAsStateWithLifecycle().value
        val excludeGroupState = excludeEditSession?.groupState
        val excludeSubs = useSubs(excludeGroupState?.subsId)
        val excludeGroup =
            useSubsGroup(excludeSubs, excludeGroupState?.groupKey, excludeGroupState?.appId)
        if (excludeEditSession != null && excludeGroupState?.groupKey != null && excludeGroupState.appId != null && excludeSubs != null && excludeGroup is RawSubscription.RawAppGroup) {
            FullscreenDialog(onDismissRequest = dismissExcludeGroupShow) {
                val keyboardController = LocalSoftwareKeyboardController.current
                val onBack = mainVm.scope.launchUiAction {
                    keyboardController?.hide()
                    val newValue = getChangedExcludeData(excludeEditSession)
                    if (newValue != null) {
                        if (!mainVm.dialogRequests.confirm(
                            title = "提示",
                            text = "当前内容未保存，是否放弃编辑？",
                        )) return@launchUiAction
                    }
                    dismissExcludeGroupShow()
                }
                BackHandler(onBack = onBack)
                Scaffold(
                    topBar = {
                        PerfTopAppBar(
                            navigationIcon = {
                                PerfIconButton(
                                    imageVector = PerfIcon.Close,
                                    onClick = onBack
                                )
                            },
                            title = {
                                TowLineText(
                                    title = excludeGroup.name,
                                    subtitle = "编辑禁用",
                                )
                            },
                            actions = {
                                PerfIconButton(imageVector = PerfIcon.Save, onClick = throttle {
                                    val newValue = getChangedExcludeData(excludeEditSession)
                                    if (newValue == null) {
                                        toast("无修改")
                                        dismissExcludeGroupShow()
                                    } else {
                                        dismissExcludeGroupShow()
                                        mainVm.scope.launchUi {
                                            saveChangedExclude(
                                                excludeEditSession,
                                                newValue,
                                            )
                                            toast("更新成功")
                                        }
                                    }
                                })
                            }
                        )
                    },
                ) { contentPadding ->
                    MultiTextField(
                        modifier = Modifier.scaffoldPadding(contentPadding),
                        textFlow = excludeTextFlow,
                        placeholderText = "请填入需要禁用的 activityId 列表\n每行一个",
                    )
                }
            }
        }
    }
}
