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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectionState
import com.example.model.PairingInfo
import com.example.ui.theme.SalimBlue
import com.example.ui.theme.SalimNearBlack
import com.example.ui.theme.SalimSecondaryText
import com.example.ui.theme.SalimStateConnected
import com.example.ui.theme.SalimStateError
import com.example.ui.theme.SalimSurfaceBorder
import com.example.ui.theme.SalimSurfaceCard
import com.example.ui.theme.SalimWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    connectionState: ConnectionState,
    pairingInfo: PairingInfo?,
    lastConnectedTime: Long,
    isDirectP2p: Boolean,
    onReconnect: () -> Unit,
    onUnpair: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showUnpairConfirmDialog by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()) }

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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "Settings & Diagnostics",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = SalimNearBlack
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Paired Host Section
                SectionTitle(title = "Paired Host Device")

                Card(
                    colors = CardDefaults.cardColors(containerColor = SalimSurfaceCard),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SalimSurfaceBorder, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsRow(label = "Host Device Name", value = pairingInfo?.hostName ?: "Not Paired")
                        SettingsRow(label = "Host ID", value = pairingInfo?.hostId ?: "--")
                        SettingsRow(
                            label = "First Paired",
                            value = if (pairingInfo != null && pairingInfo.pairedAt > 0) {
                                dateFormat.format(Date(pairingInfo.pairedAt))
                            } else "--"
                        )
                        SettingsRow(
                            label = "Last Connected",
                            value = if (lastConnectedTime > 0) {
                                dateFormat.format(Date(lastConnectedTime))
                            } else if (connectionState is ConnectionState.Connected) {
                                "Currently Connected"
                            } else "Never"
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = onReconnect,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = SalimBlue, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reconnect", fontSize = 13.sp, color = SalimBlue, fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = { showUnpairConfirmDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = SalimSurfaceCard),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SalimStateError.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Icon(Icons.Default.LinkOff, contentDescription = null, tint = SalimStateError, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Unpair Host", fontSize = 13.sp, color = SalimStateError, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Diagnostics Section
                SectionTitle(title = "Connection Diagnostics")

                Card(
                    colors = CardDefaults.cardColors(containerColor = SalimSurfaceCard),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SalimSurfaceBorder, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsRow(
                            label = "Transport Protocol",
                            value = if (isDirectP2p) "Direct P2P (UDP / STUN)" else "TURN Relay (Encrypted)"
                        )
                        SettingsRow(
                            label = "Signaling Channel",
                            value = "Firebase Firestore (Session mailbox)"
                        )
                        SettingsRow(
                            label = "Data Channels",
                            value = "control, files, notifications, status"
                        )
                        SettingsRow(
                            label = "Media Stream",
                            value = "WebRTC VideoTrack (Hardware accelerated)"
                        )
                        SettingsRow(
                            label = "Encryption",
                            value = "DTLS-SRTP 256-bit"
                        )
                    }
                }

                // System & Architecture Section
                SectionTitle(title = "System Architecture")

                Card(
                    colors = CardDefaults.cardColors(containerColor = SalimSurfaceCard),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SalimSurfaceBorder, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsRow(label = "Application", value = "Salim (Controller — Phone B)")
                        SettingsRow(label = "Host App", value = "Ubaid (Screen Host — Phone A)")
                        SettingsRow(label = "Signaling Project", value = "salim-x-ubaid")
                        SettingsRow(label = "Version", value = "1.0.0 (Production-Grade)")
                    }
                }
            }

            Spacer(modifier = Modifier.height(90.dp))
        }

        // Unpair Confirmation Dialog
        if (showUnpairConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showUnpairConfirmDialog = false },
                title = {
                    Text(
                        text = "Unpair Host Device?",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = SalimNearBlack
                    )
                },
                text = {
                    Text(
                        text = "This will disconnect from Ubaid and clear pairing credentials. You will need to scan Ubaid's QR code again to reconnect.",
                        fontSize = 13.sp,
                        color = SalimSecondaryText,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showUnpairConfirmDialog = false
                            onUnpair()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SalimStateError),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Unpair", color = SalimWhite, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUnpairConfirmDialog = false }) {
                        Text("Cancel", color = SalimSecondaryText)
                    }
                },
                containerColor = SalimWhite,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = SalimNearBlack,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

@Composable
private fun SettingsRow(
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(text = label, fontSize = 13.sp, color = SalimSecondaryText)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = SalimNearBlack)
    }
}
