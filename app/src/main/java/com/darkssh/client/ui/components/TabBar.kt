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
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
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
 * Uses SVG icons from candy-icons for major distros, fallback to text badges for others.
 */
@Composable
private fun OsIcon(osType: OsType, modifier: Modifier = Modifier) {
    val context = LocalPlatformContext.current
    
    // Map OS types to SVG icon paths
    val iconPath = when (osType) {
        OsType.ARCH -> "file:///android_asset/icons/ic_os_arch.svg"
        OsType.UBUNTU -> "file:///android_asset/icons/ic_os_ubuntu.svg"
        OsType.DEBIAN -> "file:///android_asset/icons/ic_os_debian.svg"
        OsType.FEDORA -> "file:///android_asset/icons/ic_os_fedora.svg"
        OsType.ALPINE -> "file:///android_asset/icons/ic_os_alpine.svg"
        OsType.CENTOS, OsType.REDHAT -> "file:///android_asset/icons/ic_os_fedora.svg" // Use Fedora as fallback
        OsType.LINUX -> "file:///android_asset/icons/ic_os_linux.svg"
        else -> null
    }
    
    when {
        iconPath != null -> {
            // Use SVG icon for major distros
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(iconPath)
                    .decoderFactory(SvgDecoder.Factory())
                    .build(),
                contentDescription = osType.name,
                modifier = modifier
            )
        }
        // Fallback to old vector icons for distros without SVG
        osType == OsType.CENTOS -> CentOSIcon(modifier = modifier)
        osType == OsType.REDHAT -> CentOSIcon(modifier = modifier, tint = androidx.compose.ui.graphics.Color(0xFFEE0000))
        // Text badges for less common distros
        osType == OsType.GENTOO -> Text(
            text = "G",
            modifier = modifier,
            style = MaterialTheme.typography.labelMedium,
            color = androidx.compose.ui.graphics.Color(0xFF54487A)
        )
        osType == OsType.FREEBSD -> Text(
            text = "FB",
            modifier = modifier,
            style = MaterialTheme.typography.labelSmall,
            color = androidx.compose.ui.graphics.Color(0xFFAB2B28)
        )
        osType == OsType.OPENBSD -> Text(
            text = "OB",
            modifier = modifier,
            style = MaterialTheme.typography.labelSmall,
            color = androidx.compose.ui.graphics.Color(0xFFF2CA30)
        )
        osType == OsType.SUSE -> Text(
            text = "S",
            modifier = modifier,
            style = MaterialTheme.typography.labelMedium,
            color = androidx.compose.ui.graphics.Color(0xFF73BA25)
        )
        osType == OsType.ALMA -> Text(
            text = "AL",
            modifier = modifier,
            style = MaterialTheme.typography.labelSmall,
            color = androidx.compose.ui.graphics.Color(0xFF0F4266)
        )
        osType == OsType.ROCKY -> Text(
            text = "R",
            modifier = modifier,
            style = MaterialTheme.typography.labelMedium,
            color = androidx.compose.ui.graphics.Color(0xFF10B981)
        )
        osType == OsType.AMAZON -> Text(
            text = "AWS",
            modifier = modifier,
            style = MaterialTheme.typography.labelSmall,
            color = androidx.compose.ui.graphics.Color(0xFFFF9900)
        )
        osType == OsType.RASPBIAN -> Text(
            text = "π",
            modifier = modifier,
            style = MaterialTheme.typography.labelMedium,
            color = androidx.compose.ui.graphics.Color(0xFFC51A4A)
        )
        osType == OsType.OSX -> Text(
            text = "⌘",
            modifier = modifier,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        osType == OsType.ANDROID -> Text(
            text = "🤖",
            modifier = modifier,
            style = MaterialTheme.typography.labelMedium
        )
        osType == OsType.WINDOWS -> Text(
            text = "⊞",
            modifier = modifier,
            style = MaterialTheme.typography.labelMedium,
            color = androidx.compose.ui.graphics.Color(0xFF0078D4)
        )
        osType == OsType.MIKROTIK -> Text(
            text = "MT",
            modifier = modifier,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        osType == OsType.NETBSD -> Text(
            text = "NB",
            modifier = modifier,
            style = MaterialTheme.typography.labelSmall,
            color = androidx.compose.ui.graphics.Color(0xFFFF6600)
        )
        // Show pulsing loading indicator for UNKNOWN (detecting in progress)
        osType == OsType.UNKNOWN -> OsDetectingIcon(modifier = modifier)
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
                            // Show OS icon for SSH terminals, remote folder icon for SFTP
                            when (tab.type) {
                                TabType.SSH_TERMINAL -> OsIcon(
                                    osType = osType,
                                    modifier = Modifier.size(18.dp)
                                )
                                TabType.SFTP_BROWSER -> {
                                    val context = LocalPlatformContext.current
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data("file:///android_asset/icons/ic_folder_remote.svg")
                                            .decoderFactory(SvgDecoder.Factory())
                                            .build(),
                                        contentDescription = "SFTP",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
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
