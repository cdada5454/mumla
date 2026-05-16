package se.lublin.mumla.preference

import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.preference.PreferenceDialogFragmentCompat
import se.lublin.mumla.R

class SeekBarPreferenceDialogFragment : PreferenceDialogFragmentCompat() {
    private lateinit var valueView: TextView
    private var multiplier = 0
    private var min = 0
    private var currentValue = 0
    private var suffix: String? = null

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        val seekBar: SeekBar = view.findViewById(R.id.seek_bar)
        valueView = view.findViewById(R.id.seek_bar_value_view)

        val preference = preference as SeekBarDialogPreference

        multiplier = preference.mMultiplier
        min = preference.mMin
        suffix = preference.mSuffix
        currentValue = requireNotNull(preference.sharedPreferences)
            .getInt(preference.key, preference.mDefaultValue * multiplier)
        updateValueView()

        seekBar.max = preference.mMax - min
        seekBar.progress = currentValue / multiplier - min

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentValue = (min + progress) * multiplier
                    updateValueView()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
    }

    private fun updateValueView() {
        val text = currentValue.toString()
        valueView.text = suffix?.let(text::plus) ?: text
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            val preference = preference as SeekBarDialogPreference
            if (preference.callChangeListener(currentValue)) {
                requireNotNull(preference.sharedPreferences)
                    .edit().putInt(preference.key, currentValue).apply()
            }
        }
    }

    companion object {
        fun newInstance(key: String): SeekBarPreferenceDialogFragment {
            val fragment = SeekBarPreferenceDialogFragment()
            val bundle = Bundle(1)
            bundle.putString(ARG_KEY, key)
            fragment.arguments = bundle
            return fragment
        }
    }
}
