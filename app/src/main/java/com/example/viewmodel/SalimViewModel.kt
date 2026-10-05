package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SalimApplication
import com.example.data.PairingStorage
import com.example.data.SignalingRepository
import com.example.model.ConnectionState
import com.example.model.ControlMessage
import com.example.model.FileProtocol
import com.example.model.FileTransferProgress
import com.example.model.HostStatus
import com.example.model.NotificationItem
import com.example.model.PairingInfo
import com.example.model.RemoteFileItem
import com.example.webrtc.WebRtcClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.webrtc.PeerConnection
import org.webrtc.SurfaceViewRenderer
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class SalimViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "SalimViewModel"
        private const val CHUNK_SIZE = 16 * 1024 // 16 KB chunks for stable WebRTC DataChannel transfer
    }

    private val storage = PairingStorage(application)
    private val signaling = SignalingRepository()
    private var webRtcClient: WebRtcClient? = null
    private var reconnectJob: Job? = null
    private var sessionObserverJob: Job? = null
    private var candidateObserverJob: Job? = null
    private var isReconnecting = false

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.NotPaired)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _pairingInfo = MutableStateFlow<PairingInfo?>(null)
    val pairingInfo: StateFlow<PairingInfo?> = _pairingInfo.asStateFlow()

    private val _hostStatus = MutableStateFlow(HostStatus())
    val hostStatus: StateFlow<HostStatus> = _hostStatus.asStateFlow()

    private val _remoteFiles = MutableStateFlow<List<RemoteFileItem>>(emptyList())
    val remoteFiles: StateFlow<List<RemoteFileItem>> = _remoteFiles.asStateFlow()

    private val _currentFolderPath = MutableStateFlow("/sdcard")
    val currentFolderPath: StateFlow<String> = _currentFolderPath.asStateFlow()

    private val _fileTransfers = MutableStateFlow<Map<String, FileTransferProgress>>(emptyMap())
    val fileTransfers: StateFlow<Map<String, FileTransferProgress>> = _fileTransfers.asStateFlow()

    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0: Mirror, 1: Files, 2: Alerts, 3: Status, 4: Settings
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _lastLatencyMs = MutableStateFlow(0L)
    val lastLatencyMs: StateFlow<Long> = _lastLatencyMs.asStateFlow()

    private val _isDirectP2p = MutableStateFlow(true)
    val isDirectP2p: StateFlow<Boolean> = _isDirectP2p.asStateFlow()

    private val _lastConnectedTime = MutableStateFlow(0L)
    val lastConnectedTime: StateFlow<Long> = _lastConnectedTime.asStateFlow()

    // Temporary storage for receiving chunked file downloads
    private val activeDownloadBuffers = mutableMapOf<String, FileOutputStream>()

    init {
        checkInitialState()
    }

    fun selectTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
        if (tabIndex == 1 && _remoteFiles.value.isEmpty()) {
            requestDirectory(_currentFolderPath.value)
        }
    }

    fun checkInitialState() {
        if (!SalimApplication.isFirebaseConfigured) {
            _connectionState.value = ConnectionState.SetupIncomplete
            return
        }

        val savedInfo = storage.getPairingInfo()
        _lastConnectedTime.value = storage.getLastConnectedTime()

        if (savedInfo != null) {
            _pairingInfo.value = savedInfo
            connectToHost(savedInfo)
        } else {
            _connectionState.value = ConnectionState.NotPaired
        }
    }

    fun pairWithPayload(qrPayload: String): Boolean {
        val parsed = PairingInfo.parse(qrPayload)
        if (parsed != null) {
            storage.savePairingInfo(parsed)
            _pairingInfo.value = parsed
            connectToHost(parsed)
            return true
        }
        return false
    }

    fun connectToHost(info: PairingInfo) {
        if (!SalimApplication.isFirebaseConfigured) {
            _connectionState.value = ConnectionState.SetupIncomplete
            return
        }

        reconnectJob?.cancel()
        isReconnecting = false
        _connectionState.value = ConnectionState.Connecting("Initializing secure connection...")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Clean up previous client
                webRtcClient?.close()
                signaling.cleanup()

                // Create new WebRTC client
                val client = WebRtcClient(
                    context = getApplication(),
                    onIceCandidateGenerated = { candidate ->
                        viewModelScope.launch(Dispatchers.IO) {
                            signaling.sendIceCandidate(info.hostId, candidate)
                        }
                    },
                    onConnectionStateChanged = { iceState ->
                        handleIceConnectionState(iceState, info)
                    },
                    onMessageReceived = { channelLabel, message ->
                        handleIncomingMessage(channelLabel, message)
                    },
                    onStatsUpdated = { pingMs, isDirect, fps ->
                        _lastLatencyMs.value = pingMs
                        _isDirectP2p.value = isDirect
                        val current = _connectionState.value
                        if (current is ConnectionState.Connected) {
                            _connectionState.value = current.copy(
                                pingMs = pingMs,
                                isDirectP2p = isDirect,
                                fps = fps
                            )
                        }
                        _hostStatus.value = _hostStatus.value.copy(
                            latencyMs = pingMs,
                            fps = fps,
                            connectionType = if (isDirect) "Direct P2P (Local/STUN)" else "TURN Relay"
                        )
                    }
                )

                if (!client.initialize() || !client.createPeerConnection()) {
                    _connectionState.value = ConnectionState.Disconnected("Failed to initialize WebRTC engine")
                    return@launch
                }
                webRtcClient = client

                // Send controller presence to Firestore
                signaling.sendControllerPresence(info.hostId, online = true)

                // Start observing Host session & ICE candidates
                observeHostSignaling(info.hostId)

                // Create and send SDP Offer
                _connectionState.value = ConnectionState.Connecting("Creating WebRTC offer...")
                client.createOffer { sdp ->
                    if (sdp != null) {
                        viewModelScope.launch(Dispatchers.IO) {
                            _connectionState.value = ConnectionState.Connecting("Awaiting response from Ubaid...")
                            signaling.sendOffer(info.hostId, sdp)
                        }
                    } else {
                        _connectionState.value = ConnectionState.Disconnected("Failed to generate SDP offer")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "connectToHost failed: ${e.message}", e)
                _connectionState.value = ConnectionState.Disconnected("Connection error: ${e.localizedMessage}")
            }
        }
    }

    private fun observeHostSignaling(hostId: String) {
        sessionObserverJob?.cancel()
        candidateObserverJob?.cancel()

        sessionObserverJob = viewModelScope.launch(Dispatchers.IO) {
            signaling.observeSession(hostId).collect { data ->
                if (data.isEmpty()) return@collect

                val hostOnline = data["hostOnline"] as? Boolean ?: true
                val hostStatusStr = data["status"] as? String

                if (!hostOnline) {
                    _connectionState.value = ConnectionState.UbaidUnreachable(
                        reason = "Host device is offline. Make sure Ubaid is running."
                    )
                    return@collect
                }

                if (hostStatusStr == "waiting_on_host" || hostStatusStr == "waiting_on_ubaid") {
                    _connectionState.value = ConnectionState.WaitingOnUbaid()
                    return@collect
                }

                // Check for remote SDP Answer
                val answerMap = data["answer"] as? Map<*, *>
                if (answerMap != null) {
                    val sdpType = answerMap["type"] as? String
                    val sdpContent = answerMap["sdp"] as? String
                    if (sdpType == "answer" && !sdpContent.isNullOrBlank()) {
                        webRtcClient?.setRemoteAnswer(sdpContent) { success ->
                            if (!success) {
                                Log.e(TAG, "Failed to apply remote answer")
                            }
                        }
                    }
                }

                // Check for array candidates on the document
                val candidatesList = (data["hostCandidates"] as? List<*>) ?: (data["calleeCandidates"] as? List<*>)
                candidatesList?.forEach { item ->
                    if (item is Map<*, *>) {
                        val sdpMid = item["sdpMid"] as? String ?: ""
                        val sdpMLineIndex = (item["sdpMLineIndex"] as? Number)?.toInt() ?: 0
                        val sdp = item["sdp"] as? String ?: ""
                        if (sdp.isNotEmpty()) {
                            webRtcClient?.addRemoteIceCandidate(org.webrtc.IceCandidate(sdpMid, sdpMLineIndex, sdp))
                        }
                    }
                }
            }
        }

        candidateObserverJob = viewModelScope.launch(Dispatchers.IO) {
            signaling.observeHostCandidates(hostId).collect { candidate ->
                webRtcClient?.addRemoteIceCandidate(candidate)
            }
        }
    }

    private fun handleIceConnectionState(iceState: PeerConnection.IceConnectionState, info: PairingInfo) {
        viewModelScope.launch {
            when (iceState) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> {
                    isReconnecting = false
                    reconnectJob?.cancel()
                    storage.recordSuccessfulConnection()
                    _lastConnectedTime.value = System.currentTimeMillis()
                    _connectionState.value = ConnectionState.Connected(
                        pingMs = _lastLatencyMs.value.coerceAtLeast(18),
                        isDirectP2p = _isDirectP2p.value
                    )
                    // Request initial directory listing
                    requestDirectory(_currentFolderPath.value)
                }

                PeerConnection.IceConnectionState.DISCONNECTED -> {
                    if (!isReconnecting && _connectionState.value is ConnectionState.Connected) {
                        triggerAutoReconnect(info, "Connection dropped. Auto-reconnecting...")
                    }
                }

                PeerConnection.IceConnectionState.FAILED -> {
                    triggerAutoReconnect(info, "WebRTC peer connection failed. Retrying...")
                }

                PeerConnection.IceConnectionState.CLOSED -> {
                    _connectionState.value = ConnectionState.Disconnected("Connection closed")
                }

                PeerConnection.IceConnectionState.CHECKING -> {
                    if (_connectionState.value !is ConnectionState.Connected && !isReconnecting) {
                        _connectionState.value = ConnectionState.Connecting("Verifying ICE candidates...")
                    }
                }

                else -> {}
            }
        }
    }

    private fun triggerAutoReconnect(info: PairingInfo, reason: String) {
        if (isReconnecting) return
        isReconnecting = true
        reconnectJob?.cancel()

        reconnectJob = viewModelScope.launch {
            for (attempt in 1..10) {
                if (!isActive) break
                _connectionState.value = ConnectionState.Reconnecting(
                    attempt = attempt,
                    maxAttempts = 10,
                    reason = reason
                )
                delay(3000)
                Log.i(TAG, "Executing reconnect attempt $attempt...")
                connectToHost(info)
                delay(5000)
                if (_connectionState.value is ConnectionState.Connected) {
                    isReconnecting = false
                    break
                }
            }
            if (_connectionState.value !is ConnectionState.Connected) {
                isReconnecting = false
                _connectionState.value = ConnectionState.UbaidUnreachable(
                    reason = "Could not reconnect after 10 attempts. Host may be offline or unreachable."
                )
            }
        }
    }

    private fun handleIncomingMessage(channelLabel: String, message: String) {
        viewModelScope.launch {
            try {
                val json = JSONObject(message)
                val type = json.optString("type")

                when (type) {
                    "status" -> {
                        val status = HostStatus.fromJson(json)
                        _hostStatus.value = status
                    }

                    "notification" -> {
                        val notification = NotificationItem.fromJson(json)
                        if (notification != null) {
                            _notifications.value = listOf(notification) + _notifications.value
                        }
                    }

                    "file_list" -> {
                        val parsed = FileProtocol.parseFileListResponse(json)
                        if (parsed != null) {
                            _currentFolderPath.value = parsed.first
                            _remoteFiles.value = parsed.second
                        }
                    }

                    "file_chunk" -> {
                        handleFileChunk(json)
                    }

                    "ping" -> {
                        val sentTime = json.optLong("timestamp", 0)
                        if (sentTime > 0) {
                            val rtt = System.currentTimeMillis() - sentTime
                            _lastLatencyMs.value = rtt
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing incoming message on $channelLabel: ${e.message}")
            }
        }
    }

    private fun handleFileChunk(json: JSONObject) {
        try {
            val transferId = json.getString("transferId")
            val filename = json.getString("filename")
            val chunkIndex = json.getInt("chunkIndex")
            val totalChunks = json.getInt("totalChunks")
            val isLast = json.getBoolean("isLast")
            val base64Data = json.getString("data")

            val dataBytes = Base64.decode(base64Data, Base64.DEFAULT)

            val downloadsDir = File(getApplication<Application>().cacheDir, "downloads").apply { mkdirs() }
            val targetFile = File(downloadsDir, filename)

            if (!activeDownloadBuffers.containsKey(transferId)) {
                activeDownloadBuffers[transferId] = FileOutputStream(targetFile, false)
            }

            val fos = activeDownloadBuffers[transferId]
            fos?.write(dataBytes)

            val updatedTransfers = _fileTransfers.value.toMutableMap()
            val currentProgress = updatedTransfers[transferId]
            val transferredBytes = (currentProgress?.bytesTransferred ?: 0L) + dataBytes.size

            updatedTransfers[transferId] = FileTransferProgress(
                transferId = transferId,
                filename = filename,
                isUpload = false,
                bytesTransferred = transferredBytes,
                totalBytes = if (totalChunks > 0) transferredBytes * totalChunks / (chunkIndex + 1) else transferredBytes,
                currentChunk = chunkIndex + 1,
                totalChunks = totalChunks,
                isComplete = isLast
            )
            _fileTransfers.value = updatedTransfers

            if (isLast) {
                fos?.flush()
                fos?.close()
                activeDownloadBuffers.remove(transferId)
                Log.i(TAG, "Download finished for $filename (${targetFile.length()} bytes)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleFileChunk error: ${e.message}", e)
        }
    }

    // --- Control Actions ---

    fun sendTap(normX: Float, normY: Float) {
        webRtcClient?.sendControlMessage(
            ControlMessage.Tap(
                x = normX.coerceIn(0f, 1f),
                y = normY.coerceIn(0f, 1f)
            )
        )
    }

    fun sendTouchDown(normX: Float, normY: Float, pointerId: Int = 0) {
        webRtcClient?.sendControlMessage(
            ControlMessage.TouchDown(
                x = normX.coerceIn(0f, 1f),
                y = normY.coerceIn(0f, 1f),
                pointerId = pointerId
            )
        )
    }

    fun sendTouchMove(normX: Float, normY: Float, pointerId: Int = 0) {
        webRtcClient?.sendControlMessage(
            ControlMessage.TouchMove(
                x = normX.coerceIn(0f, 1f),
                y = normY.coerceIn(0f, 1f),
                pointerId = pointerId
            )
        )
    }

    fun sendTouchUp(normX: Float, normY: Float, pointerId: Int = 0) {
        webRtcClient?.sendControlMessage(
            ControlMessage.TouchUp(
                x = normX.coerceIn(0f, 1f),
                y = normY.coerceIn(0f, 1f),
                pointerId = pointerId
            )
        )
    }

    fun sendNavigationKey(key: String) {
        webRtcClient?.sendControlMessage(ControlMessage.Key(key))
    }

    fun sendText(text: String) {
        if (text.isNotBlank()) {
            webRtcClient?.sendControlMessage(ControlMessage.Text(text))
        }
    }

    // --- File Actions ---

    fun requestDirectory(path: String) {
        _currentFolderPath.value = path
        val request = FileProtocol.createListFilesRequest(path)
        webRtcClient?.sendMessage(WebRtcClient.CHANNEL_FILES, request)
    }

    fun navigateUp() {
        val current = _currentFolderPath.value
        val parent = if (current.contains("/")) {
            current.substringBeforeLast("/").ifEmpty { "/" }
        } else "/"
        requestDirectory(parent)
    }

    fun requestFileDownload(remoteItem: RemoteFileItem) {
        val transferId = UUID.randomUUID().toString()
        val request = FileProtocol.createDownloadRequest(remoteItem.path, transferId)
        val updated = _fileTransfers.value.toMutableMap()
        updated[transferId] = FileTransferProgress(
            transferId = transferId,
            filename = remoteItem.name,
            isUpload = false,
            bytesTransferred = 0,
            totalBytes = remoteItem.sizeBytes,
            currentChunk = 0,
            totalChunks = if (remoteItem.sizeBytes > 0) ((remoteItem.sizeBytes + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt() else 1
        )
        _fileTransfers.value = updated
        webRtcClient?.sendMessage(WebRtcClient.CHANNEL_FILES, request)
    }

    fun uploadFileFromUri(uri: Uri, context: Context, destinationFolder: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                val filename = getFileNameFromUri(uri, context) ?: "upload_${System.currentTimeMillis()}"
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                val allBytes = inputStream?.readBytes() ?: return@launch
                inputStream.close()

                val transferId = UUID.randomUUID().toString()
                val totalBytes = allBytes.size.toLong()
                val totalChunks = ((allBytes.size + CHUNK_SIZE - 1) / CHUNK_SIZE).coerceAtLeast(1)

                val initMsg = FileProtocol.createUploadInit(destinationFolder, filename, totalBytes, totalChunks, transferId)
                webRtcClient?.sendMessage(WebRtcClient.CHANNEL_FILES, initMsg)

                for (chunkIdx in 0 until totalChunks) {
                    val start = chunkIdx * CHUNK_SIZE
                    val end = (start + CHUNK_SIZE).coerceAtMost(allBytes.size)
                    val chunkData = allBytes.copyOfRange(start, end)
                    val isLast = chunkIdx == totalChunks - 1
                    val base64 = Base64.encodeToString(chunkData, Base64.NO_WRAP)

                    val chunkMsg = FileProtocol.createChunkMessage(transferId, filename, chunkIdx, totalChunks, isLast, base64)
                    webRtcClient?.sendMessage(WebRtcClient.CHANNEL_FILES, chunkMsg)

                    withContext(Dispatchers.Main) {
                        val updated = _fileTransfers.value.toMutableMap()
                        updated[transferId] = FileTransferProgress(
                            transferId = transferId,
                            filename = filename,
                            isUpload = true,
                            bytesTransferred = end.toLong(),
                            totalBytes = totalBytes,
                            currentChunk = chunkIdx + 1,
                            totalChunks = totalChunks,
                            isComplete = isLast
                        )
                        _fileTransfers.value = updated
                    }
                    delay(20) // Flow control to prevent DataChannel buffer overflow
                }

                // Refresh current directory after upload
                delay(300)
                requestDirectory(destinationFolder)
            } catch (e: Exception) {
                Log.e(TAG, "uploadFileFromUri error: ${e.message}", e)
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri, context: Context): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = it.getString(idx)
                }
            }
        }
        return name ?: uri.lastPathSegment
    }

    // --- Notifications Actions ---

    fun clearNotifications() {
        _notifications.value = emptyList()
    }

    fun dismissNotification(id: String) {
        _notifications.value = _notifications.value.filter { it.id != id }
    }

    // --- Renderer Attachment ---

    fun attachRenderer(renderer: SurfaceViewRenderer) {
        webRtcClient?.attachRenderer(renderer)
    }

    fun detachRenderer() {
        webRtcClient?.detachRenderer()
    }

    // --- Unpair / Reset ---

    fun unpair() {
        storage.clearPairing()
        _pairingInfo.value = null
        reconnectJob?.cancel()
        sessionObserverJob?.cancel()
        candidateObserverJob?.cancel()
        webRtcClient?.close()
        webRtcClient = null
        signaling.cleanup()
        _remoteFiles.value = emptyList()
        _notifications.value = emptyList()
        _connectionState.value = ConnectionState.NotPaired
    }

    override fun onCleared() {
        super.onCleared()
        reconnectJob?.cancel()
        sessionObserverJob?.cancel()
        candidateObserverJob?.cancel()
        webRtcClient?.close()
        signaling.cleanup()
    }
}
