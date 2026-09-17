package li.gkd.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.data.CrashData
import li.gkd.app.ui.component.CopyTextCard
import li.gkd.app.ui.component.EmptyText
import li.gkd.app.ui.component.FixedTimeText
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.core.state.Loadable
import li.gkd.app.ui.share.noRippleClickable
import li.gkd.app.ui.style.EmptyHeight
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.itemVerticalPadding
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.ui.style.surfaceCardColors
import li.gkd.app.util.ISSUES_URL
import li.gkd.app.util.format
import li.gkd.app.ui.share.launchUi
import li.gkd.app.util.throttle
import li.gkd.app.util.ToastUtils.toast


@Serializable
data object CrashReportRoute : NavKey

@Composable
fun CrashReportPage() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel { CrashReportVm(mainVm.takeCrashDataList()) }
    val crashDataState by vm.crashDataState.collectAsStateWithLifecycle()
    val actionScope = vm.scope
    val crashDataList = crashDataState.value.orEmpty()
    val pageScrollState = rememberListScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    val listState = pageScrollState.listState
    pageScrollState.ResetOnChange(crashDataList.isNotEmpty())
    val expandedCrashId = vm.expandedCrashId
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = mainVm::popPage,
                    )
                },
                title = {
                    Text(
                        text = "崩溃记录",
                        modifier = Modifier.noRippleClickable(onClick = throttle(pageScrollState::resetScroll))
                    )
                },
                actions = {
                    if (crashDataList.isNotEmpty()) {
                        PerfIconButton(
                            imageVector = PerfIcon.Delete,
                            contentDescription = "清空崩溃记录",
                            onClick = throttle {
                                actionScope.launchUi {
                                    if (!mainVm.dialogRequests.confirm(
                                            title = "清空崩溃记录",
                                            text = "确定删除全部崩溃记录？",
                                            error = true,
                                        )
                                    ) return@launchUi
                                    vm.deleteAllCrashes()
                                    toast("删除成功")
                                }
                            },
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (crashDataList.isNotEmpty()) {
                BottomAppBar {
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = throttle { mainVm.openUrl(ISSUES_URL) },
                    ) {
                        Text(text = "问题反馈")
                    }
                    Spacer(modifier = Modifier.width(itemHorizontalPadding))
                    TextButton(
                        onClick = { mainVm.shareLog.show() },
                    ) {
                        Text(text = "导出日志")
                    }
                    Spacer(modifier = Modifier.width(itemHorizontalPadding))
                }
            }
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .scaffoldPadding(contentPadding)
                .fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(itemVerticalPadding),
        ) {
            items(
                items = crashDataList,
                key = { it.id },
            ) { crashData ->
                val expanded = expandedCrashId == crashData.id
                CrashReportCard(
                    crashData = crashData,
                    expanded = expanded,
                    onToggle = {
                        vm.toggleCrash(crashData.id)
                    },
                    onDelete = throttle {
                        actionScope.launchUi {
                            if (!mainVm.dialogRequests.confirm(
                                    title = "删除崩溃记录",
                                    text = "确定删除这条崩溃记录？",
                                    error = true,
                                )
                            ) return@launchUi
                            vm.deleteCrash(crashData)
                            toast("删除成功")
                        }
                    },
                )
            }
            item(key = "crash-report-footer") {
                if (crashDataList.isEmpty() && crashDataState !is Loadable.Loading) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                    EmptyText(
                        text = (crashDataState as? Loadable.Failure)?.cause?.message
                            ?: "暂无崩溃记录",
                    )
                }
                Spacer(modifier = Modifier.height(EmptyHeight))
            }
        }
    }
}

@Composable
private fun CrashReportCard(
    crashData: CrashData,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val exceptionName = crashData.name.substringAfterLast('.').ifBlank { "未知异常" }
    val message = crashData.message?.takeIf { it.isNotBlank() }
    val timeText = remember(crashData.mtime) {
        crashData.mtime.format("yyyy-MM-dd HH:mm:ss")
    }
    val supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = Modifier
            .padding(horizontal = itemHorizontalPadding / 2)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.extraSmall,
        colors = surfaceCardColors,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .semantics {
                    stateDescription = if (expanded) "已展开" else "已折叠"
                    onClick(
                        label = if (expanded) "收起崩溃详情" else "展开崩溃详情",
                        action = null,
                    )
                }
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = exceptionName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(4.dp))
                PerfIcon(
                    imageVector = if (expanded) PerfIcon.ExpandLess else PerfIcon.ExpandMore,
                    modifier = Modifier.size(24.dp),
                    tint = supportingColor,
                    contentDescription = null,
                )
            }
            Text(
                text = message ?: "无异常信息",
                style = MaterialTheme.typography.bodyMedium,
                color = if (message == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "v${crashData.versionName} (${crashData.versionCode}) · ${crashData.thread}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = supportingColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(8.dp))
                FixedTimeText(
                    text = timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = supportingColor,
                )
            }
        }
        if (expanded) {
            HorizontalDivider()
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "设备：${crashData.device}",
                            style = MaterialTheme.typography.bodySmall,
                            color = supportingColor,
                        )
                        Text(
                            text = "Android：${crashData.androidVersionName} (${crashData.androidVersionCode})",
                            modifier = Modifier.padding(end = 56.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = supportingColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "应用：${crashData.versionName} (${crashData.versionCode})",
                            modifier = Modifier.padding(end = 56.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = supportingColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "线程：${crashData.thread}",
                            modifier = Modifier.padding(end = 56.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = supportingColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                        PerfIconButton(
                            imageVector = PerfIcon.Delete,
                            onClick = onDelete,
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            contentDescription = "删除这条崩溃记录",
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "堆栈",
                    style = MaterialTheme.typography.titleSmall,
                )
                CopyTextCard(
                    text = crashData.stackTrace,
                    modifier = Modifier.heightIn(max = 320.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    textStyle = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
