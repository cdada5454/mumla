package se.lublin.mumla.preference

import android.os.Bundle
import androidx.preference.Preference
import se.lublin.mumla.BuildConfig
import se.lublin.mumla.R
import se.lublin.mumla.Settings
import se.lublin.mumla.app.DialogUtils.showAllNewsDialog

class AboutSettingsFragment : MumlaPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_about, rootKey)

        val summary = "${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})\nFOSS build"
        val versionPreference: Preference? = preferenceScreen.findPreference(VERSION_KEY)
        requireNotNull(versionPreference).summary = summary
        versionPreference.setOnPreferenceClickListener {
            Settings.getInstance(requireContext()).resetNewsShownVersion()
            true
        }
        val showNewsPreference: Preference? = preferenceScreen.findPreference("showNews")
        requireNotNull(showNewsPreference).setOnPreferenceClickListener {
            showAllNewsDialog(requireContext())
            true
        }
    }

    companion object {
        private const val VERSION_KEY = "version"
    }
}
