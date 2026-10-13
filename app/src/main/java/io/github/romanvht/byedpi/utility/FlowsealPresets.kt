package io.github.romanvht.byedpi.utility

import android.content.Context
import androidx.core.content.edit
import io.github.romanvht.byedpi.data.Command

/**
 * Built-in presets adapted from Flowseal/zapret-discord-youtube (winws args)
 * to the ciadpi (ByeDPI) command line. Seeded as named, pinned commands so
 * they show up in the strategy picker on the main screen.
 */
object FlowsealPresets {
    private const val PREFS_KEY = "flowseal_presets_seeded"
    private const val ASSET_NAME = "flowseal_presets.list"
    private const val ASSET_VERSION = 5

    fun seed(context: Context) {
        val preferences = context.getPreferences()
        if (preferences.getInt(PREFS_KEY, 0) >= ASSET_VERSION) return

        val presets = loadPresets(context)

        if (presets.isNotEmpty()) {
            val historyUtils = HistoryUtils(context)
            val history = historyUtils.getHistory().toMutableList()
            val existing = history.map { it.text }.toHashSet()

            val fresh = presets
                .filter { (_, args) -> args.isNotBlank() && !existing.contains(args) }
                .map { (name, args) -> Command(text = args, pinned = true, name = name) }

            if (fresh.isNotEmpty()) {
                history.addAll(0, fresh)
                historyUtils.saveHistory(history)
            }
        }

        preferences.edit { putInt(PREFS_KEY, ASSET_VERSION) }
    }

    private fun loadPresets(context: Context): List<Pair<String, String>> {
        return try {
            context.assets.open(ASSET_NAME).bufferedReader().useLines { lines ->
                lines.map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .mapNotNull { line ->
                        val separator = line.indexOf('|')
                        if (separator <= 0) null
                        else line.take(separator).trim() to line.substring(separator + 1).trim()
                    }
                    .toList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}