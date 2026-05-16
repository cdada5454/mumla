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

package se.lublin.mumla.channel

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AbsListView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import se.lublin.mumla.R
import se.lublin.mumla.db.DatabaseProvider
import se.lublin.mumla.util.HumlaServiceFragment

class AccessTokenFragment : HumlaServiceFragment() {
    private lateinit var tokens: MutableList<String>
    private lateinit var tokenList: ListView
    private lateinit var tokenAdapter: TokenAdapter
    private lateinit var tokenField: EditText
    private lateinit var provider: DatabaseProvider

    override fun onAttach(context: Context) {
        super.onAttach(context)

        tokens = ArrayList(getAccessTokens())
        tokenAdapter = TokenAdapter(context, tokens)
        provider = context as? DatabaseProvider
            ?: throw ClassCastException("$context must implement DatabaseProvider")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val density = resources.displayMetrics.density
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            tokenList = ListView(context).apply {
                adapter = tokenAdapter
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }
            addView(tokenList)

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

                tokenField = EditText(context).apply {
                    inputType = android.text.InputType.TYPE_CLASS_TEXT
                    imeOptions = EditorInfo.IME_ACTION_SEND
                    setHint(R.string.accessAdd)
                    setOnEditorActionListener { _: TextView?, actionId: Int, _: KeyEvent? ->
                        if (actionId == EditorInfo.IME_ACTION_SEND) {
                            addToken()
                            true
                        } else {
                            false
                        }
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1f
                    )
                }
                addView(tokenField)

                addView(ImageButton(context).apply {
                    setImageResource(R.drawable.ic_action_add_dark)
                    setOnClickListener { addToken() }
                    layoutParams = LinearLayout.LayoutParams(
                        (64 * density).toInt(),
                        (48 * density).toInt()
                    )
                })
            })
        }
    }

    private fun addToken() {
        val tokenText = tokenField.text.toString().trim()
        if (tokenText == "") {
            return
        }

        tokenField.setText("")
        Log.i(TAG, "Adding a token")

        tokens.add(tokenText)
        tokenAdapter.notifyDataSetChanged()

        tokenList.smoothScrollToPosition(tokens.size - 1)
        provider.database.addAccessToken(serverId, tokenText)
        val currentService = service
        if (currentService != null && currentService.isConnected) {
            currentService.HumlaSession().sendAccessTokens(tokens)
        }
    }

    private val serverId: Long
        get() = requireArguments().getLong("server")

    private fun getAccessTokens(): List<String> {
        return requireArguments().getStringArrayList("access_tokens") ?: emptyList()
    }

    private inner class TokenAdapter(
        context: Context,
        objects: List<String>
    ) : ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, objects) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView as? LinearLayout ?: createTokenRow(parent.context)
            val token = getItem(position) ?: ""

            val title = row.getChildAt(0) as TextView
            title.text = token

            val deleteButton = row.getChildAt(1) as ImageButton
            deleteButton.setOnClickListener {
                tokens.removeAt(position)
                notifyDataSetChanged()
                provider.database.removeAccessToken(serverId, token)
                val currentService = service
                if (currentService != null && currentService.isConnected) {
                    currentService.HumlaSession().sendAccessTokens(tokens)
                }
            }

            return row
        }

        private fun createTokenRow(context: Context): LinearLayout {
            val density = context.resources.displayMetrics.density
            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

                addView(TextView(context).apply {
                    textSize = 18f
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        (48 * density).toInt(),
                        1f
                    ).apply {
                        marginStart = (10 * density).toInt()
                        marginEnd = (10 * density).toInt()
                    }
                })

                addView(ImageButton(context).apply {
                    setImageResource(R.drawable.ic_action_delete_dark)
                    layoutParams = LinearLayout.LayoutParams(
                        (64 * density).toInt(),
                        (48 * density).toInt()
                    )
                })
            }
        }
    }

    companion object {
        private val TAG = AccessTokenFragment::class.java.name
    }
}
