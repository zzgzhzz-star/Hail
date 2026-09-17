package li.gkd.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import li.gkd.app.notif.StopServiceReceiver
import li.gkd.app.util.ToastUtils.toast
import li.songe.codeorigin.CallSite

fun LifecycleHookService.useServicePresence(
    stateFlow: MutableStateFlow<Boolean>,
    name: String,
    startToastDelayMillis: Long = 0L,
    @CallSite loc: String = "",
) {
    onCreated {
        stateFlow.value = true
        toast("${name}已启动", delayMillis = startToastDelayMillis, loc = loc)
    }
    onDestroyed(loc = loc) {
        stateFlow.value = false
        toast("${name}已关闭", loc = loc)
    }
}

fun LifecycleHookService.useStopServiceReceiver(
    @CallSite loc: String = "",
) {
    val receiver = StopServiceReceiver(this)
    onCreated { receiver.register() }
    onDestroyed(loc = loc) { receiver.close() }
}
