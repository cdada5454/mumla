package se.lublin.mumla.app

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.Toolbar
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR
import se.lublin.mumla.R

object MumlaMainLayout {
    @JvmStatic
    fun create(activity: Activity): Views {
        val drawerLayout = DrawerLayout(activity).apply {
            id = R.id.drawer_layout
            fitsSystemWindows = false
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val contentColumn = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = DrawerLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val appBarBackgroundColor = resolveDynamicThemeColor(
            activity,
            MaterialR.attr.colorPrimaryContainer,
            android.R.attr.colorPrimary
        )
        val appBarContentColor = resolveDynamicThemeColor(
            activity,
            MaterialR.attr.colorOnPrimaryContainer,
            MaterialR.attr.colorOnPrimary
        )

        val appBar = AppBarLayout(ContextThemeWrapper(activity, R.style.TweakedAppBarLayout)).apply {
            elevation = 0f
            setBackgroundColor(appBarBackgroundColor)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        ViewCompat.setOnApplyWindowInsetsListener(appBar) { view, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = statusBarTop)
            insets
        }

        val toolbar = MaterialToolbar(activity).apply {
            id = R.id.toolbar
            setTitle(R.string.app_name)
            setBackgroundColor(appBarBackgroundColor)
            setTitleTextColor(appBarContentColor)
            setNavigationIconTint(Color.BLACK)
            val popupOverlay = android.util.TypedValue()
            if (activity.theme.resolveAttribute(R.attr.popupOverlay, popupOverlay, true)) {
                popupTheme = popupOverlay.resourceId
            }
            layoutParams = AppBarLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                resolveActionBarSize(activity)
            )
        }
        appBar.addView(toolbar)

        val contentFrame = FrameLayout(activity).apply {
            id = R.id.content_frame
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        contentColumn.addView(appBar)
        contentColumn.addView(contentFrame)
        drawerLayout.addView(contentColumn)

        val drawer = ComposeView(activity).apply {
            id = R.id.left_drawer
            setBackgroundColor(
                resolveDynamicResourceColor(activity, MaterialR.attr.colorSurface, R.color.drawer_background)
            )
            fitsSystemWindows = false
            layoutParams = DrawerLayout.LayoutParams(
                activity.resources.displayMetrics.density.let { (336 * it).toInt() },
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = android.view.Gravity.START
            }
        }
        drawerLayout.addView(drawer)

        return Views(drawerLayout, toolbar, drawer)
    }

    class Views(
        @JvmField val root: DrawerLayout,
        @JvmField val toolbar: Toolbar,
        @JvmField val drawer: ComposeView
    )

    private fun resolveActionBarSize(activity: Activity): Int {
        val typedValue = TypedValue()
        if (activity.theme.resolveAttribute(androidx.appcompat.R.attr.actionBarSize, typedValue, true)) {
            return TypedValue.complexToDimensionPixelSize(typedValue.data, activity.resources.displayMetrics)
        }
        return (56 * activity.resources.displayMetrics.density).toInt()
    }

    private fun resolveThemeColor(context: Context, attr: Int): Int {
        return MaterialColors.getColor(context, attr, 0)
    }

    private fun resolveDynamicThemeColor(context: Context, attr: Int, fallbackAttr: Int): Int {
        val dynamicContext = DynamicColors.wrapContextIfAvailable(context)
        val fallbackColor = resolveThemeColor(context, fallbackAttr)
        return MaterialColors.getColor(dynamicContext, attr, fallbackColor)
    }

    private fun resolveDynamicResourceColor(context: Context, attr: Int, fallbackColorRes: Int): Int {
        val dynamicContext = DynamicColors.wrapContextIfAvailable(context)
        val fallbackColor = androidx.core.content.ContextCompat.getColor(context, fallbackColorRes)
        return MaterialColors.getColor(dynamicContext, attr, fallbackColor)
    }
}
