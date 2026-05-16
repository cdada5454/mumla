package se.lublin.mumla.servers

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import se.lublin.mumla.R

object ServerListLayout {
    @JvmStatic
    fun createFavouriteServerList(context: Context): ViewGroup {
        val density = context.resources.displayMetrics.density
        return FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            addView(GridView(context).apply {
                id = R.id.server_list_grid
                gravity = Gravity.TOP
                numColumns = 1
                verticalSpacing = 0
                horizontalSpacing = 0
                setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
                stretchMode = GridView.STRETCH_COLUMN_WIDTH
                clipToPadding = false
                isFastScrollEnabled = true
                setSelector(android.R.color.transparent)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            })

            addView(TextView(context).apply {
                id = R.id.server_list_grid_empty
                setText(R.string.no_servers)
                textSize = 15f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                visibility = View.GONE
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            })
        }
    }

    @JvmStatic
    fun createFavouriteServerRow(context: Context): ViewGroup {
        val density = context.resources.displayMetrics.density
        return LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = (78 * density).toInt()
            setPadding(
                (14 * density).toInt(),
                (10 * density).toInt(),
                (8 * density).toInt(),
                (10 * density).toInt()
            )
            setBackgroundResource(context.resourceFromAttr(android.R.attr.selectableItemBackground))
            layoutParams = AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            addView(FrameLayout(context).apply {
                setBackgroundResource(R.drawable.server_contact_avatar_bg)
                layoutParams = LinearLayout.LayoutParams((48 * density).toInt(), (48 * density).toInt())
                addView(ImageView(context).apply {
                    setImageResource(R.drawable.ic_action_user_dark)
                    setColorFilter(0xFFFFFFFF.toInt())
                    layoutParams = FrameLayout.LayoutParams(
                        (24 * density).toInt(),
                        (24 * density).toInt(),
                        Gravity.CENTER
                    )
                })
            })

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (14 * density).toInt()
                }

                addView(LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )

                    addView(rowText(context, R.id.server_row_name, 21f, android.R.attr.textColorPrimary).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    addView(rowText(context, R.id.server_row_latency, 14f, android.R.attr.textColorSecondary).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            marginStart = (10 * density).toInt()
                        }
                    })
                })

                addView(rowText(context, R.id.server_row_address, 15f, android.R.attr.textColorPrimary).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                })

                addView(createStatusRow(context, compact = true))

                addView(TextView(context).apply {
                    id = R.id.server_row_user
                    visibility = View.GONE
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                })
            })

            addView(rowIcon(context, R.id.server_row_more, R.drawable.ic_more_vert_black_24dp))
        }
    }

    private fun createStatusRow(context: Context, compact: Boolean): ViewGroup {
        val density = context.resources.displayMetrics.density
        val statusTextSize = if (compact) 13f else 14f
        return LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            addView(rowText(context, R.id.server_row_version_status, statusTextSize, android.R.attr.textColorSecondary).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            })
            addView(rowText(context, R.id.server_row_usercount, statusTextSize, android.R.attr.textColorSecondary).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = (8 * density).toInt()
                }
            })
            addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                id = R.id.server_row_ping_progress
                isIndeterminate = true
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams((72 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = (8 * density).toInt()
                }
            })
        }
    }

    private fun rowText(context: Context, id: Int, sizeSp: Float, colorAttr: Int): TextView {
        return TextView(context).apply {
            this.id = id
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(context.colorFromAttr(colorAttr))
            textSize = sizeSp
        }
    }

    private fun rowIcon(context: Context, id: Int, drawable: Int): ImageView {
        val density = context.resources.displayMetrics.density
        return ImageView(context).apply {
            this.id = id
            setImageResource(drawable)
            setColorFilter(context.colorFromAttr(android.R.attr.textColorSecondary))
            setPadding(
                (8 * density).toInt(),
                (8 * density).toInt(),
                (8 * density).toInt(),
                (8 * density).toInt()
            )
            setBackgroundResource(context.resourceFromAttr(android.R.attr.selectableItemBackgroundBorderless))
            layoutParams = LinearLayout.LayoutParams((36 * density).toInt(), (36 * density).toInt())
        }
    }

    private fun Context.colorFromAttr(attr: Int): Int {
        val value = TypedValue()
        theme.resolveAttribute(attr, value, true)
        return if (value.resourceId != 0) {
            ContextCompat.getColor(this, value.resourceId)
        } else {
            value.data
        }
    }

    private fun Context.resourceFromAttr(attr: Int): Int {
        val value = TypedValue()
        theme.resolveAttribute(attr, value, true)
        return value.resourceId
    }
}
