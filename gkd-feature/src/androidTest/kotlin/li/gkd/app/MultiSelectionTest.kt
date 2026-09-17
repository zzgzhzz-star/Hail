package li.gkd.app

import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.subscription.SubscriptionRepository
import li.gkd.app.data.edit
import li.gkd.app.feature.subscription.SubsAppGroupListRoute
import li.gkd.app.feature.subscription.SubsGlobalGroupListRoute
import li.gkd.app.ui.AppConfigRoute
import li.gkd.app.ui.home.BottomNavItem
import li.gkd.db.Db
import li.gkd.db.LOCAL_SUBS_ID
import li.gkd.db.SubsItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MultiSelectionTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var activity: MainActivity

    private val localId = -910001L
    private val remoteId = 910001L

    @Before
    fun seedSubscriptions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // MainActivity consumes its launch intent. ActivityScenario assumes a non-null intent,
        // so use the same lifecycle-based host setup as HomeDeepLinkTest.
        val command = "am start -W -f 0x10008000 -n " +
            "${instrumentation.targetContext.packageName}/${MainActivity::class.java.name}"
        val output = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command),
        ).bufferedReader().use { it.readText() }
        // am's first-frame wait can time out on a cold emulator even when the activity
        // starts successfully. Verify the actual resumed activity after the UI is idle.
        assertTrue(output, output.contains("Status: ok") || output.contains("Status: timeout"))
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
        }
        runBlocking {
            withTimeout(15_000) { SubscriptionRepository.awaitSnapshot() }
            for ((id, name) in listOf(localId to "多选测试本地", remoteId to "多选测试远程")) {
                val subscription = RawSubscription.parse(
                    """
                    {
                      id: $id, name: '$name', version: 0,
                      apps: [{id: '${META.appId}', groups: [
                        {key: 1, name: '应用规则 A', rules: [{matches: '[text="fixture"]'}]},
                        {key: 2, name: '应用规则 B', rules: [{matches: '[text="fixture"]'}]},
                      ]}],
                      globalGroups: [
                        {key: 1, name: '全局规则 A', matchAnyApp: true, rules: [{matches: '[text="fixture"]'}]},
                        {key: 2, name: '全局规则 B', matchAnyApp: true, rules: [{matches: '[text="fixture"]'}]},
                      ],
                    }
                    """.trimIndent(),
                )
                SubscriptionRepository.saveWithItem(subscription, SubsItem(id = id, order = -10))
            }
        }
        compose.runOnUiThread { activity.mainVm.acceptTermsStep(0) }
    }

    @After
    fun removeFixtures() {
        runBlocking { SubscriptionRepository.delete(localId, remoteId) }
        if (::activity.isInitialized) {
            compose.runOnUiThread { activity.finish() }
        }
    }

    private fun open(route: NavKey) {
        compose.runOnUiThread {
            activity.mainVm.navigatePage(route, loc = "li.gkd.app.MultiSelectionTest")
        }
    }

    private fun awaitText(text: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun back() {
        compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
    }

    private fun assertToolbarAnimates(rowText: String) {
        awaitText(rowText)
        compose.waitForIdle()
        val normalRowTop = compose.onAllNodesWithText(rowText, substring = true).onFirst()
            .fetchSemanticsNode().positionInRoot.y
        compose.mainClock.autoAdvance = false
        try {
            compose.onAllNodesWithText(rowText, substring = true).onFirst()
                .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(80)
            val enteringTop = compose.onNodeWithContentDescription("全选")
                .fetchSemanticsNode().positionInRoot.y
            val enteringTitleTop = compose.onNodeWithText("已选 1 项")
                .fetchSemanticsNode().positionInRoot.y
            val enteringTitleLeft = compose.onNodeWithText("已选 1 项")
                .fetchSemanticsNode().positionInRoot.x
            compose.mainClock.advanceTimeBy(800)
            val settledTop = compose.onNodeWithContentDescription("全选")
                .fetchSemanticsNode().positionInRoot.y
            val settledTitleTop = compose.onNodeWithText("已选 1 项")
                .fetchSemanticsNode().positionInRoot.y
            val settledTitleLeft = compose.onNodeWithText("已选 1 项")
                .fetchSemanticsNode().positionInRoot.x
            assertTrue("$rowText: toolbar must slide up when entering selection", enteringTop > settledTop + 1f)
            assertEquals("$rowText: entering title must not move sideways", settledTitleLeft, enteringTitleLeft, 1f)
            assertTrue("$rowText: title must slide up when entering selection", enteringTitleTop > settledTitleTop + 1f)
            assertEquals("$rowText: title and actions must travel together",
                enteringTop - settledTop, enteringTitleTop - settledTitleTop, 1f)
            assertEquals("$rowText: changing title must not resize the top bar", normalRowTop,
                compose.onAllNodesWithText(rowText, substring = true).onFirst()
                    .fetchSemanticsNode().positionInRoot.y, 1f)

            back()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(40)
            val exitingTop = compose.onNodeWithContentDescription("全选")
                .fetchSemanticsNode().positionInRoot.y
            assertTrue("$rowText: toolbar must slide down before disappearing", exitingTop > settledTop + 1f)
            val exitingTitleTop = compose.onNodeWithText("已选 1 项")
                .fetchSemanticsNode().positionInRoot.y
            assertEquals("$rowText: exiting title must not move sideways", settledTitleLeft,
                compose.onNodeWithText("已选 1 项").fetchSemanticsNode().positionInRoot.x, 1f)
            assertTrue("$rowText: title must retain its count and slide down before disappearing", exitingTitleTop > settledTitleTop + 1f)
            assertEquals("$rowText: outgoing title and actions must travel together",
                exitingTop - settledTop, exitingTitleTop - settledTitleTop, 1f)
            val exitingTitle = compose.onNodeWithText("已选 1 项", useUnmergedTree = true).fetchSemanticsNode()
            assertTrue("$rowText: outgoing title must be hidden from accessibility",
                generateSequence(exitingTitle) { it.parent }.any {
                    it.config.contains(SemanticsProperties.HideFromAccessibility)
                })
            compose.onNodeWithText("已选 0 项").assertDoesNotExist()
            compose.mainClock.advanceTimeBy(800)
            compose.onNodeWithContentDescription("全选").assertDoesNotExist()
            compose.onNodeWithText("已选 1 项").assertDoesNotExist()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun allFourToolbarsAnimateBothEnteringAndLeavingSelection() {
        compose.runOnUiThread { activity.mainVm.handleClickTab(BottomNavItem.SubsManage) }
        assertToolbarAnimates("多选测试本地")
        open(SubsAppGroupListRoute(localId, META.appId))
        assertToolbarAnimates("应用规则 A")
        open(SubsGlobalGroupListRoute(localId))
        assertToolbarAnimates("全局规则 A")
        runBlocking {
            for (id in listOf(localId, remoteId)) Db.subsItemDao.updateEnable(id, true)
        }
        open(AppConfigRoute(META.appId))
        assertToolbarAnimates("应用规则 A")
    }

    // Capture the icon itself to check fading without exposing animation state.
    private fun iconContrast(image: ImageBitmap): Float {
        val pixels = image.toPixelMap()
        val background = pixels[0, 0]
        var contrast = 0f
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val color = pixels[x, y]
                contrast += abs(color.red - background.red) +
                    abs(color.green - background.green) + abs(color.blue - background.blue)
            }
        }
        return contrast / (pixels.width * pixels.height)
    }

    @Test
    fun subscriptionLeftGroupSlidesWithActionsAndKeepsCountDuringRapidReentry() {
        compose.runOnUiThread { activity.mainVm.handleClickTab(BottomNavItem.SubsManage) }
        awaitText("多选测试本地")
        compose.waitForIdle()
        val normalTitlePosition = compose.onAllNodesWithText("订阅").fetchSemanticsNodes()
            .minBy { it.positionInRoot.y }.positionInRoot
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithText("多选测试本地", substring = true)
                .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(40)
            val enteringTitle = compose.onNodeWithText("已选 1 项").fetchSemanticsNode().positionInRoot
            val enteringClose = compose.onNodeWithContentDescription("取消选择").fetchSemanticsNode().positionInRoot
            val enteringAction = compose.onNodeWithContentDescription("全选").fetchSemanticsNode().positionInRoot
            val outgoingNormalTitle = compose.onAllNodesWithText("订阅").fetchSemanticsNodes()
                .minBy { it.positionInRoot.y }.positionInRoot
            assertEquals("The old title must not move sideways", normalTitlePosition.x, outgoingNormalTitle.x, 1f)
            assertTrue("The old title must slide up immediately", outgoingNormalTitle.y < normalTitlePosition.y - 1f)
            compose.mainClock.advanceTimeBy(120)
            val enteringContrast = iconContrast(compose.onNodeWithContentDescription("取消选择").captureToImage())
            compose.mainClock.advanceTimeBy(800)
            val settledTitle = compose.onNodeWithText("已选 1 项").fetchSemanticsNode().positionInRoot
            val settledClose = compose.onNodeWithContentDescription("取消选择").fetchSemanticsNode().positionInRoot
            val settledAction = compose.onNodeWithContentDescription("全选").fetchSemanticsNode().positionInRoot
            assertEquals("The new title must not move sideways", settledTitle.x, enteringTitle.x, 1f)
            assertEquals("The close button must not move sideways", settledClose.x, enteringClose.x, 1f)
            assertTrue("The new title must slide up", enteringTitle.y > settledTitle.y + 1f)
            assertEquals("The close button and title must move as one group",
                enteringTitle.y - settledTitle.y, enteringClose.y - settledClose.y, 1f)
            assertEquals("The left group and right actions must move together",
                enteringAction.y - settledAction.y, enteringTitle.y - settledTitle.y, 1f)
            val settledContrast = iconContrast(compose.onNodeWithContentDescription("取消选择").captureToImage())
            assertTrue("The close button must be partly faded in at the intermediate frame: $enteringContrast / $settledContrast",
                enteringContrast > settledContrast * 0.05f && enteringContrast < settledContrast * 0.95f)

            compose.onNodeWithText("多选测试远程", substring = true).performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(80)
            assertEquals("Updating the count must not restart the title transition", settledTitle,
                compose.onNodeWithText("已选 2 项").fetchSemanticsNode().positionInRoot)
            back()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(32)
            compose.onNodeWithText("已选 2 项").assertExists()
            compose.onNodeWithText("已选 0 项").assertDoesNotExist()
            val exitingTitle = compose.onNodeWithText("已选 2 项").fetchSemanticsNode().positionInRoot
            assertEquals("The selected title must keep its horizontal position", settledTitle.x, exitingTitle.x, 1f)
            assertTrue("The selected title must slide down while fading out", exitingTitle.y > settledTitle.y)
            compose.onNodeWithContentDescription("取消选择").assertIsNotEnabled()

            compose.onNodeWithText("多选测试远程", substring = true)
                .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(32)
            assertEquals("Reversing the transition must not move the title sideways", settledTitle.x,
                compose.onNodeWithText("已选 1 项").fetchSemanticsNode().positionInRoot.x, 1f)
            compose.mainClock.advanceTimeBy(800)
            compose.onNodeWithText("已选 1 项").assertIsDisplayed()
            compose.onNodeWithContentDescription("取消选择").assertIsEnabled()
            back()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(800)
            compose.onNodeWithContentDescription("取消选择").assertDoesNotExist()
            assertEquals(normalTitlePosition, compose.onAllNodesWithText("订阅").fetchSemanticsNodes()
                .minBy { it.positionInRoot.y }.positionInRoot)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun closeIconCannotNavigateBackDuringItsExitAnimation() {
        runBlocking {
            SubscriptionRepository.update(localId) {
                it.copy(name = "用于验证多选顶栏在窄屏和大字体下显示的较长订阅标题")
            }
        }
        open(SubsAppGroupListRoute(localId, META.appId))
        awaitText("应用规则 A")
        compose.onNodeWithText("应用规则 A").performTouchInput { longClick() }
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithContentDescription("关闭").performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(80)
            compose.onNodeWithContentDescription("返回").onParent().assertIsNotEnabled()
            compose.onNodeWithContentDescription("返回").performTouchInput { click() }
            compose.mainClock.advanceTimeBy(800)
            compose.onNodeWithText("应用规则 A").assertIsDisplayed()
            compose.onNodeWithContentDescription("返回").onParent().assertIsEnabled()
            compose.onNodeWithText("已选 1 项").assertDoesNotExist()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun appRulesKeepZeroSelectionAndBackClosesMenuBeforeExitingMode() {
        open(SubsAppGroupListRoute(localId, META.appId))
        awaitText("应用规则 A")
        compose.onNodeWithText("应用规则 A").performTouchInput { longClick() }
        compose.onNodeWithText("已选 1 项").assertIsDisplayed()
        compose.onNodeWithContentDescription("全选").performClick()
        compose.onNodeWithText("已选 2 项").assertIsDisplayed()
        compose.onNodeWithContentDescription("全选").assertIsNotEnabled()
        compose.onNodeWithContentDescription("反选").performClick()
        compose.onNodeWithText("已选 0 项").assertIsDisplayed()
        compose.onNodeWithContentDescription("更多").assertIsNotEnabled()
        compose.onNodeWithContentDescription("反选").performClick()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("复制规则").assertIsDisplayed()
        compose.onNodeWithText("全部启用").assertIsDisplayed()
        compose.onNodeWithText("全部关闭").assertIsDisplayed()
        compose.onNodeWithText("重置开关").assertIsDisplayed()
        compose.onNodeWithText("删除规则").assertIsDisplayed()
        // Popup back is dispatched through Android's input system, as on a device.
        InstrumentationRegistry.getInstrumentation()
            .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("复制规则").assertDoesNotExist()
        compose.onNodeWithText("已选 2 项").assertIsDisplayed()
        back()
        compose.onNodeWithContentDescription("全选").assertDoesNotExist()
        compose.onNodeWithText("应用规则 A").assertIsDisplayed()
    }

    @Test
    fun globalDeletionCancelPreservesSelectionAndConfirmedDeletionLeavesRemainingRules() {
        open(SubsGlobalGroupListRoute(localId))
        awaitText("全局规则 A")
        compose.onNodeWithText("全局规则 A", substring = true).performTouchInput { longClick() }
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("删除规则").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("已选 1 项").assertIsDisplayed()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("删除规则").performClick()
        compose.onNodeWithText("确定").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("全局规则 A", substring = true).fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("全局规则 B", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("全选").assertDoesNotExist()
        // A single remaining item must still be selectable by long press.
        compose.onNodeWithText("全局规则 B", substring = true).performTouchInput { longClick() }
        compose.onNodeWithText("已选 1 项").assertIsDisplayed()
    }

    @Test
    fun applicationConfigDisablesCopyForGlobalOnlySelectionAndRetainsModeAfterRefresh() {
        runBlocking {
            // Application configuration lists rules from enabled subscriptions only.
            for (id in listOf(localId, remoteId)) Db.subsItemDao.updateEnable(id, true)
        }
        open(AppConfigRoute(META.appId))
        awaitText("全局规则 A")
        compose.onAllNodesWithText("全局规则 A", substring = true).onFirst().performTouchInput { longClick() }
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("复制规则").assertIsNotEnabled()
        InstrumentationRegistry.getInstrumentation()
            .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        runBlocking {
            for (id in listOf(localId, remoteId)) {
                SubscriptionRepository.update(id) { current ->
                    current.edit { removeGlobalGroups { it.key == 1 } }
                }
            }
        }
        awaitText("已选 0 项")
        compose.onNodeWithContentDescription("全选").assertIsEnabled()
        compose.onNodeWithContentDescription("更多").assertIsNotEnabled()
    }

    @Test
    fun subscriptionLongPressPreservesSelectionAndDoesNotReorderInSelectionMode() {
        compose.runOnUiThread { activity.mainVm.handleClickTab(BottomNavItem.SubsManage) }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("多选测试本地", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        val first = compose.onNode(hasText("多选测试本地", substring = true))
        val second = compose.onNode(hasText("多选测试远程", substring = true))
        val before = runBlocking { Db.subsItemDao.queryAll().associate { it.id to it.order } }
        first.performTouchInput { longClick() }
        compose.onNodeWithText("已选 1 项").assertIsDisplayed()
        second.performClick()
        compose.onNodeWithText("已选 2 项").assertIsDisplayed()
        first.performTouchInput {
            down(center)
            advanceEventTime(800)
            moveTo(center + Offset(0f, 120f), delayMillis = 500)
            up()
        }
        compose.onNodeWithText("已选 2 项").assertIsDisplayed()
        assertEquals(before, runBlocking { Db.subsItemDao.queryAll().associate { it.id to it.order } })
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("删除订阅").assertIsDisplayed()
        assertTrue(runBlocking { SubscriptionRepository.awaitSnapshot().subscriptions.containsKey(localId) })
        InstrumentationRegistry.getInstrumentation()
            .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithContentDescription("取消选择").performClick()
        compose.onNodeWithText("已选 2 项").assertDoesNotExist()
        compose.onNodeWithContentDescription("取消选择").assertDoesNotExist()
        first.assertIsDisplayed()
    }

    @Test
    fun subscriptionDragFromNormalModeStillReordersAndExitsSelection() {
        compose.runOnUiThread { activity.mainVm.handleClickTab(BottomNavItem.SubsManage) }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("多选测试本地", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        val first = compose.onNode(hasText("多选测试本地", substring = true))
        val second = compose.onNode(hasText("多选测试远程", substring = true))
        val distance = second.fetchSemanticsNode().boundsInRoot.center.y -
            first.fetchSemanticsNode().boundsInRoot.center.y
        assertTrue("The two fixture rows must occupy different positions", distance != 0f)
        val movedDown = distance > 0f
        first.performTouchInput {
            down(center)
            advanceEventTime(800)
            repeat(12) { moveBy(Offset(0f, distance / 10), delayMillis = 60) }
            up()
        }
        compose.waitUntil(10_000) {
            val orders = runBlocking { Db.subsItemDao.queryAll().associate { it.id to it.order } }
            // Renumbering the original order must not count as a successful drag.
            if (movedDown) {
                orders.getValue(localId) > orders.getValue(remoteId)
            } else {
                orders.getValue(localId) < orders.getValue(remoteId)
            }
        }
        compose.waitForIdle()
        val reorderedDistance = second.fetchSemanticsNode().boundsInRoot.center.y -
            first.fetchSemanticsNode().boundsInRoot.center.y
        assertTrue("The two fixture rows must swap their relative position",
            if (movedDown) reorderedDistance < 0f else reorderedDistance > 0f)
        compose.onNodeWithContentDescription("全选").assertDoesNotExist()
    }

    @Test
    fun mixedSubscriptionDeletionKeepsProtectedLocalSubscriptionSelected() {
        compose.runOnUiThread { activity.mainVm.handleClickTab(BottomNavItem.SubsManage) }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("本地订阅", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText("本地订阅", substring = true)).performTouchInput { longClick() }
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("删除订阅（本地订阅不可删除）").assertIsNotEnabled()
        InstrumentationRegistry.getInstrumentation()
            .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNode(hasText("多选测试远程", substring = true)).performClick()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("删除订阅").performClick()
        compose.onNode(hasText("不包含本地订阅", substring = true)).assertIsDisplayed()
        compose.onNodeWithText("确定").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("多选测试远程", substring = true)).fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("已选 1 项").assertIsDisplayed()
        assertTrue(runBlocking { Db.subsItemDao.queryAll().any { it.id == LOCAL_SUBS_ID } })
    }
}
