package com.mengbo.mbclient;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import java.util.List;

/* loaded from: classes.dex */
public class MBServiceConnection implements ServiceConnection {
    private ComponentName componentName;
    private IInterface iInterface;
    private Context mContext;
    private ServiceConnectionCallback serviceConnectionCallback;
    private Runnable bindTask = new Runnable() { // from class: com.mengbo.mbclient.MBServiceConnection.1
        @Override // java.lang.Runnable
        public void run() {
            if (MBServiceConnection.this.mContext == null) {
                return;
            }
            Context applicationContext = MBServiceConnection.this.mContext.getApplicationContext();
            Intent intent = new Intent();
            intent.setComponent(MBServiceConnection.this.componentName);
            applicationContext.getApplicationContext().bindService(MBServiceConnection.this.createExplicitFromImplicitIntent(applicationContext, intent), MBServiceConnection.this, 1);
        }
    };
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    /* loaded from: classes.dex */
    public interface ServiceConnectionCallback {
        void onServiceConnected(IBinder iBinder);

        IInterface onServiceCreated(IBinder iBinder);
    }

    public MBServiceConnection(ComponentName componentName) {
        this.componentName = componentName;
    }

    public void registServiceConnectionCallback(ServiceConnectionCallback serviceConnectionCallback) {
        this.serviceConnectionCallback = serviceConnectionCallback;
    }

    @Override // android.content.ServiceConnection
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        if (iBinder == null) {
            bindService(this.mContext);
            return;
        }
        ServiceConnectionCallback serviceConnectionCallback = this.serviceConnectionCallback;
        if (serviceConnectionCallback != null) {
            this.iInterface = serviceConnectionCallback.onServiceCreated(iBinder);
        }
        if (this.iInterface == null) {
            bindService(this.mContext);
            return;
        }
        ServiceConnectionCallback serviceConnectionCallback2 = this.serviceConnectionCallback;
        if (serviceConnectionCallback2 != null) {
            serviceConnectionCallback2.onServiceConnected(iBinder);
        }
    }

    @Override // android.content.ServiceConnection
    public void onServiceDisconnected(ComponentName componentName) {
        bindService(this.mContext);
    }

    @Override // android.content.ServiceConnection
    public void onBindingDied(ComponentName componentName) {
        bindService(this.mContext);
    }

    @Override // android.content.ServiceConnection
    public void onNullBinding(ComponentName componentName) {
        bindService(this.mContext);
    }

    public void bindService(Context context) {
        this.iInterface = null;
        if (context == null) {
            return;
        }
        this.mContext = context;
        this.mainHandler.removeCallbacks(this.bindTask);
        this.mainHandler.postDelayed(this.bindTask, 1000L);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public Intent createExplicitFromImplicitIntent(Context context, Intent intent) {
        List<ResolveInfo> queryIntentServices = context.getPackageManager().queryIntentServices(intent, 131072);
        if (queryIntentServices == null || queryIntentServices.size() < 1) {
            return intent;
        }
        ResolveInfo resolveInfo = queryIntentServices.get(0);
        ComponentName componentName = new ComponentName(resolveInfo.serviceInfo.packageName, resolveInfo.serviceInfo.name);
        Intent intent2 = new Intent(intent);
        intent2.setComponent(componentName);
        return intent2;
    }

    public void destroy() {
        Context context = this.mContext;
        this.mContext = null;
        this.serviceConnectionCallback = null;
        if (context != null) {
            try {
                context.unbindService(this);
            } catch (Exception unused) {
            }
        }
    }

    public IInterface getiInterface() {
        return this.iInterface;
    }
}