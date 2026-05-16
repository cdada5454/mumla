package se.lublin.mumla.channel

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import se.lublin.mumla.R
import androidx.appcompat.R as AppCompatR

object ChannelListLayout {
    private const val CHANNEL_TALKING_COLOR = 0xFF22C55E.toInt()

    @JvmStatic
    fun createRecyclerView(context: Context): RecyclerView {
        val density = context.resources.displayMetrics.density
        return RecyclerView(context).apply {
            id = R.id.channelUsers
            clipToPadding = false
            setPadding(
                (10 * density).toInt(),
                (10 * density).toInt(),
                (10 * density).toInt(),
                (10 * density).toInt()
            )
            isVerticalScrollBarEnabled = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    @JvmStatic
    fun createChannelRow(context: Context): LinearLayout {
        val density = context.resources.displayMetrics.density
        return LinearLayout(context).apply {
            id = R.id.channel_row_title
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = (74 * density).toInt()
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                (8 * density).toInt(),
                (8 * density).toInt(),
                (6 * density).toInt(),
                (8 * density).toInt()
            )
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            addView(FrameLayout(context).apply {
                setBackgroundResource(R.drawable.channel_contact_avatar_bg)
                layoutParams = LinearLayout.LayoutParams((42 * density).toInt(), (42 * density).toInt()).apply {
                    marginStart = (2 * density).toInt()
                }
                addView(ImageView(context).apply {
                    setImageResource(R.drawable.ic_action_channels)
                    layoutParams = FrameLayout.LayoutParams(
                        (20 * density).toInt(),
                        (20 * density).toInt(),
                        Gravity.CENTER
                    )
                    setColorFilter(0xFFFFFFFF.toInt())
                })
            })

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (12 * density).toInt()
                }

                addView(TextView(context).apply {
                    id = R.id.channel_row_name
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    textSize = 19f
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                })
                addView(TextView(context).apply {
                    id = R.id.channel_row_count
                    textSize = 13f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = (2 * density).toInt()
                    }
                })
            })

            addView(rowCapsuleButton(context, R.id.channel_row_join))
            addView(rowIcon(context, R.id.channel_row_talking, R.drawable.record_voice_over_24px).apply {
                visibility = View.GONE
                setColorFilter(CHANNEL_TALKING_COLOR)
            })
            addView(rowIcon(context, R.id.channel_row_more, R.drawable.ic_more_vert_black_24dp))
        }
    }

    @JvmStatic
    fun createUserRow(context: Context): LinearLayout {
        val density = context.resources.displayMetrics.density
        return LinearLayout(context).apply {
            id = R.id.user_row_title
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = (72 * density).toInt()
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                (14 * density).toInt(),
                (8 * density).toInt(),
                (6 * density).toInt(),
                (8 * density).toInt()
            )
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            addView(FrameLayout(context).apply {
                setBackgroundResource(R.drawable.user_contact_avatar_bg)
                layoutParams = LinearLayout.LayoutParams((42 * density).toInt(), (42 * density).toInt())
                addView(ImageView(context).apply {
                    id = R.id.user_row_talk_highlight
                    setImageResource(R.drawable.outline_circle_talking_off)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                })
            })

            addView(TextView(context).apply {
                id = R.id.user_row_name
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                textSize = 19f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (12 * density).toInt()
                }
            })

            addView(rowIcon(context, R.id.user_row_more, R.drawable.ic_more_vert_black_24dp))
        }
    }

    private fun rowIcon(context: Context, id: Int, drawable: Int): ImageView {
        val density = context.resources.displayMetrics.density
        return ImageView(context).apply {
            this.id = id
            setImageResource(drawable)
            setPadding(
                (8 * density).toInt(),
                (8 * density).toInt(),
                (8 * density).toInt(),
                (8 * density).toInt()
            )
            layoutParams = LinearLayout.LayoutParams((36 * density).toInt(), (36 * density).toInt())
        }
    }

    private fun rowCapsuleButton(context: Context, id: Int): TextView {
        val density = context.resources.displayMetrics.density
        return TextView(context).apply {
            this.id = id
            gravity = Gravity.CENTER
            includeFontPadding = false
            minWidth = (52 * density).toInt()
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 999f * density
                setColor(resolveThemeColor(context, AppCompatR.attr.colorPrimary, 0xFF3949AB.toInt()))
            }
            setPadding(
                (12 * density).toInt(),
                0,
                (12 * density).toInt(),
                0
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (32 * density).toInt()
            ).apply {
                marginEnd = (4 * density).toInt()
            }
        }
    }

    private fun resolveThemeColor(context: Context, attr: Int, fallback: Int): Int {
        val typedValue = TypedValue()
        return if (context.theme.resolveAttribute(attr, typedValue, true)) {
            typedValue.data
        } else {
            fallback
        }
    }
}
