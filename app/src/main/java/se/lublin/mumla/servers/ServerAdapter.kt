package se.lublin.mumla.servers

import android.content.Context
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import se.lublin.humla.model.Server
import se.lublin.mumla.R
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

abstract class ServerAdapter<E : Server>(
    context: Context,
    servers: List<E>
) : ArrayAdapter<E>(context, 0, servers) {
    private val infoResponses = ConcurrentHashMap<Server, ServerInfoResponse>()
    private val pingExecutor = Executors.newFixedThreadPool(MAX_ACTIVE_PINGS)

    override fun getItemId(position: Int): Long {
        return getItem(position)?.id ?: 0L
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: ServerListLayout.createFavouriteServerRow(context)

        val server = getItem(position) ?: return view
        val infoResponse = infoResponses[server]
        val requestExists = infoResponse != null
        val requestFailure = infoResponse?.isDummy == true

        val nameText = view.findViewById<TextView>(R.id.server_row_name)
        val userText = view.findViewById<TextView?>(R.id.server_row_user)
        val addressText = view.findViewById<TextView?>(R.id.server_row_address)

        nameText.text = server.name
        userText?.text = server.username
        addressText?.text = server.host + if (server.port == 0) "" else ":${server.port}"

        view.findViewById<ImageView?>(R.id.server_row_more)?.let { moreButton ->
            moreButton.setOnClickListener { onServerOptionsClick(server, moreButton) }
        }

        val serverVersionText = view.findViewById<TextView>(R.id.server_row_version_status)
        val serverLatencyText = view.findViewById<TextView>(R.id.server_row_latency)
        val serverUsersText = view.findViewById<TextView>(R.id.server_row_usercount)
        val serverInfoProgressBar = view.findViewById<ProgressBar>(R.id.server_row_ping_progress)

        serverVersionText.visibility = if (!requestExists) View.INVISIBLE else View.VISIBLE
        serverUsersText.visibility = if (!requestExists) View.INVISIBLE else View.VISIBLE
        serverLatencyText.visibility = if (!requestExists) View.INVISIBLE else View.VISIBLE
        serverInfoProgressBar.visibility = if (!requestExists) View.VISIBLE else View.INVISIBLE

        if (infoResponse != null && !requestFailure) {
            serverVersionText.text = context.getString(R.string.online) + " (${infoResponse.versionString})"
            serverUsersText.text = "${infoResponse.currentUsers}/${infoResponse.maximumUsers}"
            serverLatencyText.text = "${infoResponse.latency}ms"
        } else if (requestFailure) {
            serverVersionText.setText(R.string.offline)
            serverUsersText.text = ""
            serverLatencyText.text = ""
        }

        if (infoResponse == null) {
            object : ServerInfoTask() {
                override fun onPostExecute(result: ServerInfoResponse?) {
                    super.onPostExecute(result)
                    if (result != null) {
                        infoResponses[server] = result
                    }
                    notifyDataSetChanged()
                }
            }.executeOnExecutor(pingExecutor, server)
        }

        return view
    }

    private fun onServerOptionsClick(server: Server, optionsButton: View) {
        PopupMenu(context, optionsButton).apply {
            inflate(popupMenuResource)
            setOnMenuItemClickListener { menuItem -> onPopupItemClick(server, menuItem) }
            show()
        }
    }

    abstract val popupMenuResource: Int

    abstract fun onPopupItemClick(server: Server, menuItem: MenuItem): Boolean

    companion object {
        private const val MAX_ACTIVE_PINGS = 50
    }
}
