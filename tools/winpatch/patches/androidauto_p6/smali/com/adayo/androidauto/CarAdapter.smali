.class public Lcom/adayo/androidauto/CarAdapter;
.super Ljava/lang/Object;
.source "CarAdapter.java"

# interfaces
.implements Lcom/adayo/phonelinkproxy/PhoneLinkConstants;


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/adayo/androidauto/CarAdapter$PhoneLinkConnectListener;
    }
.end annotation


# static fields
.field private static final MSG_LONG_PRESS_STATE:I = 0x2718

.field private static instance:Lcom/adayo/androidauto/CarAdapter;

.field private static mAndroidAutoSdkAdapter:Lcom/adayo/androidauto/AndroidAutoSDKAdapter;


# instance fields
.field private volatile currentTime:J

.field infos:Ljava/util/Map;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/Map<",
            "Ljava/lang/String;",
            "Ljava/lang/String;",
            ">;"
        }
    .end annotation
.end field

.field private isBind:Z

.field private isReceiveUP:Z

.field private volatile lastSearchDownTime:J

.field private longPressHandler:Landroid/os/Handler;

.field private mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

.field private mBluetoothListener:Lcom/adayo/phonelinkproxy/BluetoothStatusListener$Stub;

.field private mContext:Landroid/content/Context;

.field private mKeyEventListener:Lcom/adayo/phonelinkproxy/KeyEventListener$Stub;

.field private mPhoneLinkConnectListener:Lcom/adayo/androidauto/CarAdapter$PhoneLinkConnectListener;

.field private mPhoneLinkProxy:Lcom/adayo/phonelinkproxy/PhoneLinkProxy;

.field private mVehicleStatusListener:Lcom/adayo/phonelinkproxy/VehicleStatusListener$Stub;

.field private volatile searchUpReturn:Z

.field private timer:Ljava/util/Timer;


# direct methods
.method static constructor <clinit>()V
    .locals 0

    return-void
.end method

.method private constructor <init>()V
    .locals 3

    .line 52
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    const/4 v0, 0x0

    .line 42
    iput-boolean v0, p0, Lcom/adayo/androidauto/CarAdapter;->isBind:Z

    .line 43
    new-instance v1, Lcom/adayo/androidauto/CarAdapter$PhoneLinkConnectListener;

    const/4 v2, 0x0

    invoke-direct {v1, p0, v2}, Lcom/adayo/androidauto/CarAdapter$PhoneLinkConnectListener;-><init>(Lcom/adayo/androidauto/CarAdapter;Lcom/adayo/androidauto/CarAdapter$1;)V

    iput-object v1, p0, Lcom/adayo/androidauto/CarAdapter;->mPhoneLinkConnectListener:Lcom/adayo/androidauto/CarAdapter$PhoneLinkConnectListener;

    .line 45
    iput-object v2, p0, Lcom/adayo/androidauto/CarAdapter;->timer:Ljava/util/Timer;

    const-wide/16 v1, 0x0

    .line 46
    iput-wide v1, p0, Lcom/adayo/androidauto/CarAdapter;->lastSearchDownTime:J

    .line 47
    iput-wide v1, p0, Lcom/adayo/androidauto/CarAdapter;->currentTime:J

    .line 48
    iput-boolean v0, p0, Lcom/adayo/androidauto/CarAdapter;->searchUpReturn:Z

    .line 50
    new-instance v0, Ljava/util/HashMap;

    invoke-direct {v0}, Ljava/util/HashMap;-><init>()V

    iput-object v0, p0, Lcom/adayo/androidauto/CarAdapter;->infos:Ljava/util/Map;

    .line 108
    new-instance v0, Lcom/adayo/androidauto/CarAdapter$1;

    invoke-direct {v0, p0}, Lcom/adayo/androidauto/CarAdapter$1;-><init>(Lcom/adayo/androidauto/CarAdapter;)V

    iput-object v0, p0, Lcom/adayo/androidauto/CarAdapter;->mVehicleStatusListener:Lcom/adayo/phonelinkproxy/VehicleStatusListener$Stub;

    .line 206
    new-instance v0, Lcom/adayo/androidauto/CarAdapter$2;

    invoke-direct {v0, p0}, Lcom/adayo/androidauto/CarAdapter$2;-><init>(Lcom/adayo/androidauto/CarAdapter;)V

    iput-object v0, p0, Lcom/adayo/androidauto/CarAdapter;->mKeyEventListener:Lcom/adayo/phonelinkproxy/KeyEventListener$Stub;

    const/4 v0, 0x1

    .line 439
    iput-boolean v0, p0, Lcom/adayo/androidauto/CarAdapter;->isReceiveUP:Z

    .line 440
    new-instance v0, Lcom/adayo/androidauto/CarAdapter$3;

    invoke-direct {v0, p0}, Lcom/adayo/androidauto/CarAdapter$3;-><init>(Lcom/adayo/androidauto/CarAdapter;)V

    iput-object v0, p0, Lcom/adayo/androidauto/CarAdapter;->longPressHandler:Landroid/os/Handler;

    .line 462
    new-instance v0, Lcom/adayo/androidauto/CarAdapter$4;

    invoke-direct {v0, p0}, Lcom/adayo/androidauto/CarAdapter$4;-><init>(Lcom/adayo/androidauto/CarAdapter;)V

    iput-object v0, p0, Lcom/adayo/androidauto/CarAdapter;->mBluetoothListener:Lcom/adayo/phonelinkproxy/BluetoothStatusListener$Stub;

    .line 53
    invoke-static {}, Lcom/adayo/phonelinkproxy/PhoneLinkProxy;->getInstance()Lcom/adayo/phonelinkproxy/PhoneLinkProxy;

    move-result-object v0

    iput-object v0, p0, Lcom/adayo/androidauto/CarAdapter;->mPhoneLinkProxy:Lcom/adayo/phonelinkproxy/PhoneLinkProxy;

    return-void
