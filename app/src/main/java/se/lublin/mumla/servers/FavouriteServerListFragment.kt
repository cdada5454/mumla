package se.lublin.mumla.servers

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.GridView
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import se.lublin.humla.model.Server
import se.lublin.mumla.R
import se.lublin.mumla.db.DatabaseProvider

class FavouriteServerListFragment : Fragment(),
    AdapterView.OnItemClickListener,
    FavouriteServerAdapter.FavouriteServerAdapterMenuListener {

    private lateinit var connectHandler: ServerConnectHandler
    private lateinit var databaseProvider: DatabaseProvider
    private lateinit var serverGrid: GridView
    private lateinit var serverAdapter: ServerAdapter<Server>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        try {
            connectHandler = activity as ServerConnectHandler
            databaseProvider = activity as DatabaseProvider
        } catch (e: ClassCastException) {
            throw ClassCastException("$activity must implement ServerConnectHandler!")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = ServerListLayout.createFavouriteServerList(requireContext())
        serverGrid = view.findViewById(R.id.server_list_grid)
        serverGrid.onItemClickListener = this
        serverGrid.emptyView = view.findViewById(R.id.server_list_grid_empty)
        registerForContextMenu(serverGrid)
        return view
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.fragment_server_list, menu)
    }

    override fun onResume() {
        super.onResume()
        updateServers()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_add_server_item -> {
                addServer()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun addServer() {
        ServerEditFragment.createServerEditDialog(
            null,
            ServerEditFragment.Action.ADD_ACTION
        ).show(parentFragmentManager, "serverInfo")
    }

    override fun editServer(server: Server) {
        ServerEditFragment.createServerEditDialog(
            server,
            ServerEditFragment.Action.EDIT_ACTION
        ).show(parentFragmentManager, "serverInfo")
    }

    override fun shareServer(server: Server) {
        val serverUrl = "mumble://${server.host}${if (server.port == 0) "" else ":${server.port}"}/"
        startActivity(Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, getString(R.string.shareMessage, serverUrl))
            type = "text/plain"
        })
    }

    override fun deleteServer(server: Server) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.confirm_delete_server)
            .setPositiveButton(R.string.delete) { _, _ ->
                databaseProvider.database.removeServer(server)
                serverAdapter.remove(server)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun updateServers() {
        serverAdapter = FavouriteServerAdapter(requireActivity(), servers, this)
        serverGrid.adapter = serverAdapter
    }

    val servers: List<Server>
        get() = databaseProvider.database.getServers()

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        serverAdapter.getItem(position)?.let { connectHandler.connectToServer(it) }
    }

    interface ServerConnectHandler {
        fun connectToServer(server: Server)
    }
}
