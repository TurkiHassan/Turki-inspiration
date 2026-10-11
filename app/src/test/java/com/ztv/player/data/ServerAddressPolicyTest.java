package com.ztv.player.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ServerAddressPolicyTest {
    @Test
    public void acceptsBareAndFullAddresses() {
        assertEquals("http://example.com:8080", ServerAddressPolicy.normalize("example.com:8080"));
        assertEquals("http://example.com:8080", ServerAddressPolicy.normalize("  http://Example.com:8080/ "));
        assertEquals("https://tv.example.com", ServerAddressPolicy.normalize("https://tv.example.com"));
        assertEquals("http://10.0.0.5:2095", ServerAddressPolicy.normalize("10.0.0.5:2095"));
    }

    @Test
    public void stripsPastedPlaylistPaths() {
        assertEquals("http://example.com:8080", ServerAddressPolicy.normalize(
                "http://example.com:8080/get.php?username=a&password=b&type=m3u"));
        assertEquals("http://example.com:8080", ServerAddressPolicy.normalize(
                "example.com:8080/player_api.php"));
    }

    @Test
    public void rejectsUnusableInput() {
        assertEquals("", ServerAddressPolicy.normalize(""));
        assertEquals("", ServerAddressPolicy.normalize(null));
        assertEquals("", ServerAddressPolicy.normalize("not a host"));
        assertEquals("", ServerAddressPolicy.normalize("ftp://example.com"));
        assertEquals("", ServerAddressPolicy.normalize("example.com:99999"));
    }

    @Test
    public void displayDropsDefaultScheme() {
        assertEquals("example.com:8080", ServerAddressPolicy.display("http://example.com:8080"));
        assertEquals("https://example.com", ServerAddressPolicy.display("https://example.com"));
    }
}
