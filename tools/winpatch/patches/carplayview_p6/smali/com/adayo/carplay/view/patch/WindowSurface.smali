.class public final Lcom/adayo/carplay/view/patch/WindowSurface;
.super Landroid/view/SurfaceView;
.source "WindowSurface.java"


# static fields
.field private static final ACC_OFF:Ljava/lang/String; = "com.device.accoff"

.field private static final ACC_ON:Ljava/lang/String; = "com.device.accon"

.field private static final LAYOUT_KEY:Ljava/lang/String; = "tbox_winpatch_layout"

.field private static final NONE:Ljava/lang/ref/WeakReference;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/lang/ref/WeakReference<",
            "Landroid/app/Activity;",
            ">;"
        }
    .end annotation
.end field

.field private static final POWER_OFF:Ljava/lang/String; = "com.device.poweroff"

.field private static final POWER_ON:Ljava/lang/String; = "com.device.poweron"

.field private static final REDIRECTED:Ljava/lang/String; = "com.adayo.carplay.view.patch.REDIRECTED"

.field private static volatile accWatchReady:Z

.field private static volatile pendingSurfaceId:J

.field private static receiverRegistered:Z

.field private static volatile shown:Ljava/lang/ref/WeakReference;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/lang/ref/WeakReference<",
            "Landroid/app/Activity;",
            ">;"
        }
    .end annotation
.end field

.field private static volatile suppressUi:Z


# instance fields
.field private lastX:I

.field private lastY:I

.field private tracking:Z


# direct methods
.method static constructor <clinit>()V
    .locals 2

    .line 29
    const-wide/16 v0, 0x0

    sput-wide v0, Lcom/adayo/carplay/view/patch/WindowSurface;->pendingSurfaceId:J

    .line 34
    new-instance v0, Ljava/lang/ref/WeakReference;

    const/4 v1, 0x0

    invoke-direct {v0, v1}, Ljava/lang/ref/WeakReference;-><init>(Ljava/lang/Object;)V

    sput-object v0, Lcom/adayo/carplay/view/patch/WindowSurface;->NONE:Ljava/lang/ref/WeakReference;

    .line 35
    sget-object v0, Lcom/adayo/carplay/view/patch/WindowSurface;->NONE:Ljava/lang/ref/WeakReference;

    sput-object v0, Lcom/adayo/carplay/view/patch/WindowSurface;->shown:Ljava/lang/ref/WeakReference;

    return-void
.end method

.method public constructor <init>(Landroid/content/Context;Landroid/util/AttributeSet;)V
    .locals 0

    .line 134
    invoke-direct {p0, p1, p2}, Landroid/view/SurfaceView;-><init>(Landroid/content/Context;Landroid/util/AttributeSet;)V

    .line 135
    return-void
.end method

.method static synthetic access$000()Z
    .locals 1

    .line 25
    sget-boolean v0, Lcom/adayo/carplay/view/patch/WindowSurface;->accWatchReady:Z

    return v0
.end method

.method static synthetic access$002(Z)Z
    .locals 0

    .line 25
    sput-boolean p0, Lcom/adayo/carplay/view/patch/WindowSurface;->accWatchReady:Z

    return p0
.end method

.method static synthetic access$102(Z)Z
    .locals 0

    .line 25
    sput-boolean p0, Lcom/adayo/carplay/view/patch/WindowSurface;->suppressUi:Z

    return p0
.end method

