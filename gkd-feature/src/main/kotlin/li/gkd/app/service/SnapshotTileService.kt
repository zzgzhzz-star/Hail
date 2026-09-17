package li.gkd.app.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import li.gkd.app.a11y.A11yRuntime
import li.gkd.app.appScope
import li.gkd.app.ui.share.launchUi
import li.gkd.app.util.LogUtils
import li.gkd.app.snapshot.SnapshotCapture
import li.gkd.app.util.ToastUtils.toast

class SnapshotTileService : BaseTileService() {
    override val activeFlow = flowOf(false)

    override fun onTileClick() = execSnapshot()
}

private fun execSnapshot() {
    LogUtils.d("SnapshotTileService::onClick")
    val service = A11yRuntime.service
    if (service == null) {
        A11yRuntime.performActionBack()
        toast("服务未连接", forced = true)
        return
    }
    appScope.launchUi(Dispatchers.IO) {
        val oldAppId = A11yRuntime.getRoot(service)?.packageName?.toString()

        if (oldAppId == null) {
            A11yRuntime.performActionBack()
            toast("获取信息根节点失败", forced = true)
            return@launchUi
        }

        val startTime = System.currentTimeMillis()
        fun timeout(): Boolean {
            return System.currentTimeMillis() - startTime > 3000L
        }

        var ok = false
        while (isActive) {
            val latestAppId = A11yRuntime.getRoot(service)?.packageName?.toString()
            if (latestAppId == null) {
                // https://github.com/gkd-kit/gkd/issues/713
                delay(250)
                if (timeout()) {
                    toast("当前应用没有无障碍信息，捕获失败", forced = true)
                    break
                }
            } else if (latestAppId != oldAppId) {
                ok = true
                LogUtils.d("SnapshotTileService::eventExecutor.execute")
                SnapshotCapture.capture(forcedCropStatusBar = true)
                break
            } else {
                A11yRuntime.performActionBack()
                delay(500)
                if (timeout()) {
                    toast("未检测到界面切换，捕获失败", forced = true)
                    break
                }
            }
        }
        if (!ok) {
            A11yRuntime.performActionBack()
        }
    }
}
