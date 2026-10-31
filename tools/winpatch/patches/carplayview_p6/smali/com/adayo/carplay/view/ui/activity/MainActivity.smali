.class public Lcom/adayo/carplay/view/ui/activity/MainActivity;
.super Landroid/app/Activity;
.source "MainActivity.java"

# interfaces
.implements Landroid/view/View$OnClickListener;


# instance fields
.field private application:Lcom/adayo/carplay/view/CarPlayApplication;

.field private hasRequestUI:Z

.field private id:J

.field private mTouchX:I

.field private mTouchY:I

.field private needTake:Z

.field private surfaceCallBack:Landroid/view/SurfaceHolder$Callback;

.field private surfaceView:Landroid/view/SurfaceView;


# direct methods
.method public constructor <init>()V
    .locals 3

    .line 27
    invoke-direct {p0}, Landroid/app/Activity;-><init>()V

    const/4 v0, 0x0

    .line 28
    iput-boolean v0, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->hasRequestUI:Z

    const-wide/16 v1, 0x0

    .line 30
    iput-wide v1, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->id:J

    const/4 v1, 0x1

    .line 31
    iput-boolean v1, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->needTake:Z

    .line 33
    invoke-static {}, Lcom/adayo/carplay/view/CarPlayApplication;->getInstance()Lcom/adayo/carplay/view/CarPlayApplication;

    move-result-object v1

    iput-object v1, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->application:Lcom/adayo/carplay/view/CarPlayApplication;

    .line 34
    iput v0, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->mTouchX:I

    .line 35
    iput v0, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->mTouchY:I

    .line 36
    new-instance v0, Lcom/adayo/carplay/view/ui/activity/MainActivity$1;

    invoke-direct {v0, p0}, Lcom/adayo/carplay/view/ui/activity/MainActivity$1;-><init>(Lcom/adayo/carplay/view/ui/activity/MainActivity;)V

    iput-object v0, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->surfaceCallBack:Landroid/view/SurfaceHolder$Callback;

    return-void
.end method

.method static synthetic access$000(Lcom/adayo/carplay/view/ui/activity/MainActivity;)J
    .locals 2

    .line 27
    iget-wide v0, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->id:J

    return-wide v0
.end method

.method static synthetic access$100(Lcom/adayo/carplay/view/ui/activity/MainActivity;)Lcom/adayo/carplay/view/CarPlayApplication;
    .locals 0

    .line 27
    iget-object p0, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->application:Lcom/adayo/carplay/view/CarPlayApplication;

    return-object p0
.end method

