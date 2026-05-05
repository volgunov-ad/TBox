package com.mengbo.service.common;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import com.mengbo.service.common.IMBCommonServiceCallback;

/* loaded from: classes.dex */
public interface IMBCommonInterface extends IInterface {

    /* loaded from: classes.dex */
    public static class Default implements IMBCommonInterface {
        @Override // com.mengbo.service.common.IMBCommonInterface
        public void answerCall(int i) throws RemoteException {
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return null;
        }

        @Override // com.mengbo.service.common.IMBCommonInterface
        public void hangCall(int i) throws RemoteException {
        }

        @Override // com.mengbo.service.common.IMBCommonInterface
        public boolean isConnected() throws RemoteException {
            return false;
        }

        @Override // com.mengbo.service.common.IMBCommonInterface
        public void registerMBCommonServiceCallback(IMBCommonServiceCallback iMBCommonServiceCallback) throws RemoteException {
        }

        @Override // com.mengbo.service.common.IMBCommonInterface
        public void request(int i, String str) throws RemoteException {
        }

        @Override // com.mengbo.service.common.IMBCommonInterface
        public void requestInstrumentLOG(String str) throws RemoteException {
        }

        @Override // com.mengbo.service.common.IMBCommonInterface
        public void requestInstrumentOTA(int i, String str) throws RemoteException {
        }

        @Override // com.mengbo.service.common.IMBCommonInterface
        public void sendCall(int i) throws RemoteException {
        }

        @Override // com.mengbo.service.common.IMBCommonInterface
        public void unRegisterMBCommonServiceCallback(IMBCommonServiceCallback iMBCommonServiceCallback) throws RemoteException {
        }
    }

    void answerCall(int i) throws RemoteException;

    void hangCall(int i) throws RemoteException;

    boolean isConnected() throws RemoteException;

    void registerMBCommonServiceCallback(IMBCommonServiceCallback iMBCommonServiceCallback) throws RemoteException;

    void request(int i, String str) throws RemoteException;

    void requestInstrumentLOG(String str) throws RemoteException;

    void requestInstrumentOTA(int i, String str) throws RemoteException;

    void sendCall(int i) throws RemoteException;

    void unRegisterMBCommonServiceCallback(IMBCommonServiceCallback iMBCommonServiceCallback) throws RemoteException;

    /* loaded from: classes.dex */
    public static abstract class Stub extends Binder implements IMBCommonInterface {
        private static final String DESCRIPTOR = "com.mengbo.service.common.IMBCommonInterface";
        static final int TRANSACTION_answerCall = 8;
        static final int TRANSACTION_hangCall = 7;
        static final int TRANSACTION_isConnected = 9;
        static final int TRANSACTION_registerMBCommonServiceCallback = 4;
        static final int TRANSACTION_request = 3;
        static final int TRANSACTION_requestInstrumentLOG = 2;
        static final int TRANSACTION_requestInstrumentOTA = 1;
        static final int TRANSACTION_sendCall = 6;
        static final int TRANSACTION_unRegisterMBCommonServiceCallback = 5;

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IMBCommonInterface asInterface(IBinder iBinder) {
            if (iBinder == null) {
                return null;
            }
            IInterface queryLocalInterface = iBinder.queryLocalInterface(DESCRIPTOR);
            if (queryLocalInterface != null && (queryLocalInterface instanceof IMBCommonInterface)) {
                return (IMBCommonInterface) queryLocalInterface;
            }
            return new Proxy(iBinder);
        }

