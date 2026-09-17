package li.gkd.app.a11y

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class TopActivityLockTest {
    @Test
    fun blockingForegroundQueryExcludesOtherStateUpdatesUntilItCompletes() {
        val queryEntered = CountDownLatch(1)
        val finishQuery = CountDownLatch(1)
        val updateAttempted = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val events = Collections.synchronizedList(mutableListOf<String>())
        val current = currentTopActivity
        val querying = thread(start = false, isDaemon = true, name = "foreground-query") {
            try {
                A11yState.withTopActivityLock {
                    events.add("query-started")
                    queryEntered.countDown()
                    check(finishQuery.await(10, TimeUnit.SECONDS))
                    // Query callbacks must also be able to read the state reentrantly.
                    A11yState.currentRule
                    events.add("query-completed")
                }
            } catch (error: Throwable) {
                failure.compareAndSet(null, error)
            }
        }
        val updating = thread(start = false, isDaemon = true, name = "foreground-update") {
            try {
                updateAttempted.countDown()
                A11yState.updateTopActivity(current.appId, current.activityId)
                events.add("state-updated")
            } catch (error: Throwable) {
                failure.compareAndSet(null, error)
            }
        }
        try {
            querying.start()
            assertTrue(queryEntered.await(5, TimeUnit.SECONDS))
            updating.start()
            assertTrue(updateAttempted.await(5, TimeUnit.SECONDS))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (updating.isAlive && updating.state != Thread.State.BLOCKED &&
                System.nanoTime() < deadline
            ) {
                Thread.sleep(1)
            }
            assertEquals(
                "The query and the update must share one monitor",
                Thread.State.BLOCKED,
                updating.state,
            )
            assertEquals(listOf("query-started"), events.toList())
        } finally {
            finishQuery.countDown()
            querying.join(5_000)
            if (updating.state != Thread.State.NEW) updating.join(5_000)
        }
        failure.get()?.let { throw AssertionError("Foreground worker failed", it) }
        assertFalse(querying.isAlive)
        assertFalse(updating.isAlive)
        assertEquals(listOf("query-started", "query-completed", "state-updated"), events.toList())
    }
}
