package se.lublin.mumla.util

import android.app.Activity
import android.os.Bundle
import androidx.fragment.app.Fragment
import se.lublin.humla.IHumlaService
import se.lublin.humla.util.IHumlaObserver
import se.lublin.mumla.service.IMumlaService

abstract class HumlaServiceFragment : Fragment() {
    private lateinit var serviceProvider: HumlaServiceProvider
    private var bound = false

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        try {
            serviceProvider = activity as HumlaServiceProvider
        } catch (e: ClassCastException) {
            throw ClassCastException("$activity must implement HumlaServiceProvider")
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        serviceProvider.addServiceFragment(this)
        val service = serviceProvider.getService()
        if (service != null && !bound) {
            onServiceAttached(service)
        }
    }

    override fun onDestroy() {
        serviceProvider.removeServiceFragment(this)
        val service = serviceProvider.getService()
        if (service != null && bound) {
            onServiceDetached(service)
        }
        super.onDestroy()
    }

    open fun onServiceBound(service: IHumlaService?) = Unit

    open fun onServiceUnbound() = Unit

    open fun getServiceObserver(): IHumlaObserver? = null

    private fun onServiceAttached(service: IHumlaService) {
        bound = true
        getServiceObserver()?.let { service.registerObserver(it) }
        onServiceBound(service)
    }

    private fun onServiceDetached(service: IHumlaService) {
        bound = false
        getServiceObserver()?.let { service.unregisterObserver(it) }
        onServiceUnbound()
    }

    fun setServiceBound(bound: Boolean) {
        val service = serviceProvider.getService() ?: return
        if (bound && !this.bound) {
            onServiceAttached(service)
        } else if (this.bound && !bound) {
            onServiceDetached(service)
        }
    }

    val service: IMumlaService?
        get() = serviceProvider.getService()
}
