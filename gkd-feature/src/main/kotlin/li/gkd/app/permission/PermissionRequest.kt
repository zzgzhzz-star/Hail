package li.gkd.app.permission

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PermissionRequests(
    private val navigateToPrivilegeService: () -> Unit,
) {
    private val coordinator = PermissionRequestCoordinator()
    private val hostCommands = PermissionHostCommands()
    private val content = PermissionRequestContent(
        coordinator = coordinator,
        updateHostState = ::updateHostState,
        detachHost = ::detachHost,
    )
    private val requestMutex = Mutex()
    private var activeRequestJob: Job? = null
    private var disposed = false

    @Composable
    fun Render(modifier: Modifier = Modifier) {
        content.Render(modifier)
    }

    suspend fun ensurePermissions(
        vararg permissionStates: PermissionState,
    ): Boolean = withContext(Dispatchers.Main.immediate) {
        if (disposed) return@withContext false
        requestMutex.withLock {
            if (disposed) return@withLock false
            val requestJob = currentCoroutineContext().job
            activeRequestJob = requestJob
            try {
                for (permissionState in permissionStates) {
                    if (permissionState.refresh()) continue
                    val permission = permissionState.permission
                    if (permission == null) {
                        val resolution = permissionState.resolution ?: return@withLock false
                        if (coordinator.awaitResolution(permissionState)) {
                            if (resolution.navigateToPrivilegeService) {
                                navigateToPrivilegeService()
                            }
                        }
                        return@withLock false
                    }
                    hostCommands.requestPermission(
                        permission = permission,
                        prompt = PermissionPrompt(
                            title = "正在申请「${permissionState.name}」",
                            message = checkNotNull(permissionState.purpose) {
                                "${permissionState.name} 缺少权限请求说明"
                            },
                        ),
                    )
                    if (permissionState.refresh()) continue
                    if (!coordinator.awaitResolution(permissionState)) {
                        return@withLock false
                    }
                    hostCommands.openPermissionSettings(permission)
                    if (!permissionState.refresh()) {
                        return@withLock false
                    }
                }
                true
            } finally {
                if (activeRequestJob === requestJob) {
                    activeRequestJob = null
                }
            }
        }
    }

    private fun updateHostState(
        resumed: Boolean,
        hasWindowFocus: Boolean,
    ) {
        coordinator.updateHostState(resumed, hasWindowFocus)
        hostCommands.updateHostState(resumed, hasWindowFocus)
    }

    private fun detachHost() {
        coordinator.updateHostState(resumed = false, hasWindowFocus = false)
        hostCommands.detachHost()
    }

    private fun dispose() {
        disposed = true
        activeRequestJob?.cancel()
        activeRequestJob = null
        hostCommands.dispose()
        coordinator.dispose()
    }

    class Host(activity: ComponentActivity) {
        private val delegate = PermissionRequestHost(activity)

        fun bind(requests: PermissionRequests) {
            delegate.bind(
                commands = requests.hostCommands,
                coordinator = requests.coordinator,
                onDetachHost = requests::detachHost,
                onDispose = requests::dispose,
            )
        }
    }
}
