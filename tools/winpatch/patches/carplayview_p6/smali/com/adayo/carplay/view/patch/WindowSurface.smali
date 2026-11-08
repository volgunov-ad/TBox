.class public final Lcom/adayo/carplay/view/patch/WindowSurface;
.super Landroid/view/SurfaceView;
.source "WindowSurface.java"


# static fields
.field private static final LAYOUT_KEY:Ljava/lang/String; = "tbox_winpatch_layout"

.field private static final REDIRECTED:Ljava/lang/String; = "com.adayo.carplay.view.patch.REDIRECTED"

.field private static volatile pendingSurfaceId:J


# instance fields
.field private lastX:I

.field private lastY:I

.field private tracking:Z


# direct methods
.method static constructor <clinit>()V
    .locals 2

    .line 24
    const-wide/16 v0, 0x0

    sput-wide v0, Lcom/adayo/carplay/view/patch/WindowSurface;->pendingSurfaceId:J

    return-void
.end method

.method public constructor <init>(Landroid/content/Context;Landroid/util/AttributeSet;)V
    .locals 0

    .line 53
    invoke-direct {p0, p1, p2}, Landroid/view/SurfaceView;-><init>(Landroid/content/Context;Landroid/util/AttributeSet;)V

    .line 54
    return-void
.end method

.method private static alreadyRedirected(Landroid/app/Activity;)Z
    .locals 2

    .line 91
    invoke-virtual {p0}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;

    move-result-object p0

    .line 92
    const/4 v0, 0x0

    if-eqz p0, :cond_0

    const-string v1, "com.adayo.carplay.view.patch.REDIRECTED"

    invoke-virtual {p0, v1, v0}, Landroid/content/Intent;->getBooleanExtra(Ljava/lang/String;Z)Z

    move-result p0

    if-eqz p0, :cond_0

    const/4 v0, 0x1

    :cond_0
    return v0
.end method

