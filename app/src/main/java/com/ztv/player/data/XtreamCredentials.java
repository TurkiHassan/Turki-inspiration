package com.ztv.player.data;

public final class XtreamCredentials {
    private final String host;
    private final String username;
    private final String password;

    public XtreamCredentials(String host, String username, String password) {
        this.host = host == null ? "" : host.trim();
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
    }

    public String host() {
        return host;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public XtreamCredentials withHost(String newHost) {
        return new XtreamCredentials(newHost, username, password);
    }

    public boolean isComplete() {
        return !host.isEmpty() && !username.isEmpty() && !password.isEmpty();
    }
}
