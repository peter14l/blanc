package me.bnfy.blanc.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.bnfy.blanc.BlancApplication
import me.bnfy.blanc.R
import me.bnfy.blanc.storage.Favorite
import me.bnfy.blanc.storage.Repository
import java.util.UUID

/**
 * Bookmarks/Favorites page backed by Room database.
 */
@Composable
fun BookmarksPage(
    onBack: () -> Unit,
    repository: Repository = BlancApplication.getInstance().repository,
    onAddBookmark: (() -> Unit)? = null,
    onBookmarkClick: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val favorites by repository.favoriteDao.getAllFlow("personal").collectAsState(initial = emptyList())
    var isEditing by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingFavorite by remember { mutableStateOf<Favorite?>(null) }

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
                    if (isEditing) {
                        IconButton(onClick = { isEditing = false }) {
                            Text("Done", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        if (favorites.isNotEmpty()) {
                            IconButton(onClick = { isEditing = true }) {
                                Icon(painterResource(R.drawable.ic_edit), contentDescription = "Edit")
                            }
                        }
                        IconButton(onClick = {
                            if (onAddBookmark != null) onAddBookmark() else showAddDialog = true
                        }) {
                            Icon(painterResource(R.drawable.ic_add), contentDescription = "Add favorite")
                        }
                    }
                }
            )

            // Bookmarks list
            if (favorites.isEmpty()) {
                EmptyState(
                    icon = R.drawable.ic_bookmark,
                    title = "No favorites yet",
                    subtitle = "Tap + or use the star icon while browsing to add favorites",
                    actionText = "Add favorite",
                    onAction = { showAddDialog = true }
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(vertical = 8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp, horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(favorites, key = { it.id }) { favorite ->
                        FavoriteCardItem(
                            favorite = favorite,
                            isEditing = isEditing,
                            onClick = { onBookmarkClick(favorite.url) },
                            onEdit = { editingFavorite = favorite },
                            onDelete = {
                                coroutineScope.launch {
                                    repository.favoriteDao.deleteById(favorite.id)
                                }
                            },
                            onPin = {
                                coroutineScope.launch {
                                    repository.favoriteDao.setPinned(favorite.id, !favorite.isPinned, System.currentTimeMillis())
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Add dialog
    if (showAddDialog) {
        FavoriteEditDialog(
            title = "Add Favorite",
            initialTitle = "",
            initialUrl = "https://",
            onDismiss = { showAddDialog = false },
            onSave = { titleText, urlText ->
                showAddDialog = false
                coroutineScope.launch {
                    val resolvedUrl = if (urlText.startsWith("http://") || urlText.startsWith("https://")) urlText else "https://$urlText"
                    val fav = Favorite(
                        id = UUID.randomUUID().toString(),
                        url = resolvedUrl,
                        title = titleText.ifBlank { resolvedUrl },
                        profileId = "personal"
                    )
                    repository.favoriteDao.insert(fav)
                }
            }
        )
    }

    // Edit dialog
    editingFavorite?.let { fav ->
        FavoriteEditDialog(
            title = "Edit Favorite",
            initialTitle = fav.title,
            initialUrl = fav.url,
            onDismiss = { editingFavorite = null },
            onSave = { titleText, urlText ->
                editingFavorite = null
                coroutineScope.launch {
                    val resolvedUrl = if (urlText.startsWith("http://") || urlText.startsWith("https://")) urlText else "https://$urlText"
                    repository.favoriteDao.update(fav.copy(
                        title = titleText.ifBlank { resolvedUrl },
                        url = resolvedUrl,
                        updatedAt = System.currentTimeMillis()
                    ))
                }
            }
        )
    }
}

@Composable
fun FavoriteEditDialog(
    title: String,
    initialTitle: String,
    initialUrl: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var titleInput by remember { mutableStateOf(initialTitle) }
    var urlInput by remember { mutableStateOf(initialUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(titleInput.trim(), urlInput.trim()) },
                enabled = urlInput.trim().isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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
            .statusBarsPadding(),
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
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
            if (actionText != null && onAction != null) {
                Button(onClick = onAction) {
                    Text(actionText)
                }
            }
        }
    }
}

@Composable
fun FavoriteCardItem(
    favorite: Favorite,
    isEditing: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                onClick = { if (!isEditing) onClick() }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Favicon indicator
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                val initial = favorite.title.firstOrNull()?.uppercaseChar()?.toString() ?: "W"
                Text(
                    text = initial,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 16.sp
                )
            }

            // Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = favorite.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = favorite.url,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            // Actions
            if (isEditing) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onPin, modifier = Modifier.size(32.dp)) {
                        Icon(
                            painter = if (favorite.isPinned) painterResource(R.drawable.ic_pin_filled) else painterResource(R.drawable.ic_pin),
                            contentDescription = if (favorite.isPinned) "Unpin" else "Pin",
                            tint = if (favorite.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(painterResource(R.drawable.ic_edit), contentDescription = "Edit")
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                if (favorite.isPinned) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pin_filled),
                        contentDescription = "Pinned",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}