.end method

.method static synthetic access$100(Lcom/adayo/androidauto/CarAdapter;)Lcom/adayo/phonelinkproxy/VehicleStatusListener$Stub;
    .locals 0

    .line 36
    iget-object p0, p0, Lcom/adayo/androidauto/CarAdapter;->mVehicleStatusListener:Lcom/adayo/phonelinkproxy/VehicleStatusListener$Stub;

    return-object p0
.end method

.method static synthetic access$1000(Lcom/adayo/androidauto/CarAdapter;)Z
    .locals 0

    .line 36
    iget-boolean p0, p0, Lcom/adayo/androidauto/CarAdapter;->isReceiveUP:Z

    return p0
.end method

.method static synthetic access$1002(Lcom/adayo/androidauto/CarAdapter;Z)Z
    .locals 0

    .line 36
    iput-boolean p1, p0, Lcom/adayo/androidauto/CarAdapter;->isReceiveUP:Z

    return p1
.end method

.method static synthetic access$1100(Lcom/adayo/androidauto/CarAdapter;)Landroid/os/Handler;
    .locals 0

    .line 36
    iget-object p0, p0, Lcom/adayo/androidauto/CarAdapter;->longPressHandler:Landroid/os/Handler;

    return-object p0
.end method

.method static synthetic access$200(Lcom/adayo/androidauto/CarAdapter;)Lcom/adayo/phonelinkproxy/PhoneLinkProxy;
    .locals 0

    .line 36
    iget-object p0, p0, Lcom/adayo/androidauto/CarAdapter;->mPhoneLinkProxy:Lcom/adayo/phonelinkproxy/PhoneLinkProxy;

    return-object p0
.end method

.method static synthetic access$300(Lcom/adayo/androidauto/CarAdapter;)Lcom/adayo/phonelinkproxy/BluetoothStatusListener$Stub;
    .locals 0

    .line 36
    iget-object p0, p0, Lcom/adayo/androidauto/CarAdapter;->mBluetoothListener:Lcom/adayo/phonelinkproxy/BluetoothStatusListener$Stub;

    return-object p0
.end method

.method static synthetic access$400(Lcom/adayo/androidauto/CarAdapter;)Lcom/adayo/phonelinkproxy/KeyEventListener$Stub;
    .locals 0

    .line 36
    iget-object p0, p0, Lcom/adayo/androidauto/CarAdapter;->mKeyEventListener:Lcom/adayo/phonelinkproxy/KeyEventListener$Stub;

    return-object p0
.end method

.method static synthetic access$500(Lcom/adayo/androidauto/CarAdapter;Z)V
    .locals 0

    .line 36
    invoke-direct {p0, p1}, Lcom/adayo/androidauto/CarAdapter;->setBind(Z)V

    return-void
.end method

.method static synthetic access$600()Lcom/adayo/androidauto/AndroidAutoSDKAdapter;
    .locals 1

    .line 36
    sget-object v0, Lcom/adayo/androidauto/CarAdapter;->mAndroidAutoSdkAdapter:Lcom/adayo/androidauto/AndroidAutoSDKAdapter;

    return-object v0
