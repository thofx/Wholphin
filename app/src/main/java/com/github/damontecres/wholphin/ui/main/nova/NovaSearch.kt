package com.github.damontecres.wholphin.ui.main.nova

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.services.KeyValueService
import com.github.damontecres.wholphin.ui.ItemRowFields
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.main.HomePageCardContent
import com.github.damontecres.wholphin.ui.playback.theater.TheaterIcons
import com.github.damontecres.wholphin.ui.toBaseItems
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import javax.inject.Inject

private const val RECENT_KEY = "nova_recent_searches"
private const val MAX_RECENT = 8

/**
 * The empty search page's data: the user's recent searches (kept on this device, per user, via [KeyValueService])
 * and top rated movies and shows they have not watched
 */
@HiltViewModel
class NovaSearchViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val keyValueService: KeyValueService,
    ) : ViewModel() {
        private val userId get() = serverRepository.currentUser?.id

        val recentSearches: Flow<List<String>> =
            userId?.let { keyValueService.get(it, RECENT_KEY, emptyList<String>()) } ?: flowOf(emptyList())

        fun addRecent(query: String) {
            val q = query.trim()
            if (q.isNotEmpty()) updateRecent { listOf(q) + it.filter { s -> s != q } }
        }

        fun removeRecent(query: String) = updateRecent { it - query }

        fun clearRecent() = updateRecent { emptyList() }

        private fun updateRecent(change: (List<String>) -> List<String>) {
            val id = userId ?: return
            viewModelScope.launchIO {
                val current = keyValueService.get(id, RECENT_KEY, emptyList<String>()).first()
                keyValueService.save(id, RECENT_KEY, change(current).take(MAX_RECENT))
            }
        }

        private val _topRated = MutableStateFlow<List<BaseItem>>(emptyList())
        val topRated: StateFlow<List<BaseItem>> = _topRated

        fun load() {
            if (_topRated.value.isNotEmpty()) return
            viewModelScope.launchIO {
                try {
                    val request =
                        GetItemsRequest(
                            userId = serverRepository.currentUser?.id,
                            recursive = true,
                            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                            isPlayed = false,
                            sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            fields = ItemRowFields,
                            limit = 15,
                        )
                    _topRated.value = GetItemsRequestHandler.execute(api, request).toBaseItems()
                } catch (ex: Exception) {
                    Timber.w(ex, "Error loading top rated items")
                }
            }
        }
    }

/**
 * The search page before anything is typed: recent searches to run again and top rated items to pick from
 */
@Composable
fun NovaSearchEmpty(
    onSearch: (String) -> Unit,
    onClickItem: (BaseItem) -> Unit,
    onLongClickItem: (Int, BaseItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NovaSearchViewModel = hiltViewModel(),
) {
    val recent by viewModel.recentSearches.collectAsState(emptyList())
    val topRated by viewModel.topRated.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    Column(verticalArrangement = Arrangement.spacedBy(28.dp), modifier = modifier.fillMaxSize()) {
        if (recent.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = stringResource(R.string.nova_recent_searches),
                    style = MaterialTheme.typography.titleLarge,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Long press removes one; the last chip clears them all
                    recent.forEach { query ->
                        NovaActionButton(
                            label = query,
                            showLabel = true,
                            onClick = { onSearch(query) },
                            onLongClick = { viewModel.removeRecent(query) },
                        ) {
                            Icon(TheaterIcons.Clock, contentDescription = null)
                        }
                    }
                    NovaActionButton(
                        label = stringResource(R.string.nova_clear),
                        showLabel = true,
                        onClick = viewModel::clearRecent,
                    ) {
                        Icon(TheaterIcons.Trash, contentDescription = null)
                    }
                }
            }
        }
        if (topRated.isNotEmpty()) {
            val viewOptions = remember { HomeRowViewOptions() }
            ItemRow(
                title = stringResource(R.string.nova_top_rated),
                items = topRated,
                onClickItem = { _, item -> onClickItem(item) },
                onLongClickItem = onLongClickItem,
                horizontalPadding = 8.dp,
                cardContent = { index, item, mod, onClick, onLongClick ->
                    HomePageCardContent(
                        index = index,
                        item = item,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        viewOptions = viewOptions,
                        modifier = mod,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
