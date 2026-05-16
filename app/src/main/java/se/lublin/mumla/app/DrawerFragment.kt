package se.lublin.mumla.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment

class DrawerFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = inflater.context
        val provider = context as DrawerAdapter.DrawerDataProvider
        val listener = context as DrawerAdapter.DrawerSelectionListener
        return ComposeView(context).also { view ->
            DrawerAdapter(context, provider).apply {
                bind(view, listener)
            }
        }
    }
}
