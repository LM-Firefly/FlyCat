package com.github.yumelira.yumebox.runtime.service.root;

interface IRootTunStateObserver {
    oneway void onStatusChanged(String statusJson);
}
