package com.bagofholding.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DiscoveredHost(val serviceName: String, val displayName: String, val address: HostAddress)

/** Advertises this phone's share server and finds other phones on the same network. */
interface ServiceDiscovery {
    val hosts: StateFlow<List<DiscoveredHost>>
    fun register(displayName: String, port: Int)
    fun unregister()
    fun startDiscovery()
    fun stopDiscovery()
}

/**
 * [ServiceDiscovery] based on Android Network Service Discovery (mDNS / DNS-SD).
 * Uses only the application context, so it cannot leak an Activity.
 * NsdManager callbacks arrive on a background thread; state is exposed through a thread-safe StateFlow.
 */
class NsdServiceDiscovery(context: Context) : ServiceDiscovery {

    private val nsdManager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val discovered = MutableStateFlow<List<DiscoveredHost>>(emptyList())
    override val hosts: StateFlow<List<DiscoveredHost>> = discovered.asStateFlow()

    @Volatile
    private var registrationListener: NsdManager.RegistrationListener? = null

    @Volatile
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    // NsdManager.resolveService() handles one request at a time on older Android versions.
    private val lock = Any()
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    override fun register(displayName: String, port: Int) {
        unregister()
        val info = NsdServiceInfo()
        info.serviceName = SERVICE_PREFIX + displayName.take(MAX_NAME_LENGTH)
        info.serviceType = SERVICE_TYPE
        info.port = port
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                registrationListener = null
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registrationListener = listener
        nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    override fun unregister() {
        val listener = registrationListener ?: return
        registrationListener = null
        try {
            nsdManager.unregisterService(listener)
        } catch (ignored: IllegalArgumentException) {
            // Listener was never registered successfully.
        }
    }

    override fun startDiscovery() {
        if (discoveryListener != null) return
        discovered.value = emptyList()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                discoveryListener = null
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.contains(SERVICE_TYPE_BARE)) enqueueResolve(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                discovered.update { list -> list.filterNot { it.serviceName == serviceInfo.serviceName } }
            }
        }
        discoveryListener = listener
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    override fun stopDiscovery() {
        val listener = discoveryListener ?: return
        discoveryListener = null
        synchronized(lock) { resolveQueue.clear() }
        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (ignored: IllegalArgumentException) {
            // Discovery had already stopped or failed to start.
        }
    }

    private fun enqueueResolve(info: NsdServiceInfo) {
        synchronized(lock) {
            resolveQueue.addLast(info)
            if (resolving) return
            resolving = true
        }
        resolveNext()
    }

    // resolveService() and NsdServiceInfo.host are deprecated on API 34+, but their
    // replacements do not exist on API 24, the minimum SDK of this app.
    @Suppress("DEPRECATION")
    private fun resolveNext() {
        val next = synchronized(lock) {
            val item = resolveQueue.removeFirstOrNull()
            if (item == null) resolving = false
            item
        } ?: return

        nsdManager.resolveService(
            next,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    resolveNext()
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val hostAddress = serviceInfo.host?.hostAddress
                    if (hostAddress != null) {
                        val host = DiscoveredHost(
                            serviceName = serviceInfo.serviceName,
                            displayName = serviceInfo.serviceName.removePrefix(SERVICE_PREFIX),
                            address = HostAddress(hostAddress, serviceInfo.port),
                        )
                        discovered.update { list -> list.filterNot { it.serviceName == host.serviceName } + host }
                    }
                    resolveNext()
                }
            },
        )
    }

    private companion object {
        const val SERVICE_TYPE = "_bagofholding._tcp."
        const val SERVICE_TYPE_BARE = "_bagofholding._tcp"
        const val SERVICE_PREFIX = "BoH-"
        const val MAX_NAME_LENGTH = 40
    }
}
