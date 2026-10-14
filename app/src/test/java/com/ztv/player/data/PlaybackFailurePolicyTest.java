package com.ztv.player.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlaybackFailurePolicyTest {

    @Test
    public void providerRejection_matchesClientRefusalsOnly() {
        assertTrue(PlaybackFailurePolicy.isProviderRejection(400));
        assertTrue(PlaybackFailurePolicy.isProviderRejection(403));
        assertTrue(PlaybackFailurePolicy.isProviderRejection(404));
        assertFalse(PlaybackFailurePolicy.isProviderRejection(408));
        assertFalse(PlaybackFailurePolicy.isProviderRejection(429));
    }

    @Test
    public void providerRejection_ignoresSuccessAndServerSideStatuses() {
        assertFalse(PlaybackFailurePolicy.isProviderRejection(0));
        assertFalse(PlaybackFailurePolicy.isProviderRejection(200));
        assertFalse(PlaybackFailurePolicy.isProviderRejection(500));
        assertFalse(PlaybackFailurePolicy.isProviderRejection(503));
    }

    @Test
    public void shouldRetry_onlyForTransientFailuresWithinBudget() {
        assertTrue(PlaybackFailurePolicy.shouldRetry(false, 0, 1));
        assertFalse(PlaybackFailurePolicy.shouldRetry(false, 1, 1));
        assertFalse(PlaybackFailurePolicy.shouldRetry(true, 0, 1));
    }

    @Test
    public void shouldFailOver_skipsProviderRejections() {
        int ioError = 2003;
        assertTrue(PlaybackFailurePolicy.shouldFailOver(false, ioError));
        assertFalse(PlaybackFailurePolicy.shouldFailOver(true, ioError));
    }
}
