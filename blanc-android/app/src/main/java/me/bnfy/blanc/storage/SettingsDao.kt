package me.bnfy.blanc.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for settings stored in Room.
 * Used for settings that benefit from relational queries or complex values.
 * Most simple settings should use DataStore instead.
 */
@Dao
interface SettingsDao {

    /** Inserts or updates a setting. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(setting: SettingEntity): Long

    /** Updates a setting. */
    @Update
    suspend fun update(setting: SettingEntity): Int

    /** Deletes a setting. */
    @Delete
    suspend fun delete(setting: SettingEntity): Int

    /** Deletes a setting by key. */
    @Query("DELETE FROM settings WHERE key = :key AND profileId = :profileId")
    suspend fun deleteByKey(key: String, profileId: String): Int

    /** Gets a setting by key. */
    @Query("SELECT * FROM settings WHERE key = :key AND profileId = :profileId")
    suspend fun getByKey(key: String, profileId: String): SettingEntity?

    /** Gets a setting by key as Flow. */
    @Query("SELECT * FROM settings WHERE key = :key AND profileId = :profileId")
    fun getByKeyFlow(key: String, profileId: String): Flow<SettingEntity?>

    /** Gets all settings for a profile. */
    @Query("SELECT * FROM settings WHERE profileId = :profileId")
    suspend fun getAll(profileId: String): List<SettingEntity>

    /** Gets all settings as Flow. */
    @Query("SELECT * FROM settings WHERE profileId = :profileId")
    fun getAllFlow(profileId: String): Flow<List<SettingEntity>>

    /** Gets settings with keys matching a prefix. */
    @Query("SELECT * FROM settings WHERE profileId = :profileId AND key LIKE :prefix || '%'")
    suspend fun getByPrefix(profileId: String, prefix: String): List<SettingEntity>

    /** Bulk inserts/updates settings. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(settings: List<SettingEntity>): List<Long>

    /** Deletes all settings for a profile. */
    @Query("DELETE FROM settings WHERE profileId = :profileId")
    suspend fun clearAll(profileId: String): Int

    // ===== Search Engines =====

    /** Inserts a search engine. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchEngine(engine: SearchEngineEntity): Long

    /** Gets all search engines for a profile. */
    @Query("SELECT * FROM search_engines WHERE profileId = :profileId ORDER BY isDefault DESC, name ASC")
    suspend fun getSearchEngines(profileId: String): List<SearchEngineEntity>

    /** Gets all search engines as Flow. */
    @Query("SELECT * FROM search_engines WHERE profileId = :profileId ORDER BY isDefault DESC, name ASC")
    fun getSearchEnginesFlow(profileId: String): Flow<List<SearchEngineEntity>>

    /** Gets the default search engine. */
    @Query("SELECT * FROM search_engines WHERE profileId = :profileId AND isDefault = 1 LIMIT 1")
    suspend fun getDefaultSearchEngine(profileId: String): SearchEngineEntity?

    /** Gets the default search engine as Flow. */
    @Query("SELECT * FROM search_engines WHERE profileId = :profileId AND isDefault = 1 LIMIT 1")
    fun getDefaultSearchEngineFlow(profileId: String): Flow<SearchEngineEntity?>

    /** Sets a search engine as default. */
    @Query("UPDATE search_engines SET isDefault = 0 WHERE profileId = :profileId")
    suspend fun clearDefaultSearchEngine(profileId: String): Int

    @Query("UPDATE search_engines SET isDefault = 1 WHERE id = :id AND profileId = :profileId")
    suspend fun setDefaultSearchEngine(id: String, profileId: String): Int

    /** Deletes a search engine. */
    @Query("DELETE FROM search_engines WHERE id = :id AND profileId = :profileId")
    suspend fun deleteSearchEngine(id: String, profileId: String): Int

    /** Gets a search engine by ID. */
    @Query("SELECT * FROM search_engines WHERE id = :id AND profileId = :profileId")
    suspend fun getSearchEngineById(id: String, profileId: String): SearchEngineEntity?
}