.method static synthetic access$200(Landroid/content/Context;)V
    .locals 0

    .line 25
    invoke-static {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->releaseUi(Landroid/content/Context;)V

    return-void
.end method

.method private static alreadyRedirected(Landroid/app/Activity;)Z
    .locals 2

    .line 172
    invoke-virtual {p0}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;

    move-result-object p0

    .line 173
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

    .line 109
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

    .line 110
    :catch_0
    move-exception p0

    .line 111
    const-string v0, "CPViewPatch"

    const-string v1, "layout read failed"

    invoke-static {v0, v1, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 112
    const/4 p0, 0x0

    return p0
.end method

.method private static findWindowDisplayId(Landroid/content/Context;)I
    .locals 5

    .line 138
    const-string v0, "display"

    invoke-virtual {p0, v0}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object p0

    check-cast p0, Landroid/hardware/display/DisplayManager;

    .line 139
    if-eqz p0, :cond_1

    .line 140
    invoke-virtual {p0}, Landroid/hardware/display/DisplayManager;->getDisplays()[Landroid/view/Display;

    move-result-object p0

    array-length v0, p0

    const/4 v1, 0x0

    :goto_0
    if-ge v1, v0, :cond_1

    aget-object v2, p0, v1

    .line 141
    invoke-virtual {v2}, Landroid/view/Display;->getDisplayId()I

    move-result v3

    if-lez v3, :cond_0

    invoke-virtual {v2}, Landroid/view/Display;->getName()Ljava/lang/String;

    move-result-object v3

    const-string v4, "ActivityViewVirtualDisplay@"

    invoke-virtual {v3, v4}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v3

    if-eqz v3, :cond_0

    .line 142
    invoke-virtual {v2}, Landroid/view/Display;->getDisplayId()I

    move-result p0

    return p0

    .line 140
    :cond_0
    add-int/lit8 v1, v1, 0x1

    goto :goto_0

    .line 146
    :cond_1
    const/4 p0, -0x1

    return p0
.end method

.method public static interceptOnCreate(Lcom/adayo/carplay/view/ui/activity/MainActivity;)Z
    .locals 8

    .line 177
    invoke-static {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->watchAccOff(Landroid/content/Context;)V

    .line 178
    new-instance v0, Ljava/lang/ref/WeakReference;

    invoke-direct {v0, p0}, Ljava/lang/ref/WeakReference;-><init>(Ljava/lang/Object;)V

    sput-object v0, Lcom/adayo/carplay/view/patch/WindowSurface;->shown:Ljava/lang/ref/WeakReference;

    .line 179
    sget-boolean v0, Lcom/adayo/carplay/view/patch/WindowSurface;->suppressUi:Z

    const/4 v1, 0x1

    const-string v2, "CPViewPatch"

    const/4 v3, 0x0

    if-eqz v0, :cond_0

    .line 180
    const-string v0, "interceptOnCreate suppressed, acc off"

    invoke-static {v2, v0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 181
    invoke-virtual {p0, v3}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->setNeedTake(Z)V

    .line 182
    invoke-virtual {p0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->finish()V

    .line 183
    return v1

    .line 185
    :cond_0
    invoke-virtual {p0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->getWindowManager()Landroid/view/WindowManager;

    move-result-object v0

    invoke-interface {v0}, Landroid/view/WindowManager;->getDefaultDisplay()Landroid/view/Display;

    move-result-object v0

    invoke-virtual {v0}, Landroid/view/Display;->getDisplayId()I

    move-result v0

    .line 186
    invoke-virtual {p0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->getIntent()Landroid/content/Intent;

    move-result-object v4

    .line 187
    invoke-static {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->barsMode(Landroid/content/Context;)Z

    move-result v5

    if-eqz v5, :cond_3

    .line 188
    if-nez v0, :cond_1

    .line 189
    invoke-static {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->keepBars(Landroid/app/Activity;)V

    .line 190
    return v3

    .line 192
    :cond_1
    invoke-static {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->alreadyRedirected(Landroid/app/Activity;)Z

    move-result v5

    if-eqz v5, :cond_2

    .line 193
    const-string p0, "interceptOnCreate: bars redirect did not reach the main display"

    invoke-static {v2, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    .line 194
    return v3

    .line 196
    :cond_2
    invoke-static {v4}, Lcom/adayo/carplay/view/patch/WindowSurface;->rememberSurfaceId(Landroid/content/Intent;)V

    .line 197
    invoke-virtual {p0, v3}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->setNeedTake(Z)V

    .line 198
    invoke-virtual {p0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->finish()V

    .line 200
    :try_start_0
    invoke-virtual {p0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->getApplicationContext()Landroid/content/Context;

    move-result-object p0

    invoke-static {p0, v4, v3, v3}, Lcom/adayo/carplay/view/patch/WindowSurface;->startOnDisplay(Landroid/content/Context;Landroid/content/Intent;IZ)V

    .line 201
    new-instance p0, Ljava/lang/StringBuilder;

    invoke-direct {p0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "interceptOnCreate display="

    invoke-virtual {p0, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p0

    const-string v0, " -> main"

    invoke-virtual {p0, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    invoke-static {v2, p0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_0
    .catch Ljava/lang/RuntimeException; {:try_start_0 .. :try_end_0} :catch_0

    .line 204
    goto :goto_0

    .line 202
    :catch_0
    move-exception p0

    .line 203
    const-string v0, "interceptOnCreate bars redirect failed"

    invoke-static {v2, v0, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 205
    :goto_0
    return v1

    .line 207
    :cond_3
    if-lez v0, :cond_4

    .line 208
    return v3

    .line 210
    :cond_4
    invoke-static {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->alreadyRedirected(Landroid/app/Activity;)Z

    move-result v0

    if-eqz v0, :cond_5

    .line 211
    const-string p0, "interceptOnCreate: redirect already attempted, staying on display=0"

    invoke-static {v2, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    .line 212
    return v3

    .line 214
    :cond_5
    invoke-static {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->findWindowDisplayId(Landroid/content/Context;)I

    move-result v0

    .line 215
    if-gtz v0, :cond_6

    .line 216
    const-string p0, "interceptOnCreate: virtual display not ready, allowing display=0"

    invoke-static {v2, p0}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

    .line 217
    return v3

    .line 219
    :cond_6
    invoke-static {v4}, Lcom/adayo/carplay/view/patch/WindowSurface;->rememberSurfaceId(Landroid/content/Intent;)V

    .line 220
    const-wide/16 v5, 0x0

    if-eqz v4, :cond_7

    const-string v7, "surfaceId"

    invoke-virtual {v4, v7, v5, v6}, Landroid/content/Intent;->getLongExtra(Ljava/lang/String;J)J

    move-result-wide v5

    .line 221
    :cond_7
    invoke-virtual {p0, v3}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->setNeedTake(Z)V

    .line 222
    invoke-virtual {p0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->finish()V

    .line 224
    :try_start_1
    invoke-virtual {p0}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->getApplicationContext()Landroid/content/Context;

    move-result-object p0

    invoke-static {p0, v4, v0, v1}, Lcom/adayo/carplay/view/patch/WindowSurface;->startOnDisplay(Landroid/content/Context;Landroid/content/Intent;IZ)V

    .line 225
    new-instance p0, Ljava/lang/StringBuilder;

    invoke-direct {p0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "interceptOnCreate display=0 -> display="

    invoke-virtual {p0, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p0

    const-string v0, " surfaceId="

    invoke-virtual {p0, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v5, v6}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    invoke-static {v2, p0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_1
    .catch Ljava/lang/RuntimeException; {:try_start_1 .. :try_end_1} :catch_1

    .line 228
    goto :goto_1

    .line 226
    :catch_1
    move-exception p0

    .line 227
    const-string v0, "interceptOnCreate redirect failed"

    invoke-static {v2, v0, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 229
    :goto_1
    return v1
.end method

.method public static keepBars(Landroid/app/Activity;)V
    .locals 1

    .line 118
    invoke-static {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->barsMode(Landroid/content/Context;)Z

    move-result v0

    if-nez v0, :cond_0

    return-void

    .line 119
    :cond_0
    invoke-virtual {p0}, Landroid/app/Activity;->getWindowManager()Landroid/view/WindowManager;

    move-result-object v0

    invoke-interface {v0}, Landroid/view/WindowManager;->getDefaultDisplay()Landroid/view/Display;

    move-result-object v0

    invoke-virtual {v0}, Landroid/view/Display;->getDisplayId()I

    move-result v0

    if-eqz v0, :cond_1

    return-void

    .line 120
    :cond_1
    invoke-virtual {p0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;

    move-result-object p0

    .line 121
    const/16 v0, 0x600

    invoke-virtual {p0, v0}, Landroid/view/Window;->clearFlags(I)V

    .line 123
    const v0, -0x7ffff800

    invoke-virtual {p0, v0}, Landroid/view/Window;->addFlags(I)V

    .line 125
    invoke-virtual {p0}, Landroid/view/Window;->getDecorView()Landroid/view/View;

    move-result-object p0

    const/4 v0, 0x0

    invoke-virtual {p0, v0}, Landroid/view/View;->setSystemUiVisibility(I)V

    .line 126
    const-string p0, "CPViewPatch"

    const-string v0, "keepBars"

    invoke-static {p0, v0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 127
    return-void
.end method

.method private static releaseUi(Landroid/content/Context;)V
    .locals 3

    .line 89
    sget-object v0, Lcom/adayo/carplay/view/patch/WindowSurface;->shown:Ljava/lang/ref/WeakReference;

    invoke-virtual {v0}, Ljava/lang/ref/WeakReference;->get()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/app/Activity;

    .line 90
    instance-of v1, v0, Lcom/adayo/carplay/view/ui/activity/MainActivity;

    if-eqz v1, :cond_0

    invoke-virtual {v0}, Landroid/app/Activity;->isFinishing()Z

    move-result v1

    if-nez v1, :cond_0

    .line 91
    move-object v1, v0

    check-cast v1, Lcom/adayo/carplay/view/ui/activity/MainActivity;

    const/4 v2, 0x0

    invoke-virtual {v1, v2}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->setNeedTake(Z)V

    .line 93
    :cond_0
    if-eqz v0, :cond_1

    invoke-virtual {v0}, Landroid/app/Activity;->isFinishing()Z

    move-result v1

    if-nez v1, :cond_1

    .line 94
    invoke-virtual {v0}, Landroid/app/Activity;->finish()V

    .line 96
    :cond_1
    new-instance v0, Landroid/content/Intent;

    const-string v1, "android.intent.action.MAIN"

    invoke-direct {v0, v1}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    .line 97
    const-string v1, "android.intent.category.HOME"

    invoke-virtual {v0, v1}, Landroid/content/Intent;->addCategory(Ljava/lang/String;)Landroid/content/Intent;

    .line 98
    const-string v1, "com.adayo.launcher"

    invoke-virtual {v0, v1}, Landroid/content/Intent;->setPackage(Ljava/lang/String;)Landroid/content/Intent;

    .line 99
    const/high16 v1, 0x10000000

    invoke-virtual {v0, v1}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;

    .line 101
    :try_start_0
    invoke-virtual {p0, v0}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V
    :try_end_0
    .catch Ljava/lang/RuntimeException; {:try_start_0 .. :try_end_0} :catch_0

    .line 104
    goto :goto_0

    .line 102
    :catch_0
    move-exception p0

    .line 103
    const-string v0, "CPViewPatch"

    const-string v1, "home after acc off failed"

    invoke-static {v0, v1, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 105
    :goto_0
    return-void
.end method

.method private static rememberSurfaceId(Landroid/content/Intent;)V
    .locals 5

    .line 166
    if-nez p0, :cond_0

    return-void

    .line 167
    :cond_0
    const-string v0, "surfaceId"

    const-wide/16 v1, 0x0

    invoke-virtual {p0, v0, v1, v2}, Landroid/content/Intent;->getLongExtra(Ljava/lang/String;J)J

    move-result-wide v3

    .line 168
    cmp-long p0, v3, v1

    if-eqz p0, :cond_1

    sput-wide v3, Lcom/adayo/carplay/view/patch/WindowSurface;->pendingSurfaceId:J

    .line 169
    :cond_1
    return-void
.end method

.method public static resolveSurfaceId(J)J
    .locals 3

    .line 233
    const-wide/16 v0, 0x0

    cmp-long v2, p0, v0

    if-eqz v2, :cond_0

    .line 234
    sput-wide v0, Lcom/adayo/carplay/view/patch/WindowSurface;->pendingSurfaceId:J

    .line 235
    return-wide p0

    .line 237
    :cond_0
    sget-wide p0, Lcom/adayo/carplay/view/patch/WindowSurface;->pendingSurfaceId:J

    .line 238
    sput-wide v0, Lcom/adayo/carplay/view/patch/WindowSurface;->pendingSurfaceId:J

    .line 239
    cmp-long v0, p0, v0

    if-eqz v0, :cond_1

    .line 240
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

    .line 242
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

    .line 246
    invoke-static {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->watchAccOff(Landroid/content/Context;)V

    .line 247
    sget-boolean v0, Lcom/adayo/carplay/view/patch/WindowSurface;->suppressUi:Z

    const-string v1, "CPViewPatch"

    if-eqz v0, :cond_0

    .line 248
    const-string p0, "startInWindow suppressed, acc off"

    invoke-static {v1, p0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 249
    return-void

    .line 251
    :cond_0
    const-wide/16 v2, 0x0

    if-eqz p1, :cond_1

    const-string v0, "surfaceId"

    invoke-virtual {p1, v0, v2, v3}, Landroid/content/Intent;->getLongExtra(Ljava/lang/String;J)J

    move-result-wide v4

    goto :goto_0

    :cond_1
    move-wide v4, v2

    .line 252
    :goto_0
    cmp-long v0, v4, v2

    if-eqz v0, :cond_2

    .line 253
    sput-wide v4, Lcom/adayo/carplay/view/patch/WindowSurface;->pendingSurfaceId:J

    .line 255
    :cond_2
    invoke-static {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->barsMode(Landroid/content/Context;)Z

    move-result v0

    const/4 v2, 0x0

    if-eqz v0, :cond_7

    .line 256
    invoke-interface {p2}, Ljava/util/Collection;->iterator()Ljava/util/Iterator;

    move-result-object p2

    :goto_1
    invoke-interface {p2}, Ljava/util/Iterator;->hasNext()Z

    move-result v0

    if-eqz v0, :cond_6

    invoke-interface {p2}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v0

    .line 257
    check-cast v0, Landroid/app/Activity;

    .line 258
    invoke-virtual {v0}, Landroid/app/Activity;->isFinishing()Z

    move-result v3

    if-eqz v3, :cond_3

    goto :goto_1

    .line 259
    :cond_3
    invoke-virtual {v0}, Landroid/app/Activity;->getWindowManager()Landroid/view/WindowManager;

    move-result-object v3

    invoke-interface {v3}, Landroid/view/WindowManager;->getDefaultDisplay()Landroid/view/Display;

    move-result-object v3

    invoke-virtual {v3}, Landroid/view/Display;->getDisplayId()I

    move-result v3

    if-nez v3, :cond_4

    goto :goto_1

    .line 260
    :cond_4
    instance-of v3, v0, Lcom/adayo/carplay/view/ui/activity/MainActivity;

    if-eqz v3, :cond_5

    .line 261
    move-object v3, v0

    check-cast v3, Lcom/adayo/carplay/view/ui/activity/MainActivity;

    invoke-virtual {v3, v2}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->setNeedTake(Z)V

    .line 263
    :cond_5
    invoke-virtual {v0}, Landroid/app/Activity;->finish()V

    .line 264
    goto :goto_1

    .line 265
    :cond_6
    invoke-static {p0, p1, v2, v2}, Lcom/adayo/carplay/view/patch/WindowSurface;->startOnDisplay(Landroid/content/Context;Landroid/content/Intent;IZ)V

    .line 266
    new-instance p0, Ljava/lang/StringBuilder;

    invoke-direct {p0}, Ljava/lang/StringBuilder;-><init>()V

    const-string p1, "startInWindow main surfaceId="

    invoke-virtual {p0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v4, v5}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    invoke-static {v1, p0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 267
    return-void

    .line 269
    :cond_7
    nop

    .line 270
    invoke-interface {p2}, Ljava/util/Collection;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :goto_2
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v3

    if-eqz v3, :cond_9

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v3

    .line 271
    check-cast v3, Landroid/app/Activity;

    .line 272
    invoke-virtual {v3}, Landroid/app/Activity;->isFinishing()Z

    move-result v6

    if-nez v6, :cond_8

    .line 273
    invoke-virtual {v3}, Landroid/app/Activity;->getWindowManager()Landroid/view/WindowManager;

    move-result-object v3

    invoke-interface {v3}, Landroid/view/WindowManager;->getDefaultDisplay()Landroid/view/Display;

    move-result-object v3

    invoke-virtual {v3}, Landroid/view/Display;->getDisplayId()I

    move-result v3

    .line 274
    if-lez v3, :cond_8

    .line 275
    nop

    .line 276
    goto :goto_3

    .line 279
    :cond_8
    goto :goto_2

    .line 270
    :cond_9
    const/4 v3, -0x1

    .line 280
    :goto_3
    if-lez v3, :cond_a

    .line 281
    invoke-static {}, Landroid/app/ActivityOptions;->makeBasic()Landroid/app/ActivityOptions;

    move-result-object p2

    .line 282
    invoke-virtual {p2, v3}, Landroid/app/ActivityOptions;->setLaunchDisplayId(I)Landroid/app/ActivityOptions;

    .line 283
    invoke-virtual {p2}, Landroid/app/ActivityOptions;->toBundle()Landroid/os/Bundle;

    move-result-object p2

    invoke-virtual {p0, p1, p2}, Landroid/content/Context;->startActivity(Landroid/content/Intent;Landroid/os/Bundle;)V

    .line 284
    goto :goto_5

    .line 285
    :cond_a
    invoke-interface {p2}, Ljava/util/Collection;->iterator()Ljava/util/Iterator;

    move-result-object p2

    :goto_4
    invoke-interface {p2}, Ljava/util/Iterator;->hasNext()Z

    move-result v0

    if-eqz v0, :cond_d

    invoke-interface {p2}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v0

    .line 286
    check-cast v0, Landroid/app/Activity;

    .line 287
    invoke-virtual {v0}, Landroid/app/Activity;->isFinishing()Z

    move-result v3

    if-nez v3, :cond_c

    .line 288
    instance-of v3, v0, Lcom/adayo/carplay/view/ui/activity/MainActivity;

    if-eqz v3, :cond_b

    .line 289
    move-object v3, v0

    check-cast v3, Lcom/adayo/carplay/view/ui/activity/MainActivity;

    invoke-virtual {v3, v2}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->setNeedTake(Z)V

    .line 291
    :cond_b
    invoke-virtual {v0}, Landroid/app/Activity;->finish()V

    .line 293
    :cond_c
    goto :goto_4

    .line 294
    :cond_d
    invoke-static {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->findWindowDisplayId(Landroid/content/Context;)I

    move-result v3

    .line 295
    if-lez v3, :cond_e

    .line 296
    const/4 p2, 0x1

    invoke-static {p0, p1, v3, p2}, Lcom/adayo/carplay/view/patch/WindowSurface;->startOnDisplay(Landroid/content/Context;Landroid/content/Intent;IZ)V

    .line 297
    goto :goto_5

    .line 299
    :cond_e
    new-instance p1, Landroid/content/Intent;

    const-string p2, "com.adayo.launcher.LAUNCH_APP"

    invoke-direct {p1, p2}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    .line 300
    const-string p2, "com.adayo.launcher"

    invoke-virtual {p1, p2}, Landroid/content/Intent;->setPackage(Ljava/lang/String;)Landroid/content/Intent;

    .line 301
    const-string p2, "app_pkg"

    const-string v0, "com.adayo.carplay.view"

    invoke-virtual {p1, p2, v0}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 302
    const-string p2, "app_cls"

    const-string v0, "com.adayo.carplay.view.ui.activity.MainActivity"

    invoke-virtual {p1, p2, v0}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 303
    const/high16 p2, 0x10000000

    invoke-virtual {p1, p2}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;

    .line 304
    invoke-virtual {p0, p1}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V

    .line 305
    const/4 v3, -0x2

    .line 308
    :goto_5
    new-instance p0, Ljava/lang/StringBuilder;

    invoke-direct {p0}, Ljava/lang/StringBuilder;-><init>()V

    const-string p1, "startInWindow display="

    invoke-virtual {p0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p0

    const-string p1, " surfaceId="

    invoke-virtual {p0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v4, v5}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    invoke-static {v1, p0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 309
    return-void
.end method

.method private static startOnDisplay(Landroid/content/Context;Landroid/content/Intent;IZ)V
    .locals 7

    .line 150
    new-instance v0, Landroid/content/Intent;

    if-eqz p1, :cond_0

    invoke-direct {v0, p1}, Landroid/content/Intent;-><init>(Landroid/content/Intent;)V

    goto :goto_0

    :cond_0
    const-class p1, Lcom/adayo/carplay/view/ui/activity/MainActivity;

    invoke-direct {v0, p0, p1}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    .line 151
    :goto_0
    const-string p1, "com.adayo.carplay.view"

    const-string v1, "com.adayo.carplay.view.ui.activity.MainActivity"

    invoke-virtual {v0, p1, v1}, Landroid/content/Intent;->setClassName(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 152
    nop

    .line 153
    if-eqz p3, :cond_1

    const/high16 p1, 0x18000000

    goto :goto_1

    :cond_1
    const/high16 p1, 0x10000000

    .line 154
    :goto_1
    invoke-virtual {v0, p1}, Landroid/content/Intent;->setFlags(I)Landroid/content/Intent;

    .line 155
    const-string p1, "com.adayo.carplay.view.patch.REDIRECTED"

    const/4 p3, 0x1

    invoke-virtual {v0, p1, p3}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Z)Landroid/content/Intent;

    .line 156
    sget-wide v1, Lcom/adayo/carplay/view/patch/WindowSurface;->pendingSurfaceId:J

    .line 157
    const-wide/16 v3, 0x0

    cmp-long p1, v1, v3

    if-eqz p1, :cond_2

    const-string p1, "surfaceId"

    invoke-virtual {v0, p1, v3, v4}, Landroid/content/Intent;->getLongExtra(Ljava/lang/String;J)J

    move-result-wide v5

    cmp-long p3, v5, v3

    if-nez p3, :cond_2

    .line 158
    invoke-virtual {v0, p1, v1, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;J)Landroid/content/Intent;

    .line 160
    :cond_2
    invoke-static {}, Landroid/app/ActivityOptions;->makeBasic()Landroid/app/ActivityOptions;

    move-result-object p1

    .line 161
    invoke-virtual {p1, p2}, Landroid/app/ActivityOptions;->setLaunchDisplayId(I)Landroid/app/ActivityOptions;

    .line 162
    invoke-virtual {p0}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;

    move-result-object p0

    invoke-virtual {p1}, Landroid/app/ActivityOptions;->toBundle()Landroid/os/Bundle;

    move-result-object p1

    invoke-virtual {p0, v0, p1}, Landroid/content/Context;->startActivity(Landroid/content/Intent;Landroid/os/Bundle;)V

    .line 163
    return-void
.end method

.method private static watchAccOff(Landroid/content/Context;)V
    .locals 4

    .line 46
    sget-boolean v0, Lcom/adayo/carplay/view/patch/WindowSurface;->receiverRegistered:Z

    if-eqz v0, :cond_0

    return-void

    .line 47
    :cond_0
    const/4 v0, 0x1

    sput-boolean v0, Lcom/adayo/carplay/view/patch/WindowSurface;->receiverRegistered:Z

    .line 48
    new-instance v1, Landroid/content/IntentFilter;

    invoke-direct {v1}, Landroid/content/IntentFilter;-><init>()V

    .line 49
    const-string v2, "com.device.accoff"

    invoke-virtual {v1, v2}, Landroid/content/IntentFilter;->addAction(Ljava/lang/String;)V

    .line 50
    const-string v2, "com.device.poweroff"

    invoke-virtual {v1, v2}, Landroid/content/IntentFilter;->addAction(Ljava/lang/String;)V

    .line 51
    const-string v2, "com.device.accon"

    invoke-virtual {v1, v2}, Landroid/content/IntentFilter;->addAction(Ljava/lang/String;)V

    .line 52
    const-string v2, "com.device.poweron"

    invoke-virtual {v1, v2}, Landroid/content/IntentFilter;->addAction(Ljava/lang/String;)V

    .line 53
    new-instance v2, Lcom/adayo/carplay/view/patch/WindowSurface$1;

    invoke-direct {v2}, Lcom/adayo/carplay/view/patch/WindowSurface$1;-><init>()V

    .line 68
    new-instance v3, Lcom/adayo/carplay/view/patch/WindowSurface$2;

    invoke-direct {v3, p0, v2, v1}, Lcom/adayo/carplay/view/patch/WindowSurface$2;-><init>(Landroid/content/Context;Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)V

    .line 74
    invoke-static {}, Landroid/os/Looper;->myLooper()Landroid/os/Looper;

    move-result-object p0

    invoke-static {}, Landroid/os/Looper;->getMainLooper()Landroid/os/Looper;

    move-result-object v1

    if-ne p0, v1, :cond_1

    .line 75
    invoke-interface {v3}, Ljava/lang/Runnable;->run()V

    .line 76
    sput-boolean v0, Lcom/adayo/carplay/view/patch/WindowSurface;->accWatchReady:Z

    goto :goto_0

    .line 78
    :cond_1
    new-instance p0, Landroid/os/Handler;

    invoke-static {}, Landroid/os/Looper;->getMainLooper()Landroid/os/Looper;

    move-result-object v0

    invoke-direct {p0, v0}, Landroid/os/Handler;-><init>(Landroid/os/Looper;)V

    new-instance v0, Lcom/adayo/carplay/view/patch/WindowSurface$3;

    invoke-direct {v0, v3}, Lcom/adayo/carplay/view/patch/WindowSurface$3;-><init>(Ljava/lang/Runnable;)V

    invoke-virtual {p0, v0}, Landroid/os/Handler;->post(Ljava/lang/Runnable;)Z

    .line 86
    :goto_0
    return-void
.end method


# virtual methods
.method public mapTouch(Landroid/view/MotionEvent;)[I
    .locals 10

    .line 329
    invoke-virtual {p1}, Landroid/view/MotionEvent;->getActionMasked()I

    move-result v0

    .line 330
    invoke-virtual {p1}, Landroid/view/MotionEvent;->getX()F

    move-result v1

    invoke-virtual {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->getLeft()I

    move-result v2

    int-to-float v2, v2

    sub-float/2addr v1, v2

    .line 331
    invoke-virtual {p1}, Landroid/view/MotionEvent;->getY()F

    move-result v2

    invoke-virtual {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->getTop()I

    move-result v3

    int-to-float v3, v3

    sub-float/2addr v2, v3

    .line 332
    invoke-virtual {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->getWidth()I

    move-result v3

    .line 333
    invoke-virtual {p0}, Lcom/adayo/carplay/view/patch/WindowSurface;->getHeight()I

    move-result v4

    .line 334
    const/4 v5, 0x0

    if-lez v3, :cond_9

    if-gtz v4, :cond_0

    goto/16 :goto_4

    .line 335
    :cond_0
    const/4 v6, 0x1

    const/4 v7, 0x0

    if-nez v0, :cond_2

    .line 336
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

    .line 338
    :cond_2
    iget-boolean v8, p0, Lcom/adayo/carplay/view/patch/WindowSurface;->tracking:Z

    if-nez v8, :cond_3

    return-object v5

    .line 339
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

    .line 340
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

    .line 342
    const/4 v8, 0x2

    if-eq v0, v6, :cond_7

    const/4 v9, 0x3

    if-ne v0, v9, :cond_4

    goto :goto_2

    .line 345
    :cond_4
    if-eqz v0, :cond_6

    if-ne v0, v8, :cond_5

    goto :goto_1

    .line 348
    :cond_5
    return-object v5

    .line 346
    :cond_6
    :goto_1
    goto :goto_3

    .line 343
    :cond_7
    :goto_2
    iput-boolean v7, p0, Lcom/adayo/carplay/view/patch/WindowSurface;->tracking:Z

    .line 344
    move v6, v7

    .line 350
    :goto_3
    if-ne v0, v8, :cond_8

    iget v7, p0, Lcom/adayo/carplay/view/patch/WindowSurface;->lastX:I

    if-ne v1, v7, :cond_8

    iget v7, p0, Lcom/adayo/carplay/view/patch/WindowSurface;->lastY:I

    if-ne v2, v7, :cond_8

    return-object v5

    .line 351
    :cond_8
    iput v1, p0, Lcom/adayo/carplay/view/patch/WindowSurface;->lastX:I

    .line 352
    iput v2, p0, Lcom/adayo/carplay/view/patch/WindowSurface;->lastY:I

    .line 353
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

    .line 354
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

    .line 353
    const-string v0, "CPViewPatch"

    invoke-static {v0, p1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 356
    filled-new-array {v6, v1, v2}, [I

    move-result-object p1

    return-object p1

    .line 334
    :cond_9
    :goto_4
    return-object v5
.end method

.method protected onMeasure(II)V
    .locals 6

    .line 313
    invoke-static {p1}, Landroid/view/View$MeasureSpec;->getSize(I)I

    move-result v0

    .line 314
    invoke-static {p2}, Landroid/view/View$MeasureSpec;->getSize(I)I

    move-result v1

    .line 315
    if-lez v0, :cond_2

    if-gtz v1, :cond_0

    goto :goto_1

    .line 319
    :cond_0
    int-to-long p1, v0

    const-wide/16 v2, 0x438

    mul-long/2addr p1, v2

    int-to-long v2, v1

    const-wide/16 v4, 0x780

    mul-long/2addr v2, v4

    cmp-long p1, p1, v2

    if-lez p1, :cond_1

    .line 320
    mul-int/lit16 p1, v1, 0x780

    div-int/lit16 v0, p1, 0x438

    goto :goto_0

    .line 322
    :cond_1
    mul-int/lit16 p1, v0, 0x438

    div-int/lit16 v1, p1, 0x780

    .line 324
    :goto_0
    invoke-virtual {p0, v0, v1}, Lcom/adayo/carplay/view/patch/WindowSurface;->setMeasuredDimension(II)V

    .line 325
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

    .line 326
    return-void

    .line 316
    :cond_2
    :goto_1
    invoke-super {p0, p1, p2}, Landroid/view/SurfaceView;->onMeasure(II)V

    .line 317
    return-void
.end method
