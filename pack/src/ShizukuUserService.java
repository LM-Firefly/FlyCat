package dev.flycat.loader;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;
import java.lang.reflect.Method;

/**
 * 用于超级岛更新期间短暂的 XMSF 防火墙绕过的 Shizuku 用户服务。
 *
 * <p>Shizuku 从客户端自身的 DEX 文件加载此类，因此它位于加载器 DEX（APK 的 {@code classes.dex}）中，shell 用户可以读取；位于 {@code /data/user/0/<pkg>} 下的打包负载则无法被其遍历。</p>
 *
 * <p>Binder 契约必须与 {@code com.github.lmfirefly.flycat.runtime.service.shizuku.IPrivilegedService} 保持同步，防火墙调用则与 {@code ...shizuku.OemDenyFirewall} 保持同步。</p>
 */
public final class ShizukuUserService extends Binder {
    private static final String TAG = "FlyCatShizukuService";
    private static final String DESCRIPTOR = "com.github.lmfirefly.flycat.runtime.service.shizuku.IPrivilegedService";
    private static final int TRANSACTION_SET_PACKAGE_NETWORKING_ENABLED = FIRST_CALL_TRANSACTION;
    private static final int FIREWALL_CHAIN_OEM_DENY = 9;

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        if (code == INTERFACE_TRANSACTION) {
            if (reply != null) {
                reply.writeString(DESCRIPTOR);
            }
            return true;
        }
        if (code != TRANSACTION_SET_PACKAGE_NETWORKING_ENABLED) {
            return super.onTransact(code, data, reply, flags);
        }
        data.enforceInterface(DESCRIPTOR);
        int uid = data.readInt();
        boolean enabled = data.readInt() != 0;
        boolean result = setPackageNetworkingEnabled(uid, enabled);
        if (reply != null) {
            reply.writeNoException();
            reply.writeInt(result ? 1 : 0);
        }
        return true;
    }

    /**
     * 通过隐藏的连接 API 添加或移除 {@code uid} 的 OEM 拒绝防火墙规则。
     * 两次调用都以用户服务身份运行，即拥有 Shizuku 服务器的权限。
     */
    private static boolean setPackageNetworkingEnabled(int uid, boolean enabled) {
        try {
            ReflectionAccess.exemptHiddenApis();
        } catch (Throwable error) {
            Log.w(TAG, "Unable to exempt hidden APIs", error);
        }
        try {
            Method getService = Class.forName("android.os.ServiceManager").getMethod("getService", String.class);
            IBinder connectivityBinder = (IBinder) getService.invoke(null, "connectivity");
            if (connectivityBinder == null) {
                Log.e(TAG, "Connectivity service is not available");
                return false;
            }
            Object connectivityManager = Class.forName("android.net.IConnectivityManager$Stub").getMethod("asInterface", IBinder.class).invoke(null, connectivityBinder);
            connectivityManager.getClass().getMethod("setFirewallChainEnabled", int.class, boolean.class).invoke(connectivityManager, FIREWALL_CHAIN_OEM_DENY, true);
            connectivityManager.getClass().getMethod("setUidFirewallRule", int.class, int.class, int.class).invoke(connectivityManager, FIREWALL_CHAIN_OEM_DENY, uid, enabled ? 0 : 2);
            Log.d(TAG, "Networking of uid " + uid + " enabled: " + enabled);
            return true;
        } catch (Throwable error) {
            Log.e(TAG, "Failed to update networking of uid " + uid, error);
            return false;
        }
    }
}
