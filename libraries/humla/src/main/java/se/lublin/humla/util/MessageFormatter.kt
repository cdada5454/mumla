/*
 * Copyright (C) 2014 Andrew Comminos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package se.lublin.humla.util

object MessageFormatter {
    const val HIGHLIGHT_COLOR = "33b5e5"
    private const val HTML_FONT_COLOR_FORMAT = "<font color=\"#%s\">%s</font>"

    @JvmStatic
    fun highlightString(string: String): String =
        String.format(HTML_FONT_COLOR_FORMAT, HIGHLIGHT_COLOR, string)
}
