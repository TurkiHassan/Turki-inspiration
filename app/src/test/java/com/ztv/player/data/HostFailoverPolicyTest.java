package com.ztv.player.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

public class HostFailoverPolicyTest {
    private static final List<String> HOSTS = Arrays.asList(
            "https://primary.example/",
            "backup.example:8080",
            "https://third.example",
            "https://primary.example"
    );

    @Test
    public void orderedHosts_normalizesDeduplicatesAndKeepsOwnerPriority() {
        assertEquals(
                Arrays.asList(
                        "https://primary.example",
                        "http://backup.example:8080",
                        "https://third.example"),
                HostFailoverPolicy.orderedHosts(HOSTS, ""));
    }

    @Test
    public void orderedHosts_promotesOnlyAConfiguredHealthyHost() {
        assertEquals(
                Arrays.asList(
                        "http://backup.example:8080",
                        "https://primary.example",
                        "https://third.example"),
                HostFailoverPolicy.orderedHosts(HOSTS, "backup.example:8080"));

        assertEquals(
                "https://primary.example",
                HostFailoverPolicy.orderedHosts(HOSTS, "https://user-controlled.example").get(0));
    }

    @Test
    public void nextUntriedHost_skipsEveryAttemptedServer() {
        LinkedHashSet<String> attempted = new LinkedHashSet<>(Arrays.asList(
                "https://primary.example",
                "http://backup.example:8080"));

        assertEquals(
                "https://third.example",
                HostFailoverPolicy.nextUntriedHost(
                        HostFailoverPolicy.orderedHosts(HOSTS, ""), attempted));
    }

    @Test
    public void failoverClassification_rejectsAccountAndDecoderFailures() {
        assertTrue(HostFailoverPolicy.shouldFailOverHttpStatus(503));
        assertTrue(HostFailoverPolicy.shouldFailOverHttpStatus(521));
        assertFalse(HostFailoverPolicy.shouldFailOverHttpStatus(401));
        assertFalse(HostFailoverPolicy.shouldFailOverHttpStatus(404));

        assertTrue(HostFailoverPolicy.shouldFailOverPlaybackError(2001));
        assertFalse(HostFailoverPolicy.shouldFailOverPlaybackError(4001));
    }
}
