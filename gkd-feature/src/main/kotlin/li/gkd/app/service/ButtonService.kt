package li.gkd.app.service

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import li.gkd.app.notif.NotificationCatalog
import li.gkd.app.permission.PermissionStates
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.snapshot.SnapshotCapture
import li.gkd.app.ui.share.launchUi
import li.gkd.app.util.IntentUtils

class ButtonService : OverlayWindowService(
    positionKey = "button"
) {
    override fun onClickView() {
        if (isOverlayContentHidden) return
        lifecycleScope.launchUi {
            withAllOverlaysHidden {
                SnapshotCapture.capture()
            }
        }
    }

    override fun onLongClickView() = stopSelf()

    @Composable
    override fun ComposeContent() {
        val alpha = 0.75f
        PerfIcon(
            imageVector = PerfIcon.CenterFocusWeak,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha))
                .size(40.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
        )
    }

    init {
        useServicePresence(
            stateFlow = isRunning,
            name = "快照按钮服务",
        )
        onCreated {
            NotificationCatalog.button().startForeground()
        }
    }

    companion object {
        val isRunning: StateFlow<Boolean>
            field = MutableStateFlow(false)
        fun start() {
            if (!PermissionStates.drawOverlays.checkOrToast()) return
            IntentUtils.startForegroundServiceByClass(ButtonService::class)
        }

        fun stop() = IntentUtils.stopServiceByClass(ButtonService::class)

    }
}
