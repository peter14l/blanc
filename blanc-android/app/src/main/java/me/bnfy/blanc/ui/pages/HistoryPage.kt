package me.bnfy.blanc.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import me.bnfy.blanc.storage.HistoryEntry
import me.bnfy.blanc.storage.Repository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * History page connected to Room database.
 */
@Composable
fun HistoryPage(
    onBack: () -> Unit,
    repository: Repository = BlancApplication.getInstance().repository,
    onItemClick: (String) -> Unit,
    onClearHistory: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    val queryParam = if (searchQuery.trim().isEmpty()) null else searchQuery.trim()
    val historyEntries by repository.historyDao.getHistoryPageFlow(
        profileId = "personal",
        limit = 300,
        offset = 0,
        query = queryParam
    ).collectAsState(initial = emptyList())

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
                title = "History",
                onBack = onBack,
                actions = {
                    if (isSearching) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.width(200.dp),
                            singleLine = true,
                            placeholder = { Text("Search history") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Clear")
                                }
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                        IconButton(onClick = { isSearching = false; searchQuery = "" }) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = "Cancel")
                        }
                    } else {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(painterResource(R.drawable.ic_search), contentDescription = "Search")
                        }
                        if (historyEntries.isNotEmpty()) {
                            IconButton(onClick = {
                                if (onClearHistory != null) {
                                    onClearHistory()
                                } else {
                                    coroutineScope.launch {
                                        repository.historyDao.clearHistory("personal")
                                    }
                                }
                            }) {
                                Icon(painterResource(R.drawable.ic_delete), contentDescription = "Clear history")
                            }
                        }
                    }
                }
            )

            // History list grouped by date
            if (historyEntries.isEmpty()) {
                EmptyState(
                    icon = R.drawable.ic_history,
                    title = if (searchQuery.isNotEmpty()) "No matching results" else "No history",
                    subtitle = if (searchQuery.isNotEmpty()) "Try searching for a different URL or title" else "Your browsing history will appear here"
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(vertical = 8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val grouped = historyEntries.groupBy { formatDate(it.visitTime) }
                    val sortedDates = grouped.keys.toList()

                    items(sortedDates) { date ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Date header
                            Text(
                                text = date,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )

                            // Items for this date
                            grouped[date]?.forEach { entry ->
                                HistoryEntryCard(
                                    entry = entry,
                                    onClick = { onItemClick(entry.url) },
                                    onDelete = {
                                        coroutineScope.launch {
                                            repository.historyDao.deleteById(entry.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryEntryCard(
    entry: HistoryEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_history),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(4.dp)
            )
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = entry.title?.ifBlank { entry.url } ?: entry.url,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entry.url,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = formatTime(entry.visitTime),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "Remove from history",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun formatDate(timestamp: Long): String {
    val date = Date(timestamp)
    val today = Date()
    val yesterday = Date(today.time - 86400000)

    val dayFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())

    return when {
        isSameDay(date, today) -> "Today"
        isSameDay(date, yesterday) -> "Yesterday"
        else -> dayFormat.format(date)
    }
}

fun formatTime(timestamp: Long): String {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    return timeFormat.format(Date(timestamp))
}

fun isSameDay(date1: Date, date2: Date): Boolean {
    val format = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    return format.format(date1) == format.format(date2)
}