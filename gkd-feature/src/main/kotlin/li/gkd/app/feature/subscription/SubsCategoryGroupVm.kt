package li.gkd.app.feature.subscription

import li.gkd.app.domain.rule.RuleGroupTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import li.gkd.app.MainViewModel
import li.gkd.app.data.batchResetAppGroupEnable
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.ruleconfig.RuleGroupConfigService
import li.gkd.app.domain.rule.RuleGroupPolicy
import li.gkd.app.data.edit
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.store.AppStore
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.core.state.Loadable
import li.gkd.app.ui.share.filterSubsApps
import li.gkd.app.ui.share.subsAppActionOrderMapState
import li.gkd.app.ui.share.useSubsAppFilter
import li.gkd.app.util.AppSortOption
import li.gkd.app.util.EnableGroupOption
import li.gkd.app.data.appinfo.AppInfoRepository
import li.gkd.app.util.findOption
import li.gkd.app.store.AppStore.blockMatchAppListFlow
import li.gkd.db.SubsAppGroupConfig
import li.gkd.db.SubsCategoryConfig
import li.gkd.db.Db

data class SubsCategoryGroupConfigs(
    val subsConfigs: List<SubsAppGroupConfig>,
    val categoryConfig: SubsCategoryConfig?,
)

data class SubsCategoryGroupUiState(
    val subscription: RawSubscription,
    val category: RawSubscription.RawCategory,
    val apps: List<RawSubscription.RawApp>,
    val configs: Loadable<SubsCategoryGroupConfigs>,
    val showAllApps: Boolean,
)

class SubsCategoryGroupVm(
    val route: SubsCategoryGroupRoute,
    private val mainVm: MainViewModel,
) : BaseViewModel() {
    val showEditCategoryDialogFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)

    private val subscription = requiredSubscription(route.subsId)
    private val subsConfigsFlow = Db.subsAppGroupConfigDao.queryBySubsId(route.subsId)
    private val categoryConfigFlow =
        Db.subsCategoryConfigDao.queryCategoryConfig(route.subsId, route.categoryKey)
    private val appActionOrderMapState = subsAppActionOrderMapState(route.subsId)

    val uiState = subscription.buildUiState(
        initialValue = ::buildCurrentUiState,
    ) { rawSubscription ->
        val rawApps = rawSubscription.getCategoryApps(route.categoryKey)
        val appsFlow = useSubsAppFilter(
            mainVm = mainVm,
            appsFlow = flowOf(rawApps),
            appGroupType = { it.subsCategoryGroupType },
            sortType = { AppSortOption.objects.findOption(it.subsCategorySort) },
            showBlockApps = { it.subsCategoryShowBlock },
            appActionOrderMapState = appActionOrderMapState,
        )
        combine(
            appsFlow,
            subsConfigsFlow,
            categoryConfigFlow,
        ) { apps, configs, categoryConfig ->
            buildUiState(
                rawSubscription = rawSubscription,
                apps = apps,
                configs = Loadable.Ready(
                    SubsCategoryGroupConfigs(
                        subsConfigs = configs,
                        categoryConfig = categoryConfig,
                    ),
                ),
            )
        }
    }

    private fun buildCurrentUiState(
        rawSubscription: RawSubscription,
    ): SubsCategoryGroupUiState {
        val settings = storeFlow.value
        val apps = filterSubsApps(
            apps = rawSubscription.getCategoryApps(route.categoryKey),
            appMap = AppInfoRepository.appInfoMapFlow.value,
            settings = settings,
            appActionOrderMap = appActionOrderMapState.value.value.orEmpty(),
            appVisitOrderMap = mainVm.appVisitOrderMapState.value.value.orEmpty(),
            blockSet = blockMatchAppListFlow.value,
            appGroupType = { it.subsCategoryGroupType },
            sortType = { AppSortOption.objects.findOption(it.subsCategorySort) },
            showBlockApps = { it.subsCategoryShowBlock },
        )
        return buildUiState(
            rawSubscription = rawSubscription,
            apps = apps,
            configs = Loadable.Loading,
        )
    }

    private fun buildUiState(
        rawSubscription: RawSubscription,
        apps: List<RawSubscription.RawApp>,
        configs: Loadable<SubsCategoryGroupConfigs>,
    ) = SubsCategoryGroupUiState(
        subscription = rawSubscription,
        category = rawSubscription.getSafeCategory(route.categoryKey),
        apps = apps,
        configs = configs,
        showAllApps = rawSubscription.getCategoryApps(route.categoryKey).size == apps.size,
    )

    fun setEditCategoryDialogVisible(visible: Boolean) {
        showEditCategoryDialogFlow.value = visible
    }

    fun setSortType(option: AppSortOption) {
        AppStore.updateSettings { it.copy(subsCategorySort = option.value) }
    }

    fun setAppGroupType(value: Int) {
        AppStore.updateSettings { it.copy(subsCategoryGroupType = value) }
    }

    fun toggleShowBlockApps() {
        AppStore.updateSettings { it.copy(subsCategoryShowBlock = !it.subsCategoryShowBlock) }
    }

    suspend fun toggleCategoryEnabled(): String {
        val state = uiState.value.value ?: error("订阅尚未加载")
        val rawSubscription = subscription.requireValue()
        val category = state.category
        val configs = state.configs.value ?: error("类别配置尚未加载")
        val categoryConfig = configs.categoryConfig
        val newValue = when (RuleGroupPolicy.getCategoryEnabled(category, categoryConfig)) {
            false -> null
            null -> true
            true -> false
        }
        val option = EnableGroupOption.objects.findOption(newValue)
        Db.subsCategoryConfigDao.upsert(
            (categoryConfig ?: SubsCategoryConfig(
                enable = option.value,
                subsId = rawSubscription.id,
                categoryKey = category.key,
            )).copy(enable = option.value),
        )
        return option.label
    }

    suspend fun resetAllRuleSwitches(): Int {
        val state = uiState.value.value ?: error("订阅尚未加载")
        val rawSubscription = subscription.requireValue()
        return Db.subsAppGroupConfigDao.batchResetAppGroupEnable(
            rawSubscription.id,
            state.apps.flatMap { app -> app.groups }.map { group ->
                group to rawSubscription.getAppByGroup(group)
            },
        ).size
    }

    suspend fun setGroupEnabled(
        appId: String,
        group: RawSubscription.RawAppGroup,
        enabled: Boolean,
    ) {
        RuleGroupConfigService.updateGroupEnabled(
            RuleGroupTarget.App(route.subsId, appId, group.key),
            enabled,
        )
    }

    suspend fun updateCategory(name: String, description: String): String {
        val changed = subscription.update { current ->
            val category = current.getSafeCategory(route.categoryKey)
            if (current.categories.any { it.key != category.key && it.name == name }) {
                error("不可添加同名类别")
            }
            if (category.name == name && (category.desc ?: "") == description) {
                current
            } else {
                current.edit {
                    val updated = updateCategory(category.key) {
                        copy(name = name, desc = description)
                    }
                    if (!updated) error("类别已不存在")
                }
            }
        }
        return if (changed) "更新成功" else "未修改"
    }

    suspend fun deleteCategory() {
        subscription.update { current ->
            current.edit { removeCategory(route.categoryKey) }
        }
        Db.subsCategoryConfigDao.deleteByCategoryKey(
            route.subsId,
            route.categoryKey,
        )
    }
}
