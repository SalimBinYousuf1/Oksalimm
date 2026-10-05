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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.SalimApplication
import com.example.ui.theme.SalimBlue
import com.example.ui.theme.SalimNearBlack
import com.example.ui.theme.SalimSecondaryText
import com.example.ui.theme.SalimStateConnected
import com.example.ui.theme.SalimStateError
import com.example.ui.theme.SalimStateErrorBg
import com.example.ui.theme.SalimSurfaceBorder
import com.example.ui.theme.SalimSurfaceCard
import com.example.ui.theme.SalimWhite

@Composable
fun SetupErrorScreen(
    onRetrySetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SalimWhite)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(SalimStateErrorBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = SalimStateError,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Setup Incomplete",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = SalimNearBlack,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Firebase configuration is missing or invalid. Salim requires Firebase Firestore for peer-to-peer WebRTC signaling.",
                fontSize = 14.sp,
                color = SalimSecondaryText,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Diagnostic Card
            Card(
                colors = CardDefaults.cardColors(containerColor = SalimSurfaceCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SalimSurfaceBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Diagnostics & Configuration",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = SalimNearBlack
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ConfigRow(
                        label = "Package Name",
                        value = "com.salim.ctrl",
                        isOk = true
                    )

                    ConfigRow(
                        label = "Firebase Project",
                        value = "salim-x-ubaid",
                        isOk = SalimApplication.isFirebaseConfigured
                    )

                    ConfigRow(
                        label = "WebRTC Engine",
                        value = if (SalimApplication.isWebRtcAvailable) "Initialized" else "Native loader error",
                        isOk = SalimApplication.isWebRtcAvailable
                    )

                    if (SalimApplication.firebaseErrorMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Error detail: ${SalimApplication.firebaseErrorMessage}",
                            fontSize = 12.sp,
                            color = SalimStateError,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onRetrySetup,
                colors = ButtonDefaults.buttonColors(containerColor = SalimBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = SalimWhite)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry Setup", fontWeight = FontWeight.SemiBold, color = SalimWhite)
            }
        }
    }
}

@Composable
private fun ConfigRow(
    label: String,
    value: String,
    isOk: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 12.sp, color = SalimSecondaryText)
            Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = SalimNearBlack)
        }
        Icon(
            imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = if (isOk) SalimStateConnected else SalimStateError,
            modifier = Modifier.size(18.dp)
        )
    }
}
