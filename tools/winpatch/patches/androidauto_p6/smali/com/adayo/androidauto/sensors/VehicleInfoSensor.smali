.class public Lcom/adayo/androidauto/sensors/VehicleInfoSensor;
.super Lcom/google/android/projection/sink/sensors/SensorCollectorBase;
.source "VehicleInfoSensor.java"


# static fields
.field private static final PARKING_OFF:I = 0x0

.field private static final PARKING_ON:I = 0x1

.field private static final TAG:Ljava/lang/String; = "AHU.ANDROIDAUTO_TAG"

.field private static mCarDapter:Lcom/adayo/androidauto/CarAdapter;

.field private static mCarPropertyManager:Landroid/car/hardware/property/CarPropertyManager;

.field private static mVehicleInfoSensor:Lcom/adayo/androidauto/sensors/VehicleInfoSensor;


# instance fields
.field private avmIsFrontShareDataListener:Lcom/adayo/proxy/share/interfaces/IShareDataListener;

.field private final carPropertyEventBCMListener:Landroid/car/hardware/property/CarPropertyManager$CarPropertyEventListener;

.field private gear:I

.field private mCar:Landroid/car/Car;

.field private mCarPropertyIds:Ljava/util/List;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/List<",
            "Ljava/lang/Integer;",
            ">;"
        }
    .end annotation
.end field

.field private final mConnection:Landroid/content/ServiceConnection;

.field private mIsNightMode:Z

.field private mParkBreakEngaged:Z

.field private mSourceInfoShareDataListener:Lcom/adayo/proxy/share/interfaces/IShareDataListener;

.field private vehicleSpeed:I


# direct methods
.method static constructor <clinit>()V
    .locals 0

    return-void
.end method

