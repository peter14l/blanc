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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * History page
 */
@Composable
fun HistoryPage(
    onBack: () -> Unit,
    onItemClick: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    val historyItems = remember { mutableStateOf<List<HistoryItem>>(getSampleHistory()) }
    val searchQuery = remember { mutableStateOf("") }
    val isSearching = remember { mutableStateOf(false) }
    
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
                    if (isSearching.value) {
                        androidx.compose.material3.TextField(
                            value = searchQuery.value,
                            onValueChange = { searchQuery.value = it },
                            modifier = Modifier.width(200.dp),
                            singleLine = true,
                            placeholder = { Text("Search history") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { searchQuery.value = "" }) {
                                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Clear")
                                }
                            },
                            colors = androidx.compose.material3.TextFieldDefaults.textFieldColors(
                                containerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                        IconButton(onClick = { isSearching.value = false }) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = "Cancel")
                        }
                    } else {
                        IconButton(onClick = { isSearching.value = true }) {
                            Icon(painterResource(R.drawable.ic_search), contentDescription = "Search")
                        }
                        IconButton(onClick = { /* Show clear confirmation */ }) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = "Clear history")
                        }
                    }
                }
            )
            
            // History list grouped by date
            if (historyItems.value.isEmpty()) {
                EmptyState(
                    icon = R.drawable.ic_history,
                    title = "No history",
                    subtitle = "Your browsing history will appear here"
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp, horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val grouped = historyItems.value.groupBy { formatDate(it.timestamp) }
                    val sortedDates = grouped.keys.sortedDescending()
                    
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
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            
                            // Items for this date
                            grouped[date]?.forEach { item ->
                                HistoryItemCard(
                                    item = item,
                                    onClick = { onItemClick(item.url) },
                                    onDelete = { 
                                        historyItems.value = historyItems.value.filter { it.id != item.id }
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
fun HistoryItemCard(
    item: HistoryItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { /* Show context menu */ }
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
            Icon(
                painter = painterResource(R.drawable.ic_launch),
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.url,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Text(
                        text = formatTime(item.timestamp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = "Remove from history")
            }
        }
    }
}

data class HistoryItem(
    val id: String,
    val title: String,
    val url: String,
    val timestamp: Long,
    val favicon: String? = null,
    val isPrivate: Boolean = false
)

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

fun getSampleHistory(): List<HistoryItem> = listOf(
    HistoryItem("1", "Example Domain", "https://example.com", System.currentTimeMillis() - 1800000),
    HistoryItem("2", "Android Developers", "https://developer.android.com", System.currentTimeMillis() - 3600000),
    HistoryItem("3", "Kotlin Programming Language", "https://kotlinlang.org", System.currentTimeMillis() - 7200000),
    HistoryItem("4", "GitHub", "https://github.com", System.currentTimeMillis() - 90000000),
    HistoryItem("5", "Stack Overflow", "https://stackoverflow.com", System.currentTimeMillis() - 90000000)
)