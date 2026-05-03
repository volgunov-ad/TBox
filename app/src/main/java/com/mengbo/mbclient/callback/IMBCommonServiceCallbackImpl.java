package com.mengbo.mbclient.callback;

import android.os.RemoteException;
import android.util.Log;
import com.mengbo.service.common.IMBCommonServiceCallback;
import java.util.Map;

/* loaded from: classes.dex */
public class IMBCommonServiceCallbackImpl extends IMBCommonServiceCallback.Stub {
    private Map<Integer, OnResponseListener> cachedListenerMap;

    @Override // com.mengbo.service.common.IMBCommonServiceCallback
    public void onClusterCallback(int i, String str) throws RemoteException {
    }

    @Override // com.mengbo.service.common.IMBCommonServiceCallback
    public void onTBoxCallback(int i, String str) throws RemoteException {
    }

    public IMBCommonServiceCallbackImpl(Map<Integer, OnResponseListener> map) {
        this.cachedListenerMap = null;
        this.cachedListenerMap = map;
    }

    public void onDestroy() {
        Map<Integer, OnResponseListener> map = this.cachedListenerMap;
        if (map != null) {
            map.clear();
            this.cachedListenerMap = null;
        }
    }

    @Override // com.mengbo.service.common.IMBCommonServiceCallback
    public void onResponse(int i, String str) throws RemoteException {
        Log.d("chitin", String.format("mbClientLib onResponse responseCode: %d, content: %s", Integer.valueOf(i), str));
        Map<Integer, OnResponseListener> map = this.cachedListenerMap;
        if (map == null || map.get(Integer.valueOf(i)) == null) {
            return;
        }
        this.cachedListenerMap.get(Integer.valueOf(i)).onResponse(str);
    }
}