.method private constructor <init>()V
    .locals 3

    .line 62
    invoke-direct {p0}, Lcom/google/android/projection/sink/sensors/SensorCollectorBase;-><init>()V

    const/4 v0, 0x1

    .line 55
    iput-boolean v0, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mParkBreakEngaged:Z

    const/4 v0, 0x0

    .line 56
    iput-boolean v0, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mIsNightMode:Z

    .line 58
    iput v0, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->vehicleSpeed:I

    .line 59
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    iput-object v0, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mCarPropertyIds:Ljava/util/List;

    .line 241
    new-instance v0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor$3;

    invoke-direct {v0, p0}, Lcom/adayo/androidauto/sensors/VehicleInfoSensor$3;-><init>(Lcom/adayo/androidauto/sensors/VehicleInfoSensor;)V

    iput-object v0, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mSourceInfoShareDataListener:Lcom/adayo/proxy/share/interfaces/IShareDataListener;

    .line 274
    new-instance v0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor$4;

    invoke-direct {v0, p0}, Lcom/adayo/androidauto/sensors/VehicleInfoSensor$4;-><init>(Lcom/adayo/androidauto/sensors/VehicleInfoSensor;)V

    iput-object v0, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->avmIsFrontShareDataListener:Lcom/adayo/proxy/share/interfaces/IShareDataListener;

    const/4 v0, -0x1

    .line 305
    iput v0, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->gear:I

    .line 339
    new-instance v0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor$5;

    invoke-direct {v0, p0}, Lcom/adayo/androidauto/sensors/VehicleInfoSensor$5;-><init>(Lcom/adayo/androidauto/sensors/VehicleInfoSensor;)V

    iput-object v0, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->carPropertyEventBCMListener:Landroid/car/hardware/property/CarPropertyManager$CarPropertyEventListener;

    .line 374
    new-instance v0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor$6;

    invoke-direct {v0, p0}, Lcom/adayo/androidauto/sensors/VehicleInfoSensor$6;-><init>(Lcom/adayo/androidauto/sensors/VehicleInfoSensor;)V

    iput-object v0, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mConnection:Landroid/content/ServiceConnection;

    .line 63
    invoke-static {}, Lcom/adayo/androidauto/config/LoadJson;->getInstance()Lcom/adayo/androidauto/config/LoadJson;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Information;->getHeadunitModel()Ljava/lang/String;

    move-result-object v0

    .line 64
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "init headunitModel="

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    const-string v2, "AHU.ANDROIDAUTO_TAG"

    invoke-static {v2, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    const-string v1, "HMA228"

    .line 65
    invoke-virtual {v0, v1}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v1

    if-nez v1, :cond_0

    const-string v1, "HMA229"

    .line 66
    invoke-virtual {v0, v1}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v1

    if-nez v1, :cond_0

    const-string v1, "HMA214"

    .line 67
    invoke-virtual {v0, v1}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v1

    if-nez v1, :cond_0

    const-string v1, "HMA220"

    .line 68
    invoke-virtual {v0, v1}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v0

    if-eqz v0, :cond_1

    .line 69
    :cond_0
    invoke-direct {p0}, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->connectService()V

    :cond_1
    return-void
.end method

.method static synthetic access$000(Lcom/adayo/androidauto/sensors/VehicleInfoSensor;)I
    .locals 0

    .line 49
    iget p0, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->vehicleSpeed:I

    return p0
.end method

.method static synthetic access$002(Lcom/adayo/androidauto/sensors/VehicleInfoSensor;I)I
    .locals 0

    .line 49
    iput p1, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->vehicleSpeed:I

    return p1
.end method

.method static synthetic access$100(Lcom/adayo/androidauto/sensors/VehicleInfoSensor;)I
    .locals 0

    .line 49
    iget p0, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->gear:I

    return p0
.end method

.method static synthetic access$102(Lcom/adayo/androidauto/sensors/VehicleInfoSensor;I)I
    .locals 0

    .line 49
    iput p1, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->gear:I

    return p1
.end method

.method static synthetic access$200(Lcom/adayo/androidauto/sensors/VehicleInfoSensor;)Landroid/car/Car;
    .locals 0

    .line 49
    iget-object p0, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mCar:Landroid/car/Car;

    return-object p0
.end method

.method static synthetic access$300()Landroid/car/hardware/property/CarPropertyManager;
    .locals 1

    .line 49
    sget-object v0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mCarPropertyManager:Landroid/car/hardware/property/CarPropertyManager;

    return-object v0
.end method

.method static synthetic access$302(Landroid/car/hardware/property/CarPropertyManager;)Landroid/car/hardware/property/CarPropertyManager;
    .locals 0

    .line 49
    sput-object p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mCarPropertyManager:Landroid/car/hardware/property/CarPropertyManager;

    return-object p0
.end method

.method static synthetic access$400(Lcom/adayo/androidauto/sensors/VehicleInfoSensor;)V
    .locals 0

    .line 49
    invoke-direct {p0}, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->registerCarListener()V

    return-void
.end method

.method static synthetic access$500(Lcom/adayo/androidauto/sensors/VehicleInfoSensor;)V
    .locals 0

    .line 49
    invoke-direct {p0}, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->connectService()V

    return-void
.end method

.method private connectService()V
    .locals 2

    const-string v0, "AHU.ANDROIDAUTO_TAG"

    const-string v1, "connectService"

    .line 403
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 405
    :try_start_0
    iget-object v0, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mCar:Landroid/car/Car;

    if-eqz v0, :cond_0

    .line 406
    iget-object v0, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mCar:Landroid/car/Car;

    invoke-virtual {v0}, Landroid/car/Car;->disconnect()V

    .line 408
    :cond_0
    invoke-static {}, Lcom/adayo/proxy/share/utils/ContextUtil;->getContext()Landroid/content/Context;

    move-result-object v0

    iget-object v1, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mConnection:Landroid/content/ServiceConnection;

    invoke-static {v0, v1}, Landroid/car/Car;->createCar(Landroid/content/Context;Landroid/content/ServiceConnection;)Landroid/car/Car;

    move-result-object v0

    iput-object v0, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mCar:Landroid/car/Car;

    .line 409
    invoke-virtual {v0}, Landroid/car/Car;->connect()V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception v0

    .line 411
    invoke-virtual {v0}, Ljava/lang/Exception;->printStackTrace()V

    :goto_0
    return-void
.end method

.method public static getInstance()Lcom/adayo/androidauto/sensors/VehicleInfoSensor;
    .locals 2

    .line 74
    sget-object v0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mVehicleInfoSensor:Lcom/adayo/androidauto/sensors/VehicleInfoSensor;

    if-nez v0, :cond_1

    .line 75
    const-class v0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;

    monitor-enter v0

    .line 76
    :try_start_0
    sget-object v1, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mVehicleInfoSensor:Lcom/adayo/androidauto/sensors/VehicleInfoSensor;

    if-nez v1, :cond_0

    .line 77
    new-instance v1, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;

    invoke-direct {v1}, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;-><init>()V

    sput-object v1, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mVehicleInfoSensor:Lcom/adayo/androidauto/sensors/VehicleInfoSensor;

    .line 78
    invoke-static {}, Lcom/adayo/androidauto/CarAdapter;->getInstance()Lcom/adayo/androidauto/CarAdapter;

    move-result-object v1

    sput-object v1, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mCarDapter:Lcom/adayo/androidauto/CarAdapter;

    .line 80
    :cond_0
    monitor-exit v0

    goto :goto_0

    :catchall_0
    move-exception v1

    monitor-exit v0
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    throw v1

    .line 82
    :cond_1
    :goto_0
    sget-object v0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mVehicleInfoSensor:Lcom/adayo/androidauto/sensors/VehicleInfoSensor;

    return-object v0
.end method

.method private registerCarListener()V
    .locals 5

    .line 308
    invoke-virtual {p0}, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->initSignalInfor()V

    .line 309
    iget-object v0, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mCarPropertyIds:Ljava/util/List;

    invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :goto_0
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_0

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Ljava/lang/Integer;

    .line 311
    :try_start_0
    sget-object v2, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mCarPropertyManager:Landroid/car/hardware/property/CarPropertyManager;

    iget-object v3, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->carPropertyEventBCMListener:Landroid/car/hardware/property/CarPropertyManager$CarPropertyEventListener;

    invoke-virtual {v1}, Ljava/lang/Integer;->intValue()I

    move-result v1

    const/4 v4, 0x0

    invoke-virtual {v2, v3, v1, v4}, Landroid/car/hardware/property/CarPropertyManager;->registerListener(Landroid/car/hardware/property/CarPropertyManager$CarPropertyEventListener;IF)Z
    :try_end_0
    .catch Landroid/car/CarNotConnectedException; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception v1

    .line 313
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, " mCarPropertyManager is null :"

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Landroid/car/CarNotConnectedException;->toString()Ljava/lang/String;

    move-result-object v3

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    const-string v3, "AHU.ANDROIDAUTO_TAG"

    invoke-static {v3, v2}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 314
    invoke-virtual {v1}, Landroid/car/CarNotConnectedException;->printStackTrace()V

    goto :goto_0

    :cond_0
    return-void
.end method


# virtual methods
.method protected discoverSensorsImpl(Ljava/util/Set;)V
    .locals 1
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/util/Set<",
            "Ljava/lang/Integer;",
            ">;)V"
        }
    .end annotation

    const/16 v0, 0xd

    .line 180
    invoke-static {v0}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v0

    invoke-interface {p1, v0}, Ljava/util/Set;->add(Ljava/lang/Object;)Z

    const/16 v0, 0xa

    .line 181
    invoke-static {v0}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v0

    invoke-interface {p1, v0}, Ljava/util/Set;->add(Ljava/lang/Object;)Z

    const/4 v0, 0x7

    .line 182
    invoke-static {v0}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v0

    invoke-interface {p1, v0}, Ljava/util/Set;->add(Ljava/lang/Object;)Z

    return-void
