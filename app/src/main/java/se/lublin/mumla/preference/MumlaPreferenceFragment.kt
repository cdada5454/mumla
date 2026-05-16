package se.lublin.mumla.preference

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceDialogFragmentCompat
import androidx.preference.PreferenceFragmentCompat
import se.lublin.mumla.R

abstract class MumlaPreferenceFragment : PreferenceFragmentCompat() {
    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        val fragment = preference.fragment
        if (fragment != null) {
            val bundle = Bundle()
            bundle.putString("fragmentClassName", fragment)
            bundle.putCharSequence("title", preference.title)
            parentFragmentManager.setFragmentResult("launchFragment", bundle)
            return true
        }
        return super.onPreferenceTreeClick(preference)
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is SeekBarDialogPreference) {
            if (parentFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) != null) {
                return
            }
            val dialogFragment: PreferenceDialogFragmentCompat =
                SeekBarPreferenceDialogFragment.newInstance(preference.key)
            dialogFragment.setTargetFragment(this, 0)
            dialogFragment.show(parentFragmentManager, DIALOG_FRAGMENT_TAG)
            return
        }
        if (preference is KeySelectDialogPreference) {
            if (parentFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) != null) {
                return
            }
            val dialogFragment: PreferenceDialogFragmentCompat =
                KeySelectPreferenceDialogFragment.newInstance(preference.key)
            dialogFragment.setTargetFragment(this, 0)
            dialogFragment.show(parentFragmentManager, DIALOG_FRAGMENT_TAG)
            return
        }
        super.onDisplayPreferenceDialog(preference)
    }

    override fun onResume() {
        super.onResume()

        val actionBar = (requireActivity() as AppCompatActivity).supportActionBar ?: return
        val title = arguments?.getCharSequence("title")
        if (title == null) {
            actionBar.setTitle(R.string.action_settings)
            return
        }
        actionBar.title = title
    }

    companion object {
        private const val DIALOG_FRAGMENT_TAG = "androidx.preference.PreferenceFragment.DIALOG"
    }
}
