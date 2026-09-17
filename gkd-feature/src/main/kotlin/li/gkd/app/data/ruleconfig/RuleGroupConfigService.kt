package li.gkd.app.data.ruleconfig

import kotlinx.coroutines.flow.Flow
import li.gkd.app.data.ExcludeData
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.subscription.SubscriptionRepository
import li.gkd.app.domain.rule.RuleGroupPolicy
import li.gkd.app.domain.rule.RuleGroupTarget
import li.gkd.db.SubsAppGroupConfig
import li.gkd.db.Db
import li.gkd.db.SubsGlobalGroupConfig
import li.gkd.db.SubsGroupConfig
import li.gkd.db.withEnable
import li.gkd.db.withExclude

object RuleGroupConfigService {
    fun groupConfig(target: RuleGroupTarget): Flow<SubsGroupConfig?> {
        return when (target) {
            is RuleGroupTarget.App -> Db.subsAppGroupConfigDao.queryConfig(
                target.subsId,
                target.appId,
                target.groupKey,
            )

            is RuleGroupTarget.Global -> Db.subsGlobalGroupConfigDao.queryConfig(
                target.subsId,
                target.groupKey,
            )
        }
    }

    suspend fun queryGroupConfig(target: RuleGroupTarget): SubsGroupConfig? = when (target) {
        is RuleGroupTarget.App -> Db.subsAppGroupConfigDao.getConfig(
            target.subsId, target.appId, target.groupKey,
        )
        is RuleGroupTarget.Global -> Db.subsGlobalGroupConfigDao.getConfig(
            target.subsId, target.groupKey,
        )
    }

    suspend fun updateGroupEnabled(target: RuleGroupTarget, enabled: Boolean?) {
        updateConfig(target) { current ->
            if (target is RuleGroupTarget.Global && target.pageAppId != null) {
                val exclude = ExcludeData.parse(current.exclude)
                current.withExclude(exclude.copy(
                    appIds = exclude.appIds.toMutableMap().apply {
                        if (enabled == null) remove(target.pageAppId)
                        else set(target.pageAppId, !enabled)
                    },
                ).stringify())
            } else {
                current.withEnable(enabled)
            }
        }
    }

    suspend fun replaceExclude(
        target: RuleGroupTarget,
        expected: ExcludeData,
        value: ExcludeData,
    ) {
        updateConfig(target) { current ->
            check(ExcludeData.parse(current.exclude) == expected) {
                "排除配置已被其他操作修改，请重新打开编辑"
            }
            current.withExclude(value.stringify())
        }
    }

    suspend fun toggleActivityExclusion(target: RuleGroupTarget, appId: String, activityId: String) {
        updateConfig(target) { current ->
            current.withExclude(ExcludeData.parse(current.exclude).switch(appId, activityId).stringify())
        }
    }

    private suspend fun updateConfig(
        target: RuleGroupTarget,
        transform: (SubsGroupConfig) -> SubsGroupConfig,
    ) {
        when (target) {
            is RuleGroupTarget.App -> Db.subscriptionConfigStore.updateAppGroupConfig(
                target.subsId, target.appId, target.groupKey,
            ) { transform(it) as SubsAppGroupConfig }
            is RuleGroupTarget.Global -> Db.subscriptionConfigStore.updateGlobalGroupConfig(
                target.subsId, target.groupKey,
            ) { transform(it) as SubsGlobalGroupConfig }
        }
    }