.end method

.method public getCarPropertyInt(I)I
    .locals 3

    const/16 v0, 0xff

    .line 418
    :try_start_0
    sget-object v1, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mCarPropertyManager:Landroid/car/hardware/property/CarPropertyManager;

    if-nez v1, :cond_0

    return v0

    .line 421
    :cond_0
    sget-object v1, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mCarPropertyManager:Landroid/car/hardware/property/CarPropertyManager;

    const/4 v2, 0x0

    invoke-virtual {v1, p1, v2}, Landroid/car/hardware/property/CarPropertyManager;->getIntProperty(II)I

    move-result v0
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception p1

    .line 423
    invoke-virtual {p1}, Ljava/lang/Exception;->printStackTrace()V

    :goto_0
    return v0
.end method

.method public init(Landroid/content/Context;)V
    .locals 9

    .line 88
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

    .line 90
    invoke-static {}, Lcom/adayo/proxy/share/ShareDataManager;->getShareDataManager()Lcom/adayo/proxy/share/ShareDataManager;

    move-result-object v0

    const/16 v2, 0xe

    iget-object v3, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mSourceInfoShareDataListener:Lcom/adayo/proxy/share/interfaces/IShareDataListener;

    invoke-virtual {v0, v2, v3}, Lcom/adayo/proxy/share/ShareDataManager;->registerShareDataListener(ILcom/adayo/proxy/share/interfaces/IShareDataListener;)Z

    goto :goto_0

    .line 92
    :cond_0
    invoke-static {}, Lcom/adayo/proxy/share/ShareDataManager;->getShareDataManager()Lcom/adayo/proxy/share/ShareDataManager;

    move-result-object v0

    const/16 v2, 0x2766

    iget-object v3, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->avmIsFrontShareDataListener:Lcom/adayo/proxy/share/interfaces/IShareDataListener;

    invoke-virtual {v0, v2, v3}, Lcom/adayo/proxy/share/ShareDataManager;->registerShareDataListener(ILcom/adayo/proxy/share/interfaces/IShareDataListener;)Z

    .line 110
    :goto_0
    sget-object v0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mCarDapter:Lcom/adayo/androidauto/CarAdapter;

    invoke-virtual {v0}, Lcom/adayo/androidauto/CarAdapter;->isBigLight()Z

    move-result v0

    .line 111
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "light mode "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    const-string v3, "AHU.ANDROIDAUTO_TAG"

    invoke-static {v3, v2}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    const/4 v2, 0x0

    const/4 v4, 0x1

    if-eqz v0, :cond_1

    .line 113
    invoke-virtual {p0, v4}, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->onHeadlampStatusChanged(Z)V

    goto :goto_1

    .line 115
    :cond_1
    invoke-virtual {p0, v2}, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->onHeadlampStatusChanged(Z)V

    .line 120
    :goto_1
    invoke-static {}, Lcom/adayo/androidauto/config/LoadJson;->getInstance()Lcom/adayo/androidauto/config/LoadJson;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Information;->getHeadunitModel()Ljava/lang/String;

    move-result-object v0

    const-string v5, "HMA214"

    invoke-virtual {v0, v5}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v0

    if-nez v0, :cond_2

    .line 121
    invoke-static {}, Lcom/adayo/androidauto/config/LoadJson;->getInstance()Lcom/adayo/androidauto/config/LoadJson;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/config/bean/Information;->getHeadunitModel()Ljava/lang/String;

    move-result-object v0

    const-string v5, "HMA231"

    invoke-virtual {v0, v5}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v0

    if-eqz v0, :cond_5

    :cond_2
    :try_start_0
    const-string v0, "adayo_skin"

    .line 124
    invoke-virtual {p1}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v5

    const/4 v6, 0x2

    invoke-static {v5, v0, v6}, Landroid/provider/Settings$Global;->getInt(Landroid/content/ContentResolver;Ljava/lang/String;I)I

    move-result v5

    if-ne v5, v4, :cond_3

    .line 126
    invoke-virtual {p0, v2}, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->onHeadlampStatusChanged(Z)V

    goto :goto_2

    :cond_3
    if-ne v5, v6, :cond_4

    .line 128
    invoke-virtual {p0, v4}, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->onHeadlampStatusChanged(Z)V

    .line 130
    :cond_4
    :goto_2
    new-instance v6, Ljava/lang/StringBuilder;

    invoke-direct {v6}, Ljava/lang/StringBuilder;-><init>()V

    const-string v7, "skin"

    invoke-virtual {v6, v7}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v6, v5}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v6}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v5

    invoke-static {v3, v5}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 131
    invoke-static {v0}, Landroid/provider/Settings$Global;->getUriFor(Ljava/lang/String;)Landroid/net/Uri;

    move-result-object v5

    .line 132
    invoke-virtual {p1}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v6

    new-instance v7, Lcom/adayo/androidauto/sensors/VehicleInfoSensor$1;

    new-instance v8, Landroid/os/Handler;

    invoke-direct {v8}, Landroid/os/Handler;-><init>()V

    invoke-direct {v7, p0, v8, p1, v0}, Lcom/adayo/androidauto/sensors/VehicleInfoSensor$1;-><init>(Lcom/adayo/androidauto/sensors/VehicleInfoSensor;Landroid/os/Handler;Landroid/content/Context;Ljava/lang/String;)V

    invoke-virtual {v6, v5, v2, v7}, Landroid/content/ContentResolver;->registerContentObserver(Landroid/net/Uri;ZLandroid/database/ContentObserver;)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_3

    :catch_0
    move-exception p1

    .line 147
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "onChange Exception: "

    invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p1}, Ljava/lang/Exception;->getMessage()Ljava/lang/String;

    move-result-object p1

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-static {v3, p1}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V

    .line 151
    :cond_5
    :goto_3
    invoke-static {}, Lcom/adayo/androidauto/config/LoadJson;->getInstance()Lcom/adayo/androidauto/config/LoadJson;

    move-result-object p1

    invoke-virtual {p1}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object p1

    invoke-virtual {p1}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object p1

    invoke-virtual {p1}, Lcom/adayo/androidauto/config/bean/Information;->getHeadunitModel()Ljava/lang/String;

    move-result-object p1

    invoke-virtual {p1, v1}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result p1

    if-eqz p1, :cond_6

    .line 152
    invoke-static {}, Lcom/adayo/proxy/share/utils/ContextUtil;->getContext()Landroid/content/Context;

    move-result-object p1

    invoke-static {p1}, Lcom/adayo/proxy/McuProxy;->getInstance(Landroid/content/Context;)Lcom/adayo/proxy/McuProxy;

    move-result-object p1

    new-instance v0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor$2;

    invoke-direct {v0, p0}, Lcom/adayo/androidauto/sensors/VehicleInfoSensor$2;-><init>(Lcom/adayo/androidauto/sensors/VehicleInfoSensor;)V

    invoke-virtual {p1, v0, v4}, Lcom/adayo/proxy/McuProxy;->registerListener(Lcom/adayo/proxy/ProxyCallback;Z)V

    :cond_6
    return-void
