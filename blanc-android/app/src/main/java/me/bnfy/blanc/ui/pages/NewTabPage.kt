package me.bnfy.blanc.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.bnfy.blanc.BlancApplication
import me.bnfy.blanc.R
import me.bnfy.blanc.storage.Repository
import me.bnfy.blanc.tab.TabManager

/**
 * New Tab page - shows favorites, recent tabs, search bar.
 */
@Composable
fun NewTabPage(
    tabManager: TabManager,
    repository: Repository = BlancApplication.getInstance().repository,
    onNavigate: (String) -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onOpenBookmarks: (() -> Unit)? = null,
    onOpenHistory: (() -> Unit)? = null
) {
    val dbFavorites by repository.favoriteDao.getAllFlow("personal").collectAsState(initial = emptyList())
    val dbHistory by repository.historyDao.getHistoryPageFlow("personal", 8, 0, null).collectAsState(initial = emptyList())

    val favorites = if (dbFavorites.isNotEmpty()) {
        dbFavorites.map { FavoriteItem(it.id, it.title, it.url, it.favicon) }
    } else {
        getDefaultFavorites()
    }

    val recentTabs = if (dbHistory.isNotEmpty()) {
        dbHistory.map { RecentTabItem(it.id.toString(), it.title?.ifBlank { it.url } ?: it.url, it.url, it.visitTime) }
    } else {
        getRecentTabs()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 88.dp),
            verticalArrangement = Arrangement.Top
        ) {
            item {
                // Search bar
                SearchBar(onSearch = onNavigate)
            }

            item {
                // Adblock Privacy Shield Banner (Bowser tally & protection indicator)
                val blockedCount = BlancApplication.getInstance().adblockEngine.getTodayBlocked()
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_shield),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Blanc Shield Active",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (blockedCount > 0) "$blockedCount trackers & ads blocked today" else "Protecting your device at the network layer",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Favorites
            if (favorites.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Favorites",
                        onClickAll = { onOpenBookmarks?.invoke() }
                    )
                }
                items(favorites, key = { "fav_${it.id}" }) { item ->
                    FavoriteCard(item = item, onClick = { onNavigate(item.url) })
                }
            }

            // Recent tabs
            if (recentTabs.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Recent visits",
                        onClickAll = { onOpenHistory?.invoke() }
                    )
                }
                items(recentTabs, key = { "recent_${it.id}" }) { item ->
                    RecentTabCard(item = item, onClick = { onNavigate(item.url) })
                }
            }

            item {
                // Footer actions
                FooterActions(
                    onNewTab = onNewTab,
                    onNewPrivateTab = onNewPrivateTab
                )
            }
        }
    }
}

@Composable
fun SearchBar(onSearch: (String) -> Unit) {
    val query = remember { mutableStateOf("") }
    val isFocused = remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        TextField(
            value = query.value,
            onValueChange = { query.value = it },
            placeholder = { Text("Search or enter address") },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = { onSearch(query.value) }
            ),
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            trailingIcon = {
                if (query.value.isNotBlank()) {
                    IconButton(onClick = { query.value = "" }) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Clear")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )
    }
}

@Composable
fun SectionHeader(title: String, onClickAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        
        Text(
            text = "See all",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun FavoriteCard(
    item: FavoriteItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Favicon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.title.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.title,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = item.url,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun RecentTabCard(
    item: RecentTabItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_history),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = item.url,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun FavoritesGrid(
    items: List<FavoriteItem>,
    onClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        items.forEach { item ->
            FavoriteCard(item = item, onClick = { onClick(item.url) })
        }
    }
}

@Composable
fun RecentTabsList(
    items: List<RecentTabItem>,
    onClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        items.forEach { item ->
            RecentTabCard(item = item, onClick = { onClick(item.url) })
        }
    }
}

@Composable
fun FooterActions(
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = onNewTab,
            modifier = Modifier.weight(1f)
        ) {
            Icon(painterResource(R.drawable.ic_tab), contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Tab")
        }
        
        Button(
            onClick = onNewPrivateTab,
            modifier = Modifier.weight(1f),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(painterResource(R.drawable.ic_private_tab), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Private Tab")
        }
    }
}

// Data classes
data class FavoriteItem(
    val id: String,
    val title: String,
    val url: String,
    val favicon: String? = null
)

data class RecentTabItem(
    val id: String,
    val title: String,
    val url: String,
    val timestamp: Long
)

fun getDefaultFavorites(): List<FavoriteItem> = listOf(
    FavoriteItem("fav_default_1", "DuckDuckGo", "https://duckduckgo.com"),
    FavoriteItem("fav_default_2", "GitHub", "https://github.com"),
    FavoriteItem("fav_default_3", "Stack Overflow", "https://stackoverflow.com"),
    FavoriteItem("fav_default_4", "MDN Web Docs", "https://developer.mozilla.org")
)

fun getRecentTabs(): List<RecentTabItem> = listOf(
    RecentTabItem("recent_default_1", "Example Domain", "https://example.com", System.currentTimeMillis() - 3600000),
    RecentTabItem("recent_default_2", "Android Developers", "https://developer.android.com", System.currentTimeMillis() - 7200000)
)