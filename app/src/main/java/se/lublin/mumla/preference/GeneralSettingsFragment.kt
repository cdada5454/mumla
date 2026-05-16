package se.lublin.mumla.preference

import android.os.Bundle
import androidx.preference.Preference
import info.guardianproject.netcipher.proxy.OrbotHelper
import se.lublin.mumla.R

class GeneralSettingsFragment : MumlaPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_general, rootKey)

        val useOrbotPreference: Preference? = preferenceScreen.findPreference(USE_TOR_KEY)
        requireNotNull(useOrbotPreference).isEnabled = OrbotHelper.isOrbotInstalled(requireContext())
    }

    companion object {
        private const val USE_TOR_KEY = "useTor"
    }
}