.end method

.method public initSignalInfor()V
    .locals 2

    .line 334
    iget-object v0, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mCarPropertyIds:Ljava/util/List;

    const v1, 0x11401f00

    invoke-static {v1}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v1

    invoke-interface {v0, v1}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    .line 335
    iget-object v0, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mCarPropertyIds:Ljava/util/List;

    const v1, 0x21400c2b

    invoke-static {v1}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v1

    invoke-interface {v0, v1}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    .line 336
    iget-object v0, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mCarPropertyIds:Ljava/util/List;

    const v1, 0x21400c26

    invoke-static {v1}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v1

    invoke-interface {v0, v1}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    return-void
.end method

.method public onDrivingStatusChanged()V
    .locals 2

    const-string v0, "AHU.ANDROIDAUTO_TAG"

    const-string v1, "onDrivingStatusChanged "

    .line 225
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    const/16 v0, 0xd

    .line 226
    invoke-virtual {p0, v0}, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->reportAvailable(I)V

    return-void
.end method

.method public onHeadlampStatusChanged(Z)V
    .locals 0

    .line 236
    iput-boolean p1, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mIsNightMode:Z

    const/16 p1, 0xa

    .line 237
    invoke-virtual {p0, p1}, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->reportAvailable(I)V

    return-void
.end method

