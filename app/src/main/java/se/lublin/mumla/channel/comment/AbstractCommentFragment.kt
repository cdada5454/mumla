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

package se.lublin.mumla.channel.comment

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.webkit.WebView
import android.widget.EditText
import android.widget.TabHost
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import se.lublin.humla.IHumlaService
import se.lublin.mumla.R
import se.lublin.mumla.util.HumlaServiceProvider

abstract class AbstractCommentFragment : DialogFragment() {
    private var tabHost: TabHost? = null
    private var commentView: WebView? = null
    private var commentEdit: EditText? = null
    private lateinit var provider: HumlaServiceProvider
    private var comment: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        comment = arguments?.getString("comment")
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)

        provider = activity as? HumlaServiceProvider
            ?: throw RuntimeException("${activity.javaClass.name} must implement HumlaServiceProvider!")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_comment, null, false)

        commentView = view.findViewById(R.id.comment_view)
        commentEdit = view.findViewById(R.id.comment_edit)

        tabHost = view.findViewById<TabHost>(R.id.comment_tabhost).apply {
            setup()
        }

        val currentComment = comment
        if (currentComment == null) {
            commentView?.loadData("Loading...", null, null)
            provider.getService()?.let(::requestComment)
        } else {
            loadComment(currentComment)
        }

        val viewTab = tabHost?.newTabSpec("View")
        viewTab?.setIndicator(getString(R.string.comment_view))
        viewTab?.setContent(R.id.comment_tab_view)

        val editTab = tabHost?.newTabSpec("Edit")
        editTab?.setIndicator(getString(if (isEditing) R.string.comment_edit_source else R.string.comment_view_source))
        editTab?.setContent(R.id.comment_tab_edit)

        if (viewTab != null && editTab != null) {
            tabHost?.addTab(viewTab)
            tabHost?.addTab(editTab)
        }

        tabHost?.setOnTabChangedListener { tabId ->
            if ("View" == tabId) {
                commentView?.loadData(commentEdit?.text.toString(), "text/html", "UTF-8")
            } else if ("Edit" == tabId && commentEdit?.text.toString() == "") {
                commentEdit?.setText(comment)
            }
        }

        tabHost?.currentTab = if (isEditing) 1 else 0

        return if (isEditing) {
            MaterialAlertDialogBuilder(requireActivity())
                .setView(view)
                .setNegativeButton(R.string.close, null)
                .setPositiveButton(R.string.save) { _, _ ->
                    provider.getService()?.let { service ->
                        editComment(service, commentEdit?.text.toString())
                    }
                }
                .create()
        } else {
            MaterialAlertDialogBuilder(requireActivity())
                .setView(view)
                .setNegativeButton(R.string.close, null)
                .create()
        }
    }

    protected fun loadComment(comment: String) {
        val currentCommentView = commentView ?: return
        currentCommentView.loadData(comment, "text/html", "UTF-8")
        this.comment = comment
    }

    val isEditing: Boolean
        get() = arguments?.getBoolean("editing") == true

    abstract fun requestComment(service: IHumlaService)

    abstract fun editComment(service: IHumlaService, comment: String)
}
