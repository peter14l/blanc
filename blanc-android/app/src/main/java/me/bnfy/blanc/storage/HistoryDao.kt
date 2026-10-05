package me.bnfy.blanc.storage

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for history entries.
 * Provides access to browsing history with pagination and search.
 */
@Dao
interface HistoryDao {

    /** Inserts a history entry. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: HistoryEntry): Long

    /** Inserts multiple history entries. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<HistoryEntry>): List<Long>

    /** Updates a history entry. */
    @Update
    suspend fun update(entry: HistoryEntry): Int

    /** Deletes a history entry by ID. */
    @Delete
    suspend fun delete(entry: HistoryEntry): Int

    /** Deletes a history entry by ID. */
    @Query("DELETE FROM history_entries WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    /** Gets a history entry by ID. */
    @Query("SELECT * FROM history_entries WHERE id = :id")
    suspend fun getById(id: Long): HistoryEntry?

    /** Gets a history page with pagination and optional search query. */
    @Query("SELECT * FROM history_entries WHERE profileId = :profileId AND isPrivate = 0 " +
           "AND (:query IS NULL OR url LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%') " +
           "ORDER BY visitTime DESC LIMIT :limit OFFSET :offset")
    suspend fun getHistoryPage(
        profileId: String,
        limit: Int,
        offset: Int,
        query: String?
    ): List<HistoryEntry>

    /** Gets a history page as Flow for reactive UI. */
    @Query("SELECT * FROM history_entries WHERE profileId = :profileId AND isPrivate = 0 " +
           "AND (:query IS NULL OR url LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%') " +
           "ORDER BY visitTime DESC LIMIT :limit OFFSET :offset")
    fun getHistoryPageFlow(
        profileId: String,
        limit: Int,
        offset: Int,
        query: String?
    ): Flow<List<HistoryEntry>>

    /** Gets total count of history entries for a profile. */
    @Query("SELECT COUNT(*) FROM history_entries WHERE profileId = :profileId AND isPrivate = 0 " +
           "AND (:query IS NULL OR url LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%')")
    suspend fun getTotalCount(profileId: String, query: String?): Int

    /** Gets total count as Flow. */
    @Query("SELECT COUNT(*) FROM history_entries WHERE profileId = :profileId AND isPrivate = 0 " +
           "AND (:query IS NULL OR url LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%')")
    fun getTotalCountFlow(profileId: String, query: String?): Flow<Int>

    /** Records a visit (insert or update visit time). */
    @Query("INSERT OR REPLACE INTO history_entries (id, url, title, visitTime, favicon, isPrivate, profileId) " +
           "VALUES ((SELECT id FROM history_entries WHERE url = :url AND profileId = :profileId AND isPrivate = :isPrivate LIMIT 1), " +
           ":url, :title, :visitTime, :favicon, :isPrivate, :profileId)")
    suspend fun recordVisit(
        url: String,
        title: String?,
        visitTime: Long,
        favicon: String?,
        isPrivate: Boolean,
        profileId: String
    )

    /** Removes a specific history entry by URL. */
    @Query("DELETE FROM history_entries WHERE url = :url AND profileId = :profileId AND isPrivate = 0")
    suspend fun removeByUrl(url: String, profileId: String): Int

    /** Clears all history for a profile. */
    @Query("DELETE FROM history_entries WHERE profileId = :profileId AND isPrivate = 0")
    suspend fun clearHistory(profileId: String): Int

    /** Gets recent history entries (for new tab page). */
    @Query("SELECT * FROM history_entries WHERE profileId = :profileId AND isPrivate = 0 " +
           "ORDER BY visitTime DESC LIMIT :limit")
    suspend fun getRecentHistory(profileId: String, limit: Int): List<HistoryEntry>

    /** Gets most visited URLs (for new tab page). */
    @Query("SELECT url, title, favicon, COUNT(*) as visitCount, MAX(visitTime) as lastVisit " +
           "FROM history_entries WHERE profileId = :profileId AND isPrivate = 0 " +
           "GROUP BY url, title, favicon " +
           "ORDER BY visitCount DESC, lastVisit DESC LIMIT :limit")
    suspend fun getMostVisited(profileId: String, limit: Int): List<MostVisitedEntry>

    /** Gets history entries older than a given timestamp (for cleanup). */
    @Query("SELECT * FROM history_entries WHERE profileId = :profileId AND visitTime < :olderThan AND isPrivate = 0")
    suspend fun getOldEntries(profileId: String, olderThan: Long): List<HistoryEntry>

    /** Deletes history entries older than a given timestamp. */
    @Query("DELETE FROM history_entries WHERE profileId = :profileId AND visitTime < :olderThan AND isPrivate = 0")
    suspend fun deleteOldEntries(profileId: String, olderThan: Long): Int

    /** Gets history entries for a specific date range. */
    @Query("SELECT * FROM history_entries WHERE profileId = :profileId AND isPrivate = 0 " +
           "AND visitTime >= :startTime AND visitTime <= :endTime " +
           "ORDER BY visitTime DESC")
    suspend fun getHistoryInRange(
        profileId: String,
        startTime: Long,
        endTime: Long
    ): List<HistoryEntry>

    /** Gets distinct dates that have history entries. */
    @Query("SELECT DISTINCT date(visitTime/1000, 'unixepoch') as visitDate " +
           "FROM history_entries WHERE profileId = :profileId AND isPrivate = 0 " +
           "ORDER BY visitDate DESC")
    suspend fun getHistoryDates(profileId: String): List<String>

    /** Gets history grouped by date. */
    @Query("SELECT * FROM history_entries WHERE profileId = :profileId AND isPrivate = 0 " +
           "AND date(visitTime/1000, 'unixepoch') = :date " +
           "ORDER BY visitTime DESC")
    suspend fun getHistoryForDate(profileId: String, date: String): List<HistoryEntry>
}

/**
 * Data class for most visited entries.
 */
data class MostVisitedEntry(
    val url: String,
    val title: String?,
    val favicon: String?,
    val visitCount: Int,
    val lastVisit: Long
)