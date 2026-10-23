.class public Lcom/adayo/androidauto/apiservice/ApiServiceManager;
.super Ljava/lang/Object;
.source "ApiServiceManager.java"


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/adayo/androidauto/apiservice/ApiServiceManager$CarPlayConnectStatusReceiver;
    }
.end annotation


# static fields
.field private static final MSG_START_ANDROID_AAUTO:I = 0x2718

.field private static final TAG:Ljava/lang/String; = "AHU.AA.ApiServiceManager"

.field private static mInstance:Lcom/adayo/androidauto/apiservice/ApiServiceManager;


# instance fields
.field private aaDbDeviceList:Ljava/util/List;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/List<",
            "Lcom/adayo/androidauto/AppInfor/AADevice;",
            ">;"
        }
    .end annotation
.end field

.field private aaDeviceList:Ljava/util/List;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/List<",
            "Lcom/adayo/androidauto/AppInfor/AADevice;",
            ">;"
        }
    .end annotation
.end field

.field private apiHandler:Landroid/os/Handler;

.field private carplayConnectStatus:Z

.field private carplayShowStatus:Z

.field private gson:Lcom/google/gson/Gson;

.field private isStartAndroidAutoStatus:Z

.field private mContext:Landroid/content/Context;

.field private mIntent:Landroid/content/Intent;

.field private mRemoteCallbackList:Landroid/os/RemoteCallbackList;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Landroid/os/RemoteCallbackList<",
            "Lcom/adayo/androidauto/IAAStatusCallBack;",
            ">;"
        }
    .end annotation
.end field

.field private wirelessDeviceList:Ljava/util/List;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/List<",
            "Lcom/adayo/androidauto/AppInfor/AADevice;",
            ">;"
        }
    .end annotation
.end field


# direct methods
.method static constructor <clinit>()V
    .locals 0

    return-void
.end method

.method public constructor <init>()V
    .locals 1

    .line 40
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    const/4 v0, 0x1

    .line 106
    iput-boolean v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->isStartAndroidAutoStatus:Z

    .line 154
    new-instance v0, Lcom/adayo/androidauto/apiservice/ApiServiceManager$2;

    invoke-direct {v0, p0}, Lcom/adayo/androidauto/apiservice/ApiServiceManager$2;-><init>(Lcom/adayo/androidauto/apiservice/ApiServiceManager;)V

    iput-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->apiHandler:Landroid/os/Handler;

    const/4 v0, 0x0

    .line 762
    iput-boolean v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->carplayConnectStatus:Z

    .line 763
    iput-boolean v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->carplayShowStatus:Z

    return-void
.end method

.method static synthetic access$002(Lcom/adayo/androidauto/apiservice/ApiServiceManager;Z)Z
    .locals 0

    .line 40
    iput-boolean p1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->isStartAndroidAutoStatus:Z

    return p1
.end method

.method static synthetic access$202(Lcom/adayo/androidauto/apiservice/ApiServiceManager;Z)Z
    .locals 0

    .line 40
    iput-boolean p1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->carplayConnectStatus:Z

    return p1
.end method

.method static synthetic access$302(Lcom/adayo/androidauto/apiservice/ApiServiceManager;Z)Z
    .locals 0

    .line 40
    iput-boolean p1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->carplayShowStatus:Z

    return p1
.end method

