.class public Lcom/adayo/androidauto/service/AAService;
.super Landroid/app/Service;
.source "AAService.java"

# interfaces
.implements Lcom/google/android/projection/transport/TransportFactory$Callback;


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/adayo/androidauto/service/AAService$AccecptBroadcast;,
        Lcom/adayo/androidauto/service/AAService$ConnectMode;,
        Lcom/adayo/androidauto/service/AAService$AndroidAutoDeleteDeviceReceiver;,
        Lcom/adayo/androidauto/service/AAService$AndroidAutoConnectSecondReceiver;,
        Lcom/adayo/androidauto/service/AAService$AndroidAutoConnectReceiver;
    }
.end annotation


# static fields
.field private static final ACTION_USB_ACCESSORY_PERMISSION:Ljava/lang/String; = "com.google.android.projection.sink.transport.usb.ACTION_USB_ACCESSORY_PERMISSION"

.field private static final ACTION_USB_DEVICE_PERMISSION:Ljava/lang/String; = "com.google.android.projection.sink.transport.usb.ACTION_USB_DEVICE_PERMISSION"

.field public static final APP_APPS:I = 0x4

.field public static final APP_CENTER:I = 0x2

.field public static final APP_MUSIC:I = 0x3

.field public static final APP_PHONE:I = 0x1

.field public static final APP_SENSORS:I = 0x0

.field private static final AUDIO_BITS_MEDIA:I = 0x10

.field private static final AUDIO_BITS_TTS:I = 0x10

.field private static final AUDIO_CHANNELS_MEDIA:I = 0x2

.field private static final AUDIO_CHANNELS_TTS:I = 0x1

.field private static final DBG_LATENCY:Z = false

.field private static final DECODER_ADDITIONAL_DEPTH:I = 0x1

.field private static final HIDE_INSTRUCT:I = 0x3ea

.field private static final IPHONE_VID:I = 0x5ac

.field private static final MSG_ACTIVITY_DELAY_PAUSE:I = 0x66

.field private static final MSG_AUTO_CONNECT_BT:I = 0x2718

.field protected static final MSG_DO_AAP_CONNECT:I = 0x64

.field private static final MSG_IS_GET_KEY_FRAME_DATA:I = 0x2717

.field private static final MSG_IS_RESTART_APP:I = 0x2716

.field private static final MSG_LATER_SEND_SIRIVOLUME:I = 0x1

.field private static final MSG_MUTE_STATE_WARNNING:I = 0x69

.field private static final MSG_PROMPTVIEW_SHOW:I = 0x65

.field private static final MSG_STARTAA_AFTER_TERM_ACCEPTED:I = 0x68

.field private static final MSG_TO_AA_INTERNAL_APP:I = 0x67

.field private static final PHONE_SCREEN_OFF_MSG:Ljava/lang/String; = "Please turn phone screen on."

.field private static final SAMPLING_RATE_MEDIA:I = 0xbb80

.field private static final SAMPLING_RATE_TTS:I = 0xbb80

.field private static final SHOW_INSTRUCT:I = 0x3e9

.field private static final SOCKET_ACCEPT_TIMEOUT_MILLIS:I = 0x3e8

.field public static final TAG:Ljava/lang/String; = "AHU.AAS.20240704"

.field public static final TAG1:Ljava/lang/String; = "AHU.BT."

.field public static final USB1:Ljava/lang/String; = "/dev/bus/usb/001"

.field public static final USB2:Ljava/lang/String; = "/dev/bus/usb/002"

.field public static final VIDEO_FPS_60:I = 0x3c

.field public static mAudioManager:Landroid/media/AudioManager;

.field private static mCarInfo:Lcom/google/android/projection/protocol/GalReceiver$CarInfo;

.field private static mEmulatedScreen:Lcom/google/android/projection/sink/video/DimensionUtils$ScreenSettings;

.field public static usbManager:Landroid/hardware/usb/UsbManager;


# instance fields
.field private BYEBYEREASON:I

.field private androidAutoSwitchReceiver:Lcom/adayo/androidauto/receivers/AndroidAutoSwitchReceiver;

.field public app:I

.field private carAdapter:Lcom/adayo/androidauto/CarAdapter;

.field private carManufacturer:Ljava/lang/String;

.field private carModel:Ljava/lang/String;

.field private carModelYear:Ljava/lang/String;

.field private clientCertificate:Ljava/lang/String;

.field private config:Lcom/adayo/androidauto/config/LoadJson;

.field private connectedUsb:Ljava/lang/String;

.field private displayName:Ljava/lang/String;

.field private driverPosition:I

.field private headunitMake:Ljava/lang/String;

.field private headunitModel:Ljava/lang/String;

.field private heightPhysical:I

.field private heightPixel:I

.field private inputInjectorListener:Lcom/google/android/projection/protocol/InputSource$InputInjector;

.field private instrumentClusterListener:Lcom/google/android/projection/sink/InstrumentClusterListener;

.field private isActivityShowing:Z

.field private isSendGetKeyFrameData:Z

.field private volatile isStartProjection:Z

.field private isStartUSBAA:Z

.field private isUSB2Device:Z

.field private mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

.field private mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

.field private mAccReceiver:Landroid/content/BroadcastReceiver;

.field private mAdayoBtManager:Lcom/adayo/androidauto/bt/AdayoBtManager;

.field private mAdayoSourceManager:Lcom/adayo/androidauto/AdayoSourceManager;

.field private mAndroidAutoWirelessService:Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;

.field private mAppInforManager:Lcom/adayo/androidauto/AppInfor/AppInforManager;

.field private final mAppMessageListener:Lcom/google/android/projection/protocol/GalReceiver$AppMessageListener;

.field private final mAudioFocusListener:Lcom/google/android/projection/protocol/GalReceiver$AudioFocusListener;

.field private mAudioFocusManager:Lcom/google/android/projection/sink/audio/AudioFocusManager;

.field private final mAudioLatencyChecker:Lcom/google/android/projection/sink/audio/AudioLatencyChecker;

.field private final mAudioSinkMediaStreamPlayer:Lcom/google/android/projection/sink/audio/AudioPlayer;

.field private final mAudioSinkMediaStreamPlayer_AAC:Lcom/google/android/projection/sink/audio/AudioPlayer;

.field private final mAudioSinkTtsStreamPlayer:Lcom/google/android/projection/sink/audio/AudioPlayer;

.field private final mAudioSinkTtsStreamPlayer_AAC:Lcom/google/android/projection/sink/audio/AudioPlayer;

.field private mAudioSourceListener:Lcom/google/android/projection/sink/audio/MicrophoneRecorder;

.field private final mByeByeHandler:Lcom/google/android/projection/protocol/GalReceiver$ByeByeHandler;

.field private mConnectMode:Lcom/adayo/androidauto/service/AAService$ConnectMode;

.field private mContext:Landroid/content/Context;

.field private mCurUSBMDSN:Ljava/lang/String;

.field private mDebugContext:Lcom/google/android/projection/sink/debug/DebugContext;

.field private final mDelayHandler:Landroid/os/Handler;

.field protected mFirstFrame:Lcom/google/android/projection/protocol/VideoFrame;

.field private mFpsTV:Landroid/widget/TextView;

.field private final mHandler:Landroid/os/Handler;

.field private mIConfigureDependManager:Lcom/adayo/androidauto/depend/IConfigureDependManager;

.field private mIFunction:Lcom/adayo/module/servicecenterproxy/IFunction;

.field mInstructHandler:Landroid/os/Handler;

.field private mIntentControllerReceiver:Landroid/content/BroadcastReceiver;

.field private volatile mIsAANaviRunningActive:Z

.field private mIsAAPConnected:Z

.field private mIsStartNavi:Z

.field private mLocalModeCallbacks:Lcom/adayo/androidauto/constants/LocalModeCallbacks;

.field private final mLock:Ljava/lang/Object;

.field mMainHandler:Landroid/os/Handler;

.field private mMessengerServer:Lcom/adayo/androidauto/service/MessengerServer;

.field private mMusicState:I

.field private mMusicStateBeforeLeavingSource:I

.field private mNavigationFocusManager:Lcom/google/android/projection/sink/navigation/NavigationFocusManager;

.field private final mProjectedModeIconReceiver:Lcom/google/android/projection/protocol/GalReceiver$ProjectedModeIconReceiver;

.field private final mProjectionListener:Lcom/google/android/projection/protocol/VideoSink$ProjectionListener;

.field private mProjectionRenderer:Lcom/adayo/androidauto/video/AAProjectionRenderer;

.field private final mProjectionRendererListener:Lcom/adayo/androidauto/video/AAProjectionRenderer$Listener;

.field private mPromptView:Landroid/view/View;

.field private mReconnectedMac:Ljava/lang/String;

.field private mSensorDispatcher:Lcom/google/android/projection/sink/sensors/SensorManager;

.field private mServiceCenterManager:Lcom/adayo/module/servicecenterproxy/ServiceCenterManager;

.field private mSettings:Lcom/google/android/projection/sink/settings/Settings;

.field mStatusHandler:Landroid/os/Handler;

.field private final mSurfaceTouchListener:Landroid/view/View$OnTouchListener;

.field private mSurfaceView:Landroid/view/SurfaceView;

.field private mSwcReceiver:Landroid/content/BroadcastReceiver;

.field private mTransport:Lcom/google/android/projection/protocol/Transport;

.field private mTransportFactory:Lcom/google/android/projection/transport/TransportFactory;

.field private mTransportHandle:Lcom/google/android/projection/transport/TransportFactory$FactoryHandle;

.field private mUsbAccessoryOrDeviceAttachReceiver:Landroid/content/BroadcastReceiver;

.field private final mUsbDisconnectedReceiver:Landroid/content/BroadcastReceiver;

.field private mUsbPermissionReceiver:Landroid/content/BroadcastReceiver;

.field private mVehicleInfoSensor:Lcom/adayo/androidauto/sensors/VehicleInfoSensor;

.field private final mVideoLatencyChecker:Lcom/google/android/projection/sink/video/VideoLatencyChecker;

.field private volatile mVoiceState:Z

.field private mVolumeType:I

.field private maxFps:I

.field private minstructView:Landroid/view/View;

.field private onPairingRequestTime:J

.field private par:F

.field private privateKey:Ljava/lang/String;

.field private rebootAndroidAutoTime:J

.field private scaleDpiH:I

.field private scaleDpiL:I

.field private scaleDpiM:I

.field stopReason:Lcom/google/android/projection/protocol/Transport$StopReason;

.field private usbDisConnect:Z

.field private userSwitchListener:Lcom/google/android/projection/protocol/GalReceiver$UserSwitchListener;

.field private viewingDistance:I

.field private widthPhysical:I

.field private widthPixel:I

.field private wiredPlugDialog:Lcom/adayo/androidauto/dialog/WiredPlugDialog;


# direct methods
.method public constructor <init>()V
    .locals 18

    move-object/from16 v0, p0

    .line 141
    invoke-direct/range {p0 .. p0}, Landroid/app/Service;-><init>()V

    const/4 v1, -0x1

    .line 181
    iput v1, v0, Lcom/adayo/androidauto/service/AAService;->app:I

    const/4 v1, 0x0

    .line 182
    iput-object v1, v0, Lcom/adayo/androidauto/service/AAService;->mFirstFrame:Lcom/google/android/projection/protocol/VideoFrame;

    const/4 v2, 0x0

    .line 183
    iput-boolean v2, v0, Lcom/adayo/androidauto/service/AAService;->isStartProjection:Z

    .line 184
    iput-object v1, v0, Lcom/adayo/androidauto/service/AAService;->instrumentClusterListener:Lcom/google/android/projection/sink/InstrumentClusterListener;

    const-string v3, ""

    .line 185
    iput-object v3, v0, Lcom/adayo/androidauto/service/AAService;->connectedUsb:Ljava/lang/String;

    .line 186
    iput-boolean v2, v0, Lcom/adayo/androidauto/service/AAService;->mVoiceState:Z

    .line 200
    iput-object v1, v0, Lcom/adayo/androidauto/service/AAService;->mAdayoBtManager:Lcom/adayo/androidauto/bt/AdayoBtManager;

    .line 206
    iput-boolean v2, v0, Lcom/adayo/androidauto/service/AAService;->isActivityShowing:Z

    .line 209
    iput-boolean v2, v0, Lcom/adayo/androidauto/service/AAService;->isUSB2Device:Z

    const-wide/16 v4, 0x0

    .line 221
    iput-wide v4, v0, Lcom/adayo/androidauto/service/AAService;->onPairingRequestTime:J

    .line 222
    iput-boolean v2, v0, Lcom/adayo/androidauto/service/AAService;->isSendGetKeyFrameData:Z

    .line 227
    iput-object v3, v0, Lcom/adayo/androidauto/service/AAService;->mReconnectedMac:Ljava/lang/String;

    .line 228
    invoke-static {}, Lcom/adayo/androidauto/config/LoadJson;->getInstance()Lcom/adayo/androidauto/config/LoadJson;

    move-result-object v3

    iput-object v3, v0, Lcom/adayo/androidauto/service/AAService;->config:Lcom/adayo/androidauto/config/LoadJson;

    .line 229
    invoke-static {}, Lcom/adayo/androidauto/CarAdapter;->getInstance()Lcom/adayo/androidauto/CarAdapter;

    move-result-object v3

    iput-object v3, v0, Lcom/adayo/androidauto/service/AAService;->carAdapter:Lcom/adayo/androidauto/CarAdapter;

    .line 231
    new-instance v3, Ljava/lang/Object;

    invoke-direct {v3}, Ljava/lang/Object;-><init>()V

    iput-object v3, v0, Lcom/adayo/androidauto/service/AAService;->mLock:Ljava/lang/Object;

    .line 232
    new-instance v3, Landroid/os/Handler;

    invoke-direct {v3}, Landroid/os/Handler;-><init>()V

    iput-object v3, v0, Lcom/adayo/androidauto/service/AAService;->mHandler:Landroid/os/Handler;

    .line 233
    new-instance v3, Landroid/os/Handler;

    invoke-direct {v3}, Landroid/os/Handler;-><init>()V

    iput-object v3, v0, Lcom/adayo/androidauto/service/AAService;->mDelayHandler:Landroid/os/Handler;

    .line 234
    new-instance v3, Lcom/google/android/projection/sink/video/VideoLatencyChecker;

    invoke-direct {v3}, Lcom/google/android/projection/sink/video/VideoLatencyChecker;-><init>()V

    iput-object v3, v0, Lcom/adayo/androidauto/service/AAService;->mVideoLatencyChecker:Lcom/google/android/projection/sink/video/VideoLatencyChecker;

    .line 235
    new-instance v3, Lcom/google/android/projection/sink/audio/AudioLatencyChecker;

    invoke-direct {v3}, Lcom/google/android/projection/sink/audio/AudioLatencyChecker;-><init>()V

    iput-object v3, v0, Lcom/adayo/androidauto/service/AAService;->mAudioLatencyChecker:Lcom/google/android/projection/sink/audio/AudioLatencyChecker;

    .line 238
    new-instance v3, Lcom/google/android/projection/sink/audio/AudioPlayer;

    const/4 v7, 0x3

    const v8, 0xbb80

    const/16 v9, 0xc

    const/4 v10, 0x2

    const/4 v11, 0x0

    move-object v6, v3

    invoke-direct/range {v6 .. v11}, Lcom/google/android/projection/sink/audio/AudioPlayer;-><init>(IIIILcom/google/android/projection/sink/audio/AudioLatencyChecker;)V

    iput-object v3, v0, Lcom/adayo/androidauto/service/AAService;->mAudioSinkMediaStreamPlayer:Lcom/google/android/projection/sink/audio/AudioPlayer;

    .line 246
    new-instance v3, Lcom/google/android/projection/sink/audio/AudioPlayer;

    const/16 v13, 0xb

    const v14, 0xbb80

    const/4 v15, 0x4

    const/16 v16, 0x2

    const/16 v17, 0x0

    move-object v12, v3

    invoke-direct/range {v12 .. v17}, Lcom/google/android/projection/sink/audio/AudioPlayer;-><init>(IIIILcom/google/android/projection/sink/audio/AudioLatencyChecker;)V

    iput-object v3, v0, Lcom/adayo/androidauto/service/AAService;->mAudioSinkTtsStreamPlayer:Lcom/google/android/projection/sink/audio/AudioPlayer;

    .line 253
    new-instance v3, Lcom/google/android/projection/sink/audio/AudioPlayer;

    move-object v6, v3

    invoke-direct/range {v6 .. v11}, Lcom/google/android/projection/sink/audio/AudioPlayer;-><init>(IIIILcom/google/android/projection/sink/audio/AudioLatencyChecker;)V

    iput-object v3, v0, Lcom/adayo/androidauto/service/AAService;->mAudioSinkMediaStreamPlayer_AAC:Lcom/google/android/projection/sink/audio/AudioPlayer;

    .line 261
    new-instance v3, Lcom/google/android/projection/sink/audio/AudioPlayer;

    move-object v12, v3

    invoke-direct/range {v12 .. v17}, Lcom/google/android/projection/sink/audio/AudioPlayer;-><init>(IIIILcom/google/android/projection/sink/audio/AudioLatencyChecker;)V

    iput-object v3, v0, Lcom/adayo/androidauto/service/AAService;->mAudioSinkTtsStreamPlayer_AAC:Lcom/google/android/projection/sink/audio/AudioPlayer;

    .line 269
    new-instance v3, Lcom/adayo/androidauto/service/AAService$1;

    invoke-direct {v3, v0}, Lcom/adayo/androidauto/service/AAService$1;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    iput-object v3, v0, Lcom/adayo/androidauto/service/AAService;->mInstructHandler:Landroid/os/Handler;

    .line 298
    new-instance v3, Lcom/adayo/androidauto/service/AAService$2;

    invoke-direct {v3, v0}, Lcom/adayo/androidauto/service/AAService$2;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    iput-object v3, v0, Lcom/adayo/androidauto/service/AAService;->mProjectionRendererListener:Lcom/adayo/androidauto/video/AAProjectionRenderer$Listener;

    .line 319
    new-instance v3, Lcom/adayo/androidauto/service/AAService$3;

    invoke-direct {v3, v0}, Lcom/adayo/androidauto/service/AAService$3;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    iput-object v3, v0, Lcom/adayo/androidauto/service/AAService;->mSurfaceTouchListener:Landroid/view/View$OnTouchListener;

    .line 418
    iput-boolean v2, v0, Lcom/adayo/androidauto/service/AAService;->isStartUSBAA:Z

    .line 507
    iput v2, v0, Lcom/adayo/androidauto/service/AAService;->BYEBYEREASON:I

    .line 508
    new-instance v3, Lcom/adayo/androidauto/service/AAService$4;

    invoke-direct {v3, v0}, Lcom/adayo/androidauto/service/AAService$4;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    iput-object v3, v0, Lcom/adayo/androidauto/service/AAService;->mByeByeHandler:Lcom/google/android/projection/protocol/GalReceiver$ByeByeHandler;

    .line 573
    iput-boolean v2, v0, Lcom/adayo/androidauto/service/AAService;->mIsAAPConnected:Z

    .line 576
    iput-boolean v2, v0, Lcom/adayo/androidauto/service/AAService;->mIsAANaviRunningActive:Z

    .line 577
    sget-object v3, Lcom/adayo/androidauto/service/AAService$ConnectMode;->NotConnect:Lcom/adayo/androidauto/service/AAService$ConnectMode;

    iput-object v3, v0, Lcom/adayo/androidauto/service/AAService;->mConnectMode:Lcom/adayo/androidauto/service/AAService$ConnectMode;

    .line 578
    new-instance v3, Lcom/adayo/androidauto/service/AAService$5;

    invoke-direct {v3, v0}, Lcom/adayo/androidauto/service/AAService$5;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    iput-object v3, v0, Lcom/adayo/androidauto/service/AAService;->mMainHandler:Landroid/os/Handler;

    .line 644
    new-instance v3, Lcom/adayo/androidauto/service/AAService$6;

    invoke-direct {v3, v0}, Lcom/adayo/androidauto/service/AAService$6;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    iput-object v3, v0, Lcom/adayo/androidauto/service/AAService;->mProjectionListener:Lcom/google/android/projection/protocol/VideoSink$ProjectionListener;

    .line 811
    new-instance v3, Lcom/adayo/androidauto/service/AAService$7;

    invoke-direct {v3, v0}, Lcom/adayo/androidauto/service/AAService$7;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    iput-object v3, v0, Lcom/adayo/androidauto/service/AAService;->mProjectedModeIconReceiver:Lcom/google/android/projection/protocol/GalReceiver$ProjectedModeIconReceiver;

    .line 877
    new-instance v3, Lcom/adayo/androidauto/service/AAService$8;

    invoke-direct {v3, v0}, Lcom/adayo/androidauto/service/AAService$8;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    iput-object v3, v0, Lcom/adayo/androidauto/service/AAService;->userSwitchListener:Lcom/google/android/projection/protocol/GalReceiver$UserSwitchListener;

    .line 888
    new-instance v3, Lcom/adayo/androidauto/service/AAService$9;

    invoke-direct {v3, v0}, Lcom/adayo/androidauto/service/AAService$9;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    iput-object v3, v0, Lcom/adayo/androidauto/service/AAService;->mUsbPermissionReceiver:Landroid/content/BroadcastReceiver;

    .line 925
    new-instance v3, Lcom/adayo/androidauto/service/AAService$10;

    invoke-direct {v3, v0}, Lcom/adayo/androidauto/service/AAService$10;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    iput-object v3, v0, Lcom/adayo/androidauto/service/AAService;->mUsbAccessoryOrDeviceAttachReceiver:Landroid/content/BroadcastReceiver;

    .line 933
    new-instance v3, Lcom/adayo/androidauto/service/AAService$11;

    invoke-static {}, Landroid/os/Looper;->getMainLooper()Landroid/os/Looper;

    move-result-object v6

    invoke-direct {v3, v0, v6}, Lcom/adayo/androidauto/service/AAService$11;-><init>(Lcom/adayo/androidauto/service/AAService;Landroid/os/Looper;)V

    iput-object v3, v0, Lcom/adayo/androidauto/service/AAService;->mStatusHandler:Landroid/os/Handler;

    .line 991
    iput-boolean v2, v0, Lcom/adayo/androidauto/service/AAService;->mIsStartNavi:Z

    .line 992
    new-instance v3, Lcom/adayo/androidauto/service/AAService$12;

    invoke-direct {v3, v0}, Lcom/adayo/androidauto/service/AAService$12;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    iput-object v3, v0, Lcom/adayo/androidauto/service/AAService;->mAudioFocusListener:Lcom/google/android/projection/protocol/GalReceiver$AudioFocusListener;

    const/16 v3, 0xb

    .line 1000
    iput v3, v0, Lcom/adayo/androidauto/service/AAService;->mVolumeType:I

    .line 1001
    iput-wide v4, v0, Lcom/adayo/androidauto/service/AAService;->rebootAndroidAutoTime:J

    .line 1002
    new-instance v3, Lcom/adayo/androidauto/service/AAService$13;

    invoke-direct {v3, v0}, Lcom/adayo/androidauto/service/AAService$13;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    iput-object v3, v0, Lcom/adayo/androidauto/service/AAService;->mUsbDisconnectedReceiver:Landroid/content/BroadcastReceiver;

    .line 1042
    iput-object v1, v0, Lcom/adayo/androidauto/service/AAService;->stopReason:Lcom/google/android/projection/protocol/Transport$StopReason;

    new-instance v3, Lcom/adayo/androidauto/service/AAService$14;

    invoke-direct {v3, v0}, Lcom/adayo/androidauto/service/AAService$14;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    iput-object v3, v0, Lcom/adayo/androidauto/service/AAService;->mAppMessageListener:Lcom/google/android/projection/protocol/GalReceiver$AppMessageListener;

    .line 1372
    iput-object v1, v0, Lcom/adayo/androidauto/service/AAService;->androidAutoSwitchReceiver:Lcom/adayo/androidauto/receivers/AndroidAutoSwitchReceiver;

    .line 3180
    iput-boolean v2, v0, Lcom/adayo/androidauto/service/AAService;->usbDisConnect:Z

    .line 3248
    iput-object v1, v0, Lcom/adayo/androidauto/service/AAService;->mIFunction:Lcom/adayo/module/servicecenterproxy/IFunction;

    return-void
.end method

.method static synthetic access$000(Lcom/adayo/androidauto/service/AAService;)Z
    .locals 0

    .line 141
    iget-boolean p0, p0, Lcom/adayo/androidauto/service/AAService;->isStartProjection:Z

    return p0
.end method

.method static synthetic access$002(Lcom/adayo/androidauto/service/AAService;Z)Z
    .locals 0

    .line 141
    iput-boolean p1, p0, Lcom/adayo/androidauto/service/AAService;->isStartProjection:Z

    return p1
.end method

.method static synthetic access$100(Lcom/adayo/androidauto/service/AAService;)Landroid/view/View;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->minstructView:Landroid/view/View;

    return-object p0
.end method

.method static synthetic access$1000(Lcom/adayo/androidauto/service/AAService;ZLjava/lang/String;)V
    .locals 0

    .line 141
    invoke-direct {p0, p1, p2}, Lcom/adayo/androidauto/service/AAService;->startUSBAA(ZLjava/lang/String;)V

    return-void
.end method

.method static synthetic access$1100(Lcom/adayo/androidauto/service/AAService;ZLjava/lang/String;)V
    .locals 0

    .line 141
    invoke-direct {p0, p1, p2}, Lcom/adayo/androidauto/service/AAService;->startWirelessAA(ZLjava/lang/String;)V

    return-void
.end method

.method static synthetic access$1200(Lcom/adayo/androidauto/service/AAService;)I
    .locals 0

    .line 141
    iget p0, p0, Lcom/adayo/androidauto/service/AAService;->BYEBYEREASON:I

    return p0
.end method

.method static synthetic access$1202(Lcom/adayo/androidauto/service/AAService;I)I
    .locals 0

    .line 141
    iput p1, p0, Lcom/adayo/androidauto/service/AAService;->BYEBYEREASON:I

    return p1
.end method

.method static synthetic access$1300(Lcom/adayo/androidauto/service/AAService;)Lcom/adayo/androidauto/AppInfor/AppInforManager;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->mAppInforManager:Lcom/adayo/androidauto/AppInfor/AppInforManager;

    return-object p0
.end method

.method static synthetic access$1400(Lcom/adayo/androidauto/service/AAService;)Ljava/lang/String;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->mReconnectedMac:Ljava/lang/String;

    return-object p0
.end method

.method static synthetic access$1402(Lcom/adayo/androidauto/service/AAService;Ljava/lang/String;)Ljava/lang/String;
    .locals 0

    .line 141
    iput-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mReconnectedMac:Ljava/lang/String;

    return-object p1
.end method

.method static synthetic access$1500(Lcom/adayo/androidauto/service/AAService;)Z
    .locals 0

    .line 141
    iget-boolean p0, p0, Lcom/adayo/androidauto/service/AAService;->isStartUSBAA:Z

    return p0
.end method

.method static synthetic access$1502(Lcom/adayo/androidauto/service/AAService;Z)Z
    .locals 0

    .line 141
    iput-boolean p1, p0, Lcom/adayo/androidauto/service/AAService;->isStartUSBAA:Z

    return p1
.end method

.method static synthetic access$1600(Lcom/adayo/androidauto/service/AAService;)Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->mAndroidAutoWirelessService:Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;

    return-object p0
.end method

.method static synthetic access$1700(Lcom/adayo/androidauto/service/AAService;)Z
    .locals 0

    .line 141
    iget-boolean p0, p0, Lcom/adayo/androidauto/service/AAService;->mIsAAPConnected:Z

    return p0
.end method

.method static synthetic access$1702(Lcom/adayo/androidauto/service/AAService;Z)Z
    .locals 0

    .line 141
    iput-boolean p1, p0, Lcom/adayo/androidauto/service/AAService;->mIsAAPConnected:Z

    return p1
.end method

.method static synthetic access$1800(Lcom/adayo/androidauto/service/AAService;)Lcom/adayo/androidauto/service/AAService$ConnectMode;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->mConnectMode:Lcom/adayo/androidauto/service/AAService$ConnectMode;

    return-object p0
.end method

.method static synthetic access$1802(Lcom/adayo/androidauto/service/AAService;Lcom/adayo/androidauto/service/AAService$ConnectMode;)Lcom/adayo/androidauto/service/AAService$ConnectMode;
    .locals 0

    .line 141
    iput-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mConnectMode:Lcom/adayo/androidauto/service/AAService$ConnectMode;

    return-object p1
.end method

.method static synthetic access$1900(Lcom/adayo/androidauto/service/AAService;)Landroid/content/Context;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    return-object p0
.end method

.method static synthetic access$200(Lcom/adayo/androidauto/service/AAService;)Landroid/view/SurfaceView;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->mSurfaceView:Landroid/view/SurfaceView;

    return-object p0
.end method

.method static synthetic access$2000(Lcom/adayo/androidauto/service/AAService;)Lcom/google/android/projection/protocol/Transport;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->mTransport:Lcom/google/android/projection/protocol/Transport;

    return-object p0
.end method

.method static synthetic access$2002(Lcom/adayo/androidauto/service/AAService;Lcom/google/android/projection/protocol/Transport;)Lcom/google/android/projection/protocol/Transport;
    .locals 0

    .line 141
    iput-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mTransport:Lcom/google/android/projection/protocol/Transport;

    return-object p1
.end method

.method static synthetic access$2102(Lcom/adayo/androidauto/service/AAService;Z)Z
    .locals 0

    .line 141
    iput-boolean p1, p0, Lcom/adayo/androidauto/service/AAService;->usbDisConnect:Z

    return p1
.end method

.method static synthetic access$2200(Lcom/adayo/androidauto/service/AAService;)Lcom/adayo/androidauto/service/AAServiceManager;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    return-object p0
.end method

