package se.lublin.mumla.channel

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.viewpager.widget.ViewPager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.tabs.TabLayout
import se.lublin.mumla.R
import com.google.android.material.R as MaterialR

object ChannelLayout {
    private const val PUSH_TO_TALK_BUTTON_SIZE_DP = 64
    private const val PUSH_TO_TALK_ICON_SIZE_DP = 36
    private const val PUSH_TO_TALK_PANEL_SIZE_DP = 96
    private const val PUSH_TO_TALK_MARGIN_DP = 16
    private const val PUSH_TO_TALK_ELEVATION_DP = 0
    private const val PUSH_TO_TALK_PRESSED_ELEVATION_DP = 0
    private const val PUSH_TO_TALK_TRANSLATION_Z_DP = 0
    private const val PUSH_TO_TALK_PRESSED_TRANSLATION_Z_DP = 0
    private const val PUSH_TO_TALK_RIPPLE_TAG_PREFIX = "push_to_talk_ripple_"
    private const val PUSH_TO_TALK_RIPPLE_COUNT = 3
    private const val PUSH_TO_TALK_RIPPLE_COLOR = 0xFF22C55E.toInt()
    private const val PUSH_TO_TALK_RIPPLE_INTERVAL_MS = 180L
    private const val PUSH_TO_TALK_RIPPLE_DURATION_MS = 520L
    private val pushToTalkPressInterpolator = AccelerateDecelerateInterpolator()

    @JvmStatic
    fun create(context: Context): ViewGroup {
        return if (context.resources.configuration.screenWidthDp >= 600) {
            createSplitLayout(context)
        } else {
            createPagedLayout(context)
        }
    }

