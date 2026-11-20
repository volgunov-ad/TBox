.class public final Lcom/adayo/androidauto/patch/AAWindowPatch;
.super Ljava/lang/Object;
.source "AAWindowPatch.java"


# static fields
.field private static final ACC_OFF:Ljava/lang/String; = "com.device.accoff"

.field private static final ACC_ON:Ljava/lang/String; = "com.device.accon"

.field private static final BARS_HEIGHT:I = 0x360

.field private static final BARS_HEIGHT_MM:I = 0x80

.field private static final BARS_WIDTH:I = 0x780

.field private static final BARS_WIDTH_MM:I = 0x11d

.field static final LAYOUT_KEY:Ljava/lang/String; = "tbox_winpatch_layout"

.field private static final POWER_OFF:Ljava/lang/String; = "com.device.poweroff"

.field private static final POWER_ON:Ljava/lang/String; = "com.device.poweron"

.field private static final REDIRECTED:Ljava/lang/String; = "com.adayo.androidauto.patch.REDIRECTED"

.field private static final WINDOW_HEIGHT:I = 0x361

.field private static final WINDOW_HEIGHT_MM:I = 0x80

.field private static final WINDOW_WIDTH:I = 0x52f

.field private static final WINDOW_WIDTH_MM:I = 0xc5

.field private static volatile accWatchReady:Z

.field private static current:Ljava/lang/ref/WeakReference;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/lang/ref/WeakReference<",
            "Landroid/app/Activity;",
            ">;"
        }
    .end annotation
.end field

.field private static receiverRegistered:Z

.field private static volatile suppressUi:Z


# direct methods
.method static constructor <clinit>()V
    .locals 2

    .line 26
    new-instance v0, Ljava/lang/ref/WeakReference;

    const/4 v1, 0x0

    invoke-direct {v0, v1}, Ljava/lang/ref/WeakReference;-><init>(Ljava/lang/Object;)V

    sput-object v0, Lcom/adayo/androidauto/patch/AAWindowPatch;->current:Ljava/lang/ref/WeakReference;

    return-void
.end method

.method public constructor <init>()V
    .locals 0

    .line 25
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method static synthetic access$000()Z
    .locals 1

    .line 25
    sget-boolean v0, Lcom/adayo/androidauto/patch/AAWindowPatch;->accWatchReady:Z

    return v0
.end method

.method static synthetic access$002(Z)Z
    .locals 0

    .line 25
    sput-boolean p0, Lcom/adayo/androidauto/patch/AAWindowPatch;->accWatchReady:Z

    return p0
.end method

.method static synthetic access$102(Z)Z
    .locals 0

    .line 25
    sput-boolean p0, Lcom/adayo/androidauto/patch/AAWindowPatch;->suppressUi:Z

    return p0
.end method

.method static synthetic access$200(Landroid/content/Context;)V
    .locals 0

    .line 25
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->releaseUi(Landroid/content/Context;)V

    return-void
.end method

.method private static alreadyRedirected(Landroid/app/Activity;)Z
    .locals 2

    .line 188
    invoke-virtual {p0}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;

    move-result-object p0

    .line 189
    const/4 v0, 0x0

    if-eqz p0, :cond_0

    const-string v1, "com.adayo.androidauto.patch.REDIRECTED"

    invoke-virtual {p0, v1, v0}, Landroid/content/Intent;->getBooleanExtra(Ljava/lang/String;Z)Z

    move-result p0

    if-eqz p0, :cond_0

    const/4 v0, 0x1

    :cond_0
    return v0
.end method

