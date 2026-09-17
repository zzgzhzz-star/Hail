package li.gkd.app.domain.rule

import li.gkd.app.data.AppRule
import li.gkd.app.data.GlobalRule
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.ResolvedAppGroup
import li.gkd.app.data.ResolvedGlobalGroup

data class RuleSummary(
    val globalRules: List<GlobalRule> = emptyList(),
    val globalGroups: List<ResolvedGlobalGroup> = emptyList(),
    val appIdToRules: Map<String, List<AppRule>> = emptyMap(),
    val appIdToGroups: Map<String, List<RawSubscription.RawAppGroup>> = emptyMap(),
    val appIdToAllGroups: Map<String, List<ResolvedAppGroup>> = emptyMap(),
) {
    val appSize = appIdToRules.keys.size
    val appGroupSize = appIdToGroups.values.sumOf { it.size }
    val slowGlobalGroups = globalRules
        .filter { it.isSlow }
        .distinctBy { it.group }
        .map { it.group to it }
    val slowAppGroups = appIdToRules.values
        .flatten()
        .filter { it.isSlow }
        .distinctBy { it.group }
        .map { it.group to it }
    val slowGroupCount = slowGlobalGroups.size + slowAppGroups.size
}