.end method

.method static synthetic access$700(Lcom/adayo/androidauto/CarAdapter;)Lcom/adayo/androidauto/service/AAServiceManager;
    .locals 0

    .line 36
    iget-object p0, p0, Lcom/adayo/androidauto/CarAdapter;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    return-object p0
.end method

.method static synthetic access$800(Lcom/adayo/androidauto/CarAdapter;)J
    .locals 2

    .line 36
    iget-wide v0, p0, Lcom/adayo/androidauto/CarAdapter;->lastSearchDownTime:J

    return-wide v0
.end method

.method static synthetic access$802(Lcom/adayo/androidauto/CarAdapter;J)J
    .locals 0

    .line 36
    iput-wide p1, p0, Lcom/adayo/androidauto/CarAdapter;->lastSearchDownTime:J

    return-wide p1
.end method

.method static synthetic access$900(Lcom/adayo/androidauto/CarAdapter;)J
    .locals 2

    .line 36
    iget-wide v0, p0, Lcom/adayo/androidauto/CarAdapter;->currentTime:J

    return-wide v0
.end method

.method static synthetic access$902(Lcom/adayo/androidauto/CarAdapter;J)J
    .locals 0

    .line 36
    iput-wide p1, p0, Lcom/adayo/androidauto/CarAdapter;->currentTime:J

    return-wide p1
.end method

.method public static declared-synchronized getInstance()Lcom/adayo/androidauto/CarAdapter;
    .locals 2

    const-class v0, Lcom/adayo/androidauto/CarAdapter;

    monitor-enter v0

    .line 56
    :try_start_0
    sget-object v1, Lcom/adayo/androidauto/CarAdapter;->instance:Lcom/adayo/androidauto/CarAdapter;

    if-nez v1, :cond_0

    .line 57
    new-instance v1, Lcom/adayo/androidauto/CarAdapter;

    invoke-direct {v1}, Lcom/adayo/androidauto/CarAdapter;-><init>()V

    sput-object v1, Lcom/adayo/androidauto/CarAdapter;->instance:Lcom/adayo/androidauto/CarAdapter;

    .line 58
    invoke-static {}, Lcom/adayo/androidauto/AndroidAutoSDKAdapter;->getInstance()Lcom/adayo/androidauto/AndroidAutoSDKAdapter;

    move-result-object v1

    sput-object v1, Lcom/adayo/androidauto/CarAdapter;->mAndroidAutoSdkAdapter:Lcom/adayo/androidauto/AndroidAutoSDKAdapter;

    .line 60
    :cond_0
    sget-object v1, Lcom/adayo/androidauto/CarAdapter;->instance:Lcom/adayo/androidauto/CarAdapter;
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    monitor-exit v0

    return-object v1

    :catchall_0
    move-exception v1

    monitor-exit v0

    throw v1
.end method

.method private declared-synchronized setBind(Z)V
    .locals 0

    monitor-enter p0

    .line 102
    :try_start_0
    iput-boolean p1, p0, Lcom/adayo/androidauto/CarAdapter;->isBind:Z
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 103
    monitor-exit p0

    return-void

    :catchall_0
    move-exception p1

    monitor-exit p0

    throw p1
.end method


# virtual methods
.method public enableBluetooth()V
    .locals 1

    .line 515
    invoke-virtual {p0}, Lcom/adayo/androidauto/CarAdapter;->isBind()Z

    move-result v0

    if-eqz v0, :cond_0

    .line 516
    iget-object v0, p0, Lcom/adayo/androidauto/CarAdapter;->mPhoneLinkProxy:Lcom/adayo/phonelinkproxy/PhoneLinkProxy;

    invoke-virtual {v0}, Lcom/adayo/phonelinkproxy/PhoneLinkProxy;->enableBluetooth()V

    :cond_0
    return-void
.end method

