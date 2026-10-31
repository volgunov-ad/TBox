.class public final Lcom/adayo/androidauto/video/AAProjectionRenderer;
.super Ljava/lang/Object;
.source "AAProjectionRenderer.java"


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/adayo/androidauto/video/AAProjectionRenderer$DecodeOutputThread;,
        Lcom/adayo/androidauto/video/AAProjectionRenderer$DecodeInputThread;,
        Lcom/adayo/androidauto/video/AAProjectionRenderer$Listener;
    }
.end annotation


# static fields
.field private static final DBG_TRACE:Z = true

.field private static final DEQUEUE_BUFFER_FAIL_MAX_RETRY:I = 0xa

.field private static final DEQUEUE_BUFFER_WAIT_TIME_US:I = 0x186a0

.field private static final ENABLE_DEBUG_LOG:Z = false

.field private static final MSG_RESTART:I = 0xc9

.field private static final RENDERER_STATE_NOT_STARTED:I = 0x1

.field private static final RENDERER_STATE_RUNNING:I = 0x2

.field private static final RENDERER_STATE_STOPPED:I = 0x3

.field private static final TAG:Ljava/lang/String; = "AHU.VIDEO"


# instance fields
.field public isKeyFrameData:Z

.field private mCodec:Landroid/media/MediaCodec;

.field private mConfigData:[B

.field private mEmulatedScreen:Lcom/google/android/projection/sink/video/DimensionUtils$ScreenSettings;

.field public mFrameCount:I

.field private mInputBuffers:[Ljava/nio/ByteBuffer;

.field private mInputThread:Lcom/adayo/androidauto/video/AAProjectionRenderer$DecodeInputThread;

.field private volatile mIsInited:Z

.field private mLayoutChangeOngoing:Z

.field private mListener:Lcom/adayo/androidauto/video/AAProjectionRenderer$Listener;

.field private mOutputThread:Lcom/adayo/androidauto/video/AAProjectionRenderer$DecodeOutputThread;

.field private final mPendingInputBuffers:Ljava/util/concurrent/LinkedBlockingQueue;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/concurrent/LinkedBlockingQueue<",
            "Lcom/google/android/projection/protocol/VideoFrame;",
            ">;"
        }
    .end annotation
.end field

.field private final mQueuedInputBuffers:Ljava/util/concurrent/LinkedBlockingQueue;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/concurrent/LinkedBlockingQueue<",
            "Lcom/google/android/projection/protocol/VideoFrame;",
            ">;"
        }
    .end annotation
.end field

.field private volatile mState:I

.field mSubHandler:Landroid/os/Handler;

.field private mSurface:Landroid/view/Surface;

.field private final mSurfaceCallback:Landroid/view/SurfaceHolder$Callback;

.field private final mSurfaceFrame:Landroid/graphics/Rect;

.field private mSurfaceView:Landroid/view/SurfaceView;

.field private mTouchXScale:F

.field private mTouchYScale:F

.field private mVideoSize:Landroid/graphics/Rect;

.field private xMargin:I

.field private yMargin:I


