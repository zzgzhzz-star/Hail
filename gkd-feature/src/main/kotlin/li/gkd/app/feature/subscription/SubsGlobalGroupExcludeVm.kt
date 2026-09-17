package li.gkd.app.feature.subscription

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import li.gkd.app.MainViewModel
import li.gkd.app.data.AppInfo
import li.gkd.app.data.ExcludeData
import li.gkd.app.data.ruleconfig.RuleGroupConfigService
import li.gkd.app.domain.rule.RuleGroupTarget
import li.gkd.app.data.RawSubscription
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.store.AppStore
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.core.state.Loadable
import li.gkd.app.ui.share.globalGroupAppOrderListState
import li.gkd.app.ui.share.useAppFilter
import li.gkd.app.util.AppSortOption
import li.gkd.app.util.findOption
import li.gkd.db.Db
import li.gkd.db.SubsGlobalGroupConfig

data class SubsGlobalGroupExcludeConfig(
    val subsConfig: SubsGlobalGroupConfig?,
    val excludeData: ExcludeData,
)

data class SubsGlobalGroupExcludeUiState(
    val subscription: RawSubscription,
    val group: RawSubscription.RawGlobalGroup,
    val config: Loadable<SubsGlobalGroupExcludeConfig>,
    val showAppInfos: List<AppInfo>,
    val showAllApps: Boolean,
)

class SubsGlobalGroupExcludeVm(
    val route: SubsGlobalGroupExcludeRoute,
    mainVm: MainViewModel,
) : BaseViewModel() {

    private val subscription = requiredSubscription(route.subsItemId)
    private val subsConfigFlow = Db.subsGlobalGroupConfigDao
        .queryConfig(route.subsItemId, route.groupKey)

    private val appFilter = useAppFilter(
        mainVm = mainVm,
        appGroupType = { it.subsExcludeAppGroupType },
        sortType = { AppSortOption.objects.findOption(it.subsExcludeSort) },
        showBlockApps = { it.subsExcludeShowBlockApp },
        appOrderListState = globalGroupAppOrderListState(
            route.subsItemId,
            route.groupKey,
        ),
    )
    val searchStrFlow = appFilter.searchStrFlow
    val showSearchBarFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val uiState = subscription.buildUiState(
        initialValue = ::buildCurrentUiState,
    ) { rawSubscription ->
        val group = rawSubscription.globalGroups.find { it.key == route.groupKey }
            ?: error("全局规则不存在: ${route.groupKey}")
        val configState = subsConfigFlow.map { config ->
            Loadable.Ready(
                SubsGlobalGroupExcludeConfig(
                    subsConfig = config,
                    excludeData = ExcludeData.parse(config?.exclude),
                ),
            )
        }
        val showAppInfosFlow = combine(
            appFilter.appListFlow,
            storeFlow,
        ) { apps, store ->
            filterInnerDisabledApps(
                rawSubscription = rawSubscription,
                group = group,
                apps = apps,
                showInnerDisabledApps = store.subsExcludeShowInnerDisabledApp,
            )
        }
        combine(
            configState,
            showAppInfosFlow,
            appFilter.showAllAppFlow,
        ) { config, showAppInfos, showAllApps ->
            buildUiState(
                rawSubscription = rawSubscription,
                config = config,
                showAppInfos = showAppInfos,
                showAllApps = showAllApps,
            )
        }
    }

    private fun buildCurrentUiState(
        rawSubscription: RawSubscription,
    ): SubsGlobalGroupExcludeUiState {
        val group = rawSubscription.globalGroups.find { it.key == route.groupKey }
            ?: error("全局规则不存在: ${route.groupKey}")
        return buildUiState(
            rawSubscription = rawSubscription,
            config = Loadable.Loading,
            showAppInfos = filterInnerDisabledApps(
                rawSubscription = rawSubscription,
                group = group,
                apps = appFilter.appListFlow.value,
                showInnerDisabledApps = storeFlow.value.subsExcludeShowInnerDisabledApp,
            ),
            showAllApps = appFilter.showAllAppFlow.value,
        )
    }

    private fun buildUiState(
        rawSubscription: RawSubscription,
        config: Loadable<SubsGlobalGroupExcludeConfig>,
        showAppInfos: List<AppInfo>,
        showAllApps: Boolean,
    ) = SubsGlobalGroupExcludeUiState(
        subscription = rawSubscription,
        group = rawSubscription.globalGroups.find { it.key == route.groupKey }
            ?: error("全局规则不存在: ${route.groupKey}"),
        config = config,
        showAppInfos = showAppInfos,
        showAllApps = showAllApps,
    )

    private fun filterInnerDisabledApps(
        rawSubscription: RawSubscription,
        group: RawSubscription.RawGlobalGroup,
        apps: List<AppInfo>,
        showInnerDisabledApps: Boolean,
    ): List<AppInfo> = if (showInnerDisabledApps) {
        apps
    } else {
        apps.filterNot { appInfo ->
            rawSubscription.getGlobalGroupInnerDisabled(group, appInfo.id)
        }
    }

    val excludeTextFlow: StateFlow<String>
        field = MutableStateFlow("")
    val editableFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)

    private var editBaseExclude: ExcludeData? = null

    private val changedValue: ExcludeData?
        get() {
            val currentExclude = editBaseExclude ?: return null
            val newExclude = ExcludeData.parse(excludeTextFlow.value)
            return if (newExclude != currentExclude) {
                newExclude
            } else {
                null
            }
        }

    val hasUnsavedChanges: Boolean
        get() = changedValue != null

    fun setSearchText(value: String) {
        appFilter.updateSearchStr(value)
    }

    fun setSearchBarVisible(visible: Boolean) {
        showSearchBarFlow.value = visible
    }

    fun setEditable(value: Boolean) {
        if (value && !editableFlow.value) {
            val excludeData = uiState.value.value?.config?.value?.excludeData ?: return
            editBaseExclude = excludeData
            excludeTextFlow.value = excludeData.stringify()
        }
        editableFlow.value = value
    }

    fun setExcludeText(value: String) {
        excludeTextFlow.value = value
    }

    fun setSortType(value: AppSortOption) {
        AppStore.updateSettings { it.copy(subsExcludeSort = value.value) }
    }

    fun setAppGroupType(value: Int) {
        AppStore.updateSettings { it.copy(subsExcludeAppGroupType = value) }
    }

    fun toggleShowInnerDisabledApps() {
        AppStore.updateSettings {
            it.copy(subsExcludeShowInnerDisabledApp = !it.subsExcludeShowInnerDisabledApp)
        }
    }

    fun toggleShowBlockApps() {
        AppStore.updateSettings {
            it.copy(subsExcludeShowBlockApp = !it.subsExcludeShowBlockApp)
        }
    }

    suspend fun saveExcludeText(): Boolean {
        val newExclude = changedValue ?: return false
        RuleGroupConfigService.replaceExclude(
            target = RuleGroupTarget.Global(route.subsItemId, route.groupKey),
            expected = checkNotNull(editBaseExclude),
            value = newExclude,
        )
        editBaseExclude = newExclude
        return true
    }

    suspend fun setAppChecked(appId: String, checked: Boolean) {
        RuleGroupConfigService.updateGroupEnabled(
            RuleGroupTarget.Global(route.subsItemId, route.groupKey, appId),
            checked,
        )
    }
}
