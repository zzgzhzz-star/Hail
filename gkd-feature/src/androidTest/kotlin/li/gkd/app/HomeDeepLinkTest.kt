package li.gkd.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import li.gkd.app.entry.OpenSchemeActivity
import li.gkd.app.feature.settings.AdvancedPageRoute
import li.gkd.app.feature.snapshot.SnapshotSettingsRoute
import li.gkd.app.ui.home.BottomNavItem
import li.gkd.app.ui.home.HomeRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class HomeDeepLinkTest {
    @Test
    fun notificationDeepLinkReturnsToSettingsInExistingActivity() {
        assertHomeDeepLink(
            uri = "gkd://page?tab=3",
            entryActivity = MainActivity::class.java,
            expectedTab = BottomNavItem.Settings.key,
        )
    }

    @Test
    fun schemeEntryDeepLinkReturnsToSubscriptionsInExistingActivity() {
        assertHomeDeepLink(
            uri = "gkd://page?tab=1",
            entryActivity = OpenSchemeActivity::class.java,
            expectedTab = BottomNavItem.SubsManage.key,
        )
    }

    @Test
    fun homeDeepLinkWithoutTabReturnsHomeAndKeepsSelectedTab() {
        assertHomeDeepLink(
            uri = "gkd://page",
            entryActivity = OpenSchemeActivity::class.java,
            expectedTab = BottomNavItem.AppList.key,
        )
    }

    private fun assertHomeDeepLink(
        uri: String,
        entryActivity: Class<out Activity>,
        expectedTab: Int,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Shell launch also works on devices that block instrumentation's background activity starts.
        val command = "am start -W -f 0x10008000 -n " +
            "${instrumentation.targetContext.packageName}/${MainActivity::class.java.name}"
        val output = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command),
        ).bufferedReader().use { it.readText() }
        assertTrue(output, output.contains("Status: ok"))
        instrumentation.waitForIdleSync()
        lateinit var originalActivity: MainActivity
        instrumentation.runOnMainSync {
            originalActivity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
        }
        try {
            lateinit var originalViewModel: MainViewModel
            val intentReceived = CountDownLatch(1)
            instrumentation.runOnMainSync {
                val activity = originalActivity
                originalViewModel = activity.mainVm
                originalViewModel.handleClickTab(BottomNavItem.AppList)
                // The call-site compiler plugin does not populate locations in androidTest sources.
                originalViewModel.navigatePage(AdvancedPageRoute, loc = "li.gkd.app.HomeDeepLinkTest")
                originalViewModel.navigatePage(SnapshotSettingsRoute, loc = "li.gkd.app.HomeDeepLinkTest")
                assertEquals(SnapshotSettingsRoute, originalViewModel.topRoute)
                activity.addOnNewIntentListener { intent ->
                    if (intent.data == Uri.parse(uri)) intentReceived.countDown()
                }
                activity.startActivity(
                    Intent(activity, entryActivity).apply {
                        data = Uri.parse(uri)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    },
                )
            }
            assertTrue(
                "The existing activity must receive the deep link",
                intentReceived.await(10, TimeUnit.SECONDS),
            )
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                assertSame(originalActivity, activity)
                assertSame(originalViewModel, activity.mainVm)
                assertEquals(listOf(HomeRoute), activity.mainVm.backStack.toList())
                assertEquals(expectedTab, activity.mainVm.tabFlow.value)
            }
        } finally {
            instrumentation.runOnMainSync { originalActivity.finishAndRemoveTask() }
            instrumentation.waitForIdleSync()
        }
    }
}
