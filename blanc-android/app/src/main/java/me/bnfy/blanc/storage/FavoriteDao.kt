package me.bnfy.blanc.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for favorites (user-facing bookmarks with additional UI state).
 * Favorites are essentially bookmarks with pinned state and UI-specific ordering.
 */
@Dao
interface FavoriteDao {

    /** Inserts a favorite. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: Favorite): Long

    /** Inserts multiple favorites. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(favorites: List<Favorite>): List<Long>

    /** Updates a favorite. */
    @Update
    suspend fun update(favorite: Favorite): Int

    /** Deletes a favorite. */
    @Delete
    suspend fun delete(favorite: Favorite): Int

    /** Deletes a favorite by ID. */
    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun deleteById(id: String): Int

    /** Gets a favorite by ID. */
    @Query("SELECT * FROM favorites WHERE id = :id")
    suspend fun getById(id: String): Favorite?

    /** Gets all favorites for a profile (pinned first, then by position). */
    @Query("SELECT * FROM favorites WHERE profileId = :profileId ORDER BY isPinned DESC, position ASC, createdAt ASC")
    suspend fun getAll(profileId: String): List<Favorite>

    /** Gets all favorites as Flow for reactive UI. */
    @Query("SELECT * FROM favorites WHERE profileId = :profileId ORDER BY isPinned DESC, position ASC, createdAt ASC")
    fun getAllFlow(profileId: String): Flow<List<Favorite>>

    /** Gets pinned favorites. */
    @Query("SELECT * FROM favorites WHERE profileId = :profileId AND isPinned = 1 ORDER BY position ASC")
    suspend fun getPinned(profileId: String): List<Favorite>

    /** Gets pinned favorites as Flow. */
    @Query("SELECT * FROM favorites WHERE profileId = :profileId AND isPinned = 1 ORDER BY position ASC")
    fun getPinnedFlow(profileId: String): Flow<List<Favorite>>

    /** Gets unpinned favorites. */
    @Query("SELECT * FROM favorites WHERE profileId = :profileId AND isPinned = 0 ORDER BY position ASC")
    suspend fun getUnpinned(profileId: String): List<Favorite>

    /** Gets unpinned favorites as Flow. */
    @Query("SELECT * FROM favorites WHERE profileId = :profileId AND isPinned = 0 ORDER BY position ASC")
    fun getUnpinnedFlow(profileId: String): Flow<List<Favorite>>

    /** Searches favorites by query. */
    @Query("SELECT * FROM favorites WHERE profileId = :profileId " +
           "AND (url LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%') " +
           "ORDER BY isPinned DESC, position ASC")
    suspend fun search(profileId: String, query: String): List<Favorite>

    /** Gets favorite by URL. */
    @Query("SELECT * FROM favorites WHERE url = :url AND profileId = :profileId LIMIT 1")
    suspend fun getByUrl(url: String, profileId: String): Favorite?

    /** Checks if a URL is favorited. */
    @Query("SELECT COUNT(*) FROM favorites WHERE url = :url AND profileId = :profileId")
    suspend fun isFavorited(url: String, profileId: String): Int

    /** Toggles pinned state. */
    @Query("UPDATE favorites SET isPinned = :pinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, updatedAt: Long): Int

    /** Updates favorite position. */
    @Query("UPDATE favorites SET position = :position, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePosition(id: String, position: Int, updatedAt: Long): Int

    /** Gets max position for appending. */
    @Query("SELECT COALESCE(MAX(position), -1) FROM favorites WHERE profileId = :profileId AND isPinned = :isPinned")
    suspend fun getMaxPosition(profileId: String, isPinned: Boolean): Int

    /** Updates title and/or favicon. */
    @Query("UPDATE favorites SET title = :title, favicon = :favicon, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateMetadata(id: String, title: String, favicon: String?, updatedAt: Long): Int

    /** Deletes all favorites for a profile. */
    @Query("DELETE FROM favorites WHERE profileId = :profileId")
    suspend fun clearAll(profileId: String): Int
}