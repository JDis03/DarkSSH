package com.darkssh.client.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

// ─── Connection status dot ────────────────────────────────────────────────────

@Composable
private fun ConnectionDot(
    isConnected: Boolean,
    isDisconnected: Boolean,
    modifier: Modifier = Modifier,
) {
    val dotColor: Color
    val alpha: Float

    when {
        isConnected -> {
            dotColor = Color(0xFF4CAF50)
            alpha = 1f
        }
        isDisconnected -> {
            dotColor = MaterialTheme.colorScheme.error
            alpha = 1f
        }
        else -> {
            dotColor = Color.Gray
            val transition = rememberInfiniteTransition(label = "connecting")
            alpha = transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(700, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "pulse",
            ).value
        }
    }

    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(dotColor.copy(alpha = alpha)),
    )
}

// ─── OS icon (SVG distros + text fallbacks) ───────────────────────────────────

@Composable
private fun OsDetectingIcon(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "osDetecting")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alphaPulse",
    )
    androidx.compose.foundation.Canvas(modifier = modifier.size(18.dp)) {
        drawCircle(
            color = androidx.compose.ui.graphics.Color.Gray,
            radius = size.width * 0.3f,
            alpha = alpha,
        )
    }
}

@Composable
fun OsIcon(osType: OsType, modifier: Modifier = Modifier) {
    val context = LocalPlatformContext.current
    val iconPath = when (osType) {
        OsType.ARCH    -> "file:///android_asset/icons/ic_os_arch.svg"
        OsType.UBUNTU  -> "file:///android_asset/icons/ic_os_ubuntu.svg"
        OsType.DEBIAN  -> "file:///android_asset/icons/ic_os_debian.svg"
        OsType.FEDORA  -> "file:///android_asset/icons/ic_os_fedora.svg"
        OsType.ALPINE  -> "file:///android_asset/icons/ic_os_alpine.svg"
        OsType.CENTOS, OsType.REDHAT -> "file:///android_asset/icons/ic_os_fedora.svg"
        OsType.LINUX   -> "file:///android_asset/icons/ic_os_linux.svg"
        else           -> null
    }
    when {
        iconPath != null -> AsyncImage(
            model = ImageRequest.Builder(context)
                .data(iconPath).decoderFactory(SvgDecoder.Factory()).build(),
            contentDescription = osType.name,
            modifier = modifier,
        )
        osType == OsType.CENTOS   -> CentOSIcon(modifier = modifier)
        osType == OsType.REDHAT   -> CentOSIcon(modifier = modifier, tint = Color(0xFFEE0000))
        osType == OsType.GENTOO   -> Text("G",  modifier = modifier, style = MaterialTheme.typography.labelMedium, color = Color(0xFF54487A))
        osType == OsType.FREEBSD  -> Text("FB", modifier = modifier, style = MaterialTheme.typography.labelSmall,  color = Color(0xFFAB2B28))
        osType == OsType.OPENBSD  -> Text("OB", modifier = modifier, style = MaterialTheme.typography.labelSmall,  color = Color(0xFFF2CA30))
        osType == OsType.SUSE     -> Text("S",  modifier = modifier, style = MaterialTheme.typography.labelMedium, color = Color(0xFF73BA25))
        osType == OsType.ALMA     -> Text("AL", modifier = modifier, style = MaterialTheme.typography.labelSmall,  color = Color(0xFF0F4266))
        osType == OsType.ROCKY    -> Text("R",  modifier = modifier, style = MaterialTheme.typography.labelMedium, color = Color(0xFF10B981))
        osType == OsType.AMAZON   -> Text("AWS",modifier = modifier, style = MaterialTheme.typography.labelSmall,  color = Color(0xFFFF9900))
        osType == OsType.RASPBIAN -> Text("π",  modifier = modifier, style = MaterialTheme.typography.labelMedium, color = Color(0xFFC51A4A))
        osType == OsType.OSX      -> Text("⌘",  modifier = modifier, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        osType == OsType.ANDROID  -> Text("🤖", modifier = modifier, style = MaterialTheme.typography.labelMedium)
        osType == OsType.WINDOWS  -> Text("⊞",  modifier = modifier, style = MaterialTheme.typography.labelMedium, color = Color(0xFF0078D4))
        osType == OsType.MIKROTIK -> Text("MT", modifier = modifier, style = MaterialTheme.typography.labelSmall,  color = MaterialTheme.colorScheme.onSurfaceVariant)
        osType == OsType.NETBSD   -> Text("NB", modifier = modifier, style = MaterialTheme.typography.labelSmall,  color = Color(0xFFFF6600))
        osType == OsType.UNKNOWN  -> OsDetectingIcon(modifier = modifier)
    }
}

// ─── TabBar ───────────────────────────────────────────────────────────────────

private const val VIVALDI_THRESHOLD = 3  // collapse when tabs > this

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabBar(
    tabs: List<TabEntity>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    terminalService: TerminalService?,
    onAddTab: () -> Unit,
    onCloseTab: (String) -> Unit,
    onCloseOthers: (String) -> Unit = {},
    onCloseAll: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val bridges = terminalService?.bridges?.collectAsState()?.value ?: emptyList()
    val collapsed = tabs.size > VIVALDI_THRESHOLD
    val scrollState = rememberScrollState()
    val selectedIndex = pagerState.currentPage.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))

    // Auto-scroll so the selected tab is always visible when collapsed
    LaunchedEffect(selectedIndex, collapsed) {
        if (collapsed) {
            // Approximate scroll: each collapsed tab ~44dp, selected tab ~160dp
            val collapsedPx = 44 * scrollState.maxValue / (tabs.size * 44 + 160).coerceAtLeast(1)
            scrollState.animateScrollTo((selectedIndex * collapsedPx).toInt())
        }
    }

    Row(
        modifier = modifier
            .height(52.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── Tab strip ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                key(tab.id) {
                    val bridge       = bridges.find { it.tabId == tab.id }
                    val osType       by bridge?.osType?.collectAsState()       ?: remember { mutableStateOf(OsType.UNKNOWN) }
                    val isConnected  by bridge?.isConnected?.collectAsState()  ?: remember { mutableStateOf(false) }
                    val isDisconn    by bridge?.isDisconnected?.collectAsState() ?: remember { mutableStateOf(false) }
                    var showMenu     by remember { mutableStateOf(false) }

                    val isSelected  = index == selectedIndex
                    // Vivaldi: selected = expanded (text visible), others = icon-only
                    val showText    = !collapsed || isSelected
                    // Animate tab width fraction: 1f=full, 0f=icon-only
                    val widthFraction by animateFloatAsState(
                        targetValue = if (showText) 1f else 0f,
                        animationSpec = tween(200),
                        label = "tabWidth",
                    )

                    SingleTab(
                        tab         = tab,
                        isSelected  = isSelected,
                        showText    = showText,
                        widthFraction = widthFraction,
                        collapsed   = collapsed,
                        osType      = osType,
                        isConnected = isConnected,
                        isDisconn   = isDisconn,
                        showMenu    = showMenu,
                        tabCount    = tabs.size,
                        onClick     = { scope.launch { pagerState.animateScrollToPage(index) } },
                        onLongClick = { showMenu = true },
                        onDismissMenu = { showMenu = false },
                        onClose     = { onCloseTab(tab.id) },
                        onCloseOthers = { onCloseOthers(tab.id) },
                        onCloseAll  = onCloseAll,
                    )
                }
            }
        }

        // ── Add button ───────────────────────────────────────────────────────
        IconButton(
            onClick = onAddTab,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "New connection",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ─── Single tab item ──────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SingleTab(
    tab: TabEntity,
    isSelected: Boolean,
    showText: Boolean,
    widthFraction: Float,
    collapsed: Boolean,
    osType: OsType,
    isConnected: Boolean,
    isDisconn: Boolean,
    showMenu: Boolean,
    tabCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismissMenu: () -> Unit,
    onClose: () -> Unit,
    onCloseOthers: () -> Unit,
    onCloseAll: () -> Unit,
) {
    val selectedBg  = MaterialTheme.colorScheme.secondaryContainer
    val normalBg    = Color.Transparent
    val selectedText = MaterialTheme.colorScheme.onSecondaryContainer
    val normalText   = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            // collapsed: icon-only ~44dp, selected: at least 120dp; normal mode: unconstrained
            .then(
                if (collapsed) {
                    if (isSelected) Modifier.widthIn(min = 120.dp, max = 180.dp)
                    else Modifier.width(44.dp)
                } else {
                    Modifier.widthIn(min = 80.dp, max = 160.dp)
                }
            )
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) selectedBg else normalBg)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            // Icon with connection dot
            Box {
                when (tab.type) {
                    TabType.SSH_TERMINAL -> OsIcon(
                        osType = osType,
                        modifier = Modifier.size(18.dp),
                    )
                    TabType.SFTP_BROWSER -> {
                        val context = LocalPlatformContext.current
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("file:///android_asset/icons/ic_folder_remote.svg")
                                .decoderFactory(SvgDecoder.Factory()).build(),
                            contentDescription = "SFTP",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (tab.type == TabType.SSH_TERMINAL) {
                    ConnectionDot(
                        isConnected = isConnected,
                        isDisconnected = isDisconn,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp),
                    )
                }
            }

            // Title — only when expanded
            if (showText) {
                Text(
                    text = tab.title.ifEmpty {
                        when (tab.type) {
                            TabType.SSH_TERMINAL -> "Terminal"
                            TabType.SFTP_BROWSER -> "SFTP"
                        }
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) selectedText else normalText,
                    modifier = Modifier.weight(1f),
                )
                // No X button — close via long-press menu
            }
        }

        // Long-press context menu
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = onDismissMenu,
        ) {
            DropdownMenuItem(
                text = { Text("Close") },
                onClick = { onDismissMenu(); onClose() },
            )
            if (tabCount > 1) {
                DropdownMenuItem(
                    text = { Text("Close others") },
                    onClick = { onDismissMenu(); onCloseOthers() },
                )
            }
            DropdownMenuItem(
                text = { Text("Close all") },
                onClick = { onDismissMenu(); onCloseAll() },
            )
        }
    }

    // Divider between tabs
    if (!isSelected) {
        Spacer(
            modifier = Modifier
                .width(1.dp)
                .height(28.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        )
    }
}
