package com.mengbo.service.hardkey;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import com.mengbo.service.hardkey.IHardKeyCallback;

/* loaded from: classes.dex */
public interface IHardKeylInterface extends IInterface {

    /* loaded from: classes.dex */
    public static class Default implements IHardKeylInterface {
        @Override // com.mengbo.service.hardkey.IHardKeylInterface
        public void abandonAudioFocus(int i) throws RemoteException {
        }

        @Override // com.mengbo.service.hardkey.IHardKeylInterface
        public void answerCall(int i) throws RemoteException {
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return null;
        }

        @Override // com.mengbo.service.hardkey.IHardKeylInterface
        public void awakeUp() throws RemoteException {
        }

        @Override // com.mengbo.service.hardkey.IHardKeylInterface
        public void dvrPhoto() throws RemoteException {
        }

        @Override // com.mengbo.service.hardkey.IHardKeylInterface
        public void dvrRecord() throws RemoteException {
        }

        @Override // com.mengbo.service.hardkey.IHardKeylInterface
        public double getVehicleSpeed() throws RemoteException {
            return 0.0d;
        }

        @Override // com.mengbo.service.hardkey.IHardKeylInterface
        public int getVehicleSpeedLimitState() throws RemoteException {
            return 0;
        }

        @Override // com.mengbo.service.hardkey.IHardKeylInterface
        public void hangCall(int i) throws RemoteException {
        }

        @Override // com.mengbo.service.hardkey.IHardKeylInterface
        public void registerHardKeyCallback(IHardKeyCallback iHardKeyCallback) throws RemoteException {
        }

        @Override // com.mengbo.service.hardkey.IHardKeylInterface
        public void requestAudioFocus(int i) throws RemoteException {
        }

        @Override // com.mengbo.service.hardkey.IHardKeylInterface
        public void requestInstrumentOTA(int i, String str) throws RemoteException {
        }

        @Override // com.mengbo.service.hardkey.IHardKeylInterface
        public void sendCall(int i) throws RemoteException {
        }

        @Override // com.mengbo.service.hardkey.IHardKeylInterface
        public void startDream() throws RemoteException {
        }
    }

    void abandonAudioFocus(int i) throws RemoteException;

    void answerCall(int i) throws RemoteException;

    void awakeUp() throws RemoteException;

    void dvrPhoto() throws RemoteException;

    void dvrRecord() throws RemoteException;

    double getVehicleSpeed() throws RemoteException;

    int getVehicleSpeedLimitState() throws RemoteException;

    void hangCall(int i) throws RemoteException;

    void registerHardKeyCallback(IHardKeyCallback iHardKeyCallback) throws RemoteException;

    void requestAudioFocus(int i) throws RemoteException;

    void requestInstrumentOTA(int i, String str) throws RemoteException;

    void sendCall(int i) throws RemoteException;

    void startDream() throws RemoteException;

    /* loaded from: classes.dex */
    public static abstract class Stub extends Binder implements IHardKeylInterface {
        private static final String DESCRIPTOR = "com.mengbo.service.hardkey.IHardKeylInterface";
        static final int TRANSACTION_abandonAudioFocus = 11;
        static final int TRANSACTION_answerCall = 9;
        static final int TRANSACTION_awakeUp = 2;
        static final int TRANSACTION_dvrPhoto = 4;
        static final int TRANSACTION_dvrRecord = 5;
        static final int TRANSACTION_getVehicleSpeed = 12;
        static final int TRANSACTION_getVehicleSpeedLimitState = 13;
        static final int TRANSACTION_hangCall = 8;
        static final int TRANSACTION_registerHardKeyCallback = 6;
        static final int TRANSACTION_requestAudioFocus = 10;
        static final int TRANSACTION_requestInstrumentOTA = 1;
        static final int TRANSACTION_sendCall = 7;
        static final int TRANSACTION_startDream = 3;

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IHardKeylInterface asInterface(IBinder iBinder) {
            if (iBinder == null) {
                return null;
            }
            IInterface queryLocalInterface = iBinder.queryLocalInterface(DESCRIPTOR);
            if (queryLocalInterface != null && (queryLocalInterface instanceof IHardKeylInterface)) {
                return (IHardKeylInterface) queryLocalInterface;
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
                    awakeUp();
                    parcel2.writeNoException();
                    return true;
                case 3:
                    parcel.enforceInterface(DESCRIPTOR);
                    startDream();
                    parcel2.writeNoException();
                    return true;
                case 4:
                    parcel.enforceInterface(DESCRIPTOR);
                    dvrPhoto();
                    parcel2.writeNoException();
                    return true;
                case 5:
                    parcel.enforceInterface(DESCRIPTOR);
                    dvrRecord();
                    parcel2.writeNoException();
                    return true;
                case 6:
                    parcel.enforceInterface(DESCRIPTOR);
                    registerHardKeyCallback(IHardKeyCallback.Stub.asInterface(parcel.readStrongBinder()));
                    parcel2.writeNoException();
                    return true;
                case 7:
                    parcel.enforceInterface(DESCRIPTOR);
                    sendCall(parcel.readInt());
                    parcel2.writeNoException();
                    return true;
                case 8:
                    parcel.enforceInterface(DESCRIPTOR);
                    hangCall(parcel.readInt());
                    parcel2.writeNoException();
                    return true;
                case 9:
                    parcel.enforceInterface(DESCRIPTOR);
                    answerCall(parcel.readInt());
                    parcel2.writeNoException();
                    return true;
                case 10:
                    parcel.enforceInterface(DESCRIPTOR);
                    requestAudioFocus(parcel.readInt());
                    parcel2.writeNoException();
                    return true;
                case 11:
                    parcel.enforceInterface(DESCRIPTOR);
                    abandonAudioFocus(parcel.readInt());
                    parcel2.writeNoException();
                    return true;
                case 12:
                    parcel.enforceInterface(DESCRIPTOR);
                    double vehicleSpeed = getVehicleSpeed();
                    parcel2.writeNoException();
                    parcel2.writeDouble(vehicleSpeed);
                    return true;
                case 13:
                    parcel.enforceInterface(DESCRIPTOR);
                    int vehicleSpeedLimitState = getVehicleSpeedLimitState();
                    parcel2.writeNoException();
                    parcel2.writeInt(vehicleSpeedLimitState);
                    return true;
                default:
                    return super.onTransact(i, parcel, parcel2, i2);
            }
        }

