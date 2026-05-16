package se.lublin.mumla.preference

import android.content.Context
import android.util.AttributeSet
import androidx.preference.DialogPreference
import se.lublin.mumla.R

class SeekBarDialogPreference(context: Context, attrs: AttributeSet?) : DialogPreference(context, attrs) {
    val mMax: Int
    val mMin: Int
    val mMultiplier: Int
    val mSuffix: String?
    val mDefaultValue: Int

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.SeekBarDialogPreference, 0, 0).use { typedArray ->
            mMax = typedArray.getInt(R.styleable.SeekBarDialogPreference_max, 100)
            mMin = typedArray.getInt(R.styleable.SeekBarDialogPreference_min, 0)
            mMultiplier = typedArray.getInt(R.styleable.SeekBarDialogPreference_multiplier, 1)
            mSuffix = typedArray.getString(R.styleable.SeekBarDialogPreference_android_text)
            mDefaultValue = typedArray.getInt(R.styleable.SeekBarDialogPreference_android_defaultValue, 0)
        }

        setDialogLayoutResource(R.layout.dialog_seekbar_preference)
    }
}
