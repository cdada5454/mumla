package se.lublin.mumla.preference

import android.os.Bundle
import se.lublin.mumla.R

class AuthenticationSettingsFragment : MumlaPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_authentication, rootKey)
    }
}
