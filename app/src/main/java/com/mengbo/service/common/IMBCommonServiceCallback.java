package com.mengbo.service.common;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/* loaded from: classes.dex */
public interface IMBCommonServiceCallback extends IInterface {

    /* loaded from: classes.dex */
    public static class Default implements IMBCommonServiceCallback {
        @Override // android.os.IInterface
        public IBinder asBinder() {
            return null;
        }

        @Override // com.mengbo.service.common.IMBCommonServiceCallback
        public void onClusterCallback(int i, String str) throws RemoteException {
        }

        @Override // com.mengbo.service.common.IMBCommonServiceCallback
        public void onResponse(int i, String str) throws RemoteException {
        }

        @Override // com.mengbo.service.common.IMBCommonServiceCallback
        public void onTBoxCallback(int i, String str) throws RemoteException {
        }
    }

    void onClusterCallback(int i, String str) throws RemoteException;

    void onResponse(int i, String str) throws RemoteException;

    void onTBoxCallback(int i, String str) throws RemoteException;

    /* loaded from: classes.dex */
    public static abstract class Stub extends Binder implements IMBCommonServiceCallback {
        private static final String DESCRIPTOR = "com.mengbo.service.common.IMBCommonServiceCallback";
        static final int TRANSACTION_onClusterCallback = 2;
        static final int TRANSACTION_onResponse = 3;
        static final int TRANSACTION_onTBoxCallback = 1;

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IMBCommonServiceCallback asInterface(IBinder iBinder) {
            if (iBinder == null) {
                return null;
            }
            IInterface queryLocalInterface = iBinder.queryLocalInterface(DESCRIPTOR);
            if (queryLocalInterface != null && (queryLocalInterface instanceof IMBCommonServiceCallback)) {
                return (IMBCommonServiceCallback) queryLocalInterface;
            }
            return new Proxy(iBinder);
        }

        @Override // android.os.Binder
        public boolean onTransact(int i, Parcel parcel, Parcel parcel2, int i2) throws RemoteException {
            if (i == 1) {
                parcel.enforceInterface(DESCRIPTOR);
                onTBoxCallback(parcel.readInt(), parcel.readString());
                parcel2.writeNoException();
                return true;
            }
            if (i == 2) {
                parcel.enforceInterface(DESCRIPTOR);
                onClusterCallback(parcel.readInt(), parcel.readString());
                parcel2.writeNoException();
                return true;
            }
            if (i != 3) {
                if (i == 1598968902) {
                    parcel2.writeString(DESCRIPTOR);
                    return true;
                }
                return super.onTransact(i, parcel, parcel2, i2);
            }
            parcel.enforceInterface(DESCRIPTOR);
            onResponse(parcel.readInt(), parcel.readString());
            parcel2.writeNoException();
            return true;
        }

        /* loaded from: classes.dex */
        public static class Proxy implements IMBCommonServiceCallback {
            public static IMBCommonServiceCallback sDefaultImpl;
            private IBinder mRemote;

            public String getInterfaceDescriptor() {
                return Stub.DESCRIPTOR;
            }

            Proxy(IBinder iBinder) {
                this.mRemote = iBinder;
            }

            @Override // android.os.IInterface
            public IBinder asBinder() {
                return this.mRemote;
            }

            @Override // com.mengbo.service.common.IMBCommonServiceCallback
            public void onTBoxCallback(int i, String str) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    obtain.writeString(str);
                    if (!this.mRemote.transact(1, obtain, obtain2, 0) && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().onTBoxCallback(i, str);
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.mengbo.service.common.IMBCommonServiceCallback
            public void onClusterCallback(int i, String str) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    obtain.writeString(str);
                    if (!this.mRemote.transact(2, obtain, obtain2, 0) && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().onClusterCallback(i, str);
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.mengbo.service.common.IMBCommonServiceCallback
            public void onResponse(int i, String str) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    obtain.writeString(str);
                    if (!this.mRemote.transact(3, obtain, obtain2, 0) && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().onResponse(i, str);
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }
        }

        public static boolean setDefaultImpl(IMBCommonServiceCallback iMBCommonServiceCallback) {
            if (Proxy.sDefaultImpl != null || iMBCommonServiceCallback == null) {
                return false;
            }
            Proxy.sDefaultImpl = iMBCommonServiceCallback;
            return true;
        }

        public static IMBCommonServiceCallback getDefaultImpl() {
            return Proxy.sDefaultImpl;
        }
    }
}