    suspend fun batchUpdateGroupEnabled(
        groups: Collection<RuleGroupTarget>,
        enabled: Boolean?,
        launcherAppId: String,
        systemAppIds: Set<String>,
    ): List<Pair<RuleGroupTarget, SubsGroupConfig>> {
        if (groups.isEmpty()) return emptyList()
        val subscriptionSnapshot = SubscriptionRepository.awaitSnapshot()
        return Db.withTransaction {
            val subscriptionIds = groups.mapTo(mutableSetOf()) { it.subsId }.toList()
            val configByTarget = (
                Db.subsAppGroupConfigDao.queryBySubsIds(subscriptionIds) +
                    Db.subsGlobalGroupConfigDao.queryBySubsIds(subscriptionIds)
                ).associateBy(::configKey)
            val categoryConfigByKey = Db.subsCategoryConfigDao.querySubsItemConfig(subscriptionIds)
                .associateBy { it.subsId to it.categoryKey }
            val changes = groups.mapNotNull { target ->
                val subscription = subscriptionSnapshot.subscriptions[target.subsId]
                    ?: return@mapNotNull null
                val group = findGroup(subscription, target)
                if (group?.valid != true) return@mapNotNull null

                val currentConfig = configByTarget[configKey(target)]
                val categoryConfig = subscription.getCategory(group.name)?.let { category ->
                    categoryConfigByKey[target.subsId to category.key]
                }
                if (
                    enabled == null &&
                    currentConfig?.enable == null &&
                    currentConfig?.exclude.isNullOrEmpty()
                ) {
                    return@mapNotNull null
                }
                val newConfig = when (target) {
                    is RuleGroupTarget.App -> {
                        val appGroup = group as? RawSubscription.RawAppGroup
                            ?: return@mapNotNull null
                        val category = subscription.getCategory(appGroup.name)
                        val oldEnabled = RuleGroupPolicy.getGroupEnabled(
                            appGroup,
                            currentConfig,
                            category,
                            categoryConfig,
                        )
                        val candidate = currentConfig?.withEnable(enabled) ?: SubsAppGroupConfig(
                            subsId = target.subsId,
                            appId = target.appId,
                            groupKey = target.groupKey,
                            enable = enabled,
                        )
                        val newEnabled = RuleGroupPolicy.getGroupEnabled(
                            appGroup,
                            candidate,
                            category,
                            categoryConfig,
                        )
                        if (enabled == newEnabled && oldEnabled == newEnabled) {
                            return@mapNotNull null
                        }
                        candidate
                    }

                    is RuleGroupTarget.Global -> {
                        val globalGroup = group as? RawSubscription.RawGlobalGroup
                            ?: return@mapNotNull null
                        if (target.pageAppId != null) {
                            val excludeData = ExcludeData.parse(currentConfig?.exclude)
                            if (
                                RuleGroupPolicy.getGlobalGroupChecked(
                                    subscription,
                                    excludeData,
                                    globalGroup,
                                    target.pageAppId,
                                    launcherAppId,
                                    systemAppIds,
                                ) == null
                            ) {
                                return@mapNotNull null
                            }
                            (currentConfig ?: SubsGlobalGroupConfig(
                                subsId = target.subsId,
                                groupKey = target.groupKey,
                            )).withExclude(
                                exclude = excludeData.copy(
                                    appIds = excludeData.appIds.toMutableMap().apply {
                                        if (enabled != null) {
                                            if (!contains(target.pageAppId) && enabled) {
                                                return@mapNotNull null
                                            }
                                            set(target.pageAppId, !enabled)
                                        } else {
                                            if (!contains(target.pageAppId)) {
                                                return@mapNotNull null
                                            }
                                            remove(target.pageAppId)
                                        }
                                    },
                                ).stringify(),
                            )
                        } else {
                            val candidate = currentConfig?.withEnable(enabled) ?: SubsGlobalGroupConfig(
                                subsId = target.subsId,
                                groupKey = target.groupKey,
                                enable = enabled,
                            )
                            val oldEnabled = RuleGroupPolicy.getGroupEnabled(globalGroup, currentConfig)
                            val newEnabled = RuleGroupPolicy.getGroupEnabled(globalGroup, candidate)
                            if (enabled == newEnabled && oldEnabled == newEnabled) {
                                return@mapNotNull null
                            }
                            candidate
                        }
                    }
                }

                if (currentConfig != newConfig) target to newConfig else null
            }
            val newConfigs = changes.map { it.second }
            val obsoleteConfigs = newConfigs.filterIsInstance<SubsAppGroupConfig>().filter {
                it.enable == null && it.exclude.isEmpty()
            }
            newConfigs.filterNot(obsoleteConfigs::contains).forEach { save(it) }
            Db.subsAppGroupConfigDao.delete(*obsoleteConfigs.toTypedArray())
            changes
        }
    }

    private suspend fun save(config: SubsGroupConfig) {
        when (config) {
            is SubsAppGroupConfig -> Db.subsAppGroupConfigDao.upsert(config)
            is SubsGlobalGroupConfig -> Db.subsGlobalGroupConfigDao.upsert(config)
        }
    }

    private fun findGroup(
        subscription: RawSubscription,
        target: RuleGroupTarget,
    ): RawSubscription.RawGroupProps? = when (target) {
        is RuleGroupTarget.App -> subscription.apps
            .find { it.id == target.appId }
            ?.groups
            ?.find { it.key == target.groupKey }

        is RuleGroupTarget.Global -> subscription.globalGroups
            .find { it.key == target.groupKey }
    }

    private fun configKey(config: SubsGroupConfig) = GroupConfigKey(
        subsId = config.subsId,
        appId = (config as? SubsAppGroupConfig)?.appId,
        groupKey = config.groupKey,
    )

    private fun configKey(target: RuleGroupTarget) = GroupConfigKey(
        subsId = target.subsId,
        appId = target.appId,
        groupKey = target.groupKey,
    )

    private data class GroupConfigKey(
        val subsId: Long,
        val appId: String?,
        val groupKey: Int,
    )
}
