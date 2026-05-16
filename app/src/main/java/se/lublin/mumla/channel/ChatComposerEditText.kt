package se.lublin.mumla.channel

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

class ChatComposerEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {
    override fun onDraw(canvas: Canvas) {
        val layout = layout
        if (layout != null && lineCount <= 1) {
            val contentHeight = height - compoundPaddingTop - compoundPaddingBottom
            val textHeight = layout.height
            val offset = ((contentHeight - textHeight) * 0.3f).coerceAtLeast(0f)
            canvas.save()
            canvas.translate(0f, offset)
            super.onDraw(canvas)
            canvas.restore()
        } else {
            super.onDraw(canvas)
        }
    }
}