        @Override // android.os.Binder
        public boolean onTransact(int i, Parcel parcel, Parcel parcel2, int i2) throws RemoteException {
            if (i == 1598968902) {
                parcel2.writeString(DESCRIPTOR);
                return true;
            }
            switch (i) {
                case 1:
                    parcel.enforceInterface(DESCRIPTOR);
                    requestInstrumentOTA(parcel.readInt(), parcel.readString());
                    parcel2.writeNoException();
                    return true;
                case 2:
                    parcel.enforceInterface(DESCRIPTOR);
                    requestInstrumentLOG(parcel.readString());
                    parcel2.writeNoException();
                    return true;
                case 3:
                    parcel.enforceInterface(DESCRIPTOR);
                    request(parcel.readInt(), parcel.readString());
                    parcel2.writeNoException();
                    return true;
                case 4:
                    parcel.enforceInterface(DESCRIPTOR);
                    registerMBCommonServiceCallback(IMBCommonServiceCallback.Stub.asInterface(parcel.readStrongBinder()));
                    parcel2.writeNoException();
                    return true;
                case 5:
                    parcel.enforceInterface(DESCRIPTOR);
                    unRegisterMBCommonServiceCallback(IMBCommonServiceCallback.Stub.asInterface(parcel.readStrongBinder()));
                    parcel2.writeNoException();
                    return true;
                case 6:
                    parcel.enforceInterface(DESCRIPTOR);
                    sendCall(parcel.readInt());
                    parcel2.writeNoException();
                    return true;
                case 7:
                    parcel.enforceInterface(DESCRIPTOR);
                    hangCall(parcel.readInt());
                    parcel2.writeNoException();
                    return true;
                case 8:
                    parcel.enforceInterface(DESCRIPTOR);
                    answerCall(parcel.readInt());
                    parcel2.writeNoException();
                    return true;
                case 9:
                    parcel.enforceInterface(DESCRIPTOR);
                    boolean isConnected = isConnected();
                    parcel2.writeNoException();
                    parcel2.writeInt(isConnected ? 1 : 0);
                    return true;
                default:
                    return super.onTransact(i, parcel, parcel2, i2);
            }
        }

        /* loaded from: classes.dex */
        public static class Proxy implements IMBCommonInterface {
            public static IMBCommonInterface sDefaultImpl;
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

            @Override // com.mengbo.service.common.IMBCommonInterface
            public void requestInstrumentOTA(int i, String str) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    obtain.writeString(str);
                    if (!this.mRemote.transact(1, obtain, obtain2, 0) && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().requestInstrumentOTA(i, str);
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.mengbo.service.common.IMBCommonInterface
            public void requestInstrumentLOG(String str) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeString(str);
                    if (!this.mRemote.transact(2, obtain, obtain2, 0) && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().requestInstrumentLOG(str);
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.mengbo.service.common.IMBCommonInterface
            public void request(int i, String str) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    obtain.writeString(str);
                    if (!this.mRemote.transact(3, obtain, obtain2, 0) && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().request(i, str);
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.mengbo.service.common.IMBCommonInterface
            public void registerMBCommonServiceCallback(IMBCommonServiceCallback iMBCommonServiceCallback) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeStrongBinder(iMBCommonServiceCallback != null ? iMBCommonServiceCallback.asBinder() : null);
                    if (!this.mRemote.transact(4, obtain, obtain2, 0) && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().registerMBCommonServiceCallback(iMBCommonServiceCallback);
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.mengbo.service.common.IMBCommonInterface
            public void unRegisterMBCommonServiceCallback(IMBCommonServiceCallback iMBCommonServiceCallback) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeStrongBinder(iMBCommonServiceCallback != null ? iMBCommonServiceCallback.asBinder() : null);
                    if (!this.mRemote.transact(5, obtain, obtain2, 0) && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().unRegisterMBCommonServiceCallback(iMBCommonServiceCallback);
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.mengbo.service.common.IMBCommonInterface
            public void sendCall(int i) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    if (!this.mRemote.transact(6, obtain, obtain2, 0) && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().sendCall(i);
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.mengbo.service.common.IMBCommonInterface
            public void hangCall(int i) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    if (!this.mRemote.transact(7, obtain, obtain2, 0) && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().hangCall(i);
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.mengbo.service.common.IMBCommonInterface
            public void answerCall(int i) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    if (!this.mRemote.transact(8, obtain, obtain2, 0) && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().answerCall(i);
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.mengbo.service.common.IMBCommonInterface
            public boolean isConnected() throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (!this.mRemote.transact(9, obtain, obtain2, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().isConnected();
                    }
                    obtain2.readException();
                    return obtain2.readInt() != 0;
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }
        }

        public static boolean setDefaultImpl(IMBCommonInterface iMBCommonInterface) {
            if (Proxy.sDefaultImpl != null || iMBCommonInterface == null) {
                return false;
            }
            Proxy.sDefaultImpl = iMBCommonInterface;
            return true;
        }

        public static IMBCommonInterface getDefaultImpl() {
            return Proxy.sDefaultImpl;
        }
    }
}
