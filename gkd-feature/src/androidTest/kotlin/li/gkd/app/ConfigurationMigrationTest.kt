package li.gkd.app

import android.net.Uri
import androidx.room3.Room
import androidx.room3.testing.MigrationTestHelper
import androidx.room3.withWriteTransaction
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.backup.BackupArchiveReader
import li.gkd.app.data.backup.BackupDatabaseData
import li.gkd.app.data.backup.BackupFormat
import li.gkd.app.data.subscription.UsedSubsEntry
import li.gkd.app.domain.rule.RuleGroupPolicy
import li.gkd.app.domain.rule.RuleSummaryBuilder
import li.gkd.db.SubsAppConfig
import li.gkd.db.AppDb
import li.gkd.db.ActivityLog
import li.gkd.db.AppLastVisit
import li.gkd.db.SubsAppGroupConfig
import li.gkd.db.SubsCategoryConfig
import li.gkd.db.SubsGlobalGroupConfig
import li.gkd.db.Migration14To15
import li.gkd.db.SubsItem
import li.gkd.db.SubscriptionConfigSnapshot
import li.gkd.db.SubscriptionConfigStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ConfigurationMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val directory = File(context.cacheDir, "configuration-tests-${UUID.randomUUID()}")
        .apply { check(mkdirs()) }

    private fun helper(name: String) = MigrationTestHelper(
        instrumentation = instrumentation,
        file = File(directory, name),
        driver = AndroidSQLiteDriver(),
        databaseClass = AppDb::class,
    )

    private fun openDatabase(name: String) = Room.databaseBuilder(
        context,
        AppDb::class.java,
        File(directory, name).absolutePath,
    ).addMigrations(Migration14To15)
        .setDriver(AndroidSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    @After
    fun removeTestFiles() {
        directory.deleteRecursively()
    }

    @Test
    fun allReleasedSchemasMigrateWithTheDeviceSqliteDriver() = runBlocking {
        for (version in 1 until 16) {
            val helper = helper("version-$version.db")
            helper.createDatabase(version).close()
            helper.runMigrationsAndValidate(16, listOf(Migration14To15)).close()
        }
    }

    @Test
    fun version15RenamingPreservesDataAndSupportsFurtherWrites() = runBlocking {
        val name = "renaming.db"
        val helper = helper(name)
        helper.createDatabase(15).use { connection ->
            connection.execSQL("INSERT INTO subs_item VALUES (7, 1, 2, 1, 1, 0, NULL)")
            connection.execSQL("INSERT INTO app_config VALUES (0, 7, 'app.one')")
            connection.execSQL("INSERT INTO category_config VALUES (NULL, 7, 3)")
            connection.execSQL("INSERT INTO app_group_config VALUES (7, 'app.one', 4, NULL, 'app-exclude')")
            connection.execSQL("INSERT INTO global_group_config VALUES (7, 4, 1, 'global-exclude')")
            connection.execSQL("INSERT INTO activity_log_v2 VALUES (41, 100, 'app.one', 'MainActivity')")
            connection.execSQL("INSERT INTO app_visit_log VALUES ('app.one', 100), ('app.two', 200)")
            connection.execSQL("""INSERT INTO a11y_event_log VALUES (5, 300, 32, 'app.one', 'Event', 'description', '["first","second"]')""")
        }
        helper.runMigrationsAndValidate(16, listOf(Migration14To15)).use { connection ->
            connection.prepare("SELECT app_id, desc, text FROM a11y_event_log WHERE id = 5").use {
                assertTrue(it.step())
                assertEquals("app.one", it.getText(0))
                assertEquals("description", it.getText(1))
                assertEquals("""["first","second"]""", it.getText(2))
            }
            connection.prepare("SELECT last_visit_time FROM app_last_visit WHERE app_id = 'app.one'").use {
                assertTrue(it.step())
                assertEquals(100L, it.getLong(0))
            }
            connection.prepare("SELECT activity_id FROM activity_log WHERE id = 41").use {
                assertTrue(it.step())
                assertEquals("MainActivity", it.getText(0))
            }
            connection.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }
        }
        val database = openDatabase(name)
        try {
            val store = SubscriptionConfigStore(database)
            val before = store.capture()
            assertEquals(listOf(SubsItem(7, ctime = 1, mtime = 2, enable = true, order = 0)), before.subsItems)
            assertEquals(listOf(SubsAppConfig(false, 7, "app.one")), before.appConfigs)
            assertEquals(listOf(SubsCategoryConfig(null, 7, 3)), before.categoryConfigs)
            assertEquals(listOf(SubsAppGroupConfig(7, "app.one", 4, null, "app-exclude")), before.appGroupConfigs)
            assertEquals(listOf(SubsGlobalGroupConfig(7, 4, true, "global-exclude")), before.globalGroupConfigs)
            assertEquals(listOf("app.two", "app.one"), database.appLastVisitDao().query().first())
            database.appLastVisitDao().insert(AppLastVisit("app.one", 400))
            assertEquals(listOf("app.one", "app.two"), database.appLastVisitDao().query().first())
            assertEquals(listOf(42L), database.activityLogDao().insert(ActivityLog(ctime = 400, appId = "app.two")))
            assertEquals(2, database.activityLogDao().count().first())
            val updatedItem = before.subsItems.single().copy(enable = false)
            database.subsItemDao().upsert(updatedItem)
            assertEquals(before.copy(subsItems = listOf(updatedItem)), store.capture())
            database.subsItemDao().deleteById(7)
            assertEquals(SubscriptionConfigSnapshot(), store.capture())
        } finally {
            database.close()
        }
    }

    @Test
    fun version14MigrationPreservesOverridesAndSwitchChangesSurviveReopening() = runBlocking {
        val name = "migration.db"
        helper(name).createDatabase(14).use { connection ->
            connection.execSQL("INSERT INTO subs_item VALUES (7, 1, 2, 1, 1, 0, NULL)")
            connection.execSQL("INSERT INTO app_config VALUES (20, 0, 7, 'app.one')")
            connection.execSQL("INSERT INTO app_config VALUES (10, 1, 7, 'app.one')")
            connection.execSQL("INSERT INTO category_config VALUES (40, 0, 7, 3)")
            connection.execSQL("INSERT INTO category_config VALUES (30, NULL, 7, 3)")
            connection.execSQL("INSERT INTO subs_config VALUES (60, 2, 1, 7, 'app.one', 4, 'new')")
            connection.execSQL("INSERT INTO subs_config VALUES (50, 2, 0, 7, 'app.one', 4, 'old')")
            connection.execSQL("INSERT INTO subs_config VALUES (80, 3, 1, 7, '', 4, 'new-global')")
            connection.execSQL("INSERT INTO subs_config VALUES (70, 3, 0, 7, '', 4, 'old-global')")
            connection.execSQL("INSERT INTO app_config VALUES (90, 0, 99, 'orphan')")
        }
        val database = openDatabase(name)
        try {
            val store = SubscriptionConfigStore(database)
            val before = store.capture()
            assertEquals(listOf(SubsAppConfig(true, 7, "app.one")), before.appConfigs)
            assertEquals(listOf(SubsCategoryConfig(null, 7, 3)), before.categoryConfigs)
            assertEquals(listOf(SubsAppGroupConfig(7, "app.one", 4, false, "old")), before.appGroupConfigs)
            assertEquals(listOf(SubsGlobalGroupConfig(7, 4, false, "old-global")), before.globalGroupConfigs)
            assertSwitchAgreement(before, false)

            database.subsAppGroupConfigDao().upsert(before.appGroupConfigs.single().copy(enable = true))
            val changed = store.capture()
            assertSwitchAgreement(changed, true)
            assertEquals(1, database.subsAppGroupConfigDao().queryByAppId(7, "app.one").first().size)
        } finally {
            database.close()
        }
        val reopened = openDatabase(name)
        try {
            assertSwitchAgreement(SubscriptionConfigStore(reopened).capture(), true)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun oldZipBackupImportsIdempotentlyAndReexportsWithNullCategoryState() = runBlocking {
        val archive = File(directory, "legacy.zip")
        ZipOutputStream(archive.outputStream()).use { output ->
            output.putNextEntry(ZipEntry("db.json"))
            output.write(legacyBackup.toByteArray())
            output.closeEntry()
        }
        val extracted = File(directory, "extracted")
        BackupArchiveReader.extract(Uri.fromFile(archive), File(directory, "copy.zip"), extracted)
        val restored = BackupFormat.decode(File(extracted, "db.json").readText()).toSnapshot()
        val database = openDatabase("backup.db")
        try {
            val store = SubscriptionConfigStore(database)
            assertEquals(1, store.merge(restored))
            val first = store.capture()
            assertEquals(listOf(SubsCategoryConfig(null, 7, 3)), first.categoryConfigs)
            assertSwitchAgreement(first, true)
            assertEquals(1, store.merge(restored))
            assertEquals(first, store.capture())

            database.subsAppGroupConfigDao().upsert(first.appGroupConfigs.single().copy(enable = false))
            store.merge(restored)
            assertSwitchAgreement(store.capture(), false)

            val exported = BackupFormat.encode(BackupDatabaseData.fromSnapshot(store.capture()))
            assertTrue(exported.contains("\"formatVersion\":2"))
            assertEquals(store.capture(), BackupFormat.decode(exported).toSnapshot())
        } finally {
            database.close()
        }
    }

    @Test
    fun updatingSubscriptionPreservesItsOverridesAndDeletionCleansAllFourTables() = runBlocking {
        val database = openDatabase("foreign-keys.db")
        try {
            val store = SubscriptionConfigStore(database)
            store.merge(sample())
            val before = store.capture()
            val updatedItem = before.subsItems.single().copy(mtime = 3, enable = false)
            database.subsItemDao().upsert(updatedItem)
            assertEquals(before.copy(subsItems = listOf(updatedItem)), store.capture())
            database.subsItemDao().deleteById(7)
            assertEquals(SubscriptionConfigSnapshot(), store.capture())
        } finally {
            database.close()
        }
    }

    @Test
    fun observedConfigurationSnapshotsContainCompleteBatchChanges() = runBlocking {
        val database = openDatabase("observation.db")
        try {
            val store = SubscriptionConfigStore(database)
            coroutineScope {
                val events = Channel<SubscriptionConfigSnapshot>(Channel.UNLIMITED)
                val collector = launch { store.observe().collect { events.send(it) } }
                try {
                    withTimeout(10_000) {
                        assertEquals(SubscriptionConfigSnapshot(), events.receive())
                        store.merge(sample())
                        val before = events.receive()
                        val app = before.appGroupConfigs.single().copy(enable = true)
                        val global = before.globalGroupConfigs.single().copy(enable = false)
                        database.withWriteTransaction {
                            database.subsAppGroupConfigDao().upsert(app)
                            yield()
                            database.subsGlobalGroupConfigDao().upsert(global)
                        }
                        assertEquals(
                            before.copy(appGroupConfigs = listOf(app), globalGroupConfigs = listOf(global)),
                            events.receive(),
                        )
                    }
                } finally {
                    collector.cancelAndJoin()
                    events.close()
                }
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun failedWriteAndCheckpointRestoreRecoverAllConfigurationTables() = runBlocking {
        val database = openDatabase("rollback.db")
        try {
            val store = SubscriptionConfigStore(database)
            store.merge(sample())
            val before = store.capture()
            var failed = false
            try {
                database.withWriteTransaction {
                    store.merge(sample(8))
                    error("import failed")
                }
            } catch (_: IllegalStateException) {
                failed = true
            }
            assertTrue(failed)
            assertEquals(before, store.capture())

            store.merge(sample(8))
            database.subsItemDao().deleteById(7)
            store.restore(before)
            assertEquals(before, store.capture())
        } finally {
            database.close()
        }
    }

    private fun sample(id: Long = 7) = SubscriptionConfigSnapshot(
        subsItems = listOf(SubsItem(id, ctime = 1, mtime = 2, enable = true, order = 0)),
        appConfigs = listOf(SubsAppConfig(true, id, "app.one")),
        categoryConfigs = listOf(SubsCategoryConfig(null, id, 3)),
        appGroupConfigs = listOf(SubsAppGroupConfig(id, "app.one", 4, false)),
        globalGroupConfigs = listOf(SubsGlobalGroupConfig(id, 4, true)),
    )

    private fun assertSwitchAgreement(configs: SubscriptionConfigSnapshot, expected: Boolean) {
        val subscription = RawSubscription.parse(
            """
            {
              id: 7, name: 'Sample', version: 1,
              categories: [{key: 3, name: 'Batch', enable: false}],
              apps: [{id: 'app.one', groups: [{key: 4, name: 'Batch', enable: true, rules: []}]}],
              globalGroups: [{key: 4, name: 'Global', enable: true, rules: []}],
            }
            """.trimIndent(),
        )
        val group = subscription.apps.single().groups.single()
        val uiEnabled = RuleGroupPolicy.getGroupEnabled(
            group,
            configs.appGroupConfigs.find { it.groupKey == 4 },
            subscription.categories.single(),
            configs.categoryConfigs.find { it.categoryKey == 3 },
        )
        val summary = RuleSummaryBuilder.build(
            subscriptions = listOf(UsedSubsEntry(configs.subsItems.single(), subscription)),
            appInfoById = emptyMap(),
            appConfigs = configs.appConfigs,
            groupConfigs = configs.appGroupConfigs + configs.globalGroupConfigs,
            categoryConfigs = configs.categoryConfigs,
        )
        assertEquals(expected, uiEnabled)
        assertEquals(uiEnabled, summary.appIdToAllGroups.getValue("app.one").single().enable)
        assertFalse(summary.globalGroups.isNotEmpty())
    }

    // V1 backups omit null fields and identify overrides with unrelated generated IDs.
    private val legacyBackup = """
        {
          "subsItems": [{"id":7,"ctime":1,"mtime":2,"enable":true,"order":0}],
          "appConfigs": [
            {"id":20,"subsId":7,"appId":"app.one","enable":false},
            {"id":10,"subsId":7,"appId":"app.one","enable":true},
            {"id":90,"subsId":99,"appId":"orphan","enable":false}
          ],
          "categoryConfigs": [{"id":30,"subsId":7,"categoryKey":3}],
          "subsConfigs": [
            {"id":60,"type":2,"subsId":7,"appId":"app.one","groupKey":4,"enable":false},
            {"id":50,"type":2,"subsId":7,"appId":"app.one","groupKey":4},
            {"id":70,"type":3,"subsId":7,"groupKey":4,"enable":false}
          ]
        }
    """.trimIndent()
}
