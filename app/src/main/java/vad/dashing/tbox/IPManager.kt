package vad.dashing.tbox

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import androidx.annotation.RequiresPermission

class IPManager(private val context: Context, initialIP: String = "192.168.225.1") {
    private val ipList = mutableListOf<String>()
    private var currentIndex = 0
    private var isFirstCall = true

    companion object {
        private const val TAG = "IPManager"
        private const val DEFAULT_IP = "192.168.225.1"
    }

    init {
        updateIPs(initialIP)
    }

    fun updateIPs(initialIP: String = DEFAULT_IP) {
        synchronized(ipList) {
            val newList = mutableListOf<String>()

            // 1. Ранее использованный IP (initialIP) - должен быть первым в списке
            if (initialIP.isNotBlank() && isValidIP(initialIP)) {
                newList.add(initialIP)
            }

            // 2. Обязательный IP 192.168.225.1 (если его еще нет в списке)
            if (DEFAULT_IP !in newList && isValidIP(DEFAULT_IP)) {
                newList.add(DEFAULT_IP)
            }

            // 3. Шлюзы из текущих сетей (только если есть разрешения)
            try {
                if (hasNetworkStatePermission()) {
                    getCurrentGatewayIPs().forEach { gatewayIP ->
                        if (gatewayIP.isNotBlank() && gatewayIP !in newList && isValidIP(gatewayIP)) {
                            newList.add(gatewayIP)
                        }
                    }
                } else {
                    // Если нет разрешений - оставляем только initialIP и DEFAULT_IP
                    android.util.Log.d(TAG, "No network state permission, using only initial and default IPs")
                    // Удаляем все кроме initialIP и DEFAULT_IP
                    val allowedIPs = listOf(initialIP, DEFAULT_IP).filter { isValidIP(it) }
                    newList.removeAll { it !in allowedIPs }
                }
            } catch (e: SecurityException) {
                // Если возникла SecurityException - оставляем только initialIP и DEFAULT_IP
                android.util.Log.w(TAG, "Security exception, using only initial and default IPs")
                val allowedIPs = listOf(initialIP, DEFAULT_IP).filter { isValidIP(it) }
                newList.removeAll { it !in allowedIPs }
            }

            ipList.clear()
            ipList.addAll(newList.distinct()) // Убираем дубликаты
            currentIndex = 0
            isFirstCall = true

            android.util.Log.d(TAG, "Updated IP list: $ipList")
        }
    }

    private fun hasNetworkStatePermission(): Boolean {
        return context.checkSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun getCurrentGatewayIPs(): List<String> {
        val gatewayIPs = mutableListOf<String>()

        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return emptyList()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Для Android 6.0+ используем активную сеть
                val activeNetwork = connectivityManager.activeNetwork
                activeNetwork?.let { network ->
                    val linkProperties = connectivityManager.getLinkProperties(network)
                    linkProperties?.routes?.forEach { route ->
                        if (route.isDefaultRoute) {
                            route.gateway?.hostAddress?.let { gateway ->
                                if (isValidIP(gateway) && gateway !in gatewayIPs) {
                                    gatewayIPs.add(gateway)
                                }
                            }
                        }
                    }
                }
            } else {
                // Для старых версий (устаревший метод)
                @Suppress("DEPRECATION")
                (connectivityManager.allNetworks.forEach { network ->
                    val linkProperties = connectivityManager.getLinkProperties(network)
                    linkProperties?.routes?.forEach { route ->
                        if (route.isDefaultRoute) {
                            route.gateway?.hostAddress?.let { gateway ->
                                if (isValidIP(gateway) && gateway !in gatewayIPs) {
                                    gatewayIPs.add(gateway)
                                }
                            }
                        }
                    }
                })
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error getting gateway IPs", e)
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

    fun reset() {
        synchronized(ipList) {
            currentIndex = 0
            isFirstCall = true
        }
    }

    fun isCurrentIPLast(): Boolean {
        return currentIndex + 1 == ipList.size
    }

    fun getIPList(): List<String> = ipList.toList()

    fun getCurrentIP(): String? {
        synchronized(ipList) {
            return if (ipList.isEmpty()) null else ipList[currentIndex]
        }
    }

    private fun isValidIP(ip: String): Boolean {
        return try {
            val parts = ip.split(".")
            parts.size == 4 && parts.all { part ->
                part.toIntOrNull() in 0..255 && part.isNotBlank()
            }
        } catch (e: Exception) {
            false
        }
    }
}