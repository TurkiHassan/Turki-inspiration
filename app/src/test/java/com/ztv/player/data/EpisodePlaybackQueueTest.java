package com.ztv.player.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class EpisodePlaybackQueueTest {
    @Test
    public void encodeAndDecode_preservesEpisodeOrderAndFields() {
        List<EpisodePlaybackQueue.Item> queue = Arrays.asList(
                new EpisodePlaybackQueue.Item(
                        "https://example.test/series/101.mp4",
                        new XtreamMediaItem(XtreamContentType.EPISODE, "Episode 1", 101, "1", "poster-1", "detail-1", "mp4")
                ),
                new EpisodePlaybackQueue.Item(
                        "https://example.test/series/102.mkv",
                        new XtreamMediaItem(XtreamContentType.EPISODE, "Episode 2", 102, "1", "poster-2", "detail-2", "mkv")
                )
        );

        List<EpisodePlaybackQueue.Item> decoded = EpisodePlaybackQueue.decode(EpisodePlaybackQueue.encode(queue));

        assertEquals(2, decoded.size());
        assertEquals("https://example.test/series/101.mp4", decoded.get(0).streamUrl());
        assertEquals("Episode 1", decoded.get(0).mediaItem().title());
        assertEquals(101, decoded.get(0).mediaItem().id());
        assertEquals("mp4", decoded.get(0).mediaItem().containerExtension());
        assertEquals("Episode 2", decoded.get(1).mediaItem().title());
        assertEquals(102, decoded.get(1).mediaItem().id());
        assertEquals("mkv", decoded.get(1).mediaItem().containerExtension());
    }

    @Test
    public void decode_returnsEmptyQueueForMalformedInput() {
        assertTrue(EpisodePlaybackQueue.decode("bad-data").isEmpty());
    }
}
