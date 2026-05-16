package se.lublin.mumla.util

import android.text.TextUtils
import java.net.URI

object HtmlUtils {
    @JvmStatic
    fun getHostnameFromLink(link: String): String? {
        if (link.contains("://")) {
            try {
                val maybeUri = URI.create(link)
                if (!TextUtils.isEmpty(maybeUri.host)) {
                    return maybeUri.host
                }
            } catch (_: IllegalArgumentException) {
            }
        }
        return null
    }
}