.method public getBluetoothMacAddress()Ljava/lang/String;
    .locals 3

    .line 502
    invoke-virtual {p0}, Lcom/adayo/androidauto/CarAdapter;->isBind()Z

    move-result v0

    const-string v1, ""

    if-eqz v0, :cond_1

    .line 503
    iget-object v0, p0, Lcom/adayo/androidauto/CarAdapter;->mPhoneLinkProxy:Lcom/adayo/phonelinkproxy/PhoneLinkProxy;

    invoke-virtual {v0}, Lcom/adayo/phonelinkproxy/PhoneLinkProxy;->getBluetoothMacAddress()Ljava/lang/String;

    move-result-object v0

    if-eqz v0, :cond_0

    .line 504
    iget-object v0, p0, Lcom/adayo/androidauto/CarAdapter;->mPhoneLinkProxy:Lcom/adayo/phonelinkproxy/PhoneLinkProxy;

    invoke-virtual {v0}, Lcom/adayo/phonelinkproxy/PhoneLinkProxy;->getBluetoothMacAddress()Ljava/lang/String;

    move-result-object v0

    move-object v1, v0

    .line 506
    :cond_0
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "HU macAddress: "

    invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v0}, Lcom/adayo/utility/Logger;->d(Ljava/lang/String;)V

    :cond_1
    return-object v1
.end method

.method public getConfigFilePath()Ljava/lang/String;
    .locals 2

    .line 489
    invoke-virtual {p0}, Lcom/adayo/androidauto/CarAdapter;->isBind()Z

    move-result v0

    if-eqz v0, :cond_0

    .line 490
    iget-object v0, p0, Lcom/adayo/androidauto/CarAdapter;->mPhoneLinkProxy:Lcom/adayo/phonelinkproxy/PhoneLinkProxy;

    sget-object v1, Lcom/adayo/phonelinkproxy/PhoneLinkConstants$SourceDef;->ANDROIDAUTO:Lcom/adayo/phonelinkproxy/PhoneLinkConstants$SourceDef;

    invoke-virtual {v1}, Lcom/adayo/phonelinkproxy/PhoneLinkConstants$SourceDef;->value()I

    move-result v1

    invoke-virtual {v0, v1}, Lcom/adayo/phonelinkproxy/PhoneLinkProxy;->getConfigFilePath(I)Ljava/lang/String;

    move-result-object v0

    return-object v0

    :cond_0
    const/4 v0, 0x0

    return-object v0
.end method

.method public init(Landroid/content/Context;Lcom/adayo/androidauto/service/AAServiceManager;)V
    .locals 1

    .line 64
    iput-object p1, p0, Lcom/adayo/androidauto/CarAdapter;->mContext:Landroid/content/Context;

    .line 65
    iput-object p2, p0, Lcom/adayo/androidauto/CarAdapter;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    .line 67
    :try_start_0
    iget-object p2, p0, Lcom/adayo/androidauto/CarAdapter;->mPhoneLinkProxy:Lcom/adayo/phonelinkproxy/PhoneLinkProxy;

    iget-object v0, p0, Lcom/adayo/androidauto/CarAdapter;->mPhoneLinkConnectListener:Lcom/adayo/androidauto/CarAdapter$PhoneLinkConnectListener;

    invoke-virtual {p2, p1, v0}, Lcom/adayo/phonelinkproxy/PhoneLinkProxy;->bindServiceAddListener(Landroid/content/Context;Lcom/adayo/phonelinkproxy/PhoneLinkProxy$ServiceConnectListener;)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception p1

    .line 69
    invoke-virtual {p1}, Ljava/lang/Exception;->printStackTrace()V

    .line 70
    invoke-virtual {p1}, Ljava/lang/Exception;->getMessage()Ljava/lang/String;

    move-result-object p1

    invoke-static {p1}, Lcom/adayo/utility/Logger;->d(Ljava/lang/String;)V

    :goto_0
    return-void
.end method

.method public isBigLight()Z
    .locals 1

    .line 533
    invoke-virtual {p0}, Lcom/adayo/androidauto/CarAdapter;->isBind()Z

    move-result v0

    if-eqz v0, :cond_0

    .line 534
    iget-object v0, p0, Lcom/adayo/androidauto/CarAdapter;->mPhoneLinkProxy:Lcom/adayo/phonelinkproxy/PhoneLinkProxy;

    invoke-virtual {v0}, Lcom/adayo/phonelinkproxy/PhoneLinkProxy;->isBigLight()Z

    move-result v0

    return v0

    :cond_0
    const/4 v0, 0x0

    return v0
.end method

.method public declared-synchronized isBind()Z
    .locals 1

    monitor-enter p0

    .line 98
    :try_start_0
    iget-boolean v0, p0, Lcom/adayo/androidauto/CarAdapter;->isBind:Z
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    monitor-exit p0

    return v0

    :catchall_0
    move-exception v0

    monitor-exit p0

    throw v0
.end method

.method public isParking()Z
    .locals 1

    const/4 v0, 0x1

    return v0
.end method