.method static synthetic access$2300(Lcom/adayo/androidauto/service/AAService;)V
    .locals 0

    .line 141
    invoke-direct {p0}, Lcom/adayo/androidauto/service/AAService;->doAAPConnected()V

    return-void
.end method

.method static synthetic access$2400(Lcom/adayo/androidauto/service/AAService;)V
    .locals 0

    .line 141
    invoke-direct {p0}, Lcom/adayo/androidauto/service/AAService;->toApp()V

    return-void
.end method

.method static synthetic access$2500(Lcom/adayo/androidauto/service/AAService;)V
    .locals 0

    .line 141
    invoke-direct {p0}, Lcom/adayo/androidauto/service/AAService;->startAAAfterTerms()V

    return-void
.end method

.method static synthetic access$2600(Lcom/adayo/androidauto/service/AAService;)V
    .locals 0

    .line 141
    invoke-direct {p0}, Lcom/adayo/androidauto/service/AAService;->dealMuteStateWarnningInner()V

    return-void
.end method

.method static synthetic access$2700(Lcom/adayo/androidauto/service/AAService;)J
    .locals 2

    .line 141
    iget-wide v0, p0, Lcom/adayo/androidauto/service/AAService;->onPairingRequestTime:J

    return-wide v0
.end method

.method static synthetic access$2702(Lcom/adayo/androidauto/service/AAService;J)J
    .locals 0

    .line 141
    iput-wide p1, p0, Lcom/adayo/androidauto/service/AAService;->onPairingRequestTime:J

    return-wide p1
.end method

.method static synthetic access$2800(Lcom/adayo/androidauto/service/AAService;Ljava/lang/Runnable;)V
    .locals 0

    .line 141
    invoke-direct {p0, p1}, Lcom/adayo/androidauto/service/AAService;->runOnUiThread(Ljava/lang/Runnable;)V

    return-void
.end method

.method static synthetic access$2900(Lcom/adayo/androidauto/service/AAService;)Z
    .locals 0

    .line 141
    iget-boolean p0, p0, Lcom/adayo/androidauto/service/AAService;->isSendGetKeyFrameData:Z

    return p0
.end method

.method static synthetic access$2902(Lcom/adayo/androidauto/service/AAService;Z)Z
    .locals 0

    .line 141
    iput-boolean p1, p0, Lcom/adayo/androidauto/service/AAService;->isSendGetKeyFrameData:Z

    return p1
.end method

.method static synthetic access$300(Lcom/adayo/androidauto/service/AAService;)Landroid/view/View;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->mPromptView:Landroid/view/View;

    return-object p0
.end method

.method static synthetic access$3000(Lcom/adayo/androidauto/service/AAService;)Lcom/adayo/androidauto/sensors/VehicleInfoSensor;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->mVehicleInfoSensor:Lcom/adayo/androidauto/sensors/VehicleInfoSensor;

    return-object p0
.end method

.method static synthetic access$3100(Lcom/adayo/androidauto/service/AAService;)Lcom/adayo/androidauto/bt/AdayoBtManager;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->mAdayoBtManager:Lcom/adayo/androidauto/bt/AdayoBtManager;

    return-object p0
.end method

.method static synthetic access$3200(Lcom/adayo/androidauto/service/AAService;)Landroid/os/Handler;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->mDelayHandler:Landroid/os/Handler;

    return-object p0
.end method

.method static synthetic access$3300(Lcom/adayo/androidauto/service/AAService;)Lcom/adayo/androidauto/CarAdapter;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->carAdapter:Lcom/adayo/androidauto/CarAdapter;

    return-object p0
.end method

.method static synthetic access$3400(Lcom/adayo/androidauto/service/AAService;Landroid/content/Intent;)V
    .locals 0

    .line 141
    invoke-direct {p0, p1}, Lcom/adayo/androidauto/service/AAService;->onUsbEventReceive(Landroid/content/Intent;)V

    return-void
.end method

.method static synthetic access$3500(Lcom/adayo/androidauto/service/AAService;)Lcom/google/android/projection/sink/audio/AudioFocusManager;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->mAudioFocusManager:Lcom/google/android/projection/sink/audio/AudioFocusManager;

    return-object p0
.end method

.method static synthetic access$3600(Lcom/adayo/androidauto/service/AAService;)J
    .locals 2

    .line 141
    iget-wide v0, p0, Lcom/adayo/androidauto/service/AAService;->rebootAndroidAutoTime:J

    return-wide v0
.end method

.method static synthetic access$3602(Lcom/adayo/androidauto/service/AAService;J)J
    .locals 0

    .line 141
    iput-wide p1, p0, Lcom/adayo/androidauto/service/AAService;->rebootAndroidAutoTime:J

    return-wide p1
.end method

.method static synthetic access$3700(Lcom/adayo/androidauto/service/AAService;)V
    .locals 0

    .line 141
    invoke-direct {p0}, Lcom/adayo/androidauto/service/AAService;->usbInterfaceControl()V

    return-void
.end method

.method static synthetic access$3800(Lcom/adayo/androidauto/service/AAService;)Lcom/google/android/projection/sink/navigation/NavigationFocusManager;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->mNavigationFocusManager:Lcom/google/android/projection/sink/navigation/NavigationFocusManager;

    return-object p0
.end method

.method static synthetic access$3900(Lcom/adayo/androidauto/service/AAService;)Z
    .locals 0

    .line 141
    iget-boolean p0, p0, Lcom/adayo/androidauto/service/AAService;->mVoiceState:Z

    return p0
.end method

.method static synthetic access$3902(Lcom/adayo/androidauto/service/AAService;Z)Z
    .locals 0

    .line 141
    iput-boolean p1, p0, Lcom/adayo/androidauto/service/AAService;->mVoiceState:Z

    return p1
.end method

.method static synthetic access$400(Lcom/adayo/androidauto/service/AAService;)Ljava/lang/Object;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->mLock:Ljava/lang/Object;

    return-object p0
.end method

.method static synthetic access$4000(Lcom/adayo/androidauto/service/AAService;)Lcom/adayo/androidauto/service/MessengerServer;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->mMessengerServer:Lcom/adayo/androidauto/service/MessengerServer;

    return-object p0
.end method

.method static synthetic access$4100(Lcom/adayo/androidauto/service/AAService;)Lcom/google/android/projection/transport/TransportFactory;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->mTransportFactory:Lcom/google/android/projection/transport/TransportFactory;

    return-object p0
.end method

.method static synthetic access$4200(Lcom/adayo/androidauto/service/AAService;)V
    .locals 0

    .line 141
    invoke-direct {p0}, Lcom/adayo/androidauto/service/AAService;->initializeSensorServices()V

    return-void
.end method

.method static synthetic access$4300(Lcom/adayo/androidauto/service/AAService;)V
    .locals 0

    .line 141
    invoke-direct {p0}, Lcom/adayo/androidauto/service/AAService;->initAAGal()V

    return-void
.end method

.method static synthetic access$4402(Lcom/adayo/androidauto/service/AAService;Ljava/lang/String;)Ljava/lang/String;
    .locals 0

    .line 141
    iput-object p1, p0, Lcom/adayo/androidauto/service/AAService;->connectedUsb:Ljava/lang/String;

    return-object p1
.end method

.method static synthetic access$4500(Lcom/adayo/androidauto/service/AAService;)Lcom/adayo/androidauto/depend/IConfigureDependManager;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->mIConfigureDependManager:Lcom/adayo/androidauto/depend/IConfigureDependManager;

    return-object p0
.end method

.method static synthetic access$4602(Lcom/adayo/androidauto/service/AAService;Ljava/lang/String;)Ljava/lang/String;
    .locals 0

    .line 141
    iput-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mCurUSBMDSN:Ljava/lang/String;

    return-object p1
.end method

.method static synthetic access$4700(Lcom/adayo/androidauto/service/AAService;)Lcom/google/android/projection/sink/audio/MicrophoneRecorder;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->mAudioSourceListener:Lcom/google/android/projection/sink/audio/MicrophoneRecorder;

    return-object p0
.end method

.method static synthetic access$4800(Lcom/adayo/androidauto/service/AAService;)Lcom/google/android/projection/sink/audio/AudioPlayer;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->mAudioSinkMediaStreamPlayer:Lcom/google/android/projection/sink/audio/AudioPlayer;

    return-object p0
.end method

.method static synthetic access$4900(Lcom/adayo/androidauto/service/AAService;)Lcom/google/android/projection/sink/audio/AudioPlayer;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->mAudioSinkMediaStreamPlayer_AAC:Lcom/google/android/projection/sink/audio/AudioPlayer;

    return-object p0
.end method

.method static synthetic access$500(Lcom/adayo/androidauto/service/AAService;)Lcom/adayo/androidauto/AAGalIntegration;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    return-object p0
.end method

.method static synthetic access$5000(Lcom/adayo/androidauto/service/AAService;)Lcom/google/android/projection/sink/audio/AudioPlayer;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->mAudioSinkTtsStreamPlayer:Lcom/google/android/projection/sink/audio/AudioPlayer;

    return-object p0
.end method

.method static synthetic access$502(Lcom/adayo/androidauto/service/AAService;Lcom/adayo/androidauto/AAGalIntegration;)Lcom/adayo/androidauto/AAGalIntegration;
    .locals 0

    .line 141
    iput-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    return-object p1
.end method

.method static synthetic access$5100(Lcom/adayo/androidauto/service/AAService;)Lcom/google/android/projection/sink/audio/AudioPlayer;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->mAudioSinkTtsStreamPlayer_AAC:Lcom/google/android/projection/sink/audio/AudioPlayer;

    return-object p0
.end method

.method static synthetic access$5200(Lcom/adayo/androidauto/service/AAService;)Lcom/google/android/projection/sink/sensors/SensorManager;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->mSensorDispatcher:Lcom/google/android/projection/sink/sensors/SensorManager;

    return-object p0
.end method

.method static synthetic access$5202(Lcom/adayo/androidauto/service/AAService;Lcom/google/android/projection/sink/sensors/SensorManager;)Lcom/google/android/projection/sink/sensors/SensorManager;
    .locals 0

    .line 141
    iput-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mSensorDispatcher:Lcom/google/android/projection/sink/sensors/SensorManager;

    return-object p1
.end method

.method static synthetic access$5300(Lcom/adayo/androidauto/service/AAService;Ljava/lang/String;)V
    .locals 0

    .line 141
    invoke-direct {p0, p1}, Lcom/adayo/androidauto/service/AAService;->connectAA(Ljava/lang/String;)V

    return-void
.end method

.method static synthetic access$5400(Lcom/adayo/androidauto/service/AAService;)V
    .locals 0

    .line 141
    invoke-direct {p0}, Lcom/adayo/androidauto/service/AAService;->removeSendAutoConnectBTMsg()V

    return-void
.end method

.method static synthetic access$5500(Lcom/adayo/androidauto/service/AAService;)Lcom/adayo/androidauto/dialog/WiredPlugDialog;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->wiredPlugDialog:Lcom/adayo/androidauto/dialog/WiredPlugDialog;

    return-object p0
.end method

.method static synthetic access$5600(Lcom/adayo/androidauto/service/AAService;)V
    .locals 0

    .line 141
    invoke-direct {p0}, Lcom/adayo/androidauto/service/AAService;->addMoudle()V

    return-void
.end method

.method static synthetic access$5700(Lcom/adayo/androidauto/service/AAService;)V
    .locals 0

    .line 141
    invoke-direct {p0}, Lcom/adayo/androidauto/service/AAService;->sendPlayKeyEvent()V

    return-void
.end method

.method static synthetic access$600(Lcom/adayo/androidauto/service/AAService;)Lcom/adayo/androidauto/video/AAProjectionRenderer;
    .locals 0

    .line 141
    iget-object p0, p0, Lcom/adayo/androidauto/service/AAService;->mProjectionRenderer:Lcom/adayo/androidauto/video/AAProjectionRenderer;

    return-object p0
.end method

.method static synthetic access$602(Lcom/adayo/androidauto/service/AAService;Lcom/adayo/androidauto/video/AAProjectionRenderer;)Lcom/adayo/androidauto/video/AAProjectionRenderer;
    .locals 0

    .line 141
    iput-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mProjectionRenderer:Lcom/adayo/androidauto/video/AAProjectionRenderer;

    return-object p1
.end method

.method private addMoudle()V
    .locals 3

    .line 3270
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    .line 3271
    new-instance v1, Lcom/adayo/androidauto/service/AAService$26;

    invoke-direct {v1, p0}, Lcom/adayo/androidauto/service/AAService$26;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    iput-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mIFunction:Lcom/adayo/module/servicecenterproxy/IFunction;

    .line 3289
    invoke-interface {v0, v1}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    .line 3291
    new-instance v1, Lcom/adayo/androidauto/service/AAService$27;

    invoke-direct {v1, p0}, Lcom/adayo/androidauto/service/AAService$27;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    iput-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mIFunction:Lcom/adayo/module/servicecenterproxy/IFunction;

    .line 3309
    invoke-interface {v0, v1}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    .line 3311
    new-instance v1, Lcom/adayo/androidauto/service/AAService$28;

    invoke-direct {v1, p0}, Lcom/adayo/androidauto/service/AAService$28;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    iput-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mIFunction:Lcom/adayo/module/servicecenterproxy/IFunction;

    .line 3329
    invoke-interface {v0, v1}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    .line 3331
    new-instance v1, Lcom/adayo/androidauto/service/AAService$29;

    invoke-direct {v1, p0}, Lcom/adayo/androidauto/service/AAService$29;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    iput-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mIFunction:Lcom/adayo/module/servicecenterproxy/IFunction;

    .line 3349
    invoke-interface {v0, v1}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    .line 3352
    new-instance v1, Lcom/adayo/androidauto/service/AAService$30;

    invoke-direct {v1, p0}, Lcom/adayo/androidauto/service/AAService$30;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    iput-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mIFunction:Lcom/adayo/module/servicecenterproxy/IFunction;

    .line 3378
    invoke-interface {v0, v1}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    .line 3381
    new-instance v1, Lcom/adayo/androidauto/service/AAService$31;

    invoke-direct {v1, p0}, Lcom/adayo/androidauto/service/AAService$31;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    iput-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mIFunction:Lcom/adayo/module/servicecenterproxy/IFunction;

    .line 3407
    invoke-interface {v0, v1}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    .line 3410
    new-instance v1, Lcom/adayo/androidauto/service/AAService$32;

    invoke-direct {v1, p0}, Lcom/adayo/androidauto/service/AAService$32;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    iput-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mIFunction:Lcom/adayo/module/servicecenterproxy/IFunction;

    .line 3436
    invoke-interface {v0, v1}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    .line 3439
    new-instance v1, Lcom/adayo/androidauto/service/AAService$33;

    invoke-direct {v1, p0}, Lcom/adayo/androidauto/service/AAService$33;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    iput-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mIFunction:Lcom/adayo/module/servicecenterproxy/IFunction;

    .line 3465
    invoke-interface {v0, v1}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    .line 3468
    new-instance v1, Lcom/adayo/androidauto/service/AAService$34;

    invoke-direct {v1, p0}, Lcom/adayo/androidauto/service/AAService$34;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    iput-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mIFunction:Lcom/adayo/module/servicecenterproxy/IFunction;

    .line 3494
    invoke-interface {v0, v1}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    .line 3497
    new-instance v1, Lcom/adayo/androidauto/service/AAService$35;

    invoke-direct {v1, p0}, Lcom/adayo/androidauto/service/AAService$35;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    iput-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mIFunction:Lcom/adayo/module/servicecenterproxy/IFunction;

    .line 3523
    invoke-interface {v0, v1}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    .line 3526
    invoke-static {}, Lcom/adayo/androidauto/config/LoadJson;->getInstance()Lcom/adayo/androidauto/config/LoadJson;

    move-result-object v1

    invoke-virtual {v1}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v1

    invoke-virtual {v1}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v1

    invoke-virtual {v1}, Lcom/adayo/androidauto/config/bean/Information;->getHeadunitModel()Ljava/lang/String;

    move-result-object v1

    const-string v2, "HMA212"

    invoke-virtual {v1, v2}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v1

    if-nez v1, :cond_0

    const-string v1, "AHU.AAS.20240704"

    const-string v2, "registerIFunction() done"

    .line 3527
    invoke-static {v1, v2}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 3529
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mServiceCenterManager:Lcom/adayo/module/servicecenterproxy/ServiceCenterManager;

    const-string v2, "ADAYO_SOURCE_ANDROID_AUTO"

    invoke-virtual {v1, v2, v0}, Lcom/adayo/module/servicecenterproxy/ServiceCenterManager;->registerFunctionEntry(Ljava/lang/String;Ljava/util/List;)Z

    :cond_0
    return-void
.end method

.method private checkUsbDeviceAttach()V
    .locals 5

    const-string v0, "AHU.AAS.20240704"

    const-string v1, "checkUsbDeviceAttach"

    .line 1800
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 1801
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mTransport:Lcom/google/android/projection/protocol/Transport;

    if-nez v0, :cond_0

    iget-boolean v0, p0, Lcom/adayo/androidauto/service/AAService;->mIsAAPConnected:Z

    if-nez v0, :cond_0

    .line 1802
    sget-object v0, Lcom/adayo/androidauto/service/AAService;->usbManager:Landroid/hardware/usb/UsbManager;

    invoke-virtual {v0}, Landroid/hardware/usb/UsbManager;->getDeviceList()Ljava/util/HashMap;

    move-result-object v0

    .line 1803
    invoke-virtual {v0}, Ljava/util/HashMap;->keySet()Ljava/util/Set;

    move-result-object v1

    .line 1804
    invoke-interface {v1}, Ljava/util/Set;->iterator()Ljava/util/Iterator;

    move-result-object v1

    :goto_0
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z

    move-result v2

    if-eqz v2, :cond_0

    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Ljava/lang/String;

    .line 1805
    invoke-virtual {v0, v2}, Ljava/util/HashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Landroid/hardware/usb/UsbDevice;

    .line 1806
    invoke-virtual {v2}, Landroid/hardware/usb/UsbDevice;->getDeviceName()Ljava/lang/String;

    .line 1809
    iget-object v3, p0, Lcom/adayo/androidauto/service/AAService;->mAppInforManager:Lcom/adayo/androidauto/AppInfor/AppInforManager;

    invoke-virtual {v3, v2}, Lcom/adayo/androidauto/AppInfor/AppInforManager;->setUsbDevice(Landroid/hardware/usb/UsbDevice;)V

    .line 1810
    iget-object v3, p0, Lcom/adayo/androidauto/service/AAService;->mAppInforManager:Lcom/adayo/androidauto/AppInfor/AppInforManager;

    const/4 v4, 0x1

    invoke-virtual {v3, v4}, Lcom/adayo/androidauto/AppInfor/AppInforManager;->setUSBConnect(Z)V

    .line 1812
    invoke-direct {p0, v2}, Lcom/adayo/androidauto/service/AAService;->startHostMode(Landroid/hardware/usb/UsbDevice;)Z

    goto :goto_0

    :cond_0
    return-void
.end method

