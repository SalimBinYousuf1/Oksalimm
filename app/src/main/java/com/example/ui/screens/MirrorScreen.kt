package com.example.ui.screens

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.ConnectionState
import com.example.model.HostStatus
import com.example.model.PairingInfo
import com.example.ui.components.StatusBadge
import com.example.ui.theme.SalimBlue
import com.example.ui.theme.SalimNearBlack
import com.example.ui.theme.SalimOverlayBorder
import com.example.ui.theme.SalimOverlayDark
import com.example.ui.theme.SalimOverlayText
import com.example.ui.theme.SalimSecondaryText
import com.example.ui.theme.SalimStateConnected
import com.example.ui.theme.SalimStateError
import com.example.ui.theme.SalimStateErrorBg
import com.example.ui.theme.SalimStateReconnecting
import com.example.ui.theme.SalimStateWaiting
import com.example.ui.theme.SalimStateWaitingBg
import com.example.ui.theme.SalimSurfaceBorder
import com.example.ui.theme.SalimWhite
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MirrorScreen(
    connectionState: ConnectionState,
    pairingInfo: PairingInfo?,
    hostStatus: HostStatus,
    onAttachRenderer: (SurfaceViewRenderer) -> Unit,
    onDetachRenderer: () -> Unit,
    onSendTap: (normX: Float, normY: Float) -> Unit,
    onSendTouchDown: (normX: Float, normY: Float) -> Unit,
    onSendTouchMove: (normX: Float, normY: Float) -> Unit,
    onSendTouchUp: (normX: Float, normY: Float) -> Unit,
    onSendNavigationKey: (key: String) -> Unit,
    onSendText: (text: String) -> Unit,
    onReconnectClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showKeyboardDialog by remember { mutableStateOf(false) }
    val isConnected = connectionState is ConnectionState.Connected

    // Parse host aspect ratio from resolution e.g. "1080x2400"
    val hostAspect = remember(hostStatus.resolution) {
        try {
            val parts = hostStatus.resolution.split("x")
            if (parts.size == 2) {
                val w = parts[0].toFloat()
                val h = parts[1].toFloat()
                if (h > 0) w / h else 9f / 20f
            } else 9f / 20f
        } catch (_: Exception) {
            9f / 20f
        }
    }

    val eglBase = remember { EglBase.create() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val containerW = constraints.maxWidth.toFloat()
            val containerH = constraints.maxHeight.toFloat()

            // Compute letterboxed bounds in exact screen pixels
            val containerAspect = if (containerH > 0f) containerW / containerH else 1f
            val (actualW, actualH, offsetX, offsetY) = remember(containerW, containerH, hostAspect) {
                if (containerAspect > hostAspect) {
                    val w = containerH * hostAspect
                    val offX = (containerW - w) / 2f
                    listOf(w, containerH, offX, 0f)
                } else {
                    val h = if (hostAspect > 0f) containerW / hostAspect else containerH
                    val offY = (containerH - h) / 2f
                    listOf(containerW, h, 0f, offY)
                }
            }

            // WebRTC SurfaceViewRenderer
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        init(eglBase.eglBaseContext, null)
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                        setEnableHardwareScaler(true)
                    }
                },
                update = { renderer ->
                    onAttachRenderer(renderer)
                },
                onRelease = { renderer ->
                    try {
                        renderer.release()
                    } catch (_: Throwable) {}
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInteropFilter { motionEvent ->
                        if (!isConnected) return@pointerInteropFilter false

                        val touchX = motionEvent.x
                        val touchY = motionEvent.y

                        // Translate to normalized Ubaid coordinates (0.0 .. 1.0)
                        val normX = if (actualW > 0f) ((touchX - offsetX) / actualW).coerceIn(0f, 1f) else 0.5f
                        val normY = if (actualH > 0f) ((touchY - offsetY) / actualH).coerceIn(0f, 1f) else 0.5f

                        when (motionEvent.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                onSendTouchDown(normX, normY)
                                true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                onSendTouchMove(normX, normY)
                                true
                            }
                            MotionEvent.ACTION_UP -> {
                                onSendTouchUp(normX, normY)
                                true
                            }
                            MotionEvent.ACTION_CANCEL -> {
                                onSendTouchUp(normX, normY)
                                true
                            }
                            else -> false
                        }
                    }
            )
        }

        DisposableEffect(eglBase) {
            onDispose {
                onDetachRenderer()
                try {
                    eglBase.release()
                } catch (_: Throwable) {}
            }
        }

        // Top Status Header (Floating capsule, non-blocking)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            StatusBadge(state = connectionState)

            if (isConnected) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(SalimOverlayDark)
                        .border(1.dp, SalimOverlayBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${hostStatus.fps} FPS",
                        color = SalimOverlayText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• ${hostStatus.batteryPercent}%",
                        color = if (hostStatus.batteryPercent <= 20) SalimStateError else SalimOverlayText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Non-Connected State Overlay (honest representation, never a frozen image)
        AnimatedVisibility(
            visible = !isConnected,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            ConnectionOverlayCard(
                state = connectionState,
                pairingInfo = pairingInfo,
                onRetry = onReconnectClicked
            )
        }

        // Bottom Remote Navigation Bar (High contrast floating capsule with Back / Home / Recents / Keyboard)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            RemoteControlsCapsule(
                enabled = isConnected,
                onBackClicked = { onSendNavigationKey("BACK") },
                onHomeClicked = { onSendNavigationKey("HOME") },
                onRecentsClicked = { onSendNavigationKey("RECENTS") },
                onKeyboardClicked = { showKeyboardDialog = true }
            )
        }

        // Keyboard Text Input Dialog
        if (showKeyboardDialog) {
            RemoteKeyboardDialog(
                onDismiss = { showKeyboardDialog = false },
                onSendText = { text ->
                    onSendText(text)
                    showKeyboardDialog = false
                }
            )
        }
    }
}

