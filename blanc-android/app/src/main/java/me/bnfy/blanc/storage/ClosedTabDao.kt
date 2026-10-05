package me.bnfy.blanc.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for closed tabs (Reopen Closed Tab feature).
 * Manages closed tab entries with tiered recovery support.
 */
@Dao
interface ClosedTabDao {

    /** Inserts a closed tab entry. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ClosedTabEntity): Long

    /** Inserts multiple closed tab entries. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<ClosedTabEntity>): List<Long>

    /** Updates a closed tab entry. */
    @Update
    suspend fun update(entry: ClosedTabEntity): Int

    /** Deletes a closed tab entry. */
    @Delete
    suspend fun delete(entry: ClosedTabEntity): Int

    /** Deletes a closed tab entry by ID. */
    @Query("DELETE FROM closed_tabs WHERE id = :id")
    suspend fun deleteById(id: String): Int

    /** Gets a closed tab entry by ID. */
    @Query("SELECT * FROM closed_tabs WHERE id = :id")
    suspend fun getById(id: String): ClosedTabEntity?

    /** Gets a closed tab entry as Flow. */
    @Query("SELECT * FROM closed_tabs WHERE id = :id")
    fun getByIdFlow(id: String): Flow<ClosedTabEntity?>

    /** Gets all closed tabs for a window (most recent first). */
    @Query("SELECT * FROM closed_tabs WHERE windowId = :windowId ORDER BY closedAt DESC")
    suspend fun getByWindow(windowId: String): List<ClosedTabEntity>

    /** Gets all closed tabs for a window as Flow. */
    @Query("SELECT * FROM closed_tabs WHERE windowId = :windowId ORDER BY closedAt DESC")
    fun getByWindowFlow(windowId: String): Flow<List<ClosedTabEntity>>

    /** Gets all closed tabs for a profile. */
    @Query("SELECT * FROM closed_tabs WHERE profileId = :profileId ORDER BY closedAt DESC")
    suspend fun getByProfile(profileId: String): List<ClosedTabEntity>

    /** Gets all closed tabs for a profile as Flow. */
    @Query("SELECT * FROM closed_tabs WHERE profileId = :profileId ORDER BY closedAt DESC")
    fun getByProfileFlow(profileId: String): Flow<List<ClosedTabEntity>>

    /** Gets closed tabs limited by count (for UI list). */
    @Query("SELECT * FROM closed_tabs WHERE windowId = :windowId ORDER BY closedAt DESC LIMIT :limit")
    suspend fun getRecentByWindow(windowId: String, limit: Int): List<ClosedTabEntity>

    /** Gets closed tabs limited by count for a profile. */
    @Query("SELECT * FROM closed_tabs WHERE profileId = :profileId ORDER BY closedAt DESC LIMIT :limit")
    suspend fun getRecentByProfile(profileId: String, limit: Int): List<ClosedTabEntity>

    /** Gets the most recently closed tab for a window. */
    @Query("SELECT * FROM closed_tabs WHERE windowId = :windowId ORDER BY closedAt DESC LIMIT 1")
    suspend fun getMostRecent(windowId: String): ClosedTabEntity?

    /** Gets closed tabs by tier (for tier-aware recovery). */
    @Query("SELECT * FROM closed_tabs WHERE windowId = :windowId AND tier = :tier ORDER BY closedAt DESC")
    suspend fun getByTier(windowId: String, tier: Int): List<ClosedTabEntity>

    /** Gets held view entries (tier 1). */
    @Query("SELECT * FROM closed_tabs WHERE windowId = :windowId AND tier = 1 ORDER BY closedAt DESC")
    suspend fun getHeldViews(windowId: String): List<ClosedTabEntity>

    /** Gets snapshot entries (tier 2). */
    @Query("SELECT * FROM closed_tabs WHERE windowId = :windowId AND tier = 2 ORDER BY closedAt DESC")
    suspend fun getSnapshots(windowId: String): List<ClosedTabEntity>

    /** Gets URL-only entries (tier 3). */
    @Query("SELECT * FROM closed_tabs WHERE windowId = :windowId AND tier = 3 ORDER BY closedAt DESC")
    suspend fun getUrlOnly(windowId: String): List<ClosedTabEntity>

    /** Gets group entries. */
    @Query("SELECT * FROM closed_tabs WHERE windowId = :windowId AND isGroup = 1 ORDER BY closedAt DESC")
    suspend fun getGroups(windowId: String): List<ClosedTabEntity>

    /** Updates entry tier (for degradation). */
    @Query("UPDATE closed_tabs SET tier = :tier WHERE id = :id")
    suspend fun updateTier(id: String, tier: Int): Int

    /** Degrades held views to snapshots (called after ~30s). */
    @Query("UPDATE closed_tabs SET tier = 2 WHERE windowId = :windowId AND tier = 1")
    suspend fun degradeHeldViews(windowId: String): Int

    /** Deletes expired entries (older than 1 hour). */
    @Query("DELETE FROM closed_tabs WHERE windowId = :windowId AND closedAt < :expiryTime")
    suspend fun deleteExpired(windowId: String, expiryTime: Long): Int

    /** Deletes expired entries for all windows in a profile. */
    @Query("DELETE FROM closed_tabs WHERE profileId = :profileId AND closedAt < :expiryTime")
    suspend fun deleteExpiredByProfile(profileId: String, expiryTime: Long): Int

    /** Enforces max entries per window (25). Deletes oldest beyond limit. */
    @Query("DELETE FROM closed_tabs WHERE id IN (" +
           "SELECT id FROM closed_tabs WHERE windowId = :windowId " +
           "ORDER BY closedAt DESC LIMIT -1 OFFSET :maxEntries)")
    suspend fun enforceMaxEntries(windowId: String, maxEntries: Int): Int

    /** Deletes all closed tabs for a window. */
    @Query("DELETE FROM closed_tabs WHERE windowId = :windowId")
    suspend fun clearByWindow(windowId: String): Int

    /** Deletes all closed tabs for a profile. */
    @Query("DELETE FROM closed_tabs WHERE profileId = :profileId")
    suspend fun clearByProfile(profileId: String): Int

    /** Counts closed tabs for a window. */
    @Query("SELECT COUNT(*) FROM closed_tabs WHERE windowId = :windowId")
    suspend fun countByWindow(windowId: String): Int

    /** Counts closed tabs for a profile. */
    @Query("SELECT COUNT(*) FROM closed_tabs WHERE profileId = :profileId")
    suspend fun countByProfile(profileId: String): Int
}