        /* loaded from: classes.dex */
        public static class Proxy implements IHardKeylInterface {
            public static IHardKeylInterface sDefaultImpl;
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

            @Override // com.mengbo.service.hardkey.IHardKeylInterface
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

            @Override // com.mengbo.service.hardkey.IHardKeylInterface
            public void awakeUp() throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (!this.mRemote.transact(2, obtain, obtain2, 0) && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().awakeUp();
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.mengbo.service.hardkey.IHardKeylInterface
            public void startDream() throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (!this.mRemote.transact(3, obtain, obtain2, 0) && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().startDream();
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.mengbo.service.hardkey.IHardKeylInterface
            public void dvrPhoto() throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (!this.mRemote.transact(4, obtain, obtain2, 0) && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().dvrPhoto();
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.mengbo.service.hardkey.IHardKeylInterface
            public void dvrRecord() throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (!this.mRemote.transact(5, obtain, obtain2, 0) && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().dvrRecord();
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.mengbo.service.hardkey.IHardKeylInterface
            public void registerHardKeyCallback(IHardKeyCallback iHardKeyCallback) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeStrongBinder(iHardKeyCallback != null ? iHardKeyCallback.asBinder() : null);
                    if (!this.mRemote.transact(6, obtain, obtain2, 0) && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().registerHardKeyCallback(iHardKeyCallback);
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.mengbo.service.hardkey.IHardKeylInterface
            public void sendCall(int i) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    if (!this.mRemote.transact(7, obtain, obtain2, 0) && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().sendCall(i);
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.mengbo.service.hardkey.IHardKeylInterface
            public void hangCall(int i) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    if (!this.mRemote.transact(8, obtain, obtain2, 0) && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().hangCall(i);
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.mengbo.service.hardkey.IHardKeylInterface
            public void answerCall(int i) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    if (!this.mRemote.transact(9, obtain, obtain2, 0) && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().answerCall(i);
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.mengbo.service.hardkey.IHardKeylInterface
            public void requestAudioFocus(int i) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    if (!this.mRemote.transact(10, obtain, obtain2, 0) && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().requestAudioFocus(i);
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.mengbo.service.hardkey.IHardKeylInterface
            public void abandonAudioFocus(int i) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    obtain.writeInt(i);
                    if (!this.mRemote.transact(11, obtain, obtain2, 0) && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().abandonAudioFocus(i);
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.mengbo.service.hardkey.IHardKeylInterface
            public double getVehicleSpeed() throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (!this.mRemote.transact(12, obtain, obtain2, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getVehicleSpeed();
                    }
                    obtain2.readException();
                    return obtain2.readDouble();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // com.mengbo.service.hardkey.IHardKeylInterface
            public int getVehicleSpeedLimitState() throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (!this.mRemote.transact(13, obtain, obtain2, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getVehicleSpeedLimitState();
                    }
                    obtain2.readException();
                    return obtain2.readInt();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }
        }

        public static boolean setDefaultImpl(IHardKeylInterface iHardKeylInterface) {
            if (Proxy.sDefaultImpl != null || iHardKeylInterface == null) {
                return false;
            }
            Proxy.sDefaultImpl = iHardKeylInterface;
            return true;
        }

        public static IHardKeylInterface getDefaultImpl() {
            return Proxy.sDefaultImpl;
        }
    }
}
