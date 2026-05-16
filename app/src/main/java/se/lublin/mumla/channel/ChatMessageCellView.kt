package se.lublin.mumla.channel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Html
import android.text.Layout
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.max
import kotlin.math.min
import se.lublin.mumla.R
import se.lublin.mumla.util.MumbleImageGetter

class ChatMessageCellView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    data class Message(
        val body: String,
        val receivedTime: Long,
        val outgoing: Boolean,
        val info: Boolean,
        val media: Boolean
    )

    data class GroupPosition(
        val newerSameOwner: Boolean,
        val olderSameOwner: Boolean
    )

    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x22000000
        style = Paint.Style.FILL
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 15f.sp()
    }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f.sp()
        textAlign = Paint.Align.RIGHT
    }
    private val bubblePath = Path()
    private val bubbleRect = RectF()
    private val imageGetter = MumbleImageGetter(context)
    private val dateFormat: DateFormat = SimpleDateFormat.getTimeInstance(DateFormat.SHORT)

    private var message: Message? = null
    private var groupPosition = GroupPosition(newerSameOwner = false, olderSameOwner = false)
    private var messageText: Spanned = Html.fromHtml("")
    private var textLayout: StaticLayout? = null
    private var timeText = ""
    private var measuredTextWidth = 0
    private var bubbleLeft = 0f
    private var bubbleTop = 0f
    private var bubbleRight = 0f
    private var bubbleBottom = 0f
    private var textLeft = 0f
    private var textTop = 0f
    private var timeX = 0f
    private var timeBaseline = 0f

    fun setMessage(message: Message, groupPosition: GroupPosition) {
        if (this.message == message && this.groupPosition == groupPosition) return
        this.message = message
        this.groupPosition = groupPosition
        messageText = Html.fromHtml(message.body, imageGetter, null)
        timeText = dateFormat.format(Date(message.receivedTime))
        textPaint.color = ContextCompat.getColor(
            context,
            when {
                message.info -> R.color.chat_text_on_info
                message.outgoing -> R.color.chat_text_on_outgoing
                else -> R.color.chat_text_on_incoming
            }
        )
        timePaint.color = ContextCompat.getColor(
            context,
            if (message.outgoing) R.color.chat_meta_on_outgoing else R.color.chat_meta_on_surface
        )
        bubblePaint.color = ContextCompat.getColor(
            context,
            when {
                message.info -> R.color.chat_bubble_info
                message.outgoing -> R.color.chat_bubble_outgoing
                else -> R.color.chat_bubble_incoming
            }
        )
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val currentMessage = message
        val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        val availableWidth = max(1, parentWidth - horizontalGap * 2)
        val maxBubbleWidth = (availableWidth * if (currentMessage?.media == true) 0.82f else 0.78f).toInt()
        val maxTextWidth = max(1, maxBubbleWidth - (paddingLeftInside + paddingRightInside).toInt())
        textLayout = StaticLayout(
            messageText,
            textPaint,
            maxTextWidth,
            Layout.Alignment.ALIGN_NORMAL,
            1.0f,
            0f,
            false
        )
        val layout = textLayout
        var contentWidth = 0f
        if (layout != null) {
            for (line in 0 until layout.lineCount) {
                contentWidth = max(contentWidth, layout.getLineWidth(line))
            }
        }
        val timeWidth = timePaint.measureText(timeText)
        measuredTextWidth = min(maxTextWidth, max(contentWidth, timeWidth).toInt())
        textLayout = StaticLayout(
            messageText,
            textPaint,
            max(1, measuredTextWidth),
            Layout.Alignment.ALIGN_NORMAL,
            1.0f,
            0f,
            false
        )

        val textHeight = textLayout?.height ?: 0
        val bubbleWidth = measuredTextWidth + paddingLeftInside + paddingRightInside
        val bubbleHeight = paddingTopInside + textHeight + timeTopGap + timeHeight + paddingBottomInside
        val desiredHeight = (bubbleHeight + verticalGap * 2).toInt()
        setMeasuredDimension(parentWidth, desiredHeight)
        layoutBubble(parentWidth, bubbleWidth, bubbleHeight)
    }

    override fun onDraw(canvas: Canvas) {
        val currentMessage = message ?: return
        drawBubble(canvas, currentMessage)
        canvas.save()
        canvas.translate(textLeft, textTop)
        textLayout?.draw(canvas)
        canvas.restore()
        canvas.drawText(timeText, timeX, timeBaseline, timePaint)
    }

    private fun layoutBubble(parentWidth: Int, bubbleWidth: Float, bubbleHeight: Float) {
        bubbleTop = verticalGap.toFloat()
        bubbleBottom = bubbleTop + bubbleHeight
        val currentMessage = message
        if (currentMessage?.outgoing == true && currentMessage.info == false) {
            bubbleLeft = parentWidth - horizontalGap - bubbleWidth
        } else {
            bubbleLeft = horizontalGap.toFloat()
        }
        bubbleRight = bubbleLeft + bubbleWidth
        textLeft = bubbleLeft + paddingLeftInside
        textTop = bubbleTop + paddingTopInside
        timeX = bubbleRight - paddingRightInside
        timeBaseline = bubbleBottom - paddingBottomInside - timePaint.descent()
    }

    private fun drawBubble(canvas: Canvas, message: Message) {
        bubblePath.reset()

        val largeRadius = bubbleRadius
        val nearRadius = nearRadius
        val topLeft = if (!message.outgoing && groupPosition.olderSameOwner) nearRadius else largeRadius
        val bottomLeft = if (!message.outgoing && groupPosition.newerSameOwner) nearRadius else largeRadius
        val topRight = if (message.outgoing && groupPosition.olderSameOwner) nearRadius else largeRadius
        val bottomRight = if (message.outgoing && groupPosition.newerSameOwner) nearRadius else largeRadius

        val top = bubbleTop
        val bottom = bubbleBottom
        bubbleRect.set(bubbleLeft, top, bubbleRight, bottom)
        bubblePath.addRoundRect(
            bubbleRect,
            floatArrayOf(
                topLeft, topLeft,
                topRight, topRight,
                bottomRight, bottomRight,
                bottomLeft, bottomLeft
            ),
            Path.Direction.CW
        )

        canvas.save()
        canvas.translate(0f, 1f.dp())
        canvas.drawPath(bubblePath, shadowPaint)
        canvas.restore()
        canvas.drawPath(bubblePath, bubblePaint)
    }

    private companion object {
        val horizontalGap = 0f.dp().toInt()
        val verticalGap = 3f.dp().toInt()
        val paddingLeftInside = 14f.dp()
        val paddingRightInside = 14f.dp()
        val paddingTopInside = 9f.dp()
        val paddingBottomInside = 7f.dp()
        val timeTopGap = 3f.dp().toInt()
        val timeHeight = 13f.dp().toInt()
        val bubbleRadius = 18f.dp()
        val nearRadius = 6f.dp()

        fun Float.dp(): Float = this * android.content.res.Resources.getSystem().displayMetrics.density
        fun Float.sp(): Float = this * android.content.res.Resources.getSystem().displayMetrics.scaledDensity
    }
}
