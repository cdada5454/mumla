package se.lublin.mumla.app

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.widget.TextView
import androidx.core.text.HtmlCompat.fromHtml
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import se.lublin.mumla.BuildConfig
import se.lublin.mumla.R
import se.lublin.mumla.Settings

object DialogUtils {
    private val NEWS_ITEMS = linkedMapOf(
        "3.7.0" to R.string.app_news_items_v3_7_0,
        "3.7.1" to R.string.app_news_items_v3_7_1,
        "3.7.2" to R.string.app_news_items_v3_7_2,
        "3.7.3" to R.string.app_news_items_v3_7_3,
    )

    private val RELEVANT_VERSIONS = NEWS_ITEMS.keys
        .takeWhile { Version(it) <= Version(BuildConfig.VERSIONTAG) }

    @JvmStatic
    fun maybeShowNewsDialog(context: Context): Boolean {
        val shown = Settings.getInstance(context).newsShownVersions
        val toShow = RELEVANT_VERSIONS.filterNot(shown::contains)

        if (toShow.isEmpty()) {
            return false
        }

        showNewsDialogForVersions(context, toShow, true)
        return true
    }

    @JvmStatic
    fun showAllNewsDialog(context: Context) {
        showNewsDialogForVersions(context, RELEVANT_VERSIONS, false)
    }

    private fun showNewsDialogForVersions(context: Context, versions: List<String>, markShown: Boolean) {
        val newsHtml = buildString {
            for (version in versions.asReversed()) {
                val resId = NEWS_ITEMS[version] ?: continue
                append(String.format("<b>%s %s</b><br/>", context.getString(R.string.version), version))
                append(context.getString(resId).replace("\n", "<br/>"))
                append("<br/>")
            }

            if (markShown) {
                append(String.format("<em>%s</em>", context.getString(R.string.app_news_footer_on_startup)))
            }
        }

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_news, null)
        val newsTextView = dialogView.findViewById<TextView>(R.id.news_text_view)
        newsTextView.text = fromHtml(newsHtml, 0)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.app_news)
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                if (markShown) {
                    Settings.getInstance(context).addNewsShownVersions(versions)
                }
                dialog.dismiss()
            }
            .show()
    }

    private class Version(versionString: String) : Comparable<Version> {
        private val major: Int
        private val minor: Int
        private val patch: Int

        init {
            val parts = versionString.split("-")[0].split(".")
            var parsedMajor = 0
            var parsedMinor = 0
            var parsedPatch = 0
            try {
                if (parts.isNotEmpty()) parsedMajor = parts[0].toInt()
                if (parts.size > 1) parsedMinor = parts[1].toInt()
                if (parts.size > 2) parsedPatch = parts[2].toInt()
            } catch (exception: NumberFormatException) {
                Log.d("DialogUtils", "Failed to parse version string: $versionString", exception)
            }
            major = parsedMajor
            minor = parsedMinor
            patch = parsedPatch
        }

        override fun compareTo(other: Version): Int {
            if (major != other.major) {
                return major.compareTo(other.major)
            }
            if (minor != other.minor) {
                return minor.compareTo(other.minor)
            }
            return patch.compareTo(other.patch)
        }
    }
}
