package com.elad.codusb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Finds the ports of this device's own wireless-debugging services via mDNS. */
class AdbMdns(context: Context, private val type: String, private val onPort: (Int) -> Unit) {

    private val nsd = context.getSystemService(NsdManager::class.java)
    private val exec = Executors.newSingleThreadExecutor()
    @Volatile
    private var running = false

    private val listener = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
            running = false
        }

        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
        override fun onDiscoveryStarted(serviceType: String?) {}
        override fun onDiscoveryStopped(serviceType: String?) {}
        override fun onServiceLost(serviceInfo: NsdServiceInfo?) {}

        override fun onServiceFound(info: NsdServiceInfo) {
            if (!exec.isShutdown) exec.execute { resolve(info) }
        }
    }

    fun start() {
        running = true
        nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stop() {
        if (running) {
            running = false
            try {
                nsd.stopServiceDiscovery(listener)
            } catch (_: Throwable) {
            }
        }
        exec.shutdownNow()
    }

    @Suppress("DEPRECATION")
    private fun resolve(info: NsdServiceInfo) {
        val latch = CountDownLatch(1)
        try {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    latch.countDown()
                }

                override fun onServiceResolved(r: NsdServiceInfo) {
                    if (isLocal(r.host) && r.port > 0) {
                        lastHost = r.host?.hostAddress
                        onPort(r.port)
                    }
                    latch.countDown()
                }
            })
            latch.await(6, TimeUnit.SECONDS)
        } catch (_: Throwable) {
        }
    }

    companion object {
        const val PAIRING = "_adb-tls-pairing._tcp"
        const val CONNECT = "_adb-tls-connect._tcp"

        /** Address of the last service found (this device's Wi-Fi address). */
        @Volatile
        var lastHost: String? = null

        fun isLocal(host: InetAddress?): Boolean {
            if (host == null) return true
            if (host.isLoopbackAddress) return true
            return try {
                NetworkInterface.getByInetAddress(host) != null
            } catch (_: Throwable) {
                false
            }
        }

        /** Blocks until a port is found or the timeout passes. */
        fun findPort(context: Context, type: String, timeoutMs: Long): Int? {
            val latch = CountDownLatch(1)
            var port: Int? = null
            val m = AdbMdns(context, type) {
                if (port == null) {
                    port = it
                    latch.countDown()
                }
            }
            try {
                m.start()
                latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (_: Throwable) {
            } finally {
                m.stop()
            }
            return port
        }
    }
}
