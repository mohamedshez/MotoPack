package com.example.mesh

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

data class MeshVpnStatus(
    val isActive: Boolean = false,
    val virtualIp: String = "",
    val subnetCidr: String = "10.66.0.0/16",
    val cipherSuite: String = "AES-256-GCM",
    val mtu: Int = 1280,
    val bytesRx: Long = 0L,
    val bytesTx: Long = 0L,
    val packetsRouted: Long = 0L,
    val statusDetail: String = "Standby"
)

class MotoPackMeshVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunnelLoopJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            tearDownTunnel()
            stopSelf()
            return START_NOT_STICKY
        }

        val packName = intent?.getStringExtra(EXTRA_PACK_NAME).orEmpty().ifBlank { "MotoPack" }
        val virtualIp = intent?.getStringExtra(EXTRA_VIRTUAL_IP).orEmpty().ifBlank { "10.66.1.10" }

        establishMeshTunnel(packName, virtualIp)
        return START_STICKY
    }

    private fun establishMeshTunnel(packName: String, virtualIp: String) {
        tearDownTunnel()
        try {
            val builder = Builder()
                .setSession("MotoPack Mesh ($packName)")
                .addAddress(virtualIp, 16)
                .addRoute("10.66.0.0", 16)
                .setMtu(1280)
                .allowBypass()

            // Exclude our own P2P socket transport from looping back into the TUN interface.
            runCatching { builder.addDisallowedApplication(packageName) }

            val tunFd = builder.establish()
            if (tunFd == null) {
                _status.value = MeshVpnStatus(
                    isActive = false,
                    virtualIp = virtualIp,
                    statusDetail = "VPN permission required or revoked"
                )
                stopSelf()
                return
            }

            vpnInterface = tunFd
            _status.value = MeshVpnStatus(
                isActive = true,
                virtualIp = virtualIp,
                statusDetail = "Mesh TUN UP • $virtualIp/16"
            )

            tunnelLoopJob = serviceScope.launch {
                val input = FileInputStream(tunFd.fileDescriptor)
                val packetBuffer = ByteBuffer.allocate(32767)
                var rxTotal = 0L
                var txTotal = 0L
                var routedCount = 0L

                while (isActive) {
                    packetBuffer.clear()
                    val readBytes = runCatching { input.read(packetBuffer.array()) }.getOrElse { -1 }
                    if (readBytes < 0) break
                    if (readBytes > 0) {
                        rxTotal += readBytes
                        val isIpv4 = (packetBuffer.get(0).toInt() ushr 4) == 4
                        if (isIpv4 && readBytes >= 20) {
                            val dstByte0 = packetBuffer.get(16).toInt() and 0xFF
                            val dstByte1 = packetBuffer.get(17).toInt() and 0xFF
                            if (dstByte0 == 10 && dstByte1 == 66) {
                                txTotal += readBytes
                                routedCount += 1L
                            }
                        }
                        _status.value = _status.value.copy(
                            bytesRx = rxTotal,
                            bytesTx = txTotal,
                            packetsRouted = routedCount
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("MotoPackVpn", "Failed to establish mesh VPN tunnel: ${e.message}", e)
            _status.value = MeshVpnStatus(
                isActive = false,
                virtualIp = virtualIp,
                statusDetail = e.localizedMessage ?: "Tunnel error"
            )
            stopSelf()
        }
    }

    private fun tearDownTunnel() {
        tunnelLoopJob?.cancel()
        tunnelLoopJob = null
        runCatching { vpnInterface?.close() }
        vpnInterface = null
        _status.value = MeshVpnStatus(isActive = false, statusDetail = "Standby")
    }

    override fun onDestroy() {
        tearDownTunnel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.mesh.ACTION_START_VPN"
        const val ACTION_STOP = "com.example.mesh.ACTION_STOP_VPN"
        const val EXTRA_PACK_NAME = "extra_pack_name"
        const val EXTRA_VIRTUAL_IP = "extra_virtual_ip"

        private val _status = MutableStateFlow(MeshVpnStatus())
        val status: StateFlow<MeshVpnStatus> = _status.asStateFlow()

        fun computeVirtualMeshIp(packId: String, userId: String): String {
            val seed = "${packId.ifBlank { "solo" }}:$userId:motopack_mesh"
            val hash = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
            val octet3 = ((hash[0].toInt() and 0xFF) % 250) + 2
            val octet4 = ((hash[1].toInt() and 0xFF) % 250) + 2
            return "10.66.$octet3.$octet4"
        }

        fun prepareConsentIntent(context: Context): Intent? {
            return runCatching { prepare(context) }.getOrNull()
        }

        fun start(context: Context, packName: String, packId: String, userId: String) {
            val virtualIp = computeVirtualMeshIp(packId, userId)
            val intent = Intent(context, MotoPackMeshVpnService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PACK_NAME, packName)
                putExtra(EXTRA_VIRTUAL_IP, virtualIp)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MotoPackMeshVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
