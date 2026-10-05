package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ConnectionState
import com.example.ui.screens.FilesScreen
import com.example.ui.screens.MirrorScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.PairingScreen
import com.example.ui.screens.SetupErrorScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatusScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SalimBlue
import com.example.ui.theme.SalimSecondaryText
import com.example.ui.theme.SalimSurfaceCard
import com.example.ui.theme.SalimWhite
import com.example.viewmodel.SalimViewModel

data class NavTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {

    private val viewModel: SalimViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
                val pairingInfo by viewModel.pairingInfo.collectAsStateWithLifecycle()
                val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
                val hostStatus by viewModel.hostStatus.collectAsStateWithLifecycle()
                val remoteFiles by viewModel.remoteFiles.collectAsStateWithLifecycle()
                val currentPath by viewModel.currentFolderPath.collectAsStateWithLifecycle()
                val transfers by viewModel.fileTransfers.collectAsStateWithLifecycle()
                val notifications by viewModel.notifications.collectAsStateWithLifecycle()
                val isDirectP2p by viewModel.isDirectP2p.collectAsStateWithLifecycle()
                val lastConnectedTime by viewModel.lastConnectedTime.collectAsStateWithLifecycle()

                when {
                    connectionState is ConnectionState.SetupIncomplete -> {
                        SetupErrorScreen(
                            onRetrySetup = { viewModel.checkInitialState() }
                        )
                    }

                    pairingInfo == null || connectionState is ConnectionState.NotPaired -> {
                        PairingScreen(
                            onPairWithPayload = { payload ->
                                viewModel.pairWithPayload(payload)
                            }
                        )
                    }

                    else -> {
                        // Handle back button on sub-tabs
                        if (selectedTab != 0) {
                            BackHandler {
                                viewModel.selectTab(0)
                            }
                        }

                        val tabs = listOf(
                            NavTab("Mirror", Icons.Default.Tv, Icons.Outlined.Tv, "nav_mirror"),
                            NavTab("Files", Icons.Default.Folder, Icons.Outlined.Folder, "nav_files"),
                            NavTab("Alerts", Icons.Default.Notifications, Icons.Outlined.Notifications, "nav_alerts"),
                            NavTab("Status", Icons.Default.Speed, Icons.Outlined.Speed, "nav_status"),
                            NavTab("Settings", Icons.Default.Settings, Icons.Outlined.Settings, "nav_settings")
                        )

                        Scaffold(
                            bottomBar = {
                                NavigationBar(
                                    containerColor = SalimWhite,
                                    tonalElevation = 0.dp,
                                    modifier = Modifier
                                        .background(SalimWhite)
                                        .navigationBarsPadding()
                                ) {
                                    tabs.forEachIndexed { index, tab ->
                                        val isSelected = selectedTab == index
                                        NavigationBarItem(
                                            selected = isSelected,
                                            onClick = { viewModel.selectTab(index) },
                                            icon = {
                                                if (index == 2 && notifications.isNotEmpty()) {
                                                    BadgedBox(badge = {
                                                        Badge(
                                                            containerColor = SalimBlue,
                                                            contentColor = SalimWhite
                                                        ) {
                                                            Text(notifications.size.toString())
                                                        }
                                                    }) {
                                                        Icon(
                                                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                                            contentDescription = tab.title,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }
                                                } else {
                                                    Icon(
                                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                                        contentDescription = tab.title,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = tab.title,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = SalimBlue,
                                                selectedTextColor = SalimBlue,
                                                unselectedIconColor = SalimSecondaryText,
                                                unselectedTextColor = SalimSecondaryText,
                                                indicatorColor = SalimSurfaceCard
                                            ),
                                            modifier = Modifier.testTag(tab.testTag)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                AnimatedContent(
                                    targetState = selectedTab,
                                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                                    label = "tab_switch"
                                ) { targetTab ->
                                    when (targetTab) {
                                        0 -> MirrorScreen(
                                            connectionState = connectionState,
                                            pairingInfo = pairingInfo,
                                            hostStatus = hostStatus,
                                            onAttachRenderer = { renderer -> viewModel.attachRenderer(renderer) },
                                            onDetachRenderer = { viewModel.detachRenderer() },
                                            onSendTap = { x, y -> viewModel.sendTap(x, y) },
                                            onSendTouchDown = { x, y -> viewModel.sendTouchDown(x, y) },
                                            onSendTouchMove = { x, y -> viewModel.sendTouchMove(x, y) },
                                            onSendTouchUp = { x, y -> viewModel.sendTouchUp(x, y) },
                                            onSendNavigationKey = { key -> viewModel.sendNavigationKey(key) },
                                            onSendText = { text -> viewModel.sendText(text) },
                                            onReconnectClicked = {
                                                pairingInfo?.let { viewModel.connectToHost(it) }
                                            }
                                        )

                                        1 -> FilesScreen(
                                            connectionState = connectionState,
                                            currentPath = currentPath,
                                            files = remoteFiles,
                                            transfers = transfers,
                                            onNavigateDirectory = { path -> viewModel.requestDirectory(path) },
                                            onNavigateUp = { viewModel.navigateUp() },
                                            onDownloadFile = { item -> viewModel.requestFileDownload(item) },
                                            onUploadFile = { uri, ctx, dest -> viewModel.uploadFileFromUri(uri, ctx, dest) },
                                            onRefresh = { viewModel.requestDirectory(currentPath) },
                                            onRetryConnection = {
                                                pairingInfo?.let { viewModel.connectToHost(it) }
                                            }
                                        )

                                        2 -> NotificationsScreen(
                                            connectionState = connectionState,
                                            notifications = notifications,
                                            onClearAll = { viewModel.clearNotifications() },
                                            onDismissNotification = { id -> viewModel.dismissNotification(id) },
                                            onRetryConnection = {
                                                pairingInfo?.let { viewModel.connectToHost(it) }
                                            }
                                        )

                                        3 -> StatusScreen(
                                            connectionState = connectionState,
                                            pairingInfo = pairingInfo,
                                            hostStatus = hostStatus,
                                            onRetryConnection = {
                                                pairingInfo?.let { viewModel.connectToHost(it) }
                                            }
                                        )

                                        4 -> SettingsScreen(
                                            connectionState = connectionState,
                                            pairingInfo = pairingInfo,
                                            lastConnectedTime = lastConnectedTime,
                                            isDirectP2p = isDirectP2p,
                                            onReconnect = {
                                                pairingInfo?.let { viewModel.connectToHost(it) }
                                            },
                                            onUnpair = { viewModel.unpair() }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
