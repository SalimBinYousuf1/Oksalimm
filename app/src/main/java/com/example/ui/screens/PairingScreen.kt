package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.scanner.QrScannerView
import com.example.ui.theme.SalimBlue
import com.example.ui.theme.SalimNearBlack
import com.example.ui.theme.SalimSecondaryText
import com.example.ui.theme.SalimStateError
import com.example.ui.theme.SalimStateErrorBg
import com.example.ui.theme.SalimSurfaceBorder
import com.example.ui.theme.SalimSurfaceCard
import com.example.ui.theme.SalimWhite

@Composable
fun PairingScreen(
    onPairWithPayload: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    var showManualDialog by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var isScanningActive by remember { mutableStateOf(true) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SalimWhite)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Column {
                    Text(
                        text = "Pair with Ubaid",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = SalimNearBlack
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Point camera at the QR code on your host device",
                        fontSize = 13.sp,
                        color = SalimSecondaryText
                    )
                }

                IconButton(
                    onClick = { showManualDialog = true },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SalimSurfaceCard)
                        .border(1.dp, SalimSurfaceBorder, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Enter Manually",
                        tint = SalimBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // QR Scanner Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, SalimSurfaceBorder, RoundedCornerShape(20.dp))
            ) {
                if (isScanningActive) {
                    QrScannerView(
                        onQrCodeScanned = { payload ->
                            val success = onPairWithPayload(payload)
                            if (!success) {
                                isScanningActive = false
                                scanError = "Invalid QR code. The scanned code is not a valid Ubaid pairing key."
                            }
                        },
                        onEnterManuallyClicked = { showManualDialog = true },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Error Card overlay if invalid QR scanned
                if (scanError != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SalimWhite),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .align(Alignment.Center)
                            .border(1.dp, SalimStateError, RoundedCornerShape(16.dp))
                            .padding(4.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SalimStateErrorBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = SalimStateError,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Pairing Failed",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = SalimNearBlack
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = scanError ?: "Invalid code scanned.",
                                fontSize = 13.sp,
                                color = SalimSecondaryText,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = { showManualDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Text("Enter Code", fontSize = 13.sp, color = SalimNearBlack)
                                }

                                Button(
                                    onClick = {
                                        scanError = null
                                        isScanningActive = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SalimBlue),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Text("Scan Again", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SalimWhite)
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Instructions & Manual Button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SalimSurfaceCard),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SalimSurfaceBorder, RoundedCornerShape(14.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = SalimBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Open Ubaid on your host phone and select 'Show Pairing QR' to link once.",
                            fontSize = 13.sp,
                            color = SalimNearBlack,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { showManualDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = SalimBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enter Pairing Code Manually",
                        color = SalimBlue,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Manual Entry Dialog
        if (showManualDialog) {
            ManualPairingDialog(
                onDismiss = { showManualDialog = false },
                onSubmit = { input ->
                    showManualDialog = false
                    val success = onPairWithPayload(input)
                    if (!success) {
                        scanError = "The entered code could not be verified. Please check and try again."
                        isScanningActive = false
                    }
                }
            )
        }
    }
}

@Composable
private fun ManualPairingDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Manual Pairing Code",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = SalimNearBlack
            )
        },
        text = {
            Column {
                Text(
                    text = "Paste the JSON pairing payload or host token displayed in Ubaid settings:",
                    fontSize = 13.sp,
                    color = SalimSecondaryText,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = textInput,
                    onValueChange = {
                        textInput = it
                        inputError = false
                    },
                    isError = inputError,
                    placeholder = { Text("e.g. {\"hostId\": \"ubaidd-host\", \"secret\": \"...\"}") },
                    shape = RoundedCornerShape(10.dp),
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                if (inputError) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Please enter a valid pairing code or JSON payload.",
                        fontSize = 12.sp,
                        color = SalimStateError
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (textInput.isBlank()) {
                        inputError = true
                    } else {
                        onSubmit(textInput.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SalimBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Link Device", color = SalimWhite, fontWeight = FontWeight.SemiBold)
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