.method public onParkBreakStatusChanged()V
    .locals 2

    .line 230
    const/4 v0, 0x1

    iput-boolean v0, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mParkBreakEngaged:Z

    .line 231
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "onParkBreakStatusChanged "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget v1, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->vehicleSpeed:I

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    const-string v1, "AHU.ANDROIDAUTO_TAG"

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    const/4 v0, 0x7

    .line 232
    invoke-virtual {p0, v0}, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->reportAvailable(I)V

    return-void
.end method

.method protected registerSensorsImpl()V
    .locals 0

    return-void
.end method

.method protected sendUpdateImpl(Lcom/google/android/projection/sink/sensors/SensorSinkWrapper;I)Z
    .locals 3

    const-string v0, "AHU.ANDROIDAUTO_TAG"

    const/4 v1, 0x0

    if-nez p1, :cond_0

    const-string p1, "sendUpdateImpl sink is null"

    .line 201
    invoke-static {v0, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    return v1

    :cond_0
    const/16 v2, 0xa

    if-ne p2, v2, :cond_1

    .line 205
    iget-boolean p2, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mIsNightMode:Z

    invoke-virtual {p1, p2}, Lcom/google/android/projection/sink/sensors/SensorSinkWrapper;->reportNightModeData(Z)V

    goto :goto_0

    :cond_1
    const/4 v2, 0x7

    if-ne p2, v2, :cond_2

    .line 207
    iget-boolean p2, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mParkBreakEngaged:Z

    invoke-virtual {p1, p2}, Lcom/google/android/projection/sink/sensors/SensorSinkWrapper;->reportParkingBrakeData(Z)V

    goto :goto_0

    :cond_2
    const/16 v2, 0xd

    if-ne p2, v2, :cond_4

    .line 209
    iget-boolean p2, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mParkBreakEngaged:Z

    if-nez p2, :cond_3

    .line 210
    sget-object p2, Lcom/google/android/projection/proto/Protos$DrivingStatus;->DRIVE_STATUS_NO_VIDEO:Lcom/google/android/projection/proto/Protos$DrivingStatus;

    invoke-virtual {p2}, Lcom/google/android/projection/proto/Protos$DrivingStatus;->getNumber()I

    move-result p2

    sget-object v0, Lcom/google/android/projection/proto/Protos$DrivingStatus;->DRIVE_STATUS_NO_KEYBOARD_INPUT:Lcom/google/android/projection/proto/Protos$DrivingStatus;

    .line 211
    invoke-virtual {v0}, Lcom/google/android/projection/proto/Protos$DrivingStatus;->getNumber()I

    move-result v0

    or-int/2addr p2, v0

    sget-object v0, Lcom/google/android/projection/proto/Protos$DrivingStatus;->DRIVE_STATUS_NO_CONFIG:Lcom/google/android/projection/proto/Protos$DrivingStatus;

    .line 212
    invoke-virtual {v0}, Lcom/google/android/projection/proto/Protos$DrivingStatus;->getNumber()I

    move-result v0

    or-int/2addr p2, v0

    .line 213
    invoke-virtual {p1, p2}, Lcom/google/android/projection/sink/sensors/SensorSinkWrapper;->reportDrivingStatusData(I)V

    goto :goto_0

    .line 215
    :cond_3
    invoke-virtual {p1, v1}, Lcom/google/android/projection/sink/sensors/SensorSinkWrapper;->reportDrivingStatusData(I)V

    :goto_0
    const/4 p1, 0x1

    return p1

    .line 218
    :cond_4
    new-instance p1, Ljava/lang/StringBuilder;

    invoke-direct {p1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "sendUpdateImpl:not impl type "

    invoke-virtual {p1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p1, p2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {p1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-static {v0, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    return v1
.end method

.method public unRegisterCarListener()V
    .locals 4

    const-string v0, "AHU.ANDROIDAUTO_TAG"

    const-string v1, " unRegisterCarListener "

    .line 320
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 321
    iget-object v0, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mCarPropertyIds:Ljava/util/List;

    invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :cond_0
    :goto_0
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_1

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Ljava/lang/Integer;

    .line 322
    sget-object v2, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->mCarPropertyManager:Landroid/car/hardware/property/CarPropertyManager;

    if-eqz v2, :cond_0

    .line 324
    :try_start_0
    iget-object v3, p0, Lcom/adayo/androidauto/sensors/VehicleInfoSensor;->carPropertyEventBCMListener:Landroid/car/hardware/property/CarPropertyManager$CarPropertyEventListener;

    invoke-virtual {v1}, Ljava/lang/Integer;->intValue()I

    move-result v1

    invoke-virtual {v2, v3, v1}, Landroid/car/hardware/property/CarPropertyManager;->unregisterListener(Landroid/car/hardware/property/CarPropertyManager$CarPropertyEventListener;I)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception v1

    .line 326
    invoke-virtual {v1}, Ljava/lang/Exception;->printStackTrace()V

    goto :goto_0

    :cond_1
    return-void
.end method

.method protected unregisterSensorsImpl()V
    .locals 0

    return-void
.end method
