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

package se.lublin.mumla.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.StrictMode
import android.text.Html
import android.util.Base64
import android.util.Log
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.MalformedURLException
import java.net.URL
import java.net.URLDecoder
import se.lublin.mumla.Settings

class MumbleImageGetter(private val context: Context) : Html.ImageGetter {
    private val settings = Settings.getInstance(context)
    private val bitmapCache = mutableMapOf<String, Drawable>()

    init {
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
    }

    override fun getDrawable(source: String): Drawable? {
        bitmapCache[source]?.let { return it }

        val decodedSource = try {
            URLDecoder.decode(source, "UTF-8")
        } catch (exception: UnsupportedEncodingException) {
            exception.printStackTrace()
            return null
        }

        val bitmap = try {
            if (decodedSource.startsWith("data:image")) {
                getBase64Image(decodedSource.split(",")[1])
            } else if (settings.shouldLoadExternalImages()) {
                getURLImage(decodedSource)
            } else {
                null
            }
        } catch (exception: IllegalArgumentException) {
            Log.w(TAG, "exception when decoding data:image: $exception")
            return null
        } catch (exception: ArrayIndexOutOfBoundsException) {
            Log.w(TAG, "exception when decoding data:image: $exception")
            return null
        }
        if (bitmap == null) {
            return null
        }

        val drawable = BitmapDrawable(context.resources, bitmap)
        val metrics = context.resources.displayMetrics
        drawable.setBounds(
            0,
            0,
            (drawable.intrinsicWidth * metrics.density).toInt(),
            (drawable.intrinsicHeight * metrics.density).toInt(),
        )
        bitmapCache[source] = drawable
        return drawable
    }

    @Throws(IllegalArgumentException::class)
    private fun getBase64Image(base64: String): Bitmap {
        val source = Base64.decode(base64, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(source, 0, source.size)
    }

    private fun getURLImage(source: String): Bitmap? {
        return try {
            val connection = URL(source).openConnection()
            if (connection.contentLength > MAX_LENGTH) {
                return null
            }
            BitmapFactory.decodeStream(connection.getInputStream())
        } catch (exception: MalformedURLException) {
            exception.printStackTrace()
            null
        } catch (exception: IOException) {
            exception.printStackTrace()
            null
        }
    }

    companion object {
        private val TAG = MumbleImageGetter::class.java.name
        private const val MAX_LENGTH = 64000
    }
}
