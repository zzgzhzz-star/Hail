package li.gkd.app.data.subscription

import android.util.AtomicFile
import li.gkd.app.data.RawSubscription
import li.gkd.app.util.FolderUtils
import li.gkd.app.util.json
import li.gkd.db.LOCAL_HTTP_SUBS_ID
import li.gkd.db.LOCAL_SUBS_ID
import java.io.File
import java.io.FileOutputStream

object SubscriptionFileStore {
    fun load(id: Long): RawSubscription {
        val file = file(id)
        if (!file.exists()) {
            return when (id) {
                LOCAL_SUBS_ID -> RawSubscription(id = id, name = "本地订阅", version = 0)
                LOCAL_HTTP_SUBS_ID -> RawSubscription(id = id, name = "内存订阅", version = 0)
                else -> error("订阅文件不存在")
            }
        }
        val subscription = try {
            RawSubscription.parse(file.readText(), json5 = false)
        } catch (e: Exception) {
            throw Exception("订阅文件解析失败", e)
        }
        if (subscription.id != id) error("订阅文件id不一致")
        return subscription
    }

    fun readBytes(id: Long): ByteArray? = file(id).takeIf { it.exists() }?.readBytes()

    fun write(subscription: RawSubscription) {
        writeBytes(subscription.id, json.encodeToString(subscription).encodeToByteArray())
    }

    fun restore(id: Long, bytes: ByteArray?) {
        if (bytes == null) {
            delete(id)
        } else {
            writeBytes(id, bytes)
        }
    }

    fun delete(id: Long) {
        val file = file(id)
        AtomicFile(file).delete()
        if (file.exists()) error("无法删除 ${file.name}")
    }

    private fun file(id: Long): File = FolderUtils.subsFolder.resolve("$id.json")

    private fun writeBytes(id: Long, bytes: ByteArray) {
        val atomicFile = AtomicFile(file(id))
        var output: FileOutputStream? = null
        try {
            output = atomicFile.startWrite()
            output.write(bytes)
            atomicFile.finishWrite(output)
        } catch (e: Exception) {
            atomicFile.failWrite(output)
            throw e
        }
    }
}
