package com.github.lmfirefly.flycat.service.root;

import com.github.lmfirefly.flycat.service.root.IRootTunStateObserver;

interface IRootTunService {
    String startRootTun(String requestJson);
    String restartRootTun(String requestJson);
    String reloadActiveProfile(String requestJson);
    String stopRootTun();
    String queryStatus();
    String queryTunnelStateJson();
    long queryTrafficNow();
    long queryTrafficTotal();
    String queryConnectionsJson();
    String queryConnectionsOverviewJson();
    // queryAllProxyGroupsJson / queryProxyGroupJson 的代理组载荷为分隔符格式（组 \u0003 / 代理 \u0002 / 字段 \u0001，同原生端布局），
    // JSON 仅作回退；由 ProxyGroupPayload 统一编解码。queryProxyGroupNamesJson 仍是 JSON 字符串数组。
    String queryAllProxyGroupsJson(boolean excludeNotSelectable);
    String queryProxyGroupNamesJson(boolean excludeNotSelectable);
    String queryProxyGroupJson(String name, String sort);
    String queryProvidersJson();
    boolean patchSelector(String group, String name);
    boolean patchForceSelector(String group, String name);
    boolean closeConnection(String id);
    void closeAllConnections();
    String healthCheck(String group);
    String healthCheckProxy(String group, String proxyName);
    String updateProvider(String type, String name);
    void requestStop();
    String queryRecentLogsJson(long sinceSeq);
    void setLogConsumerActive(boolean active);
    oneway void appendStartupLog(String text);
    void registerStateObserver(in IRootTunStateObserver observer);
    void unregisterStateObserver(in IRootTunStateObserver observer);
}