.method public isReverse()Z
    .locals 1

    .line 543
    invoke-virtual {p0}, Lcom/adayo/androidauto/CarAdapter;->isBind()Z

    const/4 v0, 0x0

    return v0
.end method

.method public notifyAAMediaStatus(Z)V
    .locals 4

    .line 564
    invoke-virtual {p0}, Lcom/adayo/androidauto/CarAdapter;->isBind()Z

    move-result v0

    if-eqz v0, :cond_1

    .line 565
    iget-object v0, p0, Lcom/adayo/androidauto/CarAdapter;->mPhoneLinkProxy:Lcom/adayo/phonelinkproxy/PhoneLinkProxy;

    if-eqz p1, :cond_0

    sget-object p1, Lcom/adayo/phonelinkproxy/PhoneLinkConstants$AppMode;->START:Lcom/adayo/phonelinkproxy/PhoneLinkConstants$AppMode;

    goto :goto_0

    :cond_0
    sget-object p1, Lcom/adayo/phonelinkproxy/PhoneLinkConstants$AppMode;->STOP:Lcom/adayo/phonelinkproxy/PhoneLinkConstants$AppMode;

    :goto_0
    invoke-virtual {p1}, Lcom/adayo/phonelinkproxy/PhoneLinkConstants$AppMode;->value()I

    move-result p1

    sget-object v1, Lcom/adayo/phonelinkproxy/PhoneLinkConstants$SourceDef;->ANDROIDAUTO:Lcom/adayo/phonelinkproxy/PhoneLinkConstants$SourceDef;

    invoke-virtual {v1}, Lcom/adayo/phonelinkproxy/PhoneLinkConstants$SourceDef;->value()I

    move-result v1

    sget-object v2, Lcom/adayo/phonelinkproxy/PhoneLinkConstants$AppType;->MEDIA:Lcom/adayo/phonelinkproxy/PhoneLinkConstants$AppType;

    invoke-virtual {v2}, Lcom/adayo/phonelinkproxy/PhoneLinkConstants$AppType;->value()I

    move-result v2

    const/4 v3, 0x0

    invoke-virtual {v0, p1, v1, v2, v3}, Lcom/adayo/phonelinkproxy/PhoneLinkProxy;->requestUISource(IIILjava/util/Map;)V

    :cond_1
    return-void
.end method

.method public notifyAndroidAutoConnectStatus(ZLjava/lang/String;)V
    .locals 1

    .line 610
    invoke-static {}, Lcom/adayo/androidauto/AndroidAutoApplication;->instance()Lcom/adayo/androidauto/AndroidAutoApplication;

    move-result-object p2

    invoke-virtual {p2}, Lcom/adayo/androidauto/AndroidAutoApplication;->getApplicationContext()Landroid/content/Context;

    move-result-object p2

    invoke-static {p2}, Lcom/adayo/proxy/CmsProxy;->getInstance(Landroid/content/Context;)Lcom/adayo/proxy/CmsProxy;

    move-result-object p2

    const/16 v0, 0x10

    .line 611
    :try_start_cms
    invoke-virtual {p2, v0, p1}, Lcom/adayo/proxy/CmsProxy;->setStatus(II)V
    :try_end_cms
    .catch Ljava/lang/Exception; {:try_start_cms .. :try_end_cms} :catch_cms
    :catch_cms

    return-void
.end method

.method public requestAudioSource(ZLcom/adayo/phonelinkproxy/PhoneLinkConstants$VolumeType;Lcom/adayo/phonelinkproxy/PhoneLinkConstants$ChangeAudioMode;)V
    .locals 7

    .line 577
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "requestAudioSource  Action:"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    const-string v1, " VolumeType: "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 579
    invoke-virtual {p2}, Lcom/adayo/phonelinkproxy/PhoneLinkConstants$VolumeType;->name()Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v1, " mode: "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 580
    invoke-virtual {p3}, Lcom/adayo/phonelinkproxy/PhoneLinkConstants$ChangeAudioMode;->name()Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    .line 577
    invoke-static {v0}, Lcom/adayo/utility/Logger;->i(Ljava/lang/String;)V

    .line 582
    invoke-virtual {p0}, Lcom/adayo/androidauto/CarAdapter;->isBind()Z

    move-result v0

    if-eqz v0, :cond_0

    .line 583
    iget-object v1, p0, Lcom/adayo/androidauto/CarAdapter;->mPhoneLinkProxy:Lcom/adayo/phonelinkproxy/PhoneLinkProxy;

    sget-object v0, Lcom/adayo/phonelinkproxy/PhoneLinkConstants$SourceDef;->ANDROIDAUTO:Lcom/adayo/phonelinkproxy/PhoneLinkConstants$SourceDef;

    invoke-virtual {v0}, Lcom/adayo/phonelinkproxy/PhoneLinkConstants$SourceDef;->value()I

    move-result v3

    invoke-virtual {p2}, Lcom/adayo/phonelinkproxy/PhoneLinkConstants$VolumeType;->value()I

    move-result v4

    invoke-virtual {p3}, Lcom/adayo/phonelinkproxy/PhoneLinkConstants$ChangeAudioMode;->value()I

    move-result v5

    const/4 v6, 0x0

    move v2, p1

    invoke-virtual/range {v1 .. v6}, Lcom/adayo/phonelinkproxy/PhoneLinkProxy;->requestAudioSource(ZIIIZ)V

    :cond_0
    return-void
