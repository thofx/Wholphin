package com.github.damontecres.wholphin.test

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.ui.playback.TrackSelected
import com.github.damontecres.wholphin.ui.playback.TrackSelectionUtils
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamType
import org.junit.Assert
import org.junit.Test
import java.nio.file.Paths
import kotlin.io.path.readText

class TestTrackSelection {
    /**
     * Builds the tracks for the `embedded_subs.json` for the given backend
     *
     * Note: This is manual based on observation & code review of the playback for that file
     */
    private fun buildEmbeddedTracks(backend: PlayerBackend): Tracks {
        val formats =
            if (backend == PlayerBackend.VLC) {
                val video =
                    Format
                        .Builder()
                        .setId("0:1")
                        .setSampleMimeType("video/default")
                        .build()
                val audios =
                    (1..3).map {
                        Format
                            .Builder()
                            .setId("$it:$it")
                            .setSampleMimeType("audio/default")
                            .build()
                    }
                val subtitles =
                    (1..3).map {
                        Format
                            .Builder()
                            .setId("${it + 3}:$it")
                            .setSampleMimeType("text/default")
                            .build()
                    }
                (listOf(video) + audios + subtitles)
            } else {
                val video =
                    Format
                        .Builder()
                        .setId("1")
                        .setSampleMimeType("video/default")
                        .build()
                val audios =
                    (2..4).map {
                        Format
                            .Builder()
                            .setId("$it")
                            .setSampleMimeType("audio/default")
                            .build()
                    }
                val subtitles =
                    (5..7).map {
                        Format
                            .Builder()
                            .setId("$it")
                            .setSampleMimeType("text/default")
                            .build()
                    }
                (listOf(video) + audios + subtitles)
            }
        val groups =
            formats
                .map { TrackGroup(it) }
                .map { Tracks.Group(it, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false)) }
        return Tracks(groups)
    }

    /**
     * Builds the tracks for the `no_embedded_subs.json` for the given backend
     *
     * Note: This is manual based on observation & code review of the playback for that file
     */
    private fun buildNoEmbeddedTracks(backend: PlayerBackend): Tracks {
        val formats =
            if (backend == PlayerBackend.VLC) {
                val video =
                    Format
                        .Builder()
                        .setId("0:1")
                        .setSampleMimeType("video/default")
                        .build()
                val audios =
                    (1..3).map {
                        Format
                            .Builder()
                            .setId("$it:$it")
                            .setSampleMimeType("audio/default")
                            .build()
                    }
                val subtitles =
                    (1..3).map {
                        Format
                            .Builder()
                            .setId("${it + 3}:$it")
                            .setSampleMimeType("text/default")
                            .build()
                    } +
                        listOf(
                            Format
                                .Builder()
                                .setId("7:e:4")
                                .setSampleMimeType("text/default")
                                .build(),
                        )
                (listOf(video) + audios + subtitles)
            } else {
                // ExoPlayer
                val video =
                    Format
                        .Builder()
                        .setId("0:1")
                        .setSampleMimeType("video/default")
                        .build()
                val audios =
                    (2..4).map {
                        Format
                            .Builder()
                            .setId("0:$it")
                            .setSampleMimeType("audio/default")
                            .build()
                    }
                val subtitles =
                    (5..7).map {
                        Format
                            .Builder()
                            .setId("0:$it")
                            .setSampleMimeType("text/default")
                            .build()
                    } +
                        listOf(
                            Format
                                .Builder()
                                .setId("1:e:0")
                                .setSampleMimeType("text/default")
                                .build(),
                        )
                (listOf(video) + audios + subtitles)
            }
        val groups =
            formats
                .map { TrackGroup(it) }
                .map { Tracks.Group(it, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false)) }
        return Tracks(groups)
    }

    /**
     * Builds the tracks for the `external_subs.json` for the given backend.
     *
     * Must supply the desired subtitle index because ExoPlayer uses it.
     *
     * Note: This is manual based on observation & code review of the playback for that file
     */
    private fun buildExternalTracks(
        backend: PlayerBackend,
        selectedIndex: Int,
    ): Tracks {
        val formats =
            if (backend == PlayerBackend.VLC) {
                val video =
                    Format
                        .Builder()
                        .setId("1:1")
                        .setSampleMimeType("video/default")
                        .build()
                val audios =
                    listOf(
                        Format
                            .Builder()
                            .setId("0:1")
                            .setSampleMimeType("audio/default")
                            .build(),
                    )
                val subtitles =
                    listOf(
                        Format
                            .Builder()
                            .setId("2:1")
                            .setSampleMimeType("text/default")
                            .build(),
                        Format
                            .Builder()
                            .setId("3:e:2")
                            .setSampleMimeType("text/default")
                            .build(),
                    )
                (listOf(video) + audios + subtitles)
            } else {
                val video =
                    Format
                        .Builder()
                        .setId("0:2")
                        .setSampleMimeType("video/default")
                        .build()
                val audios =
                    listOf(
                        Format
                            .Builder()
                            .setId("0:1")
                            .setSampleMimeType("audio/default")
                            .build(),
                    )
                val subtitles =
                    listOf(
                        Format
                            .Builder()
                            .setId("0:3") // Embedded
                            .setSampleMimeType("text/default")
                            .build(),
                        Format
                            .Builder()
                            .setId("1:e:$selectedIndex") // External
                            .setSampleMimeType("text/default")
                            .build(),
                    )
                (listOf(video) + audios + subtitles)
            }
        val groups =
            formats
                .map { TrackGroup(it) }
                .map { Tracks.Group(it, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false)) }
        return Tracks(groups)
    }

    private fun buildMissingIdTracks(backend: PlayerBackend): Tracks {
        val formats =
            if (backend == PlayerBackend.VLC) {
                val video =
                    Format
                        .Builder()
                        .setId("0:1")
                        .setSampleMimeType("video/default")
                        .build()
                val audios =
                    (1..3).map {
                        Format
                            .Builder()
                            .setId("$it:$it")
                            .setSampleMimeType("audio/default")
                            .build()
                    }
                val subtitles =
                    (1..3).map {
                        Format
                            .Builder()
                            .setId("${it + 3}:$it")
                            .setSampleMimeType("text/default")
                            .build()
                    }
                (listOf(video) + audios + subtitles)
            } else {
                // ExoPlayer
                val video =
                    Format
                        .Builder()
                        .setId("0:1")
                        .setSampleMimeType("video/default")
                        .build()
                val audios =
                    (3..5).map {
                        Format
                            .Builder()
                            .setId("0:$it")
                            .setSampleMimeType("audio/default")
                            .build()
                    }
                val subtitles =
                    (6..8).map {
                        Format
                            .Builder()
                            .setId("0:$it")
                            .setSampleMimeType("text/default")
                            .build()
                    }
                (listOf(video) + audios + subtitles)
            }
        val groups =
            formats
                .map { TrackGroup(it) }
                .map {
                    Tracks.Group(
                        it,
                        false,
                        intArrayOf(C.FORMAT_HANDLED),
                        booleanArrayOf(false),
                    )
                }
        return Tracks(groups)
    }

    private fun buildDifferentOrderedTracks(backend: PlayerBackend): Tracks {
        val formats =
            if (backend == PlayerBackend.VLC) {
                val video =
                    Format
                        .Builder()
                        .setId("0:1")
                        .setSampleMimeType("video/default")
                        .build()
                val audios =
                    (1..3).map {
                        Format
                            .Builder()
                            .setId("$it:$it")
                            .setSampleMimeType("audio/default")
                            .build()
                    }
                val subtitles =
                    (1..3).map {
                        Format
                            .Builder()
                            .setId("${it + 3}:$it")
                            .setSampleMimeType("text/default")
                            .build()
                    }
                (listOf(video) + audios + subtitles)
            } else {
                // ExoPlayer
                val video =
                    Format
                        .Builder()
                        .setId("0:1")
                        .setSampleMimeType("video/default")
                        .build()
                val audios =
                    listOf(
                        Format
                            .Builder()
                            .setId("0:2")
                            .setSampleMimeType("audio/default")
                            .build(),
                        Format
                            .Builder()
                            .setId("0:4")
                            .setSampleMimeType("audio/default")
                            .build(),
                        Format
                            .Builder()
                            .setId("0:3")
                            .setSampleMimeType("audio/default")
                            .build(),
                    )
                val subtitles =
                    (5..8).map {
                        Format
                            .Builder()
                            .setId("0:$it")
                            .setSampleMimeType("text/default")
                            .build()
                    } +
                        listOf(
                            Format
                                .Builder()
                                .setId("1:e:8") // External
                                .setSampleMimeType("text/default")
                                .build(),
                        )
                (listOf(video) + audios + subtitles)
            }
        val groups =
            formats
                .map { TrackGroup(it) }
                .map {
                    Tracks.Group(
                        it,
                        false,
                        intArrayOf(C.FORMAT_HANDLED),
                        booleanArrayOf(false),
                    )
                }
        return Tracks(groups)
    }

    @Test
    fun `test VLC embedded`() {
        val resource = javaClass.classLoader?.getResource("embedded_subs.json")
        Assert.assertNotNull(resource)
        val fileContents = Paths.get(resource!!.toURI()).readText()
        val source = Json.decodeFromString<MediaSourceInfo>(fileContents)
        val tracks = buildEmbeddedTracks(PlayerBackend.VLC)
        Assert.assertEquals(7, source.mediaStreams?.size)

        val trackSelectionParameters = TrackSelectionParameters.Builder().build()

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                audioIndex = 1,
                subtitleIndex = 4,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.bothSelected)
                Assert.assertEquals("1:1", result.trackSelectionParameters.getAudioOverride()?.id)
                Assert.assertEquals("4:1", result.trackSelectionParameters.getSubtitleOverride()?.id)
            }

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                audioIndex = 2,
                subtitleIndex = 4,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.bothSelected)
                Assert.assertEquals("2:2", result.trackSelectionParameters.getAudioOverride()?.id)
                Assert.assertEquals("4:1", result.trackSelectionParameters.getSubtitleOverride()?.id)
            }

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                audioIndex = 1,
                subtitleIndex = TrackIndex.DISABLED,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.bothSelected)
                Assert.assertEquals("1:1", result.trackSelectionParameters.getAudioOverride()?.id)
                Assert.assertEquals(null, result.trackSelectionParameters.getSubtitleOverride()?.id)
            }
    }

    @Test
    fun `test ExoPlayer embedded`() {
        val resource = javaClass.classLoader?.getResource("embedded_subs.json")
        Assert.assertNotNull(resource)
        val fileContents = Paths.get(resource!!.toURI()).readText()
        val source = Json.decodeFromString<MediaSourceInfo>(fileContents)
        val tracks = buildEmbeddedTracks(PlayerBackend.EXO_PLAYER)
        Assert.assertEquals(7, source.mediaStreams?.size)

        val trackSelectionParameters = TrackSelectionParameters.Builder().build()

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                audioIndex = 1,
                subtitleIndex = 4,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.bothSelected)
                Assert.assertEquals("2", result.trackSelectionParameters.getAudioOverride()?.id)
                Assert.assertEquals("5", result.trackSelectionParameters.getSubtitleOverride()?.id)
            }

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                audioIndex = 2,
                subtitleIndex = 4,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.bothSelected)
                Assert.assertEquals("3", result.trackSelectionParameters.getAudioOverride()?.id)
                Assert.assertEquals("5", result.trackSelectionParameters.getSubtitleOverride()?.id)
            }

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                audioIndex = 2,
                subtitleIndex = 6,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.bothSelected)
                Assert.assertEquals("3", result.trackSelectionParameters.getAudioOverride()?.id)
                Assert.assertEquals("7", result.trackSelectionParameters.getSubtitleOverride()?.id)
            }

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                audioIndex = 1,
                subtitleIndex = TrackIndex.DISABLED,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.bothSelected)
                Assert.assertEquals("2", result.trackSelectionParameters.getAudioOverride()?.id)
                Assert.assertEquals(null, result.trackSelectionParameters.getSubtitleOverride()?.id)
            }
    }

    @Test
    fun `test VLC no embedded`() {
        val resource = javaClass.classLoader?.getResource("no_embedded_subs.json")
        Assert.assertNotNull(resource)
        val fileContents = Paths.get(resource!!.toURI()).readText()
        val source = Json.decodeFromString<MediaSourceInfo>(fileContents)
        val tracks = buildNoEmbeddedTracks(PlayerBackend.VLC)
        Assert.assertEquals(5, source.mediaStreams?.size)

        val trackSelectionParameters = TrackSelectionParameters.Builder().build()

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                audioIndex = 2,
                subtitleIndex = 0,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.bothSelected)
                Assert.assertEquals("1:1", result.trackSelectionParameters.getAudioOverride()?.id)
                Assert.assertEquals("7:e:4", result.trackSelectionParameters.getSubtitleOverride()?.id)
            }

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                audioIndex = 3,
                subtitleIndex = 0,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.bothSelected)
                Assert.assertEquals("2:2", result.trackSelectionParameters.getAudioOverride()?.id)
                Assert.assertEquals("7:e:4", result.trackSelectionParameters.getSubtitleOverride()?.id)
            }
    }

    @Test
    fun `test ExoPlayer no embedded`() {
        val resource = javaClass.classLoader?.getResource("no_embedded_subs.json")
        Assert.assertNotNull(resource)
        val fileContents = Paths.get(resource!!.toURI()).readText()
        val source = Json.decodeFromString<MediaSourceInfo>(fileContents)
        val tracks = buildNoEmbeddedTracks(PlayerBackend.EXO_PLAYER)
        Assert.assertEquals(5, source.mediaStreams?.size)

        val trackSelectionParameters = TrackSelectionParameters.Builder().build()

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                audioIndex = 2,
                subtitleIndex = 0,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.bothSelected)
                Assert.assertEquals("0:2", result.trackSelectionParameters.getAudioOverride()?.id)
                Assert.assertEquals("1:e:0", result.trackSelectionParameters.getSubtitleOverride()?.id)
            }
    }

    @Test
    fun `test VLC external`() {
        val resource = javaClass.classLoader?.getResource("external_subs.json")
        Assert.assertNotNull(resource)
        val fileContents = Paths.get(resource!!.toURI()).readText()
        val source = Json.decodeFromString<MediaSourceInfo>(fileContents)
        val tracks = buildExternalTracks(PlayerBackend.VLC, 0)
        Assert.assertEquals(6, source.mediaStreams?.size)

        val trackSelectionParameters = TrackSelectionParameters.Builder().build()

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                audioIndex = 3,
                subtitleIndex = 0,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.audioSelected)
                Assert.assertTrue(result.subtitleSelected)
                Assert.assertEquals("0:1", result.trackSelectionParameters.getAudioOverride()?.id)
                Assert.assertEquals("3:e:2", result.trackSelectionParameters.getSubtitleOverride()?.id)
            }

        // Select embedded subtitles
        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                audioIndex = 3,
                subtitleIndex = 5,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.audioSelected)
                Assert.assertTrue(result.subtitleSelected)
                Assert.assertEquals(
                    "0:1",
                    result.trackSelectionParameters.getAudioOverride()?.id,
                )
                Assert.assertEquals(
                    "2:1",
                    result.trackSelectionParameters.getSubtitleOverride()?.id,
                )
            }
    }

    @Test
    fun `test ExoPlayer external`() {
        val resource = javaClass.classLoader?.getResource("external_subs.json")
        Assert.assertNotNull(resource)
        val fileContents = Paths.get(resource!!.toURI()).readText()
        val source = Json.decodeFromString<MediaSourceInfo>(fileContents)

        buildExternalTracks(PlayerBackend.EXO_PLAYER, 0).also { tracks ->
            Assert.assertEquals(6, source.mediaStreams?.size)

            val trackSelectionParameters = TrackSelectionParameters.Builder().build()

            TrackSelectionUtils
                .createTrackSelections(
                    trackSelectionParams = trackSelectionParameters,
                    tracks = tracks,
                    audioIndex = 3,
                    subtitleIndex = 0,
                    source = source,
                ).also { result ->
                    Assert.assertTrue(result.audioSelected)
                    Assert.assertTrue(result.subtitleSelected)
                    Assert.assertEquals(
                        "0:1",
                        result.trackSelectionParameters.getAudioOverride()?.id,
                    )
                    Assert.assertEquals(
                        "1:e:0",
                        result.trackSelectionParameters.getSubtitleOverride()?.id,
                    )
                }

            // Select embedded subtitles
            TrackSelectionUtils
                .createTrackSelections(
                    trackSelectionParams = trackSelectionParameters,
                    tracks = tracks,
                    audioIndex = 3,
                    subtitleIndex = 5,
                    source = source,
                ).also { result ->
                    Assert.assertTrue(result.audioSelected)
                    Assert.assertTrue(result.subtitleSelected)
                    Assert.assertEquals(
                        "0:1",
                        result.trackSelectionParameters.getAudioOverride()?.id,
                    )
                    Assert.assertEquals(
                        "0:3",
                        result.trackSelectionParameters.getSubtitleOverride()?.id,
                    )
                }
        }

        buildExternalTracks(PlayerBackend.EXO_PLAYER, 2).also { tracks ->
            Assert.assertEquals(6, source.mediaStreams?.size)

            val trackSelectionParameters = TrackSelectionParameters.Builder().build()

            TrackSelectionUtils
                .createTrackSelections(
                    trackSelectionParams = trackSelectionParameters,
                    tracks = tracks,
                    audioIndex = 3,
                    subtitleIndex = 2,
                    source = source,
                ).also { result ->
                    Assert.assertTrue(result.audioSelected)
                    Assert.assertTrue(result.subtitleSelected)
                    Assert.assertEquals(
                        "0:1",
                        result.trackSelectionParameters.getAudioOverride()?.id,
                    )
                    Assert.assertEquals(
                        "1:e:2",
                        result.trackSelectionParameters.getSubtitleOverride()?.id,
                    )
                }
        }
    }

    @Test
    fun `test ExoPlayer missing ids`() {
        val resource = javaClass.classLoader?.getResource("no_embedded_subs.json")
        Assert.assertNotNull(resource)
        val fileContents = Paths.get(resource!!.toURI()).readText()
        val source = Json.decodeFromString<MediaSourceInfo>(fileContents)
        val tracks = buildMissingIdTracks(PlayerBackend.EXO_PLAYER)
        val ids = tracks.groups.flatMap { g -> (0..<g.length).map { g.getTrackFormat(it).id } }
        Assert.assertTrue("0:1" in ids)
        Assert.assertTrue("0:2" !in ids)
        Assert.assertTrue("0:3" in ids)

        Assert.assertEquals(5, source.mediaStreams?.size)

        val trackSelectionParameters = TrackSelectionParameters.Builder().build()

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                audioIndex = 3,
                subtitleIndex = TrackIndex.DISABLED,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.bothSelected)
                Assert.assertEquals("0:4", result.trackSelectionParameters.getAudioOverride()?.id)
                Assert.assertEquals(null, result.trackSelectionParameters.getSubtitleOverride()?.id)
            }
    }

    @Test
    fun `test VLC missing ids`() {
        val resource = javaClass.classLoader?.getResource("no_embedded_subs.json")
        Assert.assertNotNull(resource)
        val fileContents = Paths.get(resource!!.toURI()).readText()
        val source = Json.decodeFromString<MediaSourceInfo>(fileContents)
        val tracks = buildMissingIdTracks(PlayerBackend.VLC)
        val ids = tracks.groups.flatMap { g -> (0..<g.length).map { g.getTrackFormat(it).id } }
        println(ids)

        Assert.assertEquals(5, source.mediaStreams?.size)

        val trackSelectionParameters = TrackSelectionParameters.Builder().build()

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                audioIndex = 3,
                subtitleIndex = TrackIndex.DISABLED,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.bothSelected)
                Assert.assertEquals("2:2", result.trackSelectionParameters.getAudioOverride()?.id)
                Assert.assertEquals(null, result.trackSelectionParameters.getSubtitleOverride()?.id)
            }
    }

    @Test
    fun `test ExoPlayer different order`() {
        val source =
            TestTracks
                .Builder()
                .addVideo()
                .addAudio(3)
                .addSubtitle(4)
                .addExternalSubtitle(1)
                .buildMediaSourceInfo()
        val tracks = buildDifferentOrderedTracks(PlayerBackend.EXO_PLAYER)

        Assert.assertEquals(9, source.mediaStreams?.size)

        val trackSelectionParameters = TrackSelectionParameters.Builder().build()

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                audioIndex = 1,
                subtitleIndex = 8,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.bothSelected)
                Assert.assertEquals("0:2", result.trackSelectionParameters.getAudioOverride()?.id)
                Assert.assertEquals(
                    "1:e:8",
                    result.trackSelectionParameters.getSubtitleOverride()?.id,
                )
            }

        TrackSelectionUtils
            .createTrackSelections(
                trackSelectionParams = trackSelectionParameters,
                tracks = tracks,
                audioIndex = 3,
                subtitleIndex = 8,
                source = source,
            ).also { result ->
                Assert.assertTrue(result.bothSelected)
                Assert.assertEquals("0:4", result.trackSelectionParameters.getAudioOverride()?.id)
                Assert.assertEquals(
                    "1:e:8",
                    result.trackSelectionParameters.getSubtitleOverride()?.id,
                )
            }
    }

    /**
     * MP4 with AV3A + DDP5.1 + DDP2.0 + AAC2.0: the server reports AV3A without a codec and ExoPlayer
     * does not expose it at all, so the audio tracks after it must not shift by one.
     */
    @Test
    fun `test ExoPlayer hidden unknown audio`() {
        val codecs = listOf("hevc", null, "eac3", "eac3", "aac", "mov_text")
        val base =
            TestTracks
                .Builder()
                .addVideo()
                .addAudio(4)
                .addSubtitle(1)
                .buildMediaSourceInfo()
        val source = base.copy(mediaStreams = base.mediaStreams!!.map { it.copy(codec = codecs[it.index]) })
        // ExoPlayer ids are the MP4 track ids; track 2 (AV3A) is missing
        val tracks =
            TestTracks(
                listOf(
                    TestTrack("1", 0, MediaStreamType.VIDEO, false),
                    TestTrack("3", 2, MediaStreamType.AUDIO, false),
                    TestTrack("4", 3, MediaStreamType.AUDIO, false),
                    TestTrack("5", 4, MediaStreamType.AUDIO, false),
                    TestTrack("6", 5, MediaStreamType.SUBTITLE, false),
                ),
            ).toTracks()
        val params = TrackSelectionParameters.Builder().build()

        fun select(audioIndex: Int) =
            TrackSelectionUtils.createTrackSelections(params, tracks, audioIndex, TrackIndex.DISABLED, source)

        select(4).also {
            Assert.assertTrue(it.audioSelected)
            Assert.assertEquals("5", it.trackSelectionParameters.getAudioOverride()?.id)
        }
        select(2).also {
            Assert.assertTrue(it.audioSelected)
            Assert.assertEquals("3", it.trackSelectionParameters.getAudioOverride()?.id)
        }
        select(1).also {
            Assert.assertEquals(TrackSelected.NOT_FOUND, it.audio)
        }
    }
}

internal fun TrackSelectionParameters.getAudioOverride(): Format? {
    this.overrides.forEach { (trackGroup, trackSelectionOverride) ->
        if (trackGroup.type == C.TRACK_TYPE_AUDIO) {
            return trackGroup.getFormat(trackSelectionOverride.trackIndices.first())
        }
    }
    return null
}

internal fun TrackSelectionParameters.getSubtitleOverride(): Format? {
    this.overrides.forEach { (trackGroup, trackSelectionOverride) ->
        if (trackGroup.type == C.TRACK_TYPE_TEXT) {
            return trackGroup.getFormat(trackSelectionOverride.trackIndices.first())
        }
    }
    return null
}
