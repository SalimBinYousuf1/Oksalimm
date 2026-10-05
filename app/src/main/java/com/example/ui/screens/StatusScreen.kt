package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectionState
import com.example.model.HostStatus
import com.example.model.PairingInfo
import com.example.ui.components.ConnectionBanner
import com.example.ui.components.StatusBadge
import com.example.ui.theme.SalimBlue
import com.example.ui.theme.SalimNearBlack
import com.example.ui.theme.SalimSecondaryText
import com.example.ui.theme.SalimStateConnected
import com.example.ui.theme.SalimStateConnectedBg
import com.example.ui.theme.SalimStateConnectingBg
import com.example.ui.theme.SalimStateError
import com.example.ui.theme.SalimStateErrorBg
import com.example.ui.theme.SalimSurfaceBorder
import com.example.ui.theme.SalimSurfaceCard
import com.example.ui.theme.SalimWhite

@Composable
fun StatusScreen(
    connectionState: ConnectionState,
    pairingInfo: PairingInfo?,
    hostStatus: HostStatus,
    onRetryConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = connectionState is ConnectionState.Connected

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SalimWhite)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Column {
                    Text(
                        text = "Device Status",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = SalimNearBlack
                    )
                    Text(
                        text = pairingInfo?.hostName ?: "Host Device",
                        fontSize = 13.sp,
                        color = SalimSecondaryText
                    )
                }

                StatusBadge(state = connectionState)
            }

            if (!isConnected) {
                ConnectionBanner(
                    state = connectionState,
                    onRetryClicked = onRetryConnection,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Metrics Grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Battery Card
                TelemetryCard(
                    icon = if (hostStatus.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                    iconTint = if (hostStatus.batteryPercent <= 20) SalimStateError else SalimBlue,
                    title = "Battery",
                    subtitle = if (hostStatus.isCharging) "Charging • ${hostStatus.batteryPercent}%" else "${hostStatus.batteryPercent}% Remaining"
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { hostStatus.batteryPercent / 100f },
                        color = if (hostStatus.batteryPercent <= 20) SalimStateError else SalimBlue,
                        trackColor = SalimSurfaceBorder,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MetricDetail(label = "State", value = if (hostStatus.isCharging) "AC Charging" else "Discharging")
                        MetricDetail(label = "Temperature", value = "${String.format("%.1f", hostStatus.temperatureC)}°C")
                        MetricDetail(label = "Health", value = "Good")
                    }
                }

                // Network & WebRTC Connection Card
                TelemetryCard(
                    icon = Icons.Default.Wifi,
                    iconTint = SalimBlue,
                    title = "Network & WebRTC Connection",
                    subtitle = hostStatus.networkType
                ) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MetricDetail(
                            label = "Latency (RTT)",
                            value = if (isConnected) "${hostStatus.latencyMs}ms" else "--"
                        )
                        MetricDetail(
                            label = "Connection Mode",
                            value = hostStatus.connectionType
                        )
                        MetricDetail(
                            label = "Security",
                            value = "DTLS / SRTP"
                        )
                    }
                }

                // Screen & Mirroring Stream Card
                TelemetryCard(
                    icon = Icons.Default.Tv,
                    iconTint = SalimBlue,
                    title = "Screen Mirroring Stream",
                    subtitle = if (hostStatus.isScreenOn) "Screen Active" else "Screen Off"
                ) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MetricDetail(
                            label = "Resolution",
                            value = hostStatus.resolution
                        )
                        MetricDetail(
                            label = "Frame Rate",
                            value = if (isConnected) "${hostStatus.fps} FPS" else "--"
                        )
                        MetricDetail(
                            label = "Codec",
                            value = "VP8 / H.264"
                        )
                    }
                }

                // Hardware & OS Card
                TelemetryCard(
                    icon = Icons.Default.PhoneAndroid,
                    iconTint = SalimBlue,
                    title = "Host Hardware",
                    subtitle = hostStatus.modelName
                ) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MetricDetail(
                            label = "OS Version",
                            value = hostStatus.androidVersion
                        )
                        MetricDetail(
                            label = "Host ID",
                            value = pairingInfo?.hostId?.take(12) ?: "Not linked"
                        )
                        MetricDetail(
                            label = "Role",
                            value = "Host (Ubaid)"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(90.dp))
        }
    }
}

@Composable
private fun TelemetryCard(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SalimSurfaceCard),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SalimSurfaceBorder, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(iconTint.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = SalimNearBlack
                        )
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = SalimSecondaryText
                        )
                    }
                }
            }

            content()
        }
    }
}

@Composable
private fun MetricDetail(
    label: String,
    value: String
) {
    Column {
        Text(text = label, fontSize = 11.sp, color = SalimSecondaryText)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SalimNearBlack)
    }
}
