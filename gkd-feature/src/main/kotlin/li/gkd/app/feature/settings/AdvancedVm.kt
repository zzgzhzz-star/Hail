package li.gkd.app.feature.settings

import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.store.AppStore
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.util.ToastUtils.toast

class AdvancedVm : BaseViewModel() {

    fun saveHttpServerPort(value: String): Boolean {
        val newPort = value.toIntOrNull()
        if (newPort == null || newPort !in 1000..65535) {
            toast("请输入 1000-65535 的整数")
            return false
        }
        if (newPort == storeFlow.value.httpServerPort) {
            return true
        }
        AppStore.updateSettings { it.copy(httpServerPort = newPort) }
        toast("更新成功")
        return true
    }

    fun setAutoClearMemorySubs(enabled: Boolean) {
        AppStore.updateSettings { it.copy(autoClearMemorySubs = enabled) }
    }
}
