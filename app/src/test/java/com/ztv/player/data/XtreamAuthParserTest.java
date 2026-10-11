package com.ztv.player.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class XtreamAuthParserTest {
    @Test
    public void isAuthenticated_acceptsActiveAuthResponse() throws Exception {
        String json = "{\"user_info\":{\"auth\":1,\"status\":\"Active\"}}";

        assertTrue(XtreamAuthParser.isAuthenticated(json));
    }

    @Test
    public void isAuthenticated_rejectsDisabledOrMissingAuthResponse() throws Exception {
        assertFalse(XtreamAuthParser.isAuthenticated("{\"user_info\":{\"auth\":0,\"status\":\"Disabled\"}}"));
        assertFalse(XtreamAuthParser.isAuthenticated("{}"));
    }
}
