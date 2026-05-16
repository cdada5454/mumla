package se.lublin.mumla.servers

import android.content.Context
import android.view.MenuItem
import se.lublin.humla.model.Server
import se.lublin.mumla.R

class FavouriteServerAdapter(
    context: Context,
    servers: List<Server>,
    private val listener: FavouriteServerAdapterMenuListener
) : ServerAdapter<Server>(context, servers) {
    override val popupMenuResource: Int
        get() = R.menu.popup_favourite_server

    override fun onPopupItemClick(server: Server, menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.menu_server_edit -> {
                listener.editServer(server)
                true
            }
            R.id.menu_server_share -> {
                listener.shareServer(server)
                true
            }
            R.id.menu_server_delete -> {
                listener.deleteServer(server)
                true
            }
            else -> false
        }
    }

    interface FavouriteServerAdapterMenuListener {
        fun editServer(server: Server)
        fun shareServer(server: Server)
        fun deleteServer(server: Server)
    }
}
