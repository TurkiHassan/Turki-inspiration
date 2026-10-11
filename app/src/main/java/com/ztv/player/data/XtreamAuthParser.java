package com.ztv.player.data;

import org.json.JSONException;
import org.json.JSONObject;

public final class XtreamAuthParser {
    private XtreamAuthParser() {
    }

    public static boolean isAuthenticated(String json) throws JSONException {
        JSONObject root = new JSONObject(json == null ? "{}" : json);
        JSONObject userInfo = root.optJSONObject("user_info");
        if (userInfo == null) {
            return false;
        }
        int auth = userInfo.optInt("auth", 0);
        String status = userInfo.optString("status", "");
        return auth == 1 && !"Disabled".equalsIgnoreCase(status) && !"Banned".equalsIgnoreCase(status);
    }

    public static XtreamAccountInfo parseAccountInfo(String json) throws JSONException {
        JSONObject root = new JSONObject(json == null ? "{}" : json);
        JSONObject userInfo = root.optJSONObject("user_info");
        if (userInfo == null) {
            return new XtreamAccountInfo(false, "", 0L);
        }
        int auth = userInfo.optInt("auth", 0);
        String status = userInfo.optString("status", "");
        boolean authenticated = auth == 1
                && !"Disabled".equalsIgnoreCase(status)
                && !"Banned".equalsIgnoreCase(status);
        long expiry = parseExpiry(userInfo);
        return new XtreamAccountInfo(authenticated, status, expiry);
    }

    /** exp_date is unix seconds as a string; "null"/"0"/empty means the line never expires. */
    private static long parseExpiry(JSONObject userInfo) {
        String raw = userInfo.optString("exp_date", "");
        if (raw == null || raw.isEmpty() || "null".equalsIgnoreCase(raw) || "0".equals(raw)) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }
}
