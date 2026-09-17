package li.gkd.app.feature.subscription

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import li.gkd.db.SubsCategoryConfig
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.edit
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.core.state.Loadable
import li.gkd.app.util.EnableGroupOption
import li.gkd.app.util.findOption
import li.gkd.db.Db

data class SubsCategoryUiState(
    val subscription: RawSubscription,
    val categoryConfigMap: Loadable<Map<Int, SubsCategoryConfig>>,
)

class SubsCategoryVm(
    val route: SubsCategoryRoute,
) : BaseViewModel() {
    val showAddCategoryDialogFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)

    private val subscription = requiredSubscription(route.subsItemId)
    private val categoryConfigsFlow = Db.subsCategoryConfigDao.queryConfig(route.subsItemId)

    val uiState = subscription.buildUiState(
        initialValue = { rawSubscription ->
            buildUiState(rawSubscription, Loadable.Loading)
        },
    ) { rawSubscription ->
        categoryConfigsFlow.map { configs ->
            buildUiState(
                rawSubscription = rawSubscription,
                categoryConfigMap = Loadable.Ready(
                    configs.associateBy { it.categoryKey },
                ),
            )
        }
    }

    private fun buildUiState(
        rawSubscription: RawSubscription,
        categoryConfigMap: Loadable<Map<Int, SubsCategoryConfig>>,
    ) = SubsCategoryUiState(
        subscription = rawSubscription,
        categoryConfigMap = categoryConfigMap,
    )

    fun setAddCategoryDialogVisible(visible: Boolean) {
        showAddCategoryDialogFlow.value = visible
    }

    suspend fun setCategoryEnabled(
        category: RawSubscription.RawCategory,
        enabled: Boolean?,
    ): String {
        val option = EnableGroupOption.objects.findOption(enabled)
        val state = uiState.value.value ?: error("订阅尚未加载")
        val rawSubscription = subscription.requireValue()
        val categoryConfigMap = state.categoryConfigMap.value
            ?: error("类别配置尚未加载")
        val oldConfig = categoryConfigMap[category.key]
        Db.subsCategoryConfigDao.upsert(
            (oldConfig ?: SubsCategoryConfig(
                enable = option.value,
                subsId = rawSubscription.id,
                categoryKey = category.key,
            )).copy(enable = option.value),
        )
        return option.label
    }

    suspend fun addCategory(name: String, description: String): String {
        subscription.update { current ->
            if (current.categories.any { category -> category.name == name }) {
                error("不可添加同名类别")
            }
            current.edit {
                putCategory(
                    RawSubscription.RawCategory(
                        key = (current.categories.maxOfOrNull { it.key } ?: -1) + 1,
                        enable = null,
                        name = name,
                        desc = description,
                    ),
                )
            }
        }
        return "添加成功"
    }
}