.method public static barsMode(Landroid/content/Context;)Z
    .locals 2

    .line 28
    :try_start_0
    const-string v0, "bars"

    invoke-virtual {p0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object p0

    const-string v1, "tbox_winpatch_layout"

    invoke-static {p0, v1}, Landroid/provider/Settings$Global;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;

    move-result-object p0

    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p0
    :try_end_0
    .catch Ljava/lang/RuntimeException; {:try_start_0 .. :try_end_0} :catch_0

    return p0

    .line 29
    :catch_0
    move-exception p0

    .line 30
    const-string v0, "CPViewPatch"

    const-string v1, "layout read failed"

    invoke-static {v0, v1, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 31
    const/4 p0, 0x0

    return p0
.end method

.method private static findWindowDisplayId(Landroid/content/Context;)I
    .locals 5

    .line 57
    const-string v0, "display"

    invoke-virtual {p0, v0}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object p0

    check-cast p0, Landroid/hardware/display/DisplayManager;

    .line 58
    if-eqz p0, :cond_1

    .line 59
    invoke-virtual {p0}, Landroid/hardware/display/DisplayManager;->getDisplays()[Landroid/view/Display;

    move-result-object p0

    array-length v0, p0

    const/4 v1, 0x0

    :goto_0
    if-ge v1, v0, :cond_1

    aget-object v2, p0, v1

    .line 60
    invoke-virtual {v2}, Landroid/view/Display;->getDisplayId()I

    move-result v3

    if-lez v3, :cond_0

    invoke-virtual {v2}, Landroid/view/Display;->getName()Ljava/lang/String;

    move-result-object v3

    const-string v4, "ActivityViewVirtualDisplay@"

    invoke-virtual {v3, v4}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v3

    if-eqz v3, :cond_0

    .line 61
    invoke-virtual {v2}, Landroid/view/Display;->getDisplayId()I

    move-result p0

    return p0

    .line 59
    :cond_0
    add-int/lit8 v1, v1, 0x1

    goto :goto_0

    .line 65
    :cond_1
    const/4 p0, -0x1

    return p0
.end method

.method public static interceptOnCreate(Lcom/adayo/carplay/view/ui/activity/MainActivity;)Z
    .locals 8

    .line 96
    invoke-virtual {p0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->getWindowManager()Landroid/view/WindowManager;

    move-result-object v0

    invoke-interface {v0}, Landroid/view/WindowManager;->getDefaultDisplay()Landroid/view/Display;

    move-result-object v0

    invoke-virtual {v0}, Landroid/view/Display;->getDisplayId()I

    move-result v0

    .line 97
    invoke-virtual {p0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->getIntent()Landroid/content/Intent;

    move-result-object v1

    .line 98
    invoke-static {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->barsMode(Landroid/content/Context;)Z

    move-result v2

    const/4 v3, 0x1

    const-string v4, "CPViewPatch"

    const/4 v5, 0x0

    if-eqz v2, :cond_2

    .line 99
    if-nez v0, :cond_0

    .line 100
    invoke-static {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->keepBars(Landroid/app/Activity;)V

    .line 101
    return v5

    .line 103
    :cond_0
    invoke-static {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->alreadyRedirected(Landroid/app/Activity;)Z

    move-result v2

    if-eqz v2, :cond_1

    .line 104
    const-string p0, "interceptOnCreate: bars redirect did not reach the main display"

    invoke-static {v4, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    .line 105
    return v5

    .line 107
    :cond_1
    invoke-static {v1}, Lcom/adayo/carplay/view/patch/WindowSurface;->rememberSurfaceId(Landroid/content/Intent;)V

    .line 108
    invoke-virtual {p0, v5}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->setNeedTake(Z)V

    .line 109
    invoke-virtual {p0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->finish()V

    .line 111
    :try_start_0
    invoke-virtual {p0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->getApplicationContext()Landroid/content/Context;

    move-result-object p0

    invoke-static {p0, v1, v5, v5}, Lcom/adayo/carplay/view/patch/WindowSurface;->startOnDisplay(Landroid/content/Context;Landroid/content/Intent;IZ)V

    .line 112
    new-instance p0, Ljava/lang/StringBuilder;

    invoke-direct {p0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "interceptOnCreate display="

    invoke-virtual {p0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p0

    const-string v0, " -> main"

    invoke-virtual {p0, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    invoke-static {v4, p0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_0
    .catch Ljava/lang/RuntimeException; {:try_start_0 .. :try_end_0} :catch_0

    .line 115
    goto :goto_0

    .line 113
    :catch_0
    move-exception p0

    .line 114
    const-string v0, "interceptOnCreate bars redirect failed"

    invoke-static {v4, v0, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 116
    :goto_0
    return v3

    .line 118
    :cond_2
    if-lez v0, :cond_3

    .line 119
    return v5

    .line 121
    :cond_3
    invoke-static {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->alreadyRedirected(Landroid/app/Activity;)Z

    move-result v0

    if-eqz v0, :cond_4

    .line 122
    const-string p0, "interceptOnCreate: redirect already attempted, staying on display=0"

    invoke-static {v4, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    .line 123
    return v5

    .line 125
    :cond_4
    invoke-static {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->findWindowDisplayId(Landroid/content/Context;)I

    move-result v0

    .line 126
    if-gtz v0, :cond_5

    .line 127
    const-string p0, "interceptOnCreate: virtual display not ready, allowing display=0"

    invoke-static {v4, p0}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

    .line 128
    return v5

    .line 130
    :cond_5
    invoke-static {v1}, Lcom/adayo/carplay/view/patch/WindowSurface;->rememberSurfaceId(Landroid/content/Intent;)V

    .line 131
    const-wide/16 v6, 0x0

    if-eqz v1, :cond_6

    const-string v2, "surfaceId"

    invoke-virtual {v1, v2, v6, v7}, Landroid/content/Intent;->getLongExtra(Ljava/lang/String;J)J

    move-result-wide v6

    .line 132
    :cond_6
    invoke-virtual {p0, v5}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->setNeedTake(Z)V

    .line 133
    invoke-virtual {p0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->finish()V

    .line 135
    :try_start_1
    invoke-virtual {p0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->getApplicationContext()Landroid/content/Context;

    move-result-object p0

    invoke-static {p0, v1, v0, v3}, Lcom/adayo/carplay/view/patch/WindowSurface;->startOnDisplay(Landroid/content/Context;Landroid/content/Intent;IZ)V

    .line 136
    new-instance p0, Ljava/lang/StringBuilder;

    invoke-direct {p0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "interceptOnCreate display=0 -> display="

    invoke-virtual {p0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p0

    const-string v0, " surfaceId="

    invoke-virtual {p0, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v6, v7}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    invoke-static {v4, p0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_1
    .catch Ljava/lang/RuntimeException; {:try_start_1 .. :try_end_1} :catch_1

    .line 139
    goto :goto_1

    .line 137
    :catch_1
    move-exception p0

    .line 138
    const-string v0, "interceptOnCreate redirect failed"

    invoke-static {v4, v0, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 140
    :goto_1
    return v3
.end method

.method public static keepBars(Landroid/app/Activity;)V
    .locals 1

    .line 37
    invoke-static {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->barsMode(Landroid/content/Context;)Z

    move-result v0

    if-nez v0, :cond_0

    return-void

    .line 38
    :cond_0
    invoke-virtual {p0}, Landroid/app/Activity;->getWindowManager()Landroid/view/WindowManager;

    move-result-object v0

    invoke-interface {v0}, Landroid/view/WindowManager;->getDefaultDisplay()Landroid/view/Display;

    move-result-object v0

    invoke-virtual {v0}, Landroid/view/Display;->getDisplayId()I

    move-result v0

    if-eqz v0, :cond_1

    return-void

    .line 39
    :cond_1
    invoke-virtual {p0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;

    move-result-object p0

    .line 40
    const/16 v0, 0x600

    invoke-virtual {p0, v0}, Landroid/view/Window;->clearFlags(I)V

    .line 42
    const v0, -0x7ffff800

    invoke-virtual {p0, v0}, Landroid/view/Window;->addFlags(I)V

    .line 44
    invoke-virtual {p0}, Landroid/view/Window;->getDecorView()Landroid/view/View;

    move-result-object p0

    const/4 v0, 0x0

    invoke-virtual {p0, v0}, Landroid/view/View;->setSystemUiVisibility(I)V

    .line 45
    const-string p0, "CPViewPatch"

    const-string v0, "keepBars"

    invoke-static {p0, v0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 46
    return-void
.end method

.method private static rememberSurfaceId(Landroid/content/Intent;)V
    .locals 5

    .line 85
    if-nez p0, :cond_0

    return-void

    .line 86
    :cond_0
    const-string v0, "surfaceId"

    const-wide/16 v1, 0x0

    invoke-virtual {p0, v0, v1, v2}, Landroid/content/Intent;->getLongExtra(Ljava/lang/String;J)J

    move-result-wide v3

    .line 87
    cmp-long p0, v3, v1

    if-eqz p0, :cond_1

    sput-wide v3, Lcom/adayo/carplay/view/patch/WindowSurface;->pendingSurfaceId:J

    .line 88
    :cond_1
    return-void
.end method

.method public static resolveSurfaceId(J)J
    .locals 3

    .line 144
    const-wide/16 v0, 0x0

    cmp-long v2, p0, v0

    if-eqz v2, :cond_0

    .line 145
    sput-wide v0, Lcom/adayo/carplay/view/patch/WindowSurface;->pendingSurfaceId:J

    .line 146
    return-wide p0

    .line 148
    :cond_0
    sget-wide p0, Lcom/adayo/carplay/view/patch/WindowSurface;->pendingSurfaceId:J

    .line 149
    sput-wide v0, Lcom/adayo/carplay/view/patch/WindowSurface;->pendingSurfaceId:J

    .line 150
    cmp-long v0, p0, v0

    if-eqz v0, :cond_1

    .line 151
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

    .line 153
    :cond_1
    return-wide p0
.end method

.method public static startInWindow(Landroid/content/Context;Landroid/content/Intent;Ljava/util/Collection;)V
    .locals 7
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Landroid/content/Context;",
            "Landroid/content/Intent;",
            "Ljava/util/Collection<",
            "*>;)V"
        }
    .end annotation

    .line 157
    const-wide/16 v0, 0x0

    if-eqz p1, :cond_0

    const-string v2, "surfaceId"

    invoke-virtual {p1, v2, v0, v1}, Landroid/content/Intent;->getLongExtra(Ljava/lang/String;J)J

    move-result-wide v2

    goto :goto_0

    :cond_0
    move-wide v2, v0

    .line 158
    :goto_0
    cmp-long v0, v2, v0

    if-eqz v0, :cond_1

    .line 159
    sput-wide v2, Lcom/adayo/carplay/view/patch/WindowSurface;->pendingSurfaceId:J

    .line 161
    :cond_1
    invoke-static {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->barsMode(Landroid/content/Context;)Z

    move-result v0

    const-string v1, "CPViewPatch"

    const/4 v4, 0x0

    if-eqz v0, :cond_6

    .line 162
    invoke-interface {p2}, Ljava/util/Collection;->iterator()Ljava/util/Iterator;

    move-result-object p2

    :goto_1
    invoke-interface {p2}, Ljava/util/Iterator;->hasNext()Z

    move-result v0

    if-eqz v0, :cond_5

    invoke-interface {p2}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v0

    .line 163
    check-cast v0, Landroid/app/Activity;

    .line 164
    invoke-virtual {v0}, Landroid/app/Activity;->isFinishing()Z

    move-result v5

    if-eqz v5, :cond_2

    goto :goto_1

    .line 165
    :cond_2
    invoke-virtual {v0}, Landroid/app/Activity;->getWindowManager()Landroid/view/WindowManager;

    move-result-object v5

    invoke-interface {v5}, Landroid/view/WindowManager;->getDefaultDisplay()Landroid/view/Display;

    move-result-object v5

    invoke-virtual {v5}, Landroid/view/Display;->getDisplayId()I

    move-result v5

    if-nez v5, :cond_3

    goto :goto_1

    .line 166
    :cond_3
    instance-of v5, v0, Lcom/adayo/carplay/view/ui/activity/MainActivity;

    if-eqz v5, :cond_4

    .line 167
    move-object v5, v0

    check-cast v5, Lcom/adayo/carplay/view/ui/activity/MainActivity;

    invoke-virtual {v5, v4}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->setNeedTake(Z)V

    .line 169
    :cond_4
    invoke-virtual {v0}, Landroid/app/Activity;->finish()V

    .line 170
    goto :goto_1

    .line 171
    :cond_5
    invoke-static {p0, p1, v4, v4}, Lcom/adayo/carplay/view/patch/WindowSurface;->startOnDisplay(Landroid/content/Context;Landroid/content/Intent;IZ)V

    .line 172
    new-instance p0, Ljava/lang/StringBuilder;

    invoke-direct {p0}, Ljava/lang/StringBuilder;-><init>()V

    const-string p1, "startInWindow main surfaceId="

    invoke-virtual {p0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v2, v3}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    invoke-static {v1, p0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 173
    return-void

    .line 175
    :cond_6
    nop

    .line 176
    invoke-interface {p2}, Ljava/util/Collection;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :goto_2
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v5

    if-eqz v5, :cond_8

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v5

    .line 177
    check-cast v5, Landroid/app/Activity;

    .line 178
    invoke-virtual {v5}, Landroid/app/Activity;->isFinishing()Z

    move-result v6

    if-nez v6, :cond_7

    .line 179
    invoke-virtual {v5}, Landroid/app/Activity;->getWindowManager()Landroid/view/WindowManager;

    move-result-object v5

    invoke-interface {v5}, Landroid/view/WindowManager;->getDefaultDisplay()Landroid/view/Display;

    move-result-object v5

    invoke-virtual {v5}, Landroid/view/Display;->getDisplayId()I

    move-result v5

    .line 180
    if-lez v5, :cond_7

    .line 181
    nop

    .line 182
    goto :goto_3

    .line 185
    :cond_7
    goto :goto_2

    .line 176
    :cond_8
    const/4 v5, -0x1

    .line 186
    :goto_3
    if-lez v5, :cond_9

    .line 187
    invoke-static {}, Landroid/app/ActivityOptions;->makeBasic()Landroid/app/ActivityOptions;

    move-result-object p2

    .line 188
    invoke-virtual {p2, v5}, Landroid/app/ActivityOptions;->setLaunchDisplayId(I)Landroid/app/ActivityOptions;

    .line 189
    invoke-virtual {p2}, Landroid/app/ActivityOptions;->toBundle()Landroid/os/Bundle;

    move-result-object p2

    invoke-virtual {p0, p1, p2}, Landroid/content/Context;->startActivity(Landroid/content/Intent;Landroid/os/Bundle;)V

    .line 190
    goto :goto_5

    .line 191
    :cond_9
    invoke-interface {p2}, Ljava/util/Collection;->iterator()Ljava/util/Iterator;

    move-result-object p2

    :goto_4
    invoke-interface {p2}, Ljava/util/Iterator;->hasNext()Z

    move-result v0

    if-eqz v0, :cond_c

    invoke-interface {p2}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v0

    .line 192
    check-cast v0, Landroid/app/Activity;

    .line 193
    invoke-virtual {v0}, Landroid/app/Activity;->isFinishing()Z

    move-result v5

    if-nez v5, :cond_b

    .line 194
    instance-of v5, v0, Lcom/adayo/carplay/view/ui/activity/MainActivity;

    if-eqz v5, :cond_a

    .line 195
    move-object v5, v0

    check-cast v5, Lcom/adayo/carplay/view/ui/activity/MainActivity;

    invoke-virtual {v5, v4}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->setNeedTake(Z)V

    .line 197
    :cond_a
    invoke-virtual {v0}, Landroid/app/Activity;->finish()V

    .line 199
    :cond_b
    goto :goto_4

    .line 200
    :cond_c
    invoke-static {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->findWindowDisplayId(Landroid/content/Context;)I

    move-result v5

    .line 201
    if-lez v5, :cond_d

    .line 202
    const/4 p2, 0x1

    invoke-static {p0, p1, v5, p2}, Lcom/adayo/carplay/view/patch/WindowSurface;->startOnDisplay(Landroid/content/Context;Landroid/content/Intent;IZ)V

    .line 203
    goto :goto_5

    .line 205
    :cond_d
    new-instance p1, Landroid/content/Intent;

    const-string p2, "com.adayo.launcher.LAUNCH_APP"

    invoke-direct {p1, p2}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    .line 206
    const-string p2, "com.adayo.launcher"

    invoke-virtual {p1, p2}, Landroid/content/Intent;->setPackage(Ljava/lang/String;)Landroid/content/Intent;

    .line 207
    const-string p2, "app_pkg"

    const-string v0, "com.adayo.carplay.view"

    invoke-virtual {p1, p2, v0}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 208
    const-string p2, "app_cls"

    const-string v0, "com.adayo.carplay.view.ui.activity.MainActivity"

    invoke-virtual {p1, p2, v0}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 209
    const/high16 p2, 0x10000000

    invoke-virtual {p1, p2}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;

    .line 210
    invoke-virtual {p0, p1}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V

    .line 211
    const/4 v5, -0x2

    .line 214
    :goto_5
    new-instance p0, Ljava/lang/StringBuilder;

    invoke-direct {p0}, Ljava/lang/StringBuilder;-><init>()V

    const-string p1, "startInWindow display="

    invoke-virtual {p0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v5}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p0

    const-string p1, " surfaceId="

    invoke-virtual {p0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v2, v3}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    invoke-static {v1, p0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 215
    return-void
.end method

.method private static startOnDisplay(Landroid/content/Context;Landroid/content/Intent;IZ)V
    .locals 7

    .line 69
    new-instance v0, Landroid/content/Intent;

    if-eqz p1, :cond_0

    invoke-direct {v0, p1}, Landroid/content/Intent;-><init>(Landroid/content/Intent;)V

    goto :goto_0

    :cond_0
    const-class p1, Lcom/adayo/carplay/view/ui/activity/MainActivity;

    invoke-direct {v0, p0, p1}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    .line 70
    :goto_0
    const-string p1, "com.adayo.carplay.view"

    const-string v1, "com.adayo.carplay.view.ui.activity.MainActivity"

    invoke-virtual {v0, p1, v1}, Landroid/content/Intent;->setClassName(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 71
    nop

    .line 72
    if-eqz p3, :cond_1

    const/high16 p1, 0x18000000

    goto :goto_1

    :cond_1
    const/high16 p1, 0x10000000

    .line 73
    :goto_1
    invoke-virtual {v0, p1}, Landroid/content/Intent;->setFlags(I)Landroid/content/Intent;

    .line 74
    const-string p1, "com.adayo.carplay.view.patch.REDIRECTED"

    const/4 p3, 0x1

    invoke-virtual {v0, p1, p3}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Z)Landroid/content/Intent;

    .line 75
    sget-wide v1, Lcom/adayo/carplay/view/patch/WindowSurface;->pendingSurfaceId:J

    .line 76
    const-wide/16 v3, 0x0

    cmp-long p1, v1, v3

    if-eqz p1, :cond_2

    const-string p1, "surfaceId"

    invoke-virtual {v0, p1, v3, v4}, Landroid/content/Intent;->getLongExtra(Ljava/lang/String;J)J

    move-result-wide v5

    cmp-long p3, v5, v3

    if-nez p3, :cond_2

    .line 77
    invoke-virtual {v0, p1, v1, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;J)Landroid/content/Intent;

    .line 79
    :cond_2
    invoke-static {}, Landroid/app/ActivityOptions;->makeBasic()Landroid/app/ActivityOptions;

    move-result-object p1

    .line 80
    invoke-virtual {p1, p2}, Landroid/app/ActivityOptions;->setLaunchDisplayId(I)Landroid/app/ActivityOptions;

    .line 81
    invoke-virtual {p0}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;

    move-result-object p0

    invoke-virtual {p1}, Landroid/app/ActivityOptions;->toBundle()Landroid/os/Bundle;

    move-result-object p1

    invoke-virtual {p0, v0, p1}, Landroid/content/Context;->startActivity(Landroid/content/Intent;Landroid/os/Bundle;)V

    .line 82
    return-void
.end method


# virtual methods
.method public mapTouch(Landroid/view/MotionEvent;)[I
    .locals 10

    .line 235
    invoke-virtual {p1}, Landroid/view/MotionEvent;->getActionMasked()I

    move-result v0

    .line 236
    invoke-virtual {p1}, Landroid/view/MotionEvent;->getX()F

    move-result v1

    invoke-virtual {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->getLeft()I

    move-result v2

    int-to-float v2, v2

    sub-float/2addr v1, v2

    .line 237
    invoke-virtual {p1}, Landroid/view/MotionEvent;->getY()F

    move-result v2

    invoke-virtual {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->getTop()I

    move-result v3

    int-to-float v3, v3

    sub-float/2addr v2, v3

    .line 238
    invoke-virtual {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->getWidth()I

    move-result v3

    .line 239
    invoke-virtual {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->getHeight()I

    move-result v4

    .line 240
    const/4 v5, 0x0

    if-lez v3, :cond_9

    if-gtz v4, :cond_0

    goto/16 :goto_4

    .line 241
    :cond_0
    const/4 v6, 0x1

    const/4 v7, 0x0

    if-nez v0, :cond_2

    .line 242
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

    .line 244
    :cond_2
    iget-boolean v8, p0, Lcom/adayo/carplay/view/patch/WindowSurface;->tracking:Z

    if-nez v8, :cond_3

    return-object v5

    .line 245
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

    .line 246
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

    .line 248
    const/4 v8, 0x2

    if-eq v0, v6, :cond_7

    const/4 v9, 0x3

    if-ne v0, v9, :cond_4

    goto :goto_2

    .line 251
    :cond_4
    if-eqz v0, :cond_6

    if-ne v0, v8, :cond_5

    goto :goto_1

    .line 254
    :cond_5
    return-object v5

    .line 252
    :cond_6
    :goto_1
    goto :goto_3

    .line 249
    :cond_7
    :goto_2
    iput-boolean v7, p0, Lcom/adayo/carplay/view/patch/WindowSurface;->tracking:Z

    .line 250
    move v6, v7

    .line 256
    :goto_3
    if-ne v0, v8, :cond_8

    iget v7, p0, Lcom/adayo/carplay/view/patch/WindowSurface;->lastX:I

    if-ne v1, v7, :cond_8

    iget v7, p0, Lcom/adayo/carplay/view/patch/WindowSurface;->lastY:I

    if-ne v2, v7, :cond_8

    return-object v5

    .line 257
    :cond_8
    iput v1, p0, Lcom/adayo/carplay/view/patch/WindowSurface;->lastX:I

    .line 258
    iput v2, p0, Lcom/adayo/carplay/view/patch/WindowSurface;->lastY:I

    .line 259
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

    .line 260
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

    .line 259
    const-string v0, "CPViewPatch"

    invoke-static {v0, p1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 262
    filled-new-array {v6, v1, v2}, [I

    move-result-object p1

    return-object p1

    .line 240
    :cond_9
    :goto_4
    return-object v5
.end method

.method protected onMeasure(II)V
    .locals 6

    .line 219
    invoke-static {p1}, Landroid/view/View$MeasureSpec;->getSize(I)I

    move-result v0

    .line 220
    invoke-static {p2}, Landroid/view/View$MeasureSpec;->getSize(I)I

    move-result v1

    .line 221
    if-lez v0, :cond_2

    if-gtz v1, :cond_0

    goto :goto_1

    .line 225
    :cond_0
    int-to-long p1, v0

    const-wide/16 v2, 0x438

    mul-long/2addr p1, v2

    int-to-long v2, v1

    const-wide/16 v4, 0x780

    mul-long/2addr v2, v4

    cmp-long p1, p1, v2

    if-lez p1, :cond_1

    .line 226
    mul-int/lit16 p1, v1, 0x780

    div-int/lit16 v0, p1, 0x438

    goto :goto_0

    .line 228
    :cond_1
    mul-int/lit16 p1, v0, 0x438

    div-int/lit16 v1, p1, 0x780

    .line 230
    :goto_0
    invoke-virtual {p0, v0, v1}, Lcom/adayo/carplay/view/patch/WindowSurface;->setMeasuredDimension(II)V

    .line 231
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

    .line 232
    return-void

    .line 222
    :cond_2
    :goto_1
    invoke-super {p0, p1, p2}, Landroid/view/SurfaceView;->onMeasure(II)V

    .line 223
    return-void
.end method
