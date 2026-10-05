package com.example.webrtc

import android.content.Context
import android.util.Log
import com.example.SalimApplication
import com.example.model.ControlMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class WebRtcClient(
    private val context: Context,
    private val onIceCandidateGenerated: (IceCandidate) -> Unit,
    private val onConnectionStateChanged: (PeerConnection.IceConnectionState) -> Unit,
    private val onMessageReceived: (channelLabel: String, message: String) -> Unit,
    private val onStatsUpdated: (pingMs: Long, isDirectP2p: Boolean, fps: Int) -> Unit
) {
    companion object {
        private const val TAG = "WebRtcClient"
        const val CHANNEL_CONTROL = "control"
        const val CHANNEL_FILES = "files"
        const val CHANNEL_NOTIFICATIONS = "notifications"
        const val CHANNEL_STATUS = "status"
    }

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private val dataChannels = mutableMapOf<String, DataChannel>()
    private var remoteVideoTrack: VideoTrack? = null
    private var attachedRenderer: SurfaceViewRenderer? = null
    private var statsJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private val pendingIceCandidates = mutableListOf<IceCandidate>()
    private var isRemoteDescriptionSet = false

    fun initialize(): Boolean {
        if (!SalimApplication.isWebRtcAvailable) {
            Log.e(TAG, "WebRTC native library unavailable")
            return false
        }
        return try {
            val options = PeerConnectionFactory.Options()
            factory = PeerConnectionFactory.builder()
                .setOptions(options)
                .createPeerConnectionFactory()
            Log.i(TAG, "PeerConnectionFactory initialized")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Error initializing PeerConnectionFactory: ${t.message}", t)
            false
        }
    }

    fun createPeerConnection(): Boolean {
        val f = factory ?: return false
        return try {
            val iceServers = listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun.services.mozilla.com").createIceServer()
            )

            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
                bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                keyType = PeerConnection.KeyType.ECDSA
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            }

            peerConnection = f.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                    Log.d(TAG, "SignalingState: $state")
                }

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Log.i(TAG, "IceConnectionState: $state")
                    state?.let {
                        onConnectionStateChanged(it)
                        if (it == PeerConnection.IceConnectionState.CONNECTED ||
                            it == PeerConnection.IceConnectionState.COMPLETED
                        ) {
                            startStatsPolling()
                        } else if (it == PeerConnection.IceConnectionState.FAILED ||
                            it == PeerConnection.IceConnectionState.DISCONNECTED
                        ) {
                            stopStatsPolling()
                        }
                    }
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) {
                    Log.d(TAG, "IceConnectionReceiving: $receiving")
                }

                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                    Log.d(TAG, "IceGatheringState: $state")
                }

                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate?.let {
                        Log.d(TAG, "Local IceCandidate gathered: ${it.sdpMid}")
                        onIceCandidateGenerated(it)
                    }
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

                override fun onAddStream(stream: MediaStream?) {
                    Log.i(TAG, "onAddStream with ${stream?.videoTracks?.size ?: 0} video tracks")
                    stream?.videoTracks?.firstOrNull()?.let { track ->
                        setRemoteVideoTrack(track)
                    }
                }

                override fun onRemoveStream(stream: MediaStream?) {
                    Log.i(TAG, "onRemoveStream")
                }

                override fun onDataChannel(channel: DataChannel?) {
                    channel?.let {
                        Log.i(TAG, "Remote host created DataChannel: ${it.label()}")
                        registerDataChannel(it)
                    }
                }

                override fun onRenegotiationNeeded() {
                    Log.d(TAG, "onRenegotiationNeeded")
                }

                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                    Log.i(TAG, "onAddTrack")
                    (receiver?.track() as? VideoTrack)?.let { track ->
                        setRemoteVideoTrack(track)
                    }
                }

                override fun onTrack(transceiver: RtpTransceiver?) {
                    (transceiver?.receiver?.track() as? VideoTrack)?.let { track ->
                        setRemoteVideoTrack(track)
                    }
                }
            })

            // Add recvonly transceiver for incoming video track from Ubaid
            peerConnection?.addTransceiver(
                org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
            )

            // Setup DataChannels: control, files, notifications, status
            val init = DataChannel.Init().apply {
                ordered = true
                maxRetransmits = -1
            }
            createAndRegisterChannel(CHANNEL_CONTROL, init)
            createAndRegisterChannel(CHANNEL_FILES, init)
            createAndRegisterChannel(CHANNEL_NOTIFICATIONS, init)
            createAndRegisterChannel(CHANNEL_STATUS, init)

            true
        } catch (t: Throwable) {
            Log.e(TAG, "createPeerConnection failed: ${t.message}", t)
            false
        }
    }

    private fun createAndRegisterChannel(label: String, init: DataChannel.Init) {
        val pc = peerConnection ?: return
        try {
            val channel = pc.createDataChannel(label, init)
            if (channel != null) {
                registerDataChannel(channel)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to create DataChannel $label: ${t.message}")
        }
    }

    private fun registerDataChannel(channel: DataChannel) {
        val label = channel.label()
        dataChannels[label] = channel
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}

            override fun onStateChange() {
                Log.d(TAG, "DataChannel $label state: ${channel.state()}")
            }

            override fun onMessage(buffer: DataChannel.Buffer?) {
                if (buffer != null) {
                    try {
                        val bytes = ByteArray(buffer.data.remaining())
                        buffer.data.get(bytes)
                        val text = String(bytes, StandardCharsets.UTF_8)
                        onMessageReceived(label, text)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error decoding message on $label: ${t.message}")
                    }
                }
            }
        })
    }

    fun setRemoteVideoTrack(track: VideoTrack) {
        remoteVideoTrack = track
        track.setEnabled(true)
        attachedRenderer?.let { renderer ->
            try {
                track.addSink(renderer)
                Log.i(TAG, "Attached remote video track to SurfaceViewRenderer")
            } catch (t: Throwable) {
                Log.e(TAG, "Error adding sink to video track: ${t.message}")
            }
        }
    }

    fun attachRenderer(renderer: SurfaceViewRenderer) {
        attachedRenderer = renderer
        remoteVideoTrack?.let { track ->
            try {
                track.addSink(renderer)
                Log.i(TAG, "Attached SurfaceViewRenderer to existing video track")
            } catch (t: Throwable) {
                Log.e(TAG, "Error attaching renderer: ${t.message}")
            }
        }
    }

    fun detachRenderer() {
        attachedRenderer?.let { renderer ->
            try {
                remoteVideoTrack?.removeSink(renderer)
            } catch (_: Throwable) {}
        }
        attachedRenderer = null
    }

    fun createOffer(callback: (SessionDescription?) -> Unit) {
        val pc = peerConnection ?: run {
            callback(null)
            return
        }
        val sdpConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        }

        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) {
                    pc.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.i(TAG, "SetLocalDescription success")
                            callback(desc)
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "SetLocalDescription failure: $error")
                            callback(desc)
                        }
                    }, desc)
                } else {
                    callback(null)
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "CreateOffer failure: $error")
                callback(null)
            }
            override fun onSetFailure(error: String?) {}
        }, sdpConstraints)
    }

    fun setRemoteAnswer(sdpString: String, callback: (Boolean) -> Unit) {
        val pc = peerConnection ?: run {
            callback(false)
            return
        }
        val desc = SessionDescription(SessionDescription.Type.ANSWER, sdpString)
        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.i(TAG, "SetRemoteDescription (Answer) success")
                isRemoteDescriptionSet = true
                synchronized(pendingIceCandidates) {
                    for (c in pendingIceCandidates) {
                        try {
                            pc.addIceCandidate(c)
                        } catch (t: Throwable) {
                            Log.w(TAG, "Error draining queued candidate: ${t.message}")
                        }
                    }
                    pendingIceCandidates.clear()
                }
                callback(true)
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "SetRemoteDescription (Answer) failure: $error")
                callback(false)
            }
        }, desc)
    }

    fun addRemoteIceCandidate(candidate: IceCandidate) {
        if (!isRemoteDescriptionSet) {
            synchronized(pendingIceCandidates) {
                pendingIceCandidates.add(candidate)
            }
        } else {
            try {
                peerConnection?.addIceCandidate(candidate)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to add remote ICE candidate: ${t.message}")
            }
        }
    }

    fun sendMessage(channelLabel: String, text: String): Boolean {
        val channel = dataChannels[channelLabel] ?: dataChannels[CHANNEL_CONTROL]
        if (channel != null && channel.state() == DataChannel.State.OPEN) {
            return try {
                val bytes = text.toByteArray(StandardCharsets.UTF_8)
                val buffer = DataChannel.Buffer(ByteBuffer.wrap(bytes), false)
                channel.send(buffer)
            } catch (t: Throwable) {
                Log.e(TAG, "Error sending data on $channelLabel: ${t.message}")
                false
            }
        }
        return false
    }

    fun sendControlMessage(message: ControlMessage): Boolean {
        return sendMessage(CHANNEL_CONTROL, message.toJson())
    }

    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive) {
                delay(2000)
                peerConnection?.getStats { rtcStatsReport ->
                    try {
                        var pingMs = 28L
                        var isDirect = true
                        var fps = 30

                        for (stat in rtcStatsReport.statsMap.values) {
                            if (stat.type == "candidate-pair" && stat.members["state"] == "succeeded") {
                                val rtt = (stat.members["currentRoundTripTime"] as? Number)?.toDouble()
                                if (rtt != null && rtt > 0) {
                                    pingMs = (rtt * 1000).toLong().coerceAtLeast(1)
                                }
                            }
                            if (stat.type == "inbound-rtp" && stat.members["kind"] == "video") {
                                val framesPerSecond = (stat.members["framesPerSecond"] as? Number)?.toInt()
                                if (framesPerSecond != null && framesPerSecond > 0) {
                                    fps = framesPerSecond
                                }
                            }
                            if (stat.type == "remote-candidate") {
                                val candidateType = stat.members["candidateType"] as? String
                                if (candidateType == "relay") {
                                    isDirect = false
                                }
                            }
                        }
                        onStatsUpdated(pingMs, isDirect, fps)
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    private fun stopStatsPolling() {
        statsJob?.cancel()
        statsJob = null
    }

    fun close() {
        stopStatsPolling()
        detachRenderer()
        synchronized(pendingIceCandidates) {
            pendingIceCandidates.clear()
        }
        isRemoteDescriptionSet = false
        for (ch in dataChannels.values) {
            try {
                ch.unregisterObserver()
                ch.close()
            } catch (_: Throwable) {}
        }
        dataChannels.clear()

        try {
            peerConnection?.close()
            peerConnection?.dispose()
        } catch (_: Throwable) {}
        peerConnection = null

        try {
            factory?.dispose()
        } catch (_: Throwable) {}
        factory = null
    }
}
