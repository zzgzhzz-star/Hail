package li.gkd.app.data

import li.gkd.db.SubsAppGroupConfig
import li.gkd.db.Db

suspend fun SubsAppGroupConfig.SubsAppGroupConfigDao.batchResetAppGroupEnable(
    subsItemId: Long,
    list: List<Pair<RawSubscription.RawAppGroup, RawSubscription.RawApp>>,
): List<Pair<RawSubscription.RawAppGroup, RawSubscription.RawApp>> = Db.withTransaction {
    list.filter { (group, app) ->
        resetEnable(subsItemId, app.id, group.key) > 0
    }
}