.method private checkUsbHostPermission(Landroid/hardware/usb/UsbDevice;)Z
    .locals 5

    .line 1905
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    const-string v1, "usb"

    .line 1906
    invoke-virtual {v0, v1}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/hardware/usb/UsbManager;

    .line 1907
    invoke-virtual {v0, p1}, Landroid/hardware/usb/UsbManager;->hasPermission(Landroid/hardware/usb/UsbDevice;)Z

    move-result v1

    const-string v2, "AHU.AAS.20240704"

    if-nez v1, :cond_0

    .line 1908
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "no permission for the device(will request permiss broadcast). asking user "

    invoke-virtual {v1, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v3, p0, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    .line 1910
    invoke-virtual {v3}, Landroid/content/Context;->getPackageName()Ljava/lang/String;

    move-result-object v3

    invoke-virtual {v1, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    .line 1908
    invoke-static {v2, v1}, Lcom/adayo/androidauto/utils/AALog;->i(Ljava/lang/String;Ljava/lang/String;)V

    .line 1911
    new-instance v1, Landroid/content/Intent;

    const-string v2, "com.google.android.projection.sink.transport.usb.ACTION_USB_DEVICE_PERMISSION"

    invoke-direct {v1, v2}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    .line 1912
    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    const/high16 v3, 0x40000000    # 2.0f

    const/4 v4, 0x0

    invoke-static {v2, v4, v1, v3}, Landroid/app/PendingIntent;->getBroadcast(Landroid/content/Context;ILandroid/content/Intent;I)Landroid/app/PendingIntent;

    move-result-object v1

    .line 1914
    invoke-direct {p0}, Lcom/adayo/androidauto/service/AAService;->registerUsbPerrmissions()V

    .line 1915
    invoke-virtual {v0, p1, v1}, Landroid/hardware/usb/UsbManager;->requestPermission(Landroid/hardware/usb/UsbDevice;Landroid/app/PendingIntent;)V

    return v4

    :cond_0
    const-string p1, "checkUsbHostPermission,is ok"

    .line 1918
    invoke-static {v2, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    const/4 p1, 0x1

    return p1
.end method

.method private connectAA(Ljava/lang/String;)V
    .locals 2

    .line 501
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "connectAA "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    const-string v1, "AHU.AAS.20240704"

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 502
    invoke-virtual {p0}, Lcom/adayo/androidauto/service/AAService;->startTcpServerMode()V

    .line 503
    sget-object v0, Lcom/adayo/androidauto/service/AAService$ConnectMode;->Wireless:Lcom/adayo/androidauto/service/AAService$ConnectMode;

    invoke-virtual {p0, v0}, Lcom/adayo/androidauto/service/AAService;->setConnectMode(Lcom/adayo/androidauto/service/AAService$ConnectMode;)V

    .line 505
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAndroidAutoWirelessService:Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;

    sget-object v1, Lcom/google/android/projection/proto/WifiDiscoveryProtos$StartRequestReason;->USER_REQUEST:Lcom/google/android/projection/proto/WifiDiscoveryProtos$StartRequestReason;

    invoke-virtual {v0, p1, v1}, Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;->startWirelessAA(Ljava/lang/String;Lcom/google/android/projection/proto/WifiDiscoveryProtos$StartRequestReason;)V

    return-void
.end method

.method private dealMuteStateWarnningInner()V
    .locals 0

    return-void
.end method

.method private doAAPConnected()V
    .locals 9

    const-string v0, "AHU.AAS.20240704"

    const-string v1, "doAAPConnected,start"

    .line 2336
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2337
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    invoke-virtual {v1}, Lcom/adayo/androidauto/service/AAServiceManager;->getAAActivity()Lcom/adayo/androidauto/ui/AAActivity;

    move-result-object v1

    if-nez v1, :cond_0

    const-string v1, "doAAPConnected,mAAServiceManager.getAAActivity()==null"

    .line 2338
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V

    return-void

    .line 2341
    :cond_0
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    invoke-virtual {v1}, Lcom/adayo/androidauto/service/AAServiceManager;->getAAActivity()Lcom/adayo/androidauto/ui/AAActivity;

    move-result-object v1

    const v2, 0x7f05003e

    invoke-virtual {v1, v2}, Lcom/adayo/androidauto/ui/AAActivity;->findViewById(I)Landroid/view/View;

    move-result-object v1

    check-cast v1, Landroid/view/SurfaceView;

    iput-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mSurfaceView:Landroid/view/SurfaceView;

    .line 2342
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    invoke-virtual {v1}, Lcom/adayo/androidauto/service/AAServiceManager;->getAAActivity()Lcom/adayo/androidauto/ui/AAActivity;

    move-result-object v1

    const v2, 0x7f050012

    invoke-virtual {v1, v2}, Lcom/adayo/androidauto/ui/AAActivity;->findViewById(I)Landroid/view/View;

    move-result-object v1

    iput-object v1, p0, Lcom/adayo/androidauto/service/AAService;->minstructView:Landroid/view/View;

    .line 2344
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    invoke-virtual {v1}, Lcom/adayo/androidauto/service/AAServiceManager;->getAAActivity()Lcom/adayo/androidauto/ui/AAActivity;

    move-result-object v1

    const v2, 0x7f050032

    invoke-virtual {v1, v2}, Lcom/adayo/androidauto/ui/AAActivity;->findViewById(I)Landroid/view/View;

    move-result-object v1

    iput-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mPromptView:Landroid/view/View;

    .line 2346
    :cond_1
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    invoke-virtual {v1}, Lcom/adayo/androidauto/service/AAServiceManager;->getAAActivity()Lcom/adayo/androidauto/ui/AAActivity;

    move-result-object v1

    const v2, 0x7f050010

    invoke-virtual {v1, v2}, Lcom/adayo/androidauto/ui/AAActivity;->findViewById(I)Landroid/view/View;

    move-result-object v1

    check-cast v1, Landroid/widget/TextView;

    iput-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mFpsTV:Landroid/widget/TextView;

    .line 2348
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mSurfaceView:Landroid/view/SurfaceView;

    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mSurfaceTouchListener:Landroid/view/View$OnTouchListener;

    invoke-virtual {v1, v2}, Landroid/view/SurfaceView;->setOnTouchListener(Landroid/view/View$OnTouchListener;)V

    .line 2350
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mSurfaceView:Landroid/view/SurfaceView;

    const/4 v2, 0x0

    invoke-virtual {v1, v2}, Landroid/view/SurfaceView;->setVisibility(I)V

    .line 2351
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "doAAPConnected,set mSurfaceView VISIBLE ="

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mSurfaceView:Landroid/view/SurfaceView;

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2353
    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mProjectionRenderer:Lcom/adayo/androidauto/video/AAProjectionRenderer;

    if-eqz v2, :cond_2

    .line 2354
    iget-object v3, p0, Lcom/adayo/androidauto/service/AAService;->mSurfaceView:Landroid/view/SurfaceView;

    iget-object v4, p0, Lcom/adayo/androidauto/service/AAService;->mFpsTV:Landroid/widget/TextView;

    iget-object v5, p0, Lcom/adayo/androidauto/service/AAService;->mProjectionRendererListener:Lcom/adayo/androidauto/video/AAProjectionRenderer$Listener;

    sget-object v6, Lcom/adayo/androidauto/service/AAService;->mEmulatedScreen:Lcom/google/android/projection/sink/video/DimensionUtils$ScreenSettings;

    iget-object v7, p0, Lcom/adayo/androidauto/service/AAService;->mVideoLatencyChecker:Lcom/google/android/projection/sink/video/VideoLatencyChecker;

    iget-object v8, p0, Lcom/adayo/androidauto/service/AAService;->mAudioLatencyChecker:Lcom/google/android/projection/sink/audio/AudioLatencyChecker;

    invoke-virtual/range {v2 .. v8}, Lcom/adayo/androidauto/video/AAProjectionRenderer;->init(Landroid/view/SurfaceView;Landroid/widget/TextView;Lcom/adayo/androidauto/video/AAProjectionRenderer$Listener;Lcom/google/android/projection/sink/video/DimensionUtils$ScreenSettings;Lcom/google/android/projection/sink/video/VideoLatencyChecker;Lcom/google/android/projection/sink/audio/AudioLatencyChecker;)V

    goto :goto_0

    :cond_2
    const-string v1, "doAAPConnected,mProjectionRenderer==null"

    .line 2358
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V

    :goto_0
    const-string v1, "doAAPConnected,end"

    .line 2360
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    return-void
.end method

.method private getKeys()[I
    .locals 3

    .line 2964
    invoke-static {}, Lcom/adayo/androidauto/utils/DeviceTypeUtil;->getinstance()Lcom/adayo/androidauto/utils/DeviceTypeUtil;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/utils/DeviceTypeUtil;->getDeviceType()Ljava/lang/String;

    move-result-object v0

    .line 2965
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "mCarInfo deviceType ="

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v2, "device ="

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/String;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-virtual {v1, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    const-string v1, "AHU.AAS.20240704"

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2966
    sget-object v0, Lcom/adayo/androidauto/constants/KeysConstants;->DEFAULT_KEYS:[I

    return-object v0
.end method

.method private static getSystemModuleName()Ljava/lang/String;
    .locals 4

    .line 1362
    invoke-static {}, Lcom/adayo/proxy/system/systemservice/SystemServiceManager;->getInstance()Lcom/adayo/proxy/system/systemservice/SystemServiceManager;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/proxy/system/systemservice/SystemServiceManager;->conectsystemService()Z

    move-result v0

    const-string v1, "AHU.AAS.20240704"

    if-eqz v0, :cond_0

    .line 1363
    invoke-static {}, Lcom/adayo/proxy/system/systemservice/SystemServiceManager;->getInstance()Lcom/adayo/proxy/system/systemservice/SystemServiceManager;

    move-result-object v0

    const/4 v2, 0x0

    invoke-virtual {v0, v2}, Lcom/adayo/proxy/system/systemservice/SystemServiceManager;->getSystemConfigInfo(B)Ljava/lang/String;

    move-result-object v0

    invoke-virtual {v0}, Ljava/lang/String;->toLowerCase()Ljava/lang/String;

    move-result-object v0

    goto :goto_0

    :cond_0
    const-string v0, "SystemServiceManager no connection --fail"

    .line 1365
    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V

    const-string v0, ""

    .line 1367
    :goto_0
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "getSystemModuleName moduleName = "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-static {v1, v2}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    return-object v0
.end method

.method private initAAGal()V
    .locals 21

    move-object/from16 v1, p0

    const-string v0, "AHU.AAS.20240704"

    const-string v2, "initAAGal"

    .line 1487
    invoke-static {v0, v2}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 1490
    :try_start_0
    new-instance v0, Lcom/adayo/androidauto/utils/Utils;

    invoke-direct {v0}, Lcom/adayo/androidauto/utils/Utils;-><init>()V

    .line 1491
    new-instance v0, Ljava/io/File;

    const-string v2, "/map/.clientkey.txt"

    invoke-direct {v0, v2}, Ljava/io/File;-><init>(Ljava/lang/String;)V

    invoke-static {v0}, Lcom/adayo/androidauto/utils/Utils;->getAndroidAutoSSLKey(Ljava/io/File;)[B

    move-result-object v0

    .line 1492
    new-instance v2, Ljava/io/File;

    const-string v3, "/map/.privatekey.txt"

    invoke-direct {v2, v3}, Ljava/io/File;-><init>(Ljava/lang/String;)V

    invoke-static {v2}, Lcom/adayo/androidauto/utils/Utils;->getAndroidAutoSSLKey(Ljava/io/File;)[B

    move-result-object v2

    .line 1493
    new-instance v3, Ljava/lang/String;

    invoke-direct {v3, v0}, Ljava/lang/String;-><init>([B)V

    .line 1494
    new-instance v0, Ljava/lang/String;

    invoke-direct {v0, v2}, Ljava/lang/String;-><init>([B)V

    const-string v2, ""

    .line 1495
    invoke-virtual {v3, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-nez v2, :cond_0

    const-string v2, ""

    invoke-virtual {v0, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-nez v2, :cond_0

    const-string v2, "AHU.AAS.20240704"

    const-string v4, "clientkey and privatekey is not null!"

    .line 1496
    invoke-static {v2, v4}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 1497
    iput-object v3, v1, Lcom/adayo/androidauto/service/AAService;->clientCertificate:Ljava/lang/String;

    .line 1498
    iput-object v0, v1, Lcom/adayo/androidauto/service/AAService;->privateKey:Ljava/lang/String;

    :cond_0
    const-string v0, "AHU.AAS.20240704"

    .line 1500
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "clientkey= "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v3, v1, Lcom/adayo/androidauto/service/AAService;->clientCertificate:Ljava/lang/String;

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-static {v0, v2}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    const-string v0, "AHU.AAS.20240704"

    .line 1501
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "privatekey= "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v3, v1, Lcom/adayo/androidauto/service/AAService;->privateKey:Ljava/lang/String;

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-static {v0, v2}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception v0

    .line 1503
    invoke-virtual {v0}, Ljava/lang/Exception;->printStackTrace()V

    const-string v2, "AHU.AAS.20240704"

    .line 1504
    invoke-virtual {v0}, Ljava/lang/Exception;->getMessage()Ljava/lang/String;

    move-result-object v0

    invoke-static {v2, v0}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V

    .line 1506
    :goto_0
    iget-object v2, v1, Lcom/adayo/androidauto/service/AAService;->mLock:Ljava/lang/Object;

    monitor-enter v2

    .line 1507
    :try_start_1
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    if-eqz v0, :cond_1

    const-string v0, "AHU.AAS.20240704"

    const-string v3, "mAAGalIntegration !=null,already be inited,so return!"

    .line 1508
    invoke-static {v0, v3}, Lcom/adayo/androidauto/utils/AALog;->w(Ljava/lang/String;Ljava/lang/String;)V

    .line 1509
    monitor-exit v2

    return-void

    .line 1513
    :cond_1
    new-instance v4, Lcom/adayo/androidauto/AAGalIntegration;

    invoke-direct {v4}, Lcom/adayo/androidauto/AAGalIntegration;-><init>()V

    iput-object v4, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    .line 1514
    iget-object v5, v1, Lcom/adayo/androidauto/service/AAService;->mAppMessageListener:Lcom/google/android/projection/protocol/GalReceiver$AppMessageListener;

    iget-object v6, v1, Lcom/adayo/androidauto/service/AAService;->mAudioFocusListener:Lcom/google/android/projection/protocol/GalReceiver$AudioFocusListener;

    iget-object v7, v1, Lcom/adayo/androidauto/service/AAService;->mProjectedModeIconReceiver:Lcom/google/android/projection/protocol/GalReceiver$ProjectedModeIconReceiver;

    iget-object v8, v1, Lcom/adayo/androidauto/service/AAService;->mByeByeHandler:Lcom/google/android/projection/protocol/GalReceiver$ByeByeHandler;

    iget-object v9, v1, Lcom/adayo/androidauto/service/AAService;->mHandler:Landroid/os/Handler;

    sget-object v10, Lcom/adayo/androidauto/service/AAService;->mCarInfo:Lcom/google/android/projection/protocol/GalReceiver$CarInfo;

    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mLocalModeCallbacks:Lcom/adayo/androidauto/constants/LocalModeCallbacks;

    .line 1516
    invoke-interface {v0}, Lcom/adayo/androidauto/constants/LocalModeCallbacks;->getRootCert()Ljava/lang/String;

    move-result-object v11

    iget-object v12, v1, Lcom/adayo/androidauto/service/AAService;->clientCertificate:Ljava/lang/String;

    iget-object v13, v1, Lcom/adayo/androidauto/service/AAService;->privateKey:Ljava/lang/String;

    iget-object v14, v1, Lcom/adayo/androidauto/service/AAService;->inputInjectorListener:Lcom/google/android/projection/protocol/InputSource$InputInjector;

    iget-object v15, v1, Lcom/adayo/androidauto/service/AAService;->mAndroidAutoWirelessService:Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;

    .line 1514
    invoke-virtual/range {v4 .. v15}, Lcom/adayo/androidauto/AAGalIntegration;->init(Lcom/google/android/projection/protocol/GalReceiver$AppMessageListener;Lcom/google/android/projection/protocol/GalReceiver$AudioFocusListener;Lcom/google/android/projection/protocol/GalReceiver$ProjectedModeIconReceiver;Lcom/google/android/projection/protocol/GalReceiver$ByeByeHandler;Landroid/os/Handler;Lcom/google/android/projection/protocol/GalReceiver$CarInfo;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lcom/google/android/projection/protocol/InputSource$InputInjector;Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;)Z

    .line 1520
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mIConfigureDependManager:Lcom/adayo/androidauto/depend/IConfigureDependManager;

    invoke-interface {v0}, Lcom/adayo/androidauto/depend/IConfigureDependManager;->getSurfaceVideoWidth()I

    move-result v3

    .line 1521
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mIConfigureDependManager:Lcom/adayo/androidauto/depend/IConfigureDependManager;

    invoke-interface {v0}, Lcom/adayo/androidauto/depend/IConfigureDependManager;->getSurfaceVideoHeight()I

    move-result v4

    .line 1523
    new-instance v0, Lcom/google/android/projection/sink/audio/MicrophoneRecorder;

    invoke-direct {v0}, Lcom/google/android/projection/sink/audio/MicrophoneRecorder;-><init>()V

    iput-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAudioSourceListener:Lcom/google/android/projection/sink/audio/MicrophoneRecorder;

    .line 1526
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mSettings:Lcom/google/android/projection/sink/settings/Settings;

    invoke-virtual {v0}, Lcom/google/android/projection/sink/settings/Settings;->getEmulatedWidth()I

    move-result v6

    .line 1527
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mSettings:Lcom/google/android/projection/sink/settings/Settings;

    invoke-virtual {v0}, Lcom/google/android/projection/sink/settings/Settings;->getEmulatedHeight()I

    move-result v7

    .line 1528
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mSettings:Lcom/google/android/projection/sink/settings/Settings;

    invoke-virtual {v0}, Lcom/google/android/projection/sink/settings/Settings;->getEmulatedDpi()F

    move-result v8

    const-string v0, "AHU.AAS.20240704"

    .line 1529
    new-instance v5, Ljava/lang/StringBuilder;

    invoke-direct {v5}, Ljava/lang/StringBuilder;-><init>()V

    const-string v9, "initAAGal emulatedWidth(from mSettings default): "

    invoke-virtual {v5, v9}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v9, ",emulatedHeight="

    invoke-virtual {v5, v9}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v5, v7}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v9, ",emulatedDpi="

    invoke-virtual {v5, v9}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v5, v8}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    invoke-virtual {v5}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v5

    invoke-static {v0, v5}, Lcom/adayo/androidauto/utils/AALog;->w(Ljava/lang/String;Ljava/lang/String;)V

    .line 1530
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    invoke-virtual {v0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;

    move-result-object v0

    .line 1531
    invoke-virtual {v0}, Landroid/content/res/Resources;->getDisplayMetrics()Landroid/util/DisplayMetrics;

    .line 1534
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mIConfigureDependManager:Lcom/adayo/androidauto/depend/IConfigureDependManager;

    invoke-interface {v0}, Lcom/adayo/androidauto/depend/IConfigureDependManager;->getRealDPI()I

    move-result v0

    int-to-float v9, v0

    .line 1535
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mSettings:Lcom/google/android/projection/sink/settings/Settings;

    invoke-virtual {v0}, Lcom/google/android/projection/sink/settings/Settings;->isScaledToScreen()Z

    move-result v10

    .line 1540
    sget-object v0, Lcom/adayo/androidauto/service/AAService;->mEmulatedScreen:Lcom/google/android/projection/sink/video/DimensionUtils$ScreenSettings;

    if-nez v0, :cond_2

    .line 1542
    new-instance v0, Lcom/google/android/projection/sink/video/DimensionUtils$ScreenSettings;

    move-object v5, v0

    invoke-direct/range {v5 .. v10}, Lcom/google/android/projection/sink/video/DimensionUtils$ScreenSettings;-><init>(IIFFZ)V

    sput-object v0, Lcom/adayo/androidauto/service/AAService;->mEmulatedScreen:Lcom/google/android/projection/sink/video/DimensionUtils$ScreenSettings;

    :cond_2
    const-string v0, "AHU.AAS.20240704"

    .line 1546
    new-instance v5, Ljava/lang/StringBuilder;

    invoke-direct {v5}, Ljava/lang/StringBuilder;-><init>()V

    const-string v6, "initAAGal mEmulatedScreen: "

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    sget-object v6, Lcom/adayo/androidauto/service/AAService;->mEmulatedScreen:Lcom/google/android/projection/sink/video/DimensionUtils$ScreenSettings;

    invoke-virtual {v6}, Lcom/google/android/projection/sink/video/DimensionUtils$ScreenSettings;->toString()Ljava/lang/String;

    move-result-object v6

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v5}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v5

    invoke-static {v0, v5}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    const-string v0, "AHU.AAS.20240704"

    .line 1547
    new-instance v5, Ljava/lang/StringBuilder;

    invoke-direct {v5}, Ljava/lang/StringBuilder;-><init>()V

    const-string v6, "initAAGal before makeRequestedVideoSize surface Width: "

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v5, v3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v6, ",Height="

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v5, v4}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v5}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v5

    invoke-static {v0, v5}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 1550
    sget-object v0, Lcom/adayo/androidauto/service/AAService;->mEmulatedScreen:Lcom/google/android/projection/sink/video/DimensionUtils$ScreenSettings;

    invoke-static {v3, v4, v0}, Lcom/google/android/projection/sink/video/DimensionUtils;->makeRequestedVideoSize(IILcom/google/android/projection/sink/video/DimensionUtils$ScreenSettings;)Landroid/graphics/Rect;

    move-result-object v0

    const-string v5, "AHU.AAS.20240704"

    .line 1551
    new-instance v6, Ljava/lang/StringBuilder;

    invoke-direct {v6}, Ljava/lang/StringBuilder;-><init>()V

    const-string v7, "initAAGal videoSize from mEmulatedScreen Width: "

    invoke-virtual {v6, v7}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Landroid/graphics/Rect;->width()I

    move-result v7

    invoke-virtual {v6, v7}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v7, ",Height="

    invoke-virtual {v6, v7}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Landroid/graphics/Rect;->height()I

    move-result v0

    invoke-virtual {v6, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v6}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v5, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    const/4 v0, 0x4

    new-array v11, v0, [Lcom/google/android/projection/protocol/AudioSink$AudioListener;

    .line 1552
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAudioSinkMediaStreamPlayer:Lcom/google/android/projection/sink/audio/AudioPlayer;

    const/4 v15, 0x0

    aput-object v0, v11, v15

    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAudioSinkTtsStreamPlayer:Lcom/google/android/projection/sink/audio/AudioPlayer;

    const/4 v14, 0x1

    aput-object v0, v11, v14

    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAudioSinkMediaStreamPlayer_AAC:Lcom/google/android/projection/sink/audio/AudioPlayer;

    const/4 v13, 0x2

    aput-object v0, v11, v13

    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAudioSinkTtsStreamPlayer_AAC:Lcom/google/android/projection/sink/audio/AudioPlayer;

    const/4 v12, 0x3

    aput-object v0, v11, v12

    .line 1558
    new-instance v0, Lcom/google/android/projection/sink/navigation/NavigationFocusManager;

    iget-object v5, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    invoke-direct {v0, v5, v1}, Lcom/google/android/projection/sink/navigation/NavigationFocusManager;-><init>(Lcom/adayo/androidauto/AAGalIntegration;Lcom/adayo/androidauto/service/AAService;)V

    iput-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mNavigationFocusManager:Lcom/google/android/projection/sink/navigation/NavigationFocusManager;

    .line 1560
    new-instance v5, Lcom/adayo/androidauto/service/AAService$18;

    invoke-direct {v5, v1}, Lcom/adayo/androidauto/service/AAService$18;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    invoke-virtual {v0, v5}, Lcom/google/android/projection/sink/navigation/NavigationFocusManager;->registerListener(Lcom/google/android/projection/sink/navigation/NavigationFocusManager$NavigationFocusListener;)V

    .line 1572
    new-instance v0, Lcom/google/android/projection/sink/audio/AudioFocusManager;

    iget-object v5, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v6, v1, Lcom/adayo/androidauto/service/AAService;->mSettings:Lcom/google/android/projection/sink/settings/Settings;

    invoke-virtual {v6}, Lcom/google/android/projection/sink/settings/Settings;->isRestrictedAudioFocusUsed()Z

    move-result v6

    invoke-direct {v0, v5, v6, v1}, Lcom/google/android/projection/sink/audio/AudioFocusManager;-><init>(Lcom/adayo/androidauto/AAGalIntegration;ZLcom/adayo/androidauto/service/AAService;)V

    iput-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAudioFocusManager:Lcom/google/android/projection/sink/audio/AudioFocusManager;

    .line 1573
    iget-object v5, v1, Lcom/adayo/androidauto/service/AAService;->mAudioSinkMediaStreamPlayer:Lcom/google/android/projection/sink/audio/AudioPlayer;

    invoke-virtual {v0, v5}, Lcom/google/android/projection/sink/audio/AudioFocusManager;->registerFocusChangeListener(Lcom/google/android/projection/sink/audio/AudioFocusManager$FocusChangeListener;)V

    .line 1574
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAudioFocusManager:Lcom/google/android/projection/sink/audio/AudioFocusManager;

    iget-object v5, v1, Lcom/adayo/androidauto/service/AAService;->mAudioSinkTtsStreamPlayer:Lcom/google/android/projection/sink/audio/AudioPlayer;

    invoke-virtual {v0, v5}, Lcom/google/android/projection/sink/audio/AudioFocusManager;->registerFocusChangeListener(Lcom/google/android/projection/sink/audio/AudioFocusManager$FocusChangeListener;)V

    .line 1575
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAudioFocusManager:Lcom/google/android/projection/sink/audio/AudioFocusManager;

    iget-object v5, v1, Lcom/adayo/androidauto/service/AAService;->mAudioSinkMediaStreamPlayer_AAC:Lcom/google/android/projection/sink/audio/AudioPlayer;

    invoke-virtual {v0, v5}, Lcom/google/android/projection/sink/audio/AudioFocusManager;->registerFocusChangeListener(Lcom/google/android/projection/sink/audio/AudioFocusManager$FocusChangeListener;)V

    .line 1576
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAudioFocusManager:Lcom/google/android/projection/sink/audio/AudioFocusManager;

    iget-object v5, v1, Lcom/adayo/androidauto/service/AAService;->mAudioSinkTtsStreamPlayer_AAC:Lcom/google/android/projection/sink/audio/AudioPlayer;

    invoke-virtual {v0, v5}, Lcom/google/android/projection/sink/audio/AudioFocusManager;->registerFocusChangeListener(Lcom/google/android/projection/sink/audio/AudioFocusManager$FocusChangeListener;)V

    .line 1578
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAudioSinkMediaStreamPlayer:Lcom/google/android/projection/sink/audio/AudioPlayer;

    iget-object v5, v1, Lcom/adayo/androidauto/service/AAService;->mAudioFocusManager:Lcom/google/android/projection/sink/audio/AudioFocusManager;

    invoke-virtual {v0, v5}, Lcom/google/android/projection/sink/audio/AudioPlayer;->registerAudioFocusManager(Lcom/google/android/projection/sink/audio/AudioFocusManager;)V

    .line 1579
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAudioSinkTtsStreamPlayer:Lcom/google/android/projection/sink/audio/AudioPlayer;

    iget-object v5, v1, Lcom/adayo/androidauto/service/AAService;->mAudioFocusManager:Lcom/google/android/projection/sink/audio/AudioFocusManager;

    invoke-virtual {v0, v5}, Lcom/google/android/projection/sink/audio/AudioPlayer;->registerAudioFocusManager(Lcom/google/android/projection/sink/audio/AudioFocusManager;)V

    .line 1580
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAudioSinkMediaStreamPlayer_AAC:Lcom/google/android/projection/sink/audio/AudioPlayer;

    iget-object v5, v1, Lcom/adayo/androidauto/service/AAService;->mAudioFocusManager:Lcom/google/android/projection/sink/audio/AudioFocusManager;

    invoke-virtual {v0, v5}, Lcom/google/android/projection/sink/audio/AudioPlayer;->registerAudioFocusManager(Lcom/google/android/projection/sink/audio/AudioFocusManager;)V

    .line 1581
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAudioSinkTtsStreamPlayer_AAC:Lcom/google/android/projection/sink/audio/AudioPlayer;

    iget-object v5, v1, Lcom/adayo/androidauto/service/AAService;->mAudioFocusManager:Lcom/google/android/projection/sink/audio/AudioFocusManager;

    invoke-virtual {v0, v5}, Lcom/google/android/projection/sink/audio/AudioPlayer;->registerAudioFocusManager(Lcom/google/android/projection/sink/audio/AudioFocusManager;)V

    .line 1583
    new-instance v10, Lcom/adayo/androidauto/service/AAService$19;

    invoke-direct {v10, v1}, Lcom/adayo/androidauto/service/AAService$19;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    .line 1605
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mSensorDispatcher:Lcom/google/android/projection/sink/sensors/SensorManager;

    invoke-virtual {v0}, Lcom/google/android/projection/sink/sensors/SensorManager;->getSupportedSensors()Ljava/util/List;

    move-result-object v0

    .line 1606
    invoke-interface {v0}, Ljava/util/List;->size()I

    move-result v5

    new-array v8, v5, [I

    move v5, v15

    .line 1607
    :goto_1
    invoke-interface {v0}, Ljava/util/List;->size()I

    move-result v6

    if-ge v5, v6, :cond_3

    .line 1608
    invoke-interface {v0, v5}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v6

    check-cast v6, Ljava/lang/Integer;

    invoke-virtual {v6}, Ljava/lang/Integer;->intValue()I

    move-result v6

    aput v6, v8, v5

    add-int/lit8 v5, v5, 0x1

    goto :goto_1

    .line 1611
    :cond_3
    new-instance v9, Lcom/google/android/projection/sink/InstrumentClusterListener;

    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    iget-object v5, v1, Lcom/adayo/androidauto/service/AAService;->mStatusHandler:Landroid/os/Handler;

    invoke-direct {v9, v0, v5}, Lcom/google/android/projection/sink/InstrumentClusterListener;-><init>(Landroid/content/Context;Landroid/os/Handler;)V

    .line 1613
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->carAdapter:Lcom/adayo/androidauto/CarAdapter;

    invoke-virtual {v0}, Lcom/adayo/androidauto/CarAdapter;->getBluetoothMacAddress()Ljava/lang/String;

    move-result-object v0

    .line 1614
    invoke-static {v0}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z

    move-result v5
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    if-eqz v5, :cond_6

    move-object v5, v0

    move v6, v15

    :goto_2
    if-ge v6, v12, :cond_5

    const-wide/16 v16, 0x3e8

    .line 1617
    :try_start_2
    invoke-static/range {v16 .. v17}, Ljava/lang/Thread;->sleep(J)V

    .line 1618
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->carAdapter:Lcom/adayo/androidauto/CarAdapter;

    invoke-virtual {v0}, Lcom/adayo/androidauto/CarAdapter;->getBluetoothMacAddress()Ljava/lang/String;

    move-result-object v5

    .line 1619
    invoke-static {v5}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z

    move-result v0
    :try_end_2
    .catch Ljava/lang/InterruptedException; {:try_start_2 .. :try_end_2} :catch_1
    .catchall {:try_start_2 .. :try_end_2} :catchall_0

    if-nez v0, :cond_4

    goto :goto_3

    :catch_1
    move-exception v0

    .line 1623
    :try_start_3
    invoke-virtual {v0}, Ljava/lang/InterruptedException;->printStackTrace()V

    :cond_4
    add-int/lit8 v6, v6, 0x1

    goto :goto_2

    :cond_5
    :goto_3
    move-object v6, v5

    goto :goto_4

    :cond_6
    move-object v6, v0

    .line 1628
    :goto_4
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAndroidAutoWirelessService:Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;

    invoke-virtual {v0}, Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;->getWifiBssid()Ljava/lang/String;

    move-result-object v16

    .line 1629
    iget-object v5, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mSettings:Lcom/google/android/projection/sink/settings/Settings;

    .line 1631
    invoke-virtual {v0}, Lcom/google/android/projection/sink/settings/Settings;->getSupportedBluetoothPairingMethods()[I

    move-result-object v7

    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mSurfaceView:Landroid/view/SurfaceView;

    iget-object v12, v1, Lcom/adayo/androidauto/service/AAService;->mSettings:Lcom/google/android/projection/sink/settings/Settings;

    .line 1634
    invoke-virtual {v12}, Lcom/google/android/projection/sink/settings/Settings;->grantFocusOnStart()Z

    move-result v12

    iget-object v13, v1, Lcom/adayo/androidauto/service/AAService;->mAudioSourceListener:Lcom/google/android/projection/sink/audio/MicrophoneRecorder;

    iget-object v14, v1, Lcom/adayo/androidauto/service/AAService;->mProjectionListener:Lcom/google/android/projection/protocol/VideoSink$ProjectionListener;

    move-object/from16 v18, v9

    move-object v9, v0

    move-object/from16 v19, v10

    move v10, v12

    move/from16 v20, v3

    const/4 v3, 0x3

    move-object/from16 v12, v18

    const/4 v3, 0x2

    move v3, v15

    move-object/from16 v15, v19

    .line 1629
    invoke-virtual/range {v5 .. v16}, Lcom/adayo/androidauto/AAGalIntegration;->setup(Ljava/lang/String;[I[ILandroid/view/SurfaceView;Z[Lcom/google/android/projection/protocol/AudioSink$AudioListener;Lcom/google/android/projection/sink/InstrumentClusterListener;Lcom/google/android/projection/sink/audio/MicrophoneRecorder;Lcom/google/android/projection/protocol/VideoSink$ProjectionListener;Lcom/google/android/projection/protocol/BluetoothEndpoint$BluetoothEventListener;Ljava/lang/String;)V

    .line 1641
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAdayoBtManager:Lcom/adayo/androidauto/bt/AdayoBtManager;

    iget-object v5, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v5, v5, Lcom/adayo/androidauto/AAGalIntegration;->bluetooth:Lcom/google/android/projection/protocol/BluetoothEndpoint;

    invoke-virtual {v0, v5}, Lcom/adayo/androidauto/bt/AdayoBtManager;->setBluetoothEndpoint(Lcom/google/android/projection/protocol/BluetoothEndpoint;)V

    .line 1646
    iget v6, v1, Lcom/adayo/androidauto/service/AAService;->widthPixel:I

    iget v7, v1, Lcom/adayo/androidauto/service/AAService;->heightPixel:I

    const/16 v8, 0x780

    const/16 v9, 0x438

    iget v10, v1, Lcom/adayo/androidauto/service/AAService;->widthPhysical:I

    iget v11, v1, Lcom/adayo/androidauto/service/AAService;->heightPhysical:I

    invoke-static/range {v6 .. v11}, Lcom/google/android/projection/sink/video/DimensionUtils;->calSreen(IIIIII)Lcom/google/android/projection/sink/video/DimensionUtils$ScreenInfo;

    move-result-object v0

    .line 1647
    iget-object v5, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v6, v5, Lcom/adayo/androidauto/AAGalIntegration;->mainDisplayVideoSink:Lcom/google/android/projection/protocol/VideoSink;

    .line 1648
    invoke-virtual {v0}, Lcom/google/android/projection/sink/video/DimensionUtils$ScreenInfo;->getmWidthMargin()I

    move-result v5

    rsub-int v7, v5, 0x780

    .line 1649
    invoke-virtual {v0}, Lcom/google/android/projection/sink/video/DimensionUtils$ScreenInfo;->getmHeightMargin()I

    move-result v5

    rsub-int v8, v5, 0x438

    iget v9, v1, Lcom/adayo/androidauto/service/AAService;->scaleDpiH:I

    .line 1651
    invoke-virtual {v0}, Lcom/google/android/projection/sink/video/DimensionUtils$ScreenInfo;->getmRealDpi()I

    move-result v10

    iget v11, v1, Lcom/adayo/androidauto/service/AAService;->maxFps:I

    const/4 v12, 0x1

    iget v0, v1, Lcom/adayo/androidauto/service/AAService;->viewingDistance:I

    .line 1653
    invoke-static {v0}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v13

    iget v0, v1, Lcom/adayo/androidauto/service/AAService;->par:F

    .line 1654
    invoke-static {v0}, Ljava/lang/Float;->valueOf(F)Ljava/lang/Float;

    move-result-object v14

    .line 1648
    invoke-virtual/range {v6 .. v14}, Lcom/google/android/projection/protocol/VideoSink;->nearestVideoConfiguration(IIIIIILjava/lang/Integer;Ljava/lang/Float;)Lcom/google/android/projection/proto/Protos$VideoConfiguration;

    move-result-object v0

    .line 1655
    iget-object v5, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v5, v5, Lcom/adayo/androidauto/AAGalIntegration;->mainDisplayVideoSink:Lcom/google/android/projection/protocol/VideoSink;

    .line 1656
    invoke-virtual {v5, v0}, Lcom/google/android/projection/protocol/VideoSink;->addSupportedConfiguration(Lcom/google/android/projection/proto/Protos$VideoConfiguration;)V

    .line 1661
    iget v6, v1, Lcom/adayo/androidauto/service/AAService;->widthPixel:I

    iget v7, v1, Lcom/adayo/androidauto/service/AAService;->heightPixel:I

    const/16 v8, 0x500

    const/16 v9, 0x2d0

    iget v10, v1, Lcom/adayo/androidauto/service/AAService;->widthPhysical:I

    iget v11, v1, Lcom/adayo/androidauto/service/AAService;->heightPhysical:I

    invoke-static/range {v6 .. v11}, Lcom/google/android/projection/sink/video/DimensionUtils;->calSreen(IIIIII)Lcom/google/android/projection/sink/video/DimensionUtils$ScreenInfo;

    move-result-object v0

    .line 1662
    iget-object v5, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v6, v5, Lcom/adayo/androidauto/AAGalIntegration;->mainDisplayVideoSink:Lcom/google/android/projection/protocol/VideoSink;

    .line 1663
    invoke-virtual {v0}, Lcom/google/android/projection/sink/video/DimensionUtils$ScreenInfo;->getmWidthMargin()I

    move-result v5

    rsub-int v7, v5, 0x500

    invoke-virtual {v0}, Lcom/google/android/projection/sink/video/DimensionUtils$ScreenInfo;->getmHeightMargin()I

    move-result v5

    rsub-int v8, v5, 0x2d0

    iget v9, v1, Lcom/adayo/androidauto/service/AAService;->scaleDpiM:I

    .line 1665
    invoke-virtual {v0}, Lcom/google/android/projection/sink/video/DimensionUtils$ScreenInfo;->getmRealDpi()I

    move-result v10

    iget v11, v1, Lcom/adayo/androidauto/service/AAService;->maxFps:I

    const/4 v12, 0x1

    iget v0, v1, Lcom/adayo/androidauto/service/AAService;->viewingDistance:I

    .line 1668
    invoke-static {v0}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v13

    iget v0, v1, Lcom/adayo/androidauto/service/AAService;->par:F

    .line 1669
    invoke-static {v0}, Ljava/lang/Float;->valueOf(F)Ljava/lang/Float;

    move-result-object v14

    .line 1663
    invoke-virtual/range {v6 .. v14}, Lcom/google/android/projection/protocol/VideoSink;->nearestVideoConfiguration(IIIIIILjava/lang/Integer;Ljava/lang/Float;)Lcom/google/android/projection/proto/Protos$VideoConfiguration;

    move-result-object v0

    .line 1670
    iget-object v5, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v5, v5, Lcom/adayo/androidauto/AAGalIntegration;->mainDisplayVideoSink:Lcom/google/android/projection/protocol/VideoSink;

    .line 1671
    invoke-virtual {v5, v0}, Lcom/google/android/projection/protocol/VideoSink;->addSupportedConfiguration(Lcom/google/android/projection/proto/Protos$VideoConfiguration;)V

    .line 1675
    iget v6, v1, Lcom/adayo/androidauto/service/AAService;->widthPixel:I

    iget v7, v1, Lcom/adayo/androidauto/service/AAService;->heightPixel:I

    const/16 v8, 0x320

    const/16 v9, 0x1e0

    iget v10, v1, Lcom/adayo/androidauto/service/AAService;->widthPhysical:I

    iget v11, v1, Lcom/adayo/androidauto/service/AAService;->heightPhysical:I

    invoke-static/range {v6 .. v11}, Lcom/google/android/projection/sink/video/DimensionUtils;->calSreen(IIIIII)Lcom/google/android/projection/sink/video/DimensionUtils$ScreenInfo;

    move-result-object v0

    .line 1676
    iget-object v5, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v6, v5, Lcom/adayo/androidauto/AAGalIntegration;->mainDisplayVideoSink:Lcom/google/android/projection/protocol/VideoSink;

    .line 1677
    invoke-virtual {v0}, Lcom/google/android/projection/sink/video/DimensionUtils$ScreenInfo;->getmWidthMargin()I

    move-result v5

    rsub-int v7, v5, 0x320

    invoke-virtual {v0}, Lcom/google/android/projection/sink/video/DimensionUtils$ScreenInfo;->getmHeightMargin()I

    move-result v5

    rsub-int v8, v5, 0x1e0

    iget v9, v1, Lcom/adayo/androidauto/service/AAService;->scaleDpiL:I

    .line 1679
    invoke-virtual {v0}, Lcom/google/android/projection/sink/video/DimensionUtils$ScreenInfo;->getmRealDpi()I

    move-result v10

    iget v11, v1, Lcom/adayo/androidauto/service/AAService;->maxFps:I

    const/4 v12, 0x1

    iget v0, v1, Lcom/adayo/androidauto/service/AAService;->viewingDistance:I

    .line 1682
    invoke-static {v0}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v13

    iget v0, v1, Lcom/adayo/androidauto/service/AAService;->par:F

    .line 1683
    invoke-static {v0}, Ljava/lang/Float;->valueOf(F)Ljava/lang/Float;

    move-result-object v14

    .line 1677
    invoke-virtual/range {v6 .. v14}, Lcom/google/android/projection/protocol/VideoSink;->nearestVideoConfiguration(IIIIIILjava/lang/Integer;Ljava/lang/Float;)Lcom/google/android/projection/proto/Protos$VideoConfiguration;

    move-result-object v0

    .line 1684
    iget-object v5, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v5, v5, Lcom/adayo/androidauto/AAGalIntegration;->mainDisplayVideoSink:Lcom/google/android/projection/protocol/VideoSink;

    invoke-virtual {v5, v0}, Lcom/google/android/projection/protocol/VideoSink;->addSupportedConfiguration(Lcom/google/android/projection/proto/Protos$VideoConfiguration;)V

    .line 1687
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v0, v0, Lcom/adayo/androidauto/AAGalIntegration;->mainDisplayVideoSink:Lcom/google/android/projection/protocol/VideoSink;

    sget-object v5, Lcom/google/android/projection/proto/Protos$MediaCodecType;->MEDIA_CODEC_VIDEO_H264_BP:Lcom/google/android/projection/proto/Protos$MediaCodecType;

    .line 1688
    invoke-virtual {v0, v5}, Lcom/google/android/projection/protocol/VideoSink;->setCodecType(Lcom/google/android/projection/proto/Protos$MediaCodecType;)V

    .line 1689
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v0, v0, Lcom/adayo/androidauto/AAGalIntegration;->mainDisplayVideoSink:Lcom/google/android/projection/protocol/VideoSink;

    sget-object v5, Lcom/google/android/projection/proto/Protos$DisplayType;->DISPLAY_TYPE_MAIN:Lcom/google/android/projection/proto/Protos$DisplayType;

    .line 1690
    invoke-virtual {v0, v3, v5}, Lcom/google/android/projection/protocol/VideoSink;->setDisplayIdAndType(ILcom/google/android/projection/proto/Protos$DisplayType;)V

    .line 1692
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v0, v0, Lcom/adayo/androidauto/AAGalIntegration;->audioSinks:[Lcom/google/android/projection/protocol/AudioSink;

    aget-object v0, v0, v3

    const/16 v5, 0x10

    const v6, 0xbb80

    const/4 v7, 0x2

    .line 1693
    invoke-virtual {v0, v6, v5, v7}, Lcom/google/android/projection/protocol/AudioSink;->addSupportedConfiguration(III)V

    .line 1694
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v0, v0, Lcom/adayo/androidauto/AAGalIntegration;->audioSinks:[Lcom/google/android/projection/protocol/AudioSink;

    aget-object v0, v0, v3

    sget-object v7, Lcom/google/android/projection/proto/Protos$AudioStreamType;->AUDIO_STREAM_MEDIA:Lcom/google/android/projection/proto/Protos$AudioStreamType;

    .line 1695
    invoke-virtual {v0, v7}, Lcom/google/android/projection/protocol/AudioSink;->setAudioType(Lcom/google/android/projection/proto/Protos$AudioStreamType;)V

    .line 1696
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v0, v0, Lcom/adayo/androidauto/AAGalIntegration;->audioSinks:[Lcom/google/android/projection/protocol/AudioSink;

    aget-object v0, v0, v3

    sget-object v7, Lcom/google/android/projection/proto/Protos$MediaCodecType;->MEDIA_CODEC_AUDIO_PCM:Lcom/google/android/projection/proto/Protos$MediaCodecType;

    .line 1697
    invoke-virtual {v0, v7}, Lcom/google/android/projection/protocol/AudioSink;->setCodecType(Lcom/google/android/projection/proto/Protos$MediaCodecType;)V

    .line 1699
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v0, v0, Lcom/adayo/androidauto/AAGalIntegration;->audioSinks:[Lcom/google/android/projection/protocol/AudioSink;

    const/4 v7, 0x1

    aget-object v0, v0, v7

    .line 1700
    invoke-virtual {v0, v6, v5, v7}, Lcom/google/android/projection/protocol/AudioSink;->addSupportedConfiguration(III)V

    .line 1701
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v0, v0, Lcom/adayo/androidauto/AAGalIntegration;->audioSinks:[Lcom/google/android/projection/protocol/AudioSink;

    aget-object v0, v0, v7

    sget-object v8, Lcom/google/android/projection/proto/Protos$AudioStreamType;->AUDIO_STREAM_GUIDANCE:Lcom/google/android/projection/proto/Protos$AudioStreamType;

    .line 1702
    invoke-virtual {v0, v8}, Lcom/google/android/projection/protocol/AudioSink;->setAudioType(Lcom/google/android/projection/proto/Protos$AudioStreamType;)V

    .line 1703
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v0, v0, Lcom/adayo/androidauto/AAGalIntegration;->audioSinks:[Lcom/google/android/projection/protocol/AudioSink;

    aget-object v0, v0, v7

    sget-object v8, Lcom/google/android/projection/proto/Protos$MediaCodecType;->MEDIA_CODEC_AUDIO_PCM:Lcom/google/android/projection/proto/Protos$MediaCodecType;

    .line 1704
    invoke-virtual {v0, v8}, Lcom/google/android/projection/protocol/AudioSink;->setCodecType(Lcom/google/android/projection/proto/Protos$MediaCodecType;)V

    .line 1706
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v0, v0, Lcom/adayo/androidauto/AAGalIntegration;->audioSinks:[Lcom/google/android/projection/protocol/AudioSink;

    const/4 v8, 0x2

    aget-object v0, v0, v8

    .line 1707
    invoke-virtual {v0, v6, v5, v8}, Lcom/google/android/projection/protocol/AudioSink;->addSupportedConfiguration(III)V

    .line 1708
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v0, v0, Lcom/adayo/androidauto/AAGalIntegration;->audioSinks:[Lcom/google/android/projection/protocol/AudioSink;

    aget-object v0, v0, v8

    sget-object v9, Lcom/google/android/projection/proto/Protos$AudioStreamType;->AUDIO_STREAM_MEDIA:Lcom/google/android/projection/proto/Protos$AudioStreamType;

    .line 1709
    invoke-virtual {v0, v9}, Lcom/google/android/projection/protocol/AudioSink;->setAudioType(Lcom/google/android/projection/proto/Protos$AudioStreamType;)V

    .line 1710
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v0, v0, Lcom/adayo/androidauto/AAGalIntegration;->audioSinks:[Lcom/google/android/projection/protocol/AudioSink;

    aget-object v0, v0, v8

    sget-object v8, Lcom/google/android/projection/proto/Protos$MediaCodecType;->MEDIA_CODEC_AUDIO_AAC_LC:Lcom/google/android/projection/proto/Protos$MediaCodecType;

    .line 1711
    invoke-virtual {v0, v8}, Lcom/google/android/projection/protocol/AudioSink;->setCodecType(Lcom/google/android/projection/proto/Protos$MediaCodecType;)V

    .line 1713
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v0, v0, Lcom/adayo/androidauto/AAGalIntegration;->audioSinks:[Lcom/google/android/projection/protocol/AudioSink;

    const/4 v8, 0x3

    aget-object v0, v0, v8

    .line 1714
    invoke-virtual {v0, v6, v5, v7}, Lcom/google/android/projection/protocol/AudioSink;->addSupportedConfiguration(III)V

    .line 1715
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v0, v0, Lcom/adayo/androidauto/AAGalIntegration;->audioSinks:[Lcom/google/android/projection/protocol/AudioSink;

    aget-object v0, v0, v8

    sget-object v5, Lcom/google/android/projection/proto/Protos$AudioStreamType;->AUDIO_STREAM_GUIDANCE:Lcom/google/android/projection/proto/Protos$AudioStreamType;

    .line 1716
    invoke-virtual {v0, v5}, Lcom/google/android/projection/protocol/AudioSink;->setAudioType(Lcom/google/android/projection/proto/Protos$AudioStreamType;)V

    .line 1717
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v0, v0, Lcom/adayo/androidauto/AAGalIntegration;->audioSinks:[Lcom/google/android/projection/protocol/AudioSink;

    aget-object v0, v0, v8

    sget-object v5, Lcom/google/android/projection/proto/Protos$MediaCodecType;->MEDIA_CODEC_AUDIO_AAC_LC:Lcom/google/android/projection/proto/Protos$MediaCodecType;

    .line 1718
    invoke-virtual {v0, v5}, Lcom/google/android/projection/protocol/AudioSink;->setCodecType(Lcom/google/android/projection/proto/Protos$MediaCodecType;)V

    .line 1720
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAudioSourceListener:Lcom/google/android/projection/sink/audio/MicrophoneRecorder;

    iget-object v5, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v5, v5, Lcom/adayo/androidauto/AAGalIntegration;->audioSource:Lcom/google/android/projection/protocol/AudioSource;

    .line 1721
    invoke-virtual {v0, v5}, Lcom/google/android/projection/sink/audio/MicrophoneRecorder;->setGalAudioSource(Lcom/google/android/projection/protocol/AudioSource;)V

    .line 1724
    invoke-direct/range {p0 .. p0}, Lcom/adayo/androidauto/service/AAService;->getKeys()[I

    move-result-object v0

    .line 1725
    iget-object v5, v1, Lcom/adayo/androidauto/service/AAService;->mSettings:Lcom/google/android/projection/sink/settings/Settings;

    invoke-virtual {v5}, Lcom/google/android/projection/sink/settings/Settings;->isRotaryControllerEnabled()Z

    move-result v5

    if-eqz v5, :cond_7

    .line 1726
    array-length v5, v0

    add-int/2addr v5, v7

    invoke-static {v0, v5}, Ljava/util/Arrays;->copyOf([II)[I

    move-result-object v0

    .line 1728
    array-length v5, v0

    sub-int/2addr v5, v7

    sget-object v6, Lcom/google/android/projection/proto/Protos$KeyCode;->KEYCODE_ROTARY_CONTROLLER:Lcom/google/android/projection/proto/Protos$KeyCode;

    .line 1729
    invoke-virtual {v6}, Lcom/google/android/projection/proto/Protos$KeyCode;->getNumber()I

    move-result v6

    aput v6, v0, v5

    .line 1731
    :cond_7
    iget-object v5, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v5, v5, Lcom/adayo/androidauto/AAGalIntegration;->mainDisplayInputSource:Lcom/google/android/projection/protocol/InputSource;

    invoke-virtual {v5, v0}, Lcom/google/android/projection/protocol/InputSource;->registerKeyCodes([I)V

    .line 1733
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v0, v0, Lcom/adayo/androidauto/AAGalIntegration;->mainDisplayInputSource:Lcom/google/android/projection/protocol/InputSource;

    invoke-virtual {v0, v3}, Lcom/google/android/projection/protocol/InputSource;->setDisplayId(I)V

    .line 1735
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mSettings:Lcom/google/android/projection/sink/settings/Settings;

    invoke-virtual {v0}, Lcom/google/android/projection/sink/settings/Settings;->isTouchScreenEnabled()Z

    move-result v0

    if-eqz v0, :cond_8

    .line 1736
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v0, v0, Lcom/adayo/androidauto/AAGalIntegration;->mainDisplayInputSource:Lcom/google/android/projection/protocol/InputSource;

    sget v5, Lcom/google/android/projection/protocol/InputSource;->TOUCHSCREEN_TYPE_CAPACITIVE:I

    move/from16 v6, v20

    invoke-virtual {v0, v6, v4, v5}, Lcom/google/android/projection/protocol/InputSource;->registerTouchScreen(III)V

    .line 1739
    :cond_8
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v0, v0, Lcom/adayo/androidauto/AAGalIntegration;->mainDisplayInputSource:Lcom/google/android/projection/protocol/InputSource;

    const/4 v4, 0x3

    new-array v5, v4, [I

    sget v4, Lcom/google/android/projection/protocol/InputSource;->FEEDBACK_EVENT_SELECT:I

    aput v4, v5, v3

    sget v4, Lcom/google/android/projection/protocol/InputSource;->FEEDBACK_EVENT_DRAG_SELECT:I

    aput v4, v5, v7

    sget v4, Lcom/google/android/projection/protocol/InputSource;->FEEDBACK_EVENT_FOCUS_CHANGE:I

    const/4 v6, 0x2

    aput v4, v5, v6

    new-instance v4, Lcom/adayo/androidauto/service/AAService$20;

    invoke-direct {v4, v1}, Lcom/adayo/androidauto/service/AAService$20;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    invoke-virtual {v0, v5, v4}, Lcom/google/android/projection/protocol/InputSource;->registerFeedbackEvents([ILcom/google/android/projection/protocol/InputSource$OnInputFeedbackListener;)V

    .line 1764
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v0, v0, Lcom/adayo/androidauto/AAGalIntegration;->audioSource:Lcom/google/android/projection/protocol/AudioSource;

    iget-object v4, v1, Lcom/adayo/androidauto/service/AAService;->mAudioSourceListener:Lcom/google/android/projection/sink/audio/MicrophoneRecorder;

    .line 1765
    invoke-virtual {v4}, Lcom/google/android/projection/sink/audio/MicrophoneRecorder;->getSamplingRate()I

    move-result v4

    iget-object v5, v1, Lcom/adayo/androidauto/service/AAService;->mAudioSourceListener:Lcom/google/android/projection/sink/audio/MicrophoneRecorder;

    .line 1766
    invoke-virtual {v5}, Lcom/google/android/projection/sink/audio/MicrophoneRecorder;->getNumberOfBits()I

    move-result v5

    iget-object v6, v1, Lcom/adayo/androidauto/service/AAService;->mAudioSourceListener:Lcom/google/android/projection/sink/audio/MicrophoneRecorder;

    .line 1767
    invoke-virtual {v6}, Lcom/google/android/projection/sink/audio/MicrophoneRecorder;->getNumberOfChannels()I

    move-result v6

    .line 1764
    invoke-virtual {v0, v4, v5, v6}, Lcom/google/android/projection/protocol/AudioSource;->setConfig(III)V

    .line 1768
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAudioSinkMediaStreamPlayer:Lcom/google/android/projection/sink/audio/AudioPlayer;

    iget-object v4, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v4, v4, Lcom/adayo/androidauto/AAGalIntegration;->audioSinks:[Lcom/google/android/projection/protocol/AudioSink;

    aget-object v3, v4, v3

    .line 1769
    invoke-virtual {v0, v3}, Lcom/google/android/projection/sink/audio/AudioPlayer;->registerAudioSink(Lcom/google/android/projection/protocol/AudioSink;)V

    .line 1770
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAudioSinkTtsStreamPlayer:Lcom/google/android/projection/sink/audio/AudioPlayer;

    iget-object v3, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v3, v3, Lcom/adayo/androidauto/AAGalIntegration;->audioSinks:[Lcom/google/android/projection/protocol/AudioSink;

    aget-object v3, v3, v7

    .line 1771
    invoke-virtual {v0, v3}, Lcom/google/android/projection/sink/audio/AudioPlayer;->registerAudioSink(Lcom/google/android/projection/protocol/AudioSink;)V

    .line 1772
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAudioSinkMediaStreamPlayer_AAC:Lcom/google/android/projection/sink/audio/AudioPlayer;

    iget-object v3, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v3, v3, Lcom/adayo/androidauto/AAGalIntegration;->audioSinks:[Lcom/google/android/projection/protocol/AudioSink;

    const/4 v4, 0x2

    aget-object v3, v3, v4

    .line 1773
    invoke-virtual {v0, v3}, Lcom/google/android/projection/sink/audio/AudioPlayer;->registerAudioSink(Lcom/google/android/projection/protocol/AudioSink;)V

    .line 1774
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mAudioSinkTtsStreamPlayer_AAC:Lcom/google/android/projection/sink/audio/AudioPlayer;

    iget-object v3, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v3, v3, Lcom/adayo/androidauto/AAGalIntegration;->audioSinks:[Lcom/google/android/projection/protocol/AudioSink;

    const/4 v4, 0x3

    aget-object v3, v3, v4

    .line 1775
    invoke-virtual {v0, v3}, Lcom/google/android/projection/sink/audio/AudioPlayer;->registerAudioSink(Lcom/google/android/projection/protocol/AudioSink;)V

    .line 1776
    iget-object v0, v1, Lcom/adayo/androidauto/service/AAService;->mSensorDispatcher:Lcom/google/android/projection/sink/sensors/SensorManager;

    iget-object v3, v1, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    invoke-virtual {v0, v3}, Lcom/google/android/projection/sink/sensors/SensorManager;->setGalIntegration(Lcom/adayo/androidauto/AAGalIntegration;)V

    .line 1777
    monitor-exit v2

    return-void

    :catchall_0
    move-exception v0

    monitor-exit v2
    :try_end_3
    .catchall {:try_start_3 .. :try_end_3} :catchall_0

    throw v0
.end method

.method private initCarInfo()V
    .locals 18

    move-object/from16 v0, p0

    .line 1430
    iget-object v1, v0, Lcom/adayo/androidauto/service/AAService;->mDebugContext:Lcom/google/android/projection/sink/debug/DebugContext;

    if-nez v1, :cond_0

    .line 1431
    new-instance v1, Lcom/google/android/projection/sink/debug/DebugContext;

    iget-object v2, v0, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    invoke-direct {v1, v2}, Lcom/google/android/projection/sink/debug/DebugContext;-><init>(Landroid/content/Context;)V

    iput-object v1, v0, Lcom/adayo/androidauto/service/AAService;->mDebugContext:Lcom/google/android/projection/sink/debug/DebugContext;

    .line 1433
    :cond_0
    iget-object v1, v0, Lcom/adayo/androidauto/service/AAService;->mSettings:Lcom/google/android/projection/sink/settings/Settings;

    if-nez v1, :cond_1

    .line 1434
    new-instance v1, Lcom/google/android/projection/sink/settings/Settings;

    iget-object v2, v0, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    invoke-direct {v1, v2}, Lcom/google/android/projection/sink/settings/Settings;-><init>(Landroid/content/Context;)V

    iput-object v1, v0, Lcom/adayo/androidauto/service/AAService;->mSettings:Lcom/google/android/projection/sink/settings/Settings;

    .line 1438
    :cond_1
    iget-object v1, v0, Lcom/adayo/androidauto/service/AAService;->mLocalModeCallbacks:Lcom/adayo/androidauto/constants/LocalModeCallbacks;

    if-nez v1, :cond_2

    .line 1439
    invoke-static {}, Lcom/adayo/androidauto/constants/LocalModeCallbacksFactory;->getinstance()Lcom/adayo/androidauto/constants/LocalModeCallbacksFactory;

    move-result-object v1

    invoke-virtual {v1}, Lcom/adayo/androidauto/constants/LocalModeCallbacksFactory;->getLocalModeCallbacks()Lcom/adayo/androidauto/constants/LocalModeCallbacks;

    move-result-object v1

    iput-object v1, v0, Lcom/adayo/androidauto/service/AAService;->mLocalModeCallbacks:Lcom/adayo/androidauto/constants/LocalModeCallbacks;

    .line 1447
    :cond_2
    iget-object v1, v0, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    .line 1448
    invoke-virtual {v1}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v1

    const-string v2, "android_id"

    .line 1447
    invoke-static {v1, v2}, Landroid/provider/Settings$Secure;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    .line 1451
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "initCarInfo,vehicleId="

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    const-string v4, "AHU.AAS.20240704"

    invoke-static {v4, v2}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 1452
    invoke-virtual {v1}, Ljava/lang/String;->length()I

    move-result v2

    const/16 v5, 0x10

    if-ge v2, v5, :cond_3

    .line 1453
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v5, "0"

    invoke-virtual {v2, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    :cond_3
    move-object v9, v1

    .line 1455
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v1, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1, v9}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v4, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 1458
    new-instance v1, Lcom/adayo/androidauto/service/AAService$17;

    invoke-direct {v1, v0}, Lcom/adayo/androidauto/service/AAService$17;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    iput-object v1, v0, Lcom/adayo/androidauto/service/AAService;->inputInjectorListener:Lcom/google/android/projection/protocol/InputSource$InputInjector;

    .line 1477
    sget-object v1, Lcom/adayo/androidauto/service/AAService;->mCarInfo:Lcom/google/android/projection/protocol/GalReceiver$CarInfo;

    if-nez v1, :cond_4

    .line 1479
    new-instance v1, Lcom/google/android/projection/protocol/GalReceiver$CarInfo;

    iget-object v6, v0, Lcom/adayo/androidauto/service/AAService;->carManufacturer:Ljava/lang/String;

    iget-object v7, v0, Lcom/adayo/androidauto/service/AAService;->carModel:Ljava/lang/String;

    iget-object v8, v0, Lcom/adayo/androidauto/service/AAService;->carModelYear:Ljava/lang/String;

    iget v10, v0, Lcom/adayo/androidauto/service/AAService;->driverPosition:I

    iget-object v11, v0, Lcom/adayo/androidauto/service/AAService;->headunitMake:Ljava/lang/String;

    iget-object v12, v0, Lcom/adayo/androidauto/service/AAService;->headunitModel:Ljava/lang/String;

    iget-object v2, v0, Lcom/adayo/androidauto/service/AAService;->mIConfigureDependManager:Lcom/adayo/androidauto/depend/IConfigureDependManager;

    .line 1480
    invoke-interface {v2}, Lcom/adayo/androidauto/depend/IConfigureDependManager;->getHeadUnitSoftwareBuild()Ljava/lang/String;

    move-result-object v13

    iget-object v2, v0, Lcom/adayo/androidauto/service/AAService;->mIConfigureDependManager:Lcom/adayo/androidauto/depend/IConfigureDependManager;

    const/4 v3, 0x1

    invoke-interface {v2, v3}, Lcom/adayo/androidauto/depend/IConfigureDependManager;->getHeadUnitSoftwareInfo(I)Ljava/lang/String;

    move-result-object v14

    iget-object v2, v0, Lcom/adayo/androidauto/service/AAService;->mIConfigureDependManager:Lcom/adayo/androidauto/depend/IConfigureDependManager;

    invoke-interface {v2}, Lcom/adayo/androidauto/depend/IConfigureDependManager;->getCarInfoCanPlayNativeMediaDuringVr()Z

    move-result v15

    iget-object v2, v0, Lcom/adayo/androidauto/service/AAService;->mIConfigureDependManager:Lcom/adayo/androidauto/depend/IConfigureDependManager;

    .line 1481
    invoke-interface {v2}, Lcom/adayo/androidauto/depend/IConfigureDependManager;->getCarInfoSessionConfiguration()I

    move-result v16

    iget-object v2, v0, Lcom/adayo/androidauto/service/AAService;->displayName:Ljava/lang/String;

    move-object v5, v1

    move-object/from16 v17, v2

    invoke-direct/range {v5 .. v17}, Lcom/google/android/projection/protocol/GalReceiver$CarInfo;-><init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZILjava/lang/String;)V

    sput-object v1, Lcom/adayo/androidauto/service/AAService;->mCarInfo:Lcom/google/android/projection/protocol/GalReceiver$CarInfo;

    :cond_4
    return-void
.end method

.method private initConfigInfo()V
    .locals 3

    .line 1328
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->config:Lcom/adayo/androidauto/config/LoadJson;

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Information;->getCarManufacturer()Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->carManufacturer:Ljava/lang/String;

    .line 1329
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->config:Lcom/adayo/androidauto/config/LoadJson;

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Information;->getCarModel()Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->carModel:Ljava/lang/String;

    .line 1330
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->config:Lcom/adayo/androidauto/config/LoadJson;

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Information;->getCarModelYear()Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->carModelYear:Ljava/lang/String;

    .line 1331
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->config:Lcom/adayo/androidauto/config/LoadJson;

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Information;->getDriverPosition()I

    move-result v0

    iput v0, p0, Lcom/adayo/androidauto/service/AAService;->driverPosition:I

    .line 1332
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->config:Lcom/adayo/androidauto/config/LoadJson;

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Information;->getHeadunitMake()Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->headunitMake:Ljava/lang/String;

    .line 1333
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->config:Lcom/adayo/androidauto/config/LoadJson;

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Information;->getHeadunitModel()Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->headunitModel:Ljava/lang/String;

    .line 1334
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->config:Lcom/adayo/androidauto/config/LoadJson;

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Information;->getDisplayName()Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->displayName:Ljava/lang/String;

    .line 1335
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->config:Lcom/adayo/androidauto/config/LoadJson;

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Information;->getClientCertificate()Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->clientCertificate:Ljava/lang/String;

    .line 1336
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->config:Lcom/adayo/androidauto/config/LoadJson;

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Information;->getPrivateKey()Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->privateKey:Ljava/lang/String;

    .line 1337
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->config:Lcom/adayo/androidauto/config/LoadJson;

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Information;->getWidthPixel()I

    move-result v0

    iput v0, p0, Lcom/adayo/androidauto/service/AAService;->widthPixel:I

    .line 1338
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->config:Lcom/adayo/androidauto/config/LoadJson;

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Information;->getHeightPixel()I

    move-result v0

    iput v0, p0, Lcom/adayo/androidauto/service/AAService;->heightPixel:I

    .line 1339
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->config:Lcom/adayo/androidauto/config/LoadJson;

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Information;->getWidthPhysical()I

    move-result v0

    iput v0, p0, Lcom/adayo/androidauto/service/AAService;->widthPhysical:I

    .line 1340
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->config:Lcom/adayo/androidauto/config/LoadJson;

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Information;->getHeightPhysical()I

    move-result v0

    const/16 v0, 0x52f

    iput v0, p0, Lcom/adayo/androidauto/service/AAService;->widthPixel:I

    const/16 v0, 0x361

    iput v0, p0, Lcom/adayo/androidauto/service/AAService;->heightPixel:I

    const/16 v0, 0xc5

    iput v0, p0, Lcom/adayo/androidauto/service/AAService;->widthPhysical:I

    const/16 v0, 0x80

    iput v0, p0, Lcom/adayo/androidauto/service/AAService;->heightPhysical:I

    .line 1341
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->config:Lcom/adayo/androidauto/config/LoadJson;

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Information;->getMaxFps()I

    move-result v0

    iput v0, p0, Lcom/adayo/androidauto/service/AAService;->maxFps:I

    .line 1342
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->config:Lcom/adayo/androidauto/config/LoadJson;

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Information;->getPixelAspectRatio()F

    move-result v0

    iput v0, p0, Lcom/adayo/androidauto/service/AAService;->par:F

    .line 1343
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->config:Lcom/adayo/androidauto/config/LoadJson;

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Information;->getViewingDistance()I

    move-result v0

    iput v0, p0, Lcom/adayo/androidauto/service/AAService;->viewingDistance:I

    .line 1344
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->config:Lcom/adayo/androidauto/config/LoadJson;

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Information;->getScaleDpiH()I

    move-result v0

    iput v0, p0, Lcom/adayo/androidauto/service/AAService;->scaleDpiH:I

    .line 1345
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->config:Lcom/adayo/androidauto/config/LoadJson;

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Information;->getScaleDpiM()I

    move-result v0

    iput v0, p0, Lcom/adayo/androidauto/service/AAService;->scaleDpiM:I

    .line 1346
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->config:Lcom/adayo/androidauto/config/LoadJson;

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Information;->getScaleDpiL()I

    move-result v0

    iput v0, p0, Lcom/adayo/androidauto/service/AAService;->scaleDpiL:I

    .line 1351
    :try_start_0
    invoke-static {}, Lcom/adayo/androidauto/service/AAService;->getSystemModuleName()Ljava/lang/String;

    move-result-object v0

    const-string v1, "HMA220C"

    .line 1352
    invoke-virtual {v1, v0}, Ljava/lang/String;->equalsIgnoreCase(Ljava/lang/String;)Z

    move-result v1

    if-nez v1, :cond_0

    const-string v1, "HMA228A"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equalsIgnoreCase(Ljava/lang/String;)Z

    move-result v1

    if-nez v1, :cond_0

    const-string v1, "HMA228B"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equalsIgnoreCase(Ljava/lang/String;)Z

    move-result v0

    if-eqz v0, :cond_1

    :cond_0
    const/4 v0, 0x1

    .line 1353
    iput v0, p0, Lcom/adayo/androidauto/service/AAService;->driverPosition:I
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception v0

    .line 1356
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "getSystemModuleName e: "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/Exception;->getMessage()Ljava/lang/String;

    move-result-object v0

    invoke-virtual {v1, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    const-string v1, "AHU.AAS.20240704"

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V

    :cond_1
    :goto_0
    return-void
.end method

.method private initReceiver()V
    .locals 4

    .line 1292
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    invoke-static {v0}, Landroid/support/v4/content/LocalBroadcastManager;->getInstance(Landroid/content/Context;)Landroid/support/v4/content/LocalBroadcastManager;

    move-result-object v0

    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mUsbDisconnectedReceiver:Landroid/content/BroadcastReceiver;

    new-instance v2, Landroid/content/IntentFilter;

    const-string v3, "usb-disconnect"

    invoke-direct {v2, v3}, Landroid/content/IntentFilter;-><init>(Ljava/lang/String;)V

    invoke-virtual {v0, v1, v2}, Landroid/support/v4/content/LocalBroadcastManager;->registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)V

    .line 1295
    invoke-direct {p0}, Lcom/adayo/androidauto/service/AAService;->registerUsbAccessoryOrDeviceAttachReceiver()V

    .line 1297
    invoke-direct {p0}, Lcom/adayo/androidauto/service/AAService;->registerAAswitchReceiver()V

    .line 1298
    invoke-direct {p0}, Lcom/adayo/androidauto/service/AAService;->checkUsbDeviceAttach()V

    return-void
.end method

.method private initializeSensorServices()V
    .locals 5

    .line 1954
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mSensorDispatcher:Lcom/google/android/projection/sink/sensors/SensorManager;

    if-nez v0, :cond_0

    .line 1955
    new-instance v0, Lcom/google/android/projection/sink/sensors/SensorManager;

    invoke-direct {v0}, Lcom/google/android/projection/sink/sensors/SensorManager;-><init>()V

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mSensorDispatcher:Lcom/google/android/projection/sink/sensors/SensorManager;

    const-string v0, "AHU.AAS.20240704"

    const-string v1, "init SensorDispatcher"

    .line 1956
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 1957
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mSensorDispatcher:Lcom/google/android/projection/sink/sensors/SensorManager;

    const/4 v1, 0x2

    new-array v1, v1, [Lcom/google/android/projection/sink/sensors/SensorCollectorBase;

    const/4 v2, 0x0

    new-instance v3, Lcom/adayo/androidauto/sensors/GpsSensor;

    iget-object v4, p0, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    invoke-direct {v3, v4}, Lcom/adayo/androidauto/sensors/GpsSensor;-><init>(Landroid/content/Context;)V

    aput-object v3, v1, v2

    const/4 v2, 0x1

    iget-object v3, p0, Lcom/adayo/androidauto/service/AAService;->mVehicleInfoSensor:Lcom/adayo/androidauto/sensors/VehicleInfoSensor;

    aput-object v3, v1, v2

    invoke-virtual {v0, v1}, Lcom/google/android/projection/sink/sensors/SensorManager;->init([Lcom/google/android/projection/sink/sensors/SensorCollectorBase;)V

    :cond_0
    return-void
.end method

.method private isA2dpConnected()Z
    .locals 1

    const/4 v0, 0x0

    return v0
.end method

.method private isHfpConnected()Z
    .locals 1

    const/4 v0, 0x0

    return v0
.end method

.method private onUsbEventReceive(Landroid/content/Intent;)V
    .locals 5

    .line 2507
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "onUsbEventReceive action: "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p1}, Landroid/content/Intent;->getAction()Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    const-string v1, "AHU.AAS.20240704"

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2509
    invoke-virtual {p1}, Landroid/content/Intent;->getAction()Ljava/lang/String;

    move-result-object v0

    const-string v2, "android.hardware.usb.action.USB_ACCESSORY_ATTACHED"

    invoke-virtual {v0, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_0

    const-string v0, "accessory"

    .line 2511
    invoke-virtual {p1, v0}, Landroid/content/Intent;->getParcelableExtra(Ljava/lang/String;)Landroid/os/Parcelable;

    move-result-object p1

    check-cast p1, Landroid/hardware/usb/UsbAccessory;

    .line 2513
    invoke-direct {p0, p1}, Lcom/adayo/androidauto/service/AAService;->startAccessoryMode(Landroid/hardware/usb/UsbAccessory;)V

    goto/16 :goto_3

    .line 2514
    :cond_0
    invoke-virtual {p1}, Landroid/content/Intent;->getAction()Ljava/lang/String;

    move-result-object v0

    const-string v2, "android.hardware.usb.action.USB_DEVICE_ATTACHED"

    invoke-virtual {v0, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    const-string v2, "device"

    const/4 v3, 0x0

    if-eqz v0, :cond_8

    .line 2517
    iget-boolean v0, p0, Lcom/adayo/androidauto/service/AAService;->usbDisConnect:Z

    if-eqz v0, :cond_1

    .line 2518
    iput-boolean v3, p0, Lcom/adayo/androidauto/service/AAService;->usbDisConnect:Z

    return-void

    .line 2523
    :cond_1
    invoke-virtual {p1, v2}, Landroid/content/Intent;->getParcelableExtra(Ljava/lang/String;)Landroid/os/Parcelable;

    move-result-object p1

    check-cast p1, Landroid/hardware/usb/UsbDevice;

    .line 2524
    invoke-virtual {p1}, Landroid/hardware/usb/UsbDevice;->getSerialNumber()Ljava/lang/String;

    move-result-object v0

    .line 2525
    invoke-virtual {p1}, Landroid/hardware/usb/UsbDevice;->getDeviceName()Ljava/lang/String;

    .line 2526
    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mCurUSBMDSN:Ljava/lang/String;

    .line 2532
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAndroidAutoWirelessService:Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;

    invoke-virtual {v0}, Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;->getCurWirelessMDSN()Ljava/lang/String;

    move-result-object v0

    .line 2533
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v4, "curWirelessMDSN="

    invoke-virtual {v2, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2534
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "mCurUSBMDSN="

    invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mCurUSBMDSN:Ljava/lang/String;

    invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2535
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "onUsbEventReceive mConnectMode "

    invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mConnectMode:Lcom/adayo/androidauto/service/AAService$ConnectMode;

    invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2538
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAppInforManager:Lcom/adayo/androidauto/AppInfor/AppInforManager;

    invoke-virtual {v0, p1}, Lcom/adayo/androidauto/AppInfor/AppInforManager;->setUsbDevice(Landroid/hardware/usb/UsbDevice;)V

    .line 2539
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAppInforManager:Lcom/adayo/androidauto/AppInfor/AppInforManager;

    const/4 v2, 0x1

    invoke-virtual {v0, v2}, Lcom/adayo/androidauto/AppInfor/AppInforManager;->setUSBConnect(Z)V

    .line 2541
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mConnectMode:Lcom/adayo/androidauto/service/AAService$ConnectMode;

    sget-object v4, Lcom/adayo/androidauto/service/AAService$ConnectMode;->Wireless:Lcom/adayo/androidauto/service/AAService$ConnectMode;

    if-ne v0, v4, :cond_7

    .line 2543
    invoke-virtual {p1, v3}, Landroid/hardware/usb/UsbDevice;->getInterface(I)Landroid/hardware/usb/UsbInterface;

    move-result-object v0

    invoke-virtual {v0}, Landroid/hardware/usb/UsbInterface;->getInterfaceProtocol()I

    move-result v0

    .line 2544
    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    const-string v4, "interfaceProtocol="

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    invoke-static {v1, v3}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    if-eqz v0, :cond_3

    if-ne v0, v2, :cond_2

    goto :goto_0

    :cond_2
    const-string p1, "is not phone usb port"

    .line 2548
    invoke-static {v1, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    return-void

    :cond_3
    :goto_0
    const-string v0, "is phone usb port"

    .line 2546
    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2552
    invoke-virtual {p1}, Landroid/hardware/usb/UsbDevice;->getVendorId()I

    move-result v0

    const/16 v2, 0x5ac

    if-ne v0, v2, :cond_4

    const-string p1, "processDevice device is iphone,return..."

    .line 2553
    invoke-static {v1, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    return-void

    .line 2558
    :cond_4
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAppInforManager:Lcom/adayo/androidauto/AppInfor/AppInforManager;

    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mCurUSBMDSN:Ljava/lang/String;

    invoke-virtual {v0, v2}, Lcom/adayo/androidauto/AppInfor/AppInforManager;->isCurrentRunMDSN(Ljava/lang/String;)Z

    move-result v0

    if-eqz v0, :cond_5

    const-string p1, "onUsbEventReceive,same MD plug USB,so return!"

    .line 2559
    invoke-static {v1, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    goto :goto_1

    :cond_5
    const-string v0, "onUsbEventReceive diff MD plug USB"

    .line 2562
    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2564
    invoke-static {}, Lcom/adayo/androidauto/config/LoadJson;->getInstance()Lcom/adayo/androidauto/config/LoadJson;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Information;->getHeadunitModel()Ljava/lang/String;

    move-result-object v0

    const-string v1, "HMA212"

    invoke-virtual {v0, v1}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v0

    if-eqz v0, :cond_6

    .line 2565
    invoke-direct {p0, p1}, Lcom/adayo/androidauto/service/AAService;->showWiredPlug(Landroid/hardware/usb/UsbDevice;)V

    :cond_6
    :goto_1
    return-void

    .line 2571
    :cond_7
    invoke-direct {p0, p1}, Lcom/adayo/androidauto/service/AAService;->startHostMode(Landroid/hardware/usb/UsbDevice;)Z

    goto/16 :goto_3

    .line 2572
    :cond_8
    invoke-virtual {p1}, Landroid/content/Intent;->getAction()Ljava/lang/String;

    move-result-object v0

    const-string v4, "android.hardware.usb.action.USB_DEVICE_DETACHED"

    invoke-virtual {v0, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_b

    .line 2575
    invoke-virtual {p1, v2}, Landroid/content/Intent;->getParcelableExtra(Ljava/lang/String;)Landroid/os/Parcelable;

    move-result-object p1

    check-cast p1, Landroid/hardware/usb/UsbDevice;

    .line 2576
    invoke-virtual {p1}, Landroid/hardware/usb/UsbDevice;->getDeviceName()Ljava/lang/String;

    move-result-object v0

    invoke-virtual {p0, v0}, Lcom/adayo/androidauto/service/AAService;->getUsbPort(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    .line 2577
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v4, "onUsbEventReceive: ACTION_USB_DEVICE_DETACHED usb="

    invoke-virtual {v2, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2579
    iget-boolean v0, p0, Lcom/adayo/androidauto/service/AAService;->usbDisConnect:Z

    if-eqz v0, :cond_9

    const-wide/16 v2, 0xbb8

    .line 2581
    :try_start_0
    invoke-static {v2, v3}, Ljava/lang/Thread;->sleep(J)V

    .line 2582
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAppInforManager:Lcom/adayo/androidauto/AppInfor/AppInforManager;

    invoke-virtual {v0, p1}, Lcom/adayo/androidauto/AppInfor/AppInforManager;->setUsbDevice(Landroid/hardware/usb/UsbDevice;)V
    :try_end_0
    .catch Ljava/lang/InterruptedException; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_2

    :catch_0
    move-exception p1

    .line 2584
    invoke-virtual {p1}, Ljava/lang/InterruptedException;->printStackTrace()V

    goto :goto_2

    .line 2587
    :cond_9
    iput-boolean v3, p0, Lcom/adayo/androidauto/service/AAService;->usbDisConnect:Z

    .line 2588
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAppInforManager:Lcom/adayo/androidauto/AppInfor/AppInforManager;

    invoke-virtual {v0, p1}, Lcom/adayo/androidauto/AppInfor/AppInforManager;->setUsbDevice(Landroid/hardware/usb/UsbDevice;)V

    .line 2589
    iget-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mAppInforManager:Lcom/adayo/androidauto/AppInfor/AppInforManager;

    invoke-virtual {p1, v3}, Lcom/adayo/androidauto/AppInfor/AppInforManager;->setUSBConnect(Z)V

    .line 2590
    iget-object p1, p0, Lcom/adayo/androidauto/service/AAService;->wiredPlugDialog:Lcom/adayo/androidauto/dialog/WiredPlugDialog;

    if-eqz p1, :cond_a

    invoke-virtual {p1}, Lcom/adayo/androidauto/dialog/WiredPlugDialog;->isShowing()Z

    move-result p1

    if-eqz p1, :cond_a

    .line 2591
    iget-object p1, p0, Lcom/adayo/androidauto/service/AAService;->wiredPlugDialog:Lcom/adayo/androidauto/dialog/WiredPlugDialog;

    invoke-virtual {p1}, Lcom/adayo/androidauto/dialog/WiredPlugDialog;->dismiss()V

    :cond_a
    :goto_2
    const-string p1, "onUsbEventReceive usb_dis"

    .line 2594
    invoke-static {v1, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    goto :goto_3

    .line 2597
    :cond_b
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "unhandle action "

    invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p1}, Landroid/content/Intent;->getAction()Ljava/lang/String;

    move-result-object p1

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-static {v1, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    :goto_3
    return-void
.end method

.method private parsePageTypeFromIntent(Landroid/content/Intent;)V
    .locals 3

    .line 2845
    :try_start_0
    invoke-virtual {p1}, Landroid/content/Intent;->getExtras()Landroid/os/Bundle;

    move-result-object p1

    if-eqz p1, :cond_1

    const-string v0, "map"

    .line 2847
    invoke-virtual {p1, v0}, Landroid/os/Bundle;->get(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Ljava/util/HashMap;

    if-eqz p1, :cond_1

    const-string v0, "AppType"

    .line 2849
    invoke-interface {p1, v0}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Ljava/lang/String;

    const-string v0, "AHU.AAS.20240704"

    .line 2850
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "parsePageTypeFromIntent pageType="

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    const-string v0, "music"

    .line 2851
    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_0

    const/4 v0, 0x3

    .line 2852
    iput v0, p0, Lcom/adayo/androidauto/service/AAService;->app:I

    :cond_0
    const-string v0, "phone"

    .line 2862
    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-eqz p1, :cond_1

    const/4 p1, 0x1

    .line 2863
    iput p1, p0, Lcom/adayo/androidauto/service/AAService;->app:I
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception p1

    .line 2870
    invoke-virtual {p1}, Ljava/lang/Exception;->printStackTrace()V

    :cond_1
    :goto_0
    return-void
.end method

.method private registSwcReceiver()V
    .locals 2

    .line 2940
    invoke-static {}, Lcom/adayo/androidauto/receivers/SWCReceiverFactory;->getinstance()Lcom/adayo/androidauto/receivers/SWCReceiverFactory;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/receivers/SWCReceiverFactory;->getSwcReceiver()Landroid/content/BroadcastReceiver;

    move-result-object v0

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mSwcReceiver:Landroid/content/BroadcastReceiver;

    .line 2941
    invoke-static {}, Lcom/adayo/androidauto/receivers/SWCReceiverFactory;->getinstance()Lcom/adayo/androidauto/receivers/SWCReceiverFactory;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/receivers/SWCReceiverFactory;->setFilterAction()Landroid/content/IntentFilter;

    move-result-object v0

    .line 2942
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mSwcReceiver:Landroid/content/BroadcastReceiver;

    invoke-virtual {p0, v1, v0}, Lcom/adayo/androidauto/service/AAService;->registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;

    return-void
.end method

.method private registerAAswitchReceiver()V
    .locals 2

    .line 1374
    new-instance v0, Landroid/content/IntentFilter;

    invoke-direct {v0}, Landroid/content/IntentFilter;-><init>()V

    const-string v1, "com.adayo.androidauto.action.OPENANDCLOSE"

    .line 1375
    invoke-virtual {v0, v1}, Landroid/content/IntentFilter;->addAction(Ljava/lang/String;)V

    .line 1376
    new-instance v1, Lcom/adayo/androidauto/receivers/AndroidAutoSwitchReceiver;

    invoke-direct {v1}, Lcom/adayo/androidauto/receivers/AndroidAutoSwitchReceiver;-><init>()V

    iput-object v1, p0, Lcom/adayo/androidauto/service/AAService;->androidAutoSwitchReceiver:Lcom/adayo/androidauto/receivers/AndroidAutoSwitchReceiver;

    .line 1377
    invoke-virtual {p0, v1, v0}, Lcom/adayo/androidauto/service/AAService;->registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;

    return-void
.end method

.method private registerConnectReceiver()V
    .locals 3

    .line 346
    new-instance v0, Lcom/adayo/androidauto/service/AAService$AndroidAutoConnectReceiver;

    const/4 v1, 0x0

    invoke-direct {v0, p0, v1}, Lcom/adayo/androidauto/service/AAService$AndroidAutoConnectReceiver;-><init>(Lcom/adayo/androidauto/service/AAService;Lcom/adayo/androidauto/service/AAService$1;)V

    .line 347
    new-instance v1, Landroid/content/IntentFilter;

    const-string v2, "com.adayo.androidauto.action.CONNECT_DISCONNECT"

    invoke-direct {v1, v2}, Landroid/content/IntentFilter;-><init>(Ljava/lang/String;)V

    .line 348
    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    invoke-virtual {v2, v0, v1}, Landroid/content/Context;->registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;

    return-void
.end method

.method private registerConnectSecondReceiver()V
    .locals 3

    .line 352
    new-instance v0, Lcom/adayo/androidauto/service/AAService$AndroidAutoConnectSecondReceiver;

    const/4 v1, 0x0

    invoke-direct {v0, p0, v1}, Lcom/adayo/androidauto/service/AAService$AndroidAutoConnectSecondReceiver;-><init>(Lcom/adayo/androidauto/service/AAService;Lcom/adayo/androidauto/service/AAService$1;)V

    .line 353
    new-instance v1, Landroid/content/IntentFilter;

    const-string v2, "com.adayo.androidauto.action.CONNECT_NEW_DEVICE"

    invoke-direct {v1, v2}, Landroid/content/IntentFilter;-><init>(Ljava/lang/String;)V

    .line 354
    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    invoke-virtual {v2, v0, v1}, Landroid/content/Context;->registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;

    return-void
.end method

.method private registerDeleteDeviceReceiver()V
    .locals 3

    .line 358
    new-instance v0, Lcom/adayo/androidauto/service/AAService$AndroidAutoDeleteDeviceReceiver;

    const/4 v1, 0x0

    invoke-direct {v0, p0, v1}, Lcom/adayo/androidauto/service/AAService$AndroidAutoDeleteDeviceReceiver;-><init>(Lcom/adayo/androidauto/service/AAService;Lcom/adayo/androidauto/service/AAService$1;)V

    .line 359
    new-instance v1, Landroid/content/IntentFilter;

    const-string v2, "com.adayo.androidauto.action.DELETE_DEVICE"

    invoke-direct {v1, v2}, Landroid/content/IntentFilter;-><init>(Ljava/lang/String;)V

    .line 360
    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    invoke-virtual {v2, v0, v1}, Landroid/content/Context;->registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;

    return-void
.end method

.method private registerServiceCenter()V
    .locals 3

    .line 3251
    new-instance v0, Lcom/adayo/androidauto/service/AAService$AccecptBroadcast;

    invoke-direct {v0, p0}, Lcom/adayo/androidauto/service/AAService$AccecptBroadcast;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    .line 3252
    new-instance v1, Landroid/content/IntentFilter;

    invoke-direct {v1}, Landroid/content/IntentFilter;-><init>()V

    const-string v2, "com.adayo.module.servicecenter"

    .line 3253
    invoke-virtual {v1, v2}, Landroid/content/IntentFilter;->addAction(Ljava/lang/String;)V

    .line 3254
    invoke-virtual {p0, v0, v1}, Lcom/adayo/androidauto/service/AAService;->registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;

    .line 3256
    invoke-static {}, Lcom/adayo/module/servicecenterproxy/ServiceCenterManager;->getInstance()Lcom/adayo/module/servicecenterproxy/ServiceCenterManager;

    move-result-object v0

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mServiceCenterManager:Lcom/adayo/module/servicecenterproxy/ServiceCenterManager;

    return-void
.end method

.method private registerUsbAccessoryOrDeviceAttachReceiver()V
    .locals 3

    .line 1788
    new-instance v0, Landroid/content/IntentFilter;

    const-string v1, "android.hardware.usb.action.USB_ACCESSORY_DETACHED"

    invoke-direct {v0, v1}, Landroid/content/IntentFilter;-><init>(Ljava/lang/String;)V

    const-string v1, "android.hardware.usb.action.USB_DEVICE_ATTACHED"

    .line 1790
    invoke-virtual {v0, v1}, Landroid/content/IntentFilter;->addAction(Ljava/lang/String;)V

    const-string v1, "android.hardware.usb.action.USB_DEVICE_DETACHED"

    .line 1791
    invoke-virtual {v0, v1}, Landroid/content/IntentFilter;->addAction(Ljava/lang/String;)V

    const-string v1, "android.hardware.usb.action.USB_ACCESSORY_ATTACHED"

    .line 1792
    invoke-virtual {v0, v1}, Landroid/content/IntentFilter;->addAction(Ljava/lang/String;)V

    .line 1793
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mUsbAccessoryOrDeviceAttachReceiver:Landroid/content/BroadcastReceiver;

    invoke-virtual {v1, v2, v0}, Landroid/content/Context;->registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;

    return-void
.end method

.method private registerUsbPerrmissions()V
    .locals 3

    .line 1782
    new-instance v0, Landroid/content/IntentFilter;

    const-string v1, "com.google.android.projection.sink.transport.usb.ACTION_USB_DEVICE_PERMISSION"

    invoke-direct {v0, v1}, Landroid/content/IntentFilter;-><init>(Ljava/lang/String;)V

    const-string v1, "com.google.android.projection.sink.transport.usb.ACTION_USB_ACCESSORY_PERMISSION"

    .line 1783
    invoke-virtual {v0, v1}, Landroid/content/IntentFilter;->addAction(Ljava/lang/String;)V

    .line 1784
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mUsbPermissionReceiver:Landroid/content/BroadcastReceiver;

    invoke-virtual {v1, v2, v0}, Landroid/content/Context;->registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;

    return-void
.end method

.method private removeSendAutoConnectBTMsg()V
    .locals 2

    .line 3549
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mMainHandler:Landroid/os/Handler;

    if-eqz v0, :cond_0

    const/16 v1, 0x2718

    .line 3550
    invoke-virtual {v0, v1}, Landroid/os/Handler;->removeMessages(I)V

    :cond_0
    return-void
.end method

.method private runOnUiThread(Ljava/lang/Runnable;)V
    .locals 1

    .line 1992
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mHandler:Landroid/os/Handler;

    invoke-virtual {v0, p1}, Landroid/os/Handler;->post(Ljava/lang/Runnable;)Z

    return-void
.end method

.method private sendPlayKeyEvent()V
    .locals 3

    const-string v0, "AHU.AAS.20240704"

    const-string v1, "sendPlayKeyEvent"

    .line 3195
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 3196
    new-instance v0, Landroid/view/KeyEvent;

    const/4 v1, 0x0

    const/16 v2, 0x7e

    invoke-direct {v0, v1, v2}, Landroid/view/KeyEvent;-><init>(II)V

    .line 3197
    invoke-virtual {p0, v2, v0}, Lcom/adayo/androidauto/service/AAService;->handleKeyEvent(ILandroid/view/KeyEvent;)Z

    .line 3198
    new-instance v0, Landroid/view/KeyEvent;

    const/4 v1, 0x1

    invoke-direct {v0, v1, v2}, Landroid/view/KeyEvent;-><init>(II)V

    .line 3199
    invoke-virtual {p0, v2, v0}, Lcom/adayo/androidauto/service/AAService;->handleKeyEvent(ILandroid/view/KeyEvent;)Z

    return-void
.end method

.method private showWiredPlug(Landroid/hardware/usb/UsbDevice;)V
    .locals 3

    .line 2608
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    invoke-virtual {v0}, Lcom/adayo/androidauto/service/AAServiceManager;->getAAActivity()Lcom/adayo/androidauto/ui/AAActivity;

    move-result-object v0

    if-nez v0, :cond_0

    return-void

    .line 2612
    :cond_0
    new-instance v0, Lcom/adayo/androidauto/dialog/WiredPlugDialog;

    invoke-virtual {p0}, Lcom/adayo/androidauto/service/AAService;->getApplicationContext()Landroid/content/Context;

    move-result-object v1

    invoke-direct {v0, v1}, Lcom/adayo/androidauto/dialog/WiredPlugDialog;-><init>(Landroid/content/Context;)V

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->wiredPlugDialog:Lcom/adayo/androidauto/dialog/WiredPlugDialog;

    const-string v1, "Start Android Auto?"

    .line 2613
    invoke-virtual {v0, v1}, Lcom/adayo/androidauto/dialog/WiredPlugDialog;->setTitle(Ljava/lang/String;)V

    .line 2614
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->wiredPlugDialog:Lcom/adayo/androidauto/dialog/WiredPlugDialog;

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "Android Auto ["

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p1}, Landroid/hardware/usb/UsbDevice;->getProductName()Ljava/lang/String;

    move-result-object p1

    invoke-virtual {v1, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string p1, "] is connected via USB. Start now?"

    invoke-virtual {v1, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-virtual {v0, p1}, Lcom/adayo/androidauto/dialog/WiredPlugDialog;->setMessage(Ljava/lang/String;)V

    .line 2615
    iget-object p1, p0, Lcom/adayo/androidauto/service/AAService;->wiredPlugDialog:Lcom/adayo/androidauto/dialog/WiredPlugDialog;

    const/4 v0, 0x0

    invoke-virtual {p1, v0}, Lcom/adayo/androidauto/dialog/WiredPlugDialog;->setSingleButton(Z)V

    .line 2616
    iget-object p1, p0, Lcom/adayo/androidauto/service/AAService;->wiredPlugDialog:Lcom/adayo/androidauto/dialog/WiredPlugDialog;

    const-string v0, "Start"

    invoke-virtual {p1, v0}, Lcom/adayo/androidauto/dialog/WiredPlugDialog;->setStartButtonTV(Ljava/lang/String;)V

    .line 2617
    iget-object p1, p0, Lcom/adayo/androidauto/service/AAService;->wiredPlugDialog:Lcom/adayo/androidauto/dialog/WiredPlugDialog;

    new-instance v0, Lcom/adayo/androidauto/service/AAService$24;

    invoke-direct {v0, p0}, Lcom/adayo/androidauto/service/AAService$24;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    invoke-virtual {p1, v0}, Lcom/adayo/androidauto/dialog/WiredPlugDialog;->setNoOnclickListener(Lcom/adayo/androidauto/dialog/WiredPlugDialog$onNoOnclickListener;)V

    .line 2623
    iget-object p1, p0, Lcom/adayo/androidauto/service/AAService;->wiredPlugDialog:Lcom/adayo/androidauto/dialog/WiredPlugDialog;

    new-instance v0, Lcom/adayo/androidauto/service/AAService$25;

    invoke-direct {v0, p0}, Lcom/adayo/androidauto/service/AAService$25;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    invoke-virtual {p1, v0}, Lcom/adayo/androidauto/dialog/WiredPlugDialog;->setYesOnclickListener(Lcom/adayo/androidauto/dialog/WiredPlugDialog$onYesOnclickListener;)V

    .line 2631
    iget-object p1, p0, Lcom/adayo/androidauto/service/AAService;->wiredPlugDialog:Lcom/adayo/androidauto/dialog/WiredPlugDialog;

    const/4 v0, 0x1

    invoke-virtual {p1, v0}, Lcom/adayo/androidauto/dialog/WiredPlugDialog;->setCanceledOnTouchOutside(Z)V

    .line 2632
    iget-object p1, p0, Lcom/adayo/androidauto/service/AAService;->wiredPlugDialog:Lcom/adayo/androidauto/dialog/WiredPlugDialog;

    invoke-virtual {p1}, Lcom/adayo/androidauto/dialog/WiredPlugDialog;->show()V

    return-void
.end method

.method private startAAAfterTerms()V
    .locals 2

    const-string v0, "AHU.AAS.20240704"

    const-string v1, "startAAAfterTerms receiver"

    .line 2970
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2971
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mTransport:Lcom/google/android/projection/protocol/Transport;

    if-nez v1, :cond_0

    iget-boolean v1, p0, Lcom/adayo/androidauto/service/AAService;->mIsAAPConnected:Z

    if-nez v1, :cond_0

    const-string v1, "startAAAfterTerms,AA already connected!"

    .line 2981
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->w(Ljava/lang/String;Ljava/lang/String;)V

    :cond_0
    return-void
.end method

.method private startAccessoryMode(Landroid/hardware/usb/UsbAccessory;)V
    .locals 5

    const-string v0, "AHU.AAS.20240704"

    const-string v1, "Starting in accessory mode"

    .line 1829
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->i(Ljava/lang/String;Ljava/lang/String;)V

    if-nez p1, :cond_0

    const-string v1, "accessory is null"

    .line 1831
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V

    .line 1833
    :cond_0
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    const-string v2, "usb"

    .line 1834
    invoke-virtual {v1, v2}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Landroid/hardware/usb/UsbManager;

    .line 1836
    invoke-virtual {v1, p1}, Landroid/hardware/usb/UsbManager;->hasPermission(Landroid/hardware/usb/UsbAccessory;)Z

    move-result v2

    if-nez v2, :cond_1

    const-string v2, "Prompting the user for access to the accessory."

    .line 1839
    invoke-static {v0, v2}, Lcom/adayo/androidauto/utils/AALog;->i(Ljava/lang/String;Ljava/lang/String;)V

    .line 1840
    new-instance v0, Landroid/content/Intent;

    const-string v2, "com.google.android.projection.sink.transport.usb.ACTION_USB_ACCESSORY_PERMISSION"

    invoke-direct {v0, v2}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    .line 1842
    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    invoke-virtual {v2}, Landroid/content/Context;->getPackageName()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v0, v2}, Landroid/content/Intent;->setPackage(Ljava/lang/String;)Landroid/content/Intent;

    .line 1843
    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    const/4 v3, 0x0

    const/high16 v4, 0x40000000    # 2.0f

    invoke-static {v2, v3, v0, v4}, Landroid/app/PendingIntent;->getBroadcast(Landroid/content/Context;ILandroid/content/Intent;I)Landroid/app/PendingIntent;

    move-result-object v0

    .line 1845
    invoke-virtual {v1, p1, v0}, Landroid/hardware/usb/UsbManager;->requestPermission(Landroid/hardware/usb/UsbAccessory;Landroid/app/PendingIntent;)V

    return-void

    .line 1848
    :cond_1
    invoke-virtual {p0, p1}, Lcom/adayo/androidauto/service/AAService;->startConnectionEstablishing(Landroid/os/Parcelable;)V

    return-void
.end method

.method private startHostMode(Landroid/hardware/usb/UsbDevice;)Z
    .locals 5

    const-string v0, "AHU.AAS.20240704"

    const-string v1, "startHostMode,Starting in host mode"

    .line 1866
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 1867
    invoke-direct {p0, p1}, Lcom/adayo/androidauto/service/AAService;->checkUsbHostPermission(Landroid/hardware/usb/UsbDevice;)Z

    move-result v1

    const/4 v2, 0x0

    if-nez v1, :cond_0

    const-string p1, "startHostMode,checkUsbHostPermission fail,not go to processDevice!"

    .line 1868
    invoke-static {v0, p1}, Lcom/adayo/androidauto/utils/AALog;->i(Ljava/lang/String;Ljava/lang/String;)V

    const-string p1, "checkUsbHostPermission :false"

    .line 1870
    invoke-static {v0, p1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    return v2

    :cond_0
    const-string v1, "startHostMode,go to processDevice"

    .line 1873
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 1875
    :try_start_0
    invoke-virtual {p1}, Landroid/hardware/usb/UsbDevice;->getDeviceName()Ljava/lang/String;

    move-result-object v1

    .line 1876
    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    const-string v4, "startHostMode,device.getDeviceName="

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    invoke-static {v0, v3}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 1877
    invoke-virtual {p0, v1}, Lcom/adayo/androidauto/service/AAService;->getUsbPort(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    iput-object v1, p0, Lcom/adayo/androidauto/service/AAService;->connectedUsb:Ljava/lang/String;

    const-string v3, "/dev/bus/usb/001"

    .line 1878
    invoke-virtual {v3, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_1

    const-string p1, "startHostMode,Only USB1 is allowed, other is forbidden!"

    .line 1879
    invoke-static {v0, p1}, Lcom/adayo/androidauto/utils/AALog;->w(Ljava/lang/String;Ljava/lang/String;)V

    return v2

    .line 1882
    :cond_1
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "startHostMode,connectedUsb="

    invoke-virtual {v1, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v3, p0, Lcom/adayo/androidauto/service/AAService;->connectedUsb:Ljava/lang/String;

    invoke-virtual {v1, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 1883
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    .line 1884
    invoke-static {p1, v1}, Lcom/google/android/projection/sink/transport/usb/UsbHostSetup;->processDevice(Landroid/hardware/usb/UsbDevice;Landroid/content/Context;)Z

    move-result v1

    if-eqz v1, :cond_2

    const-string v1, "processDevice true"

    .line 1886
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 1888
    invoke-virtual {p0, p1}, Lcom/adayo/androidauto/service/AAService;->startConnectionEstablishing(Landroid/os/Parcelable;)V

    const-string p1, "startHostMode,end"

    .line 1890
    invoke-static {v0, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    const/4 p1, 0x1

    return p1

    :cond_2
    const-string p1, "Not attached to accessory yetfalse"

    .line 1893
    invoke-static {v0, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    return v2

    :catch_0
    move-exception p1

    .line 1897
    invoke-virtual {p1}, Ljava/lang/Exception;->printStackTrace()V

    .line 1898
    invoke-virtual {p1}, Ljava/lang/Exception;->getMessage()Ljava/lang/String;

    move-result-object p1

    invoke-static {v0, p1}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V

    return v2
.end method

.method private startUSBAA(ZLjava/lang/String;)V
    .locals 2

    .line 420
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "AndroidAutoConnectReceiver USB_Connect connect_status\uff1a  "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    const-string v1, "  connect_mac\uff1a  "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p2

    const-string v0, "AHU.AAS.20240704"

    invoke-static {v0, p2}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    const/4 p2, 0x1

    if-eqz p1, :cond_1

    .line 424
    iget-boolean p1, p0, Lcom/adayo/androidauto/service/AAService;->mIsAAPConnected:Z

    if-eqz p1, :cond_0

    const-string p1, "AndroidAutoConnectReceiver WirelessConnect"

    .line 425
    invoke-static {v0, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 426
    iput-boolean p2, p0, Lcom/adayo/androidauto/service/AAService;->isStartUSBAA:Z

    .line 427
    iget-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object p1, p1, Lcom/adayo/androidauto/AAGalIntegration;->galReceiver:Lcom/google/android/projection/protocol/GalReceiver;

    invoke-virtual {p1, p2}, Lcom/google/android/projection/protocol/GalReceiver;->sendByeByeRequest(I)V

    goto :goto_0

    .line 429
    :cond_0
    invoke-direct {p0}, Lcom/adayo/androidauto/service/AAService;->usbInterfaceControl()V

    goto :goto_0

    :cond_1
    const-string p1, "AndroidAutoConnectReceiver DisUSBConnect"

    .line 433
    invoke-static {v0, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 434
    iget-boolean p1, p0, Lcom/adayo/androidauto/service/AAService;->mIsAAPConnected:Z

    if-eqz p1, :cond_2

    const/4 p1, 0x0

    .line 435
    iput-boolean p1, p0, Lcom/adayo/androidauto/service/AAService;->isStartUSBAA:Z

    .line 436
    iput-boolean p2, p0, Lcom/adayo/androidauto/service/AAService;->usbDisConnect:Z

    .line 437
    iget-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mTransport:Lcom/google/android/projection/protocol/Transport;

    sget-object p2, Lcom/google/android/projection/protocol/Transport$StopReason;->HU_BYEBYE:Lcom/google/android/projection/protocol/Transport$StopReason;

    invoke-virtual {p1, p2}, Lcom/google/android/projection/protocol/Transport;->stopTransport(Lcom/google/android/projection/protocol/Transport$StopReason;)V

    goto :goto_0

    :cond_2
    const-string p1, "AndroidAutoConnectReceiver DisUSBConnect AAPNotConnected"

    .line 439
    invoke-static {v0, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    :goto_0
    return-void
.end method

.method private startWirelessAA(ZLjava/lang/String;)V
    .locals 2

    .line 402
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "AndroidAutoConnectReceiver startWirelessAA connect_status"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    const-string v1, "connect_mac"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    const-string v1, "AHU.AAS.20240704"

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    const/4 v0, 0x1

    if-eqz p1, :cond_1

    .line 404
    iget-boolean p1, p0, Lcom/adayo/androidauto/service/AAService;->mIsAAPConnected:Z

    if-eqz p1, :cond_0

    .line 405
    iget-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object p1, p1, Lcom/adayo/androidauto/AAGalIntegration;->galReceiver:Lcom/google/android/projection/protocol/GalReceiver;

    invoke-virtual {p1, v0}, Lcom/google/android/projection/protocol/GalReceiver;->sendByeByeRequest(I)V

    .line 406
    iput-object p2, p0, Lcom/adayo/androidauto/service/AAService;->mReconnectedMac:Ljava/lang/String;

    const-string p1, "AndroidAutoConnectReceiver onReceive mIsAAPConnected is true"

    .line 407
    invoke-static {v1, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    return-void

    .line 410
    :cond_0
    invoke-virtual {p0, v0, p2}, Lcom/adayo/androidauto/service/AAService;->switchAA(ZLjava/lang/String;)V

    goto :goto_0

    .line 412
    :cond_1
    iget-boolean p1, p0, Lcom/adayo/androidauto/service/AAService;->mIsAAPConnected:Z

    if-eqz p1, :cond_2

    .line 413
    iget-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object p1, p1, Lcom/adayo/androidauto/AAGalIntegration;->galReceiver:Lcom/google/android/projection/protocol/GalReceiver;

    invoke-virtual {p1, v0}, Lcom/google/android/projection/protocol/GalReceiver;->sendByeByeRequest(I)V

    :cond_2
    :goto_0
    return-void
.end method

.method private toApp()V
    .locals 6

    .line 2395
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "to app :"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget v1, p0, Lcom/adayo/androidauto/service/AAService;->app:I

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    const-string v1, "AHU.AAS.20240704"

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2396
    iget v0, p0, Lcom/adayo/androidauto/service/AAService;->app:I

    const/4 v2, -0x1

    if-ne v0, v2, :cond_0

    return-void

    :cond_0
    const/4 v3, 0x1

    if-eq v0, v3, :cond_2

    const/4 v4, 0x3

    if-eq v0, v4, :cond_1

    move v0, v2

    goto :goto_0

    .line 2409
    :cond_1
    sget-object v0, Lcom/google/android/projection/proto/Protos$KeyCode;->KEYCODE_MEDIA:Lcom/google/android/projection/proto/Protos$KeyCode;

    invoke-virtual {v0}, Lcom/google/android/projection/proto/Protos$KeyCode;->getNumber()I

    move-result v0

    goto :goto_0

    .line 2404
    :cond_2
    sget-object v0, Lcom/google/android/projection/proto/Protos$KeyCode;->KEYCODE_TEL:Lcom/google/android/projection/proto/Protos$KeyCode;

    invoke-virtual {v0}, Lcom/google/android/projection/proto/Protos$KeyCode;->getNumber()I

    move-result v0

    .line 2416
    :goto_0
    new-instance v4, Landroid/view/KeyEvent;

    const/4 v5, 0x0

    invoke-direct {v4, v5, v0}, Landroid/view/KeyEvent;-><init>(II)V

    .line 2417
    invoke-virtual {p0, v0, v4}, Lcom/adayo/androidauto/service/AAService;->handleKeyEvent(ILandroid/view/KeyEvent;)Z

    .line 2418
    new-instance v4, Landroid/view/KeyEvent;

    invoke-direct {v4, v3, v0}, Landroid/view/KeyEvent;-><init>(II)V

    .line 2419
    invoke-virtual {p0, v0, v4}, Lcom/adayo/androidauto/service/AAService;->handleKeyEvent(ILandroid/view/KeyEvent;)Z

    .line 2420
    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    const-string v4, "TO APP KEYCODE:"

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2421
    iput v2, p0, Lcom/adayo/androidauto/service/AAService;->app:I

    return-void
.end method

.method private unRegistSwceiver()V
    .locals 1

    .line 2946
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mSwcReceiver:Landroid/content/BroadcastReceiver;

    invoke-virtual {p0, v0}, Lcom/adayo/androidauto/service/AAService;->unregisterReceiver(Landroid/content/BroadcastReceiver;)V

    return-void
.end method

.method private unRegisterAAswitchReceiver()V
    .locals 1

    .line 1381
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->androidAutoSwitchReceiver:Lcom/adayo/androidauto/receivers/AndroidAutoSwitchReceiver;

    invoke-virtual {p0, v0}, Lcom/adayo/androidauto/service/AAService;->unregisterReceiver(Landroid/content/BroadcastReceiver;)V

    return-void
.end method

.method private usbInterfaceControl()V
    .locals 3

    const-string v0, "AHU.AAS.20240704"

    const-string v1, "usbInterfaceControl in-out"

    .line 3182
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    const-string v0, "usb.carplay.state"

    const-string v1, "in"

    .line 3183
    invoke-static {v0, v1}, Landroid/os/SystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V

    const-string v1, "sys.usb.config"

    const-string v2, "adb"

    .line 3184
    invoke-static {v1, v2}, Landroid/os/SystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V

    const-string v2, "none"

    .line 3186
    invoke-static {v1, v2}, Landroid/os/SystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V

    const-string v1, "out"

    .line 3187
    invoke-static {v0, v1}, Landroid/os/SystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V

    return-void
.end method


# virtual methods
.method public checkAndroidAutoSSLKey(Ljava/lang/String;)[B
    .locals 1

    .line 3243
    new-instance v0, Lcom/adayo/androidauto/utils/Utils;

    invoke-direct {v0}, Lcom/adayo/androidauto/utils/Utils;-><init>()V

    .line 3244
    new-instance v0, Ljava/io/File;

    invoke-direct {v0, p1}, Ljava/io/File;-><init>(Ljava/lang/String;)V

    invoke-static {v0}, Lcom/adayo/androidauto/utils/Utils;->getAndroidAutoSSLKey(Ljava/io/File;)[B

    move-result-object p1

    return-object p1
.end method

.method public disablePhoneFocus()V
    .locals 2

    const-string v0, "AHU.AAS.20240704"

    const-string v1, "disablePhoneFocus"

    .line 2771
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2772
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAudioFocusManager:Lcom/google/android/projection/sink/audio/AudioFocusManager;

    if-eqz v0, :cond_0

    .line 2773
    invoke-virtual {v0}, Lcom/google/android/projection/sink/audio/AudioFocusManager;->disablePhoneFocus()V

    :cond_0
    return-void
.end method

.method public dispatchGenericMotionEvent(Landroid/view/MotionEvent;)Z
    .locals 2

    .line 2322
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mLock:Ljava/lang/Object;

    monitor-enter v0

    .line 2323
    :try_start_0
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    if-eqz v1, :cond_0

    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v1, v1, Lcom/adayo/androidauto/AAGalIntegration;->mainDisplayInputSource:Lcom/google/android/projection/protocol/InputSource;

    if-eqz v1, :cond_0

    .line 2324
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v1, v1, Lcom/adayo/androidauto/AAGalIntegration;->mainDisplayInputSource:Lcom/google/android/projection/protocol/InputSource;

    invoke-virtual {v1, p1}, Lcom/google/android/projection/protocol/InputSource;->sendTouchEvent(Landroid/view/MotionEvent;)V

    .line 2326
    :cond_0
    monitor-exit v0
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 2327
    iget-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    invoke-virtual {p1}, Lcom/adayo/androidauto/service/AAServiceManager;->getAAActivity()Lcom/adayo/androidauto/ui/AAActivity;

    move-result-object p1

    if-eqz p1, :cond_1

    const/4 p1, 0x1

    return p1

    :cond_1
    const-string p1, "AHU.AAS.20240704"

    const-string v0, "dispatchGenericMotionEvent: activity NOT running"

    .line 2330
    invoke-static {p1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    const/4 p1, 0x0

    return p1

    :catchall_0
    move-exception p1

    .line 2326
    :try_start_1
    monitor-exit v0
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    throw p1
.end method

.method public enablePhoneFocus()V
    .locals 2

    const-string v0, "AHU.AAS.20240704"

    const-string v1, "enablePhoneFocus"

    .line 2741
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2742
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAudioFocusManager:Lcom/google/android/projection/sink/audio/AudioFocusManager;

    if-eqz v0, :cond_0

    .line 2743
    invoke-virtual {v0}, Lcom/google/android/projection/sink/audio/AudioFocusManager;->enablePhoneFocus()V

    :cond_0
    return-void
.end method

.method public enablePhoneFocus(Z)V
    .locals 2

    const-string v0, "AHU.AAS.20240704"

    const-string v1, "enablePhoneFocus"

    .line 2759
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2762
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAudioFocusManager:Lcom/google/android/projection/sink/audio/AudioFocusManager;

    if-eqz v0, :cond_0

    .line 2763
    invoke-virtual {v0, p1}, Lcom/google/android/projection/sink/audio/AudioFocusManager;->enablePhoneFocus(Z)V

    :cond_0
    return-void
.end method

.method public exitBackCamera()V
    .locals 5

    const-string v0, "AHU.AAS.20240704"

    const-string v1, "--exitBackCamera "

    .line 3159
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 3160
    iget-boolean v1, p0, Lcom/adayo/androidauto/service/AAService;->mIsAAPConnected:Z

    if-eqz v1, :cond_1

    .line 3161
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v1, v1, Lcom/adayo/androidauto/AAGalIntegration;->mainDisplayVideoSink:Lcom/google/android/projection/protocol/VideoSink;

    sget v2, Lcom/google/android/projection/protocol/VideoSink;->VIDEO_FOCUS_PROJECTED:I

    const/4 v3, -0x1

    const/4 v4, 0x1

    invoke-virtual {v1, v2, v3, v4}, Lcom/google/android/projection/protocol/VideoSink;->setVideoFocus(IIZ)V

    .line 3167
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mSurfaceView:Landroid/view/SurfaceView;

    if-eqz v1, :cond_0

    invoke-virtual {v1}, Landroid/view/SurfaceView;->getAlpha()F

    move-result v1

    const/high16 v2, 0x3f800000    # 1.0f

    cmpg-float v1, v1, v2

    if-gez v1, :cond_0

    .line 3168
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mMainHandler:Landroid/os/Handler;

    new-instance v2, Lcom/adayo/androidauto/service/-$$Lambda$AAService$Y0ZUtH5IfxxtjubBJTQykBTXMMY;

    invoke-direct {v2, p0}, Lcom/adayo/androidauto/service/-$$Lambda$AAService$Y0ZUtH5IfxxtjubBJTQykBTXMMY;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    const-wide/16 v3, 0x1f6

    invoke-virtual {v1, v2, v3, v4}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z

    .line 3173
    :cond_0
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "--exitBackCamera 2 getAlpha: "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mSurfaceView:Landroid/view/SurfaceView;

    invoke-virtual {v2}, Landroid/view/SurfaceView;->getAlpha()F

    move-result v2

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    :cond_1
    return-void
.end method

.method public getAdayoBTManager()Lcom/adayo/androidauto/bt/AdayoBtManager;
    .locals 1

    .line 3065
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAdayoBtManager:Lcom/adayo/androidauto/bt/AdayoBtManager;

    return-object v0
.end method

.method public getAdayoSourceManager()Lcom/adayo/androidauto/AdayoSourceManager;
    .locals 1

    .line 3053
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAdayoSourceManager:Lcom/adayo/androidauto/AdayoSourceManager;

    return-object v0
.end method

.method public getAndroidAutoWirelessService()Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;
    .locals 1

    .line 3069
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAndroidAutoWirelessService:Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;

    return-object v0
.end method

.method public getAppInforManager()Lcom/adayo/androidauto/AppInfor/AppInforManager;
    .locals 1

    .line 3057
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAppInforManager:Lcom/adayo/androidauto/AppInfor/AppInforManager;

    return-object v0
.end method

.method public getConfigureDependManger()Lcom/adayo/androidauto/depend/IConfigureDependManager;
    .locals 1

    .line 3073
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mIConfigureDependManager:Lcom/adayo/androidauto/depend/IConfigureDependManager;

    return-object v0
.end method

.method public getConnectionMode()Lcom/adayo/androidauto/service/AAService$ConnectMode;
    .locals 3

    .line 3128
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mTransport:Lcom/google/android/projection/protocol/Transport;

    const-string v1, "AHU.AAS.20240704"

    if-nez v0, :cond_0

    const-string v0, "getConnectionMode not connect"

    .line 3129
    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 3130
    sget-object v0, Lcom/adayo/androidauto/service/AAService$ConnectMode;->NotConnect:Lcom/adayo/androidauto/service/AAService$ConnectMode;

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mConnectMode:Lcom/adayo/androidauto/service/AAService$ConnectMode;

    return-object v0

    .line 3132
    :cond_0
    instance-of v2, v0, Lcom/google/android/projection/transport/usb/UsbAccessoryTransport;

    if-eqz v2, :cond_1

    const-string v0, "getConnectionMode wired Android Auto UsbAccessoryTransport"

    .line 3133
    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 3134
    sget-object v0, Lcom/adayo/androidauto/service/AAService$ConnectMode;->USB:Lcom/adayo/androidauto/service/AAService$ConnectMode;

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mConnectMode:Lcom/adayo/androidauto/service/AAService$ConnectMode;

    return-object v0

    .line 3135
    :cond_1
    instance-of v2, v0, Lcom/google/android/projection/transport/usb/UsbHostTransport;

    if-eqz v2, :cond_2

    const-string v0, "getConnectionMode wired Android Auto UsbHostTransport"

    .line 3136
    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 3137
    sget-object v0, Lcom/adayo/androidauto/service/AAService$ConnectMode;->USB:Lcom/adayo/androidauto/service/AAService$ConnectMode;

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mConnectMode:Lcom/adayo/androidauto/service/AAService$ConnectMode;

    return-object v0

    .line 3138
    :cond_2
    instance-of v0, v0, Lcom/google/android/projection/transport/net/TcpTransport;

    if-eqz v0, :cond_3

    const-string v0, "getConnectionMode wireless Android Auto"

    .line 3139
    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 3140
    sget-object v0, Lcom/adayo/androidauto/service/AAService$ConnectMode;->Wireless:Lcom/adayo/androidauto/service/AAService$ConnectMode;

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mConnectMode:Lcom/adayo/androidauto/service/AAService$ConnectMode;

    return-object v0

    .line 3142
    :cond_3
    sget-object v0, Lcom/adayo/androidauto/service/AAService$ConnectMode;->NotConnect:Lcom/adayo/androidauto/service/AAService$ConnectMode;

    return-object v0
.end method

.method public getCurUSBMDSN()Ljava/lang/String;
    .locals 1

    .line 3044
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mCurUSBMDSN:Ljava/lang/String;

    return-object v0
.end method

.method public getCurrentRunningSource()I
    .locals 1

    .line 2755
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAdayoSourceManager:Lcom/adayo/androidauto/AdayoSourceManager;

    invoke-virtual {v0}, Lcom/adayo/androidauto/AdayoSourceManager;->getCurrentRunningSource()I

    move-result v0

    return v0
.end method

.method public getFirstSwitchFlag()Z
    .locals 1

    .line 2896
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAdayoSourceManager:Lcom/adayo/androidauto/AdayoSourceManager;

    invoke-virtual {v0}, Lcom/adayo/androidauto/AdayoSourceManager;->getFirstSwitchFlag()Z

    move-result v0

    return v0
.end method

.method public getMusicState()I
    .locals 1

    .line 2711
    iget v0, p0, Lcom/adayo/androidauto/service/AAService;->mMusicState:I

    return v0
.end method

.method public getNaviStart()Z
    .locals 1

    .line 2904
    iget-boolean v0, p0, Lcom/adayo/androidauto/service/AAService;->mIsStartNavi:Z

    return v0
.end method

.method public getProjectionRendererListener()Lcom/adayo/androidauto/video/AAProjectionRenderer$Listener;
    .locals 1

    .line 2681
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mProjectionRendererListener:Lcom/adayo/androidauto/video/AAProjectionRenderer$Listener;

    return-object v0
.end method

.method public getSurfaceTouchListener()Landroid/view/View$OnTouchListener;
    .locals 1

    .line 2685
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mSurfaceTouchListener:Landroid/view/View$OnTouchListener;

    return-object v0
.end method

.method public getUsbPort(Ljava/lang/String;)Ljava/lang/String;
    .locals 2

    const-string v0, "/dev/bus/usb/001"

    .line 2988
    invoke-virtual {p1, v0}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v1

    if-eqz v1, :cond_0

    return-object v0

    :cond_0
    const-string v0, "/dev/bus/usb/002"

    .line 2991
    invoke-virtual {p1, v0}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result p1

    if-eqz p1, :cond_1

    return-object v0

    :cond_1
    const-string p1, ""

    return-object p1
.end method

.method public getVehicleInfoSensor()Lcom/adayo/androidauto/sensors/VehicleInfoSensor;
    .locals 1

    .line 3061
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mVehicleInfoSensor:Lcom/adayo/androidauto/sensors/VehicleInfoSensor;

    return-object v0
.end method

.method public getVolumeType()I
    .locals 2

    .line 3026
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "getVolumeType mVolumeType = "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget v1, p0, Lcom/adayo/androidauto/service/AAService;->mVolumeType:I

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    const-string v1, "AHU.AAS.20240704"

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 3027
    iget v0, p0, Lcom/adayo/androidauto/service/AAService;->mVolumeType:I

    return v0
.end method

.method public getmAAPConnectState()Z
    .locals 1

    .line 2835
    iget-boolean v0, p0, Lcom/adayo/androidauto/service/AAService;->mIsAAPConnected:Z

    return v0
.end method

.method public handleKeyEvent(ILandroid/view/KeyEvent;)Z
    .locals 4

    .line 2268
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    const/4 v1, 0x0

    if-eqz v0, :cond_5

    iget-object v0, v0, Lcom/adayo/androidauto/AAGalIntegration;->mainDisplayInputSource:Lcom/google/android/projection/protocol/InputSource;

    if-nez v0, :cond_0

    goto/16 :goto_1

    .line 2273
    :cond_0
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "send Key event: "

    invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    const-string v2, "AHU.AAS.20240704"

    invoke-static {v2, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    const/4 v0, 0x4

    const/4 v3, 0x1

    if-eq p1, v0, :cond_4

    const/4 v0, 0x5

    if-eq p1, v0, :cond_4

    const/4 v0, 0x6

    if-eq p1, v0, :cond_4

    const/16 v0, 0x8

    if-eq p1, v0, :cond_3

    const/16 v0, 0x9

    if-eq p1, v0, :cond_2

    const/16 v0, 0x18

    if-eq p1, v0, :cond_1

    const/16 v0, 0x19

    if-eq p1, v0, :cond_1

    const/16 v0, 0x42

    if-eq p1, v0, :cond_5

    const/16 v0, 0x107

    if-eq p1, v0, :cond_4

    const v0, 0x10001

    if-eq p1, v0, :cond_4

    const v0, 0x10004

    if-eq p1, v0, :cond_4

    const/16 v0, 0x54

    if-eq p1, v0, :cond_4

    const/16 v0, 0x55

    if-eq p1, v0, :cond_4

    const/16 v0, 0x57

    if-eq p1, v0, :cond_4

    const/16 v0, 0x58

    if-eq p1, v0, :cond_4

    const/16 v0, 0x66

    if-eq p1, v0, :cond_3

    const/16 v0, 0x67

    if-eq p1, v0, :cond_2

    const/16 v0, 0x7e

    if-eq p1, v0, :cond_4

    const/16 v0, 0x7f

    if-eq p1, v0, :cond_4

    .line 2315
    new-instance p2, Ljava/lang/StringBuilder;

    invoke-direct {p2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v0, "Got keycode: "

    invoke-virtual {p2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p2, p1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {p2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-static {v2, p1}, Lcom/adayo/androidauto/utils/AALog;->i(Ljava/lang/String;Ljava/lang/String;)V

    goto :goto_1

    .line 2310
    :cond_1
    iget-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mSettings:Lcom/google/android/projection/sink/settings/Settings;

    invoke-virtual {p1}, Lcom/google/android/projection/sink/settings/Settings;->volumeToggleDebug()Z

    move-result p1

    if-eqz p1, :cond_5

    goto :goto_0

    .line 2302
    :cond_2
    invoke-virtual {p2}, Landroid/view/KeyEvent;->getAction()I

    move-result p1

    if-ne p1, v3, :cond_5

    .line 2303
    iget-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object p1, p1, Lcom/adayo/androidauto/AAGalIntegration;->mainDisplayInputSource:Lcom/google/android/projection/protocol/InputSource;

    sget-object p2, Lcom/google/android/projection/proto/Protos$KeyCode;->KEYCODE_ROTARY_CONTROLLER:Lcom/google/android/projection/proto/Protos$KeyCode;

    .line 2304
    invoke-virtual {p2}, Lcom/google/android/projection/proto/Protos$KeyCode;->getNumber()I

    move-result p2

    .line 2303
    invoke-virtual {p1, p2, v3}, Lcom/google/android/projection/protocol/InputSource;->sendRelativeEvent(II)V

    goto :goto_0

    .line 2294
    :cond_3
    invoke-virtual {p2}, Landroid/view/KeyEvent;->getAction()I

    move-result p1

    if-ne p1, v3, :cond_5

    .line 2295
    iget-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object p1, p1, Lcom/adayo/androidauto/AAGalIntegration;->mainDisplayInputSource:Lcom/google/android/projection/protocol/InputSource;

    sget-object p2, Lcom/google/android/projection/proto/Protos$KeyCode;->KEYCODE_ROTARY_CONTROLLER:Lcom/google/android/projection/proto/Protos$KeyCode;

    .line 2296
    invoke-virtual {p2}, Lcom/google/android/projection/proto/Protos$KeyCode;->getNumber()I

    move-result p2

    const/4 v0, -0x1

    .line 2295
    invoke-virtual {p1, p2, v0}, Lcom/google/android/projection/protocol/InputSource;->sendRelativeEvent(II)V

    goto :goto_0

    .line 2287
    :cond_4
    iget-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object p1, p1, Lcom/adayo/androidauto/AAGalIntegration;->mainDisplayInputSource:Lcom/google/android/projection/protocol/InputSource;

    invoke-virtual {p1, p2}, Lcom/google/android/projection/protocol/InputSource;->sendKeyEvent(Landroid/view/KeyEvent;)V

    :goto_0
    move v1, v3

    :cond_5
    :goto_1
    return v1
.end method

.method public handleMuteState()V
    .locals 4

    .line 2998
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mMainHandler:Landroid/os/Handler;

    if-eqz v0, :cond_0

    const/16 v1, 0x69

    .line 2999
    invoke-virtual {v0, v1}, Landroid/os/Handler;->hasMessages(I)Z

    move-result v0

    if-nez v0, :cond_0

    .line 3000
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mMainHandler:Landroid/os/Handler;

    const-wide/16 v2, 0x12c

    invoke-virtual {v0, v1, v2, v3}, Landroid/os/Handler;->sendEmptyMessageDelayed(IJ)Z

    :cond_0
    return-void
.end method

.method public inToBackCamera()V
    .locals 5

    const-string v0, "AHU.AAS.20240704"

    const-string v1, "--inToBackCamera "

    .line 3146
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 3147
    iget-boolean v1, p0, Lcom/adayo/androidauto/service/AAService;->mIsAAPConnected:Z

    if-eqz v1, :cond_1

    .line 3148
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v1, v1, Lcom/adayo/androidauto/AAGalIntegration;->mainDisplayVideoSink:Lcom/google/android/projection/protocol/VideoSink;

    sget v2, Lcom/google/android/projection/protocol/VideoSink;->VIDEO_FOCUS_NATIVE:I

    const/4 v3, -0x1

    const/4 v4, 0x1

    invoke-virtual {v1, v2, v3, v4}, Lcom/google/android/projection/protocol/VideoSink;->setVideoFocus(IIZ)V

    .line 3153
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mSurfaceView:Landroid/view/SurfaceView;

    if-eqz v1, :cond_0

    const/4 v2, 0x0

    invoke-virtual {v1, v2}, Landroid/view/SurfaceView;->setAlpha(F)V

    .line 3154
    :cond_0
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "--inToBackCamera 1 getAlpha: "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mSurfaceView:Landroid/view/SurfaceView;

    invoke-virtual {v2}, Landroid/view/SurfaceView;->getAlpha()F

    move-result v2

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    :cond_1
    return-void
.end method

.method public inputSourceOnKeyEvent(Landroid/view/KeyEvent;)V
    .locals 2

    .line 2702
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "inputSourceOnKeyEvent from MD, event "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    const-string v1, "AHU.AAS.20240704"

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2703
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mSurfaceView:Landroid/view/SurfaceView;

    if-eqz v0, :cond_0

    .line 2704
    invoke-virtual {v0, p1}, Landroid/view/SurfaceView;->dispatchKeyEvent(Landroid/view/KeyEvent;)Z

    goto :goto_0

    :cond_0
    const-string p1, "inputSourceOnKeyEvent,mSurfaceView == null"

    .line 2706
    invoke-static {v1, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    :goto_0
    return-void
.end method

.method public inputSourceOnMotionEvent(Landroid/view/MotionEvent;)V
    .locals 2

    .line 2695
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "inputSourceOnMotionEvent from MD,event "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    const-string v1, "AHU.AAS.20240704"

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2696
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mSurfaceView:Landroid/view/SurfaceView;

    if-eqz v0, :cond_0

    .line 2697
    invoke-virtual {v0, p1}, Landroid/view/SurfaceView;->dispatchTouchEvent(Landroid/view/MotionEvent;)Z

    :cond_0
    return-void
.end method

.method public isActivityShowing()Z
    .locals 1

    .line 2879
    iget-boolean v0, p0, Lcom/adayo/androidauto/service/AAService;->isActivityShowing:Z

    return v0
.end method

.method public declared-synchronized isGpsMuted()Z
    .locals 1

    monitor-enter p0

    .line 1288
    monitor-exit p0

    const/4 v0, 0x1

    return v0
.end method

.method public isInVR()Z
    .locals 1

    .line 2920
    iget-boolean v0, p0, Lcom/adayo/androidauto/service/AAService;->mVoiceState:Z

    return v0
.end method

.method public isInVoiceState()Z
    .locals 1

    .line 2875
    iget-boolean v0, p0, Lcom/adayo/androidauto/service/AAService;->mVoiceState:Z

    return v0
.end method

.method public isStartProjection()Z
    .locals 2

    .line 3048
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "isStartProjection="

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-boolean v1, p0, Lcom/adayo/androidauto/service/AAService;->isStartProjection:Z

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    const-string v1, "AHU.AAS.20240704"

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 3049
    iget-boolean v0, p0, Lcom/adayo/androidauto/service/AAService;->isStartProjection:Z

    return v0
.end method

.method public synthetic lambda$exitBackCamera$0$AAService()V
    .locals 2

    .line 3169
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mSurfaceView:Landroid/view/SurfaceView;

    if-eqz v0, :cond_0

    const/high16 v1, 0x3f800000    # 1.0f

    invoke-virtual {v0, v1}, Landroid/view/SurfaceView;->setAlpha(F)V

    .line 3170
    :cond_0
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mMainHandler:Landroid/os/Handler;

    const/16 v1, 0x2717

    invoke-virtual {v0, v1}, Landroid/os/Handler;->removeMessages(I)V

    return-void
.end method

.method public notifyResult(Ljava/lang/String;Ljava/lang/String;JLandroid/os/Bundle;)V
    .locals 7

    .line 3534
    :try_start_0
    invoke-static {}, Lcom/adayo/module/servicecenterproxy/ServiceCenterManager;->getInstance()Lcom/adayo/module/servicecenterproxy/ServiceCenterManager;

    move-result-object v0

    const-string v1, "ADAYO_SOURCE_ANDROID_AUTO"

    move-object v2, p1

    move-object v3, p2

    move-wide v4, p3

    move-object v6, p5

    invoke-virtual/range {v0 .. v6}, Lcom/adayo/module/servicecenterproxy/ServiceCenterManager;->notifyResult(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;JLandroid/os/Bundle;)I
    :try_end_0
    .catch Landroid/os/RemoteException; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception p1

    .line 3536
    invoke-virtual {p1}, Landroid/os/RemoteException;->printStackTrace()V

    .line 3537
    invoke-virtual {p1}, Landroid/os/RemoteException;->getMessage()Ljava/lang/String;

    move-result-object p1

    const-string p2, "AHU.AAS.20240704"

    invoke-static {p2, p1}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V

    :goto_0
    return-void
.end method

.method public notifytSensorStatus(I)V
    .locals 1

    .line 2689
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mSensorDispatcher:Lcom/google/android/projection/sink/sensors/SensorManager;

    if-eqz v0, :cond_0

    .line 2690
    invoke-virtual {v0, p1}, Lcom/google/android/projection/sink/sensors/SensorManager;->reportAvailable(I)V

    :cond_0
    return-void
.end method

.method public onAANaviStart()V
    .locals 2

    const-string v0, "AHU.AAS.20240704"

    const-string v1, "onAANaviStart"

    .line 2793
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2794
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAdayoSourceManager:Lcom/adayo/androidauto/AdayoSourceManager;

    if-eqz v0, :cond_0

    .line 2795
    invoke-virtual {v0}, Lcom/adayo/androidauto/AdayoSourceManager;->onAANaviStart()V

    :cond_0
    return-void
.end method

.method public onActivityCreate(Lcom/adayo/androidauto/ui/AAActivity;)V
    .locals 3

    .line 2365
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "onActivityCreate"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    const-string v1, "AHU.AAS.20240704"

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2367
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mMainHandler:Landroid/os/Handler;

    const/16 v2, 0x66

    invoke-virtual {v0, v2}, Landroid/os/Handler;->removeMessages(I)V

    .line 2370
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    if-nez v0, :cond_0

    const-string p1, "onActivityCreate:AOA NOT connected,mAAGalIntegration is null!"

    .line 2371
    invoke-static {v1, p1}, Lcom/adayo/androidauto/utils/AALog;->w(Ljava/lang/String;Ljava/lang/String;)V

    return-void

    .line 2375
    :cond_0
    iget-boolean v0, p0, Lcom/adayo/androidauto/service/AAService;->mIsAAPConnected:Z

    if-nez v0, :cond_1

    const-string p1, "onActivityCreate:AAP NOT connected"

    .line 2376
    invoke-static {v1, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    return-void

    .line 2380
    :cond_1
    invoke-virtual {p1}, Lcom/adayo/androidauto/ui/AAActivity;->getIntent()Landroid/content/Intent;

    move-result-object p1

    invoke-direct {p0, p1}, Lcom/adayo/androidauto/service/AAService;->parsePageTypeFromIntent(Landroid/content/Intent;)V

    .line 2383
    :try_start_0
    invoke-direct {p0}, Lcom/adayo/androidauto/service/AAService;->doAAPConnected()V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception p1

    .line 2386
    invoke-virtual {p1}, Ljava/lang/Exception;->printStackTrace()V

    .line 2390
    :goto_0
    iget-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mInstructHandler:Landroid/os/Handler;

    const/16 v0, 0x3e9

    invoke-virtual {p1, v0}, Landroid/os/Handler;->removeMessages(I)V

    .line 2391
    iget-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mInstructHandler:Landroid/os/Handler;

    const-wide/16 v1, 0xbb8

    invoke-virtual {p1, v0, v1, v2}, Landroid/os/Handler;->sendEmptyMessageDelayed(IJ)Z

    return-void
.end method

.method public onActivityNewIntent(Landroid/content/Intent;)V
    .locals 1

    .line 2839
    invoke-direct {p0, p1}, Lcom/adayo/androidauto/service/AAService;->parsePageTypeFromIntent(Landroid/content/Intent;)V

    .line 2840
    new-instance p1, Ljava/lang/StringBuilder;

    invoke-direct {p1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v0, "onActivityNewIntent app:"

    invoke-virtual {p1, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget v0, p0, Lcom/adayo/androidauto/service/AAService;->app:I

    invoke-virtual {p1, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {p1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    const-string v0, "AHU.AAS.20240704"

    invoke-static {v0, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    return-void
.end method

.method public onActivityPause()V
    .locals 5

    .line 2461
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "onActivityPause mAAServiceManager.getAAActivity() = "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    invoke-virtual {v1}, Lcom/adayo/androidauto/service/AAServiceManager;->getAAActivity()Lcom/adayo/androidauto/ui/AAActivity;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    const-string v1, "AHU.AAS.20240704"

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2462
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "onActivityPause mProjectionRenderer = "

    invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mProjectionRenderer:Lcom/adayo/androidauto/video/AAProjectionRenderer;

    invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2464
    :try_start_0
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mProjectionRenderer:Lcom/adayo/androidauto/video/AAProjectionRenderer;

    if-nez v0, :cond_0

    return-void

    .line 2467
    :cond_0
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mProjectionRenderer:Lcom/adayo/androidauto/video/AAProjectionRenderer;

    invoke-virtual {v0}, Lcom/adayo/androidauto/video/AAProjectionRenderer;->isStarted()Z

    move-result v0

    .line 2468
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "onActivityPause mProjectionRenderer.isStarted = "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-static {v1, v2}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2470
    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mProjectionRenderer:Lcom/adayo/androidauto/video/AAProjectionRenderer;

    if-eqz v2, :cond_1

    if-eqz v0, :cond_1

    .line 2471
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v0, v0, Lcom/adayo/androidauto/AAGalIntegration;->mainDisplayVideoSink:Lcom/google/android/projection/protocol/VideoSink;

    sget v2, Lcom/google/android/projection/protocol/VideoSink;->VIDEO_FOCUS_NATIVE:I

    const/4 v3, -0x1

    const/4 v4, 0x1

    invoke-virtual {v0, v2, v3, v4}, Lcom/google/android/projection/protocol/VideoSink;->setVideoFocus(IIZ)V

    .line 2474
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mProjectionRenderer:Lcom/adayo/androidauto/video/AAProjectionRenderer;

    invoke-virtual {v0}, Lcom/adayo/androidauto/video/AAProjectionRenderer;->clear()V

    const-string v0, "onActivityPause,set VIDEO_FOCUS_NATIVE"

    .line 2475
    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    :cond_1
    const/4 v0, 0x0

    .line 2477
    iput-boolean v0, p0, Lcom/adayo/androidauto/service/AAService;->isActivityShowing:Z

    .line 2478
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "onActivityPause isActivityShowing="

    invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-boolean v2, p0, Lcom/adayo/androidauto/service/AAService;->isActivityShowing:Z

    invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception v0

    .line 2480
    invoke-virtual {v0}, Ljava/lang/Exception;->printStackTrace()V

    .line 2481
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "onActivityPause error: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/Exception;->getCause()Ljava/lang/Throwable;

    move-result-object v0

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V

    :goto_0
    const-string v0, "MSG_IS_RESTART_APP:Remove_killProcess"

    .line 2485
    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2486
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mMainHandler:Landroid/os/Handler;

    const/16 v1, 0x2716

    invoke-virtual {v0, v1}, Landroid/os/Handler;->removeMessages(I)V

    return-void
.end method

.method public onActivityResume()V
    .locals 6

    .line 2425
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "onActivityResume mAAServiceManager.getAAActivity()="

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    invoke-virtual {v1}, Lcom/adayo/androidauto/service/AAServiceManager;->getAAActivity()Lcom/adayo/androidauto/ui/AAActivity;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    const-string v1, "mProjectionRenderer ="

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mProjectionRenderer:Lcom/adayo/androidauto/video/AAProjectionRenderer;

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    const-string v1, "AHU.AAS.20240704"

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2426
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    invoke-virtual {v0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v0

    const-string v2, "ANDROIDAUTO_BYEBYEREASON"

    const/4 v3, 0x0

    invoke-static {v0, v2, v3}, Landroid/provider/Settings$System;->putInt(Landroid/content/ContentResolver;Ljava/lang/String;I)Z

    .line 2427
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mSurfaceView:Landroid/view/SurfaceView;

    if-nez v0, :cond_surface_inited

    invoke-direct {p0}, Lcom/adayo/androidauto/service/AAService;->doAAPConnected()V

    :cond_surface_inited
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mProjectionRenderer:Lcom/adayo/androidauto/video/AAProjectionRenderer;

    const/4 v2, -0x1

    const/4 v3, 0x1

    if-eqz v0, :cond_0

    invoke-virtual {v0}, Lcom/adayo/androidauto/video/AAProjectionRenderer;->isStarted()Z

    move-result v0

    if-eqz v0, :cond_0

    .line 2428
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v0, v0, Lcom/adayo/androidauto/AAGalIntegration;->mainDisplayVideoSink:Lcom/google/android/projection/protocol/VideoSink;

    sget v4, Lcom/google/android/projection/protocol/VideoSink;->VIDEO_FOCUS_PROJECTED:I

    invoke-virtual {v0, v4, v2, v3}, Lcom/google/android/projection/protocol/VideoSink;->setVideoFocus(IIZ)V

    const-string v0, "onActivityResume,set VIDEO_FOCUS_PROJECTED11"

    .line 2431
    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    goto :goto_0

    .line 2432
    :cond_0
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mProjectionRenderer:Lcom/adayo/androidauto/video/AAProjectionRenderer;

    if-nez v0, :cond_1

    const-string v0, "onActivityResume mProjectionRenderer is null!"

    .line 2433
    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->w(Ljava/lang/String;Ljava/lang/String;)V

    .line 2434
    new-instance v0, Lcom/adayo/androidauto/video/AAProjectionRenderer;

    invoke-direct {v0}, Lcom/adayo/androidauto/video/AAProjectionRenderer;-><init>()V

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mProjectionRenderer:Lcom/adayo/androidauto/video/AAProjectionRenderer;

    .line 2435
    invoke-direct {p0}, Lcom/adayo/androidauto/service/AAService;->doAAPConnected()V

    .line 2436
    iget-boolean v0, p0, Lcom/adayo/androidauto/service/AAService;->mIsAAPConnected:Z

    if-eqz v0, :cond_1

    .line 2437
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v0, v0, Lcom/adayo/androidauto/AAGalIntegration;->mainDisplayVideoSink:Lcom/google/android/projection/protocol/VideoSink;

    sget v4, Lcom/google/android/projection/protocol/VideoSink;->VIDEO_FOCUS_PROJECTED:I

    invoke-virtual {v0, v4, v2, v3}, Lcom/google/android/projection/protocol/VideoSink;->setVideoFocus(IIZ)V

    const-string v0, "onActivityResume,set VIDEO_FOCUS_PROJECTED22"

    .line 2440
    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2443
    :cond_1
    :goto_0
    iput-boolean v3, p0, Lcom/adayo/androidauto/service/AAService;->isActivityShowing:Z

    .line 2447
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    invoke-virtual {v0}, Lcom/adayo/androidauto/service/AAServiceManager;->getAAActivity()Lcom/adayo/androidauto/ui/AAActivity;

    move-result-object v0

    const v2, 0x7f050032

    invoke-virtual {v0, v2}, Lcom/adayo/androidauto/ui/AAActivity;->findViewById(I)Landroid/view/View;

    move-result-object v0

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mPromptView:Landroid/view/View;

    .line 2449
    :cond_2
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mPromptView:Landroid/view/View;

    invoke-virtual {v0}, Landroid/view/View;->getVisibility()I

    move-result v0

    const/16 v2, 0x8

    if-eq v0, v2, :cond_3

    const-string v0, "MSG_IS_RESTART_APP:Start_killProcess"

    .line 2450
    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2451
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mMainHandler:Landroid/os/Handler;

    nop

    .line 2455
    :cond_3
    invoke-static {}, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->getInstance()Lcom/adayo/androidauto/apiservice/ApiServiceManager;

    move-result-object v0

    invoke-virtual {v0, v3}, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->setStartAndroidAutoStatus(Z)V

    .line 2457
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "onActivityResume isActivityShowing="

    invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-boolean v2, p0, Lcom/adayo/androidauto/service/AAService;->isActivityShowing:Z

    invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    return-void
.end method

.method public onActivityonDestroy()V
    .locals 2

    const-string v0, "AHU.AAS.20240704"

    const-string v1, "onActivityonDestroy"

    .line 2490
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    const/4 v0, 0x0

    .line 2493
    :try_start_0
    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mFpsTV:Landroid/widget/TextView;

    .line 2494
    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mSurfaceView:Landroid/view/SurfaceView;

    .line 2495
    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->minstructView:Landroid/view/View;

    .line 2496
    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mPromptView:Landroid/view/View;
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception v0

    .line 2499
    invoke-virtual {v0}, Ljava/lang/Exception;->printStackTrace()V

    :goto_0
    return-void
.end method

.method public onBackPressed()V
    .locals 2

    .line 2673
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mLock:Ljava/lang/Object;

    monitor-enter v0

    .line 2674
    :try_start_0
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    if-eqz v1, :cond_0

    .line 2675
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mAAGalIntegration:Lcom/adayo/androidauto/AAGalIntegration;

    iget-object v1, v1, Lcom/adayo/androidauto/AAGalIntegration;->mainDisplayInputSource:Lcom/google/android/projection/protocol/InputSource;

    invoke-virtual {v1}, Lcom/google/android/projection/protocol/InputSource;->sendBackEvent()V

    .line 2677
    :cond_0
    monitor-exit v0

    return-void

    :catchall_0
    move-exception v1

    monitor-exit v0
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    throw v1
.end method

.method public onBind(Landroid/content/Intent;)Landroid/os/IBinder;
    .locals 2

    .line 2819
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "onBind: "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    const-string v0, "AHU.AAS.20240704"

    invoke-static {v0, p1}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

    .line 2820
    iget-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mMessengerServer:Lcom/adayo/androidauto/service/MessengerServer;

    if-eqz p1, :cond_0

    .line 2821
    invoke-virtual {p1}, Lcom/adayo/androidauto/service/MessengerServer;->getBinder()Landroid/os/IBinder;

    move-result-object p1

    return-object p1

    :cond_0
    const-string p1, "mMsgManager is null!"

    .line 2823
    invoke-static {v0, p1}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V

    const/4 p1, 0x0

    return-object p1
.end method

.method public onConnected(Lcom/google/android/projection/protocol/Transport;)V
    .locals 2

    .line 1998
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "251013 16:05 onConnected()...mIsAAPConnected="

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-boolean v1, p0, Lcom/adayo/androidauto/service/AAService;->mIsAAPConnected:Z

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    const-string v1, " mConnectMode="

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mConnectMode:Lcom/adayo/androidauto/service/AAService$ConnectMode;

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    const-string v1, "AHU.AAS.20240704"

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    const/4 v0, 0x0

    .line 1999
    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mTransportHandle:Lcom/google/android/projection/transport/TransportFactory$FactoryHandle;

    .line 2000
    iget-boolean v0, p0, Lcom/adayo/androidauto/service/AAService;->mIsAAPConnected:Z

    if-eqz v0, :cond_0

    const-string p1, "onConnected,current is onConnected,no need to handle again,so return!"

    .line 2001
    invoke-static {v1, p1}, Lcom/adayo/androidauto/utils/AALog;->w(Ljava/lang/String;Ljava/lang/String;)V

    return-void

    .line 2009
    :cond_0
    new-instance v0, Lcom/adayo/androidauto/service/AAService$22;

    invoke-direct {v0, p0, p1}, Lcom/adayo/androidauto/service/AAService$22;-><init>(Lcom/adayo/androidauto/service/AAService;Lcom/google/android/projection/protocol/Transport;)V

    invoke-direct {p0, v0}, Lcom/adayo/androidauto/service/AAService;->runOnUiThread(Ljava/lang/Runnable;)V

    return-void
.end method

.method public onCreate()V
    .locals 4

    .line 1165
    invoke-super {p0}, Landroid/app/Service;->onCreate()V

    .line 1166
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "onCreate start: this="

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    const-string v1, "AHU.AAS.20240704"

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->w(Ljava/lang/String;Ljava/lang/String;)V

    .line 1167
    iput-object p0, p0, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    .line 1168
    invoke-static {}, Lcom/adayo/androidauto/service/AndroidAutoContainer;->getInstance()Lcom/adayo/androidauto/service/AndroidAutoContainer;

    move-result-object v0

    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    invoke-virtual {v0, v2, p0}, Lcom/adayo/androidauto/service/AndroidAutoContainer;->init(Landroid/content/Context;Lcom/adayo/androidauto/service/AAService;)V

    .line 1169
    const-class v0, Landroid/hardware/usb/UsbManager;

    invoke-virtual {p0, v0}, Lcom/adayo/androidauto/service/AAService;->getSystemService(Ljava/lang/Class;)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/hardware/usb/UsbManager;

    sput-object v0, Lcom/adayo/androidauto/service/AAService;->usbManager:Landroid/hardware/usb/UsbManager;

    .line 1170
    new-instance v0, Lcom/adayo/androidauto/service/MessengerServer;

    invoke-direct {v0}, Lcom/adayo/androidauto/service/MessengerServer;-><init>()V

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mMessengerServer:Lcom/adayo/androidauto/service/MessengerServer;

    .line 1172
    invoke-static {}, Lcom/adayo/androidauto/service/AAServiceManager;->getInstance()Lcom/adayo/androidauto/service/AAServiceManager;

    move-result-object v0

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    .line 1173
    invoke-virtual {v0, p0}, Lcom/adayo/androidauto/service/AAServiceManager;->init(Lcom/adayo/androidauto/service/AAService;)V

    .line 1174
    new-instance v0, Lcom/adayo/androidauto/depend/impl/haoke/configure/ConfigureDependManagerImpl;

    invoke-direct {v0}, Lcom/adayo/androidauto/depend/impl/haoke/configure/ConfigureDependManagerImpl;-><init>()V

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mIConfigureDependManager:Lcom/adayo/androidauto/depend/IConfigureDependManager;

    .line 1175
    invoke-static {}, Lcom/adayo/androidauto/AdayoSourceManager;->getInstance()Lcom/adayo/androidauto/AdayoSourceManager;

    move-result-object v0

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAdayoSourceManager:Lcom/adayo/androidauto/AdayoSourceManager;

    .line 1176
    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    invoke-virtual {v0, v2, p0}, Lcom/adayo/androidauto/AdayoSourceManager;->init(Landroid/content/Context;Lcom/adayo/androidauto/service/AAService;)V

    .line 1177
    invoke-static {}, Lcom/adayo/androidauto/bt/AdayoBtManager;->getInstance()Lcom/adayo/androidauto/bt/AdayoBtManager;

    move-result-object v0

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAdayoBtManager:Lcom/adayo/androidauto/bt/AdayoBtManager;

    .line 1178
    invoke-virtual {v0, p0}, Lcom/adayo/androidauto/bt/AdayoBtManager;->init(Lcom/adayo/androidauto/service/AAService;)V

    .line 1180
    invoke-static {}, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->getInstance()Lcom/adayo/androidauto/sensors/VehicleInfoSensor;

    move-result-object v0

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mVehicleInfoSensor:Lcom/adayo/androidauto/sensors/VehicleInfoSensor;

    .line 1181
    invoke-static {}, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->getInstance()Lcom/adayo/androidauto/sensors/VehicleInfoSensor;

    move-result-object v0

    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    invoke-virtual {v0, v2}, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->init(Landroid/content/Context;)V

    .line 1183
    invoke-static {}, Lcom/adayo/androidauto/AppInfor/AppInforManager;->getInstance()Lcom/adayo/androidauto/AppInfor/AppInforManager;

    move-result-object v0

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAppInforManager:Lcom/adayo/androidauto/AppInfor/AppInforManager;

    .line 1185
    invoke-direct {p0}, Lcom/adayo/androidauto/service/AAService;->registerConnectReceiver()V

    .line 1186
    invoke-direct {p0}, Lcom/adayo/androidauto/service/AAService;->registerConnectSecondReceiver()V

    .line 1187
    invoke-direct {p0}, Lcom/adayo/androidauto/service/AAService;->registerDeleteDeviceReceiver()V

    .line 1188
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->carAdapter:Lcom/adayo/androidauto/CarAdapter;

    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    iget-object v3, p0, Lcom/adayo/androidauto/service/AAService;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    invoke-virtual {v0, v2, v3}, Lcom/adayo/androidauto/CarAdapter;->init(Landroid/content/Context;Lcom/adayo/androidauto/service/AAServiceManager;)V

    .line 1189
    invoke-direct {p0}, Lcom/adayo/androidauto/service/AAService;->initConfigInfo()V

    .line 1191
    invoke-static {p0}, Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;->getInstance(Landroid/content/Context;)Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;

    move-result-object v0

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAndroidAutoWirelessService:Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;

    .line 1192
    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mIConfigureDependManager:Lcom/adayo/androidauto/depend/IConfigureDependManager;

    invoke-virtual {v0, p0, v2}, Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;->init(Lcom/adayo/androidauto/service/AAService;Lcom/adayo/androidauto/depend/IConfigureDependManager;)V

    .line 1193
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAndroidAutoWirelessService:Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;

    new-instance v2, Lcom/adayo/androidauto/depend/impl/adayo6w/wifi/WifiDependManagerImpl;

    invoke-direct {v2, p0}, Lcom/adayo/androidauto/depend/impl/adayo6w/wifi/WifiDependManagerImpl;-><init>(Landroid/content/Context;)V

    invoke-virtual {v0, v2}, Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;->setWifiDependManager(Lcom/adayo/androidauto/depend/IWifiDependManager;)V

    .line 1194
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAndroidAutoWirelessService:Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;

    new-instance v2, Lcom/adayo/androidauto/service/AAService$15;

    invoke-direct {v2, p0}, Lcom/adayo/androidauto/service/AAService$15;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    invoke-virtual {v0, v2}, Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;->setWifiStateListener(Lcom/adayo/androidauto/wireless/IWifiStateListener;)V

    .line 1219
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mIConfigureDependManager:Lcom/adayo/androidauto/depend/IConfigureDependManager;

    invoke-interface {v0}, Lcom/adayo/androidauto/depend/IConfigureDependManager;->getTCPSocketPort()I

    move-result v0

    .line 1220
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "onCreate tcpPort="

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 1222
    new-instance v0, Lcom/google/android/projection/transport/TransportFactory$TcpConnectionSocketOptions;

    invoke-direct {v0}, Lcom/google/android/projection/transport/TransportFactory$TcpConnectionSocketOptions;-><init>()V

    const/16 v2, 0x1388

    .line 1223
    iput v2, v0, Lcom/google/android/projection/transport/TransportFactory$TcpConnectionSocketOptions;->readTimeoutInMillis:I

    const/high16 v2, 0x20000

    .line 1224
    iput v2, v0, Lcom/google/android/projection/transport/TransportFactory$TcpConnectionSocketOptions;->readSocketBufferSize:I

    .line 1225
    invoke-static {}, Lcom/google/android/projection/transport/TransportFactory;->builder()Lcom/google/android/projection/transport/TransportFactory$Builder;

    move-result-object v2

    invoke-virtual {v2, v0}, Lcom/google/android/projection/transport/TransportFactory$Builder;->setTcpConnectionSocketOptions(Lcom/google/android/projection/transport/TransportFactory$TcpConnectionSocketOptions;)Lcom/google/android/projection/transport/TransportFactory$Builder;

    move-result-object v0

    new-instance v2, Lcom/adayo/androidauto/service/AAService$16;

    invoke-direct {v2, p0}, Lcom/adayo/androidauto/service/AAService$16;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    .line 1226
    invoke-virtual {v0, v2}, Lcom/google/android/projection/transport/TransportFactory$Builder;->setServerSocketProvider(Lcom/google/android/projection/transport/ServerSocketProvider;)Lcom/google/android/projection/transport/TransportFactory$Builder;

    move-result-object v0

    .line 1249
    invoke-virtual {v0, p0}, Lcom/google/android/projection/transport/TransportFactory$Builder;->setContext(Landroid/content/Context;)Lcom/google/android/projection/transport/TransportFactory$Builder;

    move-result-object v0

    invoke-virtual {v0}, Lcom/google/android/projection/transport/TransportFactory$Builder;->build()Lcom/google/android/projection/transport/TransportFactory;

    move-result-object v0

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mTransportFactory:Lcom/google/android/projection/transport/TransportFactory;

    .line 1252
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAppInforManager:Lcom/adayo/androidauto/AppInfor/AppInforManager;

    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mAndroidAutoWirelessService:Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;

    invoke-virtual {v0, v2}, Lcom/adayo/androidauto/AppInfor/AppInforManager;->addAppInforListener(Lcom/adayo/androidauto/AppInfor/IAppInforListener;)V

    .line 1253
    invoke-direct {p0}, Lcom/adayo/androidauto/service/AAService;->initReceiver()V

    const-string v0, "audio"

    .line 1254
    invoke-virtual {p0, v0}, Lcom/adayo/androidauto/service/AAService;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/media/AudioManager;

    sput-object v0, Lcom/adayo/androidauto/service/AAService;->mAudioManager:Landroid/media/AudioManager;

    .line 1256
    invoke-direct {p0}, Lcom/adayo/androidauto/service/AAService;->initCarInfo()V

    .line 1258
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAndroidAutoWirelessService:Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;

    invoke-virtual {v0}, Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;->startRfcommServer()V

    .line 1260
    invoke-virtual {p0}, Lcom/adayo/androidauto/service/AAService;->startTcpServerMode()V

    .line 1262
    invoke-static {}, Lcom/adayo/androidauto/SourceManagerProxy;->getInstance()Lcom/adayo/androidauto/SourceManagerProxy;

    move-result-object v0

    invoke-virtual {v0, p0}, Lcom/adayo/androidauto/SourceManagerProxy;->init(Lcom/adayo/androidauto/service/AAService;)V

    .line 1263
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    invoke-virtual {v0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v0

    const-string v2, "ANDROIDAUTO_BYEBYEREASON"

    const/4 v3, 0x0

    invoke-static {v0, v2, v3}, Landroid/provider/Settings$System;->putInt(Landroid/content/ContentResolver;Ljava/lang/String;I)Z

    .line 1264
    invoke-direct {p0}, Lcom/adayo/androidauto/service/AAService;->registerServiceCenter()V

    const-string v0, "onCreate end"

    .line 1265
    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    return-void
.end method

.method public onDestroy()V
    .locals 3

    const-string v0, "AHU.AAS.20240704"

    const-string v1, "onDestroy"

    .line 1386
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    const/4 v0, 0x0

    .line 1389
    :try_start_0
    invoke-direct {p0}, Lcom/adayo/androidauto/service/AAService;->unRegistSwceiver()V

    .line 1390
    invoke-virtual {p0}, Lcom/adayo/androidauto/service/AAService;->unRegistAccReceiver()V

    .line 1391
    invoke-direct {p0}, Lcom/adayo/androidauto/service/AAService;->unRegisterAAswitchReceiver()V

    .line 1393
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mUsbAccessoryOrDeviceAttachReceiver:Landroid/content/BroadcastReceiver;

    if-eqz v1, :cond_0

    .line 1394
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mUsbAccessoryOrDeviceAttachReceiver:Landroid/content/BroadcastReceiver;

    invoke-virtual {v1, v2}, Landroid/content/Context;->unregisterReceiver(Landroid/content/BroadcastReceiver;)V

    .line 1397
    :cond_0
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mUsbDisconnectedReceiver:Landroid/content/BroadcastReceiver;

    if-eqz v1, :cond_1

    .line 1398
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mContext:Landroid/content/Context;

    invoke-static {v1}, Landroid/support/v4/content/LocalBroadcastManager;->getInstance(Landroid/content/Context;)Landroid/support/v4/content/LocalBroadcastManager;

    move-result-object v1

    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mUsbDisconnectedReceiver:Landroid/content/BroadcastReceiver;

    invoke-virtual {v1, v2}, Landroid/support/v4/content/LocalBroadcastManager;->unregisterReceiver(Landroid/content/BroadcastReceiver;)V

    .line 1401
    :cond_1
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mMainHandler:Landroid/os/Handler;

    invoke-virtual {v1, v0}, Landroid/os/Handler;->removeCallbacksAndMessages(Ljava/lang/Object;)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception v1

    .line 1404
    invoke-virtual {v1}, Ljava/lang/Exception;->printStackTrace()V

    .line 1407
    :goto_0
    :try_start_1
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mTransportFactory:Lcom/google/android/projection/transport/TransportFactory;

    if-eqz v1, :cond_2

    .line 1408
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mTransportFactory:Lcom/google/android/projection/transport/TransportFactory;

    invoke-virtual {v1}, Lcom/google/android/projection/transport/TransportFactory;->shutdown()V

    .line 1409
    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mTransportFactory:Lcom/google/android/projection/transport/TransportFactory;

    .line 1412
    :cond_2
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mDebugContext:Lcom/google/android/projection/sink/debug/DebugContext;

    if-eqz v1, :cond_3

    .line 1413
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mDebugContext:Lcom/google/android/projection/sink/debug/DebugContext;

    invoke-virtual {v1}, Lcom/google/android/projection/sink/debug/DebugContext;->shutdown()V

    .line 1414
    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mDebugContext:Lcom/google/android/projection/sink/debug/DebugContext;

    .line 1416
    :cond_3
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mProjectionRenderer:Lcom/adayo/androidauto/video/AAProjectionRenderer;

    if-eqz v0, :cond_4

    .line 1417
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mProjectionRenderer:Lcom/adayo/androidauto/video/AAProjectionRenderer;

    invoke-virtual {v0}, Lcom/adayo/androidauto/video/AAProjectionRenderer;->release()V
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_1

    goto :goto_1

    :catch_1
    move-exception v0

    .line 1423
    invoke-virtual {v0}, Ljava/lang/Exception;->printStackTrace()V

    .line 1426
    :cond_4
    :goto_1
    invoke-super {p0}, Landroid/app/Service;->onDestroy()V

    return-void
.end method

.method public onDisconnected()V
    .locals 2

    const-string v0, "AHU.AAS.20240704"

    const-string v1, "onDisconnected,Transport disconnected start"

    .line 2058
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2059
    new-instance v1, Lcom/adayo/androidauto/service/AAService$23;

    invoke-direct {v1, p0}, Lcom/adayo/androidauto/service/AAService$23;-><init>(Lcom/adayo/androidauto/service/AAService;)V

    invoke-direct {p0, v1}, Lcom/adayo/androidauto/service/AAService;->runOnUiThread(Ljava/lang/Runnable;)V

    const-string v1, "Transport disconnected end"

    .line 2263
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    return-void
.end method

.method public onNativeNaviEnd()V
    .locals 2

    const-string v0, "AHU.AAS.20240704"

    const-string v1, "onNativeNaviEnd"

    .line 2786
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2787
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mNavigationFocusManager:Lcom/google/android/projection/sink/navigation/NavigationFocusManager;

    if-eqz v0, :cond_0

    const/4 v1, 0x0

    .line 2788
    invoke-virtual {v0, v1}, Lcom/google/android/projection/sink/navigation/NavigationFocusManager;->enforceNavigationFocus(Z)V

    :cond_0
    return-void
.end method

.method public onNativeNaviStart()V
    .locals 2

    const-string v0, "AHU.AAS.20240704"

    const-string v1, "onNativeNaviStart"

    .line 2779
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2780
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mNavigationFocusManager:Lcom/google/android/projection/sink/navigation/NavigationFocusManager;

    if-eqz v0, :cond_0

    const/4 v1, 0x1

    .line 2781
    invoke-virtual {v0, v1}, Lcom/google/android/projection/sink/navigation/NavigationFocusManager;->enforceNavigationFocus(Z)V

    :cond_0
    return-void
.end method

.method public onStartCommand(Landroid/content/Intent;II)I
    .locals 2

    .line 2829
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "onStartCommand: "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    const-string v1, "AHU.AAS.20240704"

    invoke-static {v1, v0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2831
    invoke-super {p0, p1, p2, p3}, Landroid/app/Service;->onStartCommand(Landroid/content/Intent;II)I

    move-result p1

    return p1
.end method

.method public onUIFinish()V
    .locals 4

    const-string v0, "AHU.AAS.20240704"

    .line 2884
    :try_start_0
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    invoke-virtual {v1}, Lcom/adayo/androidauto/service/AAServiceManager;->getAAActivity()Lcom/adayo/androidauto/ui/AAActivity;

    move-result-object v1

    if-eqz v1, :cond_0

    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    invoke-virtual {v1}, Lcom/adayo/androidauto/service/AAServiceManager;->getAAActivity()Lcom/adayo/androidauto/ui/AAActivity;

    move-result-object v1

    invoke-virtual {v1}, Lcom/adayo/androidauto/ui/AAActivity;->isFinishing()Z

    move-result v1

    if-nez v1, :cond_0

    const-string v1, "onUIFinish"

    .line 2885
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V

    .line 2886
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    invoke-virtual {v1}, Lcom/adayo/androidauto/service/AAServiceManager;->getAAActivity()Lcom/adayo/androidauto/ui/AAActivity;

    move-result-object v1

    invoke-virtual {v1}, Lcom/adayo/androidauto/ui/AAActivity;->finish()V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception v1

    .line 2889
    invoke-virtual {v1}, Ljava/lang/Exception;->printStackTrace()V

    .line 2890
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "onUIFinish error: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/Exception;->getCause()Ljava/lang/Throwable;

    move-result-object v1

    invoke-virtual {v2, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V

    :cond_0
    :goto_0
    return-void
.end method

.method public pausePhoneFocus()V
    .locals 2

    const-string v0, "AHU.AAS.20240704"

    const-string v1, "pausePhoneFocus"

    .line 2748
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2749
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAudioFocusManager:Lcom/google/android/projection/sink/audio/AudioFocusManager;

    if-eqz v0, :cond_0

    .line 2750
    invoke-virtual {v0}, Lcom/google/android/projection/sink/audio/AudioFocusManager;->pausePhoneFocus()V

    :cond_0
    return-void
.end method

.method public registAccReceiver()V
    .locals 2

    const-string v0, "AHU.BT."

    const-string v1, "registAccReceiver"

    .line 2950
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    return-void
.end method

.method public releaseAutoSource()V
    .locals 2

    .line 2724
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAdayoSourceManager:Lcom/adayo/androidauto/AdayoSourceManager;

    if-eqz v0, :cond_0

    .line 2725
    invoke-virtual {v0}, Lcom/adayo/androidauto/AdayoSourceManager;->releaseAutoSource()V

    goto :goto_0

    :cond_0
    const-string v0, "AHU.AAS.20240704"

    const-string v1, "mAdayoSourceManager is null!"

    .line 2727
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->w(Ljava/lang/String;Ljava/lang/String;)V

    :goto_0
    return-void
.end method

.method public releaseAutoUISource()V
    .locals 2

    .line 2733
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAdayoSourceManager:Lcom/adayo/androidauto/AdayoSourceManager;

    if-eqz v0, :cond_0

    .line 2734
    iget-object v1, p0, Lcom/adayo/androidauto/service/AAService;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    invoke-virtual {v1}, Lcom/adayo/androidauto/service/AAServiceManager;->getAAActivity()Lcom/adayo/androidauto/ui/AAActivity;

    move-result-object v1

    invoke-virtual {v0, v1}, Lcom/adayo/androidauto/AdayoSourceManager;->releaseAutoUISource(Landroid/app/Activity;)V

    goto :goto_0

    :cond_0
    const-string v0, "AHU.AAS.20240704"

    const-string v1, "mAdayoSourceManager is null!"

    .line 2736
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->w(Ljava/lang/String;Ljava/lang/String;)V

    :goto_0
    return-void
.end method

.method public requestAutoSource()V
    .locals 1

    .line 2715
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAdayoSourceManager:Lcom/adayo/androidauto/AdayoSourceManager;

    invoke-virtual {v0}, Lcom/adayo/androidauto/AdayoSourceManager;->requestAutoSource()V

    return-void
.end method

.method public requestAutoUISource()V
    .locals 1

    .line 2720
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAdayoSourceManager:Lcom/adayo/androidauto/AdayoSourceManager;

    invoke-virtual {v0}, Lcom/adayo/androidauto/AdayoSourceManager;->requestAutoUISource()V

    return-void
.end method

.method public sendAutoConnectBTMsg(I)V
    .locals 4

    .line 3543
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mMainHandler:Landroid/os/Handler;

    if-eqz v0, :cond_0

    const/16 v1, 0x2718

    int-to-long v2, p1

    .line 3544
    invoke-virtual {v0, v1, v2, v3}, Landroid/os/Handler;->sendEmptyMessageDelayed(IJ)Z

    :cond_0
    return-void
.end method

.method public sendMediaNextKeyEvent()V
    .locals 3

    const-string v0, "AHU.AAS.20240704"

    const-string v1, "sendMediaNextKeyEvent"

    .line 3228
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 3229
    new-instance v0, Landroid/view/KeyEvent;

    const/4 v1, 0x0

    const/16 v2, 0x57

    invoke-direct {v0, v1, v2}, Landroid/view/KeyEvent;-><init>(II)V

    .line 3230
    invoke-virtual {p0, v2, v0}, Lcom/adayo/androidauto/service/AAService;->handleKeyEvent(ILandroid/view/KeyEvent;)Z

    .line 3231
    new-instance v0, Landroid/view/KeyEvent;

    const/4 v1, 0x1

    invoke-direct {v0, v1, v2}, Landroid/view/KeyEvent;-><init>(II)V

    .line 3232
    invoke-virtual {p0, v2, v0}, Lcom/adayo/androidauto/service/AAService;->handleKeyEvent(ILandroid/view/KeyEvent;)Z

    return-void
.end method

.method public sendMediaPreviousKeyEvent()V
    .locals 3

    const-string v0, "AHU.AAS.20240704"

    const-string v1, "sendPreviousKeyEvent"

    .line 3217
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 3218
    new-instance v0, Landroid/view/KeyEvent;

    const/4 v1, 0x0

    const/16 v2, 0x58

    invoke-direct {v0, v1, v2}, Landroid/view/KeyEvent;-><init>(II)V

    .line 3219
    invoke-virtual {p0, v2, v0}, Lcom/adayo/androidauto/service/AAService;->handleKeyEvent(ILandroid/view/KeyEvent;)Z

    .line 3220
    new-instance v0, Landroid/view/KeyEvent;

    const/4 v1, 0x1

    invoke-direct {v0, v1, v2}, Landroid/view/KeyEvent;-><init>(II)V

    .line 3221
    invoke-virtual {p0, v2, v0}, Lcom/adayo/androidauto/service/AAService;->handleKeyEvent(ILandroid/view/KeyEvent;)Z

    return-void
.end method

.method public sendNAVIStateToClient(Z)Z
    .locals 1

    .line 2915
    invoke-virtual {p0, p1}, Lcom/adayo/androidauto/service/AAService;->setNaviStart(Z)V

    .line 2916
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mMessengerServer:Lcom/adayo/androidauto/service/MessengerServer;

    invoke-virtual {v0, p1}, Lcom/adayo/androidauto/service/MessengerServer;->sendNAVIStateToClient(Z)Z

    move-result p1

    return p1
.end method

.method public sendPauseKeyEvent()V
    .locals 3

    const-string v0, "AHU.AAS.20240704"

    const-string v1, "sendPauseKeyEvent"

    .line 3206
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 3207
    new-instance v0, Landroid/view/KeyEvent;

    const/4 v1, 0x0

    const/16 v2, 0x7f

    invoke-direct {v0, v1, v2}, Landroid/view/KeyEvent;-><init>(II)V

    .line 3208
    invoke-virtual {p0, v2, v0}, Lcom/adayo/androidauto/service/AAService;->handleKeyEvent(ILandroid/view/KeyEvent;)Z

    .line 3209
    new-instance v0, Landroid/view/KeyEvent;

    const/4 v1, 0x1

    invoke-direct {v0, v1, v2}, Landroid/view/KeyEvent;-><init>(II)V

    .line 3210
    invoke-virtual {p0, v2, v0}, Lcom/adayo/androidauto/service/AAService;->handleKeyEvent(ILandroid/view/KeyEvent;)Z

    return-void
.end method

.method public sendSearchKeyEvent()V
    .locals 3

    const-string v0, "AHU.AAS.20240704"

    const-string v1, "sendSearchKeyEvent"

    .line 2923
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2924
    new-instance v0, Landroid/view/KeyEvent;

    const/4 v1, 0x0

    const/16 v2, 0x54

    invoke-direct {v0, v1, v2}, Landroid/view/KeyEvent;-><init>(II)V

    .line 2925
    invoke-virtual {p0, v2, v0}, Lcom/adayo/androidauto/service/AAService;->handleKeyEvent(ILandroid/view/KeyEvent;)Z

    .line 2926
    new-instance v0, Landroid/view/KeyEvent;

    const/4 v1, 0x1

    invoke-direct {v0, v1, v2}, Landroid/view/KeyEvent;-><init>(II)V

    .line 2927
    invoke-virtual {p0, v2, v0}, Lcom/adayo/androidauto/service/AAService;->handleKeyEvent(ILandroid/view/KeyEvent;)Z

    return-void
.end method

.method public sendnaviKeyEvent()V
    .locals 3

    const-string v0, "AHU.AAS.20240704"

    const-string v1, "sendnaviKeyEvent"

    .line 2931
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 2932
    new-instance v0, Landroid/view/KeyEvent;

    const/4 v1, 0x0

    const/16 v2, 0x107

    invoke-direct {v0, v1, v2}, Landroid/view/KeyEvent;-><init>(II)V

    .line 2933
    invoke-virtual {p0, v2, v0}, Lcom/adayo/androidauto/service/AAService;->handleKeyEvent(ILandroid/view/KeyEvent;)Z

    .line 2934
    new-instance v0, Landroid/view/KeyEvent;

    const/4 v1, 0x1

    invoke-direct {v0, v1, v2}, Landroid/view/KeyEvent;-><init>(II)V

    .line 2935
    invoke-virtual {p0, v2, v0}, Lcom/adayo/androidauto/service/AAService;->handleKeyEvent(ILandroid/view/KeyEvent;)Z

    return-void
.end method

.method public setConnectMode(Lcom/adayo/androidauto/service/AAService$ConnectMode;)V
    .locals 2

    .line 3035
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "setConnectMode "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p1}, Lcom/adayo/androidauto/service/AAService$ConnectMode;->name()Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    const-string v1, "AHU.AAS.20240704"

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 3036
    iput-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mConnectMode:Lcom/adayo/androidauto/service/AAService$ConnectMode;

    return-void
.end method

.method public setFirstWitchFlag(Z)V
    .locals 1

    .line 2900
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAdayoSourceManager:Lcom/adayo/androidauto/AdayoSourceManager;

    invoke-virtual {v0, p1}, Lcom/adayo/androidauto/AdayoSourceManager;->setFirstWitchFlag(Z)V

    return-void
.end method

.method public declared-synchronized setGpsMute(Z)V
    .locals 3

    monitor-enter p0

    :try_start_0
    const-string v0, "AHU.AAS.20240704"

    .line 1270
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "setGpsMute("

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1, p1}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    const-string p1, ")"

    invoke-virtual {v1, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-static {v0, p1}, Lcom/adayo/androidauto/utils/AALog;->i(Ljava/lang/String;Ljava/lang/String;)V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 1271
    monitor-exit p0

    return-void

    :catchall_0
    move-exception p1

    monitor-exit p0

    throw p1
.end method

.method public setGpsVolume()V
    .locals 2

    const-string v0, "AHU.AAS.20240704"

    const-string v1, "setGpsVolume"

    .line 1274
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    return-void
.end method

.method public setInitConnectMode()V
    .locals 1

    .line 3040
    sget-object v0, Lcom/adayo/androidauto/service/AAService$ConnectMode;->NotConnect:Lcom/adayo/androidauto/service/AAService$ConnectMode;

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mConnectMode:Lcom/adayo/androidauto/service/AAService$ConnectMode;

    return-void
.end method

.method public setNaviStart(Z)V
    .locals 1

    .line 2908
    iget-boolean v0, p0, Lcom/adayo/androidauto/service/AAService;->mIsStartNavi:Z

    if-eq v0, p1, :cond_0

    .line 2909
    iput-boolean p1, p0, Lcom/adayo/androidauto/service/AAService;->mIsStartNavi:Z

    :cond_0
    return-void
.end method

.method public startConnectionEstablishing(Landroid/os/Parcelable;)V
    .locals 1

    .line 1963
    new-instance v0, Lcom/adayo/androidauto/service/AAService$21;

    invoke-direct {v0, p0, p1}, Lcom/adayo/androidauto/service/AAService$21;-><init>(Lcom/adayo/androidauto/service/AAService;Landroid/os/Parcelable;)V

    invoke-direct {p0, v0}, Lcom/adayo/androidauto/service/AAService;->runOnUiThread(Ljava/lang/Runnable;)V

    return-void
.end method

.method public declared-synchronized startTcpServerMode()V
    .locals 4

    monitor-enter p0

    :try_start_0
    const-string v0, "AHU.AAS.20240704"

    .line 1927
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "startTcpServerMode(open tcp server)...mConnectMode="

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mConnectMode:Lcom/adayo/androidauto/service/AAService$ConnectMode;

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->w(Ljava/lang/String;Ljava/lang/String;)V

    .line 1928
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mConnectMode:Lcom/adayo/androidauto/service/AAService$ConnectMode;

    sget-object v1, Lcom/adayo/androidauto/service/AAService$ConnectMode;->NotConnect:Lcom/adayo/androidauto/service/AAService$ConnectMode;

    if-ne v0, v1, :cond_1

    .line 1930
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mTransportHandle:Lcom/google/android/projection/transport/TransportFactory$FactoryHandle;
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    if-eqz v0, :cond_0

    :try_start_1
    const-string v0, "AHU.AAS.20240704"

    const-string v1, "startTcpServerMode tcpServer is started,so interrupt first and open!"

    .line 1932
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->w(Ljava/lang/String;Ljava/lang/String;)V

    .line 1933
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mTransportHandle:Lcom/google/android/projection/transport/TransportFactory$FactoryHandle;

    invoke-interface {v0}, Lcom/google/android/projection/transport/TransportFactory$FactoryHandle;->interrupt()V
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_0
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    goto :goto_0

    :catch_0
    move-exception v0

    :try_start_2
    const-string v1, "AHU.AAS.20240704"

    .line 1935
    invoke-virtual {v0}, Ljava/lang/Exception;->getMessage()Ljava/lang/String;

    move-result-object v2

    invoke-static {v1, v2}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V

    const-string v1, "AHU.AAS.20240704"

    .line 1936
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "startTcpServerMode interrupt error: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/Exception;->getMessage()Ljava/lang/String;

    move-result-object v0

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V
    :try_end_2
    .catchall {:try_start_2 .. :try_end_2} :catchall_0

    .line 1940
    :cond_0
    :goto_0
    :try_start_3
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mTransportFactory:Lcom/google/android/projection/transport/TransportFactory;

    const/4 v1, 0x0

    invoke-virtual {v0, v1, p0}, Lcom/google/android/projection/transport/TransportFactory;->open(Landroid/os/Parcelable;Lcom/google/android/projection/transport/TransportFactory$Callback;)Lcom/google/android/projection/transport/TransportFactory$FactoryHandle;

    move-result-object v0

    iput-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mTransportHandle:Lcom/google/android/projection/transport/TransportFactory$FactoryHandle;

    const-string v0, "AHU.AAS.20240704"

    .line 1941
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "mTransportHandle="

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mTransportHandle:Lcom/google/android/projection/transport/TransportFactory$FactoryHandle;

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V
    :try_end_3
    .catch Ljava/io/IOException; {:try_start_3 .. :try_end_3} :catch_1
    .catchall {:try_start_3 .. :try_end_3} :catchall_0

    goto :goto_1

    :catch_1
    move-exception v0

    .line 1943
    :try_start_4
    invoke-virtual {p0}, Lcom/adayo/androidauto/service/AAService;->onUIFinish()V

    const-string v1, "AHU.AAS.20240704"

    .line 1944
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "startTcpServerMode Failed to start TCP server on port "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v3, p0, Lcom/adayo/androidauto/service/AAService;->mSettings:Lcom/google/android/projection/sink/settings/Settings;

    invoke-virtual {v3}, Lcom/google/android/projection/sink/settings/Settings;->getListenTcpPort()I

    move-result v3

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-static {v1, v2, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    goto :goto_1

    :cond_1
    const-string v0, "AHU.AAS.20240704"

    const-string v1, "Current is in Wireless mode or USB mode,can not reopen!"

    .line 1947
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->w(Ljava/lang/String;Ljava/lang/String;)V

    :goto_1
    const-string v0, "AHU.AAS.20240704"

    .line 1950
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "startTcpServerMode now waiting for TCP socket connected callback  server port="

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v2, p0, Lcom/adayo/androidauto/service/AAService;->mSettings:Lcom/google/android/projection/sink/settings/Settings;

    invoke-virtual {v2}, Lcom/google/android/projection/sink/settings/Settings;->getListenTcpPort()I

    move-result v2

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V
    :try_end_4
    .catchall {:try_start_4 .. :try_end_4} :catchall_0

    .line 1951
    monitor-exit p0

    return-void

    :catchall_0
    move-exception v0

    monitor-exit p0

    throw v0
.end method

.method public switchAA(ZLjava/lang/String;)V
    .locals 3

    const-string v0, "AHU.AAS.20240704"

    if-eqz p1, :cond_3

    const-string p1, "switchAA"

    .line 3082
    invoke-static {v0, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 3083
    iget-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mTransport:Lcom/google/android/projection/protocol/Transport;

    if-eqz p1, :cond_2

    .line 3084
    instance-of v1, p1, Lcom/google/android/projection/transport/usb/UsbHostTransport;

    if-eqz v1, :cond_0

    const-string p1, "UsbHostTransport ..."

    .line 3085
    invoke-static {v0, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 3086
    iget-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mTransport:Lcom/google/android/projection/protocol/Transport;

    sget-object p2, Lcom/google/android/projection/protocol/Transport$StopReason;->USER_SWITCH:Lcom/google/android/projection/protocol/Transport$StopReason;

    invoke-virtual {p1, p2}, Lcom/google/android/projection/protocol/Transport;->stopTransport(Lcom/google/android/projection/protocol/Transport$StopReason;)V

    goto/16 :goto_0

    .line 3087
    :cond_0
    instance-of p1, p1, Lcom/google/android/projection/transport/net/TcpTransport;

    if-eqz p1, :cond_4

    .line 3088
    invoke-virtual {p0}, Lcom/adayo/androidauto/service/AAService;->onDisconnected()V

    .line 3089
    sget-object p1, Lcom/adayo/androidauto/service/AAService$ConnectMode;->NotConnect:Lcom/adayo/androidauto/service/AAService$ConnectMode;

    iput-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mConnectMode:Lcom/adayo/androidauto/service/AAService$ConnectMode;

    .line 3090
    invoke-virtual {p0}, Lcom/adayo/androidauto/service/AAService;->startTcpServerMode()V

    .line 3091
    iget-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mAppInforManager:Lcom/adayo/androidauto/AppInfor/AppInforManager;

    invoke-virtual {p1}, Lcom/adayo/androidauto/AppInfor/AppInforManager;->getAppInfo()Lcom/adayo/androidauto/AppInfor/AppInfor;

    move-result-object p1

    if-eqz p1, :cond_4

    .line 3093
    invoke-virtual {p1}, Lcom/adayo/androidauto/AppInfor/AppInfor;->getConnectedBTAddress()Ljava/lang/String;

    move-result-object p1

    .line 3094
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "switchAA phoneAdress="

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v2, ",macAdress="

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    const-string v0, ""

    .line 3095
    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-nez p1, :cond_1

    .line 3096
    iget-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mAndroidAutoWirelessService:Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;

    sget-object v0, Lcom/google/android/projection/proto/WifiDiscoveryProtos$StartRequestReason;->USER_REQUEST:Lcom/google/android/projection/proto/WifiDiscoveryProtos$StartRequestReason;

    invoke-virtual {p1, p2, v0}, Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;->startWirelessAA(Ljava/lang/String;Lcom/google/android/projection/proto/WifiDiscoveryProtos$StartRequestReason;)V

    goto :goto_0

    .line 3098
    :cond_1
    iget-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mAndroidAutoWirelessService:Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;

    sget-object v0, Lcom/google/android/projection/proto/WifiDiscoveryProtos$StartRequestReason;->USER_REQUEST:Lcom/google/android/projection/proto/WifiDiscoveryProtos$StartRequestReason;

    invoke-virtual {p1, p2, v0}, Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;->startWirelessAA(Ljava/lang/String;Lcom/google/android/projection/proto/WifiDiscoveryProtos$StartRequestReason;)V

    goto :goto_0

    :cond_2
    const-string p1, "mTransport is null"

    .line 3106
    invoke-static {v0, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 3107
    sget-object p1, Lcom/adayo/androidauto/service/AAService$ConnectMode;->NotConnect:Lcom/adayo/androidauto/service/AAService$ConnectMode;

    iput-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mConnectMode:Lcom/adayo/androidauto/service/AAService$ConnectMode;

    .line 3108
    invoke-virtual {p0}, Lcom/adayo/androidauto/service/AAService;->startTcpServerMode()V

    .line 3109
    iget-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mAppInforManager:Lcom/adayo/androidauto/AppInfor/AppInforManager;

    invoke-virtual {p1}, Lcom/adayo/androidauto/AppInfor/AppInforManager;->getAppInfo()Lcom/adayo/androidauto/AppInfor/AppInfor;

    move-result-object p1

    if-eqz p1, :cond_4

    .line 3111
    invoke-virtual {p1}, Lcom/adayo/androidauto/AppInfor/AppInfor;->getConnectedBTAddress()Ljava/lang/String;

    move-result-object p1

    .line 3112
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "switchAA macAdress="

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-static {v0, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 3113
    iget-object p1, p0, Lcom/adayo/androidauto/service/AAService;->mAndroidAutoWirelessService:Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;

    sget-object v0, Lcom/google/android/projection/proto/WifiDiscoveryProtos$StartRequestReason;->USER_REQUEST:Lcom/google/android/projection/proto/WifiDiscoveryProtos$StartRequestReason;

    invoke-virtual {p1, p2, v0}, Lcom/adayo/androidauto/wireless/AndroidAutoWirelessService;->startWirelessAA(Ljava/lang/String;Lcom/google/android/projection/proto/WifiDiscoveryProtos$StartRequestReason;)V

    goto :goto_0

    :cond_3
    const-string p1, "HU start AA"

    .line 3118
    invoke-static {v0, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    :cond_4
    :goto_0
    return-void
.end method

.method public unRegistAccReceiver()V
    .locals 1

    .line 2960
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService;->mAccReceiver:Landroid/content/BroadcastReceiver;

    invoke-virtual {p0, v0}, Lcom/adayo/androidauto/service/AAService;->unregisterReceiver(Landroid/content/BroadcastReceiver;)V

    return-void
.end method

.method public writeAndroidAutoSSLKey([BLjava/lang/String;)V
    .locals 1

    .line 3236
    new-instance v0, Lcom/adayo/androidauto/utils/Utils;

    invoke-direct {v0}, Lcom/adayo/androidauto/utils/Utils;-><init>()V

    .line 3237
    invoke-static {p1, p2}, Lcom/adayo/androidauto/utils/Utils;->writeToFile([BLjava/lang/String;)V

    .line 3238
    invoke-static {}, Lcom/adayo/androidauto/utils/Utils;->stopWriteToFile()V

    return-void
.end method
