package se.lublin.mumla.util

import se.lublin.mumla.service.IMumlaService

interface HumlaServiceProvider {
    fun getService(): IMumlaService?
    fun addServiceFragment(fragment: HumlaServiceFragment)
    fun removeServiceFragment(fragment: HumlaServiceFragment)
}
