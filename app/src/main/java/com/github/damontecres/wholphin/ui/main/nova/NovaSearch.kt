package com.github.damontecres.wholphin.ui.main.nova

import android.content.Context
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.github.damontecres.wholphin.ui.ItemRowFields
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.main.HomePageCardContent
import com.github.damontecres.wholphin.ui.playback.theater.TheaterIcons
import com.github.damontecres.wholphin.ui.toBaseItems
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import javax.inject.Inject

/** Recent search queries, on this device only */
object RecentSearches {
    private const val PREFS_NAME = "nova_search"
    private const val KEY = "recent"
    private const val MAX = 8

    fun get(context: Context): List<String> =
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            .orEmpty()

    fun add(
        context: Context,
        query: String,
    ) {
        val q = query.trim()
        if (q.isEmpty()) return
        save(context, (listOf(q) + get(context).filter { it != q }).take(MAX))
    }

    fun remove(
        context: Context,
        query: String,
    ) = save(context, get(context).filter { it != query })

    fun clear(context: Context) = save(context, emptyList())

    private fun save(
        context: Context,
        list: List<String>,
    ) = context
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY, list.joinToString("\n"))
        .apply()
}

/** Top rated movies and shows the user has not watched, for the empty search page */
@HiltViewModel
class NovaSearchViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
    ) : ViewModel() {
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
    val context = LocalContext.current
    var recent by remember { mutableStateOf(RecentSearches.get(context)) }
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
                            onLongClick = {
                                RecentSearches.remove(context, query)
                                recent = RecentSearches.get(context)
                            },
                        ) {
                            Icon(TheaterIcons.Clock, contentDescription = null)
                        }
                    }
                    NovaActionButton(
                        label = stringResource(R.string.nova_clear),
                        showLabel = true,
                        onClick = {
                            RecentSearches.clear(context)
                            recent = emptyList()
                        },
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
