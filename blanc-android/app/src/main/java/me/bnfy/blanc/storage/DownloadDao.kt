package me.bnfy.blanc.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for downloads.
 * Manages download metadata and state.
 */
@Dao
interface DownloadDao {

    /** Inserts a download. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity): Long

    /** Inserts multiple downloads. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(downloads: List<DownloadEntity>): List<Long>

    /** Updates a download. */
    @Update
    suspend fun update(download: DownloadEntity): Int

    /** Deletes a download. */
    @Delete
    suspend fun delete(download: DownloadEntity): Int

    /** Deletes a download by ID. */
    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String): Int

    /** Gets a download by ID. */
    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: String): DownloadEntity?

    /** Gets a download by ID as Flow. */
    @Query("SELECT * FROM downloads WHERE id = :id")
    fun getByIdFlow(id: String): Flow<DownloadEntity?>

    /** Gets all downloads for a window (most recent first). */
    @Query("SELECT * FROM downloads WHERE windowId = :windowId ORDER BY startedAt DESC")
    suspend fun getByWindow(windowId: String): List<DownloadEntity>

    /** Gets all downloads for a window as Flow. */
    @Query("SELECT * FROM downloads WHERE windowId = :windowId ORDER BY startedAt DESC")
    fun getByWindowFlow(windowId: String): Flow<List<DownloadEntity>>

    /** Gets all downloads for a profile. */
    @Query("SELECT * FROM downloads WHERE profileId = :profileId ORDER BY startedAt DESC")
    suspend fun getByProfile(profileId: String): List<DownloadEntity>

    /** Gets all downloads for a profile as Flow. */
    @Query("SELECT * FROM downloads WHERE profileId = :profileId ORDER BY startedAt DESC")
    fun getByProfileFlow(profileId: String): Flow<List<DownloadEntity>>

    /** Gets all downloads for a tab. */
    @Query("SELECT * FROM downloads WHERE tabId = :tabId ORDER BY startedAt DESC")
    suspend fun getByTab(tabId: String): List<DownloadEntity>

    /** Gets active downloads (in progress). */
    @Query("SELECT * FROM downloads WHERE windowId = :windowId AND state = 1 ORDER BY startedAt DESC")
    suspend fun getActiveDownloads(windowId: String): List<DownloadEntity>

    /** Gets active downloads as Flow. */
    @Query("SELECT * FROM downloads WHERE windowId = :windowId AND state = 1 ORDER BY startedAt DESC")
    fun getActiveDownloadsFlow(windowId: String): Flow<List<DownloadEntity>>

    /** Gets completed downloads. */
    @Query("SELECT * FROM downloads WHERE windowId = :windowId AND state = 2 ORDER BY completedAt DESC")
    suspend fun getCompletedDownloads(windowId: String): List<DownloadEntity>

    /** Gets completed downloads as Flow. */
    @Query("SELECT * FROM downloads WHERE windowId = :windowId AND state = 2 ORDER BY completedAt DESC")
    fun getCompletedDownloadsFlow(windowId: String): Flow<List<DownloadEntity>>

    /** Gets failed downloads. */
    @Query("SELECT * FROM downloads WHERE windowId = :windowId AND state = 4 ORDER BY startedAt DESC")
    suspend fun getFailedDownloads(windowId: String): List<DownloadEntity>

    /** Gets downloads by state. */
    @Query("SELECT * FROM downloads WHERE windowId = :windowId AND state = :state ORDER BY startedAt DESC")
    suspend fun getByState(windowId: String, state: Int): List<DownloadEntity>

    /** Updates download progress. */
    @Query("UPDATE downloads SET receivedBytes = :receivedBytes, totalBytes = :totalBytes, state = 1 WHERE id = :id")
    suspend fun updateProgress(id: String, receivedBytes: Long, totalBytes: Long): Int

    /** Updates download state. */
    @Query("UPDATE downloads SET state = :state, error = :error, completedAt = :completedAt WHERE id = :id")
    suspend fun updateState(id: String, state: Int, error: String?, completedAt: Long): Int

    /** Updates download target path. */
    @Query("UPDATE downloads SET targetPath = :targetPath WHERE id = :id")
    suspend fun updateTargetPath(id: String, targetPath: String): Int

    /** Updates download file name. */
    @Query("UPDATE downloads SET fileName = :fileName WHERE id = :id")
    suspend fun updateFileName(id: String, fileName: String): Int

    /** Cancels a download (sets state to cancelled). */
    @Query("UPDATE downloads SET state = 3, completedAt = :completedAt WHERE id = :id")
    suspend fun cancel(id: String, completedAt: Long): Int

    /** Deletes completed downloads for a window. */
    @Query("DELETE FROM downloads WHERE windowId = :windowId AND state = 2")
    suspend fun clearCompleted(windowId: String): Int

    /** Deletes completed downloads for a profile. */
    @Query("DELETE FROM downloads WHERE profileId = :profileId AND state = 2")
    suspend fun clearCompletedByProfile(profileId: String): Int

    /** Deletes all downloads for a window. */
    @Query("DELETE FROM downloads WHERE windowId = :windowId")
    suspend fun clearByWindow(windowId: String): Int

    /** Deletes all downloads for a profile. */
    @Query("DELETE FROM downloads WHERE profileId = :profileId")
    suspend fun clearByProfile(profileId: String): Int

    /** Gets total download count for a window. */
    @Query("SELECT COUNT(*) FROM downloads WHERE windowId = :windowId")
    suspend fun countByWindow(windowId: String): Int

    /** Gets completed download count for a window. */
    @Query("SELECT COUNT(*) FROM downloads WHERE windowId = :windowId AND state = 2")
    suspend fun countCompletedByWindow(windowId: String): Int

    /** Gets active download count for a window. */
    @Query("SELECT COUNT(*) FROM downloads WHERE windowId = :windowId AND state = 1")
    suspend fun countActiveByWindow(windowId: String): Int
}