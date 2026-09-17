package li.gkd.app.data.backup

import android.net.Uri
import li.gkd.app.app
import li.gkd.app.util.ZipUtils
import java.io.File
import java.io.IOException

object BackupArchiveReader {
    fun extract(uri: Uri, archiveFile: File, destination: File) {
        copyArchive(uri, archiveFile)
        ZipUtils.unzipFile(archiveFile, destination)
    }

    private fun copyArchive(uri: Uri, archiveFile: File) {
        val input = app.contentResolver.openInputStream(uri)
            ?: throw IOException("无法读取备份文件")
        input.use {
            archiveFile.outputStream().use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var copiedBytes = 0L
                while (true) {
                    val size = input.read(buffer)
                    if (size < 0) break
                    copiedBytes += size
                    if (copiedBytes > MAX_ARCHIVE_BYTES) {
                        throw IOException("备份压缩包超过大小限制")
                    }
                    output.write(buffer, 0, size)
                }
            }
        }
    }

    private const val BUFFER_SIZE = 8_192
    private const val MAX_ARCHIVE_BYTES = 64L * 1024 * 1024
}
