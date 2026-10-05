package me.bnfy.blanc.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import me.bnfy.blanc.R

/**
 * Bookmarks/Favorites page
 */
@Composable
fun BookmarksPage(
    onBack: () -> Unit,
    onAddBookmark: () -> Unit,
    onBookmarkClick: (String) -> Unit
) {
    val bookmarks = remember { mutableStateOf<List<BookmarkItem>>(getDefaultBookmarks()) }
    val isEditing = remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            // Toolbar
            Toolbar(
                title = "Favorites",
                onBack = onBack,
                actions = {
                    if (isEditing.value) {
                        IconButton(onClick = { isEditing.value = false }) {
                            Text("Done", style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        IconButton(onClick = { isEditing.value = true }) {
                            Icon(painterResource(R.drawable.ic_edit), contentDescription = "Edit")
                        }
                        IconButton(onClick = onAddBookmark) {
                            Icon(painterResource(R.drawable.ic_add), contentDescription = "Add bookmark")
                        }
                    }
                }
            )
            
            // Bookmarks list
            if (bookmarks.value.isEmpty()) {
                EmptyState(
                    icon = R.drawable.ic_bookmark,
                    title = "No favorites yet",
                    subtitle = "Tap + to add your first favorite",
                    actionText = "Add favorite",
                    onAction = onAddBookmark
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp, horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(bookmarks.value) { bookmark ->
                        BookmarkCard(
                            bookmark = bookmark,
                            isEditing = isEditing.value,
                            onClick = { onBookmarkClick(bookmark.url) },
                            onEdit = { /* Edit bookmark */ },
                            onDelete = { 
                                bookmarks.value = bookmarks.value.filter { it.id != bookmark.id }
                            },
                            onPin = { /* Toggle pin */ }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Toolbar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = "Back")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            actions()
        }
    }
}

@Composable
fun EmptyState(
    icon: Int,
    title: String,
    subtitle: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            actionText?.let { text ->
                Button(onClick = onAction!!) {
                    Text(text)
                }
            }
        }
    }
}

@Composable
fun BookmarkCard(
    bookmark: BookmarkItem,
    isEditing: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (!isEditing) onClick() },
                onLongClick = { if (!isEditing) onEdit() }
            ),
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
                    .background(Color.Gray)
            )
            
            // Bookmark info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = bookmark.title,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = bookmark.url,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            
            // Actions
            if (isEditing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onPin) {
                        Icon(
                            painter = if (bookmark.isPinned) painterResource(R.drawable.ic_pin_filled) else painterResource(R.drawable.ic_pin),
                            contentDescription = if (bookmark.isPinned) "Unpin" else "Pin"
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(painterResource(R.drawable.ic_edit), contentDescription = "Edit")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete")
                    }
                }
            } else {
                Icon(
                    painter = if (bookmark.isPinned) painterResource(R.drawable.ic_pin_filled) else painterResource(R.drawable.ic_pin),
                    contentDescription = null,
                    tint = if (bookmark.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

data class BookmarkItem(
    val id: String,
    val title: String,
    val url: String,
    val favicon: String? = null,
    val isPinned: Boolean = false,
    val folderId: String? = null
)

fun getDefaultBookmarks(): List<BookmarkItem> = listOf(
    BookmarkItem("1", "DuckDuckGo", "https://duckduckgo.com", isPinned = true),
    BookmarkItem("2", "GitHub", "https://github.com", isPinned = true),
    BookmarkItem("3", "Stack Overflow", "https://stackoverflow.com"),
    BookmarkItem("4", "MDN Web Docs", "https://developer.mozilla.org"),
    BookmarkItem("5", "Android Developers", "https://developer.android.com"),
    BookmarkItem("6", "Kotlin Lang", "https://kotlinlang.org")
)