# direct methods
.method public constructor <init>()V
    .locals 3

    .line 102
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    const/4 v0, 0x1

    .line 69
    iput v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mState:I

    .line 71
    new-instance v1, Landroid/graphics/Rect;

    invoke-direct {v1}, Landroid/graphics/Rect;-><init>()V

    iput-object v1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceFrame:Landroid/graphics/Rect;

    const/high16 v1, 0x3f800000    # 1.0f

    .line 74
    iput v1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mTouchXScale:F

    .line 75
    iput v1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mTouchYScale:F

    const/4 v1, 0x0

    .line 76
    iput v1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->xMargin:I

    .line 77
    iput v1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->yMargin:I

    .line 78
    iput-boolean v1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mLayoutChangeOngoing:Z

    .line 86
    new-instance v2, Ljava/util/concurrent/LinkedBlockingQueue;

    invoke-direct {v2}, Ljava/util/concurrent/LinkedBlockingQueue;-><init>()V

    iput-object v2, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mPendingInputBuffers:Ljava/util/concurrent/LinkedBlockingQueue;

    .line 92
    new-instance v2, Ljava/util/concurrent/LinkedBlockingQueue;

    invoke-direct {v2}, Ljava/util/concurrent/LinkedBlockingQueue;-><init>()V

    iput-object v2, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mQueuedInputBuffers:Ljava/util/concurrent/LinkedBlockingQueue;

    .line 98
    iput-boolean v1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mIsInited:Z

    const/4 v2, 0x0

    .line 99
    iput-object v2, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mConfigData:[B

    .line 496
    new-instance v2, Lcom/adayo/androidauto/video/AAProjectionRenderer$1;

    invoke-direct {v2, p0}, Lcom/adayo/androidauto/video/AAProjectionRenderer$1;-><init>(Lcom/adayo/androidauto/video/AAProjectionRenderer;)V

    iput-object v2, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSubHandler:Landroid/os/Handler;

    .line 530
    iput v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mFrameCount:I

    .line 531
    iput-boolean v1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->isKeyFrameData:Z

    .line 803
    new-instance v0, Lcom/adayo/androidauto/video/AAProjectionRenderer$2;

    invoke-direct {v0, p0}, Lcom/adayo/androidauto/video/AAProjectionRenderer$2;-><init>(Lcom/adayo/androidauto/video/AAProjectionRenderer;)V

    iput-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceCallback:Landroid/view/SurfaceHolder$Callback;

    const-string v0, "AHU.VIDEO"

    const-string v1, "AAProjectionRenderer construct"

    .line 103
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    return-void
.end method

.method static synthetic access$000(Lcom/adayo/androidauto/video/AAProjectionRenderer;)Ljava/util/concurrent/LinkedBlockingQueue;
    .locals 0

    .line 38
    iget-object p0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mPendingInputBuffers:Ljava/util/concurrent/LinkedBlockingQueue;

    return-object p0
.end method

.method static synthetic access$100(Lcom/adayo/androidauto/video/AAProjectionRenderer;)Landroid/media/MediaCodec;
    .locals 0

    .line 38
    iget-object p0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mCodec:Landroid/media/MediaCodec;

    return-object p0
.end method

.method static synthetic access$200(Lcom/adayo/androidauto/video/AAProjectionRenderer;)V
    .locals 0

    .line 38
    invoke-direct {p0}, Lcom/adayo/androidauto/video/AAProjectionRenderer;->ackFrame()V

    return-void
.end method

.method static synthetic access$300(Lcom/adayo/androidauto/video/AAProjectionRenderer;)[Ljava/nio/ByteBuffer;
    .locals 0

    .line 38
    iget-object p0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mInputBuffers:[Ljava/nio/ByteBuffer;

    return-object p0
.end method

.method static synthetic access$400(Lcom/adayo/androidauto/video/AAProjectionRenderer;[B)V
    .locals 0

    .line 38
    invoke-direct {p0, p1}, Lcom/adayo/androidauto/video/AAProjectionRenderer;->getKeyFrameData([B)V

    return-void
.end method

.method static synthetic access$500(Lcom/adayo/androidauto/video/AAProjectionRenderer;)Ljava/util/concurrent/LinkedBlockingQueue;
    .locals 0

    .line 38
    iget-object p0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mQueuedInputBuffers:Ljava/util/concurrent/LinkedBlockingQueue;

    return-object p0
.end method

.method static synthetic access$600(Lcom/adayo/androidauto/video/AAProjectionRenderer;Landroid/view/Surface;II)V
    .locals 0

    .line 38
    invoke-direct {p0, p1, p2, p3}, Lcom/adayo/androidauto/video/AAProjectionRenderer;->updateSurface(Landroid/view/Surface;II)V

    return-void
.end method

.method static synthetic access$700(Lcom/adayo/androidauto/video/AAProjectionRenderer;)Lcom/adayo/androidauto/video/AAProjectionRenderer$Listener;
    .locals 0

    .line 38
    iget-object p0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mListener:Lcom/adayo/androidauto/video/AAProjectionRenderer$Listener;

    return-object p0
.end method

.method static synthetic access$802(Lcom/adayo/androidauto/video/AAProjectionRenderer;Landroid/view/Surface;)Landroid/view/Surface;
    .locals 0

    .line 38
    iput-object p1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurface:Landroid/view/Surface;

    return-object p1
.end method

.method static synthetic access$900(Lcom/adayo/androidauto/video/AAProjectionRenderer;)V
    .locals 0

    .line 38
    invoke-direct {p0}, Lcom/adayo/androidauto/video/AAProjectionRenderer;->updateCodec()V

    return-void
.end method

.method private ackFrame()V
    .locals 2

    .line 515
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mQueuedInputBuffers:Ljava/util/concurrent/LinkedBlockingQueue;

    invoke-virtual {v0}, Ljava/util/concurrent/LinkedBlockingQueue;->poll()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Lcom/google/android/projection/protocol/VideoFrame;

    if-eqz v0, :cond_0

    .line 517
    iget-object v1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mListener:Lcom/adayo/androidauto/video/AAProjectionRenderer$Listener;

    iget v0, v0, Lcom/google/android/projection/protocol/VideoFrame;->sessionId:I

    invoke-interface {v1, v0}, Lcom/adayo/androidauto/video/AAProjectionRenderer$Listener;->onFrameRendered(I)V

    :cond_0
    return-void
.end method

.method private codecShouldBeRunning()Z
    .locals 2

    .line 329
    iget v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mState:I

    const/4 v1, 0x2

    if-ne v0, v1, :cond_0

    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurface:Landroid/view/Surface;

    if-eqz v0, :cond_0

    const/4 v0, 0x1

    goto :goto_0

    :cond_0
    const/4 v0, 0x0

    :goto_0
    return v0
.end method

.method private getKeyFrameData([B)V
    .locals 14

    .line 534
    iget v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mFrameCount:I

    const/4 v1, 0x1

    if-ne v0, v1, :cond_2

    add-int/2addr v0, v1

    .line 536
    iput v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mFrameCount:I

    if-eqz p1, :cond_2

    .line 538
    array-length v0, p1

    if-lez v0, :cond_2

    const/4 v0, 0x0

    .line 539
    aget-byte v2, p1, v0

    and-int/lit16 v2, v2, 0xff

    invoke-static {v2}, Ljava/lang/Integer;->toHexString(I)Ljava/lang/String;

    move-result-object v2

    .line 540
    aget-byte v3, p1, v1

    and-int/lit16 v3, v3, 0xff

    invoke-static {v3}, Ljava/lang/Integer;->toHexString(I)Ljava/lang/String;

    move-result-object v3

    const/4 v4, 0x2

    .line 541
    aget-byte v4, p1, v4

    and-int/lit16 v4, v4, 0xff

    invoke-static {v4}, Ljava/lang/Integer;->toHexString(I)Ljava/lang/String;

    move-result-object v4

    const/4 v5, 0x3

    .line 542
    aget-byte v5, p1, v5

    and-int/lit16 v5, v5, 0xff

    invoke-static {v5}, Ljava/lang/Integer;->toHexString(I)Ljava/lang/String;

    move-result-object v5

    const/4 v6, 0x4

    .line 543
    aget-byte v6, p1, v6

    and-int/lit16 v6, v6, 0xff

    invoke-static {v6}, Ljava/lang/Integer;->toHexString(I)Ljava/lang/String;

    move-result-object v6

    const/4 v7, 0x5

    .line 545
    aget-byte v7, p1, v7

    and-int/lit16 v7, v7, 0xff

    invoke-static {v7}, Ljava/lang/Integer;->toHexString(I)Ljava/lang/String;

    move-result-object v7

    const/4 v8, 0x6

    .line 546
    aget-byte v8, p1, v8

    and-int/lit16 v8, v8, 0xff

    invoke-static {v8}, Ljava/lang/Integer;->toHexString(I)Ljava/lang/String;

    move-result-object v8

    const/4 v9, 0x7

    .line 547
    aget-byte v9, p1, v9

    and-int/lit16 v9, v9, 0xff

    invoke-static {v9}, Ljava/lang/Integer;->toHexString(I)Ljava/lang/String;

    move-result-object v9

    const/16 v10, 0x8

    .line 548
    aget-byte v10, p1, v10

    and-int/lit16 v10, v10, 0xff

    invoke-static {v10}, Ljava/lang/Integer;->toHexString(I)Ljava/lang/String;

    move-result-object v10

    const/16 v11, 0x9

    .line 549
    aget-byte p1, p1, v11

    and-int/lit16 p1, p1, 0xff

    invoke-static {p1}, Ljava/lang/Integer;->toHexString(I)Ljava/lang/String;

    move-result-object p1

    .line 551
    new-instance v11, Ljava/lang/StringBuilder;

    invoke-direct {v11}, Ljava/lang/StringBuilder;-><init>()V

    const-string v12, "isCurrFrameSpsPps_num1-5 -- "

    invoke-virtual {v11, v12}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget v12, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mFrameCount:I

    invoke-virtual {v11, v12}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v12, " num1:"

    invoke-virtual {v11, v12}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v11, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v12, " num2:"

    invoke-virtual {v11, v12}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v11, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v12, " num3:"

    invoke-virtual {v11, v12}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v11, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v12, " num4:"

    invoke-virtual {v11, v12}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v11, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v12, " num5:"

    invoke-virtual {v11, v12}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v11, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v11}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v11

    const-string v12, "AHU.VIDEO"

    invoke-static {v12, v11}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 552
    new-instance v11, Ljava/lang/StringBuilder;

    invoke-direct {v11}, Ljava/lang/StringBuilder;-><init>()V

    const-string v13, "isCurrFrameSpsPps_num6-10 -- "

    invoke-virtual {v11, v13}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget v13, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mFrameCount:I

    invoke-virtual {v11, v13}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v13, " num6:"

    invoke-virtual {v11, v13}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v11, v7}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v13, " num7:"

    invoke-virtual {v11, v13}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v11, v8}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v13, " num8:"

    invoke-virtual {v11, v13}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v11, v9}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v13, " num9:"

    invoke-virtual {v11, v13}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v11, v10}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v13, " num10:"

    invoke-virtual {v11, v13}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v11, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v11}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-static {v12, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    const-string p1, "0"

    .line 555
    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    const-string v11, "67"

    const-string v13, "1"

    if-eqz v2, :cond_0

    invoke-virtual {p1, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_0

    invoke-virtual {p1, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_0

    invoke-virtual {v13, v5}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_0

    invoke-virtual {v11, v6}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_0

    .line 556
    iput-boolean v1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->isKeyFrameData:Z

    const-string p1, "isCurrFrameSpsPps 1..."

    .line 557
    invoke-static {v12, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    goto :goto_0

    .line 558
    :cond_0
    invoke-virtual {p1, v7}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_1

    invoke-virtual {p1, v8}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-eqz p1, :cond_1

    invoke-virtual {v13, v9}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-eqz p1, :cond_1

    invoke-virtual {v11, v10}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-eqz p1, :cond_1

    .line 560
    iput-boolean v1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->isKeyFrameData:Z

    const-string p1, "isCurrFrameSpsPps 2..."

    .line 561
    invoke-static {v12, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    goto :goto_0

    .line 563
    :cond_1
    iput-boolean v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->isKeyFrameData:Z

    :cond_2
    :goto_0
    return-void
.end method

.method private declared-synchronized updateCodec()V
    .locals 11

    monitor-enter p0

    .line 356
    :try_start_0
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mCodec:Landroid/media/MediaCodec;

    const/4 v1, 0x0

    if-nez v0, :cond_7

    const-string v0, "AHU.VIDEO"

    .line 357
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "updateCodec1,mCodec start mState: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget v3, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mState:I

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v3, " mSurfaceFrame.right="

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v3, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceFrame:Landroid/graphics/Rect;

    iget v3, v3, Landroid/graphics/Rect;->right:I

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v3, " mSurfaceFrame.bottom="

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v3, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceFrame:Landroid/graphics/Rect;

    iget v3, v3, Landroid/graphics/Rect;->bottom:I

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v3, " mVideoSize="

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v3, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mVideoSize:Landroid/graphics/Rect;

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-static {v0, v2}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 358
    invoke-direct {p0}, Lcom/adayo/androidauto/video/AAProjectionRenderer;->codecShouldBeRunning()Z

    move-result v0

    if-eqz v0, :cond_a

    .line 360
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceFrame:Landroid/graphics/Rect;

    iget v0, v0, Landroid/graphics/Rect;->right:I

    iget-object v2, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceFrame:Landroid/graphics/Rect;

    iget v2, v2, Landroid/graphics/Rect;->bottom:I

    iget-object v3, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mVideoSize:Landroid/graphics/Rect;

    iget-object v4, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mEmulatedScreen:Lcom/google/android/projection/sink/video/DimensionUtils$ScreenSettings;

    iget-object v4, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceView:Landroid/view/SurfaceView;

    invoke-static {v4, v3}, Lcom/adayo/androidauto/patch/AAWindowPatch;->viewport(Landroid/view/SurfaceView;Landroid/graphics/Rect;)Landroid/graphics/Rect;

    move-result-object v0

    const-string v2, "AHU.VIDEO"

    .line 363
    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    const-string v4, "updateCodec2,mSurfaceFrame.right="

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v4, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceFrame:Landroid/graphics/Rect;

    iget v4, v4, Landroid/graphics/Rect;->right:I

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v4, " mSurfaceFrame.bottom="

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v4, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceFrame:Landroid/graphics/Rect;

    iget v4, v4, Landroid/graphics/Rect;->bottom:I

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v4, " mVideoSize.width="

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v4, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mVideoSize:Landroid/graphics/Rect;

    .line 364
    invoke-virtual {v4}, Landroid/graphics/Rect;->width()I

    move-result v4

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v4, " mSurfaceView.getWidth="

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v4, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceView:Landroid/view/SurfaceView;

    .line 365
    invoke-virtual {v4}, Landroid/view/SurfaceView;->getWidth()I

    move-result v4

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v4, " mSurfaceView.getHeight="

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v4, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceView:Landroid/view/SurfaceView;

    invoke-virtual {v4}, Landroid/view/SurfaceView;->getHeight()I

    move-result v4

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v4, " mVideoSize.height="

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v4, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mVideoSize:Landroid/graphics/Rect;

    .line 366
    invoke-virtual {v4}, Landroid/graphics/Rect;->height()I

    move-result v4

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v4, " mLayoutChangeOngoing="

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-boolean v4, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mLayoutChangeOngoing:Z

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    .line 363
    invoke-static {v2, v3}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    const-string v2, "AHU.VIDEO"

    .line 368
    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    const-string v4, "updateCodec2,viewport="

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    invoke-static {v2, v3}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 370
    iget-object v2, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceView:Landroid/view/SurfaceView;

    invoke-virtual {v2}, Landroid/view/SurfaceView;->getWidth()I

    move-result v2

    invoke-virtual {v0}, Landroid/graphics/Rect;->width()I

    move-result v3

    const/4 v4, 0x1

    if-ne v2, v3, :cond_0

    iget-object v2, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceView:Landroid/view/SurfaceView;

    .line 371
    invoke-virtual {v2}, Landroid/view/SurfaceView;->getHeight()I

    move-result v2

    invoke-virtual {v0}, Landroid/graphics/Rect;->height()I

    move-result v3

    if-eq v2, v3, :cond_1

    :cond_0
    iget-boolean v2, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mLayoutChangeOngoing:Z

    if-nez v2, :cond_1

    .line 374
    iget-object v1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceView:Landroid/view/SurfaceView;

    invoke-static {v1, v0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->applyViewport(Landroid/view/SurfaceView;Landroid/graphics/Rect;)V

    .line 388
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceView:Landroid/view/SurfaceView;

    invoke-virtual {v0}, Landroid/view/SurfaceView;->requestLayout()V

    const-string v0, "AHU.VIDEO"

    const-string v1, "updateCodec3,mSurfaceView size difference with viewport size,so call mSurfaceView.requestLayout to update,return!"

    .line 390
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 391
    iput-boolean v4, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mLayoutChangeOngoing:Z
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 392
    monitor-exit p0

    return-void

    :cond_1
    const/4 v0, 0x0

    .line 394
    :try_start_1
    iput-boolean v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mLayoutChangeOngoing:Z
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    :try_start_2
    const-string v2, "video/avc"

    .line 396
    invoke-static {v2}, Landroid/media/MediaCodec;->createDecoderByType(Ljava/lang/String;)Landroid/media/MediaCodec;

    move-result-object v2

    iput-object v2, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mCodec:Landroid/media/MediaCodec;

    const-string v2, "video/avc"

    .line 397
    iget-object v3, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mVideoSize:Landroid/graphics/Rect;

    .line 398
    invoke-virtual {v3}, Landroid/graphics/Rect;->width()I

    move-result v3

    iget-object v5, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mVideoSize:Landroid/graphics/Rect;

    invoke-virtual {v5}, Landroid/graphics/Rect;->height()I

    move-result v5

    .line 397
    invoke-static {v2, v3, v5}, Landroid/media/MediaFormat;->createVideoFormat(Ljava/lang/String;II)Landroid/media/MediaFormat;

    move-result-object v2

    const-string v3, "AHU.VIDEO"

    .line 399
    new-instance v5, Ljava/lang/StringBuilder;

    invoke-direct {v5}, Ljava/lang/StringBuilder;-><init>()V

    const-string v6, "updateCodec4,mVideoSize.right="

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v6, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mVideoSize:Landroid/graphics/Rect;

    iget v6, v6, Landroid/graphics/Rect;->right:I

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v6, " mSurfaceFrame.right="

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v6, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceFrame:Landroid/graphics/Rect;

    iget v6, v6, Landroid/graphics/Rect;->right:I

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v6, " mVideoSize.bottom="

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v6, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mVideoSize:Landroid/graphics/Rect;

    iget v6, v6, Landroid/graphics/Rect;->bottom:I

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v6, " mSurfaceFrame.bottom="

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v6, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceFrame:Landroid/graphics/Rect;

    iget v6, v6, Landroid/graphics/Rect;->bottom:I

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v5}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v5

    invoke-static {v3, v5}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 401
    invoke-static {}, Lcom/adayo/androidauto/config/LoadJson;->getInstance()Lcom/adayo/androidauto/config/LoadJson;

    move-result-object v3

    invoke-virtual {v3}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v3

    invoke-virtual {v3}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v3

    invoke-virtual {v3}, Lcom/adayo/androidauto/config/bean/Information;->getWidthPixel()I

    move-result v5

    .line 402
    invoke-static {}, Lcom/adayo/androidauto/config/LoadJson;->getInstance()Lcom/adayo/androidauto/config/LoadJson;

    move-result-object v3

    invoke-virtual {v3}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v3

    invoke-virtual {v3}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v3

    invoke-virtual {v3}, Lcom/adayo/androidauto/config/bean/Information;->getHeightPixel()I

    move-result v6

    .line 403
    invoke-static {}, Lcom/adayo/androidauto/config/LoadJson;->getInstance()Lcom/adayo/androidauto/config/LoadJson;

    move-result-object v3

    invoke-virtual {v3}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v3

    invoke-virtual {v3}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v3

    invoke-virtual {v3}, Lcom/adayo/androidauto/config/bean/Information;->getWidthPhysical()I

    move-result v9

    .line 404
    invoke-static {}, Lcom/adayo/androidauto/config/LoadJson;->getInstance()Lcom/adayo/androidauto/config/LoadJson;

    move-result-object v3

    invoke-virtual {v3}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v3

    invoke-virtual {v3}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v3

    invoke-virtual {v3}, Lcom/adayo/androidauto/config/bean/Information;->getHeightPhysical()I

    move-result v10

    .line 406
    iget-object v3, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mVideoSize:Landroid/graphics/Rect;

    iget v7, v3, Landroid/graphics/Rect;->right:I

    iget-object v3, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mVideoSize:Landroid/graphics/Rect;

    iget v8, v3, Landroid/graphics/Rect;->bottom:I

    invoke-static/range {v5 .. v10}, Lcom/google/android/projection/sink/video/DimensionUtils;->calSreen(IIIIII)Lcom/google/android/projection/sink/video/DimensionUtils$ScreenInfo;

    move-result-object v3

    .line 407
    invoke-virtual {v3}, Lcom/google/android/projection/sink/video/DimensionUtils$ScreenInfo;->getmHeightMargin()I

    move-result v5

    div-int/lit8 v5, v5, 0x2

    iput v5, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->yMargin:I

    .line 408
    invoke-virtual {v3}, Lcom/google/android/projection/sink/video/DimensionUtils$ScreenInfo;->getmWidthMargin()I

    move-result v3

    div-int/lit8 v3, v3, 0x2

    iput v3, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->xMargin:I

    .line 409
    iget-object v3, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mVideoSize:Landroid/graphics/Rect;

    iget v3, v3, Landroid/graphics/Rect;->right:I

    int-to-float v3, v3

    iget-object v5, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceFrame:Landroid/graphics/Rect;

    iget v5, v5, Landroid/graphics/Rect;->right:I

    int-to-float v5, v5

    div-float/2addr v3, v5

    iput v3, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mTouchXScale:F

    .line 410
    iget-object v3, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mVideoSize:Landroid/graphics/Rect;

    iget v3, v3, Landroid/graphics/Rect;->bottom:I

    int-to-float v3, v3

    iget-object v5, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceFrame:Landroid/graphics/Rect;

    iget v5, v5, Landroid/graphics/Rect;->bottom:I

    int-to-float v5, v5

    div-float/2addr v3, v5

    iput v3, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mTouchYScale:F

    const-string v3, "AHU.VIDEO"

    .line 411
    new-instance v5, Ljava/lang/StringBuilder;

    invoke-direct {v5}, Ljava/lang/StringBuilder;-><init>()V

    const-string v6, "before yMargin: "

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget v6, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->yMargin:I

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v6, ",xMargin: "

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget v6, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->xMargin:I

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v6, ",mTouchYScale: "

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget v6, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mTouchYScale:F

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    const-string v6, ",mTouchXScale: "

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget v6, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mTouchXScale:F

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    invoke-virtual {v5}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v5

    invoke-static {v3, v5}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 413
    invoke-static {}, Lcom/adayo/androidauto/config/LoadJson;->getInstance()Lcom/adayo/androidauto/config/LoadJson;

    move-result-object v3

    invoke-virtual {v3}, Lcom/adayo/androidauto/config/LoadJson;->getConfig()Lcom/adayo/androidauto/config/bean/Config;

    move-result-object v3

    invoke-virtual {v3}, Lcom/adayo/androidauto/config/bean/Config;->getInformation()Lcom/adayo/androidauto/config/bean/Information;

    move-result-object v3

    invoke-virtual {v3}, Lcom/adayo/androidauto/config/bean/Information;->getHeadunitModel()Ljava/lang/String;

    move-result-object v3

    .line 414
    invoke-static {v3}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z

    move-result v5

    if-nez v5, :cond_6

    const-string v5, "HMA212"

    .line 415
    invoke-virtual {v3, v5}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v5

    if-nez v5, :cond_2

    const-string v5, "HMA219"

    .line 416
    invoke-virtual {v3, v5}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v5

    if-nez v5, :cond_2

    const-string v5, "HMA220"

    .line 417
    invoke-virtual {v3, v5}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v5

    if-nez v5, :cond_2

    const-string v5, "HMA227"

    .line 418
    invoke-virtual {v3, v5}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v5

    if-nez v5, :cond_2

    const-string v5, "HMA228"

    .line 419
    invoke-virtual {v3, v5}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v5

    if-nez v5, :cond_2

    const-string v5, "HMA229"

    .line 420
    invoke-virtual {v3, v5}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v3

    if-eqz v3, :cond_6

    .line 421
    :cond_2
    iget-object v3, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mVideoSize:Landroid/graphics/Rect;

    iget v3, v3, Landroid/graphics/Rect;->right:I

    const/16 v5, 0x320

    if-eq v3, v5, :cond_5

    const/16 v5, 0x500

    if-eq v3, v5, :cond_4

    const/16 v5, 0x780

    if-eq v3, v5, :cond_3

    goto :goto_0

    .line 423
    :cond_3
    iput v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->xMargin:I

    const/16 v3, 0xb4

    .line 424
    iput v3, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->yMargin:I

    const/high16 v3, 0x3f800000    # 1.0f

    .line 425
    iput v3, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mTouchXScale:F

    .line 426
    iput v3, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mTouchYScale:F

    goto :goto_0

    .line 429
    :cond_4
    iput v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->xMargin:I

    const/16 v3, 0x78

    .line 430
    iput v3, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->yMargin:I

    const v3, 0x3f2aaaab

    .line 431
    iput v3, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mTouchXScale:F

    .line 432
    iput v3, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mTouchYScale:F

    goto :goto_0

    .line 435
    :cond_5
    iput v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->xMargin:I

    const/16 v3, 0x5a

    .line 436
    iput v3, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->yMargin:I

    const v3, 0x3ed55555

    .line 437
    iput v3, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mTouchXScale:F

    .line 438
    iput v3, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mTouchYScale:F

    .line 442
    :cond_6
    :goto_0
    iget-object v3, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mCodec:Landroid/media/MediaCodec;

    iget-object v5, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceView:Landroid/view/SurfaceView;

    invoke-virtual {v5}, Landroid/view/SurfaceView;->getHolder()Landroid/view/SurfaceHolder;

    move-result-object v5

    invoke-interface {v5}, Landroid/view/SurfaceHolder;->getSurface()Landroid/view/Surface;

    move-result-object v5

    invoke-virtual {v3, v2, v5, v1, v0}, Landroid/media/MediaCodec;->configure(Landroid/media/MediaFormat;Landroid/view/Surface;Landroid/media/MediaCrypto;I)V

    .line 443
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mCodec:Landroid/media/MediaCodec;

    invoke-virtual {v0, v4}, Landroid/media/MediaCodec;->setVideoScalingMode(I)V

    .line 444
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mCodec:Landroid/media/MediaCodec;

    invoke-virtual {v0}, Landroid/media/MediaCodec;->start()V

    .line 445
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mCodec:Landroid/media/MediaCodec;

    invoke-virtual {v0}, Landroid/media/MediaCodec;->getInputBuffers()[Ljava/nio/ByteBuffer;

    move-result-object v0

    iput-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mInputBuffers:[Ljava/nio/ByteBuffer;

    const-string v0, "AHU.VIDEO"

    .line 446
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "yMargin: "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget v2, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->yMargin:I

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v2, ",xMargin: "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget v2, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->xMargin:I

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v2, ",mTouchYScale: "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget v2, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mTouchYScale:F

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    const-string v2, ",mTouchXScale: "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget v2, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mTouchXScale:F

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V
    :try_end_2
    .catch Ljava/io/IOException; {:try_start_2 .. :try_end_2} :catch_4
    .catch Ljava/lang/IllegalArgumentException; {:try_start_2 .. :try_end_2} :catch_3
    .catch Ljava/lang/NullPointerException; {:try_start_2 .. :try_end_2} :catch_2
    .catch Ljava/lang/IllegalStateException; {:try_start_2 .. :try_end_2} :catch_1
    .catch Landroid/media/MediaCodec$CryptoException; {:try_start_2 .. :try_end_2} :catch_0
    .catchall {:try_start_2 .. :try_end_2} :catchall_0

    goto :goto_1

    :catch_0
    move-exception v0

    .line 462
    :try_start_3
    invoke-virtual {v0}, Landroid/media/MediaCodec$CryptoException;->printStackTrace()V

    const-string v1, "AHU.VIDEO"

    .line 463
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "CryptoException error: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Landroid/media/MediaCodec$CryptoException;->getCause()Ljava/lang/Throwable;

    move-result-object v0

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V

    goto :goto_1

    :catch_1
    move-exception v0

    .line 459
    invoke-virtual {v0}, Ljava/lang/IllegalStateException;->printStackTrace()V

    const-string v1, "AHU.VIDEO"

    .line 460
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "IllegalStateException error: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/IllegalStateException;->getCause()Ljava/lang/Throwable;

    move-result-object v0

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V

    goto :goto_1

    :catch_2
    move-exception v0

    .line 456
    invoke-virtual {v0}, Ljava/lang/NullPointerException;->printStackTrace()V

    const-string v1, "AHU.VIDEO"

    .line 457
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "NullPointerException error: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/NullPointerException;->getCause()Ljava/lang/Throwable;

    move-result-object v0

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V

    .line 465
    :goto_1
    new-instance v0, Lcom/adayo/androidauto/video/AAProjectionRenderer$DecodeOutputThread;

    invoke-direct {v0, p0}, Lcom/adayo/androidauto/video/AAProjectionRenderer$DecodeOutputThread;-><init>(Lcom/adayo/androidauto/video/AAProjectionRenderer;)V

    iput-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mOutputThread:Lcom/adayo/androidauto/video/AAProjectionRenderer$DecodeOutputThread;

    .line 466
    invoke-virtual {v0}, Lcom/adayo/androidauto/video/AAProjectionRenderer$DecodeOutputThread;->start()V

    .line 468
    new-instance v0, Lcom/adayo/androidauto/video/AAProjectionRenderer$DecodeInputThread;

    invoke-direct {v0, p0}, Lcom/adayo/androidauto/video/AAProjectionRenderer$DecodeInputThread;-><init>(Lcom/adayo/androidauto/video/AAProjectionRenderer;)V

    iput-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mInputThread:Lcom/adayo/androidauto/video/AAProjectionRenderer$DecodeInputThread;

    .line 469
    invoke-virtual {v0}, Lcom/adayo/androidauto/video/AAProjectionRenderer$DecodeInputThread;->start()V

    goto/16 :goto_2

    :catch_3
    move-exception v0

    .line 451
    invoke-virtual {v0}, Ljava/lang/IllegalArgumentException;->printStackTrace()V

    const-string v1, "AHU.VIDEO"

    .line 452
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "IllegalArgumentException error: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/IllegalArgumentException;->getCause()Ljava/lang/Throwable;

    move-result-object v0

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V

    .line 453
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSubHandler:Landroid/os/Handler;

    const/16 v1, 0xc9

    const-wide/16 v2, 0xc8

    invoke-virtual {v0, v1, v2, v3}, Landroid/os/Handler;->sendEmptyMessageDelayed(IJ)Z
    :try_end_3
    .catchall {:try_start_3 .. :try_end_3} :catchall_0

    .line 454
    monitor-exit p0

    return-void

    :catch_4
    move-exception v0

    :try_start_4
    const-string v1, "AHU.VIDEO"

    .line 448
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "updateCodec IOException="

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/io/IOException;->getMessage()Ljava/lang/String;

    move-result-object v3

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-static {v1, v2}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V

    .line 449
    new-instance v1, Ljava/lang/RuntimeException;

    invoke-direct {v1, v0}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/Throwable;)V

    throw v1
    :try_end_4
    .catchall {:try_start_4 .. :try_end_4} :catchall_0

    .line 473
    :cond_7
    :try_start_5
    invoke-direct {p0}, Lcom/adayo/androidauto/video/AAProjectionRenderer;->codecShouldBeRunning()Z

    move-result v0

    if-nez v0, :cond_a

    .line 474
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mInputThread:Lcom/adayo/androidauto/video/AAProjectionRenderer$DecodeInputThread;

    if-eqz v0, :cond_8

    .line 475
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mInputThread:Lcom/adayo/androidauto/video/AAProjectionRenderer$DecodeInputThread;

    invoke-virtual {v0}, Lcom/adayo/androidauto/video/AAProjectionRenderer$DecodeInputThread;->shutdown()V

    .line 476
    iput-object v1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mInputThread:Lcom/adayo/androidauto/video/AAProjectionRenderer$DecodeInputThread;

    .line 479
    :cond_8
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mOutputThread:Lcom/adayo/androidauto/video/AAProjectionRenderer$DecodeOutputThread;

    if-eqz v0, :cond_9

    .line 480
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mOutputThread:Lcom/adayo/androidauto/video/AAProjectionRenderer$DecodeOutputThread;

    invoke-virtual {v0}, Lcom/adayo/androidauto/video/AAProjectionRenderer$DecodeOutputThread;->shutdown()V

    .line 481
    iput-object v1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mOutputThread:Lcom/adayo/androidauto/video/AAProjectionRenderer$DecodeOutputThread;

    .line 484
    :cond_9
    iput-object v1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mInputBuffers:[Ljava/nio/ByteBuffer;

    .line 485
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mCodec:Landroid/media/MediaCodec;

    invoke-virtual {v0}, Landroid/media/MediaCodec;->stop()V

    .line 486
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mCodec:Landroid/media/MediaCodec;

    invoke-virtual {v0}, Landroid/media/MediaCodec;->release()V

    .line 487
    iput-object v1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mCodec:Landroid/media/MediaCodec;
    :try_end_5
    .catch Ljava/lang/Exception; {:try_start_5 .. :try_end_5} :catch_5
    .catchall {:try_start_5 .. :try_end_5} :catchall_0

    goto :goto_2

    :catch_5
    move-exception v0

    .line 490
    :try_start_6
    invoke-virtual {v0}, Ljava/lang/Exception;->printStackTrace()V
    :try_end_6
    .catchall {:try_start_6 .. :try_end_6} :catchall_0

    .line 494
    :cond_a
    :goto_2
    monitor-exit p0

    return-void

    :catchall_0
    move-exception v0

    monitor-exit p0

    throw v0
.end method

.method private updateSurface(Landroid/view/Surface;II)V
    .locals 2

    .line 337
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "updateSurface 0 set mSurfaceFrame to,width="

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v1, " height="

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v1, " mLayoutChangeOngoing="

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-boolean v1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mLayoutChangeOngoing:Z

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    const-string v1, "AHU.VIDEO"

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 338
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurface:Landroid/view/Surface;

    if-eqz v0, :cond_0

    .line 339
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mListener:Lcom/adayo/androidauto/video/AAProjectionRenderer$Listener;

    iget-boolean v1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mLayoutChangeOngoing:Z

    invoke-interface {v0, v1}, Lcom/adayo/androidauto/video/AAProjectionRenderer$Listener;->onSurfaceDestroyed(Z)V

    .line 342
    :cond_0
    iput-object p1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurface:Landroid/view/Surface;

    .line 343
    iget-object p1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceFrame:Landroid/graphics/Rect;

    const/4 v0, 0x0

    invoke-virtual {p1, v0, v0, p2, p3}, Landroid/graphics/Rect;->set(IIII)V

    if-lez p2, :cond_1

    if-lez p3, :cond_1

    .line 346
    invoke-direct {p0}, Lcom/adayo/androidauto/video/AAProjectionRenderer;->updateCodec()V

    .line 349
    :cond_1
    iget-object p1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mListener:Lcom/adayo/androidauto/video/AAProjectionRenderer$Listener;

    iget-boolean v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mLayoutChangeOngoing:Z

    invoke-interface {p1, p2, p3, v0}, Lcom/adayo/androidauto/video/AAProjectionRenderer$Listener;->onSurfaceCreated(IIZ)V

    return-void
.end method


# virtual methods
.method public declared-synchronized clear()V
    .locals 1

    monitor-enter p0

    .line 241
    :try_start_0
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mPendingInputBuffers:Ljava/util/concurrent/LinkedBlockingQueue;

    invoke-virtual {v0}, Ljava/util/concurrent/LinkedBlockingQueue;->clear()V

    .line 242
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mQueuedInputBuffers:Ljava/util/concurrent/LinkedBlockingQueue;

    invoke-virtual {v0}, Ljava/util/concurrent/LinkedBlockingQueue;->clear()V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 243
    monitor-exit p0

    return-void

    :catchall_0
    move-exception v0

    monitor-exit p0

    throw v0
.end method

.method public declared-synchronized decodeBuffer(Lcom/google/android/projection/protocol/VideoFrame;)V
    .locals 2

    monitor-enter p0

    if-eqz p1, :cond_2

    .line 269
    :try_start_0
    iget v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mState:I

    const/4 v1, 0x1

    if-eq v0, v1, :cond_1

    .line 272
    iget v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mState:I

    const/4 v1, 0x3

    if-ne v0, v1, :cond_0

    const-string p1, "AHU.VIDEO"

    const-string v0, "Received frame when shutting down, ignoring."

    .line 273
    invoke-static {p1, v0}, Lcom/adayo/androidauto/utils/AALog;->i(Ljava/lang/String;Ljava/lang/String;)V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 274
    monitor-exit p0

    return-void

    .line 277
    :cond_0
    :try_start_1
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mPendingInputBuffers:Ljava/util/concurrent/LinkedBlockingQueue;

    invoke-virtual {v0, p1}, Ljava/util/concurrent/LinkedBlockingQueue;->add(Ljava/lang/Object;)Z

    .line 278
    iget-object p1, p1, Lcom/google/android/projection/protocol/VideoFrame;->data:Ljava/nio/ByteBuffer;
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    .line 288
    monitor-exit p0

    return-void

    .line 270
    :cond_1
    :try_start_2
    new-instance p1, Ljava/lang/IllegalStateException;

    invoke-direct {p1}, Ljava/lang/IllegalStateException;-><init>()V

    throw p1

    :catchall_0
    move-exception p1

    goto :goto_0

    .line 267
    :cond_2
    new-instance p1, Ljava/lang/IllegalArgumentException;

    invoke-direct {p1}, Ljava/lang/IllegalArgumentException;-><init>()V

    throw p1
    :try_end_2
    .catchall {:try_start_2 .. :try_end_2} :catchall_0

    :goto_0
    monitor-exit p0

    throw p1
.end method

.method public getIsKeyFrameData()Z
    .locals 1

    .line 578
    iget-boolean v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->isKeyFrameData:Z

    return v0
.end method

.method public getRenderFrame()Landroid/graphics/Rect;
    .locals 1

    .line 257
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceFrame:Landroid/graphics/Rect;

    return-object v0
.end method

.method public declared-synchronized handleCodecConfig([B)V
    .locals 4

    monitor-enter p0

    :try_start_0
    const-string v0, "AHU.VIDEO"

    .line 291
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "handleCodecConfig CodecConfig data size="

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    array-length v2, p1

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v2, " mIsInited="

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-boolean v2, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mIsInited:Z

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 306
    iget v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mState:I

    const/4 v1, 0x1

    if-eq v0, v1, :cond_2

    .line 309
    iget v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mState:I

    const/4 v1, 0x3

    if-ne v0, v1, :cond_0

    const-string p1, "AHU.VIDEO"

    const-string v0, "handleCodecConfig,Received codec config while shutting down. Ignoring."

    .line 310
    invoke-static {p1, v0}, Lcom/adayo/androidauto/utils/AALog;->i(Ljava/lang/String;Ljava/lang/String;)V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 311
    monitor-exit p0

    return-void

    .line 313
    :cond_0
    :try_start_1
    array-length v0, p1

    invoke-static {v0}, Ljava/nio/ByteBuffer;->allocate(I)Ljava/nio/ByteBuffer;

    move-result-object v0

    .line 314
    invoke-virtual {v0, p1}, Ljava/nio/ByteBuffer;->put([B)Ljava/nio/ByteBuffer;

    .line 315
    new-instance p1, Lcom/google/android/projection/protocol/VideoFrame;

    const/4 v1, 0x0

    const-wide/16 v2, 0x0

    invoke-direct {p1, v1, v2, v3, v0}, Lcom/google/android/projection/protocol/VideoFrame;-><init>(IJLjava/nio/ByteBuffer;)V

    .line 316
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mPendingInputBuffers:Ljava/util/concurrent/LinkedBlockingQueue;

    invoke-virtual {v0, p1}, Ljava/util/concurrent/LinkedBlockingQueue;->add(Ljava/lang/Object;)Z

    const-string p1, "AHU.VIDEO"

    const-string v0, "handleCodecConfig,pps/sps frams add complete"

    .line 317
    invoke-static {p1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 320
    iget-object p1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceView:Landroid/view/SurfaceView;

    if-eqz p1, :cond_1

    iget-object p1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceView:Landroid/view/SurfaceView;

    invoke-virtual {p1}, Landroid/view/SurfaceView;->getAlpha()F

    move-result p1

    const/high16 v0, 0x3f800000    # 1.0f

    cmpg-float p1, p1, v0

    if-gez p1, :cond_1

    .line 321
    iget-object p1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSubHandler:Landroid/os/Handler;

    new-instance v0, Lcom/adayo/androidauto/video/-$$Lambda$AAProjectionRenderer$Qqh-RnDbboCIH_5h56b77f7-1Hc;

    invoke-direct {v0, p0}, Lcom/adayo/androidauto/video/-$$Lambda$AAProjectionRenderer$Qqh-RnDbboCIH_5h56b77f7-1Hc;-><init>(Lcom/adayo/androidauto/video/AAProjectionRenderer;)V

    const-wide/16 v1, 0x1f6

    invoke-virtual {p1, v0, v1, v2}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z

    const-string p1, "AHU.VIDEO"

    .line 324
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "getAlpha: "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceView:Landroid/view/SurfaceView;

    invoke-virtual {v1}, Landroid/view/SurfaceView;->getAlpha()F

    move-result v1

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {p1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    .line 326
    :cond_1
    monitor-exit p0

    return-void

    .line 307
    :cond_2
    :try_start_2
    new-instance p1, Ljava/lang/IllegalStateException;

    invoke-direct {p1}, Ljava/lang/IllegalStateException;-><init>()V

    throw p1
    :try_end_2
    .catchall {:try_start_2 .. :try_end_2} :catchall_0

    :catchall_0
    move-exception p1

    monitor-exit p0

    throw p1
.end method

.method public init(Landroid/view/SurfaceView;Landroid/widget/TextView;Lcom/adayo/androidauto/video/AAProjectionRenderer$Listener;Lcom/google/android/projection/sink/video/DimensionUtils$ScreenSettings;Lcom/google/android/projection/sink/video/VideoLatencyChecker;Lcom/google/android/projection/sink/audio/AudioLatencyChecker;)V
    .locals 0

    const-string p2, "AHU.VIDEO"

    const-string p5, "AAProjectionRenderer,init1"

    .line 122
    invoke-static {p2, p5}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 123
    iput-object p1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceView:Landroid/view/SurfaceView;

    .line 124
    iput-object p3, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mListener:Lcom/adayo/androidauto/video/AAProjectionRenderer$Listener;

    .line 125
    iput-object p4, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mEmulatedScreen:Lcom/google/android/projection/sink/video/DimensionUtils$ScreenSettings;

    .line 141
    invoke-virtual {p1}, Landroid/view/SurfaceView;->getHolder()Landroid/view/SurfaceHolder;

    move-result-object p1

    .line 142
    invoke-interface {p1}, Landroid/view/SurfaceHolder;->getSurface()Landroid/view/Surface;

    move-result-object p2

    if-eqz p2, :cond_0

    .line 151
    invoke-interface {p1}, Landroid/view/SurfaceHolder;->getSurfaceFrame()Landroid/graphics/Rect;

    move-result-object p3

    .line 153
    invoke-virtual {p3}, Landroid/graphics/Rect;->width()I

    move-result p4

    invoke-virtual {p3}, Landroid/graphics/Rect;->height()I

    move-result p3

    invoke-direct {p0, p2, p4, p3}, Lcom/adayo/androidauto/video/AAProjectionRenderer;->updateSurface(Landroid/view/Surface;II)V

    .line 155
    :cond_0
    iget-object p2, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceCallback:Landroid/view/SurfaceHolder$Callback;

    invoke-interface {p1, p2}, Landroid/view/SurfaceHolder;->addCallback(Landroid/view/SurfaceHolder$Callback;)V

    return-void
.end method

.method public isStarted()Z
    .locals 2

    .line 250
    iget v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mState:I

    const/4 v1, 0x2

    if-ne v0, v1, :cond_0

    const/4 v0, 0x1

    goto :goto_0

    :cond_0
    const/4 v0, 0x0

    :goto_0
    return v0
.end method

.method public synthetic lambda$handleCodecConfig$0$AAProjectionRenderer()V
    .locals 2

    .line 322
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceView:Landroid/view/SurfaceView;

    if-eqz v0, :cond_0

    const/high16 v1, 0x3f800000    # 1.0f

    invoke-virtual {v0, v1}, Landroid/view/SurfaceView;->setAlpha(F)V

    :cond_0
    return-void
.end method

.method public declared-synchronized prepareStart(II)V
    .locals 3

    monitor-enter p0

    :try_start_0
    const-string v0, "AHU.VIDEO"

    .line 197
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "prepareStart from videosink,set mVideoSize width="

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1, p1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v2, "height="

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1, p2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->w(Ljava/lang/String;Ljava/lang/String;)V

    .line 198
    new-instance v0, Landroid/graphics/Rect;

    const/4 v1, 0x0

    invoke-direct {v0, v1, v1, p1, p2}, Landroid/graphics/Rect;-><init>(IIII)V

    iput-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mVideoSize:Landroid/graphics/Rect;

    const/4 p1, 0x2

    .line 199
    iput p1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mState:I
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 200
    monitor-exit p0

    return-void

    :catchall_0
    move-exception p1

    monitor-exit p0

    throw p1
.end method

.method public declared-synchronized release()V
    .locals 3

    monitor-enter p0

    :try_start_0
    const-string v0, "AHU.VIDEO"

    const-string v1, "release,set mSurfaceView to null!"

    .line 178
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 179
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurface:Landroid/view/Surface;

    const/4 v1, 0x0

    if-eqz v0, :cond_0

    .line 180
    iput-object v1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurface:Landroid/view/Surface;

    .line 181
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mListener:Lcom/adayo/androidauto/video/AAProjectionRenderer$Listener;

    const/4 v2, 0x0

    invoke-interface {v0, v2}, Lcom/adayo/androidauto/video/AAProjectionRenderer$Listener;->onSurfaceDestroyed(Z)V

    .line 184
    :cond_0
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceView:Landroid/view/SurfaceView;

    if-eqz v0, :cond_1

    .line 185
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceView:Landroid/view/SurfaceView;

    invoke-virtual {v0}, Landroid/view/SurfaceView;->getHolder()Landroid/view/SurfaceHolder;

    move-result-object v0

    iget-object v2, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceCallback:Landroid/view/SurfaceHolder$Callback;

    invoke-interface {v0, v2}, Landroid/view/SurfaceHolder;->removeCallback(Landroid/view/SurfaceHolder$Callback;)V

    .line 186
    iput-object v1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceView:Landroid/view/SurfaceView;

    .line 189
    :cond_1
    invoke-virtual {p0}, Lcom/adayo/androidauto/video/AAProjectionRenderer;->stop()V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 190
    monitor-exit p0

    return-void

    :catchall_0
    move-exception v0

    monitor-exit p0

    throw v0
.end method

.method public setFrameCount(I)V
    .locals 0

    .line 570
    iput p1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mFrameCount:I

    return-void
.end method

.method public setIsKeyFrameData(Z)V
    .locals 0

    .line 574
    iput-boolean p1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->isKeyFrameData:Z

    return-void
.end method

.method public declared-synchronized start()V
    .locals 3

    monitor-enter p0

    :try_start_0
    const-string v0, "AHU.VIDEO"

    .line 208
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "start...mSurfaceFrame.width()="

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v2, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceFrame:Landroid/graphics/Rect;

    invoke-virtual {v2}, Landroid/graphics/Rect;->width()I

    move-result v2

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v2, "--mSurfaceFrame.height()="

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v2, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceFrame:Landroid/graphics/Rect;

    invoke-virtual {v2}, Landroid/graphics/Rect;->height()I

    move-result v2

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->w(Ljava/lang/String;Ljava/lang/String;)V

    .line 209
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceFrame:Landroid/graphics/Rect;

    invoke-virtual {v0}, Landroid/graphics/Rect;->width()I

    move-result v0

    if-lez v0, :cond_0

    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceFrame:Landroid/graphics/Rect;

    invoke-virtual {v0}, Landroid/graphics/Rect;->height()I

    move-result v0

    if-lez v0, :cond_0

    .line 210
    invoke-direct {p0}, Lcom/adayo/androidauto/video/AAProjectionRenderer;->updateCodec()V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 212
    :cond_0
    monitor-exit p0

    return-void

    :catchall_0
    move-exception v0

    monitor-exit p0

    throw v0
.end method

.method public declared-synchronized stop()V
    .locals 2

    monitor-enter p0

    const/4 v0, 0x3

    .line 220
    :try_start_0
    iput v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mState:I

    .line 222
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSubHandler:Landroid/os/Handler;

    const/16 v1, 0xc9

    invoke-virtual {v0, v1}, Landroid/os/Handler;->removeMessages(I)V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 224
    :try_start_1
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mPendingInputBuffers:Ljava/util/concurrent/LinkedBlockingQueue;

    invoke-virtual {v0}, Ljava/util/concurrent/LinkedBlockingQueue;->clear()V

    .line 225
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mQueuedInputBuffers:Ljava/util/concurrent/LinkedBlockingQueue;

    invoke-virtual {v0}, Ljava/util/concurrent/LinkedBlockingQueue;->clear()V

    const/4 v0, 0x0

    .line 227
    iput-boolean v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mIsInited:Z

    .line 229
    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mConfigData:[B

    if-eqz v0, :cond_0

    const/4 v0, 0x0

    .line 230
    iput-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mConfigData:[B

    .line 234
    :cond_0
    invoke-direct {p0}, Lcom/adayo/androidauto/video/AAProjectionRenderer;->updateCodec()V
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_0
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    goto :goto_0

    :catch_0
    move-exception v0

    .line 236
    :try_start_2
    invoke-virtual {v0}, Ljava/lang/Exception;->printStackTrace()V
    :try_end_2
    .catchall {:try_start_2 .. :try_end_2} :catchall_0

    .line 238
    :goto_0
    monitor-exit p0

    return-void

    :catchall_0
    move-exception v0

    monitor-exit p0

    throw v0
.end method

.method public transformMotion(Landroid/view/MotionEvent;)Landroid/view/MotionEvent;
    .locals 2

    iget-object v0, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceView:Landroid/view/SurfaceView;
    iget-object v1, p0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mVideoSize:Landroid/graphics/Rect;
    invoke-static {v0, v1, p1}, Lcom/adayo/androidauto/patch/AAWindowPatch;->touch(Landroid/view/SurfaceView;Landroid/graphics/Rect;Landroid/view/MotionEvent;)Landroid/view/MotionEvent;
    move-result-object v0
    return-object v0
.end method

.method public legacyTransformMotion(Landroid/view/MotionEvent;)Landroid/view/MotionEvent;
    .locals 18

    move-object/from16 v0, p0

    move-object/from16 v1, p1

    .line 832
    invoke-virtual/range {p1 .. p1}, Landroid/view/MotionEvent;->getPointerCount()I

    move-result v6

    .line 833
    new-array v7, v6, [Landroid/view/MotionEvent$PointerProperties;

    .line 834
    new-array v8, v6, [Landroid/view/MotionEvent$PointerCoords;

    const/4 v2, 0x0

    :goto_0
    if-ge v2, v6, :cond_0

    .line 837
    new-instance v3, Landroid/view/MotionEvent$PointerProperties;

    invoke-direct {v3}, Landroid/view/MotionEvent$PointerProperties;-><init>()V

    aput-object v3, v7, v2

    .line 838
    aget-object v3, v7, v2

    invoke-virtual {v1, v2, v3}, Landroid/view/MotionEvent;->getPointerProperties(ILandroid/view/MotionEvent$PointerProperties;)V

    .line 839
    new-instance v3, Landroid/view/MotionEvent$PointerCoords;

    invoke-direct {v3}, Landroid/view/MotionEvent$PointerCoords;-><init>()V

    aput-object v3, v8, v2

    .line 840
    aget-object v3, v8, v2

    invoke-virtual {v1, v2, v3}, Landroid/view/MotionEvent;->getPointerCoords(ILandroid/view/MotionEvent$PointerCoords;)V

    .line 844
    iget-object v3, v0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mSurfaceView:Landroid/view/SurfaceView;

    .line 845
    invoke-virtual {v3}, Landroid/view/SurfaceView;->getLayoutParams()Landroid/view/ViewGroup$LayoutParams;

    move-result-object v3

    check-cast v3, Landroid/view/ViewGroup$MarginLayoutParams;

    .line 851
    aget-object v3, v8, v2

    iget v4, v3, Landroid/view/MotionEvent$PointerCoords;->x:F

    iget v5, v0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mTouchXScale:F

    mul-float/2addr v4, v5

    iput v4, v3, Landroid/view/MotionEvent$PointerCoords;->x:F

    .line 852
    aget-object v3, v8, v2

    iget v4, v3, Landroid/view/MotionEvent$PointerCoords;->y:F

    iget v5, v0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->mTouchYScale:F

    mul-float/2addr v4, v5

    iput v4, v3, Landroid/view/MotionEvent$PointerCoords;->y:F

    .line 853
    aget-object v3, v8, v2

    iget v4, v3, Landroid/view/MotionEvent$PointerCoords;->x:F

    iget v5, v0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->xMargin:I

    int-to-float v5, v5

    sub-float/2addr v4, v5

    iput v4, v3, Landroid/view/MotionEvent$PointerCoords;->x:F

    .line 854
    aget-object v3, v8, v2

    iget v4, v3, Landroid/view/MotionEvent$PointerCoords;->y:F

    iget v5, v0, Lcom/adayo/androidauto/video/AAProjectionRenderer;->yMargin:I

    int-to-float v5, v5

    sub-float/2addr v4, v5

    iput v4, v3, Landroid/view/MotionEvent$PointerCoords;->y:F

    add-int/lit8 v2, v2, 0x1

    goto :goto_0

    .line 864
    :cond_0
    invoke-virtual/range {p1 .. p1}, Landroid/view/MotionEvent;->getDownTime()J

    move-result-wide v2

    .line 865
    invoke-virtual/range {p1 .. p1}, Landroid/view/MotionEvent;->getEventTime()J

    move-result-wide v4

    .line 866
    invoke-virtual/range {p1 .. p1}, Landroid/view/MotionEvent;->getAction()I

    move-result v9

    .line 868
    invoke-virtual/range {p1 .. p1}, Landroid/view/MotionEvent;->getMetaState()I

    move-result v10

    .line 869
    invoke-virtual/range {p1 .. p1}, Landroid/view/MotionEvent;->getButtonState()I

    move-result v11

    .line 870
    invoke-virtual/range {p1 .. p1}, Landroid/view/MotionEvent;->getXPrecision()F

    move-result v12

    .line 871
    invoke-virtual/range {p1 .. p1}, Landroid/view/MotionEvent;->getYPrecision()F

    move-result v13

    .line 872
    invoke-virtual/range {p1 .. p1}, Landroid/view/MotionEvent;->getDeviceId()I

    move-result v14

    .line 873
    invoke-virtual/range {p1 .. p1}, Landroid/view/MotionEvent;->getEdgeFlags()I

    move-result v15

    .line 874
    invoke-virtual/range {p1 .. p1}, Landroid/view/MotionEvent;->getSource()I

    move-result v16

    .line 875
    invoke-virtual/range {p1 .. p1}, Landroid/view/MotionEvent;->getFlags()I

    move-result v17

    move-wide v1, v2

    move-wide v3, v4

    move v5, v9

    move v9, v10

    move v10, v11

    move v11, v12

    move v12, v13

    move v13, v14

    move v14, v15

    move/from16 v15, v16

    move/from16 v16, v17

    .line 863
    invoke-static/range {v1 .. v16}, Landroid/view/MotionEvent;->obtain(JJII[Landroid/view/MotionEvent$PointerProperties;[Landroid/view/MotionEvent$PointerCoords;IIFFIIII)Landroid/view/MotionEvent;

    move-result-object v1

    return-object v1
.end method
