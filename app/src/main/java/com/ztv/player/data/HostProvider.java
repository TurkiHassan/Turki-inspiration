package com.ztv.player.data;

import java.util.List;

public interface HostProvider {
    List<String> orderedHosts();

    void markHealthy(String host);
}
