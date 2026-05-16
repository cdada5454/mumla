package se.lublin.mumla.channel

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.max

class ChatKeyboardFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    interface KeyboardHeightDelegate {
        fun onKeyboardHeightChanged(height: Int)
    }

    private val visibleFrame = Rect()
    private var keyboardHeight = 0
    private var useInsetsForKeyboard = false
    private var delegate: KeyboardHeightDelegate? = null

    private val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        notifyKeyboardHeightChangedFromVisibleFrame()
    }

    fun setKeyboardHeightDelegate(delegate: KeyboardHeightDelegate?) {
        this.delegate = delegate
        notifyKeyboardHeightChangedFromVisibleFrame()
        ViewCompat.requestApplyInsets(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            notifyKeyboardHeightChangedFromInsets(insets)
            insets
        }
        viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
        post {
            notifyKeyboardHeightChangedFromVisibleFrame()
            ViewCompat.requestApplyInsets(this)
        }
    }

    override fun onDetachedFromWindow() {
        ViewCompat.setOnApplyWindowInsetsListener(this, null)
        viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
        delegate = null
        super.onDetachedFromWindow()
    }

    private fun notifyKeyboardHeightChangedFromInsets(insets: WindowInsetsCompat) {
        useInsetsForKeyboard = true
        val newKeyboardHeight = if (insets.isVisible(WindowInsetsCompat.Type.ime())) {
            insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        } else {
            0
        }
        updateKeyboardHeight(newKeyboardHeight)
    }

    private fun notifyKeyboardHeightChangedFromVisibleFrame() {
        if (useInsetsForKeyboard) return
        if (height == 0) return
        getWindowVisibleDisplayFrame(visibleFrame)
        val screenHeight = rootView.height
        val bottomInset = max(0, screenHeight - visibleFrame.bottom)
        val newKeyboardHeight = if (bottomInset > height * KEYBOARD_MIN_HEIGHT_RATIO) bottomInset else 0
        updateKeyboardHeight(newKeyboardHeight)
    }

    private fun updateKeyboardHeight(newKeyboardHeight: Int) {
        if (newKeyboardHeight != keyboardHeight) {
            keyboardHeight = newKeyboardHeight
            delegate?.onKeyboardHeightChanged(keyboardHeight)
        }
    }

    private companion object {
        const val KEYBOARD_MIN_HEIGHT_RATIO = 0.15f
    }
}
