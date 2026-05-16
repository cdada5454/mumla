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

import java.net.MalformedURLException
import java.util.regex.Pattern
import se.lublin.humla.Constants
import se.lublin.humla.model.Server

object MumbleURLParser {
    private val URL_PATTERN: Pattern = Pattern.compile("mumble://(([^:]+)?(:(.+?))?@)?(.+?)(:([0-9]+?))?/")

    @JvmStatic
    @Throws(MalformedURLException::class)
    fun parseURL(url: String): Server {
        val matcher = URL_PATTERN.matcher(url)
        if (matcher.find()) {
            val username = matcher.group(2)
            val password = matcher.group(4)
            val host = matcher.group(5)
            val portString = matcher.group(7)
            val port = portString?.toInt() ?: Constants.DEFAULT_PORT
            return Server(-1, null, host, port, username, password)
        }
        throw MalformedURLException()
    }
}
