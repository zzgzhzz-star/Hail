package li.gkd.app.ui.app

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import li.gkd.app.priv.AutomationService
import li.gkd.app.priv.uiAutomationOccupiedFlow
import li.gkd.app.service.A11yService
import li.gkd.app.ui.PrivilegeServiceRoute
import li.gkd.app.ui.component.AppAlertDialog
import li.gkd.app.ui.component.TermsAcceptDialog
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.util.ToastUtils.toast

@Composable
fun AppOverlayHost() {
    val mainVm = LocalMainViewModel.current
    if (!mainVm.termsAcceptedFlow.collectAsStateWithLifecycle().value) {
        TermsAcceptDialog()
    } else {
        // Sheet
        mainVm.subsSheet.Render()

        // Dialog
        UiAutomationAlreadyRegisteredDlg()
        AccessRestrictedSettingsDlg()
        mainVm.dialogRequests.Render()
        mainVm.githubUpload.Render()
        mainVm.subsLinkDialog.Render()
        mainVm.ruleGroupState.Render()
        mainVm.textDialog.Render()
        mainVm.shareLog.Render()
    }
}

private val accessRestrictedSettingsShowFlow = MutableStateFlow(false)

fun showAccessRestrictedSettingsDialog() {
    accessRestrictedSettingsShowFlow.value = true
}

private fun dismissAccessRestrictedSettingsDialog() {
    accessRestrictedSettingsShowFlow.value = false
}

@Composable
private fun AccessRestrictedSettingsDlg() {
    val a11yRunning by A11yService.isRunning.collectAsStateWithLifecycle()
    LaunchedEffect(a11yRunning) {
        if (a11yRunning) {
            dismissAccessRestrictedSettingsDialog()
        }
    }
    val accessRestrictedSettingsShow by accessRestrictedSettingsShowFlow.collectAsStateWithLifecycle()
    val mainVm = LocalMainViewModel.current
    val isPrivilegeServicePage = mainVm.topRoute is PrivilegeServiceRoute
    LaunchedEffect(isPrivilegeServicePage, accessRestrictedSettingsShow) {
        if (isPrivilegeServicePage && accessRestrictedSettingsShow && !a11yRunning) {
            toast("请重新授权以解除限制")
            dismissAccessRestrictedSettingsDialog()
        }
    }
    if (accessRestrictedSettingsShow && !isPrivilegeServicePage && !a11yRunning) {
        AppAlertDialog(
            title = {
                Text(text = "权限受限")
            },
            text = {
                Text(text = "当前操作权限「访问受限设置」已被限制，请前往特权服务重新授权")
            },
            onDismissRequest = {
                dismissAccessRestrictedSettingsDialog()
            },
            confirmButton = {
                TextButton({
                    dismissAccessRestrictedSettingsDialog()
                    mainVm.navigatePage(PrivilegeServiceRoute)
                }) {
                    Text(text = "前往授权")
                }
            },
            dismissButton = {
                TextButton({
                    dismissAccessRestrictedSettingsDialog()
                }) {
                    Text(text = "关闭")
                }
            },
        )
    }
}

@Composable
private fun UiAutomationAlreadyRegisteredDlg() {
    if (uiAutomationOccupiedFlow.collectAsStateWithLifecycle().value) {
        AppAlertDialog(
            onDismissRequest = {
                AutomationService.dismissOccupiedWarning()
            },
            title = { Text(text = "启动失败") },
            text = {
                Text(text = "自动化服务启动失败，检测到自动化服务已被其他应用占用，请先关闭已有服务后重试\n\n注：自动化服务只能同时运行一个，请确保没有其他应用或测试框架占用后再启动")
            },
            confirmButton = {
                TextButton(onClick = {
                    AutomationService.dismissOccupiedWarning()
                }) {
                    Text(text = "我知道了")
                }
            }
        )
    }
}
