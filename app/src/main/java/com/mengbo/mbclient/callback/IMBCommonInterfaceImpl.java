package com.mengbo.mbclient.callback;

import android.os.RemoteException;
import com.mengbo.service.common.IMBCommonInterface;
import com.mengbo.service.common.IMBCommonServiceCallback;

/* loaded from: classes.dex */
public class IMBCommonInterfaceImpl {
    private IMBCommonInterface imbCommonInterface;

    public IMBCommonInterfaceImpl(IMBCommonInterface iMBCommonInterface) {
        this.imbCommonInterface = iMBCommonInterface;
    }

    public void onDestroy() {
        this.imbCommonInterface = null;
    }

    public boolean isConnected() {
        IMBCommonInterface iMBCommonInterface = this.imbCommonInterface;
        if (iMBCommonInterface == null) {
            return false;
        }
        try {
            return iMBCommonInterface.isConnected();
        } catch (RemoteException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void requestInstrumentOTA(int i, String str) {
        IMBCommonInterface iMBCommonInterface = this.imbCommonInterface;
        if (iMBCommonInterface != null) {
            try {
                iMBCommonInterface.requestInstrumentOTA(i, str);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public void requestInstrumentLOG(String str) {
        IMBCommonInterface iMBCommonInterface = this.imbCommonInterface;
        if (iMBCommonInterface != null) {
            try {
                iMBCommonInterface.requestInstrumentLOG(str);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public int request(int i, String str) {
        IMBCommonInterface iMBCommonInterface = this.imbCommonInterface;
        if (iMBCommonInterface != null) {
            try {
                iMBCommonInterface.request(i, str);
                return 0;
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return -1;
    }

    public void registerMBCommonServiceCallback(IMBCommonServiceCallback iMBCommonServiceCallback) {
        IMBCommonInterface iMBCommonInterface = this.imbCommonInterface;
        if (iMBCommonInterface != null) {
            try {
                iMBCommonInterface.registerMBCommonServiceCallback(iMBCommonServiceCallback);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public void unRegisterMBCommonServiceCallback(IMBCommonServiceCallback iMBCommonServiceCallback) {
        IMBCommonInterface iMBCommonInterface = this.imbCommonInterface;
        if (iMBCommonInterface != null) {
            try {
                iMBCommonInterface.unRegisterMBCommonServiceCallback(iMBCommonServiceCallback);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendCall(int i) {
        IMBCommonInterface iMBCommonInterface = this.imbCommonInterface;
        if (iMBCommonInterface != null) {
            try {
                iMBCommonInterface.sendCall(i);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public void hangCall(int i) {
        IMBCommonInterface iMBCommonInterface = this.imbCommonInterface;
        if (iMBCommonInterface != null) {
            try {
                iMBCommonInterface.hangCall(i);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public void answerCall(int i) {
        IMBCommonInterface iMBCommonInterface = this.imbCommonInterface;
        if (iMBCommonInterface != null) {
            try {
                iMBCommonInterface.answerCall(i);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }
}
