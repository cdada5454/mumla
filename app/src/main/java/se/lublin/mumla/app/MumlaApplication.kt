package se.lublin.mumla.app

import android.app.Application
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.setApplicationLocales
import androidx.core.os.LocaleListCompat.forLanguageTags
import androidx.core.os.LocaleListCompat.getEmptyLocaleList
import androidx.preference.PreferenceManager
import se.lublin.mumla.Settings

class MumlaApplication : Application(), SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onCreate() {
        super.onCreate()
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        applyTheme(preferences)
        preferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String?) {
        when (key) {
            Settings.PREF_LANGUAGE -> {
                val language = preferences.getString(Settings.PREF_LANGUAGE, "system")
                setApplicationLocales(
                    if (language == "system") getEmptyLocaleList() else forLanguageTags(language),
                )
            }
            Settings.PREF_THEME -> applyTheme(preferences)
        }
    }

    companion object {
        private fun applyTheme(preferences: SharedPreferences) {
            when (preferences.getString(Settings.PREF_THEME, "system")) {
                "forceLight" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                "forceDark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                else -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    preferences.edit().putString(Settings.PREF_THEME, "system").apply()
                }
            }
        }
    }
}
