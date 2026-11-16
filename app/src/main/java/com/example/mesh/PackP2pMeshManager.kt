package com.example.mesh

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.crypto.PackCrypto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKey

@Serializable
data class MeshWireEnvelope(
    val packetId: String,
    val originUserId: String,
    val packId: String,
    val kind: String, // "TELEMETRY", "CHAT", "PTT_VOICE", "SOS"
    val ttl: Int = 3,
    val encryptedPayload: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Serializable
data class MeshTelemetryBody(
    val userId: String,
    val displayName: String,
    val photoUrl: String = "",
    val bikeModel: String = "Adventure Tourer",
    val phoneNumber: String = "",
    val lat: Double,
    val lng: Double,
    val speedKmh: Double = 0.0,
    val headingDeg: Float = 0f,
    val altitudeM: Double = 0.0,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val totalDistanceKm: Double = 0.0,
    val elevationClimbedM: Double = 0.0,
    val meshVirtualIp: String = "",
    val timestampMs: Long = System.currentTimeMillis()
)

@Serializable
data class MeshMessageBody(
    val messageId: String,
    val senderId: String,
    val senderName: String,
    val recipientId: String = "",
    val text: String = "",
    val messageType: String = "text", // "text", "voice", "alert"
    val audioBase64: String = "",
    val audioCodec: String = "",
    val audioDurationMs: Int = 0,
    val audioSizeBytes: Int = 0,
    val audioPeaks: String = "",
    val timestampMs: Long = System.currentTimeMillis()
)

data class DiscoveredP2pPeer(
    val deviceName: String,
    val deviceAddress: String,
    val isGroupOwner: Boolean = false,
    val statusLabel: String = "Available"
)

data class MeshPeerEndpoint(
    val serviceName: String,
    val hostAddress: String,
    val port: Int,
    val lastSeenMs: Long = System.currentTimeMillis()
)

data class P2pMeshUiState(
    val isActive: Boolean = false,
    val isDiscovering: Boolean = false,
    val hasNearbyWifiPermission: Boolean = false,
    val localTcpPort: Int = 0,
    val virtualMeshIp: String = "",
    val wifiDirectPeers: List<DiscoveredP2pPeer> = emptyList(),
    val activeSocketPeers: List<MeshPeerEndpoint> = emptyList(),
    val packetsSent: Int = 0,
    val packetsRelayed: Int = 0,
    val packetsReceived: Int = 0,
    val lastActivityMs: Long? = null,
    val transportSummary: String = "Mesh standby"
)

sealed interface MeshIncomingEvent {
    data class Telemetry(val body: MeshTelemetryBody, val viaHopCount: Int) : MeshIncomingEvent
    data class Message(val body: MeshMessageBody, val viaHopCount: Int) : MeshIncomingEvent
}

class PackP2pMeshManager(private val context: Context?) {

    private val appContext = context?.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _uiState = MutableStateFlow(P2pMeshUiState())
    val uiState: StateFlow<P2pMeshUiState> = _uiState.asStateFlow()

    private val _incomingEvents = MutableSharedFlow<MeshIncomingEvent>(extraBufferCapacity = 32)
    val incomingEvents: SharedFlow<MeshIncomingEvent> = _incomingEvents.asSharedFlow()

    private val knownEndpoints = ConcurrentHashMap<String, MeshPeerEndpoint>()
    private val seenPacketIds = Collections.synchronizedSet(LinkedHashSet<String>())

    private var activePackId: String = ""
    private var activePackKey: SecretKey? = null
    private var activeUserId: String = ""

    private var serverSocket: ServerSocket? = null
    private var udpSocket: DatagramSocket? = null
    private var tcpAcceptJob: Job? = null
    private var udpListenJob: Job? = null

    private var wifiP2pManager: WifiP2pManager? = null
    private var wifiP2pChannel: WifiP2pManager.Channel? = null
    private var wifiP2pReceiver: BroadcastReceiver? = null
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun hasRequiredP2pPermissions(): Boolean {
        val ctx = appContext ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun startMesh(
        packId: String,
        inviteCode: String,
        encryptionSalt: String,
        userId: String
    ) {
        val isSamePack = _uiState.value.isActive && activePackId == packId && activeUserId == userId
        activePackId = packId
        activeUserId = userId
        activePackKey = PackCrypto.derivePackKey(inviteCode, encryptionSalt)
        val virtualIp = MotoPackMeshVpnService.computeVirtualMeshIp(packId, userId)
        val hasPerm = hasRequiredP2pPermissions()

        if (isSamePack) {
            _uiState.value = _uiState.value.copy(
                hasNearbyWifiPermission = hasPerm,
                virtualMeshIp = virtualIp
            )
            return
        }

        stopSocketsAndDiscovery()

        val boundPort = startTcpAndUdpMeshServers()
        _uiState.value = P2pMeshUiState(
            isActive = true,
            isDiscovering = true,
            hasNearbyWifiPermission = hasPerm,
            localTcpPort = boundPort,
            virtualMeshIp = virtualIp,
            transportSummary = if (hasPerm) {
                "Wi-Fi Direct + Mesh UDP/TCP active on :$boundPort"
            } else {
                "LAN/Hotspot Mesh active on :$boundPort (grant Nearby Wi-Fi for P2P Direct)"
            }
        )

        val ctx = appContext ?: return
        startNsdAdvertisementAndDiscovery(ctx, boundPort, userId)
        if (hasPerm) {
            startWifiDirectDiscovery(ctx)
        }
    }

    fun stopMesh() {
        stopSocketsAndDiscovery()
        activePackKey = null
        _uiState.value = P2pMeshUiState(isActive = false, transportSummary = "Mesh standby")
    }

    fun addManualMeshPeer(hostAddress: String, port: Int = DEFAULT_MESH_PORT) {
        val cleanHost = hostAddress.trim()
        if (cleanHost.isBlank()) return
        registerEndpoint(
            MeshPeerEndpoint(
                serviceName = "Manual-$cleanHost",
                hostAddress = cleanHost,
                port = port
            )
        )
    }

    @SuppressLint("MissingPermission")
    fun connectToWifiDirectPeer(deviceAddress: String) {
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return
        if (!hasRequiredP2pPermissions()) return
        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
        }
        runCatching {
            manager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    _uiState.value = _uiState.value.copy(
                        transportSummary = "Connecting Wi-Fi Direct peer $deviceAddress..."
                    )
                }

                override fun onFailure(reason: Int) {
                    _uiState.value = _uiState.value.copy(
                        transportSummary = "Wi-Fi Direct connect pending (code $reason)"
                    )
                }
            })
        }
    }

    fun broadcastTelemetry(body: MeshTelemetryBody) {
        val key = activePackKey ?: return
        if (!_uiState.value.isActive) return
        val plain = json.encodeToString(body)
        val encrypted = PackCrypto.encryptString(plain, key)
        val envelope = MeshWireEnvelope(
            packetId = UUID.randomUUID().toString(),
            originUserId = activeUserId,
            packId = activePackId,
            kind = KIND_TELEMETRY,
            ttl = 3,
            encryptedPayload = encrypted
        )
        sendEnvelopeToMesh(envelope, excludeHost = null)
    }

    fun broadcastMessage(body: MeshMessageBody) {
        val key = activePackKey ?: return
        if (!_uiState.value.isActive) return
        val plain = json.encodeToString(body)
        val encrypted = PackCrypto.encryptString(plain, key)
        val kind = if (body.messageType == "voice") KIND_PTT_VOICE else KIND_CHAT
        val envelope = MeshWireEnvelope(
            packetId = UUID.randomUUID().toString(),
            originUserId = activeUserId,
            packId = activePackId,
            kind = kind,
            ttl = 3,
            encryptedPayload = encrypted
        )
        sendEnvelopeToMesh(envelope, excludeHost = null)
    }

    private fun startTcpAndUdpMeshServers(): Int {
        val tcp = runCatching { ServerSocket(DEFAULT_MESH_PORT) }
            .getOrElse { runCatching { ServerSocket(0) }.getOrNull() }
        serverSocket = tcp
        val port = tcp?.localPort ?: DEFAULT_MESH_PORT

        if (tcp != null) {
            tcpAcceptJob = scope.launch {
                while (isActive && !tcp.isClosed) {
                    val socket = runCatching { tcp.accept() }.getOrNull() ?: break
                    launch { handleIncomingTcpSocket(socket) }
                }
            }
        }

        val udp = runCatching {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(port))
            }
        }.getOrNull()
        udpSocket = udp

        if (udp != null) {
            udpListenJob = scope.launch {
                val buf = ByteArray(65507)
                while (isActive && !udp.isClosed) {
                    val packet = DatagramPacket(buf, buf.size)
                    val ok = runCatching { udp.receive(packet) }.isSuccess
                    if (!ok) break
                    val senderHost = packet.address?.hostAddress.orEmpty()
                    val raw = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    if (raw.isNotBlank()) {
                        handleWireJson(raw, senderHost)
                    }
                }
            }
        }
        return port
    }

    private fun handleIncomingTcpSocket(socket: Socket) {
        socket.use { s ->
            val senderHost = s.inetAddress?.hostAddress.orEmpty()
            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
            val line = reader.readLine()?.trim().orEmpty()
            if (line.isNotBlank()) {
                handleWireJson(line, senderHost)
            }
        }
    }

    fun processIncomingWireJsonForTest(rawJson: String, sourceHost: String = "127.0.0.1"): Boolean {
        return handleWireJson(rawJson, sourceHost)
    }

    private fun handleWireJson(rawJson: String, sourceHost: String): Boolean {
        val envelope = runCatching { json.decodeFromString<MeshWireEnvelope>(rawJson) }.getOrNull()
            ?: return false
        if (envelope.originUserId == activeUserId) return false
        if (envelope.packId != activePackId) return false

        synchronized(seenPacketIds) {
            if (seenPacketIds.contains(envelope.packetId)) return false
            if (seenPacketIds.size > 500) {
                val first = seenPacketIds.firstOrNull()
                if (first != null) seenPacketIds.remove(first)
            }
            seenPacketIds.add(envelope.packetId)
        }

        val key = activePackKey ?: return false
        val plain = PackCrypto.decryptString(envelope.encryptedPayload, key) ?: return false
        val hops = (3 - envelope.ttl).coerceAtLeast(1)

        if (sourceHost.isNotBlank() && sourceHost != "127.0.0.1") {
            registerEndpoint(
                MeshPeerEndpoint(
                    serviceName = "Mesh-${envelope.originUserId.take(6)}",
                    hostAddress = sourceHost,
                    port = DEFAULT_MESH_PORT
                )
            )
        }

        when (envelope.kind) {
            KIND_TELEMETRY -> {
                val body = runCatching { json.decodeFromString<MeshTelemetryBody>(plain) }.getOrNull()
                    ?: return false
                _incomingEvents.tryEmit(MeshIncomingEvent.Telemetry(body, hops))
            }
            KIND_CHAT, KIND_PTT_VOICE, KIND_SOS -> {
                val body = runCatching { json.decodeFromString<MeshMessageBody>(plain) }.getOrNull()
                    ?: return false
                _incomingEvents.tryEmit(MeshIncomingEvent.Message(body, hops))
            }
            else -> return false
        }

        _uiState.value = _uiState.value.copy(
            packetsReceived = _uiState.value.packetsReceived + 1,
            lastActivityMs = System.currentTimeMillis()
        )

        // Multi-hop mesh relay if TTL > 1
        if (envelope.ttl > 1) {
            val relayed = envelope.copy(ttl = envelope.ttl - 1)
            sendEnvelopeToMesh(relayed, excludeHost = sourceHost, isRelay = true)
        }
        return true
    }

    private fun sendEnvelopeToMesh(
        envelope: MeshWireEnvelope,
        excludeHost: String?,
        isRelay: Boolean = false
    ) {
        synchronized(seenPacketIds) {
            seenPacketIds.add(envelope.packetId)
        }
        val wire = json.encodeToString(envelope)
        val wireBytes = wire.toByteArray(Charsets.UTF_8)
        val targets = knownEndpoints.values
            .filter { it.hostAddress.isNotBlank() && it.hostAddress != excludeHost }

        scope.launch {
            targets.forEach { peer ->
                runCatching {
                    Socket().use { s ->
                        s.connect(InetSocketAddress(peer.hostAddress, peer.port), 1200)
                        val writer = PrintWriter(s.getOutputStream(), true)
                        writer.println(wire)
                    }
                }
            }

            // Local subnet / Wi-Fi Direct group owner UDP broadcast fallback
            if (wireBytes.size < 60_000) {
                val port = _uiState.value.localTcpPort.takeIf { it > 0 } ?: DEFAULT_MESH_PORT
                listOf("255.255.255.255", "192.168.49.255").forEach { broadcastAddr ->
                    runCatching {
                        val addr = InetAddress.getByName(broadcastAddr)
                        val packet = DatagramPacket(wireBytes, wireBytes.size, addr, port)
                        udpSocket?.send(packet)
                    }
                }
            }

            _uiState.value = _uiState.value.copy(
                packetsSent = if (isRelay) _uiState.value.packetsSent else _uiState.value.packetsSent + 1,
                packetsRelayed = if (isRelay) _uiState.value.packetsRelayed + 1 else _uiState.value.packetsRelayed,
                lastActivityMs = System.currentTimeMillis()
            )
        }
    }

    private fun registerEndpoint(endpoint: MeshPeerEndpoint) {
        knownEndpoints[endpoint.hostAddress] = endpoint
        _uiState.value = _uiState.value.copy(
            activeSocketPeers = knownEndpoints.values.sortedByDescending { it.lastSeenMs }
        )
    }

    private fun startNsdAdvertisementAndDiscovery(ctx: Context, port: Int, userId: String) {
        val manager = ctx.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        nsdManager = manager

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "MotoPack_${userId.take(6)}"
            serviceType = NSD_SERVICE_TYPE
            setPort(port)
        }

        val regListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.d("PackP2pMesh", "NSD registered: ${info.serviceName}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = regListener
        runCatching {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, regListener)
        }

        val discListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains("_motopack") && !service.serviceName.endsWith(userId.take(6))) {
                    runCatching {
                        manager.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                            override fun onServiceResolved(resolved: NsdServiceInfo) {
                                val host = resolved.host?.hostAddress ?: return
                                registerEndpoint(
                                    MeshPeerEndpoint(
                                        serviceName = resolved.serviceName,
                                        hostAddress = host,
                                        port = resolved.port
                                    )
                                )
                            }
                        })
                    }
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                val toRemove = knownEndpoints.values.firstOrNull { it.serviceName == service.serviceName }
                if (toRemove != null) {
                    knownEndpoints.remove(toRemove.hostAddress)
                    _uiState.value = _uiState.value.copy(
                        activeSocketPeers = knownEndpoints.values.toList()
                    )
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        discoveryListener = discListener
        runCatching {
            manager.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discListener)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startWifiDirectDiscovery(ctx: Context) {
        val manager = ctx.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager ?: return
        val channel = manager.initialize(ctx, Looper.getMainLooper(), null) ?: return
        wifiP2pManager = manager
        wifiP2pChannel = channel

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        if (hasRequiredP2pPermissions()) {
                            runCatching {
                                manager.requestPeers(channel) { peers ->
                                    val list = peers?.deviceList.orEmpty().map { dev ->
                                        DiscoveredP2pPeer(
                                            deviceName = dev.deviceName.ifBlank { "Nearby Rider" },
                                            deviceAddress = dev.deviceAddress,
                                            isGroupOwner = dev.isGroupOwner,
                                            statusLabel = when (dev.status) {
                                                WifiP2pDevice.CONNECTED -> "Connected"
                                                WifiP2pDevice.INVITED -> "Invited"
                                                WifiP2pDevice.AVAILABLE -> "Available"
                                                else -> "Nearby"
                                            }
                                        )
                                    }
                                    _uiState.value = _uiState.value.copy(wifiDirectPeers = list)
                                }
                            }
                        }
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        runCatching {
                            manager.requestConnectionInfo(channel) { info ->
                                val ownerIp = info?.groupOwnerAddress?.hostAddress
                                if (!ownerIp.isNullOrBlank() && info.groupFormed && !info.isGroupOwner) {
                                    registerEndpoint(
                                        MeshPeerEndpoint(
                                            serviceName = "WiFiDirect-Leader",
                                            hostAddress = ownerIp,
                                            port = DEFAULT_MESH_PORT
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        wifiP2pReceiver = receiver
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        runCatching {
            manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    _uiState.value = _uiState.value.copy(isDiscovering = true)
                }

                override fun onFailure(reason: Int) {
                    _uiState.value = _uiState.value.copy(isDiscovering = false)
                }
            })
        }
    }

    private fun stopSocketsAndDiscovery() {
        tcpAcceptJob?.cancel()
        tcpAcceptJob = null
        udpListenJob?.cancel()
        udpListenJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        runCatching { udpSocket?.close() }
        udpSocket = null

        val ctx = appContext
        if (ctx != null && wifiP2pReceiver != null) {
            runCatching { ctx.unregisterReceiver(wifiP2pReceiver) }
        }
        wifiP2pReceiver = null
        wifiP2pManager = null
        wifiP2pChannel = null

        val nsd = nsdManager
        if (nsd != null) {
            registrationListener?.let { runCatching { nsd.unregisterService(it) } }
            discoveryListener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        }
        registrationListener = null
        discoveryListener = null
        nsdManager = null
        knownEndpoints.clear()
    }

    fun release() {
        stopMesh()
        scope.cancel()
    }

    companion object {
        const val DEFAULT_MESH_PORT = 47821
        const val NSD_SERVICE_TYPE = "_motopack._tcp."
        const val KIND_TELEMETRY = "TELEMETRY"
        const val KIND_CHAT = "CHAT"
        const val KIND_PTT_VOICE = "PTT_VOICE"
        const val KIND_SOS = "SOS"
    }
}
