package se.lublin.mumla.preference

import android.os.Bundle
import androidx.preference.Preference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import se.lublin.mumla.BuildConfig
import se.lublin.mumla.R
import se.lublin.mumla.Settings
import se.lublin.mumla.app.DialogUtils.showAllNewsDialog

class AboutSettingsFragment : MumlaPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_about, rootKey)

        var summary = "${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})"
        if (BuildConfig.FLAVOR == "foss") {
            summary += "\nFOSS flavor"
        } else if (BuildConfig.FLAVOR == "beta") {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")
            summary += "\nBeta flavor with versioncode ${BuildConfig.VERSION_CODE}"
            summary += "\nBuildtime ${dateFormat.format(Date(BuildConfig.TIMESTAMP))} UTC"
        } else if (BuildConfig.FLAVOR == "donation") {
            summary += "\n*) ${getString(R.string.donation_thanks)}"
        }
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