.end method

.method public requestMicSource(ZLcom/adayo/phonelinkproxy/PhoneLinkConstants$VolumeType;)V
    .locals 7

    if-nez p2, :cond_0

    const-string p1, "volumeType == null"

    .line 594
    invoke-static {p1}, Lcom/adayo/utility/Logger;->e(Ljava/lang/String;)V

    return-void

    .line 597
    :cond_0
    invoke-virtual {p0}, Lcom/adayo/androidauto/CarAdapter;->isBind()Z

    move-result v0

    if-eqz v0, :cond_1

    .line 598
    iget-object v1, p0, Lcom/adayo/androidauto/CarAdapter;->mPhoneLinkProxy:Lcom/adayo/phonelinkproxy/PhoneLinkProxy;

    sget-object v0, Lcom/adayo/phonelinkproxy/PhoneLinkConstants$SourceDef;->ANDROIDAUTO:Lcom/adayo/phonelinkproxy/PhoneLinkConstants$SourceDef;

    invoke-virtual {v0}, Lcom/adayo/phonelinkproxy/PhoneLinkConstants$SourceDef;->value()I

    move-result v3

    invoke-virtual {p2}, Lcom/adayo/phonelinkproxy/PhoneLinkConstants$VolumeType;->value()I

    move-result v4

    sget-object p2, Lcom/adayo/phonelinkproxy/PhoneLinkConstants$ChangeAudioMode;->TRANSIENT:Lcom/adayo/phonelinkproxy/PhoneLinkConstants$ChangeAudioMode;

    invoke-virtual {p2}, Lcom/adayo/phonelinkproxy/PhoneLinkConstants$ChangeAudioMode;->value()I

    move-result v5

    const/4 v6, 0x0

    move v2, p1

    invoke-virtual/range {v1 .. v6}, Lcom/adayo/phonelinkproxy/PhoneLinkProxy;->requestMicSource(ZIIIZ)V

    :cond_1
    return-void
.end method

.method public requestUISource(Lcom/adayo/phonelinkproxy/PhoneLinkConstants$AppType;)V
    .locals 4

    .line 554
    invoke-virtual {p0}, Lcom/adayo/androidauto/CarAdapter;->isBind()Z

    move-result v0

    if-eqz v0, :cond_0

    .line 555
    iget-object v0, p0, Lcom/adayo/androidauto/CarAdapter;->mPhoneLinkProxy:Lcom/adayo/phonelinkproxy/PhoneLinkProxy;

    sget-object v1, Lcom/adayo/phonelinkproxy/PhoneLinkConstants$AppMode;->START:Lcom/adayo/phonelinkproxy/PhoneLinkConstants$AppMode;

    invoke-virtual {v1}, Lcom/adayo/phonelinkproxy/PhoneLinkConstants$AppMode;->value()I

    move-result v1

    sget-object v2, Lcom/adayo/phonelinkproxy/PhoneLinkConstants$SourceDef;->ANDROIDAUTO:Lcom/adayo/phonelinkproxy/PhoneLinkConstants$SourceDef;

    invoke-virtual {v2}, Lcom/adayo/phonelinkproxy/PhoneLinkConstants$SourceDef;->value()I

    move-result v2

    invoke-virtual {p1}, Lcom/adayo/phonelinkproxy/PhoneLinkConstants$AppType;->value()I

    move-result p1

    const/4 v3, 0x0

    invoke-virtual {v0, v1, v2, p1, v3}, Lcom/adayo/phonelinkproxy/PhoneLinkProxy;->requestUISource(IIILjava/util/Map;)V

    :cond_0
    return-void
.end method
