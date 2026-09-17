package li.gkd.app.feature.subscription

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.core.state.Loadable
import li.gkd.app.ui.component.AnimationFloatingActionButton
import li.gkd.app.ui.component.BatchActionMenuItem
import li.gkd.app.ui.component.EmptyText
import li.gkd.app.ui.component.MultiSelectionActions
import li.gkd.app.ui.component.MultiSelectionTopAppBar
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.RuleBatchMenuItems
import li.gkd.app.ui.component.RuleGroupCard
import li.gkd.app.ui.component.SubscriptionPageContent
import li.gkd.app.ui.component.TowLineText
import li.gkd.app.ui.component.animateListItem
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.component.rememberMultiSelectionState
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.style.EmptyHeight
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.ToastUtils.toast


@Serializable
data class SubsGlobalGroupListRoute(val subsItemId: Long, val focusGroupKey: Int? = null) : NavKey

@Composable
fun SubsGlobalGroupListPage(route: SubsGlobalGroupListRoute) {
    val subsItemId = route.subsItemId
    val focusGroupKey = route.focusGroupKey

    val mainVm = LocalMainViewModel.current
    val vm = viewModel { SubsGlobalGroupListVm(route) }
    val scope = vm.scope
    val batchBusy by vm.batchBusyFlow.collectAsStateWithLifecycle()
    val focusGroup = vm.focusGroupFlow?.collectAsStateWithLifecycle()?.value

    SubscriptionPageContent(vm.uiState) { state ->
        val subs = state.subscription
        val subsConfigs = state.subsConfigs.value.orEmpty()
        val switchEnabled = state.subsConfigs is Loadable.Ready
        val editable = subsItemId < 0
        val globalGroups = subs.globalGroups

        val selectionState = rememberMultiSelectionState<Int>()
        val allKeys = remember(globalGroups) { globalGroups.mapTo(mutableSetOf()) { it.key } }
        val selectedKeys = selectionState.selectedKeys intersect allKeys
        val isSelectedMode = selectionState.active
        LaunchedEffect(allKeys) {
            selectionState.retain(allKeys)
        }
        BackHandler(isSelectedMode) {
            selectionState.clear()
        }

        val updateSelected: (Boolean?) -> Unit = { enabled ->
            val keysToUpdate = selectedKeys
            if (keysToUpdate.isNotEmpty()) {
                scope.launchUi {
                    vm.runBatchAction {
                        val action = when (enabled) {
                            false -> "关闭"
                            true -> "启用"
                            null -> "重置开关至默认值"
                        }
                        if (!mainVm.dialogRequests.confirm(
                            title = "操作提示",
                            text = "是否将所选 ${keysToUpdate.size} 个规则组全部${action}?\n\n注: 也可在「订阅-规则类别」操作",
                        )) return@runBatchAction
                        val changedSize = vm.updateSelectedEnabled(keysToUpdate, enabled)
                        val result = if (enabled == null) "已重置" else if (enabled) "已启用" else "已关闭"
                        toast(if (changedSize > 0) "$result $changedSize 个规则组" else "无规则被改变，所选规则可能已变化")
                    }
                }
            }
        }
        val pageScrollState = rememberListScrollState()
        val scrollBehavior = pageScrollState.scrollBehavior
        val listState = pageScrollState.listState
        pageScrollState.ResetOnChange(globalGroups.isEmpty())
        if (focusGroupKey != null) {
            LaunchedEffect(null) {
                if (focusGroup != null) {
                    val i = globalGroups.indexOfFirst { it.key == focusGroupKey }
                    if (i >= 0) {
                        listState.scrollToItem(i)
                    }
                }
            }
        }
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                MultiSelectionTopAppBar(
                    selectedMode = isSelectedMode,
                    selectedCount = selectedKeys.size,
                    onExitSelection = selectionState::clear,
                    scrollBehavior = scrollBehavior,
                    onNavigateBack = { mainVm.popPage() },
                    onTitleClick = pageScrollState::resetScroll,
                    title = {
                        TowLineText(
                            title = subs.name,
                            subtitle = "全局规则",
                        )
                    },
                    actions = { selectedMode ->
                        if (selectedMode) {
                            MultiSelectionActions(
                                selectionState = selectionState,
                                keys = allKeys,
                                enabled = isSelectedMode && !batchBusy,
                            ) { dismiss ->
                                RuleBatchMenuItems(
                                    enabled = switchEnabled,
                                    onDismiss = dismiss,
                                    onUpdate = updateSelected,
                                )
                                if (editable) {
                                    HorizontalDivider()
                                    BatchActionMenuItem(
                                        text = "删除规则",
                                        onDismiss = dismiss,
                                        destructive = true,
                                        onClick = {
                                            val keysToDelete = selectedKeys
                                            scope.launchUi {
                                                vm.runBatchAction {
                                                    if (!mainVm.dialogRequests.confirm(
                                                        title = "删除规则",
                                                        text = "确定删除所选 ${keysToDelete.size} 个规则组?",
                                                        error = true,
                                                    )) return@runBatchAction
                                                    val deletedSize = vm.deleteSelectedGroups(keysToDelete)
                                                    selectionState.removeDeleted(keysToDelete)
                                                    toast(if (deletedSize > 0) "已删除 $deletedSize 个规则组" else "所选规则已变化")
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                if (editable) {
                    AnimationFloatingActionButton(
                        visible = !isSelectedMode,
                        onClick = {
                            mainVm.navigatePage(
                                UpsertRuleGroupRoute(
                                    subsId = subsItemId,
                                    groupKey = null,
                                    appId = null,
                                )
                            )
                        },
                        imageVector = PerfIcon.Add,
                        contentDescription = "添加规则"
                    )
                }
            },
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier.scaffoldPadding(paddingValues),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(globalGroups, { g -> g.key }) { group ->
                    val subsConfig = subsConfigs.find { it.groupKey == group.key }
                    RuleGroupCard(
                        modifier = Modifier.animateListItem(),
                        subs = subs,
                        appId = null,
                        group = group,
                        focusGroup = focusGroup,
                        onFocusHandled = vm::consumeFocusGroup,
                        subsConfig = subsConfig,
                        categoryConfig = null,
                        switchEnabled = switchEnabled,
                        onOpen = {
                            mainVm.showRuleGroup(
                                subscriptionId = subs.id,
                                appId = null,
                                group = group,
                            )
                        },
                        onCheckedChange = { enabled ->
                            scope.launchUi {
                                vm.setGroupEnabled(group, enabled)
                            }
                        },
                        isSelectedMode = isSelectedMode,
                        selectionEnabled = !batchBusy,
                        isSelected = group.key in selectedKeys,
                        onLongClick = {
                            if (!batchBusy) {
                                selectionState.select(group.key)
                            }
                        },
                        onSelectedChange = {
                            selectionState.toggle(group.key)
                        }
                    )
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                    if (globalGroups.isEmpty()) {
                        EmptyText(text = "暂无规则")
                    }
                }
            }
        }
    }
}
