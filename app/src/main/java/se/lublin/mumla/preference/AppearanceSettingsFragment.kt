package se.lublin.mumla.preference

import android.os.Bundle
import androidx.preference.ListPreference
import java.util.Locale
import se.lublin.mumla.R
import se.lublin.mumla.Settings

class AppearanceSettingsFragment : MumlaPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_appearance, rootKey)

        val languagePreference: ListPreference? = findPreference(Settings.PREF_LANGUAGE)
        if (languagePreference != null) {
            val codes = resources.getStringArray(R.array.languageValues)
            val listNames = arrayOfNulls<String>(codes.size + 1)
            val listCodes = arrayOfNulls<String>(codes.size + 1)
            listNames[0] = getString(R.string.language_system)
            listCodes[0] = "system"
            var dest = 1
            for (code in codes) {
                val locale = Locale.forLanguageTag(code)
                listNames[dest] = locale.getDisplayName(locale)
                listCodes[dest] = code
                dest++
            }
            languagePreference.entries = listNames
            languagePreference.entryValues = listCodes
        }
    }
}
