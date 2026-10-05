package me.bnfy.blanc.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Blanc Room Database.
 * 
 * Entities:
 * - HistoryEntry: Browsing history
 * - Bookmark: Bookmarks (internal)
 * - Favorite: Favorites (user-facing bookmarks with UI state)
 * - TabEntity: Tab state for session restore
 * - WindowEntity: Window state
 * - TabGroupEntity: Tab groups
 * - ClosedTabEntity: Closed tabs for reopen feature
 * - DownloadEntity: Download metadata
 * - ProfileEntity: User profiles
 * - WorkspaceEntity: Named workspaces (Patron)
 * - PermissionDecisionEntity: Site permission decisions
 * - AdblockExceptionEntity: User adblock allowlist
 * - SettingEntity: Room-backed settings
 * - SearchEngineEntity: Search engines
 * 
 * Version 1: Initial schema
 */
@Database(
    entities = [
        HistoryEntry::class,
        Bookmark::class,
        Favorite::class,
        TabEntity::class,
        WindowEntity::class,
        TabGroupEntity::class,
        ClosedTabEntity::class,
        DownloadEntity::class,
        ProfileEntity::class,
        WorkspaceEntity::class,
        PermissionDecisionEntity::class,
        AdblockExceptionEntity::class,
        SettingEntity::class,
        SearchEngineEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun settingsDao(): SettingsDao
    abstract fun tabDao(): TabDao
    abstract fun closedTabDao(): ClosedTabDao
    abstract fun downloadDao(): DownloadDao
    abstract fun profileDao(): ProfileDao

    companion object {
        @Suppress("UNUSED_PARAMETER")
        @Volatile private var INSTANCE: AppDatabase? = null

        /**
         * Gets the singleton database instance.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "blanc_database"
                )
                    .addMigrations(MIGRATION_1_2) // Add future migrations here
                    .fallbackToDestructiveMigration() // For development; remove in production
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Creates an in-memory database for testing.
         */
        fun createInMemory(context: Context): AppDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AppDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }

        /**
         * Migration from version 1 to 2 (template for future use).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Example migration:
                // database.execSQL("ALTER TABLE history_entries ADD COLUMN new_column INTEGER DEFAULT 0")
            }
        }

        /**
         * Clears the singleton instance (for testing).
         */
        fun clearInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}