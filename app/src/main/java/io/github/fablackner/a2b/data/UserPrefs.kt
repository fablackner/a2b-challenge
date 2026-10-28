package io.github.fablackner.a2b.data

import android.content.Context
import androidx.preference.PreferenceManager

object UserPrefs {

    const val KEY_NICKNAME = "nickname"
    const val KEY_UPDATE_TAG = "updateTagQ"

    fun nickname(context: Context): String =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getString(KEY_NICKNAME, null)?.takeIf { it.isNotBlank() } ?: "anonymous"

    fun writeScoreToTag(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(KEY_UPDATE_TAG, true)
}