    private fun createPagedLayout(context: Context): ViewGroup {
        val density = context.resources.displayMetrics.density
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            addView(createTabLayout(context).apply {
                id = R.id.channel_tab_layout
                visibility = android.view.View.GONE
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.START
                    setMargins(
                        (12 * density).toInt(),
                        (8 * density).toInt(),
                        (12 * density).toInt(),
                        (8 * density).toInt()
                    )
                }
            })
            addView(ViewPager(context).apply {
                id = R.id.channel_view_pager
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            })
            addView(createTargetPanel(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins((12 * density).toInt(), 0, (12 * density).toInt(), (8 * density).toInt())
                }
            })
        }
    }

    private fun createSplitLayout(context: Context): ViewGroup {
        val density = context.resources.displayMetrics.density
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (12 * density).toInt(),
                (12 * density).toInt(),
                (12 * density).toInt(),
                (12 * density).toInt()
            )
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            addView(FrameLayout(context).apply {
                id = R.id.list_fragment
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            })
            addView(createTargetPanel(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (8 * density).toInt()
                    bottomMargin = (8 * density).toInt()
                }
            })
        }
    }

    private fun createTabLayout(context: Context): TabLayout {
        val density = context.resources.displayMetrics.density
        return TabLayout(context).apply {
            setBackgroundResource(R.drawable.channel_tab_container_bg)
            setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
            tabMode = TabLayout.MODE_SCROLLABLE
            tabGravity = TabLayout.GRAVITY_CENTER
            setSelectedTabIndicator(null)
            setTabTextColors(
                resources.getColor(R.color.channel_tab_unselected_text),
                resources.getColor(R.color.channel_tab_selected_text)
            )
        }
    }

    private fun createTargetPanel(context: Context): MaterialCardView {
        val density = context.resources.displayMetrics.density
        return MaterialCardView(context).apply {
            id = R.id.target_panel
            visibility = View.GONE
            radius = 18 * density
            cardElevation = 0f
            setCardBackgroundColor(resources.getColor(R.color.chat_composer_surface))
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = (46 * density).toInt()
                setPadding((14 * density).toInt(), 0, (6 * density).toInt(), 0)
                addView(TextView(context).apply {
                    id = R.id.target_panel_warning
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    isSingleLine = true
                    setTextColor(resources.getColor(R.color.chat_meta_on_surface))
                    textSize = 12f
                    setTypeface(typeface, android.graphics.Typeface.ITALIC)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                })
                addView(ImageView(context).apply {
                    id = R.id.target_panel_cancel
                    setImageResource(R.drawable.ic_action_delete_dark)
                    setPadding((6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt())
                    layoutParams = LinearLayout.LayoutParams((32 * density).toInt(), (32 * density).toInt())
                })
            })
        }
    }

    fun createPushToTalkPanel(context: Context): FrameLayout {
        val density = context.resources.displayMetrics.density
        val contentColor = resolveAppBarContentColor(context)
        val panelSize = resolvePushToTalkPanelSize(context)
        val buttonSize = resolvePushToTalkButtonSize(context)
        val iconSize = resolvePushToTalkIconSize(context)
        return FrameLayout(context).apply {
            id = R.id.pushtotalk_view
            minimumWidth = panelSize
            minimumHeight = panelSize
            clipToPadding = false
            clipChildren = false
            val shadowPadding = (16 * density).toInt()
            setPadding(shadowPadding, shadowPadding, shadowPadding, shadowPadding)
            setBackgroundColor(Color.TRANSPARENT)
            repeat(PUSH_TO_TALK_RIPPLE_COUNT) { index ->
                addView(View(context).apply {
                    tag = pushToTalkRippleTag(index)
                    alpha = 0f
                    visibility = View.INVISIBLE
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(PUSH_TO_TALK_RIPPLE_COLOR)
                    }
                    layoutParams = FrameLayout.LayoutParams(
                        buttonSize,
                        buttonSize,
                        Gravity.CENTER
                    )
                })
            }
            addView(MaterialCardView(context).apply {
                id = R.id.pushtotalk_card
                radius = buttonSize / 2f
                cardElevation = PUSH_TO_TALK_ELEVATION_DP * density
                maxCardElevation = 16 * density
                translationZ = PUSH_TO_TALK_TRANSLATION_Z_DP * density
                useCompatPadding = false
                strokeWidth = 0
                strokeColor = Color.TRANSPARENT
                foreground = null
                rippleColor = ColorStateList.valueOf(Color.TRANSPARENT)
                setCardBackgroundColor(resolvePushToTalkBackgroundColor(context))
                layoutParams = FrameLayout.LayoutParams(
                    buttonSize,
                    buttonSize,
                    Gravity.CENTER
                ).apply {
                    val margin = (PUSH_TO_TALK_MARGIN_DP * density).toInt()
                    setMargins(margin, margin, margin, margin)
                }
                addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding((10 * density).toInt(), 0, (10 * density).toInt(), 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                addView(ImageButton(context).apply {
                    id = R.id.pushtotalk
                    setImageResource(R.drawable.record_voice_over_24px)
                    setColorFilter(contentColor)
                    setBackgroundColor(Color.TRANSPARENT)
                    background = null
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    minimumHeight = 0
                    minimumWidth = 0
                    setPadding(0, 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(
                        iconSize,
                        iconSize
                    ).apply {
                        gravity = Gravity.CENTER
                    }
                })
                addView(LinearLayout(context).apply {
                    visibility = View.GONE
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                addView(TextView(context).apply {
                    id = R.id.pushtotalk_server
                    isSingleLine = true
                    textSize = 11f
                    setTextColor(contentColor)
                    alpha = 0.82f
                    includeFontPadding = false
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                })
                addView(TextView(context).apply {
                    text = "  ·  "
                    textSize = 11f
                    setTextColor(contentColor)
                    alpha = 0.82f
                    includeFontPadding = false
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                })
                addView(TextView(context).apply {
                    id = R.id.pushtotalk_channel
                    isSingleLine = true
                    textSize = 11f
                    setTextColor(contentColor)
                    alpha = 0.82f
                    includeFontPadding = false
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                })
                })
                })
            })
        }
    }

    fun updatePushToTalkPressedState(panel: View?, pressed: Boolean) {
        val context = panel?.context ?: return
        val density = context.resources.displayMetrics.density
        val card = panel.findViewById<MaterialCardView>(R.id.pushtotalk_card) ?: return
        val targetScale = if (pressed) 0.98f else 1f
        card.setCardBackgroundColor(if (pressed) resolveAppBarPressedColor(context) else resolvePushToTalkBackgroundColor(context))
        card.cardElevation = if (pressed) {
            PUSH_TO_TALK_PRESSED_ELEVATION_DP * density
        } else {
            PUSH_TO_TALK_ELEVATION_DP * density
        }
        card.translationZ = if (pressed) {
            PUSH_TO_TALK_PRESSED_TRANSLATION_Z_DP * density
        } else {
            PUSH_TO_TALK_TRANSLATION_Z_DP * density
        }
        card.animate().cancel()
        card.animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .setDuration(if (pressed) 90L else 140L)
            .setInterpolator(pushToTalkPressInterpolator)
            .start()
        panel.isActivated = pressed
        if (pressed) {
            startPushToTalkRippleLoop(panel)
        }
    }

    private fun startPushToTalkRippleLoop(panel: View) {
        emitPushToTalkRipple(panel, 0)
    }

    private fun emitPushToTalkRipple(panel: View, index: Int) {
        if (!panel.isActivated) {
            return
        }
        val ripple = (panel as? ViewGroup)?.findViewWithTag<View>(pushToTalkRippleTag(index)) ?: return
        ripple.animate().cancel()
        ripple.visibility = View.VISIBLE
        ripple.scaleX = 1f
        ripple.scaleY = 1f
        ripple.alpha = 0.35f
        ripple.animate()
            .scaleX(1.55f)
            .scaleY(1.55f)
            .alpha(0f)
            .setDuration(PUSH_TO_TALK_RIPPLE_DURATION_MS)
            .setInterpolator(pushToTalkPressInterpolator)
            .withEndAction {
                ripple.visibility = View.INVISIBLE
            }
            .start()
        panel.postDelayed({
            emitPushToTalkRipple(panel, (index + 1) % PUSH_TO_TALK_RIPPLE_COUNT)
        }, PUSH_TO_TALK_RIPPLE_INTERVAL_MS)
    }

    private fun pushToTalkRippleTag(index: Int): String {
        return "$PUSH_TO_TALK_RIPPLE_TAG_PREFIX$index"
    }

    fun updatePushToTalkSize(panel: View?) {
        val context = panel?.context ?: return
        val panelSize = resolvePushToTalkPanelSize(context)
        val buttonSize = resolvePushToTalkButtonSize(context)
        panel.layoutParams = panel.layoutParams?.apply {
            width = panelSize
            height = panelSize
        }
        panel.minimumWidth = panelSize
        panel.minimumHeight = panelSize
        (panel as? ViewGroup)?.clipToPadding = false
        (panel as? ViewGroup)?.clipChildren = false
        val card = panel.findViewById<MaterialCardView>(R.id.pushtotalk_card) ?: return
        card.radius = buttonSize / 2f
        card.strokeWidth = 0
        card.strokeColor = Color.TRANSPARENT
        card.foreground = null
        card.rippleColor = ColorStateList.valueOf(Color.TRANSPARENT)
        card.layoutParams = (card.layoutParams as? FrameLayout.LayoutParams)?.apply {
            width = buttonSize
            height = buttonSize
            gravity = Gravity.CENTER
        } ?: card.layoutParams
        repeat(PUSH_TO_TALK_RIPPLE_COUNT) { index ->
            val ripple = (panel as? ViewGroup)?.findViewWithTag<View>(pushToTalkRippleTag(index))
            ripple?.layoutParams = (ripple?.layoutParams as? FrameLayout.LayoutParams)?.apply {
                width = buttonSize
                height = buttonSize
                gravity = Gravity.CENTER
            } ?: ripple?.layoutParams
        }
        panel.requestLayout()
    }

    fun resolveActionBarSize(context: Context): Int {
        val typedValue = TypedValue()
        if (context.theme.resolveAttribute(androidx.appcompat.R.attr.actionBarSize, typedValue, true)) {
            return TypedValue.complexToDimensionPixelSize(typedValue.data, context.resources.displayMetrics)
        }
        return (56 * context.resources.displayMetrics.density).toInt()
    }

    fun resolveImmersiveAppBarSize(context: Context): Int {
        return resolveActionBarSize(context) + resolveStatusBarHeight(context)
    }

    fun resolvePushToTalkButtonSize(context: Context): Int {
        return (PUSH_TO_TALK_BUTTON_SIZE_DP * context.resources.displayMetrics.density).toInt()
    }

    fun resolvePushToTalkIconSize(context: Context): Int {
        return (PUSH_TO_TALK_ICON_SIZE_DP * context.resources.displayMetrics.density).toInt()
    }

    fun resolvePushToTalkPanelSize(context: Context): Int {
        return (PUSH_TO_TALK_PANEL_SIZE_DP * context.resources.displayMetrics.density).toInt()
    }

    fun resolveAppBarBackgroundColor(context: Context): Int {
        return resolveDynamicThemeColor(context, MaterialR.attr.colorPrimaryContainer, android.R.attr.colorPrimary)
    }

    fun resolveAppBarPressedColor(context: Context): Int {
        return context.resources.getColor(R.color.md_theme_light_primary_darker)
    }

    private fun resolvePushToTalkBackgroundColor(context: Context): Int {
        return context.resources.getColor(R.color.md_theme_light_primary_darker)
    }

    private fun resolveAppBarContentColor(context: Context): Int {
        return context.resources.getColor(R.color.md_theme_light_onPrimary)
    }

    private fun resolveDynamicThemeColor(context: Context, attr: Int, fallbackAttr: Int): Int {
        val dynamicContext = DynamicColors.wrapContextIfAvailable(context)
        val fallbackColor = MaterialColors.getColor(context, fallbackAttr, Color.BLACK)
        return MaterialColors.getColor(dynamicContext, attr, fallbackColor)
    }

    private fun resolveStatusBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            return context.resources.getDimensionPixelSize(resourceId)
        }
        return 0
    }
}
