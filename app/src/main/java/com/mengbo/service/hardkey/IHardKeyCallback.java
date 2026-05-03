package com.mengbo.service.hardkey;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/* loaded from: classes.dex */
public interface IHardKeyCallback extends IInterface {

    /* loaded from: classes.dex */
    public static class Default implements IHardKeyCallback {
        @Override // android.os.IInterface
        public IBinder asBinder() {
            return null;
        }

        @Override // com.mengbo.service.hardkey.IHardKeyCallback
        public void onError(String str) throws RemoteException {
        }

        @Override // com.mengbo.service.hardkey.IHardKeyCallback
        public void onStatusChanged(int i) throws RemoteException {
        }
    }

    void onError(String str) throws RemoteException;

    void onStatusChanged(int i) throws RemoteException;

    /* loaded from: classes.dex */
    public static abstract class Stub extends Binder implements IHardKeyCallback {
        private static final String DESCRIPTOR = "com.mengbo.service.hardkey.IHardKeyCallback";
        static final int TRANSACTION_onError = 2;
        static final int TRANSACTION_onStatusChanged = 1;

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IHardKeyCallback asInterface(IBinder iBinder) {
            if (iBinder == null) {
                return null;
            }
            IInterface queryLocalInterface = iBinder.queryLocalInterface(DESCRIPTOR);
            if (queryLocalInterface != null && (queryLocalInterface instanceof IHardKeyCallback)) {
                return (IHardKeyCallback) queryLocalInterface;
            }
            return new Proxy(iBinder);
        }

        @Override // android.os.Binder
        public boolean onTransact(int i, Parcel parcel, Parcel parcel2, int i2) throws RemoteException {
            if (i == 1) {
                parcel.enforceInterface(DESCRIPTOR);
                onStatusChanged(parcel.readInt());
                parcel2.writeNoException();
                return true;
            }
            if (i != 2) {
                if (i == 1598968902) {
                    parcel2.writeString(DESCRIPTOR);
                    return true;
                }
                return super.onTransact(i, parcel, parcel2, i2);
            }
            parcel.enforceInterface(DESCRIPTOR);
            onError(parcel.readString());
            parcel2.writeNoException();
            return true;
        }

        /* loaded from: classes.dex */
        public static class Proxy implements IHardKeyCallback {
            public static IHardKeyCallback sDefaultImpl;
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

            @Override // com.mengbo.service.hardkey.IHardKeyCallback
            public void onStatusChanged(int i) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    if (!this.mRemote.transact(1, obtain, obtain2, 0) && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().onStatusChanged(i);
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.mengbo.service.hardkey.IHardKeyCallback
            public void onError(String str) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeString(str);
                    if (!this.mRemote.transact(2, obtain, obtain2, 0) && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().onError(str);
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }
        }

        public static boolean setDefaultImpl(IHardKeyCallback iHardKeyCallback) {
            if (Proxy.sDefaultImpl != null || iHardKeyCallback == null) {
                return false;
            }
            Proxy.sDefaultImpl = iHardKeyCallback;
            return true;
        }

        public static IHardKeyCallback getDefaultImpl() {
            return Proxy.sDefaultImpl;
        }
    }
}
