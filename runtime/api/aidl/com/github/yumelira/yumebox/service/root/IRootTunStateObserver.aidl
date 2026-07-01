package com.github.yumelira.yumebox.service.root;

interface IRootTunStateObserver {
    oneway void onStatusChanged(String statusJson);
}