@Composable
private fun RemoteControlsCapsule(
    enabled: Boolean,
    onBackClicked: () -> Unit,
    onHomeClicked: () -> Unit,
    onRecentsClicked: () -> Unit,
    onKeyboardClicked: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(32.dp))
            .background(SalimOverlayDark)
            .border(1.dp, SalimOverlayBorder, RoundedCornerShape(32.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Back
        IconButton(
            onClick = onBackClicked,
            enabled = enabled,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = if (enabled) SalimOverlayText else SalimOverlayText.copy(alpha = 0.4f),
                modifier = Modifier.size(24.dp)
            )
        }

        // Home
        IconButton(
            onClick = onHomeClicked,
            enabled = enabled,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Circle,
                contentDescription = "Home",
                tint = if (enabled) SalimOverlayText else SalimOverlayText.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }

        // Recents / App Switcher
        IconButton(
            onClick = onRecentsClicked,
            enabled = enabled,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = "Recents",
                tint = if (enabled) SalimOverlayText else SalimOverlayText.copy(alpha = 0.4f),
                modifier = Modifier.size(24.dp)
            )
        }

        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(24.dp)
                .background(SalimOverlayBorder)
        )

        // Remote Keyboard Input
        IconButton(
            onClick = onKeyboardClicked,
            enabled = enabled,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Keyboard,
                contentDescription = "Type text to host",
                tint = if (enabled) SalimBlue else SalimOverlayText.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun ConnectionOverlayCard(
    state: ConnectionState,
    pairingInfo: PairingInfo?,
    onRetry: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SalimWhite),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth(0.88f)
            .border(1.dp, SalimSurfaceBorder, RoundedCornerShape(20.dp))
            .padding(4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            when (state) {
                is ConnectionState.Connecting, is ConnectionState.Reconnecting -> {
                    CircularProgressIndicator(
                        color = SalimBlue,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp)
                    )
                }
                is ConnectionState.WaitingOnUbaid -> {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(SalimStateWaitingBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⏳", fontSize = 26.sp)
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(SalimStateErrorBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⚡", fontSize = 26.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = state.label,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = SalimNearBlack,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = state.detail,
                fontSize = 13.sp,
                color = SalimSecondaryText,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            if (pairingInfo != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Host ID: ${pairingInfo.hostId}",
                    fontSize = 12.sp,
                    color = SalimSecondaryText,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = SalimBlue),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = SalimWhite, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reconnect to Host", fontWeight = FontWeight.SemiBold, color = SalimWhite)
            }
        }
    }
}

@Composable
private fun RemoteKeyboardDialog(
    onDismiss: () -> Unit,
    onSendText: (String) -> Unit
) {
    var textInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Send Text to Ubaid",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = SalimNearBlack
            )
        },
        text = {
            Column {
                Text(
                    text = "Type text to send directly to the currently focused input field on Ubaid:",
                    fontSize = 13.sp,
                    color = SalimSecondaryText,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Enter text or URL...") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (textInput.isNotEmpty()) {
                        onSendText(textInput)
                    }
                },
                enabled = textInput.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = SalimBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = null, tint = SalimWhite, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Send", color = SalimWhite, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SalimSecondaryText)
            }
        },
        containerColor = SalimWhite,
        shape = RoundedCornerShape(16.dp)
    )
}
