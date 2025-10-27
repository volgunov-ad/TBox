package com.dashing.tbox

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import androidx.annotation.RequiresPermission

class IPManager(private val context: Context, initialIP: String = "192.168.225.1") {
    private val ipList = mutableListOf<String>()
    private var currentIndex = 0
    private var isFirstCall = true

    init {
        updateIPs(initialIP)
    }

    fun updateIPs(initialIP: String = "192.168.225.1") {
        synchronized(ipList) {
            val newList = mutableListOf<String>()

            // 1. Ранее использованный IP
            if (initialIP.isNotBlank() && isValidIP(initialIP)) {
                newList.add(initialIP)
            }

            // 2. Стандартный IP
            val defaultIP = "192.168.225.1"
            if (defaultIP !in newList && isValidIP(defaultIP)) {
                newList.add(defaultIP)
            }

            // 3. Шлюзы из текущих сетей
            getCurrentGatewayIPs().forEach { gatewayIP ->
                if (gatewayIP.isNotBlank() && gatewayIP !in newList && isValidIP(gatewayIP)) {
                    newList.add(gatewayIP)
                }
            }

            ipList.clear()
            ipList.addAll(newList)
            currentIndex = 0
            isFirstCall = true
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun getCurrentGatewayIPs(): List<String> {
        val gatewayIPs = mutableListOf<String>()

        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            connectivityManager?.allNetworks?.forEach { network ->
                val linkProperties = connectivityManager.getLinkProperties(network)
                linkProperties?.routes?.forEach { route ->
                    // Берем только default routes (шлюзы по умолчанию)
                    if (route.isDefaultRoute) {
                        route.gateway?.hostAddress?.let { gateway ->
                            if (isValidIP(gateway) && gateway !in gatewayIPs) {
                                gatewayIPs.add(gateway)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return gatewayIPs
    }

    fun getNextIP(): String? {
        synchronized(ipList) {
            if (ipList.isEmpty()) return null
            return if (isFirstCall) {
                isFirstCall = false
                ipList.first()
            } else {
                currentIndex = (currentIndex + 1) % ipList.size
                ipList[currentIndex]
            }
        }
    }

    fun isCurrentIPLast(): Boolean {
        return currentIndex + 1 == ipList.size
    }

    fun getIPList(): List<String> = ipList.toList()

    private fun isValidIP(ip: String): Boolean {
        return try {
            val parts = ip.split(".")
            parts.size == 4 && parts.all { part -> part.toIntOrNull() in 0..255 }
        } catch (e: Exception) {
            false
        }
    }
}