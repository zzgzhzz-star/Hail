package li.gkd.app.feature.snapshot

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import li.gkd.app.data.screenshotFile
import li.gkd.app.data.snapshot.SnapshotRepository
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.util.ImageUtils
import li.gkd.app.data.appinfo.AppInfoRepository
import li.gkd.db.Snapshot
import java.io.File

data class SnapshotUiState(
    val snapshots: List<Snapshot>,
    val appNames: Map<String, String>,
)

class SnapshotVm : BaseViewModel() {
    private val snapshotsFlow = SnapshotRepository.snapshots()

    val uiState = combine(
        snapshotsFlow,
        AppInfoRepository.appInfoMapFlow,
    ) { snapshots, appInfoMap ->
        SnapshotUiState(
            snapshots = snapshots,
            appNames = appInfoMap.mapValues { it.value.name },
        )
    }.stateLoadable()

    suspend fun deleteAllSnapshots() = SnapshotRepository.deleteAll()

    suspend fun deleteSnapshot(snapshot: Snapshot) = SnapshotRepository.delete(snapshot)

    suspend fun buildShareArchive(snapshot: Snapshot): File {
        return SnapshotRepository.createArchive(snapshot.id, snapshot.appId, snapshot.activityId)
    }

    suspend fun buildUploadArchive(snapshot: Snapshot): File =
        SnapshotRepository.createArchive(snapshot.id)

    suspend fun saveScreenshotToAlbum(snapshot: Snapshot) = withContext(Dispatchers.IO) {
        ImageUtils.save2Album(BitmapFactory.decodeFile(snapshot.screenshotFile.absolutePath))
    }

    suspend fun markUploaded(snapshot: Snapshot, githubAssetId: Int) =
        SnapshotRepository.markUploaded(snapshot, githubAssetId)

    suspend fun replaceScreenshot(snapshot: Snapshot, newBytes: ByteArray): Boolean {
        return SnapshotRepository.replaceScreenshot(snapshot, newBytes)
    }
}
