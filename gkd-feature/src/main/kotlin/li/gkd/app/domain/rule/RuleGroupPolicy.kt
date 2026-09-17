package li.gkd.app.domain.rule

import li.gkd.app.data.ExcludeData
import li.gkd.app.data.RawSubscription
import li.gkd.db.SubsCategoryConfig
import li.gkd.db.SubsGroupConfig

object RuleGroupPolicy {
    fun getCategoryEnabled(
        category: RawSubscription.RawCategory?,
        categoryConfig: SubsCategoryConfig?,
    ): Boolean? = if (categoryConfig != null) {
        // 已保存的 null 表示使用各规则组默认值，而不是类别默认值。
        categoryConfig.enable
    } else {
        category?.enable
    }

    fun getGroupEnabled(
        group: RawSubscription.RawGroupProps,
        subsConfig: SubsGroupConfig?,
        category: RawSubscription.RawCategory? = null,
        categoryConfig: SubsCategoryConfig? = null,
    ): Boolean = group.valid && when (group) {
        is RawSubscription.RawAppGroup -> subsConfig?.enable
            ?: getCategoryEnabled(category, categoryConfig)
            ?: group.enable
            ?: true

        is RawSubscription.RawGlobalGroup -> subsConfig?.enable ?: group.enable ?: true
    }

    fun getGlobalGroupChecked(
        subscription: RawSubscription,
        excludeData: ExcludeData,
        group: RawSubscription.RawGlobalGroup,
        appId: String,
        launcherAppId: String,
        systemAppIds: Set<String>,
    ): Boolean? {
        if (subscription.getGlobalGroupInnerDisabled(group, appId)) {
            return null
        }
        excludeData.appIds[appId]?.let { return !it }
        if (group.appIdEnable[appId] == true) return true
        if (appId == launcherAppId) {
            return group.matchLauncher ?: false
        }
        if (appId in systemAppIds) {
            return group.matchSystemApp ?: false
        }
        return group.matchAnyApp ?: true
    }

    fun getActualGroupChecked(
        subscription: RawSubscription,
        group: RawSubscription.RawGroupProps,
        appId: String?,
        subsConfig: SubsGroupConfig?,
        categoryConfig: SubsCategoryConfig?,
        launcherAppId: String,
        systemAppIds: Set<String>,
    ): Boolean {
        if (!group.valid) return false
        return if (appId != null && group is RawSubscription.RawGlobalGroup) {
            getGlobalGroupChecked(
                subscription = subscription,
                excludeData = ExcludeData.parse(subsConfig?.exclude),
                group = group,
                appId = appId,
                launcherAppId = launcherAppId,
                systemAppIds = systemAppIds,
            )
        } else {
            getGroupEnabled(
                group,
                subsConfig,
                subscription.getCategory(group.name),
                categoryConfig,
            )
        } ?: false
    }
}
