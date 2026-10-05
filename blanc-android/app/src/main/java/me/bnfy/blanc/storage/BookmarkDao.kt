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
 * DAO for bookmarks/favorites.
 * Provides access to user bookmarks with folder support.
 */
@Dao
interface BookmarkDao {

    /** Inserts a bookmark. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: Bookmark): Long

    /** Inserts multiple bookmarks. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bookmarks: List<Bookmark>): List<Long>

    /** Updates a bookmark. */
    @Update
    suspend fun update(bookmark: Bookmark): Int

    /** Deletes a bookmark. */
    @Delete
    suspend fun delete(bookmark: Bookmark): Int

    /** Deletes a bookmark by ID. */
    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: String): Int

    /** Gets a bookmark by ID. */
    @Query("SELECT * FROM bookmarks WHERE id = :id")
    suspend fun getById(id: String): Bookmark?

    /** Gets all bookmarks for a profile. */
    @Query("SELECT * FROM bookmarks WHERE profileId = :profileId ORDER BY position ASC, createdAt ASC")
    suspend fun getAll(profileId: String): List<Bookmark>

    /** Gets all bookmarks as Flow for reactive UI. */
    @Query("SELECT * FROM bookmarks WHERE profileId = :profileId ORDER BY position ASC, createdAt ASC")
    fun getAllFlow(profileId: String): Flow<List<Bookmark>>

    /** Gets bookmarks in a folder. */
    @Query("SELECT * FROM bookmarks WHERE profileId = :profileId AND folderId = :folderId ORDER BY position ASC")
    suspend fun getByFolder(profileId: String, folderId: String): List<Bookmark>

    /** Gets bookmarks in a folder as Flow. */
    @Query("SELECT * FROM bookmarks WHERE profileId = :profileId AND folderId = :folderId ORDER BY position ASC")
    fun getByFolderFlow(profileId: String, folderId: String): Flow<List<Bookmark>>

    /** Gets root-level bookmarks (no folder). */
    @Query("SELECT * FROM bookmarks WHERE profileId = :profileId AND folderId IS NULL ORDER BY position ASC")
    suspend fun getRootBookmarks(profileId: String): List<Bookmark>

    /** Gets root-level bookmarks as Flow. */
    @Query("SELECT * FROM bookmarks WHERE profileId = :profileId AND folderId IS NULL ORDER BY position ASC")
    fun getRootBookmarksFlow(profileId: String): Flow<List<Bookmark>>

    /** Searches bookmarks by query. */
    @Query("SELECT * FROM bookmarks WHERE profileId = :profileId " +
           "AND (url LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%') " +
           "ORDER BY position ASC, createdAt ASC")
    suspend fun search(profileId: String, query: String): List<Bookmark>

    /** Gets bookmark by URL. */
    @Query("SELECT * FROM bookmarks WHERE url = :url AND profileId = :profileId LIMIT 1")
    suspend fun getByUrl(url: String, profileId: String): Bookmark?

    /** Checks if a URL is bookmarked. */
    @Query("SELECT COUNT(*) FROM bookmarks WHERE url = :url AND profileId = :profileId")
    suspend fun isBookmarked(url: String, profileId: String): Int

    /** Gets max position for a folder (for appending). */
    @Query("SELECT COALESCE(MAX(position), -1) FROM bookmarks WHERE profileId = :profileId AND folderId = :folderId")
    suspend fun getMaxPosition(profileId: String, folderId: String?): Int

    /** Updates bookmark positions (for reordering). */
    @Query("UPDATE bookmarks SET position = :position, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePosition(id: String, position: Int, updatedAt: Long): Int

    /** Updates multiple bookmark positions in a transaction. */
    @Query("UPDATE bookmarks SET position = CASE id " +
           "WHEN :id1 THEN :pos1 " +
           "WHEN :id2 THEN :pos2 " +
           "WHEN :id3 THEN :pos3 " +
           "WHEN :id4 THEN :pos4 " +
           "WHEN :id5 THEN :pos5 " +
           "END, updatedAt = :updatedAt WHERE id IN (:id1, :id2, :id3, :id4, :id5)")
    suspend fun updatePositions(
        id1: String, pos1: Int,
        id2: String, pos2: Int,
        id3: String, pos3: Int,
        id4: String, pos4: Int,
        id5: String, pos5: Int,
        updatedAt: Long
    ): Int

    /** Moves a bookmark to a different folder. */
    @Query("UPDATE bookmarks SET folderId = :folderId, position = :position, updatedAt = :updatedAt WHERE id = :id")
    suspend fun moveToFolder(id: String, folderId: String?, position: Int, updatedAt: Long): Int

    /** Gets count of bookmarks in a folder. */
    @Query("SELECT COUNT(*) FROM bookmarks WHERE profileId = :profileId AND folderId = :folderId")
    suspend fun getFolderCount(profileId: String, folderId: String): Int

    /** Gets all folders (bookmarks that act as folders). */
    @Query("SELECT DISTINCT folderId FROM bookmarks WHERE profileId = :profileId AND folderId IS NOT NULL")
    suspend fun getFolderIds(profileId: String): List<String>

    /** Deletes all bookmarks in a folder. */
    @Query("DELETE FROM bookmarks WHERE profileId = :profileId AND folderId = :folderId")
    suspend fun deleteFolder(profileId: String, folderId: String): Int

    /** Deletes all bookmarks for a profile. */
    @Query("DELETE FROM bookmarks WHERE profileId = :profileId")
    suspend fun clearAll(profileId: String): Int
}