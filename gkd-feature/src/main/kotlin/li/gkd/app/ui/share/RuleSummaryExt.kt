package li.gkd.app.ui.share

import li.gkd.app.domain.rule.RuleSummary
import li.gkd.app.util.EMPTY_RULE_TIP

val RuleSummary.numText: String
    get() = if (globalGroups.size + appGroupSize > 0) {
        if (globalGroups.isNotEmpty()) {
            "${globalGroups.size}全局" + if (appGroupSize > 0) "/" else ""
        } else {
            ""
        } + if (appGroupSize > 0) {
            "${appSize}应用/${appGroupSize}规则"
        } else {
            ""
        }
    } else {
        EMPTY_RULE_TIP
    }

fun RuleSummary.statusText(actionCount: Long): String = if (actionCount > 0) {
    "$numText/${actionCount}触发"
} else {
    numText
}
