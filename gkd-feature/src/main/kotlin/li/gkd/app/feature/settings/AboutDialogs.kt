package li.gkd.app.feature.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import li.gkd.app.META
import li.gkd.app.MainActivity
import li.gkd.app.ui.component.AppAlertDialog
import li.gkd.app.ui.component.TextListDialog
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.util.PLAY_STORE_URL
import li.gkd.app.util.ShortUrlSet
import li.gkd.app.util.format
import li.gkd.app.util.getShareApkFile
import li.gkd.app.ui.share.launchUiAction
import li.gkd.app.util.IntentUtils

@Composable
fun AboutDialogs(
    showVersionInfo: Boolean,
    onDismissVersionInfo: () -> Unit,
    showShareApp: Boolean,
    onDismissShareApp: () -> Unit,
) {
    VersionInfoDialog(showVersionInfo, onDismissVersionInfo)
    ShareAppDialog(showShareApp, onDismissShareApp)
}

@Composable
private fun VersionInfoDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
) {
    if (visible) {
        AppAlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = "版本信息") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column {
                        Text(text = "构建渠道")
                        Text(text = META.channel)
                    }
                    Column {
                        Text(text = "版本代码")
                        Text(text = META.versionCode.toString())
                    }
                    Column {
                        Text(text = "版本名称")
                        Text(text = META.versionName)
                    }
                    Column {
                        Text(text = "代码记录")
                        Text(
                            modifier = Modifier.clickable { IntentUtils.openUri(META.commitUrl) },
                            text = META.tagName ?: META.commitId.substring(0, 16),
                            color = MaterialTheme.colorScheme.primary,
                            style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
                        )
                    }
                    Column {
                        Text(text = "提交时间")
                        Text(text = META.commitTime.format("yyyy-MM-dd HH:mm:ss ZZ"))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = "关闭")
                }
            },
        )
    }
}

@Composable
private fun ShareAppDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
) {
    val context = LocalActivity.current as MainActivity
    val mainVm = LocalMainViewModel.current
    if (visible) {
        val exportPlayTipText = buildAnnotatedString {
            append("当前导出的 APK 文件只能在已安装 Google 框架的设备上才能使用，否则安装打开后会提示报错，")
            withLink(
                LinkAnnotation.Url(
                    ShortUrlSet.URL13,
                    TextLinkStyles(
                        style = SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    )
                )
            ) {
                append("建议点此从官网下载")
            }
            append("，或点击下方继续操作")
        }
        TextListDialog(
            onDismiss = onDismissRequest,
            textList = listOf(
                "分享到其他应用" to mainVm.scope.launchUiAction(Dispatchers.IO) {
                    if (!META.isGkdChannel) {
                        if (!mainVm.dialogRequests.confirm(
                            title = "分享提示",
                            text = exportPlayTipText,
                            confirmText = "继续",
                        )) return@launchUiAction
                    }
                    context.shareFile(getShareApkFile(), "分享安装文件")
                },
                "保存到下载" to mainVm.scope.launchUiAction(Dispatchers.IO) {
                    if (!META.isGkdChannel) {
                        if (!mainVm.dialogRequests.confirm(
                            title = "保存提示",
                            text = exportPlayTipText,
                            confirmText = "继续",
                        )) return@launchUiAction
                    }
                    context.saveFileToDownloads(getShareApkFile())
                },
                "Google Play" to {
                    mainVm.openUrl(PLAY_STORE_URL)
                },
            )
        )
    }
}
