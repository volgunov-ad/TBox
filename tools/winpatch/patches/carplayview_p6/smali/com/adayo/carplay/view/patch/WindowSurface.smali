.class public final Lcom/adayo/carplay/view/patch/WindowSurface;
.super Landroid/view/SurfaceView;
.source "WindowSurface.java"


# static fields
.field private static final REDIRECTED:Ljava/lang/String; = "com.adayo.carplay.view.patch.REDIRECTED"

.field private static volatile pendingSurfaceId:J


# instance fields
.field private lastX:I

.field private lastY:I

.field private tracking:Z


# direct methods
.method static constructor <clinit>()V
    .locals 2

    .line 18
    const-wide/16 v0, 0x0

    sput-wide v0, Lcom/adayo/carplay/view/patch/WindowSurface;->pendingSurfaceId:J

    return-void
.end method

.method public constructor <init>(Landroid/content/Context;Landroid/util/AttributeSet;)V
    .locals 0

    .line 25
    invoke-direct {p0, p1, p2}, Landroid/view/SurfaceView;-><init>(Landroid/content/Context;Landroid/util/AttributeSet;)V

    .line 26
    return-void
.end method

.method private static findWindowDisplayId(Landroid/content/Context;)I
    .locals 5

    .line 29
    const-string v0, "display"

    invoke-virtual {p0, v0}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object p0

    check-cast p0, Landroid/hardware/display/DisplayManager;

    .line 30
    if-eqz p0, :cond_1

    .line 31
    invoke-virtual {p0}, Landroid/hardware/display/DisplayManager;->getDisplays()[Landroid/view/Display;

    move-result-object p0

    array-length v0, p0

    const/4 v1, 0x0

    :goto_0
    if-ge v1, v0, :cond_1

    aget-object v2, p0, v1

    .line 32
    invoke-virtual {v2}, Landroid/view/Display;->getDisplayId()I

    move-result v3

    if-lez v3, :cond_0

    invoke-virtual {v2}, Landroid/view/Display;->getName()Ljava/lang/String;

    move-result-object v3

    const-string v4, "ActivityViewVirtualDisplay@"

    invoke-virtual {v3, v4}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v3

    if-eqz v3, :cond_0

    .line 33
    invoke-virtual {v2}, Landroid/view/Display;->getDisplayId()I

    move-result p0

    return p0

    .line 31
    :cond_0
    add-int/lit8 v1, v1, 0x1

    goto :goto_0

    .line 37
    :cond_1
    const/4 p0, -0x1

    return p0
.end method

.method public static interceptOnCreate(Lcom/adayo/carplay/view/ui/activity/MainActivity;)Z
    .locals 8

    .line 55
    invoke-virtual {p0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->getWindowManager()Landroid/view/WindowManager;

    move-result-object v0

    invoke-interface {v0}, Landroid/view/WindowManager;->getDefaultDisplay()Landroid/view/Display;

    move-result-object v0

    invoke-virtual {v0}, Landroid/view/Display;->getDisplayId()I

    move-result v0

    .line 56
    const/4 v1, 0x0

    if-lez v0, :cond_0

    .line 57
    return v1

    .line 59
    :cond_0
    invoke-virtual {p0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->getIntent()Landroid/content/Intent;

    move-result-object v0

    .line 60
    const-string v2, "CPViewPatch"

    if-eqz v0, :cond_1

    const-string v3, "com.adayo.carplay.view.patch.REDIRECTED"

    invoke-virtual {v0, v3, v1}, Landroid/content/Intent;->getBooleanExtra(Ljava/lang/String;Z)Z

    move-result v3

    if-eqz v3, :cond_1

    .line 61
    const-string p0, "interceptOnCreate: redirect already attempted, staying on display=0"

    invoke-static {v2, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    .line 62
    return v1

    .line 64
    :cond_1
    invoke-static {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->findWindowDisplayId(Landroid/content/Context;)I

    move-result v3

    .line 65
    if-gtz v3, :cond_2

    .line 66
    const-string p0, "interceptOnCreate: virtual display not ready, allowing display=0"

    invoke-static {v2, p0}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

    .line 67
    return v1

    .line 69
    :cond_2
    nop

    .line 70
    const-wide/16 v4, 0x0

    if-eqz v0, :cond_4

    .line 71
    const-string v6, "surfaceId"

    invoke-virtual {v0, v6, v4, v5}, Landroid/content/Intent;->getLongExtra(Ljava/lang/String;J)J

    move-result-wide v6

    .line 72
    cmp-long v4, v6, v4

    if-eqz v4, :cond_3

    .line 73
    sput-wide v6, Lcom/adayo/carplay/view/patch/WindowSurface;->pendingSurfaceId:J

    .line 76
    :cond_3
    move-wide v4, v6

    :cond_4
    invoke-virtual {p0, v1}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->setNeedTake(Z)V

    .line 77
    invoke-virtual {p0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->finish()V

    .line 79
    :try_start_0
    invoke-virtual {p0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->getApplicationContext()Landroid/content/Context;

    move-result-object p0

    invoke-static {p0, v0, v3}, Lcom/adayo/carplay/view/patch/WindowSurface;->startOnDisplay(Landroid/content/Context;Landroid/content/Intent;I)V

    .line 80
    new-instance p0, Ljava/lang/StringBuilder;

    invoke-direct {p0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v0, "interceptOnCreate display=0 -> display="

    invoke-virtual {p0, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p0

    const-string v0, " surfaceId="

    invoke-virtual {p0, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v4, v5}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    invoke-static {v2, p0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_0
    .catch Ljava/lang/RuntimeException; {:try_start_0 .. :try_end_0} :catch_0

    .line 83
    goto :goto_0

    .line 81
    :catch_0
    move-exception p0

    .line 82
    const-string v0, "interceptOnCreate redirect failed"

    invoke-static {v2, v0, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 84
    :goto_0
    const/4 p0, 0x1

    return p0
.end method

.method public static resolveSurfaceId(J)J
    .locals 3

    .line 88
    const-wide/16 v0, 0x0

    cmp-long v2, p0, v0

    if-eqz v2, :cond_0

    .line 89
    sput-wide v0, Lcom/adayo/carplay/view/patch/WindowSurface;->pendingSurfaceId:J

    .line 90
    return-wide p0

    .line 92
    :cond_0
    sget-wide p0, Lcom/adayo/carplay/view/patch/WindowSurface;->pendingSurfaceId:J

    .line 93
    sput-wide v0, Lcom/adayo/carplay/view/patch/WindowSurface;->pendingSurfaceId:J

    .line 94
    cmp-long v0, p0, v0

    if-eqz v0, :cond_1

    .line 95
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "resolveSurfaceId restored pending="

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {v0, p0, p1}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    const-string v1, "CPViewPatch"

    invoke-static {v1, v0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 97
    :cond_1
    return-wide p0
.end method

.method public static startInWindow(Landroid/content/Context;Landroid/content/Intent;Ljava/util/Collection;)V
    .locals 5
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Landroid/content/Context;",
            "Landroid/content/Intent;",
            "Ljava/util/Collection<",
            "*>;)V"
        }
    .end annotation

    .line 101
    const-wide/16 v0, 0x0

    if-eqz p1, :cond_0

    const-string v2, "surfaceId"

    invoke-virtual {p1, v2, v0, v1}, Landroid/content/Intent;->getLongExtra(Ljava/lang/String;J)J

    move-result-wide v2

    goto :goto_0

    :cond_0
    move-wide v2, v0

    .line 102
    :goto_0
    cmp-long v0, v2, v0

    if-eqz v0, :cond_1

    .line 103
    sput-wide v2, Lcom/adayo/carplay/view/patch/WindowSurface;->pendingSurfaceId:J

    .line 105
    :cond_1
    nop

    .line 106
    invoke-interface {p2}, Ljava/util/Collection;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :goto_1
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_3

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    .line 107
    check-cast v1, Landroid/app/Activity;

    .line 108
    invoke-virtual {v1}, Landroid/app/Activity;->isFinishing()Z

    move-result v4

    if-nez v4, :cond_2

    .line 109
    invoke-virtual {v1}, Landroid/app/Activity;->getWindowManager()Landroid/view/WindowManager;

    move-result-object v1

    invoke-interface {v1}, Landroid/view/WindowManager;->getDefaultDisplay()Landroid/view/Display;

    move-result-object v1

    invoke-virtual {v1}, Landroid/view/Display;->getDisplayId()I

    move-result v1

    .line 110
    if-lez v1, :cond_2

    .line 111
    nop

    .line 112
    goto :goto_2

    .line 115
    :cond_2
    goto :goto_1

    .line 106
    :cond_3
    const/4 v1, -0x1

    .line 116
    :goto_2
    if-lez v1, :cond_4

    .line 117
    invoke-static {}, Landroid/app/ActivityOptions;->makeBasic()Landroid/app/ActivityOptions;

    move-result-object p2

    .line 118
    invoke-virtual {p2, v1}, Landroid/app/ActivityOptions;->setLaunchDisplayId(I)Landroid/app/ActivityOptions;

    .line 119
    invoke-virtual {p2}, Landroid/app/ActivityOptions;->toBundle()Landroid/os/Bundle;

    move-result-object p2

    invoke-virtual {p0, p1, p2}, Landroid/content/Context;->startActivity(Landroid/content/Intent;Landroid/os/Bundle;)V

    .line 120
    goto :goto_4

    .line 121
    :cond_4
    invoke-interface {p2}, Ljava/util/Collection;->iterator()Ljava/util/Iterator;

    move-result-object p2

    :goto_3
    invoke-interface {p2}, Ljava/util/Iterator;->hasNext()Z

    move-result v0

    if-eqz v0, :cond_7

    invoke-interface {p2}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v0

    .line 122
    check-cast v0, Landroid/app/Activity;

    .line 123
    invoke-virtual {v0}, Landroid/app/Activity;->isFinishing()Z

    move-result v1

    if-nez v1, :cond_6

    .line 124
    instance-of v1, v0, Lcom/adayo/carplay/view/ui/activity/MainActivity;

    if-eqz v1, :cond_5

    .line 125
    move-object v1, v0

    check-cast v1, Lcom/adayo/carplay/view/ui/activity/MainActivity;

    const/4 v4, 0x0

    invoke-virtual {v1, v4}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->setNeedTake(Z)V

    .line 127
    :cond_5
    invoke-virtual {v0}, Landroid/app/Activity;->finish()V

    .line 129
    :cond_6
    goto :goto_3

    .line 130
    :cond_7
    invoke-static {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->findWindowDisplayId(Landroid/content/Context;)I

    move-result v1

    .line 131
    if-lez v1, :cond_8

    .line 132
    invoke-static {p0, p1, v1}, Lcom/adayo/carplay/view/patch/WindowSurface;->startOnDisplay(Landroid/content/Context;Landroid/content/Intent;I)V

    .line 133
    goto :goto_4

    .line 135
    :cond_8
    new-instance p1, Landroid/content/Intent;

    const-string p2, "com.adayo.launcher.LAUNCH_APP"

    invoke-direct {p1, p2}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    .line 136
    const-string p2, "com.adayo.launcher"

    invoke-virtual {p1, p2}, Landroid/content/Intent;->setPackage(Ljava/lang/String;)Landroid/content/Intent;

    .line 137
    const-string p2, "app_pkg"

    const-string v0, "com.adayo.carplay.view"

    invoke-virtual {p1, p2, v0}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 138
    const-string p2, "app_cls"

    const-string v0, "com.adayo.carplay.view.ui.activity.MainActivity"

    invoke-virtual {p1, p2, v0}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 139
    const/high16 p2, 0x10000000

    invoke-virtual {p1, p2}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;

    .line 140
    invoke-virtual {p0, p1}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V

    .line 141
    const/4 v1, -0x2

    .line 144
    :goto_4
    new-instance p0, Ljava/lang/StringBuilder;

    invoke-direct {p0}, Ljava/lang/StringBuilder;-><init>()V

    const-string p1, "startInWindow display="

    invoke-virtual {p0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p0

    const-string p1, " surfaceId="

    invoke-virtual {p0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v2, v3}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    const-string p1, "CPViewPatch"

    invoke-static {p1, p0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 145
    return-void
.end method

.method private static startOnDisplay(Landroid/content/Context;Landroid/content/Intent;I)V
    .locals 7

    .line 41
    new-instance v0, Landroid/content/Intent;

    if-eqz p1, :cond_0

    invoke-direct {v0, p1}, Landroid/content/Intent;-><init>(Landroid/content/Intent;)V

    goto :goto_0

    :cond_0
    const-class p1, Lcom/adayo/carplay/view/ui/activity/MainActivity;

    invoke-direct {v0, p0, p1}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    .line 42
    :goto_0
    const-string p1, "com.adayo.carplay.view"

    const-string v1, "com.adayo.carplay.view.ui.activity.MainActivity"

    invoke-virtual {v0, p1, v1}, Landroid/content/Intent;->setClassName(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 43
    const/high16 p1, 0x18000000

    invoke-virtual {v0, p1}, Landroid/content/Intent;->setFlags(I)Landroid/content/Intent;

    .line 44
    const-string p1, "com.adayo.carplay.view.patch.REDIRECTED"

    const/4 v1, 0x1

    invoke-virtual {v0, p1, v1}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Z)Landroid/content/Intent;

    .line 45
    sget-wide v1, Lcom/adayo/carplay/view/patch/WindowSurface;->pendingSurfaceId:J

    .line 46
    const-wide/16 v3, 0x0

    cmp-long p1, v1, v3

    if-eqz p1, :cond_1

    const-string p1, "surfaceId"

    invoke-virtual {v0, p1, v3, v4}, Landroid/content/Intent;->getLongExtra(Ljava/lang/String;J)J

    move-result-wide v5

    cmp-long v3, v5, v3

    if-nez v3, :cond_1

    .line 47
    invoke-virtual {v0, p1, v1, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;J)Landroid/content/Intent;

    .line 49
    :cond_1
    invoke-static {}, Landroid/app/ActivityOptions;->makeBasic()Landroid/app/ActivityOptions;

    move-result-object p1

    .line 50
    invoke-virtual {p1, p2}, Landroid/app/ActivityOptions;->setLaunchDisplayId(I)Landroid/app/ActivityOptions;

    .line 51
    invoke-virtual {p0}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;

    move-result-object p0

    invoke-virtual {p1}, Landroid/app/ActivityOptions;->toBundle()Landroid/os/Bundle;

    move-result-object p1

    invoke-virtual {p0, v0, p1}, Landroid/content/Context;->startActivity(Landroid/content/Intent;Landroid/os/Bundle;)V

    .line 52
    return-void
.end method


# virtual methods
.method public mapTouch(Landroid/view/MotionEvent;)[I
    .locals 10

    .line 165
    invoke-virtual {p1}, Landroid/view/MotionEvent;->getActionMasked()I

    move-result v0

    .line 166
    invoke-virtual {p1}, Landroid/view/MotionEvent;->getX()F

    move-result v1

    invoke-virtual {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->getLeft()I

    move-result v2

    int-to-float v2, v2

    sub-float/2addr v1, v2

    .line 167
    invoke-virtual {p1}, Landroid/view/MotionEvent;->getY()F

    move-result v2

    invoke-virtual {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->getTop()I

    move-result v3

    int-to-float v3, v3

    sub-float/2addr v2, v3

    .line 168
    invoke-virtual {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->getWidth()I

    move-result v3

    .line 169
    invoke-virtual {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->getHeight()I

    move-result v4

    .line 170
    const/4 v5, 0x0

    if-lez v3, :cond_9

    if-gtz v4, :cond_0

    goto/16 :goto_4

    .line 171
    :cond_0
    const/4 v6, 0x1

    const/4 v7, 0x0

    if-nez v0, :cond_2

    .line 172
    const/4 v8, 0x0

    cmpl-float v9, v1, v8

    if-ltz v9, :cond_1

    cmpl-float v8, v2, v8

    if-ltz v8, :cond_1

    int-to-float v8, v3

    cmpg-float v8, v1, v8

    if-gez v8, :cond_1

    int-to-float v8, v4

    cmpg-float v8, v2, v8

    if-gez v8, :cond_1

    move v8, v6

    goto :goto_0

    :cond_1
    move v8, v7

    :goto_0
    iput-boolean v8, p0, Lcom/adayo/carplay/view/patch/WindowSurface;->tracking:Z

    .line 174
    :cond_2
    iget-boolean v8, p0, Lcom/adayo/carplay/view/patch/WindowSurface;->tracking:Z

    if-nez v8, :cond_3

    return-object v5

    .line 175
    :cond_3
    const/high16 v8, 0x44f00000    # 1920.0f

    mul-float/2addr v1, v8

    int-to-float v8, v3

    div-float/2addr v1, v8

    float-to-int v1, v1

    const/16 v8, 0x77f

    invoke-static {v8, v1}, Ljava/lang/Math;->min(II)I

    move-result v1

    invoke-static {v7, v1}, Ljava/lang/Math;->max(II)I

    move-result v1

    .line 176
    const/high16 v8, 0x44870000    # 1080.0f

    mul-float/2addr v2, v8

    int-to-float v8, v4

    div-float/2addr v2, v8

    float-to-int v2, v2

    const/16 v8, 0x437

    invoke-static {v8, v2}, Ljava/lang/Math;->min(II)I

    move-result v2

    invoke-static {v7, v2}, Ljava/lang/Math;->max(II)I

    move-result v2

    .line 178
    const/4 v8, 0x2

    if-eq v0, v6, :cond_7

    const/4 v9, 0x3

    if-ne v0, v9, :cond_4

    goto :goto_2

    .line 181
    :cond_4
    if-eqz v0, :cond_6

    if-ne v0, v8, :cond_5

    goto :goto_1

    .line 184
    :cond_5
    return-object v5

    .line 182
    :cond_6
    :goto_1
    goto :goto_3

    .line 179
    :cond_7
    :goto_2
    iput-boolean v7, p0, Lcom/adayo/carplay/view/patch/WindowSurface;->tracking:Z

    .line 180
    move v6, v7

    .line 186
    :goto_3
    if-ne v0, v8, :cond_8

    iget v7, p0, Lcom/adayo/carplay/view/patch/WindowSurface;->lastX:I

    if-ne v1, v7, :cond_8

    iget v7, p0, Lcom/adayo/carplay/view/patch/WindowSurface;->lastY:I

    if-ne v2, v7, :cond_8

    return-object v5

    .line 187
    :cond_8
    iput v1, p0, Lcom/adayo/carplay/view/patch/WindowSurface;->lastX:I

    .line 188
    iput v2, p0, Lcom/adayo/carplay/view/patch/WindowSurface;->lastY:I

    .line 189
    new-instance v5, Ljava/lang/StringBuilder;

    invoke-direct {v5}, Ljava/lang/StringBuilder;-><init>()V

    const-string v7, "action="

    invoke-virtual {v5, v7}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v5

    invoke-virtual {v5, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v0

    const-string v5, " local="

    invoke-virtual {v0, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {p1}, Landroid/view/MotionEvent;->getX()F

    move-result v5

    invoke-virtual {v0, v5}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    move-result-object v0

    const-string v5, ","

    invoke-virtual {v0, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {p1}, Landroid/view/MotionEvent;->getY()F

    move-result p1

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    move-result-object p1

    const-string v0, " video="

    invoke-virtual {p1, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p1

    .line 190
    invoke-virtual {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->getLeft()I

    move-result v0

    invoke-virtual {p1, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p1, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->getTop()I

    move-result v0

    invoke-virtual {p1, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p1, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p1, v3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p1, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p1, v4}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p1

    const-string v0, " mapped="

    invoke-virtual {p1, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p1, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p1, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p1, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p1

    const-string v0, " pressed="

    invoke-virtual {p1, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p1, v6}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    .line 189
    const-string v0, "CPViewPatch"

    invoke-static {v0, p1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 192
    filled-new-array {v6, v1, v2}, [I

    move-result-object p1

    return-object p1

    .line 170
    :cond_9
    :goto_4
    return-object v5
.end method

.method protected onMeasure(II)V
    .locals 6

    .line 149
    invoke-static {p1}, Landroid/view/View$MeasureSpec;->getSize(I)I

    move-result v0

    .line 150
    invoke-static {p2}, Landroid/view/View$MeasureSpec;->getSize(I)I

    move-result v1

    .line 151
    if-lez v0, :cond_2

    if-gtz v1, :cond_0

    goto :goto_1

    .line 155
    :cond_0
    int-to-long p1, v0

    const-wide/16 v2, 0x438

    mul-long/2addr p1, v2

    int-to-long v2, v1

    const-wide/16 v4, 0x780

    mul-long/2addr v2, v4

    cmp-long p1, p1, v2

    if-lez p1, :cond_1

    .line 156
    mul-int/lit16 p1, v1, 0x780

    div-int/lit16 v0, p1, 0x438

    goto :goto_0

    .line 158
    :cond_1
    mul-int/lit16 p1, v0, 0x438

    div-int/lit16 v1, p1, 0x780

    .line 160
    :goto_0
    invoke-virtual {p0, v0, v1}, Lcom/adayo/carplay/view/patch/WindowSurface;->setMeasuredDimension(II)V

    .line 161
    new-instance p1, Ljava/lang/StringBuilder;

    invoke-direct {p1}, Ljava/lang/StringBuilder;-><init>()V

    const-string p2, "fit16x9="

    invoke-virtual {p1, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p1, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p1

    const-string p2, "x"

    invoke-virtual {p1, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p1, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    const-string p2, "CPViewPatch"

    invoke-static {p2, p1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 162
    return-void

    .line 152
    :cond_2
    :goto_1
    invoke-super {p0, p1, p2}, Landroid/view/SurfaceView;->onMeasure(II)V

    .line 153
    return-void
.end method
