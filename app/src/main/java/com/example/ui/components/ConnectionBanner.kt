package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectionState
import com.example.ui.theme.SalimNearBlack
import com.example.ui.theme.SalimSecondaryText
import com.example.ui.theme.SalimWhite

@Composable
fun ConnectionBanner(
    state: ConnectionState,
    onRetryClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state !is ConnectionState.Connected,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        val visuals = getVisualsForState(state)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(visuals.backgroundColor)
                .border(1.dp, visuals.primaryColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (visuals.isProgress) {
                    CircularProgressIndicator(
                        color = visuals.primaryColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = visuals.icon,
                        contentDescription = state.label,
                        tint = visuals.primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = state.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = visuals.primaryColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = state.detail,
                        fontSize = 12.sp,
                        color = SalimSecondaryText,
                        lineHeight = 16.sp
                    )
                }
            }

            if (state is ConnectionState.Disconnected ||
                state is ConnectionState.UbaidUnreachable ||
                state is ConnectionState.WaitingOnUbaid
            ) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onRetryClicked,
                    colors = ButtonDefaults.buttonColors(containerColor = visuals.primaryColor),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SalimWhite)
                }
            }
        }
    }
}
