package se.lublin.mumla.preference

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import se.lublin.mumla.R
import se.lublin.mumla.Settings

class AudioSettingsFragment : MumlaPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_audio, rootKey)

        val inputPreference: ListPreference? = preferenceScreen.findPreference(Settings.PREF_INPUT_METHOD)
        requireNotNull(inputPreference).setOnPreferenceChangeListener { _, newValue ->
            updateAudioDependents(preferenceScreen, newValue as String)
            true
        }

        val inputQualityPreference: ListPreference? = preferenceScreen.findPreference(Settings.PREF_INPUT_RATE)
        requireNotNull(inputQualityPreference)
        val bitrateNames = Array(inputQualityPreference.entryValues.size) { index ->
            val bitrate = inputQualityPreference.entryValues[index].toString().toInt()
            "$bitrate Hz".replace(" ", "")
        }
        inputQualityPreference.entries = bitrateNames

        val echoCancellationPref: ListPreference? = findPreference(Settings.PREF_ECHO_CANCELLATION_METHOD)
        if (echoCancellationPref != null) {
            val current = echoCancellationPref.value
            if (current == null || !echoCancellationPref.entryValues.contains(current)) {
                echoCancellationPref.value = Settings.DEFAULT_ECHO_CANCELLATION_METHOD
            }
        }

        updateAudioDependents(preferenceScreen, inputPreference.value)
    }

    companion object {
        private fun updateAudioDependents(screen: PreferenceScreen, inputMethod: String) {
            val pttCategory: PreferenceCategory? = screen.findPreference("ptt_settings")
            val vadCategory: PreferenceCategory? = screen.findPreference("vad_settings")
            requireNotNull(pttCategory).isEnabled = Settings.ARRAY_INPUT_METHOD_PTT == inputMethod
            requireNotNull(vadCategory).isEnabled = Settings.ARRAY_INPUT_METHOD_VOICE == inputMethod
        }
    }
}
