package com.darkssh.client.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkssh.client.data.entity.Tab as TabEntity
import com.darkssh.client.data.entity.TabType
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabBar(
    tabs: List<TabEntity>,
    pagerState: PagerState,
    onAddTab: () -> Unit,
    onCloseTab: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = Modifier.weight(1f),
            edgePadding = 0.dp,
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector =
                                    when (tab.type) {
                                        TabType.SSH_TERMINAL -> Icons.Default.Computer
                                        TabType.SFTP_BROWSER -> Icons.Default.Folder
                                    },
                                contentDescription = null,
                            )
                            Text(
                                tab.title.ifEmpty {
                                    when (tab.type) {
                                        TabType.SSH_TERMINAL -> "Terminal"
                                        TabType.SFTP_BROWSER -> "SFTP"
                                    }
                                },
                            )
                            IconButton(
                                onClick = { onCloseTab(tab.id) },
                                modifier = Modifier.padding(0.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                )
                            }
                        }
                    },
                )
            }
        }

        IconButton(onClick = onAddTab) {
            Icon(Icons.Default.Add, contentDescription = "Add tab")
        }
    }
}
