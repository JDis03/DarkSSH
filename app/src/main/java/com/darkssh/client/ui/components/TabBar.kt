package com.darkssh.client.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.darkssh.client.data.entity.Tab as TabEntity
import com.darkssh.client.data.entity.TabType
import com.darkssh.client.data.model.OsType
import com.darkssh.client.service.TerminalService
import kotlinx.coroutines.launch

/**
 * Loading indicator for OS detection (pulsing dot animation)
 */
@Composable
private fun OsDetectingIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "osDetecting")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alphaPulse"
    )
    
    androidx.compose.foundation.Canvas(modifier = modifier.size(18.dp)) {
        // Pulsing dot to indicate detection in progress
        drawCircle(
            color = androidx.compose.ui.graphics.Color.Gray,
            radius = size.width * 0.3f,
            alpha = alpha
        )
    }
}

/**
 * Get icon for OS type.
 * Uses vector-based icons inspired by Vivaldi's clean design.
 */
@Composable
private fun OsIcon(osType: OsType, modifier: Modifier = Modifier) {
    when (osType) {
        OsType.ARCH -> ArchLinuxIcon(modifier = modifier)
        OsType.UBUNTU -> UbuntuIcon(modifier = modifier)
        OsType.DEBIAN -> DebianIcon(modifier = modifier)
        OsType.FEDORA -> FedoraIcon(modifier = modifier)
        OsType.ALPINE -> AlpineIcon(modifier = modifier)
        OsType.CENTOS -> CentOSIcon(modifier = modifier)
        OsType.REDHAT -> CentOSIcon(modifier = modifier, tint = androidx.compose.ui.graphics.Color(0xFFEE0000))
        OsType.LINUX -> GenericLinuxIcon(modifier = modifier)
        
        // Fallback to text badges for less common distros
        OsType.GENTOO -> Text(
            text = "G",
            modifier = modifier,
            style = MaterialTheme.typography.labelMedium,
            color = androidx.compose.ui.graphics.Color(0xFF54487A)
        )
        OsType.FREEBSD -> Text(
            text = "FB",
            modifier = modifier,
            style = MaterialTheme.typography.labelSmall,
            color = androidx.compose.ui.graphics.Color(0xFFAB2B28)
        )
        OsType.OPENBSD -> Text(
            text = "OB",
            modifier = modifier,
            style = MaterialTheme.typography.labelSmall,
            color = androidx.compose.ui.graphics.Color(0xFFF2CA30)
        )
        OsType.SUSE -> Text(
            text = "S",
            modifier = modifier,
            style = MaterialTheme.typography.labelMedium,
            color = androidx.compose.ui.graphics.Color(0xFF73BA25)
        )
        OsType.ALMA -> Text(
            text = "AL",
            modifier = modifier,
            style = MaterialTheme.typography.labelSmall,
            color = androidx.compose.ui.graphics.Color(0xFF0F4266)
        )
        OsType.ROCKY -> Text(
            text = "R",
            modifier = modifier,
            style = MaterialTheme.typography.labelMedium,
            color = androidx.compose.ui.graphics.Color(0xFF10B981)
        )
        OsType.AMAZON -> Text(
            text = "AWS",
            modifier = modifier,
            style = MaterialTheme.typography.labelSmall,
            color = androidx.compose.ui.graphics.Color(0xFFFF9900)
        )
        OsType.RASPBIAN -> Text(
            text = "π",
            modifier = modifier,
            style = MaterialTheme.typography.labelMedium,
            color = androidx.compose.ui.graphics.Color(0xFFC51A4A)
        )
        OsType.OSX -> Text(
            text = "⌘",
            modifier = modifier,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OsType.ANDROID -> Text(
            text = "🤖",
            modifier = modifier,
            style = MaterialTheme.typography.labelMedium
        )
        OsType.WINDOWS -> Text(
            text = "⊞",
            modifier = modifier,
            style = MaterialTheme.typography.labelMedium,
            color = androidx.compose.ui.graphics.Color(0xFF0078D4)
        )
        OsType.MIKROTIK -> Text(
            text = "MT",
            modifier = modifier,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OsType.NETBSD -> Text(
            text = "NB",
            modifier = modifier,
            style = MaterialTheme.typography.labelSmall,
            color = androidx.compose.ui.graphics.Color(0xFFFF6600)
        )
        // Show pulsing loading indicator for UNKNOWN (detecting in progress)
        OsType.UNKNOWN -> OsDetectingIcon(modifier = modifier)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabBar(
    tabs: List<TabEntity>,
    pagerState: PagerState,
    terminalService: TerminalService?,
    onAddTab: () -> Unit,
    onCloseTab: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val bridges = terminalService?.bridges?.collectAsState()?.value ?: emptyList()

    Row(
        modifier = modifier
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage.coerceIn(0, tabs.size - 1).takeIf { tabs.isNotEmpty() } ?: 0,
            modifier = Modifier.weight(1f),
            edgePadding = 4.dp,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            divider = {},
        ) {
            tabs.forEachIndexed { index, tab ->
                // Find corresponding bridge to get detected OS
                val bridge = bridges.find { it.tabId == tab.id }
                val osType by bridge?.osType?.collectAsState() ?: remember { mutableStateOf(OsType.UNKNOWN) }
                
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            // Show OS icon for SSH terminals, generic icon for SFTP
                            when (tab.type) {
                                TabType.SSH_TERMINAL -> OsIcon(
                                    osType = osType,
                                    modifier = Modifier.size(18.dp)
                                )
                                TabType.SFTP_BROWSER -> Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            
                            Text(
                                text = tab.title.ifEmpty {
                                    when (tab.type) {
                                        TabType.SSH_TERMINAL -> "Terminal"
                                        TabType.SFTP_BROWSER -> "SFTP"
                                    }
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            IconButton(
                                onClick = { 
                                    try {
                                        onCloseTab(tab.id)
                                    } catch (e: Exception) {
                                        timber.log.Timber.e(e, "Error closing tab ${tab.id}")
                                    }
                                },
                                modifier = Modifier.size(20.dp),
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    },
                )
            }
        }

        IconButton(
            onClick = onAddTab,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add tab",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
