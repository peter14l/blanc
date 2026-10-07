package me.bnfy.blanc.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.*

/**
 * DAO for profiles and workspaces.
 * Manages user profiles, named profiles, and workspaces (Patron feature).
 */
@Dao
interface ProfileDao {

    // ===== Profile Operations =====

    /** Inserts a profile. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ProfileEntity): Long

    /** Inserts multiple profiles. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<ProfileEntity>): List<Long>

    /** Updates a profile. */
    @Update
    suspend fun update(profile: ProfileEntity): Int

    /** Deletes a profile. */
    @Delete
    suspend fun delete(profile: ProfileEntity): Int

    /** Deletes a profile by ID. */
    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteById(id: String): Int

    /** Gets a profile by ID. */
    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: String): ProfileEntity?

    /** Gets a profile by ID as Flow. */
    @Query("SELECT * FROM profiles WHERE id = :id")
    fun getByIdFlow(id: String): Flow<ProfileEntity?>

    /** Gets a profile by name. */
    @Query("SELECT * FROM profiles WHERE name = :name")
    suspend fun getByName(name: String): ProfileEntity?

    /** Gets the personal profile (isPersonal = 1). */
    @Query("SELECT * FROM profiles WHERE isPersonal = 1 LIMIT 1")
    suspend fun getPersonalProfile(): ProfileEntity?

    /** Gets the personal profile as Flow. */
    @Query("SELECT * FROM profiles WHERE isPersonal = 1 LIMIT 1")
    fun getPersonalProfileFlow(): Flow<ProfileEntity?>

    /** Gets the default profile. */
    @Query("SELECT * FROM profiles WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultProfile(): ProfileEntity?

    /** Gets the default profile as Flow. */
    @Query("SELECT * FROM profiles WHERE isDefault = 1 LIMIT 1")
    fun getDefaultProfileFlow(): Flow<ProfileEntity?>

    /** Gets all profiles. */
    @Query("SELECT * FROM profiles ORDER BY isPersonal DESC, isDefault DESC, name ASC")
    suspend fun getAll(): List<ProfileEntity>

    /** Gets all profiles as Flow. */
    @Query("SELECT * FROM profiles ORDER BY isPersonal DESC, isDefault DESC, name ASC")
    fun getAllFlow(): Flow<List<ProfileEntity>>

    /** Gets named profiles (non-personal). */
    @Query("SELECT * FROM profiles WHERE isPersonal = 0 ORDER BY name ASC")
    suspend fun getNamedProfiles(): List<ProfileEntity>

    /** Gets named profiles as Flow. */
    @Query("SELECT * FROM profiles WHERE isPersonal = 0 ORDER BY name ASC")
    fun getNamedProfilesFlow(): Flow<List<ProfileEntity>>

    /** Updates profile name. */
    @Query("UPDATE profiles SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateName(id: String, name: String, updatedAt: Long): Int

    /** Updates profile avatar. */
    @Query("UPDATE profiles SET avatar = :avatar, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateAvatar(id: String, avatar: String?, updatedAt: Long): Int

    /** Sets a profile as default. */
    @Query("UPDATE profiles SET isDefault = 0")
    suspend fun clearDefault(): Int

    @Query("UPDATE profiles SET isDefault = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setDefault(id: String, updatedAt: Long): Int

    // ===== Workspace Operations =====

    /** Inserts a workspace. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkspace(workspace: WorkspaceEntity): Long

    /** Updates a workspace. */
    @Update
    suspend fun updateWorkspace(workspace: WorkspaceEntity): Int

    /** Deletes a workspace. */
    @Delete
    suspend fun deleteWorkspace(workspace: WorkspaceEntity): Int

    /** Deletes a workspace by ID. */
    @Query("DELETE FROM workspaces WHERE id = :id")
    suspend fun deleteWorkspaceById(id: String): Int

    /** Gets a workspace by ID. */
    @Query("SELECT * FROM workspaces WHERE id = :id")
    suspend fun getWorkspaceById(id: String): WorkspaceEntity?

    /** Gets a workspace by ID as Flow. */
    @Query("SELECT * FROM workspaces WHERE id = :id")
    fun getWorkspaceByIdFlow(id: String): Flow<WorkspaceEntity?>

    /** Gets all workspaces for a profile. */
    @Query("SELECT * FROM workspaces WHERE profileId = :profileId ORDER BY createdAt ASC")
    suspend fun getWorkspacesByProfile(profileId: String): List<WorkspaceEntity>

    /** Gets all workspaces for a profile as Flow. */
    @Query("SELECT * FROM workspaces WHERE profileId = :profileId ORDER BY createdAt ASC")
    fun getWorkspacesByProfileFlow(profileId: String): Flow<List<WorkspaceEntity>>

    /** Updates workspace name. */
    @Query("UPDATE workspaces SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateWorkspaceName(id: String, name: String, updatedAt: Long): Int

    /** Updates workspace icon. */
    @Query("UPDATE workspaces SET icon = :icon, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateWorkspaceIcon(id: String, icon: String?, updatedAt: Long): Int

    /** Updates workspace window IDs. */
    @Query("UPDATE workspaces SET windowIds = :windowIds, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateWorkspaceWindows(id: String, windowIds: String, updatedAt: Long): Int

    /** Deletes all workspaces for a profile. */
    @Query("DELETE FROM workspaces WHERE profileId = :profileId")
    suspend fun deleteWorkspacesByProfile(profileId: String): Int

    // ===== Permission Decisions =====

    /** Inserts a permission decision. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPermission(decision: PermissionDecisionEntity): Long

    /** Gets permission decision for origin and resource. */
    @Query("SELECT * FROM permission_decisions WHERE origin = :origin AND resource = :resource AND profileId = :profileId LIMIT 1")
    suspend fun getPermission(origin: String, resource: String, profileId: String): PermissionDecisionEntity?

    /** Gets permission decision as Flow. */
    @Query("SELECT * FROM permission_decisions WHERE origin = :origin AND resource = :resource AND profileId = :profileId LIMIT 1")
    fun getPermissionFlow(origin: String, resource: String, profileId: String): Flow<PermissionDecisionEntity?>

    /** Gets all permission decisions for a profile. */
    @Query("SELECT * FROM permission_decisions WHERE profileId = :profileId ORDER BY origin ASC, resource ASC")
    suspend fun getPermissionsByProfile(profileId: String): List<PermissionDecisionEntity>

    /** Gets all permission decisions as Flow. */
    @Query("SELECT * FROM permission_decisions WHERE profileId = :profileId ORDER BY origin ASC, resource ASC")
    fun getPermissionsByProfileFlow(profileId: String): Flow<List<PermissionDecisionEntity>>

    /** Gets permission decisions for an origin. */
    @Query("SELECT * FROM permission_decisions WHERE origin = :origin AND profileId = :profileId")
    suspend fun getPermissionsForOrigin(origin: String, profileId: String): List<PermissionDecisionEntity>

    /** Deletes a permission decision. */
    @Query("DELETE FROM permission_decisions WHERE origin = :origin AND resource = :resource AND profileId = :profileId")
    suspend fun deletePermission(origin: String, resource: String, profileId: String): Int

    /** Deletes all permissions for an origin. */
    @Query("DELETE FROM permission_decisions WHERE origin = :origin AND profileId = :profileId")
    suspend fun deletePermissionsForOrigin(origin: String, profileId: String): Int

    /** Deletes all permissions for a profile. */
    @Query("DELETE FROM permission_decisions WHERE profileId = :profileId")
    suspend fun clearPermissions(profileId: String): Int

    // ===== Adblock Exceptions =====

    /** Inserts an adblock exception. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAdblockException(exception: AdblockExceptionEntity): Long

    /** Gets all adblock exceptions for a profile. */
    @Query("SELECT * FROM adblock_exceptions WHERE profileId = :profileId ORDER BY hostname ASC")
    suspend fun getAdblockExceptions(profileId: String): List<AdblockExceptionEntity>

    /** Gets all adblock exceptions as Flow. */
    @Query("SELECT * FROM adblock_exceptions WHERE profileId = :profileId ORDER BY hostname ASC")
    fun getAdblockExceptionsFlow(profileId: String): Flow<List<AdblockExceptionEntity>>

    /** Checks if a hostname is excepted. */
    @Query("SELECT COUNT(*) FROM adblock_exceptions WHERE hostname = :hostname AND profileId = :profileId")
    suspend fun isException(hostname: String, profileId: String): Int

    /** Deletes an adblock exception. */
    @Query("DELETE FROM adblock_exceptions WHERE hostname = :hostname AND profileId = :profileId")
    suspend fun deleteAdblockException(hostname: String, profileId: String): Int

    /** Deletes all adblock exceptions for a profile. */
    @Query("DELETE FROM adblock_exceptions WHERE profileId = :profileId")
    suspend fun clearAdblockExceptions(profileId: String): Int

    // ===== Combined Operations =====

    /** Gets full profile data with workspaces. */
    @Transaction
    suspend fun getProfileWithWorkspaces(profileId: String): ProfileWithWorkspaces? {
        val profile = getById(profileId)
        if (profile == null) return null
        val workspaces = getWorkspacesByProfile(profileId)
        return ProfileWithWorkspaces(profile, workspaces)
    }

    /** Gets full profile data with workspaces as Flow. */
    fun getProfileWithWorkspacesFlow(profileId: String): Flow<ProfileWithWorkspaces?> {
        return getByIdFlow(profileId).flatMapLatest { profile ->
            if (profile == null) {
                flowOf(null)
            } else {
                getWorkspacesByProfileFlow(profileId).map { ws -> ProfileWithWorkspaces(profile, ws) }
            }
        }.distinctUntilChanged()
    }
}

/**
 * Profile with its workspaces.
 */
data class ProfileWithWorkspaces(
    val profile: ProfileEntity,
    val workspaces: List<WorkspaceEntity>
)