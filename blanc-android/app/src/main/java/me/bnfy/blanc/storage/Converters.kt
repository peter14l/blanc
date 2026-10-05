package me.bnfy.blanc.storage

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.util.Date

/**
 * Type converters for Room database.
 * Handles serialization of complex types (Lists, Dates, Enums) to/from database columns.
 */
class Converters {

    private val gson = Gson()

    // ===== List<String> <-> String (JSON) =====

    @TypeConverter
    fun fromStringList(value: String?): List<String> {
        if (value == null || value.isEmpty()) return emptyList()
        val type: Type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun toStringList(list: List<String>?): String {
        if (list == null || list.isEmpty()) return "[]"
        return gson.toJson(list)
    }

    // ===== List<Long> <-> String (JSON) =====

    @TypeConverter
    fun fromLongList(value: String?): List<Long> {
        if (value == null || value.isEmpty()) return emptyList()
        val type: Type = object : TypeToken<List<Long>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun toLongList(list: List<Long>?): String {
        if (list == null || list.isEmpty()) return "[]"
        return gson.toJson(list)
    }

    // ===== Date <-> Long (timestamp) =====

    @TypeConverter
    fun fromDate(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun toDate(date: Date?): Long? {
        return date?.time
    }

    // ===== Boolean <-> Int =====

    @TypeConverter
    fun fromBoolean(value: Int?): Boolean {
        return value == 1
    }

    @TypeConverter
    fun toBoolean(value: Boolean): Int {
        return if (value) 1 else 0
    }

    // ===== DownloadState Enum <-> Int =====

    enum class DownloadState(val value: Int) {
        PENDING(0),
        IN_PROGRESS(1),
        COMPLETED(2),
        CANCELLED(3),
        FAILED(4)
    }

    @TypeConverter
    fun fromDownloadState(value: Int): DownloadState {
        return DownloadState.values().firstOrNull { it.value == value } ?: DownloadState.PENDING
    }

    @TypeConverter
    fun toDownloadState(state: DownloadState): Int {
        return state.value
    }

    // ===== PermissionDecision Enum <-> Int =====

    enum class PermissionDecision(val value: Int) {
        DEFAULT(0),
        ALLOW(1),
        DENY(2),
        ASK(3)
    }

    @TypeConverter
    fun fromPermissionDecision(value: Int): PermissionDecision {
        return PermissionDecision.values().firstOrNull { it.value == value } ?: PermissionDecision.DEFAULT
    }

    @TypeConverter
    fun toPermissionDecision(decision: PermissionDecision): Int {
        return decision.value
    }

    // ===== PermissionResource Enum <-> String =====

    enum class PermissionResource(val value: String) {
        GEOLOCATION("geolocation"),
        CAMERA("camera"),
        MICROPHONE("microphone"),
        NOTIFICATIONS("notifications"),
        MIDI("midi"),
        CAMERA_MIC("camera+mic"),
        CLIPBOARD_READ("clipboard-read"),
        CLIPBOARD_WRITE("clipboard-write"),
        PAYMENT_HANDLER("payment-handler"),
        BACKGROUND_FETCH("background-fetch"),
        BACKGROUND_SYNC("background-sync"),
        BLUETOOTH("bluetooth"),
        BLUETOOTH_SCAN("bluetooth-scan"),
        BLUETOOTH_CONNECT("bluetooth-connect"),
        USB("usb"),
        HID("hid"),
        SERIAL("serial"),
        NFC("nfc"),
        IDLE_DETECTION("idle-detection"),
        WINDOW_MANAGEMENT("window-management"),
        LOCAL_FONTS("local-fonts"),
        DISPLAY_CAPTURE("display-capture")
    }

    @TypeConverter
    fun fromPermissionResource(value: String): PermissionResource {
        return PermissionResource.values().firstOrNull { it.value == value } ?: PermissionResource.GEOLOCATION
    }

    @TypeConverter
    fun toPermissionResource(resource: PermissionResource): String {
        return resource.value
    }

    // ===== TabTier Enum <-> Int (for closed tabs) =====

    enum class TabTier(val value: Int) {
        HELD_VIEW(1),      // Live WebContentsView parked
        SNAPSHOT(2),       // Serialized navigation state
        URL_ONLY(3)        // Only URL preserved
    }

    @TypeConverter
    fun fromTabTier(value: Int): TabTier {
        return TabTier.values().firstOrNull { it.value == value } ?: TabTier.SNAPSHOT
    }

    @TypeConverter
    fun toTabTier(tier: TabTier): Int {
        return tier.value
    }

    // ===== Map<String, String> <-> String (JSON) =====

    @TypeConverter
    fun fromStringMap(value: String?): Map<String, String> {
        if (value == null || value.isEmpty()) return emptyMap()
        val type: Type = object : TypeToken<Map<String, String>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun toStringMap(map: Map<String, String>?): String {
        if (map == null || map.isEmpty()) return "{}"
        return gson.toJson(map)
    }

    // ===== Map<String, Any> <-> String (JSON) =====

    @TypeConverter
    fun fromAnyMap(value: String?): Map<String, Any> {
        if (value == null || value.isEmpty()) return emptyMap()
        val type: Type = object : TypeToken<Map<String, Any>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun toAnyMap(map: Map<String, Any>?): String {
        if (map == null || map.isEmpty()) return "{}"
        return gson.toJson(map)
    }
}