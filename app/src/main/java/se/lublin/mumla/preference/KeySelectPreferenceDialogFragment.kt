package se.lublin.mumla.preference

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceDialogFragmentCompat
import se.lublin.mumla.R

class KeySelectPreferenceDialogFragment : PreferenceDialogFragmentCompat(), View.OnKeyListener {
    private lateinit var valueView: TextView
    private var currentValue = 0

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)

        builder.setNeutralButton(R.string.reset_key) { _, _ ->
            val preference = preference as KeySelectDialogPreference
            currentValue = 0
            if (preference.callChangeListener(currentValue)) {
                requireNotNull(preference.sharedPreferences)
                    .edit().putInt(preference.key, currentValue).apply()
            }
        }
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        view.setOnKeyListener(this)
        view.isFocusableInTouchMode = true
        view.requestFocus()

        valueView = view.findViewById(R.id.key_select_value_view)
        val preference = preference as KeySelectDialogPreference
        currentValue = requireNotNull(preference.sharedPreferences).getInt(preference.key, 0)
        updateValueView()
    }

    override fun onKey(view: View, keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return false
        }
        if (keyCode != KeyEvent.KEYCODE_BACK) {
            currentValue = keyCode
            updateValueView()
        } else {
            dismiss()
        }
        return true
    }

    private fun updateValueView() {
        if (currentValue == 0) {
            valueView.setText(R.string.no_ptt_key)
        } else {
            val stripPrefix = "KEYCODE_"
            var keyName = KeyEvent.keyCodeToString(currentValue)
            if (keyName.startsWith(stripPrefix)) {
                keyName = keyName.substring(stripPrefix.length)
            }
            valueView.text = keyName
        }
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            val preference = preference as KeySelectDialogPreference
            if (preference.callChangeListener(currentValue)) {
                requireNotNull(preference.sharedPreferences)
                    .edit().putInt(preference.key, currentValue).apply()
            }
        }
    }

    companion object {
        fun newInstance(key: String): KeySelectPreferenceDialogFragment {
            val fragment = KeySelectPreferenceDialogFragment()
            val bundle = Bundle(1)
            bundle.putString(ARG_KEY, key)
            fragment.arguments = bundle
            return fragment
        }
    }
}
