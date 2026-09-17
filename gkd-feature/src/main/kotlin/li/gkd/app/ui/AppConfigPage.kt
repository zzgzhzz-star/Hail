package li.gkd.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.core.state.Loadable
import li.gkd.app.data.RawSubscription
import li.gkd.app.domain.rule.RuleGroupTarget
import li.gkd.app.domain.rule.toRuleGroupTarget
import li.gkd.app.feature.log.ActionLogRoute
import li.gkd.app.feature.subscription.SubsAppGroupListRoute
import li.gkd.app.feature.subscription.UpsertRuleGroupRoute
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.ui.component.AnimationFloatingActionButton
import li.gkd.app.ui.component.AppNameText
import li.gkd.app.ui.component.BatchActionMenuItem
import li.gkd.app.ui.component.EmptyText
import li.gkd.app.ui.component.MenuGroupCard
import li.gkd.app.ui.component.MenuItemCheckbox
import li.gkd.app.ui.component.MenuItemRadioButton
import li.gkd.app.ui.component.MultiSelectionActions
import li.gkd.app.ui.component.MultiSelectionTopAppBar
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.RuleBatchMenuItems
import li.gkd.app.ui.component.RuleGroupCard
import li.gkd.app.ui.component.animateListItem
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.component.rememberMultiSelectionState
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.style.EmptyHeight
import li.gkd.app.ui.style.iconTextSize
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.RuleSortOption
import li.gkd.app.util.ToastUtils.copyText
import li.gkd.app.util.ToastUtils.toast
import li.gkd.app.util.findOption
import li.gkd.app.util.throttle
import li.gkd.db.ActionLog
import li.gkd.db.LOCAL_SUBS_ID

@Serializable
data class AppConfigRoute(
    val appId: String,
    val focusLog: ActionLog? = null,
) : NavKey