.method private getDeviceName(Ljava/lang/String;)Ljava/lang/String;
    .locals 6

    .line 515
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDeviceList:Ljava/util/List;

    const-string v1, "AHU.AA.ApiServiceManager"

    if-eqz v0, :cond_1

    invoke-interface {v0}, Ljava/util/List;->size()I

    move-result v0

    if-lez v0, :cond_1

    .line 516
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDeviceList:Ljava/util/List;

    invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :cond_0
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v2

    if-eqz v2, :cond_1

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Lcom/adayo/androidauto/AppInfor/AADevice;

    .line 517
    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    const-string v4, "getDeviceName:"

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Lcom/adayo/androidauto/AppInfor/AADevice;->toString()Ljava/lang/String;

    move-result-object v5

    invoke-virtual {v3, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    invoke-static {v1, v3}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 518
    invoke-virtual {v2}, Lcom/adayo/androidauto/AppInfor/AADevice;->getConnectedBTAddress()Ljava/lang/String;

    move-result-object v3

    invoke-virtual {v3, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v3

    if-eqz v3, :cond_0

    .line 519
    new-instance p1, Ljava/lang/StringBuilder;

    invoke-direct {p1}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {p1, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Lcom/adayo/androidauto/AppInfor/AADevice;->getDeviceName()Ljava/lang/String;

    move-result-object v0

    invoke-virtual {p1, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-static {v1, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 520
    invoke-virtual {v2}, Lcom/adayo/androidauto/AppInfor/AADevice;->getDeviceName()Ljava/lang/String;

    move-result-object p1

    return-object p1

    :cond_1
    const-string p1, "getDeviceName:null"

    .line 524
    invoke-static {v1, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    const/4 p1, 0x0

    return-object p1
.end method

.method public static declared-synchronized getInstance()Lcom/adayo/androidauto/apiservice/ApiServiceManager;
    .locals 2

    const-class v0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;

    monitor-enter v0

    .line 52
    :try_start_0
    sget-object v1, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mInstance:Lcom/adayo/androidauto/apiservice/ApiServiceManager;

    if-nez v1, :cond_0

    .line 53
    new-instance v1, Lcom/adayo/androidauto/apiservice/ApiServiceManager;

    invoke-direct {v1}, Lcom/adayo/androidauto/apiservice/ApiServiceManager;-><init>()V

    sput-object v1, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mInstance:Lcom/adayo/androidauto/apiservice/ApiServiceManager;

    .line 55
    :cond_0
    sget-object v1, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mInstance:Lcom/adayo/androidauto/apiservice/ApiServiceManager;
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    monitor-exit v0

    return-object v1

    :catchall_0
    move-exception v1

    monitor-exit v0

    throw v1
.end method

.method private isUSBDeviceAvailable()Z
    .locals 2

    .line 533
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDbDeviceList:Ljava/util/List;

    invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :cond_0
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_1

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/adayo/androidauto/AppInfor/AADevice;

    .line 534
    invoke-virtual {v1}, Lcom/adayo/androidauto/AppInfor/AADevice;->isUsbOpenState()Z

    move-result v1

    if-eqz v1, :cond_0

    const/4 v0, 0x1

    return v0

    :cond_1
    const/4 v0, 0x0

    return v0
.end method

.method private isUSBDeviceConnect()Z
    .locals 2

    .line 546
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDbDeviceList:Ljava/util/List;

    invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :cond_0
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_1

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/adayo/androidauto/AppInfor/AADevice;

    .line 547
    invoke-virtual {v1}, Lcom/adayo/androidauto/AppInfor/AADevice;->isUsbConnectionState()Z

    move-result v1

    if-eqz v1, :cond_0

    const/4 v0, 0x1

    return v0

    :cond_1
    const/4 v0, 0x0

    return v0
.end method

.method static synthetic lambda$listDataComparison$0(Lcom/adayo/androidauto/AppInfor/AADevice;)Z
    .locals 1

    .line 456
    invoke-virtual {p0}, Lcom/adayo/androidauto/AppInfor/AADevice;->isWirelessConnecttionState()Z

    move-result p0

    const/4 v0, 0x1

    if-ne p0, v0, :cond_0

    goto :goto_0

    :cond_0
    const/4 v0, 0x0

    :goto_0
    return v0
.end method

.method static synthetic lambda$listDataComparison$1(Lcom/adayo/androidauto/AppInfor/AADevice;)Z
    .locals 1

    .line 461
    invoke-virtual {p0}, Lcom/adayo/androidauto/AppInfor/AADevice;->isUsbConnectionState()Z

    move-result p0

    const/4 v0, 0x1

    if-ne p0, v0, :cond_0

    goto :goto_0

    :cond_0
    const/4 v0, 0x0

    :goto_0
    return v0
.end method

.method static synthetic lambda$listDataComparison$2(Lcom/adayo/androidauto/AppInfor/AADevice;Lcom/adayo/androidauto/AppInfor/AADevice;)Z
    .locals 0

    .line 471
    invoke-virtual {p1}, Lcom/adayo/androidauto/AppInfor/AADevice;->getConnectedBTAddress()Ljava/lang/String;

    move-result-object p1

    invoke-virtual {p0}, Lcom/adayo/androidauto/AppInfor/AADevice;->getConnectedBTAddress()Ljava/lang/String;

    move-result-object p0

    invoke-virtual {p1, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p0

    return p0
.end method

.method private listDataComparison(Ljava/util/List;)Lcom/adayo/androidauto/AppInfor/AADevice;
    .locals 4
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/util/List<",
            "Lcom/adayo/androidauto/AppInfor/AADevice;",
            ">;)",
            "Lcom/adayo/androidauto/AppInfor/AADevice;"
        }
    .end annotation

    .line 450
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->wirelessDeviceList:Ljava/util/List;

    const/4 v1, 0x0

    if-nez v0, :cond_0

    return-object v1

    .line 455
    :cond_0
    invoke-interface {p1}, Ljava/util/List;->stream()Ljava/util/stream/Stream;

    move-result-object v0

    sget-object v2, Lcom/adayo/androidauto/apiservice/-$$Lambda$ApiServiceManager$xL5YgBkjppIgdNSaEWz9tPdhdzs;->INSTANCE:Lcom/adayo/androidauto/apiservice/-$$Lambda$ApiServiceManager$xL5YgBkjppIgdNSaEWz9tPdhdzs;

    invoke-interface {v0, v2}, Ljava/util/stream/Stream;->filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;

    move-result-object v0

    .line 457
    invoke-interface {v0}, Ljava/util/stream/Stream;->findAny()Ljava/util/Optional;

    move-result-object v0

    invoke-virtual {v0}, Ljava/util/Optional;->isPresent()Z

    move-result v0

    .line 460
    invoke-interface {p1}, Ljava/util/List;->stream()Ljava/util/stream/Stream;

    move-result-object v2

    sget-object v3, Lcom/adayo/androidauto/apiservice/-$$Lambda$ApiServiceManager$mmdw0BBgKLQ0uzra3_fmdFb7UmU;->INSTANCE:Lcom/adayo/androidauto/apiservice/-$$Lambda$ApiServiceManager$mmdw0BBgKLQ0uzra3_fmdFb7UmU;

    invoke-interface {v2, v3}, Ljava/util/stream/Stream;->filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;

    move-result-object v2

    .line 462
    invoke-interface {v2}, Ljava/util/stream/Stream;->findAny()Ljava/util/Optional;

    move-result-object v2

    invoke-virtual {v2}, Ljava/util/Optional;->isPresent()Z

    move-result v2

    if-nez v0, :cond_1

    if-eqz v2, :cond_3

    .line 466
    :cond_1
    invoke-interface {p1}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object p1

    :cond_2
    invoke-interface {p1}, Ljava/util/Iterator;->hasNext()Z

    move-result v0

    if-eqz v0, :cond_3

    invoke-interface {p1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Lcom/adayo/androidauto/AppInfor/AADevice;

    .line 468
    invoke-virtual {v0}, Lcom/adayo/androidauto/AppInfor/AADevice;->isUsbConnectionState()Z

    move-result v2

    if-nez v2, :cond_2

    invoke-virtual {v0}, Lcom/adayo/androidauto/AppInfor/AADevice;->isWirelessOpenState()Z

    move-result v2

    if-eqz v2, :cond_2

    .line 469
    iget-object v2, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->wirelessDeviceList:Ljava/util/List;

    invoke-interface {v2}, Ljava/util/List;->stream()Ljava/util/stream/Stream;

    move-result-object v2

    new-instance v3, Lcom/adayo/androidauto/apiservice/-$$Lambda$ApiServiceManager$CzoxkEYRtRGQlJF9O64lHJtAlb0;

    invoke-direct {v3, v0}, Lcom/adayo/androidauto/apiservice/-$$Lambda$ApiServiceManager$CzoxkEYRtRGQlJF9O64lHJtAlb0;-><init>(Lcom/adayo/androidauto/AppInfor/AADevice;)V

    invoke-interface {v2, v3}, Ljava/util/stream/Stream;->filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;

    move-result-object v2

    .line 472
    invoke-interface {v2}, Ljava/util/stream/Stream;->findAny()Ljava/util/Optional;

    move-result-object v2

    invoke-virtual {v2}, Ljava/util/Optional;->isPresent()Z

    move-result v2

    if-nez v2, :cond_2

    .line 475
    invoke-virtual {v0}, Lcom/adayo/androidauto/AppInfor/AADevice;->isUsbConnectionState()Z

    move-result v2

    if-nez v2, :cond_2

    return-object v0

    :cond_3
    return-object v1
.end method

.method private registerCarPlayConnectStatusReceiver()V
    .locals 3

    .line 757
    new-instance v0, Lcom/adayo/androidauto/apiservice/ApiServiceManager$CarPlayConnectStatusReceiver;

    const/4 v1, 0x0

    invoke-direct {v0, p0, v1}, Lcom/adayo/androidauto/apiservice/ApiServiceManager$CarPlayConnectStatusReceiver;-><init>(Lcom/adayo/androidauto/apiservice/ApiServiceManager;Lcom/adayo/androidauto/apiservice/ApiServiceManager$1;)V

    .line 758
    new-instance v1, Landroid/content/IntentFilter;

    const-string v2, "com.adayo.carplay.carplay_status"

    invoke-direct {v1, v2}, Landroid/content/IntentFilter;-><init>(Ljava/lang/String;)V

    .line 759
    iget-object v2, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mContext:Landroid/content/Context;

    invoke-virtual {v2, v0, v1}, Landroid/content/Context;->registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;

    return-void
.end method

.method private startAActivity(Ljava/lang/String;)V
    .locals 3

    .line 174
    new-instance v0, Landroid/content/Intent;

    iget-object v1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mContext:Landroid/content/Context;

    const-class v2, Lcom/adayo/androidauto/ui/AAActivity;

    invoke-direct {v0, v1, v2}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    const/high16 v1, 0x10000000

    .line 175
    invoke-virtual {v0, v1}, Landroid/content/Intent;->setFlags(I)Landroid/content/Intent;

    if-eqz p1, :cond_0

    const-string v1, ""

    .line 177
    invoke-virtual {p1, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_0

    const-string v1, "default"

    invoke-virtual {p1, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_0

    .line 178
    new-instance v1, Ljava/util/HashMap;

    invoke-direct {v1}, Ljava/util/HashMap;-><init>()V

    const-string v2, "AppType"

    .line 179
    invoke-interface {v1, v2, p1}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    .line 180
    check-cast v1, Ljava/io/Serializable;

    const-string p1, "map"

    invoke-virtual {v0, p1, v1}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/io/Serializable;)Landroid/content/Intent;

    .line 183
    :cond_0
    iget-object p1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mContext:Landroid/content/Context;

    invoke-static {p1, v0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->start(Landroid/content/Context;Landroid/content/Intent;)V

    return-void
.end method


# virtual methods
.method public deleteAndroidAutoDevice(Ljava/lang/String;)V
    .locals 2

    .line 400
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    const-string v1, "com.adayo.androidauto.action.DELETE_DEVICE"

    invoke-virtual {v0, v1}, Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;

    .line 401
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    const-string v1, "bt_address"

    invoke-virtual {v0, v1, p1}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 402
    iget-object p1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mContext:Landroid/content/Context;

    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    invoke-virtual {p1, v0}, Landroid/content/Context;->sendBroadcast(Landroid/content/Intent;)V

    return-void
.end method

.method public disConnectAndroidAutoDevice(Ljava/lang/String;I)V
    .locals 3

    .line 194
    invoke-virtual {p0, p1}, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->queryAADevicesListStatus(Ljava/lang/String;)Lcom/adayo/androidauto/AppInfor/AADevice;

    move-result-object v0

    const-string v1, "AHU.AA.ApiServiceManager"

    if-eqz v0, :cond_3

    if-nez p2, :cond_2

    .line 197
    invoke-virtual {v0}, Lcom/adayo/androidauto/AppInfor/AADevice;->isWirelessConnecttionState()Z

    move-result p2

    const/4 v2, 0x0

    if-eqz p2, :cond_0

    const/4 p2, 0x1

    .line 198
    invoke-virtual {p0, p1, v2, p2}, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->notifyAAServiceConnectStatus(Ljava/lang/String;ZI)V

    goto :goto_0

    .line 199
    :cond_0
    invoke-virtual {v0}, Lcom/adayo/androidauto/AppInfor/AADevice;->isUsbConnectionState()Z

    move-result p2

    if-eqz p2, :cond_1

    .line 200
    invoke-virtual {p0, p1, v2, v2}, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->notifyAAServiceConnectStatus(Ljava/lang/String;ZI)V

    goto :goto_0

    :cond_1
    const-string p1, "No device is found !!!"

    .line 202
    invoke-static {v1, p1}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V

    goto :goto_0

    .line 205
    :cond_2
    invoke-virtual {p0, p1}, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->deleteAndroidAutoDevice(Ljava/lang/String;)V

    goto :goto_0

    :cond_3
    const-string p1, "No device is found !!! queryAADevicesListStatus == null"

    .line 208
    invoke-static {v1, p1}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V

    :goto_0
    return-void
.end method

.method public getCarplayConnectStatus()Z
    .locals 1

    .line 799
    iget-boolean v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->carplayConnectStatus:Z

    return v0
.end method

.method public getCarplayShowStatus()Z
    .locals 1

    .line 805
    iget-boolean v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->carplayShowStatus:Z

    return v0
.end method

.method public getDevicesListData()Ljava/lang/String;
    .locals 12

    const-string v0, "deviceinfo:  null"

    const-string v1, "AHU.AA.ApiServiceManager"

    const-string v2, "deviceinfo"

    .line 605
    filled-new-array {v2}, [Ljava/lang/String;

    move-result-object v5

    const-string v6, "deviceid=?"

    const-string v3, "1"

    .line 607
    filled-new-array {v3}, [Ljava/lang/String;

    move-result-object v7

    const/4 v11, 0x0

    .line 610
    :try_start_0
    iget-object v3, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mContext:Landroid/content/Context;

    invoke-static {v3}, Lcom/adayo/androidauto/sqlite/DatabaseManger;->getInstance(Landroid/content/Context;)Lcom/adayo/androidauto/sqlite/DatabaseManger;

    move-result-object v3

    const-string v4, "ANDROID_AUTO_DEVICES_LIST"

    const/4 v8, 0x0

    const/4 v9, 0x0

    const/4 v10, 0x0

    invoke-virtual/range {v3 .. v10}, Lcom/adayo/androidauto/sqlite/DatabaseManger;->queryData(Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Landroid/database/Cursor;

    move-result-object v3

    .line 611
    invoke-interface {v3}, Landroid/database/Cursor;->moveToFirst()Z

    .line 612
    invoke-interface {v3}, Landroid/database/Cursor;->getCount()I

    move-result v4

    if-lez v4, :cond_0

    .line 614
    invoke-interface {v3, v2}, Landroid/database/Cursor;->getColumnIndex(Ljava/lang/String;)I

    move-result v2

    invoke-interface {v3, v2}, Landroid/database/Cursor;->getString(I)Ljava/lang/String;

    move-result-object v11
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    :cond_0
    if-eqz v11, :cond_1

    .line 622
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "deviceinfo:  "

    invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, v11}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    goto :goto_0

    .line 624
    :cond_1
    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    :goto_0
    return-object v11

    :catch_0
    move-exception v2

    .line 617
    :try_start_1
    invoke-virtual {v2}, Ljava/lang/Exception;->printStackTrace()V

    .line 618
    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    const-string v4, "getDevicesListData:  "

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/Exception;->getMessage()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v3, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-static {v1, v2}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    .line 624
    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    return-object v11

    :catchall_0
    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    return-object v11
.end method

.method public getIsSupportAndroidAuto(Ljava/lang/String;)Z
    .locals 5

    const-string v0, "AHU.AA.ApiServiceManager"

    const/4 v1, 0x0

    .line 492
    :try_start_0
    iget-object v2, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDeviceList:Ljava/util/List;

    if-eqz v2, :cond_3

    iget-object v2, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDeviceList:Ljava/util/List;

    invoke-interface {v2}, Ljava/util/List;->size()I

    move-result v2

    if-lez v2, :cond_3

    .line 493
    iget-object v2, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDeviceList:Ljava/util/List;

    invoke-interface {v2}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v2

    :cond_0
    invoke-interface {v2}, Ljava/util/Iterator;->hasNext()Z

    move-result v3

    if-eqz v3, :cond_4

    invoke-interface {v2}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v3

    check-cast v3, Lcom/adayo/androidauto/AppInfor/AADevice;

    .line 494
    invoke-virtual {v3}, Lcom/adayo/androidauto/AppInfor/AADevice;->getConnectedBTAddress()Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v4, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v4

    if-eqz v4, :cond_0

    .line 495
    invoke-virtual {v3}, Lcom/adayo/androidauto/AppInfor/AADevice;->isWirelessOpenState()Z

    move-result p1

    if-nez p1, :cond_1

    invoke-virtual {v3}, Lcom/adayo/androidauto/AppInfor/AADevice;->isUsbOpenState()Z

    move-result p1

    if-eqz p1, :cond_2

    :cond_1
    const/4 v1, 0x1

    :cond_2
    return v1

    :cond_3
    const-string p1, "ApiServiceManager_getIsSupportAndroidAuto 4: null"

    .line 499
    invoke-static {v0, p1}, Lcom/adayo/androidauto/utils/AALog;->w(Ljava/lang/String;Ljava/lang/String;)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    :cond_4
    return v1

    :catch_0
    move-exception p1

    .line 503
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "getIsSupportAndroidAuto Exception:  "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p1}, Ljava/lang/Exception;->getMessage()Ljava/lang/String;

    move-result-object p1

    invoke-virtual {v2, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-static {v0, p1}, Lcom/adayo/androidauto/utils/AALog;->w(Ljava/lang/String;Ljava/lang/String;)V

    return v1
.end method

.method public init(Landroid/content/Context;)V
    .locals 3

    .line 59
    iput-object p1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mContext:Landroid/content/Context;

    .line 60
    new-instance p1, Landroid/content/Intent;

    invoke-direct {p1}, Landroid/content/Intent;-><init>()V

    iput-object p1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    .line 61
    new-instance p1, Lcom/google/gson/Gson;

    invoke-direct {p1}, Lcom/google/gson/Gson;-><init>()V

    iput-object p1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->gson:Lcom/google/gson/Gson;

    .line 62
    new-instance p1, Ljava/util/ArrayList;

    invoke-direct {p1}, Ljava/util/ArrayList;-><init>()V

    iput-object p1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDeviceList:Ljava/util/List;

    .line 63
    new-instance p1, Ljava/util/ArrayList;

    invoke-direct {p1}, Ljava/util/ArrayList;-><init>()V

    iput-object p1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDbDeviceList:Ljava/util/List;

    .line 64
    new-instance p1, Landroid/os/RemoteCallbackList;

    invoke-direct {p1}, Landroid/os/RemoteCallbackList;-><init>()V

    iput-object p1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mRemoteCallbackList:Landroid/os/RemoteCallbackList;

    const-string p1, "AHU.AA.ApiServiceManager"

    const-string v0, "init aaDeviceList == start"

    .line 65
    invoke-static {p1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 67
    invoke-virtual {p0}, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->getDevicesListData()Ljava/lang/String;

    move-result-object v0

    if-eqz v0, :cond_1

    .line 68
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "init aaDeviceList 1=="

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p0}, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->getDevicesListData()Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {p1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 69
    new-instance v0, Lcom/adayo/androidauto/apiservice/ApiServiceManager$1;

    invoke-direct {v0, p0}, Lcom/adayo/androidauto/apiservice/ApiServiceManager$1;-><init>(Lcom/adayo/androidauto/apiservice/ApiServiceManager;)V

    invoke-virtual {v0}, Lcom/adayo/androidauto/apiservice/ApiServiceManager$1;->getType()Ljava/lang/reflect/Type;

    move-result-object v0

    .line 70
    iget-object v1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->gson:Lcom/google/gson/Gson;

    invoke-virtual {p0}, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->getDevicesListData()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v1, v2, v0}, Lcom/google/gson/Gson;->fromJson(Ljava/lang/String;Ljava/lang/reflect/Type;)Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Ljava/util/List;

    iput-object v1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDeviceList:Ljava/util/List;

    .line 71
    iget-object v1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->gson:Lcom/google/gson/Gson;

    invoke-virtual {p0}, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->getDevicesListData()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v1, v2, v0}, Lcom/google/gson/Gson;->fromJson(Ljava/lang/String;Ljava/lang/reflect/Type;)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Ljava/util/List;

    iput-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDbDeviceList:Ljava/util/List;

    if-eqz v0, :cond_0

    .line 74
    invoke-interface {v0}, Ljava/util/List;->size()I

    move-result v0

    if-lez v0, :cond_0

    const/4 v0, 0x0

    move v1, v0

    .line 75
    :goto_0
    iget-object v2, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDbDeviceList:Ljava/util/List;

    invoke-interface {v2}, Ljava/util/List;->size()I

    move-result v2

    if-ge v1, v2, :cond_0

    .line 76
    iget-object v2, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDeviceList:Ljava/util/List;

    invoke-interface {v2, v1}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Lcom/adayo/androidauto/AppInfor/AADevice;

    invoke-virtual {v2, v0}, Lcom/adayo/androidauto/AppInfor/AADevice;->setUsbConnectionState(Z)V

    .line 77
    iget-object v2, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDeviceList:Ljava/util/List;

    invoke-interface {v2, v1}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Lcom/adayo/androidauto/AppInfor/AADevice;

    invoke-virtual {v2, v0}, Lcom/adayo/androidauto/AppInfor/AADevice;->setUsbOpenState(Z)V

    .line 78
    iget-object v2, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDeviceList:Ljava/util/List;

    invoke-interface {v2, v1}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Lcom/adayo/androidauto/AppInfor/AADevice;

    invoke-virtual {v2, v0}, Lcom/adayo/androidauto/AppInfor/AADevice;->setWirelessOpenState(Z)V

    .line 79
    iget-object v2, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDeviceList:Ljava/util/List;

    invoke-interface {v2, v1}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Lcom/adayo/androidauto/AppInfor/AADevice;

    invoke-virtual {v2, v0}, Lcom/adayo/androidauto/AppInfor/AADevice;->setWirelessConnecttionState(Z)V

    .line 81
    iget-object v2, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDbDeviceList:Ljava/util/List;

    invoke-interface {v2, v1}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Lcom/adayo/androidauto/AppInfor/AADevice;

    invoke-virtual {v2, v0}, Lcom/adayo/androidauto/AppInfor/AADevice;->setUsbConnectionState(Z)V

    .line 82
    iget-object v2, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDbDeviceList:Ljava/util/List;

    invoke-interface {v2, v1}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Lcom/adayo/androidauto/AppInfor/AADevice;

    invoke-virtual {v2, v0}, Lcom/adayo/androidauto/AppInfor/AADevice;->setUsbOpenState(Z)V

    .line 83
    iget-object v2, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDbDeviceList:Ljava/util/List;

    invoke-interface {v2, v1}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Lcom/adayo/androidauto/AppInfor/AADevice;

    invoke-virtual {v2, v0}, Lcom/adayo/androidauto/AppInfor/AADevice;->setWirelessOpenState(Z)V

    .line 84
    iget-object v2, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDbDeviceList:Ljava/util/List;

    invoke-interface {v2, v1}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Lcom/adayo/androidauto/AppInfor/AADevice;

    invoke-virtual {v2, v0}, Lcom/adayo/androidauto/AppInfor/AADevice;->setWirelessConnecttionState(Z)V

    add-int/lit8 v1, v1, 0x1

    goto :goto_0

    .line 88
    :cond_0
    invoke-static {}, Lcom/adayo/androidauto/AppInfor/AppInforManager;->getInstance()Lcom/adayo/androidauto/AppInfor/AppInforManager;

    move-result-object v0

    iget-object v1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDbDeviceList:Ljava/util/List;

    invoke-virtual {v0, v1}, Lcom/adayo/androidauto/AppInfor/AppInforManager;->initConnectMacListData(Ljava/util/List;)V

    .line 89
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "init aaDeviceList 2=="

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDeviceList:Ljava/util/List;

    invoke-virtual {v1}, Ljava/lang/Object;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {p1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 93
    :cond_1
    invoke-direct {p0}, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->registerCarPlayConnectStatusReceiver()V

    const-string v0, "init aaDeviceList == end"

    .line 95
    invoke-static {p1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    return-void
.end method

.method public isNewDeviceConnect(Ljava/util/List;)V
    .locals 5
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/util/List<",
            "Lcom/adayo/androidauto/AppInfor/AADevice;",
            ">;)V"
        }
    .end annotation

    .line 415
    invoke-direct {p0, p1}, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->listDataComparison(Ljava/util/List;)Lcom/adayo/androidauto/AppInfor/AADevice;

    move-result-object v0

    const/4 v1, 0x0

    const/4 v2, 0x1

    if-eqz v0, :cond_1

    .line 416
    invoke-virtual {v0}, Lcom/adayo/androidauto/AppInfor/AADevice;->getConnectedBTAddress()Ljava/lang/String;

    move-result-object v3

    if-eqz v3, :cond_1

    .line 417
    invoke-virtual {v0}, Lcom/adayo/androidauto/AppInfor/AADevice;->getConnectedBTAddress()Ljava/lang/String;

    move-result-object v3

    .line 418
    invoke-virtual {v0}, Lcom/adayo/androidauto/AppInfor/AADevice;->isUsbOpenState()Z

    move-result v4

    if-nez v4, :cond_0

    invoke-virtual {v0}, Lcom/adayo/androidauto/AppInfor/AADevice;->isWirelessOpenState()Z

    move-result v0

    if-eqz v0, :cond_2

    :cond_0
    move v1, v2

    goto :goto_0

    :cond_1
    const-string v3, ""

    .line 422
    :cond_2
    :goto_0
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->wirelessDeviceList:Ljava/util/List;

    if-eqz v0, :cond_3

    .line 423
    invoke-interface {v0}, Ljava/util/List;->clear()V

    goto :goto_1

    .line 425
    :cond_3
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    iput-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->wirelessDeviceList:Ljava/util/List;

    .line 429
    :goto_1
    invoke-interface {p1}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object p1

    :cond_4
    :goto_2
    invoke-interface {p1}, Ljava/util/Iterator;->hasNext()Z

    move-result v0

    if-eqz v0, :cond_5

    invoke-interface {p1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Lcom/adayo/androidauto/AppInfor/AADevice;

    .line 430
    invoke-virtual {v0}, Lcom/adayo/androidauto/AppInfor/AADevice;->isWirelessOpenState()Z

    move-result v4

    if-eqz v4, :cond_4

    .line 431
    iget-object v4, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->wirelessDeviceList:Ljava/util/List;

    invoke-interface {v4, v0}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    goto :goto_2

    .line 437
    :cond_5
    invoke-static {v3}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z

    move-result p1

    if-nez p1, :cond_6

    iget-object p1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->wirelessDeviceList:Ljava/util/List;

    invoke-interface {p1}, Ljava/util/List;->size()I

    move-result p1

    if-le p1, v2, :cond_6

    .line 438
    invoke-virtual {p0, v3, v2, v1}, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->notifyAndroidAutoConnectSupport(Ljava/lang/String;IZ)V

    :cond_6
    return-void
.end method

.method public killRemoteCallback()V
    .locals 1

    .line 666
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mRemoteCallbackList:Landroid/os/RemoteCallbackList;

    if-eqz v0, :cond_0

    .line 667
    invoke-virtual {v0}, Landroid/os/RemoteCallbackList;->kill()V

    :cond_0
    return-void
.end method

.method public notifyAAServiceConnectStatus(Ljava/lang/String;ZI)V
    .locals 2

    .line 387
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    const-string v1, "com.adayo.androidauto.action.CONNECT_DISCONNECT"

    invoke-virtual {v0, v1}, Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;

    .line 388
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    const-string v1, "connect_status"

    invoke-virtual {v0, v1, p2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Z)Landroid/content/Intent;

    .line 389
    iget-object p2, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    const-string v0, "connect_mac"

    invoke-virtual {p2, v0, p1}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 390
    iget-object p1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    const-string p2, "connect_type"

    invoke-virtual {p1, p2, p3}, Landroid/content/Intent;->putExtra(Ljava/lang/String;I)Landroid/content/Intent;

    .line 391
    iget-object p1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mContext:Landroid/content/Context;

    iget-object p2, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    invoke-virtual {p1, p2}, Landroid/content/Context;->sendBroadcast(Landroid/content/Intent;)V

    return-void
.end method

.method public notifyAndroidAutoConnectStatus(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZI)V
    .locals 2

    .line 301
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    const-string v1, "com.adayo.androidauto.action.NOTIFY_ANDROID_AUTO_CONNECT_STATUS"

    invoke-virtual {v0, v1}, Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;

    .line 302
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    const-string v1, "instanceId"

    invoke-virtual {v0, v1, p1}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 303
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    const-string v1, "btAddress"

    invoke-virtual {v0, v1, p2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 304
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    const-string v1, "connectStatus"

    invoke-virtual {v0, v1, p5}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Z)Landroid/content/Intent;

    .line 305
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    const-string v1, "connect_status"

    invoke-virtual {v0, v1, p5}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Z)Landroid/content/Intent;

    .line 306
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    const-string v1, "connect_mac"

    invoke-virtual {v0, v1, p2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 307
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    const-string v1, "isAvailable"

    invoke-virtual {v0, v1, p4}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Z)Landroid/content/Intent;

    .line 308
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    const-string v1, "deviceName"

    invoke-virtual {v0, v1, p3}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 309
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    const-string v1, "connectType"

    invoke-virtual {v0, v1, p6}, Landroid/content/Intent;->putExtra(Ljava/lang/String;I)Landroid/content/Intent;

    .line 310
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mContext:Landroid/content/Context;

    iget-object v1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    invoke-virtual {v0, v1}, Landroid/content/Context;->sendBroadcast(Landroid/content/Intent;)V

    .line 312
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "instanceId:"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v1, "  btAddress:"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v1, "  connectStatus:"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p5}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    const-string v1, "  isAvailable:"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p4}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    const-string v1, "  deviceName:"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    const-string v1, "AHU.DevicesListApi"

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 320
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

    if-eqz v0, :cond_0

    .line 322
    invoke-virtual/range {p0 .. p6}, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->retAndroidAutoInfoCallBack(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZI)V

    :cond_0
    return-void
.end method

.method public notifyAndroidAutoConnectSupport(Ljava/lang/String;IZ)V
    .locals 3

    .line 334
    invoke-static {p1}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z

    move-result v0

    if-eqz v0, :cond_0

    return-void

    .line 338
    :cond_0
    invoke-direct {p0, p1}, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->getDeviceName(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    if-nez v0, :cond_1

    const-string v0, "Unknown Wireless Device"

    .line 344
    :cond_1
    iget-object v1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    const-string v2, "com.adayo.androidauto.action.NOTIFY_ANDROID_AUTO_CONNECT_SUPPORT"

    invoke-virtual {v1, v2}, Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;

    .line 345
    iget-object v1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    const-string v2, "connect_mac"

    invoke-virtual {v1, v2, p1}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 346
    iget-object v1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    const-string v2, "connect_device_name"

    invoke-virtual {v1, v2, v0}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 347
    iget-object v1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    const-string v2, "connect_type"

    invoke-virtual {v1, v2, p2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;I)Landroid/content/Intent;

    .line 348
    iget-object p2, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    const-string v1, "is_support"

    invoke-virtual {p2, v1, p3}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Z)Landroid/content/Intent;

    .line 349
    iget-object p2, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mContext:Landroid/content/Context;

    iget-object p3, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    invoke-virtual {p2, p3}, Landroid/content/Context;->sendBroadcast(Landroid/content/Intent;)V

    .line 351
    invoke-static {}, Lcom/adayo/androidauto/config/LoadJson;->getInstance()Lcom/adayo/androidauto/config/LoadJson;

    move-result-object p2

    invoke-virtual {p2}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object p2

    invoke-virtual {p2}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object p2

    invoke-virtual {p2}, Lcom/adayo/androidauto/config/bean/Information;->getHeadunitModel()Ljava/lang/String;

    move-result-object p2

    const-string p3, "HMA212"

    invoke-virtual {p2, p3}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result p2

    if-eqz p2, :cond_2

    .line 353
    invoke-virtual {p0, p1, v0}, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->retAndroidAutoSupportCallBack(Ljava/lang/String;Ljava/lang/String;)V

    :cond_2
    return-void
.end method

.method public notifyAndroidAutoConnectUSBSupport(Ljava/lang/String;Ljava/lang/String;)V
    .locals 2

    if-eqz p2, :cond_0

    const-string v0, ""

    .line 364
    invoke-virtual {p2, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_1

    :cond_0
    const-string p2, "Unknown USB Device"

    .line 369
    :cond_1
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    const-string v1, "com.adayo.androidauto.action.NOTIFY_ANDROID_AUTO_CONNECT_SUPPORT"

    invoke-virtual {v0, v1}, Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;

    .line 370
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    const-string v1, "connect_mac"

    invoke-virtual {v0, v1, p1}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 371
    iget-object p1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    const-string v0, "connect_device_name"

    invoke-virtual {p1, v0, p2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 374
    iget-object p1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mContext:Landroid/content/Context;

    iget-object p2, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    invoke-virtual {p1, p2}, Landroid/content/Context;->sendBroadcast(Landroid/content/Intent;)V

    return-void
.end method

.method public notifyOpenHotspot(Z)V
    .locals 2

    .line 636
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    const-string v1, "com.adayo.androidauto.action.NOTIFY_OPEN_HOT_SPOT"

    invoke-virtual {v0, v1}, Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;

    .line 637
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    const-string v1, "isOpenHotspot"

    invoke-virtual {v0, v1, p1}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Z)Landroid/content/Intent;

    .line 638
    iget-object p1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mContext:Landroid/content/Context;

    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mIntent:Landroid/content/Intent;

    invoke-virtual {p1, v0}, Landroid/content/Context;->sendBroadcast(Landroid/content/Intent;)V

    const-string p1, "AHU.AA.ApiServiceManager"

    const-string v0, "notifyOpenHotspot"

    .line 639
    invoke-static {p1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    return-void
.end method

.method public queryAADevicesListStatus(Ljava/lang/String;)Lcom/adayo/androidauto/AppInfor/AADevice;
    .locals 3

    const-string v0, "AHU.AA.ApiServiceManager"

    const-string v1, "aaDeviceList:queryAADevicesListStatus"

    .line 238
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 239
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDeviceList:Ljava/util/List;

    if-eqz v0, :cond_1

    invoke-interface {v0}, Ljava/util/List;->size()I

    move-result v0

    if-lez v0, :cond_1

    .line 240
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDeviceList:Ljava/util/List;

    invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :cond_0
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_1

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/adayo/androidauto/AppInfor/AADevice;

    .line 241
    invoke-virtual {v1}, Lcom/adayo/androidauto/AppInfor/AADevice;->getConnectedBTAddress()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v2, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_0

    return-object v1

    :cond_1
    const/4 p1, 0x0

    return-object p1
.end method

.method public queryAndroidAutoConnect(Ljava/lang/String;)Z
    .locals 3

    const/4 v0, 0x0

    .line 219
    :try_start_0
    invoke-virtual {p0, p1}, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->queryAADevicesListStatus(Ljava/lang/String;)Lcom/adayo/androidauto/AppInfor/AADevice;

    move-result-object p1

    if-eqz p1, :cond_1

    .line 221
    invoke-virtual {p1}, Lcom/adayo/androidauto/AppInfor/AADevice;->isUsbConnectionState()Z

    move-result v1

    if-nez v1, :cond_0

    .line 222
    invoke-virtual {p1}, Lcom/adayo/androidauto/AppInfor/AADevice;->isWirelessConnecttionState()Z

    move-result p1
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    if-eqz p1, :cond_1

    :cond_0
    const/4 v0, 0x1

    :cond_1
    return v0

    :catch_0
    move-exception p1

    .line 227
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "queryAndroidAutoConnect Exception:  "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p1}, Ljava/lang/Exception;->getMessage()Ljava/lang/String;

    move-result-object p1

    invoke-virtual {v1, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    const-string v1, "AHU.AA.ApiServiceManager"

    invoke-static {v1, p1}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V

    return v0
.end method

.method public queryDbAADevicesListStatus(Ljava/lang/String;)Lcom/adayo/androidauto/AppInfor/AADevice;
    .locals 3

    const-string v0, "AHU.AA.ApiServiceManager"

    const-string v1, "aaDeviceList:queryDbAADevicesListStatus"

    .line 255
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 256
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDbDeviceList:Ljava/util/List;

    if-eqz v0, :cond_1

    invoke-interface {v0}, Ljava/util/List;->size()I

    move-result v0

    if-lez v0, :cond_1

    .line 257
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDbDeviceList:Ljava/util/List;

    invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :cond_0
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_1

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/adayo/androidauto/AppInfor/AADevice;

    .line 258
    invoke-virtual {v1}, Lcom/adayo/androidauto/AppInfor/AADevice;->getConnectedBTAddress()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v2, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_0

    return-object v1

    :cond_1
    const/4 p1, 0x0

    return-object p1
.end method

.method public registerRemoteCallback(Lcom/adayo/androidauto/IAAStatusCallBack;)V
    .locals 1

    .line 647
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mRemoteCallbackList:Landroid/os/RemoteCallbackList;

    if-eqz v0, :cond_0

    if-eqz p1, :cond_0

    .line 648
    invoke-virtual {v0, p1}, Landroid/os/RemoteCallbackList;->register(Landroid/os/IInterface;)Z

    :cond_0
    return-void
.end method

.method public retAndroidAutoInfoCallBack(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZI)V
    .locals 1

    .line 682
    new-instance v0, Lcom/adayo/androidauto/AAStatusBean;

    invoke-direct {v0}, Lcom/adayo/androidauto/AAStatusBean;-><init>()V

    .line 683
    invoke-virtual {v0, p1}, Lcom/adayo/androidauto/AAStatusBean;->setInstanceId(Ljava/lang/String;)V

    .line 684
    invoke-virtual {v0, p2}, Lcom/adayo/androidauto/AAStatusBean;->setBtAddress(Ljava/lang/String;)V

    .line 685
    invoke-virtual {v0, p3}, Lcom/adayo/androidauto/AAStatusBean;->setDeviceName(Ljava/lang/String;)V

    .line 686
    invoke-virtual {v0, p4}, Lcom/adayo/androidauto/AAStatusBean;->setIsAvailable(I)V

    .line 687
    invoke-virtual {v0, p5}, Lcom/adayo/androidauto/AAStatusBean;->setConnectStatus(I)V

    .line 688
    invoke-virtual {v0, p6}, Lcom/adayo/androidauto/AAStatusBean;->setConnectType(I)V

    .line 689
    new-instance p1, Ljava/lang/StringBuilder;

    invoke-direct {p1}, Ljava/lang/StringBuilder;-><init>()V

    const-string p2, "instanceId:"

    invoke-virtual {p1, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 690
    invoke-virtual {v0}, Lcom/adayo/androidauto/AAStatusBean;->getInstanceId()Ljava/lang/String;

    move-result-object p2

    invoke-virtual {p1, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string p2, "  btAddress:"

    invoke-virtual {p1, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 691
    invoke-virtual {v0}, Lcom/adayo/androidauto/AAStatusBean;->getBtAddress()Ljava/lang/String;

    move-result-object p2

    invoke-virtual {p1, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string p2, "  deviceName:"

    invoke-virtual {p1, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 692
    invoke-virtual {v0}, Lcom/adayo/androidauto/AAStatusBean;->getDeviceName()Ljava/lang/String;

    move-result-object p2

    invoke-virtual {p1, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string p2, "  isAvailable:"

    invoke-virtual {p1, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 693
    invoke-virtual {v0}, Lcom/adayo/androidauto/AAStatusBean;->getIsAvailable()I

    move-result p2

    invoke-virtual {p1, p2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string p2, "  connectStatus:"

    invoke-virtual {p1, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 694
    invoke-virtual {v0}, Lcom/adayo/androidauto/AAStatusBean;->getConnectStatus()I

    move-result p2

    invoke-virtual {p1, p2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string p2, "  connectType:"

    invoke-virtual {p1, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 695
    invoke-virtual {v0}, Lcom/adayo/androidauto/AAStatusBean;->getConnectType()I

    move-result p2

    invoke-virtual {p1, p2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {p1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    const-string p2, "AHU.AA.ApiServiceManager"

    .line 689
    invoke-static {p2, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 697
    :try_start_0
    new-instance p1, Ljava/lang/StringBuilder;

    invoke-direct {p1}, Ljava/lang/StringBuilder;-><init>()V

    const-string p3, "mRemoteCallbackList.beginBroadcast()---"

    invoke-virtual {p1, p3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object p3, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mRemoteCallbackList:Landroid/os/RemoteCallbackList;

    invoke-virtual {p3}, Landroid/os/RemoteCallbackList;->getRegisteredCallbackCount()I

    move-result p3

    invoke-virtual {p1, p3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {p1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-static {p2, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 698
    iget-object p1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mRemoteCallbackList:Landroid/os/RemoteCallbackList;

    invoke-virtual {p1}, Landroid/os/RemoteCallbackList;->beginBroadcast()I

    move-result p1

    .line 699
    new-instance p3, Ljava/lang/StringBuilder;

    invoke-direct {p3}, Ljava/lang/StringBuilder;-><init>()V

    const-string p4, "mRemoteCallbackList.beginBroadcast():"

    invoke-virtual {p3, p4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p3, p1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {p3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p3

    invoke-static {p2, p3}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    :goto_0
    if-lez p1, :cond_0

    add-int/lit8 p1, p1, -0x1

    .line 702
    iget-object p3, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mRemoteCallbackList:Landroid/os/RemoteCallbackList;

    invoke-virtual {p3, p1}, Landroid/os/RemoteCallbackList;->getBroadcastItem(I)Landroid/os/IInterface;

    move-result-object p3

    check-cast p3, Lcom/adayo/androidauto/IAAStatusCallBack;

    invoke-interface {p3, v0}, Lcom/adayo/androidauto/IAAStatusCallBack;->retAndroidAutoStatus(Lcom/adayo/androidauto/AAStatusBean;)V
    :try_end_0
    .catch Landroid/os/RemoteException; {:try_start_0 .. :try_end_0} :catch_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    goto :goto_0

    :catchall_0
    move-exception p1

    goto :goto_1

    :catch_0
    move-exception p1

    .line 705
    :try_start_1
    invoke-virtual {p1}, Landroid/os/RemoteException;->printStackTrace()V

    .line 706
    new-instance p3, Ljava/lang/StringBuilder;

    invoke-direct {p3}, Ljava/lang/StringBuilder;-><init>()V

    const-string p4, "statusCallBack:"

    invoke-virtual {p3, p4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p1}, Landroid/os/RemoteException;->getMessage()Ljava/lang/String;

    move-result-object p1

    invoke-virtual {p3, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-static {p2, p1}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    .line 708
    :cond_0
    iget-object p1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mRemoteCallbackList:Landroid/os/RemoteCallbackList;

    invoke-virtual {p1}, Landroid/os/RemoteCallbackList;->finishBroadcast()V

    return-void

    :goto_1
    iget-object p2, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mRemoteCallbackList:Landroid/os/RemoteCallbackList;

    invoke-virtual {p2}, Landroid/os/RemoteCallbackList;->finishBroadcast()V

    .line 709
    throw p1
.end method

.method public retAndroidAutoMediaInfoCallBack(Ljava/lang/String;)V
    .locals 4

    const-string v0, "AHU.AA.ApiServiceManager"

    .line 739
    :try_start_0
    iget-object v1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mRemoteCallbackList:Landroid/os/RemoteCallbackList;

    invoke-virtual {v1}, Landroid/os/RemoteCallbackList;->beginBroadcast()I

    move-result v1

    .line 740
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "mRemoteCallbackList.beginBroadcast():"

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-static {v0, v2}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    :goto_0
    if-lez v1, :cond_0

    add-int/lit8 v1, v1, -0x1

    .line 743
    iget-object v2, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mRemoteCallbackList:Landroid/os/RemoteCallbackList;

    invoke-virtual {v2, v1}, Landroid/os/RemoteCallbackList;->getBroadcastItem(I)Landroid/os/IInterface;

    move-result-object v2

    check-cast v2, Lcom/adayo/androidauto/IAAStatusCallBack;

    invoke-interface {v2, p1}, Lcom/adayo/androidauto/IAAStatusCallBack;->retAndroidAutoMediaInfo(Ljava/lang/String;)V
    :try_end_0
    .catch Landroid/os/RemoteException; {:try_start_0 .. :try_end_0} :catch_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    goto :goto_0

    .line 749
    :cond_0
    :goto_1
    iget-object p1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mRemoteCallbackList:Landroid/os/RemoteCallbackList;

    invoke-virtual {p1}, Landroid/os/RemoteCallbackList;->finishBroadcast()V

    goto :goto_2

    :catchall_0
    move-exception p1

    goto :goto_3

    :catch_0
    move-exception p1

    .line 746
    :try_start_1
    invoke-virtual {p1}, Landroid/os/RemoteException;->printStackTrace()V

    .line 747
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "statusCallBack:"

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p1}, Landroid/os/RemoteException;->getMessage()Ljava/lang/String;

    move-result-object p1

    invoke-virtual {v1, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-static {v0, p1}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    goto :goto_1

    :goto_2
    return-void

    .line 749
    :goto_3
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mRemoteCallbackList:Landroid/os/RemoteCallbackList;

    invoke-virtual {v0}, Landroid/os/RemoteCallbackList;->finishBroadcast()V

    .line 750
    throw p1
.end method

.method public retAndroidAutoSupportCallBack(Ljava/lang/String;Ljava/lang/String;)V
    .locals 4

    const-string v0, "AHU.AA.ApiServiceManager"

    .line 719
    :try_start_0
    iget-object v1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mRemoteCallbackList:Landroid/os/RemoteCallbackList;

    invoke-virtual {v1}, Landroid/os/RemoteCallbackList;->beginBroadcast()I

    move-result v1

    .line 720
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "mRemoteCallbackList.beginBroadcast():"

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-static {v0, v2}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    :goto_0
    if-lez v1, :cond_0

    add-int/lit8 v1, v1, -0x1

    .line 723
    iget-object v2, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mRemoteCallbackList:Landroid/os/RemoteCallbackList;

    invoke-virtual {v2, v1}, Landroid/os/RemoteCallbackList;->getBroadcastItem(I)Landroid/os/IInterface;

    move-result-object v2

    check-cast v2, Lcom/adayo/androidauto/IAAStatusCallBack;

    invoke-interface {v2, p1, p2}, Lcom/adayo/androidauto/IAAStatusCallBack;->retAndroidAutoSupport(Ljava/lang/String;Ljava/lang/String;)V
    :try_end_0
    .catch Landroid/os/RemoteException; {:try_start_0 .. :try_end_0} :catch_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    goto :goto_0

    .line 729
    :cond_0
    :goto_1
    iget-object p1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mRemoteCallbackList:Landroid/os/RemoteCallbackList;

    invoke-virtual {p1}, Landroid/os/RemoteCallbackList;->finishBroadcast()V

    goto :goto_2

    :catchall_0
    move-exception p1

    goto :goto_3

    :catch_0
    move-exception p1

    .line 726
    :try_start_1
    invoke-virtual {p1}, Landroid/os/RemoteException;->printStackTrace()V

    .line 727
    new-instance p2, Ljava/lang/StringBuilder;

    invoke-direct {p2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "statusCallBack:"

    invoke-virtual {p2, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p1}, Landroid/os/RemoteException;->getMessage()Ljava/lang/String;

    move-result-object p1

    invoke-virtual {p2, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-static {v0, p1}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    goto :goto_1

    :goto_2
    return-void

    .line 729
    :goto_3
    iget-object p2, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mRemoteCallbackList:Landroid/os/RemoteCallbackList;

    invoke-virtual {p2}, Landroid/os/RemoteCallbackList;->finishBroadcast()V

    .line 730
    throw p1
.end method

.method public setAaDeviceListData(Ljava/lang/String;)V
    .locals 3

    .line 268
    new-instance v0, Lcom/adayo/androidauto/apiservice/ApiServiceManager$3;

    invoke-direct {v0, p0}, Lcom/adayo/androidauto/apiservice/ApiServiceManager$3;-><init>(Lcom/adayo/androidauto/apiservice/ApiServiceManager;)V

    invoke-virtual {v0}, Lcom/adayo/androidauto/apiservice/ApiServiceManager$3;->getType()Ljava/lang/reflect/Type;

    move-result-object v0

    .line 269
    iget-object v1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->gson:Lcom/google/gson/Gson;

    invoke-virtual {v1, p1, v0}, Lcom/google/gson/Gson;->fromJson(Ljava/lang/String;Ljava/lang/reflect/Type;)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Ljava/util/List;

    const-string v1, "AHU.AA.ApiServiceManager"

    .line 271
    invoke-static {v1, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 273
    iget-object p1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDeviceList:Ljava/util/List;

    if-eqz p1, :cond_0

    invoke-interface {p1}, Ljava/util/List;->size()I

    move-result p1

    if-lez p1, :cond_0

    .line 274
    iget-object p1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDeviceList:Ljava/util/List;

    invoke-interface {p1}, Ljava/util/List;->clear()V

    goto :goto_0

    .line 276
    :cond_0
    new-instance p1, Ljava/util/ArrayList;

    invoke-direct {p1}, Ljava/util/ArrayList;-><init>()V

    iput-object p1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDeviceList:Ljava/util/List;

    :goto_0
    const-string p1, "aaDeviceList:mDevicesListData1"

    .line 278
    invoke-static {v1, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 281
    invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object p1

    :goto_1
    invoke-interface {p1}, Ljava/util/Iterator;->hasNext()Z

    move-result v0

    if-eqz v0, :cond_1

    invoke-interface {p1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Lcom/adayo/androidauto/AppInfor/AADevice;

    .line 282
    iget-object v2, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDeviceList:Ljava/util/List;

    invoke-interface {v2, v0}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    goto :goto_1

    :cond_1
    const-string p1, "aaDeviceList:mDevicesListData2"

    .line 284
    invoke-static {v1, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 285
    iget-object p1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDeviceList:Ljava/util/List;

    invoke-virtual {p0, p1}, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->isNewDeviceConnect(Ljava/util/List;)V

    const-string p1, "aaDeviceList:mDevicesListData3"

    .line 286
    invoke-static {v1, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 288
    new-instance p1, Ljava/lang/StringBuilder;

    invoke-direct {p1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v0, "aaDeviceList:"

    invoke-virtual {p1, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDeviceList:Ljava/util/List;

    invoke-interface {v0}, Ljava/util/List;->size()I

    move-result v0

    invoke-virtual {p1, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {p1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-static {v1, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    return-void
.end method

.method public setStartAndroidAutoStatus(Z)V
    .locals 0

    .line 166
    iput-boolean p1, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->isStartAndroidAutoStatus:Z

    return-void
.end method

.method public startAndroidAutoDevice(Ljava/lang/String;Ljava/lang/String;)V
    .locals 6

    .line 110
    iget-boolean v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->isStartAndroidAutoStatus:Z

    const-string v1, "AHU.AA.ApiServiceManager"

    if-eqz v0, :cond_8

    const/4 v0, 0x0

    .line 111
    iput-boolean v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->isStartAndroidAutoStatus:Z

    .line 112
    iget-object v2, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->apiHandler:Landroid/os/Handler;

    const/16 v3, 0x2718

    const-wide/16 v4, 0x2710

    invoke-virtual {v2, v3, v4, v5}, Landroid/os/Handler;->sendEmptyMessageDelayed(IJ)Z

    .line 119
    invoke-static {p1}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z

    move-result v2

    const/4 v3, 0x1

    if-eqz v2, :cond_2

    .line 121
    invoke-direct {p0}, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->isUSBDeviceAvailable()Z

    move-result v2

    if-eqz v2, :cond_1

    .line 123
    invoke-direct {p0}, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->isUSBDeviceConnect()Z

    move-result v2

    if-eqz v2, :cond_0

    .line 124
    invoke-direct {p0, p2}, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->startAActivity(Ljava/lang/String;)V

    const-string p1, "startAndroidAutoDevice startAActivity"

    .line 125
    invoke-static {v1, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    goto :goto_0

    .line 128
    :cond_0
    invoke-virtual {p0, p1, v3, v0}, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->notifyAAServiceConnectStatus(Ljava/lang/String;ZI)V

    const-string p1, "startAndroidAutoDevice notifyAAServiceConnectStatus"

    .line 129
    invoke-static {v1, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    goto :goto_0

    .line 133
    :cond_1
    invoke-virtual {p0, p1, v3, v0}, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->notifyAAServiceConnectStatus(Ljava/lang/String;ZI)V

    :goto_0
    return-void

    .line 138
    :cond_2
    invoke-virtual {p0, p1}, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->queryAADevicesListStatus(Ljava/lang/String;)Lcom/adayo/androidauto/AppInfor/AADevice;

    move-result-object v0

    if-eqz v0, :cond_7

    .line 140
    invoke-virtual {v0}, Lcom/adayo/androidauto/AppInfor/AADevice;->isUsbConnectionState()Z

    move-result v2

    if-nez v2, :cond_6

    invoke-virtual {v0}, Lcom/adayo/androidauto/AppInfor/AADevice;->isWirelessConnecttionState()Z

    move-result v2

    if-eqz v2, :cond_3

    goto :goto_2

    .line 142
    :cond_3
    invoke-virtual {v0}, Lcom/adayo/androidauto/AppInfor/AADevice;->isUsbOpenState()Z

    move-result p2

    if-nez p2, :cond_5

    invoke-virtual {v0}, Lcom/adayo/androidauto/AppInfor/AADevice;->isWirelessOpenState()Z

    move-result p2

    if-eqz p2, :cond_4

    goto :goto_1

    :cond_4
    const-string p1, "No device is found !!!"

    .line 146
    invoke-static {v1, p1}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V

    goto :goto_3

    :cond_5
    :goto_1
    const/4 p2, 0x2

    .line 144
    invoke-virtual {p0, p1, v3, p2}, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->notifyAAServiceConnectStatus(Ljava/lang/String;ZI)V

    goto :goto_3

    .line 141
    :cond_6
    :goto_2
    invoke-direct {p0, p2}, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->startAActivity(Ljava/lang/String;)V

    goto :goto_3

    :cond_7
    const-string p1, "No device is found !!! queryAADevicesListStatus == null"

    .line 149
    invoke-static {v1, p1}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V

    :goto_3
    return-void

    :cond_8
    const-string p1, "connectAndroidAuto cannot be executed again for 10 seconds"

    .line 114
    invoke-static {v1, p1}, Lcom/adayo/androidauto/utils/AALog;->w(Ljava/lang/String;Ljava/lang/String;)V

    return-void
.end method

.method public unregisterRemoteCallback(Lcom/adayo/androidauto/IAAStatusCallBack;)V
    .locals 1

    .line 657
    iget-object v0, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mRemoteCallbackList:Landroid/os/RemoteCallbackList;

    if-eqz v0, :cond_0

    if-eqz p1, :cond_0

    .line 658
    invoke-virtual {v0, p1}, Landroid/os/RemoteCallbackList;->unregister(Landroid/os/IInterface;)Z

    :cond_0
    return-void
.end method

.method public updateDevicesListData(Ljava/lang/String;)V
    .locals 12

    const-string v0, "AHU.AA.ApiServiceManager"

    .line 561
    new-instance v1, Landroid/content/ContentValues;

    invoke-direct {v1}, Landroid/content/ContentValues;-><init>()V

    const-string v2, "deviceid"

    const-string v3, "1"

    .line 562
    invoke-virtual {v1, v2, v3}, Landroid/content/ContentValues;->put(Ljava/lang/String;Ljava/lang/String;)V

    const-string v2, "deviceinfo"

    .line 563
    invoke-virtual {v1, v2, p1}, Landroid/content/ContentValues;->put(Ljava/lang/String;Ljava/lang/String;)V

    .line 565
    filled-new-array {v2}, [Ljava/lang/String;

    move-result-object v6

    const-string v2, "deviceid=?"

    .line 567
    filled-new-array {v3}, [Ljava/lang/String;

    move-result-object v3

    .line 570
    :try_start_0
    iget-object v4, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mContext:Landroid/content/Context;

    invoke-static {v4}, Lcom/adayo/androidauto/sqlite/DatabaseManger;->getInstance(Landroid/content/Context;)Lcom/adayo/androidauto/sqlite/DatabaseManger;

    move-result-object v4

    const-string v5, "ANDROID_AUTO_DEVICES_LIST"

    const/4 v9, 0x0

    const/4 v10, 0x0

    const/4 v11, 0x0

    move-object v7, v2

    move-object v8, v3

    invoke-virtual/range {v4 .. v11}, Lcom/adayo/androidauto/sqlite/DatabaseManger;->queryData(Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Landroid/database/Cursor;

    move-result-object v4

    .line 571
    invoke-interface {v4}, Landroid/database/Cursor;->moveToFirst()Z

    const-string v5, "aaDeviceList:updateDevicesListData"

    .line 573
    invoke-static {v0, v5}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 576
    invoke-interface {v4}, Landroid/database/Cursor;->getCount()I

    move-result v4
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    const-string v5, "ANDROID_AUTO_DEVICES_LIST"

    if-lez v4, :cond_0

    :try_start_1
    const-string v4, "deviceinfo:  Successful: cursor.getCount() > 0"

    .line 577
    invoke-static {v0, v4}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 578
    iget-object v4, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mContext:Landroid/content/Context;

    invoke-static {v4}, Lcom/adayo/androidauto/sqlite/DatabaseManger;->getInstance(Landroid/content/Context;)Lcom/adayo/androidauto/sqlite/DatabaseManger;

    move-result-object v4

    invoke-virtual {v4, v5, v1, v2, v3}, Lcom/adayo/androidauto/sqlite/DatabaseManger;->updateData(Ljava/lang/String;Landroid/content/ContentValues;Ljava/lang/String;[Ljava/lang/String;)I

    move-result v1

    int-to-long v1, v1

    goto :goto_0

    :cond_0
    const-string v2, "deviceinfo:  Successful: cursor.getCount() < 0"

    .line 580
    invoke-static {v0, v2}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 581
    iget-object v2, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->mContext:Landroid/content/Context;

    invoke-static {v2}, Lcom/adayo/androidauto/sqlite/DatabaseManger;->getInstance(Landroid/content/Context;)Lcom/adayo/androidauto/sqlite/DatabaseManger;

    move-result-object v2

    invoke-virtual {v2, v5, v1}, Lcom/adayo/androidauto/sqlite/DatabaseManger;->insetData(Ljava/lang/String;Landroid/content/ContentValues;)J

    move-result-wide v1

    .line 585
    :goto_0
    new-instance v3, Lcom/adayo/androidauto/apiservice/ApiServiceManager$4;

    invoke-direct {v3, p0}, Lcom/adayo/androidauto/apiservice/ApiServiceManager$4;-><init>(Lcom/adayo/androidauto/apiservice/ApiServiceManager;)V

    invoke-virtual {v3}, Lcom/adayo/androidauto/apiservice/ApiServiceManager$4;->getType()Ljava/lang/reflect/Type;

    move-result-object v3

    .line 586
    iget-object v4, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->gson:Lcom/google/gson/Gson;

    invoke-virtual {v4, p1, v3}, Lcom/google/gson/Gson;->fromJson(Ljava/lang/String;Ljava/lang/reflect/Type;)Ljava/lang/Object;

    move-result-object v3

    check-cast v3, Ljava/util/List;

    iput-object v3, p0, Lcom/adayo/androidauto/apiservice/ApiServiceManager;->aaDbDeviceList:Ljava/util/List;

    const-wide/16 v3, 0x0

    cmp-long v1, v1, v3

    if-gez v1, :cond_1

    const-string p1, "deviceinfo:  Data save failure"

    .line 589
    invoke-static {v0, p1}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V

    goto :goto_1

    .line 591
    :cond_1
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "deviceinfo:  Successful:"

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-static {v0, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_0

    goto :goto_1

    :catch_0
    move-exception p1

    .line 594
    invoke-virtual {p1}, Ljava/lang/Exception;->printStackTrace()V

    .line 595
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "updateDevicesListData:  "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p1}, Ljava/lang/Exception;->getMessage()Ljava/lang/String;

    move-result-object p1

    invoke-virtual {v1, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-static {v0, p1}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V

    :goto_1
    return-void
.end method
