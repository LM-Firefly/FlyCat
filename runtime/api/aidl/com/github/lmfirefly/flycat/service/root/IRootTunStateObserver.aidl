package com.github.lmfirefly.flycat.service.root;

interface IRootTunStateObserver {
    oneway void onStatusChanged(String statusJson);
}
