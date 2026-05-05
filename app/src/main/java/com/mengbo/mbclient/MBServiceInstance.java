package com.mengbo.mbclient;

import android.content.ComponentName;
import android.content.Context;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;
import com.mengbo.mbclient.MBServiceConnection;
import com.mengbo.mbclient.callback.IMBCommonInterfaceImpl;
import com.mengbo.mbclient.callback.IMBCommonServiceCallbackImpl;
import com.mengbo.mbclient.callback.OnResponseListener;
import com.mengbo.mbclient.defines.MBCCanRequest;
import com.mengbo.service.common.IMBCommonInterface;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

/* loaded from: classes.dex */
public class MBServiceInstance {
    private static final String AISERVICE_PACKAGE = "com.mengbo.aiservice";
    private static final String COMMON_SERVICE = "com.mengbo.service.common.CommonService";
    private static volatile MBServiceInstance instance;
    private IMBCommonInterfaceImpl imbCommonInterface;
    private Context mContext;
    private MBServiceConnection mbServiceConnection;
    private Map<Integer, OnResponseListener> cachedListenerMap = new HashMap();
    private MBServiceConnection.ServiceConnectionCallback serviceConnectionCallback = new MBServiceConnection.ServiceConnectionCallback() { // from class: com.mengbo.mbclient.MBServiceInstance.1
        @Override // com.mengbo.mbclient.MBServiceConnection.ServiceConnectionCallback
        public void onServiceConnected(IBinder iBinder) {
            if (MBServiceInstance.this.imbCommonInterface == null || !MBServiceInstance.this.imbCommonInterface.isConnected()) {
                return;
            }
            MBServiceInstance.this.imbCommonInterface.registerMBCommonServiceCallback(MBServiceInstance.this.imbCommonServiceCallback);
        }

        @Override // com.mengbo.mbclient.MBServiceConnection.ServiceConnectionCallback
        public IInterface onServiceCreated(IBinder iBinder) {
            IMBCommonInterface asInterface = IMBCommonInterface.Stub.asInterface(iBinder);
            MBServiceInstance.this.imbCommonInterface = new IMBCommonInterfaceImpl(asInterface);
            return asInterface;
        }
    };
    private IMBCommonServiceCallbackImpl imbCommonServiceCallback = new IMBCommonServiceCallbackImpl(this.cachedListenerMap);

    private MBServiceInstance(Context context) {
        this.mContext = context;
        MBServiceConnection mBServiceConnection = new MBServiceConnection(new ComponentName(AISERVICE_PACKAGE, COMMON_SERVICE));
        this.mbServiceConnection = mBServiceConnection;
        mBServiceConnection.registServiceConnectionCallback(this.serviceConnectionCallback);
        this.mbServiceConnection.bindService(context);
    }

    public static void registerInstance(Context context) {
        if (instance == null) {
            synchronized (MBServiceInstance.class) {
                if (instance == null) {
                    instance = new MBServiceInstance(context);
                }
            }
        }
    }

    public static void unregisterInstance() {
        if (instance != null) {
            instance.onDestroy();
            instance = null;
        }
    }

    public static MBServiceInstance getInstance() {
        return instance;
    }

    private void onDestroy() {
        IMBCommonInterfaceImpl iMBCommonInterfaceImpl = this.imbCommonInterface;
        if (iMBCommonInterfaceImpl != null) {
            iMBCommonInterfaceImpl.unRegisterMBCommonServiceCallback(this.imbCommonServiceCallback);
        }
        this.cachedListenerMap.clear();
        this.imbCommonServiceCallback.onDestroy();
        MBServiceConnection mBServiceConnection = this.mbServiceConnection;
        if (mBServiceConnection != null) {
            mBServiceConnection.destroy();
        }
        this.mbServiceConnection = null;
        this.imbCommonServiceCallback = null;
        this.imbCommonInterface = null;
    }

    public int request(int i, String str, OnResponseListener onResponseListener) throws RemoteException {
        IMBCommonInterfaceImpl iMBCommonInterfaceImpl = this.imbCommonInterface;
        if (iMBCommonInterfaceImpl == null || !iMBCommonInterfaceImpl.isConnected()) {
            Log.d("chitin", "Service is disconnected");
            return -1;
        }
        if (i == MBCCanRequest.CAN_SET_VEHICLE_PROPERTY.getValue() || i == MBCCanRequest.CAN_GET_VEHICLE_PROPERTY.getValue()) {
            try {
                new JSONObject(str).put("pid", Process.myPid());
            } catch (Exception unused) {
            }
        }
        this.cachedListenerMap.put(Integer.valueOf(i), onResponseListener);
        IMBCommonInterfaceImpl iMBCommonInterfaceImpl2 = this.imbCommonInterface;
        if (iMBCommonInterfaceImpl2 != null) {
            return iMBCommonInterfaceImpl2.request(i, str);
        }
        return -1;
    }

    public IMBCommonInterfaceImpl getCommonInterface() {
        return this.imbCommonInterface;
    }
}