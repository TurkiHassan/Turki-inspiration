package com.ztv.player.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PosterUrlsTest {
    @Test
    public void acceptsHttpUrlsOnly() {
        assertTrue(PosterUrls.isUsable("https://img.example/poster.jpg"));
        assertTrue(PosterUrls.isUsable(" http://img.example/a.png "));
        assertFalse(PosterUrls.isUsable(""));
        assertFalse(PosterUrls.isUsable(null));
        assertFalse(PosterUrls.isUsable("{\"movie_image\":\"https://x\"}"));
    }
}
