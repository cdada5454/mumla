package se.lublin.mumla.util

import android.content.Context
import android.graphics.PorterDuff
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem

class TintedMenuInflater(
    context: Context,
    private val inflater: MenuInflater = MenuInflater(context)
) {
    private val tintColour: Int

    init {
        val actionBarThemeArray = context.obtainStyledAttributes(intArrayOf(androidx.appcompat.R.attr.actionBarStyle))
        val actionBarTheme = actionBarThemeArray.getResourceId(0, 0)
        actionBarThemeArray.recycle()

        val titleTextStyleArray = context.obtainStyledAttributes(actionBarTheme, intArrayOf(androidx.appcompat.R.attr.titleTextStyle))
        val titleTextStyle = titleTextStyleArray.getResourceId(0, 0)
        titleTextStyleArray.recycle()

        val textColorArray = context.obtainStyledAttributes(titleTextStyle, intArrayOf(android.R.attr.textColor))
        tintColour = textColorArray.getColor(0, 0)
        textColorArray.recycle()
    }

    fun inflate(menuRes: Int, menu: Menu) {
        inflater.inflate(menuRes, menu)
        for (index in 0 until menu.size()) {
            tintItem(menu.getItem(index))
        }
    }

    fun tintItem(item: MenuItem) {
        item.icon?.mutate()?.setColorFilter(tintColour, PorterDuff.Mode.MULTIPLY)
    }
}