.method public static applyViewport(Landroid/view/SurfaceView;Landroid/graphics/Rect;)V
    .locals 4

    .line 375
    invoke-virtual {p0}, Landroid/view/SurfaceView;->getParent()Landroid/view/ViewParent;

    move-result-object v0

    check-cast v0, Landroid/view/ViewGroup;

    .line 376
    const/4 v1, 0x1

    invoke-virtual {v0, v1}, Landroid/view/ViewGroup;->setClipChildren(Z)V

    .line 377
    invoke-virtual {v0, v1}, Landroid/view/ViewGroup;->setClipToPadding(Z)V

    .line 378
    invoke-virtual {p0}, Landroid/view/SurfaceView;->getLayoutParams()Landroid/view/ViewGroup$LayoutParams;

    move-result-object v1

    check-cast v1, Landroid/view/ViewGroup$MarginLayoutParams;

    .line 379
    invoke-virtual {v0}, Landroid/view/ViewGroup;->getWidth()I

    move-result v2

    .line 380
    invoke-virtual {v0}, Landroid/view/ViewGroup;->getHeight()I

    move-result v0

    .line 381
    invoke-virtual {p1}, Landroid/graphics/Rect;->width()I

    move-result v3

    iput v3, v1, Landroid/view/ViewGroup$MarginLayoutParams;->width:I

    .line 382
    invoke-virtual {p1}, Landroid/graphics/Rect;->height()I

    move-result v3

    iput v3, v1, Landroid/view/ViewGroup$MarginLayoutParams;->height:I

    .line 383
    iget v3, p1, Landroid/graphics/Rect;->left:I

    iput v3, v1, Landroid/view/ViewGroup$MarginLayoutParams;->leftMargin:I

    .line 384
    iget v3, p1, Landroid/graphics/Rect;->top:I

    iput v3, v1, Landroid/view/ViewGroup$MarginLayoutParams;->topMargin:I

    .line 385
    iget v3, p1, Landroid/graphics/Rect;->right:I

    sub-int v3, v2, v3

    iput v3, v1, Landroid/view/ViewGroup$MarginLayoutParams;->rightMargin:I

    .line 386
    iget v3, p1, Landroid/graphics/Rect;->bottom:I

    sub-int v3, v0, v3

    iput v3, v1, Landroid/view/ViewGroup$MarginLayoutParams;->bottomMargin:I

    .line 387
    invoke-virtual {p0, v1}, Landroid/view/SurfaceView;->setLayoutParams(Landroid/view/ViewGroup$LayoutParams;)V

    .line 388
    new-instance p0, Ljava/lang/StringBuilder;

    invoke-direct {p0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "applyViewport parent="

    invoke-virtual {p0, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p0

    const-string v2, "x"

    invoke-virtual {p0, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p0

    const-string v0, " viewport="

    invoke-virtual {p0, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    move-result-object p0

    const-string p1, " margins="

    invoke-virtual {p0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    iget p1, v1, Landroid/view/ViewGroup$MarginLayoutParams;->leftMargin:I

    invoke-virtual {p0, p1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p0

    const-string p1, ","

    invoke-virtual {p0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    iget v0, v1, Landroid/view/ViewGroup$MarginLayoutParams;->topMargin:I

    invoke-virtual {p0, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    iget v0, v1, Landroid/view/ViewGroup$MarginLayoutParams;->rightMargin:I

    invoke-virtual {p0, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    iget p1, v1, Landroid/view/ViewGroup$MarginLayoutParams;->bottomMargin:I

    invoke-virtual {p0, p1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    const-string p1, "AAWindowPatch"

    invoke-static {p1, p0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 391
    return-void
.end method

.method public static barsMode(Landroid/content/Context;)Z
    .locals 2

    .line 126
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

    .line 127
    :catch_0
    move-exception p0

    .line 128
    const-string v0, "AAWindowPatch"

    const-string v1, "layout read failed"

    invoke-static {v0, v1, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 129
    const/4 p0, 0x0

    return p0
.end method

.method public static ensureWindow(Landroid/app/Activity;)V
    .locals 4

    .line 251
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->watchAccOff(Landroid/content/Context;)V

    .line 252
    sget-boolean v0, Lcom/adayo/androidauto/patch/AAWindowPatch;->suppressUi:Z

    if-eqz v0, :cond_1

    .line 253
    invoke-virtual {p0}, Landroid/app/Activity;->isFinishing()Z

    move-result v0

    if-nez v0, :cond_0

    invoke-virtual {p0}, Landroid/app/Activity;->finish()V

    .line 254
    :cond_0
    return-void

    .line 256
    :cond_1
    invoke-virtual {p0}, Landroid/app/Activity;->getWindowManager()Landroid/view/WindowManager;

    move-result-object v0

    invoke-interface {v0}, Landroid/view/WindowManager;->getDefaultDisplay()Landroid/view/Display;

    move-result-object v0

    invoke-virtual {v0}, Landroid/view/Display;->getDisplayId()I

    move-result v0

    .line 257
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "activity display="

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v1

    const-string v2, " bars="

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->barsMode(Landroid/content/Context;)Z

    move-result v2

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    const-string v2, "AAWindowPatch"

    invoke-static {v2, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 258
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->barsMode(Landroid/content/Context;)Z

    move-result v1

    if-eqz v1, :cond_5

    .line 259
    if-nez v0, :cond_2

    .line 260
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->keepBars(Landroid/app/Activity;)V

    .line 261
    return-void

    .line 263
    :cond_2
    invoke-virtual {p0}, Landroid/app/Activity;->isFinishing()Z

    move-result v0

    if-nez v0, :cond_4

    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->alreadyRedirected(Landroid/app/Activity;)Z

    move-result v0

    if-eqz v0, :cond_3

    goto :goto_1

    .line 268
    :cond_3
    :try_start_0
    invoke-virtual {p0}, Landroid/app/Activity;->finish()V

    .line 269
    invoke-virtual {p0}, Landroid/app/Activity;->getApplicationContext()Landroid/content/Context;

    move-result-object v0

    invoke-virtual {p0}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;

    move-result-object p0

    const/4 v1, 0x0

    invoke-static {v0, p0, v1, v1}, Lcom/adayo/androidauto/patch/AAWindowPatch;->startOnDisplay(Landroid/content/Context;Landroid/content/Intent;IZ)V
    :try_end_0
    .catch Ljava/lang/RuntimeException; {:try_start_0 .. :try_end_0} :catch_0

    .line 272
    goto :goto_0

    .line 270
    :catch_0
    move-exception p0

    .line 271
    const-string v0, "bars redirect failed"

    invoke-static {v2, v0, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 273
    :goto_0
    return-void

    .line 264
    :cond_4
    :goto_1
    const-string p0, "bars redirect did not reach the main display"

    invoke-static {v2, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    .line 265
    return-void

    .line 275
    :cond_5
    if-nez v0, :cond_9

    invoke-virtual {p0}, Landroid/app/Activity;->isFinishing()Z

    move-result v0

    if-eqz v0, :cond_6

    goto :goto_3

    .line 276
    :cond_6
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->alreadyRedirected(Landroid/app/Activity;)Z

    move-result v0

    if-eqz v0, :cond_7

    .line 277
    const-string p0, "redirect did not reach window; refusing retry"

    invoke-static {v2, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    .line 278
    return-void

    .line 280
    :cond_7
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->windowDisplay(Landroid/content/Context;)I

    move-result v0

    .line 281
    if-gez v0, :cond_8

    .line 282
    const-string p0, "launcher window unavailable; refusing redirect"

    invoke-static {v2, p0}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

    .line 283
    return-void

    .line 286
    :cond_8
    :try_start_1
    invoke-virtual {p0}, Landroid/app/Activity;->finish()V

    .line 287
    invoke-virtual {p0}, Landroid/app/Activity;->getApplicationContext()Landroid/content/Context;

    move-result-object v1

    invoke-virtual {p0}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;

    move-result-object p0

    const/4 v3, 0x1

    invoke-static {v1, p0, v0, v3}, Lcom/adayo/androidauto/patch/AAWindowPatch;->startOnDisplay(Landroid/content/Context;Landroid/content/Intent;IZ)V
    :try_end_1
    .catch Ljava/lang/RuntimeException; {:try_start_1 .. :try_end_1} :catch_1

    .line 290
    goto :goto_2

    .line 288
    :catch_1
    move-exception p0

    .line 289
    const-string v0, "window redirect failed"

    invoke-static {v2, v0, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 291
    :goto_2
    return-void

    .line 275
    :cond_9
    :goto_3
    return-void
.end method

.method public static interceptOnCreate(Landroid/app/Activity;)Z
    .locals 6

    .line 193
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->watchAccOff(Landroid/content/Context;)V

    .line 194
    sget-boolean v0, Lcom/adayo/androidauto/patch/AAWindowPatch;->suppressUi:Z

    const/4 v1, 0x1

    const-string v2, "AAWindowPatch"

    if-eqz v0, :cond_0

    .line 195
    const-string v0, "interceptOnCreate suppressed, acc off"

    invoke-static {v2, v0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 196
    invoke-virtual {p0}, Landroid/app/Activity;->finish()V

    .line 197
    return v1

    .line 199
    :cond_0
    invoke-virtual {p0}, Landroid/app/Activity;->getWindowManager()Landroid/view/WindowManager;

    move-result-object v0

    invoke-interface {v0}, Landroid/view/WindowManager;->getDefaultDisplay()Landroid/view/Display;

    move-result-object v0

    invoke-virtual {v0}, Landroid/view/Display;->getDisplayId()I

    move-result v0

    .line 200
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->barsMode(Landroid/content/Context;)Z

    move-result v3

    const/4 v4, 0x0

    if-eqz v3, :cond_3

    .line 201
    if-nez v0, :cond_1

    .line 202
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->keepBars(Landroid/app/Activity;)V

    .line 203
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->track(Landroid/app/Activity;)V

    .line 204
    return v4

    .line 206
    :cond_1
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->alreadyRedirected(Landroid/app/Activity;)Z

    move-result v3

    if-eqz v3, :cond_2

    .line 207
    const-string v0, "interceptOnCreate: bars redirect did not reach the main display"

    invoke-static {v2, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    .line 208
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->track(Landroid/app/Activity;)V

    .line 209
    return v4

    .line 212
    :cond_2
    :try_start_0
    invoke-virtual {p0}, Landroid/app/Activity;->finish()V

    .line 213
    invoke-virtual {p0}, Landroid/app/Activity;->getApplicationContext()Landroid/content/Context;

    move-result-object v3

    invoke-virtual {p0}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;

    move-result-object v5

    invoke-static {v3, v5, v4, v4}, Lcom/adayo/androidauto/patch/AAWindowPatch;->startOnDisplay(Landroid/content/Context;Landroid/content/Intent;IZ)V

    .line 214
    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    const-string v5, "interceptOnCreate display="

    invoke-virtual {v3, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    invoke-virtual {v3, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v0

    const-string v3, " -> main"

    invoke-virtual {v0, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v2, v0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_0
    .catch Ljava/lang/RuntimeException; {:try_start_0 .. :try_end_0} :catch_0

    .line 215
    return v1

    .line 216
    :catch_0
    move-exception v0

    .line 217
    const-string v1, "interceptOnCreate bars redirect failed"

    invoke-static {v2, v1, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 218
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->track(Landroid/app/Activity;)V

    .line 219
    return v4

    .line 222
    :cond_3
    if-lez v0, :cond_4

    .line 223
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->track(Landroid/app/Activity;)V

    .line 224
    return v4

    .line 226
    :cond_4
    invoke-virtual {p0}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;

    move-result-object v0

    .line 227
    if-eqz v0, :cond_5

    const-string v3, "com.adayo.androidauto.patch.REDIRECTED"

    invoke-virtual {v0, v3, v4}, Landroid/content/Intent;->getBooleanExtra(Ljava/lang/String;Z)Z

    move-result v3

    if-eqz v3, :cond_5

    .line 228
    const-string v0, "interceptOnCreate: redirect did not reach window; refusing retry"

    invoke-static {v2, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    .line 229
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->track(Landroid/app/Activity;)V

    .line 230
    return v4

    .line 232
    :cond_5
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->windowDisplay(Landroid/content/Context;)I

    move-result v3

    .line 233
    if-gtz v3, :cond_6

    .line 234
    const-string v0, "interceptOnCreate: launcher window unavailable; allowing display=0"

    invoke-static {v2, v0}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

    .line 235
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->track(Landroid/app/Activity;)V

    .line 236
    return v4

    .line 239
    :cond_6
    :try_start_1
    invoke-virtual {p0}, Landroid/app/Activity;->finish()V

    .line 240
    invoke-virtual {p0}, Landroid/app/Activity;->getApplicationContext()Landroid/content/Context;

    move-result-object v5

    invoke-static {v5, v0, v3, v1}, Lcom/adayo/androidauto/patch/AAWindowPatch;->startOnDisplay(Landroid/content/Context;Landroid/content/Intent;IZ)V

    .line 241
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v5, "interceptOnCreate display=0 -> display="

    invoke-virtual {v0, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {v0, v3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v2, v0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_1
    .catch Ljava/lang/RuntimeException; {:try_start_1 .. :try_end_1} :catch_1

    .line 242
    return v1

    .line 243
    :catch_1
    move-exception v0

    .line 244
    const-string v1, "interceptOnCreate redirect failed"

    invoke-static {v2, v1, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 245
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->track(Landroid/app/Activity;)V

    .line 246
    return v4
.end method

.method public static keepBars(Landroid/app/Activity;)V
    .locals 1

    .line 151
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->barsMode(Landroid/content/Context;)Z

    move-result v0

    if-nez v0, :cond_0

    return-void

    .line 152
    :cond_0
    invoke-virtual {p0}, Landroid/app/Activity;->getWindowManager()Landroid/view/WindowManager;

    move-result-object v0

    invoke-interface {v0}, Landroid/view/WindowManager;->getDefaultDisplay()Landroid/view/Display;

    move-result-object v0

    invoke-virtual {v0}, Landroid/view/Display;->getDisplayId()I

    move-result v0

    if-eqz v0, :cond_1

    return-void

    .line 153
    :cond_1
    invoke-virtual {p0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;

    move-result-object p0

    .line 154
    const/16 v0, 0x600

    invoke-virtual {p0, v0}, Landroid/view/Window;->clearFlags(I)V

    .line 156
    const v0, -0x7ffff800

    invoke-virtual {p0, v0}, Landroid/view/Window;->addFlags(I)V

    .line 158
    invoke-virtual {p0}, Landroid/view/Window;->getDecorView()Landroid/view/View;

    move-result-object p0

    const/4 v0, 0x0

    invoke-virtual {p0, v0}, Landroid/view/View;->setSystemUiVisibility(I)V

    .line 159
    const-string p0, "AAWindowPatch"

    const-string v0, "keepBars"

    invoke-static {p0, v0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 160
    return-void
.end method

.method private static releaseUi(Landroid/content/Context;)V
    .locals 2

    .line 109
    sget-object v0, Lcom/adayo/androidauto/patch/AAWindowPatch;->current:Ljava/lang/ref/WeakReference;

    invoke-virtual {v0}, Ljava/lang/ref/WeakReference;->get()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/app/Activity;

    .line 110
    if-eqz v0, :cond_0

    invoke-virtual {v0}, Landroid/app/Activity;->isFinishing()Z

    move-result v1

    if-nez v1, :cond_0

    .line 111
    invoke-virtual {v0}, Landroid/app/Activity;->finish()V

    .line 113
    :cond_0
    new-instance v0, Landroid/content/Intent;

    const-string v1, "android.intent.action.MAIN"

    invoke-direct {v0, v1}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    .line 114
    const-string v1, "android.intent.category.HOME"

    invoke-virtual {v0, v1}, Landroid/content/Intent;->addCategory(Ljava/lang/String;)Landroid/content/Intent;

    .line 115
    const-string v1, "com.adayo.launcher"

    invoke-virtual {v0, v1}, Landroid/content/Intent;->setPackage(Ljava/lang/String;)Landroid/content/Intent;

    .line 116
    const/high16 v1, 0x10000000

    invoke-virtual {v0, v1}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;

    .line 118
    :try_start_0
    invoke-virtual {p0, v0}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V
    :try_end_0
    .catch Ljava/lang/RuntimeException; {:try_start_0 .. :try_end_0} :catch_0

    .line 121
    goto :goto_0

    .line 119
    :catch_0
    move-exception p0

    .line 120
    const-string v0, "AAWindowPatch"

    const-string v1, "home after acc off failed"

    invoke-static {v0, v1, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 122
    :goto_0
    return-void
.end method

.method public static start(Landroid/content/Context;Landroid/content/Intent;)V
    .locals 3

    .line 294
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->watchAccOff(Landroid/content/Context;)V

    .line 295
    sget-boolean v0, Lcom/adayo/androidauto/patch/AAWindowPatch;->suppressUi:Z

    const-string v1, "AAWindowPatch"

    if-eqz v0, :cond_0

    .line 296
    const-string p0, "start suppressed, acc off"

    invoke-static {v1, p0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 297
    return-void

    .line 299
    :cond_0
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->barsMode(Landroid/content/Context;)Z

    move-result v0

    if-eqz v0, :cond_2

    .line 300
    sget-object v0, Lcom/adayo/androidauto/patch/AAWindowPatch;->current:Ljava/lang/ref/WeakReference;

    invoke-virtual {v0}, Ljava/lang/ref/WeakReference;->get()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/app/Activity;

    .line 301
    if-eqz v0, :cond_1

    invoke-virtual {v0}, Landroid/app/Activity;->isFinishing()Z

    move-result v1

    if-nez v1, :cond_1

    .line 302
    invoke-virtual {v0}, Landroid/app/Activity;->getWindowManager()Landroid/view/WindowManager;

    move-result-object v1

    invoke-interface {v1}, Landroid/view/WindowManager;->getDefaultDisplay()Landroid/view/Display;

    move-result-object v1

    invoke-virtual {v1}, Landroid/view/Display;->getDisplayId()I

    move-result v1

    .line 303
    if-eqz v1, :cond_1

    invoke-virtual {v0}, Landroid/app/Activity;->finish()V

    .line 305
    :cond_1
    const/4 v0, 0x0

    invoke-static {p0, p1, v0, v0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->startOnDisplay(Landroid/content/Context;Landroid/content/Intent;IZ)V

    .line 306
    return-void

    .line 308
    :cond_2
    sget-object v0, Lcom/adayo/androidauto/patch/AAWindowPatch;->current:Ljava/lang/ref/WeakReference;

    invoke-virtual {v0}, Ljava/lang/ref/WeakReference;->get()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/app/Activity;

    .line 309
    if-eqz v0, :cond_4

    invoke-virtual {v0}, Landroid/app/Activity;->isFinishing()Z

    move-result v2

    if-nez v2, :cond_4

    .line 310
    invoke-virtual {v0}, Landroid/app/Activity;->getWindowManager()Landroid/view/WindowManager;

    move-result-object v2

    invoke-interface {v2}, Landroid/view/WindowManager;->getDefaultDisplay()Landroid/view/Display;

    move-result-object v2

    invoke-virtual {v2}, Landroid/view/Display;->getDisplayId()I

    move-result v2

    .line 311
    if-lez v2, :cond_3

    .line 312
    invoke-static {}, Landroid/app/ActivityOptions;->makeBasic()Landroid/app/ActivityOptions;

    move-result-object v0

    .line 313
    invoke-virtual {v0, v2}, Landroid/app/ActivityOptions;->setLaunchDisplayId(I)Landroid/app/ActivityOptions;

    .line 314
    invoke-virtual {p0}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;

    move-result-object p0

    invoke-virtual {v0}, Landroid/app/ActivityOptions;->toBundle()Landroid/os/Bundle;

    move-result-object v0

    invoke-virtual {p0, p1, v0}, Landroid/content/Context;->startActivity(Landroid/content/Intent;Landroid/os/Bundle;)V

    .line 315
    new-instance p0, Ljava/lang/StringBuilder;

    invoke-direct {p0}, Ljava/lang/StringBuilder;-><init>()V

    const-string p1, "start existing window display="

    invoke-virtual {p0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    invoke-static {v1, p0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 316
    return-void

    .line 318
    :cond_3
    invoke-virtual {v0}, Landroid/app/Activity;->finish()V

    .line 320
    :cond_4
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->windowDisplay(Landroid/content/Context;)I

    move-result v0

    .line 321
    if-lez v0, :cond_5

    .line 322
    const/4 v1, 0x1

    invoke-static {p0, p1, v0, v1}, Lcom/adayo/androidauto/patch/AAWindowPatch;->startOnDisplay(Landroid/content/Context;Landroid/content/Intent;IZ)V

    .line 323
    return-void

    .line 325
    :cond_5
    new-instance p1, Landroid/content/Intent;

    const-string v0, "com.adayo.launcher.LAUNCH_APP"

    invoke-direct {p1, v0}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    .line 326
    const-string v0, "com.adayo.launcher"

    invoke-virtual {p1, v0}, Landroid/content/Intent;->setPackage(Ljava/lang/String;)Landroid/content/Intent;

    .line 327
    const-string v0, "app_pkg"

    const-string v2, "com.adayo.androidauto"

    invoke-virtual {p1, v0, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 328
    const-string v0, "app_cls"

    const-string v2, "com.adayo.androidauto.ui.AAActivity"

    invoke-virtual {p1, v0, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 329
    const/high16 v0, 0x10000000

    invoke-virtual {p1, v0}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;

    .line 330
    invoke-virtual {p0, p1}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V

    .line 331
    const-string p0, "start via launcher"

    invoke-static {v1, p0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 332
    return-void
.end method

.method private static startOnDisplay(Landroid/content/Context;Landroid/content/Intent;IZ)V
    .locals 2

    .line 175
    new-instance v0, Landroid/content/Intent;

    if-eqz p1, :cond_0

    invoke-direct {v0, p1}, Landroid/content/Intent;-><init>(Landroid/content/Intent;)V

    goto :goto_0

    :cond_0
    invoke-direct {v0}, Landroid/content/Intent;-><init>()V

    .line 176
    :goto_0
    const-string p1, "com.adayo.androidauto"

    const-string v1, "com.adayo.androidauto.ui.AAActivity"

    invoke-virtual {v0, p1, v1}, Landroid/content/Intent;->setClassName(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 177
    nop

    .line 178
    if-eqz p3, :cond_1

    const/high16 p1, 0x18000000

    goto :goto_1

    :cond_1
    const/high16 p1, 0x10000000

    .line 179
    :goto_1
    invoke-virtual {v0, p1}, Landroid/content/Intent;->setFlags(I)Landroid/content/Intent;

    .line 180
    const-string p1, "com.adayo.androidauto.patch.REDIRECTED"

    const/4 p3, 0x1

    invoke-virtual {v0, p1, p3}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Z)Landroid/content/Intent;

    .line 181
    invoke-static {}, Landroid/app/ActivityOptions;->makeBasic()Landroid/app/ActivityOptions;

    move-result-object p1

    .line 182
    invoke-virtual {p1, p2}, Landroid/app/ActivityOptions;->setLaunchDisplayId(I)Landroid/app/ActivityOptions;

    .line 183
    invoke-virtual {p0}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;

    move-result-object p3

    invoke-virtual {p1}, Landroid/app/ActivityOptions;->toBundle()Landroid/os/Bundle;

    move-result-object p1

    invoke-virtual {p3, v0, p1}, Landroid/content/Context;->startActivity(Landroid/content/Intent;Landroid/os/Bundle;)V

    .line 184
    new-instance p1, Ljava/lang/StringBuilder;

    invoke-direct {p1}, Ljava/lang/StringBuilder;-><init>()V

    const-string p3, "start display="

    invoke-virtual {p1, p3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p1, p2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p1

    const-string p2, " bars="

    invoke-virtual {p1, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->barsMode(Landroid/content/Context;)Z

    move-result p0

    invoke-virtual {p1, p0}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    const-string p1, "AAWindowPatch"

    invoke-static {p1, p0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 185
    return-void
.end method

.method public static touch(Landroid/view/SurfaceView;Landroid/graphics/Rect;Landroid/view/MotionEvent;)Landroid/view/MotionEvent;
    .locals 10

    .line 400
    invoke-static {p2}, Landroid/view/MotionEvent;->obtain(Landroid/view/MotionEvent;)Landroid/view/MotionEvent;

    move-result-object v0

    .line 401
    invoke-virtual {p0}, Landroid/view/SurfaceView;->getWidth()I

    move-result v1

    .line 402
    invoke-virtual {p0}, Landroid/view/SurfaceView;->getHeight()I

    move-result v2

    .line 403
    invoke-virtual {p1}, Landroid/graphics/Rect;->width()I

    move-result v3

    .line 404
    invoke-virtual {p1}, Landroid/graphics/Rect;->height()I

    move-result p1

    .line 405
    if-lez v1, :cond_2

    if-lez v2, :cond_2

    if-lez v3, :cond_2

    if-gtz p1, :cond_0

    goto/16 :goto_1

    .line 406
    :cond_0
    invoke-virtual {p0}, Landroid/view/SurfaceView;->getParent()Landroid/view/ViewParent;

    move-result-object p0

    check-cast p0, Landroid/view/View;

    .line 407
    invoke-virtual {p0}, Landroid/view/View;->getWidth()I

    move-result v4

    .line 408
    invoke-virtual {p0}, Landroid/view/View;->getHeight()I

    move-result p0

    .line 409
    nop

    .line 410
    nop

    .line 411
    const/4 v5, 0x0

    if-lez v4, :cond_1

    if-lez p0, :cond_1

    .line 412
    int-to-float v4, v4

    int-to-float v6, v3

    div-float v7, v4, v6

    int-to-float p0, p0

    int-to-float v8, p1

    div-float v9, p0, v8

    invoke-static {v7, v9}, Ljava/lang/Math;->max(FF)F

    move-result v7

    .line 413
    div-float/2addr v4, v7

    sub-float/2addr v6, v4

    invoke-static {v5, v6}, Ljava/lang/Math;->max(FF)F

    move-result v4

    .line 414
    div-float/2addr p0, v7

    sub-float/2addr v8, p0

    invoke-static {v5, v8}, Ljava/lang/Math;->max(FF)F

    move-result v5

    move p0, v5

    move v5, v4

    goto :goto_0

    .line 416
    :cond_1
    move p0, v5

    :goto_0
    new-instance v4, Landroid/graphics/Matrix;

    invoke-direct {v4}, Landroid/graphics/Matrix;-><init>()V

    .line 417
    int-to-float v3, v3

    int-to-float v6, v1

    div-float/2addr v3, v6

    int-to-float p1, p1

    int-to-float v6, v2

    div-float/2addr p1, v6

    invoke-virtual {v4, v3, p1}, Landroid/graphics/Matrix;->setScale(FF)V

    .line 418
    neg-float p1, v5

    const/high16 v3, 0x40000000    # 2.0f

    div-float/2addr p1, v3

    neg-float v6, p0

    div-float/2addr v6, v3

    invoke-virtual {v4, p1, v6}, Landroid/graphics/Matrix;->postTranslate(FF)Z

    .line 419
    invoke-virtual {v0, v4}, Landroid/view/MotionEvent;->transform(Landroid/graphics/Matrix;)V

    .line 420
    new-instance p1, Ljava/lang/StringBuilder;

    invoke-direct {p1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "action="

    invoke-virtual {p1, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p2}, Landroid/view/MotionEvent;->getActionMasked()I

    move-result v3

    invoke-virtual {p1, v3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p1

    const-string v3, " local="

    invoke-virtual {p1, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p2}, Landroid/view/MotionEvent;->getX()F

    move-result v3

    invoke-virtual {p1, v3}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    move-result-object p1

    const-string v3, ","

    invoke-virtual {p1, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p2}, Landroid/view/MotionEvent;->getY()F

    move-result p2

    invoke-virtual {p1, p2}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    move-result-object p1

    const-string p2, " surface="

    invoke-virtual {p1, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p1, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p1

    const-string p2, "x"

    invoke-virtual {p1, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p1, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p1

    const-string p2, " margin="

    invoke-virtual {p1, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p1, v5}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p1, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p1, p0}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    move-result-object p0

    const-string p1, " mapped="

    invoke-virtual {p0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    .line 423
    invoke-virtual {v0}, Landroid/view/MotionEvent;->getX()F

    move-result p1

    invoke-virtual {p0, p1}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {v0}, Landroid/view/MotionEvent;->getY()F

    move-result p1

    invoke-virtual {p0, p1}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    .line 420
    const-string p1, "AAWindowPatch"

    invoke-static {p1, p0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 424
    return-object v0

    .line 405
    :cond_2
    :goto_1
    return-object v0
.end method

.method public static track(Landroid/app/Activity;)V
    .locals 1

    .line 29
    new-instance v0, Ljava/lang/ref/WeakReference;

    invoke-direct {v0, p0}, Ljava/lang/ref/WeakReference;-><init>(Ljava/lang/Object;)V

    sput-object v0, Lcom/adayo/androidauto/patch/AAWindowPatch;->current:Ljava/lang/ref/WeakReference;

    .line 30
    return-void
.end method

.method public static videoHeight(Landroid/content/Context;)I
    .locals 0

    .line 138
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->barsMode(Landroid/content/Context;)Z

    move-result p0

    if-eqz p0, :cond_0

    const/16 p0, 0x360

    goto :goto_0

    :cond_0
    const/16 p0, 0x361

    :goto_0
    return p0
.end method

.method public static videoHeightMm(Landroid/content/Context;)I
    .locals 0

    .line 146
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->barsMode(Landroid/content/Context;)Z

    const/16 p0, 0x80

    return p0
.end method

.method public static videoWidth(Landroid/content/Context;)I
    .locals 0

    .line 134
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->barsMode(Landroid/content/Context;)Z

    move-result p0

    if-eqz p0, :cond_0

    const/16 p0, 0x780

    goto :goto_0

    :cond_0
    const/16 p0, 0x52f

    :goto_0
    return p0
.end method

.method public static videoWidthMm(Landroid/content/Context;)I
    .locals 0

    .line 142
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->barsMode(Landroid/content/Context;)Z

    move-result p0

    if-eqz p0, :cond_0

    const/16 p0, 0x11d

    goto :goto_0

    :cond_0
    const/16 p0, 0xc5

    :goto_0
    return p0
.end method

.method public static viewport(Landroid/view/SurfaceView;Landroid/graphics/Rect;)Landroid/graphics/Rect;
    .locals 10

    .line 342
    invoke-virtual {p0}, Landroid/view/SurfaceView;->getParent()Landroid/view/ViewParent;

    move-result-object v0

    check-cast v0, Landroid/view/View;

    .line 343
    invoke-virtual {v0}, Landroid/view/View;->getWidth()I

    move-result v1

    .line 344
    invoke-virtual {v0}, Landroid/view/View;->getHeight()I

    move-result v0

    .line 345
    invoke-virtual {p1}, Landroid/graphics/Rect;->width()I

    move-result v2

    .line 346
    invoke-virtual {p1}, Landroid/graphics/Rect;->height()I

    move-result v3

    .line 347
    if-lez v1, :cond_4

    if-lez v0, :cond_4

    if-lez v2, :cond_4

    if-gtz v3, :cond_0

    goto/16 :goto_2

    .line 352
    :cond_0
    int-to-long v4, v1

    int-to-long v6, v3

    mul-long/2addr v4, v6

    int-to-long v8, v0

    int-to-long v2, v2

    mul-long/2addr v8, v2

    cmp-long p0, v4, v8

    if-ltz p0, :cond_2

    .line 353
    nop

    .line 354
    div-long/2addr v4, v2

    long-to-int p0, v4

    .line 355
    div-int/lit8 p0, p0, 0x2

    mul-int/lit8 p0, p0, 0x2

    .line 356
    if-ge p0, v0, :cond_1

    goto :goto_0

    :cond_1
    move v2, p0

    move p0, v1

    goto :goto_1

    .line 358
    :cond_2
    nop

    .line 359
    div-long/2addr v8, v6

    long-to-int p0, v8

    .line 360
    div-int/lit8 p0, p0, 0x2

    mul-int/lit8 p0, p0, 0x2

    .line 361
    if-ge p0, v1, :cond_3

    .line 363
    :goto_0
    move v2, v0

    move p0, v1

    goto :goto_1

    .line 361
    :cond_3
    move v2, v0

    .line 363
    :goto_1
    sub-int v3, v1, p0

    div-int/lit8 v3, v3, 0x2

    .line 364
    sub-int v4, v0, v2

    div-int/lit8 v4, v4, 0x2

    .line 365
    new-instance v5, Landroid/graphics/Rect;

    add-int/2addr p0, v3

    add-int/2addr v2, v4

    invoke-direct {v5, v3, v4, p0, v2}, Landroid/graphics/Rect;-><init>(IIII)V

    .line 366
    new-instance p0, Ljava/lang/StringBuilder;

    invoke-direct {p0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "parent="

    invoke-virtual {p0, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p0

    const-string v1, "x"

    invoke-virtual {p0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p0

    const-string v0, " video="

    invoke-virtual {p0, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    move-result-object p0

    const-string p1, " viewport="

    invoke-virtual {p0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    const-string p1, "AAWindowPatch"

    invoke-static {p1, p0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 367
    return-object v5

    .line 348
    :cond_4
    :goto_2
    new-instance p1, Landroid/graphics/Rect;

    invoke-virtual {p0}, Landroid/view/SurfaceView;->getWidth()I

    move-result v0

    const/4 v1, 0x1

    invoke-static {v1, v0}, Ljava/lang/Math;->max(II)I

    move-result v0

    invoke-virtual {p0}, Landroid/view/SurfaceView;->getHeight()I

    move-result p0

    invoke-static {v1, p0}, Ljava/lang/Math;->max(II)I

    move-result p0

    const/4 v1, 0x0

    invoke-direct {p1, v1, v1, v0, p0}, Landroid/graphics/Rect;-><init>(IIII)V

    return-object p1
.end method

.method private static watchAccOff(Landroid/content/Context;)V
    .locals 4

    .line 66
    sget-boolean v0, Lcom/adayo/androidauto/patch/AAWindowPatch;->receiverRegistered:Z

    if-eqz v0, :cond_0

    return-void

    .line 67
    :cond_0
    const/4 v0, 0x1

    sput-boolean v0, Lcom/adayo/androidauto/patch/AAWindowPatch;->receiverRegistered:Z

    .line 68
    new-instance v1, Landroid/content/IntentFilter;

    invoke-direct {v1}, Landroid/content/IntentFilter;-><init>()V

    .line 69
    const-string v2, "com.device.accoff"

    invoke-virtual {v1, v2}, Landroid/content/IntentFilter;->addAction(Ljava/lang/String;)V

    .line 70
    const-string v2, "com.device.poweroff"

    invoke-virtual {v1, v2}, Landroid/content/IntentFilter;->addAction(Ljava/lang/String;)V

    .line 71
    const-string v2, "com.device.accon"

    invoke-virtual {v1, v2}, Landroid/content/IntentFilter;->addAction(Ljava/lang/String;)V

    .line 72
    const-string v2, "com.device.poweron"

    invoke-virtual {v1, v2}, Landroid/content/IntentFilter;->addAction(Ljava/lang/String;)V

    .line 73
    new-instance v2, Lcom/adayo/androidauto/patch/AAWindowPatch$1;

    invoke-direct {v2}, Lcom/adayo/androidauto/patch/AAWindowPatch$1;-><init>()V

    .line 88
    new-instance v3, Lcom/adayo/androidauto/patch/AAWindowPatch$2;

    invoke-direct {v3, p0, v2, v1}, Lcom/adayo/androidauto/patch/AAWindowPatch$2;-><init>(Landroid/content/Context;Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)V

    .line 94
    invoke-static {}, Landroid/os/Looper;->myLooper()Landroid/os/Looper;

    move-result-object p0

    invoke-static {}, Landroid/os/Looper;->getMainLooper()Landroid/os/Looper;

    move-result-object v1

    if-ne p0, v1, :cond_1

    .line 95
    invoke-interface {v3}, Ljava/lang/Runnable;->run()V

    .line 96
    sput-boolean v0, Lcom/adayo/androidauto/patch/AAWindowPatch;->accWatchReady:Z

    goto :goto_0

    .line 98
    :cond_1
    new-instance p0, Landroid/os/Handler;

    invoke-static {}, Landroid/os/Looper;->getMainLooper()Landroid/os/Looper;

    move-result-object v0

    invoke-direct {p0, v0}, Landroid/os/Handler;-><init>(Landroid/os/Looper;)V

    new-instance v0, Lcom/adayo/androidauto/patch/AAWindowPatch$3;

    invoke-direct {v0, v3}, Lcom/adayo/androidauto/patch/AAWindowPatch$3;-><init>(Ljava/lang/Runnable;)V

    invoke-virtual {p0, v0}, Landroid/os/Handler;->post(Ljava/lang/Runnable;)Z

    .line 106
    :goto_0
    return-void
.end method

.method private static windowDisplay(Landroid/content/Context;)I
    .locals 5

    .line 163
    const-string v0, "display"

    invoke-virtual {p0, v0}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object p0

    check-cast p0, Landroid/hardware/display/DisplayManager;

    .line 164
    if-eqz p0, :cond_1

    .line 165
    invoke-virtual {p0}, Landroid/hardware/display/DisplayManager;->getDisplays()[Landroid/view/Display;

    move-result-object p0

    array-length v0, p0

    const/4 v1, 0x0

    :goto_0
    if-ge v1, v0, :cond_1

    aget-object v2, p0, v1

    .line 166
    invoke-virtual {v2}, Landroid/view/Display;->getDisplayId()I

    move-result v3

    if-lez v3, :cond_0

    invoke-virtual {v2}, Landroid/view/Display;->getName()Ljava/lang/String;

    move-result-object v3

    const-string v4, "ActivityViewVirtualDisplay@"

    invoke-virtual {v3, v4}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v3

    if-eqz v3, :cond_0

    .line 167
    invoke-virtual {v2}, Landroid/view/Display;->getDisplayId()I

    move-result p0

    return p0

    .line 165
    :cond_0
    add-int/lit8 v1, v1, 0x1

    goto :goto_0

    .line 171
    :cond_1
    const/4 p0, -0x1

    return p0
.end method
