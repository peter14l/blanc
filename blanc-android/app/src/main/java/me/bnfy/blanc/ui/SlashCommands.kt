package me.bnfy.blanc.ui

data class SlashCommandItem(
    val command: String,
    val description: String,
    val example: String = command
)

object SlashCommands {
    val ALL: List<SlashCommandItem> = listOf(
        SlashCommandItem("/new", "Open a new tab"),
        SlashCommandItem("/private", "Open a private tab (incognito)"),
        SlashCommandItem("/close", "Close the active tab"),
        SlashCommandItem("/reopen", "Reopen the last closed tab"),
        SlashCommandItem("/favorites", "Open favorites & bookmarks"),
        SlashCommandItem("/bookmarks", "Open favorites & bookmarks"),
        SlashCommandItem("/history", "Open browsing history"),
        SlashCommandItem("/downloads", "Open downloads"),
        SlashCommandItem("/settings", "Open browser settings"),
        SlashCommandItem("/find", "Find text on current page"),
        SlashCommandItem("/pin", "Pin or unpin active tab"),
        SlashCommandItem("/mute", "Mute or unmute active tab"),
        SlashCommandItem("/duplicate", "Duplicate current tab"),
        SlashCommandItem("/desktop", "Toggle desktop / mobile site mode"),
        SlashCommandItem("/block-ads", "Enable ad blocking on this site"),
        SlashCommandItem("/allow-ads", "Allow ads on this site"),
        SlashCommandItem("/theme", "Cycle theme (system, light, dark, sunrise)"),
        SlashCommandItem("/clear", "Clear browsing history and cache"),
        SlashCommandItem("/help", "List all slash commands")
    )

    fun filter(query: String): List<SlashCommandItem> {
        val trimmed = query.trim().lowercase()
        if (trimmed == "/") return ALL
        return ALL.filter { it.command.lowercase().startsWith(trimmed) }
    }
}
