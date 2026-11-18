package com.github.damontecres.wholphin.ui.main.nova

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.util.EmptyStringProvider
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class SpotlightDataTest {
    private fun userData(
        played: Boolean = false,
        positionTicks: Long = 0,
    ) = UserItemDataDto(
        playbackPositionTicks = positionTicks,
        playCount = 0,
        isFavorite = false,
        played = played,
        key = "",
        itemId = UUID.randomUUID(),
    )

    private fun movie(
        name: String,
        backdrop: Boolean = true,
        played: Boolean = false,
        positionTicks: Long = 0,
    ) = BaseItem(
        BaseItemDto(
            id = UUID.randomUUID(),
            type = BaseItemKind.MOVIE,
            name = name,
            backdropImageTags = if (backdrop) listOf("tag") else emptyList(),
            userData = userData(played, positionTicks),
        ),
    )

    private fun episode(
        name: String,
        seriesId: UUID,
        positionTicks: Long = 0,
    ) = BaseItem(
        BaseItemDto(
            id = UUID.randomUUID(),
            type = BaseItemKind.EPISODE,
            name = name,
            seriesId = seriesId,
            parentBackdropImageTags = listOf("tag"),
            userData = userData(positionTicks = positionTicks),
        ),
    )

    private fun row(
        type: HomeRowConfig,
        vararg items: BaseItem,
    ) = HomeRowLoadingState.Success(EmptyStringProvider, items.toList(), rowType = type)

    private val recent = HomeRowConfig.RecentlyAdded(UUID.randomUUID())

    @Test
    fun watchingFirst_thenNewFromEachRowInTurn() {
        val series = UUID.randomUUID()
        val rows =
            listOf(
                row(
                    HomeRowConfig.ContinueWatchingCombined(),
                    episode("resumed", series, positionTicks = 10),
                    // Same series: skipped
                    episode("same series", series, positionTicks = 10),
                    movie("not started"),
                    movie("next up over its limit"),
                ),
                row(recent, movie("m1"), movie("no backdrop", backdrop = false), movie("watched", played = true), movie("m2")),
                row(HomeRowConfig.RecentlyAdded(UUID.randomUUID()), movie("t1"), movie("t2")),
                // Not spotlight sources
                row(HomeRowConfig.RecentlyReleased(UUID.randomUUID()), movie("released")),
                row(HomeRowConfig.Genres(UUID.randomUUID()), movie("genre")),
            )

        val result = buildSpotlight(rows)

        assertEquals(listOf("resumed", "not started", "m1", "t1", "t2", "m2"), result.map { it.item.name })
        assertEquals(
            listOf(SpotlightKind.CONTINUE, SpotlightKind.NEXT_UP) + List(4) { SpotlightKind.NEW },
            result.map { it.kind },
        )
        // Where each item is in the home rows, for the context menu
        assertEquals(
            listOf(RowColumn(0, 0), RowColumn(0, 2), RowColumn(1, 0), RowColumn(2, 0), RowColumn(2, 1), RowColumn(1, 3)),
            result.map { it.source },
        )
    }

    @Test
    fun resumedAndNextUpHaveTheirOwnLimits() {
        val rows =
            listOf(
                row(HomeRowConfig.ContinueWatching(), *Array(4) { movie("r$it", positionTicks = 10) }),
                row(HomeRowConfig.NextUp(), *Array(3) { episode("n$it", UUID.randomUUID(), positionTicks = 10) }),
            )
        assertEquals(listOf("r0", "r1", "n0"), buildSpotlight(rows).map { it.item.name })
    }

    @Test
    fun sourcesCanBeTurnedOff() {
        val rows =
            listOf(
                row(HomeRowConfig.ContinueWatching(), movie("resumed", positionTicks = 10)),
                row(recent, movie("m1")),
            )
        assertEquals(listOf("resumed"), buildSpotlight(rows, recentlyAdded = false).map { it.item.name })
        assertEquals(listOf("m1"), buildSpotlight(rows, maxResumed = 0).map { it.item.name })
    }

    @Test
    fun limitsTheCount() {
        val rows = listOf(row(recent, *Array(20) { movie("m$it") }))
        assertEquals(7, buildSpotlight(rows).size)
        assertEquals(3, buildSpotlight(rows, maxEntries = 3).size)
    }

    @Test
    fun ignoresRowsThatAreNotLoaded() {
        val rows = listOf(HomeRowLoadingState.Loading(EmptyStringProvider), row(recent, movie("m1")))
        assertEquals(listOf("m1"), buildSpotlight(rows).map { it.item.name })
    }
}
