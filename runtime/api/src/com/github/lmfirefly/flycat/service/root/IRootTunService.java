/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: /root/android-sdk/build-tools/37.0.0/aidl -o /mnt/d/firefly/Documents/Github/LM-Firefly/FlyCat/runtime/api/src -I /mnt/d/firefly/Documents/Github/LM-Firefly/FlyCat/runtime/api/aidl /mnt/d/firefly/Documents/Github/LM-Firefly/FlyCat/runtime/api/aidl/com/github/lmfirefly/flycat/service/root/IRootTunService.aidl
 *
 * DO NOT CHECK THIS FILE INTO A CODE TREE (e.g. git, etc..).
 * ALWAYS GENERATE THIS FILE FROM UPDATED AIDL COMPILER
 * AS A BUILD INTERMEDIATE ONLY. THIS IS NOT SOURCE CODE.
 */
package com.github.lmfirefly.flycat.service.root;
public interface IRootTunService extends android.os.IInterface
{
  /** Default implementation for IRootTunService. */
  public static class Default implements com.github.lmfirefly.flycat.service.root.IRootTunService
  {
    @Override public java.lang.String startRootTun(java.lang.String requestJson) throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.lang.String restartRootTun(java.lang.String requestJson) throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.lang.String reloadActiveProfile(java.lang.String requestJson) throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.lang.String stopRootTun() throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.lang.String queryStatus() throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.lang.String queryTunnelStateJson() throws android.os.RemoteException
    {
      return null;
    }
    @Override public long queryTrafficNow() throws android.os.RemoteException
    {
      return 0L;
    }
    @Override public long queryTrafficTotal() throws android.os.RemoteException
    {
      return 0L;
    }
    @Override public java.lang.String queryConnectionsJson() throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.lang.String queryConnectionsOverviewJson() throws android.os.RemoteException
    {
      return null;
    }
    // queryAllProxyGroupsJson / queryProxyGroupJson 的代理组载荷为分隔符格式（组 \u0003 / 代理 \u0002 / 字段 \u0001，同原生端布局），
    // JSON 仅作回退；由 ProxyGroupPayload 统一编解码。queryProxyGroupNamesJson 仍是 JSON 字符串数组。
    @Override public java.lang.String queryAllProxyGroupsJson(boolean excludeNotSelectable) throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.lang.String queryProxyGroupNamesJson(boolean excludeNotSelectable) throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.lang.String queryProxyGroupJson(java.lang.String name, java.lang.String sort) throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.lang.String queryProvidersJson() throws android.os.RemoteException
    {
      return null;
    }
    @Override public boolean patchSelector(java.lang.String group, java.lang.String name) throws android.os.RemoteException
    {
      return false;
    }
    @Override public boolean patchForceSelector(java.lang.String group, java.lang.String name) throws android.os.RemoteException
    {
      return false;
    }
    @Override public boolean closeConnection(java.lang.String id) throws android.os.RemoteException
    {
      return false;
    }
    @Override public void closeAllConnections() throws android.os.RemoteException
    {
    }
    @Override public java.lang.String healthCheck(java.lang.String group) throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.lang.String healthCheckProxy(java.lang.String group, java.lang.String proxyName) throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.lang.String updateProvider(java.lang.String type, java.lang.String name) throws android.os.RemoteException
    {
      return null;
    }
    @Override public void requestStop() throws android.os.RemoteException
    {
    }
    @Override public java.lang.String queryRecentLogsJson(long sinceSeq) throws android.os.RemoteException
    {
      return null;
    }
    @Override public void setLogConsumerActive(boolean active) throws android.os.RemoteException
    {
    }
    @Override public void appendStartupLog(java.lang.String text) throws android.os.RemoteException
    {
    }
    @Override public void registerStateObserver(com.github.lmfirefly.flycat.service.root.IRootTunStateObserver observer) throws android.os.RemoteException
    {
    }
    @Override public void unregisterStateObserver(com.github.lmfirefly.flycat.service.root.IRootTunStateObserver observer) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.github.lmfirefly.flycat.service.root.IRootTunService
  {
    /** Construct the stub and attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.github.lmfirefly.flycat.service.root.IRootTunService interface,
     * generating a proxy if needed.
     */
    public static com.github.lmfirefly.flycat.service.root.IRootTunService asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.github.lmfirefly.flycat.service.root.IRootTunService))) {
        return ((com.github.lmfirefly.flycat.service.root.IRootTunService)iin);
      }
      return new com.github.lmfirefly.flycat.service.root.IRootTunService.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(DESCRIPTOR);
      }
      switch (code)
      {
        case TRANSACTION_startRootTun:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          java.lang.String _result = this.startRootTun(_arg0);
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_restartRootTun:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          java.lang.String _result = this.restartRootTun(_arg0);
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_reloadActiveProfile:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          java.lang.String _result = this.reloadActiveProfile(_arg0);
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_stopRootTun:
        {
          java.lang.String _result = this.stopRootTun();
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_queryStatus:
        {
          java.lang.String _result = this.queryStatus();
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_queryTunnelStateJson:
        {
          java.lang.String _result = this.queryTunnelStateJson();
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_queryTrafficNow:
        {
          long _result = this.queryTrafficNow();
          reply.writeNoException();
          reply.writeLong(_result);
          break;
        }
        case TRANSACTION_queryTrafficTotal:
        {
          long _result = this.queryTrafficTotal();
          reply.writeNoException();
          reply.writeLong(_result);
          break;
        }
        case TRANSACTION_queryConnectionsJson:
        {
          java.lang.String _result = this.queryConnectionsJson();
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_queryConnectionsOverviewJson:
        {
          java.lang.String _result = this.queryConnectionsOverviewJson();
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_queryAllProxyGroupsJson:
        {
          boolean _arg0;
          _arg0 = (0!=data.readInt());
          java.lang.String _result = this.queryAllProxyGroupsJson(_arg0);
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_queryProxyGroupNamesJson:
        {
          boolean _arg0;
          _arg0 = (0!=data.readInt());
          java.lang.String _result = this.queryProxyGroupNamesJson(_arg0);
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_queryProxyGroupJson:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          java.lang.String _arg1;
          _arg1 = data.readString();
          java.lang.String _result = this.queryProxyGroupJson(_arg0, _arg1);
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_queryProvidersJson:
        {
          java.lang.String _result = this.queryProvidersJson();
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_patchSelector:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          java.lang.String _arg1;
          _arg1 = data.readString();
          boolean _result = this.patchSelector(_arg0, _arg1);
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        case TRANSACTION_patchForceSelector:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          java.lang.String _arg1;
          _arg1 = data.readString();
          boolean _result = this.patchForceSelector(_arg0, _arg1);
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        case TRANSACTION_closeConnection:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          boolean _result = this.closeConnection(_arg0);
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        case TRANSACTION_closeAllConnections:
        {
          this.closeAllConnections();
          reply.writeNoException();
          break;
        }
        case TRANSACTION_healthCheck:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          java.lang.String _result = this.healthCheck(_arg0);
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_healthCheckProxy:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          java.lang.String _arg1;
          _arg1 = data.readString();
          java.lang.String _result = this.healthCheckProxy(_arg0, _arg1);
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_updateProvider:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          java.lang.String _arg1;
          _arg1 = data.readString();
          java.lang.String _result = this.updateProvider(_arg0, _arg1);
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_requestStop:
        {
          this.requestStop();
          reply.writeNoException();
          break;
        }
        case TRANSACTION_queryRecentLogsJson:
        {
          long _arg0;
          _arg0 = data.readLong();
          java.lang.String _result = this.queryRecentLogsJson(_arg0);
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_setLogConsumerActive:
        {
          boolean _arg0;
          _arg0 = (0!=data.readInt());
          this.setLogConsumerActive(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_appendStartupLog:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          this.appendStartupLog(_arg0);
          break;
        }
        case TRANSACTION_registerStateObserver:
        {
          com.github.lmfirefly.flycat.service.root.IRootTunStateObserver _arg0;
          _arg0 = com.github.lmfirefly.flycat.service.root.IRootTunStateObserver.Stub.asInterface(data.readStrongBinder());
          this.registerStateObserver(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_unregisterStateObserver:
        {
          com.github.lmfirefly.flycat.service.root.IRootTunStateObserver _arg0;
          _arg0 = com.github.lmfirefly.flycat.service.root.IRootTunStateObserver.Stub.asInterface(data.readStrongBinder());
          this.unregisterStateObserver(_arg0);
          reply.writeNoException();
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static final class Proxy implements com.github.lmfirefly.flycat.service.root.IRootTunService
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public final java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      @Override public java.lang.String startRootTun(java.lang.String requestJson) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(requestJson);
          boolean _status = mRemote.transact(Stub.TRANSACTION_startRootTun, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.lang.String restartRootTun(java.lang.String requestJson) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(requestJson);
          boolean _status = mRemote.transact(Stub.TRANSACTION_restartRootTun, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.lang.String reloadActiveProfile(java.lang.String requestJson) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(requestJson);
          boolean _status = mRemote.transact(Stub.TRANSACTION_reloadActiveProfile, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.lang.String stopRootTun() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_stopRootTun, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.lang.String queryStatus() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_queryStatus, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.lang.String queryTunnelStateJson() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_queryTunnelStateJson, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public long queryTrafficNow() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        long _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_queryTrafficNow, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readLong();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public long queryTrafficTotal() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        long _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_queryTrafficTotal, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readLong();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.lang.String queryConnectionsJson() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_queryConnectionsJson, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.lang.String queryConnectionsOverviewJson() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_queryConnectionsOverviewJson, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      // queryAllProxyGroupsJson / queryProxyGroupJson 的代理组载荷为分隔符格式（组 \u0003 / 代理 \u0002 / 字段 \u0001，同原生端布局），
      // JSON 仅作回退；由 ProxyGroupPayload 统一编解码。queryProxyGroupNamesJson 仍是 JSON 字符串数组。
      @Override public java.lang.String queryAllProxyGroupsJson(boolean excludeNotSelectable) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(((excludeNotSelectable)?(1):(0)));
          boolean _status = mRemote.transact(Stub.TRANSACTION_queryAllProxyGroupsJson, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.lang.String queryProxyGroupNamesJson(boolean excludeNotSelectable) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(((excludeNotSelectable)?(1):(0)));
          boolean _status = mRemote.transact(Stub.TRANSACTION_queryProxyGroupNamesJson, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.lang.String queryProxyGroupJson(java.lang.String name, java.lang.String sort) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(name);
          _data.writeString(sort);
          boolean _status = mRemote.transact(Stub.TRANSACTION_queryProxyGroupJson, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.lang.String queryProvidersJson() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_queryProvidersJson, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public boolean patchSelector(java.lang.String group, java.lang.String name) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(group);
          _data.writeString(name);
          boolean _status = mRemote.transact(Stub.TRANSACTION_patchSelector, _data, _reply, 0);
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public boolean patchForceSelector(java.lang.String group, java.lang.String name) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(group);
          _data.writeString(name);
          boolean _status = mRemote.transact(Stub.TRANSACTION_patchForceSelector, _data, _reply, 0);
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public boolean closeConnection(java.lang.String id) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(id);
          boolean _status = mRemote.transact(Stub.TRANSACTION_closeConnection, _data, _reply, 0);
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public void closeAllConnections() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_closeAllConnections, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public java.lang.String healthCheck(java.lang.String group) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(group);
          boolean _status = mRemote.transact(Stub.TRANSACTION_healthCheck, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.lang.String healthCheckProxy(java.lang.String group, java.lang.String proxyName) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(group);
          _data.writeString(proxyName);
          boolean _status = mRemote.transact(Stub.TRANSACTION_healthCheckProxy, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.lang.String updateProvider(java.lang.String type, java.lang.String name) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(type);
          _data.writeString(name);
          boolean _status = mRemote.transact(Stub.TRANSACTION_updateProvider, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public void requestStop() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_requestStop, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public java.lang.String queryRecentLogsJson(long sinceSeq) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeLong(sinceSeq);
          boolean _status = mRemote.transact(Stub.TRANSACTION_queryRecentLogsJson, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public void setLogConsumerActive(boolean active) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(((active)?(1):(0)));
          boolean _status = mRemote.transact(Stub.TRANSACTION_setLogConsumerActive, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void appendStartupLog(java.lang.String text) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(text);
          boolean _status = mRemote.transact(Stub.TRANSACTION_appendStartupLog, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      @Override public void registerStateObserver(com.github.lmfirefly.flycat.service.root.IRootTunStateObserver observer) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(observer);
          boolean _status = mRemote.transact(Stub.TRANSACTION_registerStateObserver, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void unregisterStateObserver(com.github.lmfirefly.flycat.service.root.IRootTunStateObserver observer) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(observer);
          boolean _status = mRemote.transact(Stub.TRANSACTION_unregisterStateObserver, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_startRootTun = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_restartRootTun = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_reloadActiveProfile = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_stopRootTun = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
    static final int TRANSACTION_queryStatus = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
    static final int TRANSACTION_queryTunnelStateJson = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
    static final int TRANSACTION_queryTrafficNow = (android.os.IBinder.FIRST_CALL_TRANSACTION + 6);
    static final int TRANSACTION_queryTrafficTotal = (android.os.IBinder.FIRST_CALL_TRANSACTION + 7);
    static final int TRANSACTION_queryConnectionsJson = (android.os.IBinder.FIRST_CALL_TRANSACTION + 8);
    static final int TRANSACTION_queryConnectionsOverviewJson = (android.os.IBinder.FIRST_CALL_TRANSACTION + 9);
    static final int TRANSACTION_queryAllProxyGroupsJson = (android.os.IBinder.FIRST_CALL_TRANSACTION + 10);
    static final int TRANSACTION_queryProxyGroupNamesJson = (android.os.IBinder.FIRST_CALL_TRANSACTION + 11);
    static final int TRANSACTION_queryProxyGroupJson = (android.os.IBinder.FIRST_CALL_TRANSACTION + 12);
    static final int TRANSACTION_queryProvidersJson = (android.os.IBinder.FIRST_CALL_TRANSACTION + 13);
    static final int TRANSACTION_patchSelector = (android.os.IBinder.FIRST_CALL_TRANSACTION + 14);
    static final int TRANSACTION_patchForceSelector = (android.os.IBinder.FIRST_CALL_TRANSACTION + 15);
    static final int TRANSACTION_closeConnection = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16);
    static final int TRANSACTION_closeAllConnections = (android.os.IBinder.FIRST_CALL_TRANSACTION + 17);
    static final int TRANSACTION_healthCheck = (android.os.IBinder.FIRST_CALL_TRANSACTION + 18);
    static final int TRANSACTION_healthCheckProxy = (android.os.IBinder.FIRST_CALL_TRANSACTION + 19);
    static final int TRANSACTION_updateProvider = (android.os.IBinder.FIRST_CALL_TRANSACTION + 20);
    static final int TRANSACTION_requestStop = (android.os.IBinder.FIRST_CALL_TRANSACTION + 21);
    static final int TRANSACTION_queryRecentLogsJson = (android.os.IBinder.FIRST_CALL_TRANSACTION + 22);
    static final int TRANSACTION_setLogConsumerActive = (android.os.IBinder.FIRST_CALL_TRANSACTION + 23);
    static final int TRANSACTION_appendStartupLog = (android.os.IBinder.FIRST_CALL_TRANSACTION + 24);
    static final int TRANSACTION_registerStateObserver = (android.os.IBinder.FIRST_CALL_TRANSACTION + 25);
    static final int TRANSACTION_unregisterStateObserver = (android.os.IBinder.FIRST_CALL_TRANSACTION + 26);
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "com.github.lmfirefly.flycat.service.root.IRootTunService";
  public java.lang.String startRootTun(java.lang.String requestJson) throws android.os.RemoteException;
  public java.lang.String restartRootTun(java.lang.String requestJson) throws android.os.RemoteException;
  public java.lang.String reloadActiveProfile(java.lang.String requestJson) throws android.os.RemoteException;
  public java.lang.String stopRootTun() throws android.os.RemoteException;
  public java.lang.String queryStatus() throws android.os.RemoteException;
  public java.lang.String queryTunnelStateJson() throws android.os.RemoteException;
  public long queryTrafficNow() throws android.os.RemoteException;
  public long queryTrafficTotal() throws android.os.RemoteException;
  public java.lang.String queryConnectionsJson() throws android.os.RemoteException;
  public java.lang.String queryConnectionsOverviewJson() throws android.os.RemoteException;
  // queryAllProxyGroupsJson / queryProxyGroupJson 的代理组载荷为分隔符格式（组 \u0003 / 代理 \u0002 / 字段 \u0001，同原生端布局），
  // JSON 仅作回退；由 ProxyGroupPayload 统一编解码。queryProxyGroupNamesJson 仍是 JSON 字符串数组。
  public java.lang.String queryAllProxyGroupsJson(boolean excludeNotSelectable) throws android.os.RemoteException;
  public java.lang.String queryProxyGroupNamesJson(boolean excludeNotSelectable) throws android.os.RemoteException;
  public java.lang.String queryProxyGroupJson(java.lang.String name, java.lang.String sort) throws android.os.RemoteException;
  public java.lang.String queryProvidersJson() throws android.os.RemoteException;
  public boolean patchSelector(java.lang.String group, java.lang.String name) throws android.os.RemoteException;
  public boolean patchForceSelector(java.lang.String group, java.lang.String name) throws android.os.RemoteException;
  public boolean closeConnection(java.lang.String id) throws android.os.RemoteException;
  public void closeAllConnections() throws android.os.RemoteException;
  public java.lang.String healthCheck(java.lang.String group) throws android.os.RemoteException;
  public java.lang.String healthCheckProxy(java.lang.String group, java.lang.String proxyName) throws android.os.RemoteException;
  public java.lang.String updateProvider(java.lang.String type, java.lang.String name) throws android.os.RemoteException;
  public void requestStop() throws android.os.RemoteException;
  public java.lang.String queryRecentLogsJson(long sinceSeq) throws android.os.RemoteException;
  public void setLogConsumerActive(boolean active) throws android.os.RemoteException;
  public void appendStartupLog(java.lang.String text) throws android.os.RemoteException;
  public void registerStateObserver(com.github.lmfirefly.flycat.service.root.IRootTunStateObserver observer) throws android.os.RemoteException;
  public void unregisterStateObserver(com.github.lmfirefly.flycat.service.root.IRootTunStateObserver observer) throws android.os.RemoteException;
}