@Composable
fun AppConfigPage(route: AppConfigRoute) {
    val appId = route.appId
    val focusLog = route.focusLog
    val mainVm = LocalMainViewModel.current
    val vm = viewModel { AppConfigVm(route) }
    val scope = vm.scope
    val batchBusy by vm.batchBusyFlow.collectAsStateWithLifecycle()

    val store by storeFlow.collectAsStateWithLifecycle()
    val loadableState by vm.uiState.collectAsStateWithLifecycle()
    val state = loadableState.value
    val firstLoading = loadableState is Loadable.Loading
    val loadError = (loadableState as? Loadable.Failure)?.cause
    val globalSubsConfigs = state?.globalSubsConfigs.orEmpty()
    val categoryConfigs = state?.categoryConfigs.orEmpty()
    val appSubsConfigs = state?.appSubsConfigs.orEmpty()
    val subsPairs = state?.subsPairs.orEmpty()
    val groupSize = subsPairs.sumOf { it.second.size }
    val focusGroup = vm.focusGroupFlow?.collectAsStateWithLifecycle()?.value

    val allGroupStates = remember(subsPairs, appId) {
        subsPairs.flatMap { (entry, groups) ->
            groups.map { group -> group.toRuleGroupTarget(entry.subsItem.id, appId) }
        }.toSet()
    }
    val selectionState = rememberMultiSelectionState<RuleGroupTarget>()
    val selectedDataSet = selectionState.selectedKeys intersect allGroupStates
    val isSelectedMode = selectionState.active
    LaunchedEffect(allGroupStates, loadableState) {
        if (loadableState is Loadable.Ready) selectionState.retain(allGroupStates)
    }
    BackHandler(isSelectedMode) {
        selectionState.clear()
    }

    val updateSelected: (Boolean?) -> Unit = { enabled ->
        val targets = selectedDataSet
        if (targets.isNotEmpty()) {
            scope.launchUi {
                vm.runBatchAction {
                    val action = when (enabled) {
                        false -> "关闭"
                        true -> "启用"
                        null -> "重置开关至默认值"
                    }
                    if (!mainVm.dialogRequests.confirm(
                        title = "操作提示",
                        text = "是否将所选 ${targets.size} 个规则组全部${action}?\n\n全局规则仅调整在当前应用的开关。\n也可在「订阅-规则类别」操作。",
                    )) return@runBatchAction
                    val changedSize = vm.updateSelectedEnabled(targets, enabled)
                    val result = if (enabled == null) "已重置" else if (enabled) "已启用" else "已关闭"
                    toast(if (changedSize > 0) "$result $changedSize 个规则组" else "无规则被改变，所选规则可能已变化")
                }
            }
        }
    }
    val pageScrollState = rememberListScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    val listState = pageScrollState.listState
    pageScrollState.ResetOnChange(
        groupSize > 0,
        firstLoading,
    )
    if (focusLog != null) {
        LaunchedEffect(focusGroup, groupSize) {
            if (focusGroup != null && groupSize > 0) {
                val i = subsPairs.run {
                    var j = 0
                    forEach { (entry, groups) ->
                        groups.forEach {
                            if (entry.subsItem.id == focusLog.subsId && it.groupType == focusLog.groupType && it.key == focusLog.groupKey) {
                                return@run j
                            }
                            j++
                        }
                    }
                    -1
                }
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
                selectedCount = selectedDataSet.size,
                onExitSelection = selectionState::clear,
                scrollBehavior = scrollBehavior,
                onNavigateBack = { mainVm.popPage() },
                onTitleClick = pageScrollState::resetScroll,
                title = {
                    AppNameText(appId = appId)
                },
                actions = { selectedMode ->
                    if (selectedMode) {
                        MultiSelectionActions(
                            selectionState = selectionState,
                            keys = allGroupStates,
                            enabled = isSelectedMode && !batchBusy && loadableState is Loadable.Ready,
                        ) { dismiss ->
                            BatchActionMenuItem(
                                text = "复制规则",
                                enabled = selectedDataSet.any { it is RuleGroupTarget.App },
                                onDismiss = dismiss,
                                onClick = {
                                    val targets = selectedDataSet.filterIsInstance<RuleGroupTarget.App>().toSet()
                                    scope.launchUi {
                                        vm.runBatchAction {
                                            copyText(vm.buildSelectedGroupsText(targets))
                                        }
                                    }
                                },
                            )
                            RuleBatchMenuItems(
                                enabled = true,
                                onDismiss = dismiss,
                                onUpdate = updateSelected,
                            )
                        }
                    } else {
                        var expanded by remember { mutableStateOf(false) }
                        PerfIconButton(
                            enabled = !isSelectedMode,
                            imageVector = PerfIcon.History,
                            onClick = throttle {
                                mainVm.navigatePage(ActionLogRoute(appId = appId))
                            },
                        )
                        Box {
                            PerfIconButton(
                                enabled = !isSelectedMode,
                                imageVector = PerfIcon.Sort,
                                onClick = { expanded = true },
                            )
                            DropdownMenu(
                                expanded = expanded && !isSelectedMode,
                                onDismissRequest = { expanded = false },
                            ) {
                                MenuGroupCard(inTop = true, title = "排序") {
                                    val handleItem: (RuleSortOption) -> Unit = throttle(vm::setRuleSortType)
                                    RuleSortOption.objects.forEach { option ->
                                        MenuItemRadioButton(
                                            text = option.label,
                                            selected = RuleSortOption.objects.findOption(store.appRuleSort) == option,
                                            onClick = { handleItem(option) },
                                        )
                                    }
                                }
                                MenuGroupCard(title = "筛选") {
                                    MenuItemCheckbox(
                                        text = "未启用",
                                        checked = store.showDisabledRule,
                                        onClick = vm::toggleShowDisabledRule,
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            AnimationFloatingActionButton(
                visible = !isSelectedMode,
                onClick = {
                    mainVm.navigatePage(
                        UpsertRuleGroupRoute(
                            subsId = LOCAL_SUBS_ID,
                            groupKey = null,
                            appId = appId
                        )
                    )
                },
                imageVector = PerfIcon.Add,
                contentDescription = "添加规则"
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            subsPairs.forEach { (entry, groups) ->
                val subsId = entry.subsItem.id
                stickyHeader(entry.subsItem.id) {
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 8.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .clickable(onClick = throttle {
                                mainVm.navigatePage(
                                    SubsAppGroupListRoute(
                                        subsItemId = subsId,
                                        appId = appId,
                                    )
                                )
                            })
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = entry.subscription.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                        PerfIcon(
                            imageVector = PerfIcon.KeyboardArrowRight,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.iconTextSize()
                        )
                    }
                }
                items(groups, { Triple(subsId, it.groupType, it.key) }) { group ->
                    val subsConfig = when (group) {
                        is RawSubscription.RawAppGroup -> appSubsConfigs
                        is RawSubscription.RawGlobalGroup -> globalSubsConfigs
                    }.find { it.subsId == entry.subsItem.id && it.groupKey == group.key }
                    val category = when (group) {
                        is RawSubscription.RawAppGroup -> entry.subscription.getCategory(group.name)
                        is RawSubscription.RawGlobalGroup -> null
                    }
                    val categoryConfig = if (category != null) {
                        categoryConfigs.find { it.subsId == subsId && it.categoryKey == category.key }
                    } else {
                        null
                    }
                    val isSelected = selectedDataSet.any {
                        it.subsId == subsId && it.groupType == group.groupType && it.groupKey == group.key
                    }
                    val onLongClick = {
                        if (!batchBusy) {
                            selectionState.select(
                                group.toRuleGroupTarget(
                                    subsId = subsId,
                                    appId = appId,
                                ),
                            )
                        }
                    }
                    val onSelectedChange = {
                        selectionState.toggle(
                            group.toRuleGroupTarget(
                                subsId = subsId,
                                appId = appId,
                            )
                        )
                    }
                    RuleGroupCard(
                        modifier = Modifier.animateListItem(),
                        subs = entry.subscription,
                        appId = appId,
                        group = group,
                        subsConfig = subsConfig,
                        categoryConfig = categoryConfig,
                        onOpen = {
                            mainVm.showRuleGroup(
                                subscriptionId = subsId,
                                appId = appId,
                                group = group,
                            )
                        },
                        onCheckedChange = { enabled ->
                            scope.launchUi {
                                vm.setGroupEnabled(entry.subscription, group, enabled)
                            }
                        },
                        onLongClick = onLongClick,
                        isSelectedMode = isSelectedMode,
                        selectionEnabled = !batchBusy,
                        isSelected = isSelected,
                        onSelectedChange = onSelectedChange,
                        focusGroup = focusGroup,
                        onFocusHandled = vm::consumeFocusGroup,
                    )
                }
            }
            item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (groupSize == 0 && !firstLoading) {
                    EmptyText(
                        text = if (loadError != null) {
                            loadError.message ?: "数据加载失败"
                        } else if (store.showDisabledRule) {
                            "暂无数据"
                        } else {
                            "暂无数据，或修改筛选"
                        }
                    )
                }
            }
        }
    }
}
