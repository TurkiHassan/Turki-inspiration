package com.ztv.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ztv.player.data.XtreamContentType;

import org.junit.Test;

public class PlayerControlsPolicyTest {
    @Test
    public void enhancedControls_areUsedForMoviesAndEpisodesOnly() {
        assertTrue(PlayerControlsPolicy.useEnhancedControls(XtreamContentType.MOVIE.name()));
        assertTrue(PlayerControlsPolicy.useEnhancedControls(XtreamContentType.EPISODE.name()));

        assertFalse(PlayerControlsPolicy.useEnhancedControls(XtreamContentType.LIVE.name()));
        assertFalse(PlayerControlsPolicy.useEnhancedControls(XtreamContentType.SERIES.name()));
        assertFalse(PlayerControlsPolicy.useEnhancedControls(null));
    }

    @Test
    public void enhancedControls_useLongerTimeoutThanLiveControls() {
        assertEquals(5000, PlayerControlsPolicy.controllerShowTimeoutMs(XtreamContentType.MOVIE.name()));
        assertEquals(5000, PlayerControlsPolicy.controllerShowTimeoutMs(XtreamContentType.EPISODE.name()));
        assertEquals(3500, PlayerControlsPolicy.controllerShowTimeoutMs(XtreamContentType.LIVE.name()));
    }

    @Test
    public void vodControls_useFifteenSecondSeekIncrements() {
        assertEquals(15000, PlayerControlsPolicy.seekBackIncrementMs());
        assertEquals(15000, PlayerControlsPolicy.seekForwardIncrementMs());
    }

    @Test
    public void nextEpisodeButton_isShownOnlyForEpisodesWithKnownNextEpisode() {
        assertTrue(PlayerControlsPolicy.showNextEpisodeButton(XtreamContentType.EPISODE.name(), true));

        assertFalse(PlayerControlsPolicy.showNextEpisodeButton(XtreamContentType.EPISODE.name(), false));
        assertFalse(PlayerControlsPolicy.showNextEpisodeButton(XtreamContentType.MOVIE.name(), true));
        assertFalse(PlayerControlsPolicy.showNextEpisodeButton(XtreamContentType.LIVE.name(), true));
        assertFalse(PlayerControlsPolicy.showNextEpisodeButton(null, true));
    }

    @Test
    public void backFromPlayer_reopensLibraryWhenPlayerIsTaskRoot() {
        assertTrue(PlayerControlsPolicy.shouldOpenLibraryOnBack(XtreamContentType.MOVIE.name(), true));
        assertTrue(PlayerControlsPolicy.shouldOpenLibraryOnBack(XtreamContentType.EPISODE.name(), true));
        assertTrue(PlayerControlsPolicy.shouldOpenLibraryOnBack(XtreamContentType.LIVE.name(), true));

        assertFalse(PlayerControlsPolicy.shouldOpenLibraryOnBack(XtreamContentType.MOVIE.name(), false));
        assertFalse(PlayerControlsPolicy.shouldOpenLibraryOnBack(null, true));
    }
}