.method private isAppOnForeground(Ljava/lang/String;Landroid/content/Context;)Z
    .locals 2

    const/4 v0, 0x0

    if-nez p2, :cond_0

    return v0

    :cond_0
    const-string v1, "activity"

    .line 240
    invoke-virtual {p2, v1}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object p2

    check-cast p2, Landroid/app/ActivityManager;

    if-nez p2, :cond_1

    const-string p1, "getSystemService ActivityManager failed"

    .line 242
    invoke-static {p1}, Lcom/adayo/utility/Logger;->e(Ljava/lang/String;)V

    return v0

    :cond_1
    const/4 v1, 0x1

    .line 245
    invoke-virtual {p2, v1}, Landroid/app/ActivityManager;->getRunningTasks(I)Ljava/util/List;

    move-result-object p2

    if-nez p2, :cond_2

    const-string p1, "runningTasks is null"

    .line 247
    invoke-static {p1}, Lcom/adayo/utility/Logger;->e(Ljava/lang/String;)V

    return v0

    .line 251
    :cond_2
    invoke-interface {p2}, Ljava/util/List;->size()I

    move-result v1

    if-nez v1, :cond_3

    return v0

    .line 255
    :cond_3
    invoke-interface {p2, v0}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object p2

    check-cast p2, Landroid/app/ActivityManager$RunningTaskInfo;

    iget-object p2, p2, Landroid/app/ActivityManager$RunningTaskInfo;->topActivity:Landroid/content/ComponentName;

    invoke-virtual {p2}, Landroid/content/ComponentName;->getPackageName()Ljava/lang/String;

    move-result-object p2

    .line 256
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "runningStack :"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v0}, Lcom/adayo/utility/Logger;->d(Ljava/lang/String;)V

    .line 257
    invoke-virtual {p1, p2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    return p1
.end method

.method private requestCarPlayUI()V
    .locals 3

    const-string v0, "requestCarPlayUI"

    .line 141
    invoke-static {v0}, Lcom/adayo/utility/Logger;->i(Ljava/lang/String;)V

    .line 142
    invoke-virtual {p0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->getIntent()Landroid/content/Intent;

    move-result-object v0

    if-nez v0, :cond_0

    return-void

    .line 148
    :cond_0
    invoke-virtual {v0}, Landroid/content/Intent;->getExtras()Landroid/os/Bundle;

    move-result-object v1

    if-eqz v1, :cond_2

    const-string v2, "map"

    .line 150
    invoke-virtual {v1, v2}, Landroid/os/Bundle;->get(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Ljava/util/HashMap;

    if-eqz v1, :cond_2

    const-string v0, "AppType"

    .line 152
    invoke-interface {v1, v0}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Ljava/lang/String;

    .line 153
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "AppType:"

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v1}, Lcom/adayo/utility/Logger;->i(Ljava/lang/String;)V

    .line 154
    iget-object v1, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->application:Lcom/adayo/carplay/view/CarPlayApplication;

    if-nez v0, :cond_1

    const-string v0, "None"

    :cond_1
    invoke-virtual {v1, v0}, Lcom/adayo/carplay/view/CarPlayApplication;->launchCarPlay(Ljava/lang/String;)V

    return-void

    :cond_2
    const-string v1, "SourceType"

    .line 159
    invoke-virtual {v0, v1}, Landroid/content/Intent;->getStringExtra(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    if-nez v1, :cond_3

    const-string v0, "None"

    goto :goto_0

    :cond_3
    const-string v1, "SourceType"

    invoke-virtual {v0, v1}, Landroid/content/Intent;->getStringExtra(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    .line 160
    :goto_0
    iget-object v1, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->application:Lcom/adayo/carplay/view/CarPlayApplication;

    invoke-virtual {v1, v0}, Lcom/adayo/carplay/view/CarPlayApplication;->launchCarPlay(Ljava/lang/String;)V

    return-void
.end method


# virtual methods
.method public carAppisShow()Z
    .locals 2

    const-string v0, "com.adayo.carplay"

    .line 232
    invoke-static {}, Lcom/adayo/carplay/view/CarPlayApplication;->getInstance()Lcom/adayo/carplay/view/CarPlayApplication;

    move-result-object v1

    invoke-virtual {v1}, Lcom/adayo/carplay/view/CarPlayApplication;->getContext()Landroid/content/Context;

    move-result-object v1

    invoke-direct {p0, v0, v1}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->isAppOnForeground(Ljava/lang/String;Landroid/content/Context;)Z

    move-result v0

    return v0
.end method

.method public dispatchTouchEvent(Landroid/view/MotionEvent;)Z
    .locals 5

    iget-object v0, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->surfaceView:Landroid/view/SurfaceView;

    check-cast v0, Lcom/adayo/carplay/view/patch/WindowSurface;

    invoke-virtual {v0, p1}, Lcom/adayo/carplay/view/patch/WindowSurface;->mapTouch(Landroid/view/MotionEvent;)[I

    move-result-object v0

    if-eqz v0, :cond_0

    const/4 v1, 0x0

    aget v1, v0, v1

    const/4 v2, 0x1

    aget v2, v0, v2

    const/4 v3, 0x2

    aget v3, v0, v3

    iget-object v4, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->application:Lcom/adayo/carplay/view/CarPlayApplication;

    invoke-virtual {v4, v1, v2, v3}, Lcom/adayo/carplay/view/CarPlayApplication;->touchEvent(III)V

    :cond_0
    const/4 v0, 0x1

    return v0
.end method

.method protected hideBottomUIMenu()V
    .locals 0

    return-void
.end method

.method protected initView()V
    .locals 2

    const/high16 v0, 0x7f020000

    .line 183
    invoke-virtual {p0, v0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->findViewById(I)Landroid/view/View;

    move-result-object v0

    check-cast v0, Landroid/view/SurfaceView;

    iput-object v0, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->surfaceView:Landroid/view/SurfaceView;

    .line 187
    iget-object v0, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->surfaceView:Landroid/view/SurfaceView;

    invoke-virtual {v0}, Landroid/view/SurfaceView;->getHolder()Landroid/view/SurfaceHolder;

    move-result-object v0

    iget-object v1, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->surfaceCallBack:Landroid/view/SurfaceHolder$Callback;

    invoke-interface {v0, v1}, Landroid/view/SurfaceHolder;->addCallback(Landroid/view/SurfaceHolder$Callback;)V

    return-void
.end method

.method public isHasRequestUI()Z
    .locals 1

    .line 69
    iget-boolean v0, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->hasRequestUI:Z

    return v0
.end method

.method public declared-synchronized isNeedTake()Z
    .locals 1

    monitor-enter p0

    .line 77
    :try_start_0
    iget-boolean v0, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->needTake:Z
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    monitor-exit p0

    return v0

    :catchall_0
    move-exception v0

    monitor-exit p0

    throw v0
.end method

.method public legacyTouchEvent(Landroid/view/MotionEvent;)Z
    .locals 10

    invoke-virtual {p1}, Landroid/view/MotionEvent;->getAction()I

    move-result v0

    invoke-virtual {p1}, Landroid/view/MotionEvent;->getX()F

    move-result v1

    float-to-int v1, v1

    invoke-virtual {p1}, Landroid/view/MotionEvent;->getY()F

    move-result p1

    float-to-int p1, p1

    move v8, v1

    move v9, p1

    iget-object v4, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->surfaceView:Landroid/view/SurfaceView;

    const/4 v5, 0x1

    if-eqz v4, :cond_0

    invoke-virtual {v4}, Landroid/view/SurfaceView;->getWidth()I

    move-result v5

    if-lez v5, :cond_0

    goto :goto_0

    :cond_0
    const/4 v5, 0x1

    :goto_0
    const/4 v6, 0x1

    if-eqz v4, :cond_1

    invoke-virtual {v4}, Landroid/view/SurfaceView;->getHeight()I

    move-result v6

    if-lez v6, :cond_1

    goto :goto_1

    :cond_1
    const/4 v6, 0x1

    :goto_1
    int-to-float v7, v1

    const/high16 v1, 0x44f00000    # 1920.0f

    mul-float/2addr v7, v1

    int-to-float v1, v5

    div-float/2addr v7, v1

    float-to-int v1, v7

    int-to-float v7, p1

    const/high16 p1, 0x44870000    # 1080.0f

    mul-float/2addr v7, p1

    int-to-float p1, v6

    div-float/2addr v7, p1

    float-to-int p1, v7

    const-string v4, "CPViewPatch"

    new-instance v5, Ljava/lang/StringBuilder;

    invoke-direct {v5}, Ljava/lang/StringBuilder;-><init>()V

    const-string v6, "touch x="

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v5, v8}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v6, " y="

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v5, v9}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v6, " scaled x="

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v5, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v6, " y="

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v5, p1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v5}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v5

    invoke-static {v4, v5}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    const/4 v2, 0x0

    const/4 v3, 0x1

    packed-switch v0, :pswitch_data_0

    return v2

    .line 222
    :pswitch_0
    iget-object v0, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->application:Lcom/adayo/carplay/view/CarPlayApplication;

    invoke-virtual {v0, v2, v1, p1}, Lcom/adayo/carplay/view/CarPlayApplication;->touchEvent(III)V

    .line 223
    iget-object p1, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->surfaceView:Landroid/view/SurfaceView;

    invoke-virtual {p1}, Landroid/view/SurfaceView;->performClick()Z

    return v3

    .line 213
    :pswitch_1
    iget v0, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->mTouchX:I

    if-ne v0, v1, :cond_2

    iget v0, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->mTouchY:I

    if-ne v0, p1, :cond_2

    return v3

    .line 216
    :cond_2
    iput v1, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->mTouchX:I

    .line 217
    iput p1, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->mTouchY:I

    .line 218
    iget-object v0, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->application:Lcom/adayo/carplay/view/CarPlayApplication;

    invoke-virtual {v0, v3, v1, p1}, Lcom/adayo/carplay/view/CarPlayApplication;->touchEvent(III)V

    return v3

    :pswitch_data_0
    .packed-switch 0x0
        :pswitch_1
        :pswitch_0
        :pswitch_1
        :pswitch_0
    .end packed-switch
.end method

.method public onClick(Landroid/view/View;)V
    .locals 0

    return-void
.end method

.method protected onCreate(Landroid/os/Bundle;)V
    .locals 5

    .line 86
    invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V

    invoke-static {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->interceptOnCreate(Lcom/adayo/carplay/view/ui/activity/MainActivity;)Z

    move-result p1

    if-eqz p1, :cond_window_ok

    return-void

    :cond_window_ok
    const-string p1, "onCreate"

    .line 87
    invoke-static {p1}, Lcom/adayo/utility/Logger;->i(Ljava/lang/String;)V

    .line 88
    invoke-virtual {p0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->hideBottomUIMenu()V

    .line 89
    invoke-virtual {p0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->getWindow()Landroid/view/Window;

    move-result-object p1

    const/16 v0, 0x0

    invoke-virtual {p1, v0, v0}, Landroid/view/Window;->setFlags(II)V

    const/high16 p1, 0x7f030000

    .line 92
    invoke-virtual {p0, p1}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->setContentView(I)V

    .line 93
    invoke-virtual {p0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->initView()V

    .line 95
    invoke-virtual {p0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->getIntent()Landroid/content/Intent;

    move-result-object p1

    const-wide/16 v0, 0x0

    if-eqz p1, :cond_0

    const-string v2, "surfaceId"

    .line 97
    invoke-virtual {p1, v2, v0, v1}, Landroid/content/Intent;->getLongExtra(Ljava/lang/String;J)J

    move-result-wide v2

    .line 98
    new-instance p1, Ljava/lang/StringBuilder;

    invoke-direct {p1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v4, "getLongExtra surfaceId = "

    invoke-virtual {p1, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p1, v2, v3}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;

    invoke-virtual {p1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-static {p1}, Lcom/adayo/utility/Logger;->i(Ljava/lang/String;)V

    goto :goto_0

    :cond_0
    move-wide v2, v0

    .line 101
    :goto_0
    invoke-static {v2, v3}, Lcom/adayo/carplay/view/patch/WindowSurface;->resolveSurfaceId(J)J

    move-result-wide v2

    new-instance p1, Ljava/lang/StringBuilder;

    invoke-direct {p1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v4, "surfaceId = "

    invoke-virtual {p1, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p1, v2, v3}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;

    invoke-virtual {p1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-static {p1}, Lcom/adayo/utility/Logger;->i(Ljava/lang/String;)V

    cmp-long p1, v0, v2

    if-eqz p1, :cond_1

    .line 103
    iput-wide v2, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->id:J

    goto :goto_1

    .line 105
    :cond_1
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v0

    iput-wide v0, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->id:J

    .line 106
    invoke-virtual {p0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->isHasRequestUI()Z

    move-result p1

    if-nez p1, :cond_2

    .line 107
    invoke-direct {p0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->requestCarPlayUI()V

    const/4 p1, 0x1

    .line 108
    invoke-virtual {p0, p1}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->setHasRequestUI(Z)V

    .line 111
    :cond_2
    :goto_1
    iget-object p1, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->application:Lcom/adayo/carplay/view/CarPlayApplication;

    iget-wide v0, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->id:J

    invoke-virtual {p1, v0, v1, p0}, Lcom/adayo/carplay/view/CarPlayApplication;->setMainActivity(JLcom/adayo/carplay/view/ui/activity/MainActivity;)V

    return-void
.end method

.method protected onDestroy()V
    .locals 1

    .line 178
    invoke-super {p0}, Landroid/app/Activity;->onDestroy()V

    const-string v0, "onDestroy"

    .line 179
    invoke-static {v0}, Lcom/adayo/utility/Logger;->i(Ljava/lang/String;)V

    return-void
.end method

.method protected onNewIntent(Landroid/content/Intent;)V
    .locals 3

    invoke-super {p0, p1}, Landroid/app/Activity;->onNewIntent(Landroid/content/Intent;)V

    invoke-virtual {p0, p1}, Landroid/app/Activity;->setIntent(Landroid/content/Intent;)V

    const-string v0, "surfaceId"

    iget-wide v1, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->id:J

    invoke-virtual {p1, v0, v1, v2}, Landroid/content/Intent;->getLongExtra(Ljava/lang/String;J)J

    move-result-wide v0

    iput-wide v0, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->id:J

    iget-object p1, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->application:Lcom/adayo/carplay/view/CarPlayApplication;

    invoke-virtual {p1, v0, v1, p0}, Lcom/adayo/carplay/view/CarPlayApplication;->setMainActivity(JLcom/adayo/carplay/view/ui/activity/MainActivity;)V

    iget-object v0, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->surfaceView:Landroid/view/SurfaceView;

    invoke-virtual {v0}, Landroid/view/SurfaceView;->getHolder()Landroid/view/SurfaceHolder;

    move-result-object v0

    invoke-interface {v0}, Landroid/view/SurfaceHolder;->getSurface()Landroid/view/Surface;

    move-result-object v0

    iget-wide v1, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->id:J

    invoke-virtual {p1, v0, v1, v2}, Lcom/adayo/carplay/view/CarPlayApplication;->setSurface(Landroid/view/Surface;J)V

    return-void
.end method

.method protected onResume()V
    .locals 1

    .line 165
    invoke-super {p0}, Landroid/app/Activity;->onResume()V

    const-string v0, "onResume"

    .line 166
    invoke-static {v0}, Lcom/adayo/utility/Logger;->i(Ljava/lang/String;)V

    return-void
.end method

.method protected onStart()V
    .locals 1

    const-string v0, "onStart"

    .line 131
    invoke-static {v0}, Lcom/adayo/utility/Logger;->i(Ljava/lang/String;)V

    .line 132
    invoke-super {p0}, Landroid/app/Activity;->onStart()V

    invoke-virtual {p0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->isFinishing()Z

    move-result v0

    if-nez v0, :cond_skip_take

    const/4 v0, 0x1

    .line 133
    invoke-virtual {p0, v0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->setNeedTake(Z)V

    :cond_skip_take
    return-void
.end method

.method protected onStop()V
    .locals 1

    .line 171
    invoke-super {p0}, Landroid/app/Activity;->onStop()V

    const-string v0, "onStop"

    .line 172
    invoke-static {v0}, Lcom/adayo/utility/Logger;->i(Ljava/lang/String;)V

    const/4 v0, 0x0

    .line 173
    invoke-virtual {p0, v0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->setHasRequestUI(Z)V

    return-void
.end method

.method public onWindowFocusChanged(Z)V
    .locals 2

    .line 61
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "onWindowFocusChanged() called with: hasFocus = ["

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    const-string v1, "]"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v0}, Lcom/adayo/utility/Logger;->i(Ljava/lang/String;)V

    if-eqz p1, :cond_0

    .line 63
    invoke-virtual {p0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->hideBottomUIMenu()V

    .line 65
    :cond_0
    invoke-super {p0, p1}, Landroid/app/Activity;->onWindowFocusChanged(Z)V

    return-void
.end method

.method public setHasRequestUI(Z)V
    .locals 0

    .line 73
    iput-boolean p1, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->hasRequestUI:Z

    return-void
.end method

.method public declared-synchronized setNeedTake(Z)V
    .locals 0

    monitor-enter p0

    .line 81
    :try_start_0
    iput-boolean p1, p0, Lcom/adayo/carplay/view/ui/activity/MainActivity;->needTake:Z
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 82
    monitor-exit p0

    return-void

    :catchall_0
    move-exception p1

    .line 80
    monitor-exit p0

    throw p1
.end method
