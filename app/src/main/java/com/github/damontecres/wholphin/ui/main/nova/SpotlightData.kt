package com.github.damontecres.wholphin.ui.main.nova

import androidx.annotation.StringRes
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import org.jellyfin.sdk.model.api.BaseItemKind

enum class SpotlightKind(
    @param:StringRes val label: Int,
) {
    CONTINUE(R.string.continue_watching),
    NEXT_UP(R.string.next_up),
    NEW(R.string.recently_added),
}

/**
 * @param source the item's position in the home rows, so actions on it (context menu, remove) know its row
 */
data class SpotlightEntry(
    val kind: SpotlightKind,
    val item: BaseItem,
    val source: RowColumn,
)

/**
 * Picks the spotlight items from the loaded home rows, so it follows the user's home settings without extra requests:
 * up to [maxResumed] resumed and [maxNextUp] next up items from the watching rows first, then unwatched items of the
 * recently added rows, taking one row after the other. Only items with a backdrop, one per series.
 */
fun buildSpotlight(
    rows: List<HomeRowLoadingState>,
    maxEntries: Int = 7,
    maxResumed: Int = 2,
    maxNextUp: Int = 1,
    recentlyAdded: Boolean = true,
): List<SpotlightEntry> {
    val loaded = rows.withIndex().mapNotNull { (rowIndex, row) -> (row as? HomeRowLoadingState.Success)?.let { rowIndex to it } }
    val seenSeries = mutableSetOf<Any>()
    val result = mutableListOf<SpotlightEntry>()

    fun tryAdd(
        kind: SpotlightKind,
        item: BaseItem,
        source: RowColumn,
    ): Boolean {
        if (result.size >= maxEntries || !item.hasBackdrop || !seenSeries.add(item.data.seriesId ?: item.id)) return false
        result.add(SpotlightEntry(kind, item, source))
        return true
    }

    var resumed = 0
    var nextUp = 0
    loaded
        .filter { (_, row) -> row.rowType.isWatching }
        .forEach { (rowIndex, row) ->
            row.items.forEachIndexed { column, item ->
                if (item == null) return@forEachIndexed
                val isResumed = row.rowType !is HomeRowConfig.NextUp && item.playbackPosition.isPositive()
                if (isResumed) {
                    if (resumed < maxResumed && tryAdd(SpotlightKind.CONTINUE, item, RowColumn(rowIndex, column))) resumed++
                } else if (nextUp < maxNextUp && tryAdd(SpotlightKind.NEXT_UP, item, RowColumn(rowIndex, column))) {
                    nextUp++
                }
            }
        }

    val recentlyAdded =
        loaded
            .filter { (_, row) -> recentlyAdded && row.rowType is HomeRowConfig.RecentlyAdded }
            .map { (rowIndex, row) ->
                row.items.withIndex().mapNotNull { (column, item) ->
                    item?.takeIf { !it.played }?.let { RowColumn(rowIndex, column) to it }
                }
            }
    var column = 0
    while (result.size < maxEntries && recentlyAdded.any { column < it.size }) {
        recentlyAdded.forEach { items -> items.getOrNull(column)?.let { (source, item) -> tryAdd(SpotlightKind.NEW, item, source) } }
        column++
    }
    return result
}

private val HomeRowConfig?.isWatching: Boolean
    get() = this is HomeRowConfig.ContinueWatching || this is HomeRowConfig.NextUp || this is HomeRowConfig.ContinueWatchingCombined

/** A full screen spotlight without a backdrop looks broken; episodes use their series' */
internal val BaseItem.hasBackdrop: Boolean
    get() =
        if (type == BaseItemKind.EPISODE || type == BaseItemKind.SEASON) {
            !data.parentBackdropImageTags.isNullOrEmpty()
        } else {
            !data.backdropImageTags.isNullOrEmpty()
        }
