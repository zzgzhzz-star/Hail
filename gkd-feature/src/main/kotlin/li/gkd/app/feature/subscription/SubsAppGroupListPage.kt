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
import li.gkd.app.util.ToastUtils.copyText
import li.gkd.app.util.ToastUtils.toast

@Serializable
data class SubsAppGroupListRoute(
    val subsItemId: Long,
    val appId: String,
    val focusGroupKey: Int? = null, // 背景/边框高亮一下
) : NavKey

@Composable
fun SubsAppGroupListPage(route: SubsAppGroupListRoute) {
    val subsItemId = route.subsItemId
    val appId = route.appId
    val focusGroupKey = route.focusGroupKey

    val mainVm = LocalMainViewModel.current
    val vm = viewModel { SubsAppGroupListVm(route) }
    val scope = vm.scope
    val batchBusy by vm.batchBusyFlow.collectAsStateWithLifecycle()
    val focusGroup = vm.focusGroupFlow?.collectAsStateWithLifecycle()?.value

    SubscriptionPageContent(vm.uiState) { state ->
        val subs = state.subscription
        val configs = state.configs.value
        val subsConfigs = configs?.subsConfigs.orEmpty()
        val categoryConfigs = configs?.categoryConfigs.orEmpty()
        val switchEnabled = state.configs is Loadable.Ready
        val app = state.app
        val editable = subsItemId < 0
        val selectionState = rememberMultiSelectionState<Int>()
        val allKeys = remember(app.groups) { app.groups.mapTo(mutableSetOf()) { it.key } }
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
        pageScrollState.ResetOnChange(app.groups.isEmpty())
        if (focusGroupKey != null) {
            LaunchedEffect(null) {
                if (focusGroup != null) {
                    val i = app.groups.indexOfFirst { it.key == focusGroupKey }
                    if (i >= 0) {
                        listState.scrollToItem(i)
                    }
                }
            }
        }
        Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
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
                        subtitle = appId,
                        showApp = true,
                        appFallbackName = app.name,
                    )
                },
                actions = { selectedMode ->
                    if (selectedMode) {
                        MultiSelectionActions(
                            selectionState = selectionState,
                            keys = allKeys,
                            enabled = isSelectedMode && !batchBusy,
                        ) { dismiss ->
                            BatchActionMenuItem(
                                text = "复制规则",
                                onDismiss = dismiss,
                                onClick = {
                                    val keysToCopy = selectedKeys
                                    scope.launchUi {
                                        vm.runBatchAction {
                                            copyText(vm.buildSelectedGroupsText(keysToCopy))
                                        }
                                    }
                                },
                            )
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
        }, floatingActionButton = {
            if (editable) {
                AnimationFloatingActionButton(
                    visible = !isSelectedMode,
                    onClick = {
                        mainVm.navigatePage(
                            UpsertRuleGroupRoute(
                                subsId = subsItemId,
                                groupKey = null,
                                appId = appId
                            )
                        )
                    },
                    contentDescription = "添加规则",
                    imageVector = PerfIcon.Add,
                )
            }
        }) { contentPadding ->
            LazyColumn(
                modifier = Modifier.scaffoldPadding(contentPadding),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(app.groups, { it.key }) { group ->
                    val category = subs.getCategory(group.name)
                    val subsConfig = subsConfigs.find { it.groupKey == group.key }
                    val categoryConfig = categoryConfigs.find {
                        it.categoryKey == category?.key
                    }
                    RuleGroupCard(
                        modifier = Modifier.animateListItem(),
                        subs = subs,
                        appId = appId,
                        group = group,
                        subsConfig = subsConfig,
                        categoryConfig = categoryConfig,
                        switchEnabled = switchEnabled,
                        onOpen = {
                            mainVm.showRuleGroup(
                                subscriptionId = subs.id,
                                appId = appId,
                                group = group,
                            )
                        },
                        onCheckedChange = { enabled ->
                            scope.launchUi {
                                vm.setGroupEnabled(group, enabled)
                            }
                        },
                        focusGroup = focusGroup,
                        onFocusHandled = vm::consumeFocusGroup,
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
                    if (app.groups.isEmpty()) {
                        EmptyText(text = "暂无规则")
                    }
                }
            }
        }
    }
}
