/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: /root/android-sdk/build-tools/37.0.0/aidl -o /mnt/d/firefly/Documents/Github/LM-Firefly/FlyCat/runtime/api/src -I /mnt/d/firefly/Documents/Github/LM-Firefly/FlyCat/runtime/api/aidl /mnt/d/firefly/Documents/Github/LM-Firefly/FlyCat/runtime/api/aidl/com/github/lmfirefly/flycat/service/root/IRootTunStateObserver.aidl
 *
 * DO NOT CHECK THIS FILE INTO A CODE TREE (e.g. git, etc..).
 * ALWAYS GENERATE THIS FILE FROM UPDATED AIDL COMPILER
 * AS A BUILD INTERMEDIATE ONLY. THIS IS NOT SOURCE CODE.
 */
package com.github.lmfirefly.flycat.service.root;
public interface IRootTunStateObserver extends android.os.IInterface
{
  /** Default implementation for IRootTunStateObserver. */
  public static class Default implements com.github.lmfirefly.flycat.service.root.IRootTunStateObserver
  {
    @Override public void onStatusChanged(java.lang.String statusJson) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.github.lmfirefly.flycat.service.root.IRootTunStateObserver
  {
    /** Construct the stub and attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.github.lmfirefly.flycat.service.root.IRootTunStateObserver interface,
     * generating a proxy if needed.
     */
    public static com.github.lmfirefly.flycat.service.root.IRootTunStateObserver asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.github.lmfirefly.flycat.service.root.IRootTunStateObserver))) {
        return ((com.github.lmfirefly.flycat.service.root.IRootTunStateObserver)iin);
      }
      return new com.github.lmfirefly.flycat.service.root.IRootTunStateObserver.Stub.Proxy(obj);
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
        case TRANSACTION_onStatusChanged:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          this.onStatusChanged(_arg0);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static final class Proxy implements com.github.lmfirefly.flycat.service.root.IRootTunStateObserver
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
      @Override public void onStatusChanged(java.lang.String statusJson) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(statusJson);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onStatusChanged, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_onStatusChanged = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "com.github.lmfirefly.flycat.service.root.IRootTunStateObserver";
  public void onStatusChanged(java.lang.String statusJson) throws android.os.RemoteException;
}
