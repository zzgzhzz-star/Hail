package li.gkd.app.feature.settings

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.Serializable
import li.gkd.app.META
import li.gkd.app.R
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.component.SettingItem
import li.gkd.app.ui.share.LocalDarkTheme
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.style.EmptyHeight
import li.gkd.app.ui.style.itemPadding
import li.gkd.app.ui.style.titleItemPadding
import li.gkd.app.util.ISSUES_URL
import li.gkd.app.util.REPOSITORY_URL
import li.gkd.app.util.ShortUrlSet
import li.gkd.app.ui.share.launchUiAction
import li.gkd.app.ui.share.launchUi
import li.gkd.app.util.throttle
import li.gkd.app.util.ToastUtils.toast

@Serializable
data object AboutRoute : NavKey

@Composable
fun AboutPage() {
    val mainVm = LocalMainViewModel.current
    var showVersionInfoDialog by rememberSaveable { mutableStateOf(false) }
    var showShareAppDialog by rememberSaveable { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = {
                            mainVm.popPage()
                        },
                    )
                },
                title = { Text(text = "关于") },
                actions = {
                    PerfIconButton(
                        imageVector = PerfIcon.Share,
                        onClick = { showShareAppDialog = true },
                    )
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedLogoIcon(
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = throttle { toast("你干嘛~ 哎呦~") }
                        )
                        .fillMaxWidth(0.33f)
                        .aspectRatio(1f)
                )
                Column(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable(onClick = { showVersionInfoDialog = true })
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = META.appName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = META.versionName,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            SettingItem(
                imageVector = null,
                title = "开源代码",
                onClick = {
                    mainVm.openUrl(REPOSITORY_URL)
                },
            )
            if (META.isGkdChannel) {
                SettingItem(
                    imageVector = null,
                    title = "捐赠支持",
                    onClick = {
                        mainVm.navigateWebPage(ShortUrlSet.URL10)
                    },
                )
            }
            SettingItem(
                imageVector = null,
                title = "使用协议",
                onClick = {
                    mainVm.navigateWebPage(ShortUrlSet.URL12)
                },
            )
            SettingItem(
                imageVector = null,
                title = "隐私政策",
                onClick = {
                    mainVm.navigateWebPage(ShortUrlSet.URL11)
                },
            )

            FeedbackSection()
            SettingItem(
                title = "导出日志",
                imageVector = PerfIcon.Share,
                onClick = {
                    mainVm.shareLog.show()
                }
            )
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }

    AboutDialogs(
        showVersionInfo = showVersionInfoDialog,
        onDismissVersionInfo = { showVersionInfoDialog = false },
        showShareApp = showShareAppDialog,
        onDismissShareApp = { showShareAppDialog = false },
    )
}

@Composable
private fun FeedbackSection() {
    val mainVm = LocalMainViewModel.current
    val primaryColor = MaterialTheme.colorScheme.primary
    Text(
        text = "反馈",
        modifier = Modifier.titleItemPadding(),
        style = MaterialTheme.typography.titleSmall,
        color = primaryColor,
    )
    Column(
        modifier = Modifier
            .clickable(onClick = throttle(mainVm.scope.launchUiAction {
                val noticeText = buildAnnotatedString {
                    val highlightStyle = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                    )
                    append("感谢您愿意花时间反馈，")
                    withStyle(style = highlightStyle) {
                        append("GKD 默认不携带任何规则，只接受应用本体功能相关的反馈")
                    }
                    append("\n\n")
                    append("请先判断是不是第三方规则订阅的问题，如果是，您应该向规则提供者反馈，而不是在此处反馈。")
                    withStyle(style = highlightStyle) {
                        append("如果您已经确信是 GKD 应用本体的问题")
                    }
                    append("，可点击下方继续反馈")
                }
                if (!mainVm.dialogRequests.confirm(
                    title = "反馈须知",
                    text = noticeText,
                    confirmText = "继续",
                    dismissOnRequest = true,
                )) return@launchUiAction
                mainVm.openUrl(ISSUES_URL)
            }))
            .fillMaxWidth()
            .itemPadding()
    ) {
        Text(
            text = "问题反馈",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun AnimatedLogoIcon(
    modifier: Modifier = Modifier
) {
    val darkTheme = LocalDarkTheme.current
    val colorRid = if (darkTheme) R.color.better_white else R.color.better_black
    var atEnd by remember { mutableStateOf(false) }
    val animation = AnimatedImageVector.animatedVectorResource(id = R.drawable.ic_anim_logo)
    val painter = rememberAnimatedVectorPainter(
        animation,
        atEnd
    )
    LaunchedEffect(Unit) {
        while (isActive) {
            atEnd = !atEnd
            delay(animation.totalDuration.toLong())
        }
    }
    Icon(
        modifier = modifier,
        painter = painter,
        contentDescription = null,
        tint = colorResource(colorRid),
    )
}
