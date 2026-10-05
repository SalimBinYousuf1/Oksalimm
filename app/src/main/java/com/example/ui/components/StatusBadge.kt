package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectionState
import com.example.ui.theme.SalimNearBlack
import com.example.ui.theme.SalimSecondaryText
import com.example.ui.theme.SalimStateConnected
import com.example.ui.theme.SalimStateConnectedBg
import com.example.ui.theme.SalimStateConnecting
import com.example.ui.theme.SalimStateConnectingBg
import com.example.ui.theme.SalimStateError
import com.example.ui.theme.SalimStateErrorBg
import com.example.ui.theme.SalimStateReconnecting
import com.example.ui.theme.SalimStateReconnectingBg
import com.example.ui.theme.SalimStateWaiting
import com.example.ui.theme.SalimStateWaitingBg

data class ConnectionVisuals(
    val icon: ImageVector,
    val primaryColor: Color,
    val backgroundColor: Color,
    val isProgress: Boolean = false
)

fun getVisualsForState(state: ConnectionState): ConnectionVisuals {
    return when (state) {
        is ConnectionState.Connected -> ConnectionVisuals(
            icon = Icons.Default.CheckCircle,
            primaryColor = SalimStateConnected,
            backgroundColor = SalimStateConnectedBg
        )
        is ConnectionState.Connecting -> ConnectionVisuals(
            icon = Icons.Default.Sync,
            primaryColor = SalimStateConnecting,
            backgroundColor = SalimStateConnectingBg,
            isProgress = true
        )
        is ConnectionState.Reconnecting -> ConnectionVisuals(
            icon = Icons.Default.Refresh,
            primaryColor = SalimStateReconnecting,
            backgroundColor = SalimStateReconnectingBg,
            isProgress = true
        )
        is ConnectionState.WaitingOnUbaid -> ConnectionVisuals(
            icon = Icons.Default.HourglassTop,
            primaryColor = SalimStateWaiting,
            backgroundColor = SalimStateWaitingBg
        )
        is ConnectionState.UbaidUnreachable -> ConnectionVisuals(
            icon = Icons.Default.CloudOff,
            primaryColor = SalimStateError,
            backgroundColor = SalimStateErrorBg
        )
        is ConnectionState.SetupIncomplete -> ConnectionVisuals(
            icon = Icons.Default.ErrorOutline,
            primaryColor = SalimStateError,
            backgroundColor = SalimStateErrorBg
        )
        is ConnectionState.NotPaired -> ConnectionVisuals(
            icon = Icons.Default.LinkOff,
            primaryColor = SalimSecondaryText,
            backgroundColor = Color(0xFFF3F4F6)
        )
        is ConnectionState.Disconnected -> ConnectionVisuals(
            icon = Icons.Default.LinkOff,
            primaryColor = SalimSecondaryText,
            backgroundColor = Color(0xFFF3F4F6)
        )
    }
}

@Composable
fun StatusBadge(
    state: ConnectionState,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val visuals = getVisualsForState(state)
    val animatedBg by animateColorAsState(targetValue = visuals.backgroundColor, label = "badge_bg")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(animatedBg)
            .border(1.dp, visuals.primaryColor.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 4.dp else 6.dp)
    ) {
        if (visuals.isProgress) {
            CircularProgressIndicator(
                color = visuals.primaryColor,
                strokeWidth = 2.dp,
                modifier = Modifier.size(if (compact) 12.dp else 14.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(if (compact) 8.dp else 10.dp)
                    .clip(CircleShape)
                    .background(visuals.primaryColor)
            )
        }

        Spacer(modifier = Modifier.width(if (compact) 6.dp else 8.dp))

        Text(
            text = state.label,
            color = visuals.primaryColor,
            fontSize = if (compact) 12.sp else 13.sp,
            fontWeight = FontWeight.SemiBold
        )

        if (!compact && state is ConnectionState.Connected) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "• ${state.pingMs}ms",
                color = SalimSecondaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
