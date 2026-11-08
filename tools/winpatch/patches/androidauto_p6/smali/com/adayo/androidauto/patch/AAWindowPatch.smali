.class public final Lcom/adayo/androidauto/patch/AAWindowPatch;
.super Ljava/lang/Object;
.source "AAWindowPatch.java"


# static fields
.field private static final BARS_HEIGHT:I = 0x360

.field private static final BARS_HEIGHT_MM:I = 0x80

.field private static final BARS_WIDTH:I = 0x780

.field private static final BARS_WIDTH_MM:I = 0x11d

.field static final LAYOUT_KEY:Ljava/lang/String; = "tbox_winpatch_layout"

.field private static final REDIRECTED:Ljava/lang/String; = "com.adayo.androidauto.patch.REDIRECTED"

.field private static final WINDOW_HEIGHT:I = 0x361

.field private static final WINDOW_HEIGHT_MM:I = 0x80

.field private static final WINDOW_WIDTH:I = 0x52f

.field private static final WINDOW_WIDTH_MM:I = 0xc5

.field private static current:Ljava/lang/ref/WeakReference;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/lang/ref/WeakReference<",
            "Landroid/app/Activity;",
            ">;"
        }
    .end annotation
.end field


# direct methods
.method static constructor <clinit>()V
    .locals 2

    .line 22
    new-instance v0, Ljava/lang/ref/WeakReference;

    const/4 v1, 0x0

    invoke-direct {v0, v1}, Ljava/lang/ref/WeakReference;-><init>(Ljava/lang/Object;)V

    sput-object v0, Lcom/adayo/androidauto/patch/AAWindowPatch;->current:Ljava/lang/ref/WeakReference;

    return-void
.end method

.method public constructor <init>()V
    .locals 0

    .line 21
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method private static alreadyRedirected(Landroid/app/Activity;)Z
    .locals 2

    .line 111
    invoke-virtual {p0}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;

    move-result-object p0

    .line 112
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

    .line 273
    invoke-virtual {p0}, Landroid/view/SurfaceView;->getParent()Landroid/view/ViewParent;

    move-result-object v0

    check-cast v0, Landroid/view/ViewGroup;

    .line 274
    const/4 v1, 0x1

    invoke-virtual {v0, v1}, Landroid/view/ViewGroup;->setClipChildren(Z)V

    .line 275
    invoke-virtual {v0, v1}, Landroid/view/ViewGroup;->setClipToPadding(Z)V

    .line 276
    invoke-virtual {p0}, Landroid/view/SurfaceView;->getLayoutParams()Landroid/view/ViewGroup$LayoutParams;

    move-result-object v1

    check-cast v1, Landroid/view/ViewGroup$MarginLayoutParams;

    .line 277
    invoke-virtual {v0}, Landroid/view/ViewGroup;->getWidth()I

    move-result v2

    .line 278
    invoke-virtual {v0}, Landroid/view/ViewGroup;->getHeight()I

    move-result v0

    .line 279
    invoke-virtual {p1}, Landroid/graphics/Rect;->width()I

    move-result v3

    iput v3, v1, Landroid/view/ViewGroup$MarginLayoutParams;->width:I

    .line 280
    invoke-virtual {p1}, Landroid/graphics/Rect;->height()I

    move-result v3

    iput v3, v1, Landroid/view/ViewGroup$MarginLayoutParams;->height:I

    .line 281
    iget v3, p1, Landroid/graphics/Rect;->left:I

    iput v3, v1, Landroid/view/ViewGroup$MarginLayoutParams;->leftMargin:I

    .line 282
    iget v3, p1, Landroid/graphics/Rect;->top:I

    iput v3, v1, Landroid/view/ViewGroup$MarginLayoutParams;->topMargin:I

    .line 283
    iget v3, p1, Landroid/graphics/Rect;->right:I

    sub-int v3, v2, v3

    iput v3, v1, Landroid/view/ViewGroup$MarginLayoutParams;->rightMargin:I

    .line 284
    iget v3, p1, Landroid/graphics/Rect;->bottom:I

    sub-int v3, v0, v3

    iput v3, v1, Landroid/view/ViewGroup$MarginLayoutParams;->bottomMargin:I

    .line 285
    invoke-virtual {p0, v1}, Landroid/view/SurfaceView;->setLayoutParams(Landroid/view/ViewGroup$LayoutParams;)V

    .line 286
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

    .line 289
    return-void
.end method

.method public static barsMode(Landroid/content/Context;)Z
    .locals 2

    .line 49
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

    .line 50
    :catch_0
    move-exception p0

    .line 51
    const-string v0, "AAWindowPatch"

    const-string v1, "layout read failed"

    invoke-static {v0, v1, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 52
    const/4 p0, 0x0

    return p0
.end method

.method public static ensureWindow(Landroid/app/Activity;)V
    .locals 4

    .line 168
    invoke-virtual {p0}, Landroid/app/Activity;->getWindowManager()Landroid/view/WindowManager;

    move-result-object v0

    invoke-interface {v0}, Landroid/view/WindowManager;->getDefaultDisplay()Landroid/view/Display;

    move-result-object v0

    invoke-virtual {v0}, Landroid/view/Display;->getDisplayId()I

    move-result v0

    .line 169
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

    .line 170
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->barsMode(Landroid/content/Context;)Z

    move-result v1

    if-eqz v1, :cond_3

    .line 171
    if-nez v0, :cond_0

    .line 172
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->keepBars(Landroid/app/Activity;)V

    .line 173
    return-void

    .line 175
    :cond_0
    invoke-virtual {p0}, Landroid/app/Activity;->isFinishing()Z

    move-result v0

    if-nez v0, :cond_2

    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->alreadyRedirected(Landroid/app/Activity;)Z

    move-result v0

    if-eqz v0, :cond_1

    goto :goto_1

    .line 180
    :cond_1
    :try_start_0
    invoke-virtual {p0}, Landroid/app/Activity;->finish()V

    .line 181
    invoke-virtual {p0}, Landroid/app/Activity;->getApplicationContext()Landroid/content/Context;

    move-result-object v0

    invoke-virtual {p0}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;

    move-result-object p0

    const/4 v1, 0x0

    invoke-static {v0, p0, v1, v1}, Lcom/adayo/androidauto/patch/AAWindowPatch;->startOnDisplay(Landroid/content/Context;Landroid/content/Intent;IZ)V
    :try_end_0
    .catch Ljava/lang/RuntimeException; {:try_start_0 .. :try_end_0} :catch_0

    .line 184
    goto :goto_0

    .line 182
    :catch_0
    move-exception p0

    .line 183
    const-string v0, "bars redirect failed"

    invoke-static {v2, v0, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 185
    :goto_0
    return-void

    .line 176
    :cond_2
    :goto_1
    const-string p0, "bars redirect did not reach the main display"

    invoke-static {v2, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    .line 177
    return-void

    .line 187
    :cond_3
    if-nez v0, :cond_7

    invoke-virtual {p0}, Landroid/app/Activity;->isFinishing()Z

    move-result v0

    if-eqz v0, :cond_4

    goto :goto_3

    .line 188
    :cond_4
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->alreadyRedirected(Landroid/app/Activity;)Z

    move-result v0

    if-eqz v0, :cond_5

    .line 189
    const-string p0, "redirect did not reach window; refusing retry"

    invoke-static {v2, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    .line 190
    return-void

    .line 192
    :cond_5
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->windowDisplay(Landroid/content/Context;)I

    move-result v0

    .line 193
    if-gez v0, :cond_6

    .line 194
    const-string p0, "launcher window unavailable; refusing redirect"

    invoke-static {v2, p0}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

    .line 195
    return-void

    .line 198
    :cond_6
    :try_start_1
    invoke-virtual {p0}, Landroid/app/Activity;->finish()V

    .line 199
    invoke-virtual {p0}, Landroid/app/Activity;->getApplicationContext()Landroid/content/Context;

    move-result-object v1

    invoke-virtual {p0}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;

    move-result-object p0

    const/4 v3, 0x1

    invoke-static {v1, p0, v0, v3}, Lcom/adayo/androidauto/patch/AAWindowPatch;->startOnDisplay(Landroid/content/Context;Landroid/content/Intent;IZ)V
    :try_end_1
    .catch Ljava/lang/RuntimeException; {:try_start_1 .. :try_end_1} :catch_1

    .line 202
    goto :goto_2

    .line 200
    :catch_1
    move-exception p0

    .line 201
    const-string v0, "window redirect failed"

    invoke-static {v2, v0, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 203
    :goto_2
    return-void

    .line 187
    :cond_7
    :goto_3
    return-void
.end method

.method public static interceptOnCreate(Landroid/app/Activity;)Z
    .locals 6

    .line 116
    invoke-virtual {p0}, Landroid/app/Activity;->getWindowManager()Landroid/view/WindowManager;

    move-result-object v0

    invoke-interface {v0}, Landroid/view/WindowManager;->getDefaultDisplay()Landroid/view/Display;

    move-result-object v0

    invoke-virtual {v0}, Landroid/view/Display;->getDisplayId()I

    move-result v0

    .line 117
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->barsMode(Landroid/content/Context;)Z

    move-result v1

    const/4 v2, 0x1

    const-string v3, "AAWindowPatch"

    const/4 v4, 0x0

    if-eqz v1, :cond_2

    .line 118
    if-nez v0, :cond_0

    .line 119
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->keepBars(Landroid/app/Activity;)V

    .line 120
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->track(Landroid/app/Activity;)V

    .line 121
    return v4

    .line 123
    :cond_0
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->alreadyRedirected(Landroid/app/Activity;)Z

    move-result v1

    if-eqz v1, :cond_1

    .line 124
    const-string v0, "interceptOnCreate: bars redirect did not reach the main display"

    invoke-static {v3, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    .line 125
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->track(Landroid/app/Activity;)V

    .line 126
    return v4

    .line 129
    :cond_1
    :try_start_0
    invoke-virtual {p0}, Landroid/app/Activity;->finish()V

    .line 130
    invoke-virtual {p0}, Landroid/app/Activity;->getApplicationContext()Landroid/content/Context;

    move-result-object v1

    invoke-virtual {p0}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;

    move-result-object v5

    invoke-static {v1, v5, v4, v4}, Lcom/adayo/androidauto/patch/AAWindowPatch;->startOnDisplay(Landroid/content/Context;Landroid/content/Intent;IZ)V

    .line 131
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v5, "interceptOnCreate display="

    invoke-virtual {v1, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v0

    const-string v1, " -> main"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v3, v0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_0
    .catch Ljava/lang/RuntimeException; {:try_start_0 .. :try_end_0} :catch_0

    .line 132
    return v2

    .line 133
    :catch_0
    move-exception v0

    .line 134
    const-string v1, "interceptOnCreate bars redirect failed"

    invoke-static {v3, v1, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 135
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->track(Landroid/app/Activity;)V

    .line 136
    return v4

    .line 139
    :cond_2
    if-lez v0, :cond_3

    .line 140
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->track(Landroid/app/Activity;)V

    .line 141
    return v4

    .line 143
    :cond_3
    invoke-virtual {p0}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;

    move-result-object v0

    .line 144
    if-eqz v0, :cond_4

    const-string v1, "com.adayo.androidauto.patch.REDIRECTED"

    invoke-virtual {v0, v1, v4}, Landroid/content/Intent;->getBooleanExtra(Ljava/lang/String;Z)Z

    move-result v1

    if-eqz v1, :cond_4

    .line 145
    const-string v0, "interceptOnCreate: redirect did not reach window; refusing retry"

    invoke-static {v3, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    .line 146
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->track(Landroid/app/Activity;)V

    .line 147
    return v4

    .line 149
    :cond_4
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->windowDisplay(Landroid/content/Context;)I

    move-result v1

    .line 150
    if-gtz v1, :cond_5

    .line 151
    const-string v0, "interceptOnCreate: launcher window unavailable; allowing display=0"

    invoke-static {v3, v0}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

    .line 152
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->track(Landroid/app/Activity;)V

    .line 153
    return v4

    .line 156
    :cond_5
    :try_start_1
    invoke-virtual {p0}, Landroid/app/Activity;->finish()V

    .line 157
    invoke-virtual {p0}, Landroid/app/Activity;->getApplicationContext()Landroid/content/Context;

    move-result-object v5

    invoke-static {v5, v0, v1, v2}, Lcom/adayo/androidauto/patch/AAWindowPatch;->startOnDisplay(Landroid/content/Context;Landroid/content/Intent;IZ)V

    .line 158
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v5, "interceptOnCreate display=0 -> display="

    invoke-virtual {v0, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v3, v0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_1
    .catch Ljava/lang/RuntimeException; {:try_start_1 .. :try_end_1} :catch_1

    .line 159
    return v2

    .line 160
    :catch_1
    move-exception v0

    .line 161
    const-string v1, "interceptOnCreate redirect failed"

    invoke-static {v3, v1, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 162
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->track(Landroid/app/Activity;)V

    .line 163
    return v4
.end method

.method public static keepBars(Landroid/app/Activity;)V
    .locals 1

    .line 74
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->barsMode(Landroid/content/Context;)Z

    move-result v0

    if-nez v0, :cond_0

    return-void

    .line 75
    :cond_0
    invoke-virtual {p0}, Landroid/app/Activity;->getWindowManager()Landroid/view/WindowManager;

    move-result-object v0

    invoke-interface {v0}, Landroid/view/WindowManager;->getDefaultDisplay()Landroid/view/Display;

    move-result-object v0

    invoke-virtual {v0}, Landroid/view/Display;->getDisplayId()I

    move-result v0

    if-eqz v0, :cond_1

    return-void

    .line 76
    :cond_1
    invoke-virtual {p0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;

    move-result-object p0

    .line 77
    const/16 v0, 0x600

    invoke-virtual {p0, v0}, Landroid/view/Window;->clearFlags(I)V

    .line 79
    const v0, -0x7ffff800

    invoke-virtual {p0, v0}, Landroid/view/Window;->addFlags(I)V

    .line 81
    invoke-virtual {p0}, Landroid/view/Window;->getDecorView()Landroid/view/View;

    move-result-object p0

    const/4 v0, 0x0

    invoke-virtual {p0, v0}, Landroid/view/View;->setSystemUiVisibility(I)V

    .line 82
    const-string p0, "AAWindowPatch"

    const-string v0, "keepBars"

    invoke-static {p0, v0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 83
    return-void
.end method

.method public static start(Landroid/content/Context;Landroid/content/Intent;)V
    .locals 3

    .line 206
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->barsMode(Landroid/content/Context;)Z

    move-result v0

    if-eqz v0, :cond_1

    .line 207
    sget-object v0, Lcom/adayo/androidauto/patch/AAWindowPatch;->current:Ljava/lang/ref/WeakReference;

    invoke-virtual {v0}, Ljava/lang/ref/WeakReference;->get()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/app/Activity;

    .line 208
    if-eqz v0, :cond_0

    invoke-virtual {v0}, Landroid/app/Activity;->isFinishing()Z

    move-result v1

    if-nez v1, :cond_0

    .line 209
    invoke-virtual {v0}, Landroid/app/Activity;->getWindowManager()Landroid/view/WindowManager;

    move-result-object v1

    invoke-interface {v1}, Landroid/view/WindowManager;->getDefaultDisplay()Landroid/view/Display;

    move-result-object v1

    invoke-virtual {v1}, Landroid/view/Display;->getDisplayId()I

    move-result v1

    .line 210
    if-eqz v1, :cond_0

    invoke-virtual {v0}, Landroid/app/Activity;->finish()V

    .line 212
    :cond_0
    const/4 v0, 0x0

    invoke-static {p0, p1, v0, v0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->startOnDisplay(Landroid/content/Context;Landroid/content/Intent;IZ)V

    .line 213
    return-void

    .line 215
    :cond_1
    sget-object v0, Lcom/adayo/androidauto/patch/AAWindowPatch;->current:Ljava/lang/ref/WeakReference;

    invoke-virtual {v0}, Ljava/lang/ref/WeakReference;->get()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/app/Activity;

    .line 216
    const-string v1, "AAWindowPatch"

    if-eqz v0, :cond_3

    invoke-virtual {v0}, Landroid/app/Activity;->isFinishing()Z

    move-result v2

    if-nez v2, :cond_3

    .line 217
    invoke-virtual {v0}, Landroid/app/Activity;->getWindowManager()Landroid/view/WindowManager;

    move-result-object v2

    invoke-interface {v2}, Landroid/view/WindowManager;->getDefaultDisplay()Landroid/view/Display;

    move-result-object v2

    invoke-virtual {v2}, Landroid/view/Display;->getDisplayId()I

    move-result v2

    .line 218
    if-lez v2, :cond_2

    .line 219
    invoke-static {}, Landroid/app/ActivityOptions;->makeBasic()Landroid/app/ActivityOptions;

    move-result-object v0

    .line 220
    invoke-virtual {v0, v2}, Landroid/app/ActivityOptions;->setLaunchDisplayId(I)Landroid/app/ActivityOptions;

    .line 221
    invoke-virtual {p0}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;

    move-result-object p0

    invoke-virtual {v0}, Landroid/app/ActivityOptions;->toBundle()Landroid/os/Bundle;

    move-result-object v0

    invoke-virtual {p0, p1, v0}, Landroid/content/Context;->startActivity(Landroid/content/Intent;Landroid/os/Bundle;)V

    .line 222
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

    .line 223
    return-void

    .line 225
    :cond_2
    invoke-virtual {v0}, Landroid/app/Activity;->finish()V

    .line 227
    :cond_3
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->windowDisplay(Landroid/content/Context;)I

    move-result v0

    .line 228
    if-lez v0, :cond_4

    .line 229
    const/4 v1, 0x1

    invoke-static {p0, p1, v0, v1}, Lcom/adayo/androidauto/patch/AAWindowPatch;->startOnDisplay(Landroid/content/Context;Landroid/content/Intent;IZ)V

    .line 230
    return-void

    .line 232
    :cond_4
    new-instance p1, Landroid/content/Intent;

    const-string v0, "com.adayo.launcher.LAUNCH_APP"

    invoke-direct {p1, v0}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    .line 233
    const-string v0, "com.adayo.launcher"

    invoke-virtual {p1, v0}, Landroid/content/Intent;->setPackage(Ljava/lang/String;)Landroid/content/Intent;

    .line 234
    const-string v0, "app_pkg"

    const-string v2, "com.adayo.androidauto"

    invoke-virtual {p1, v0, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 235
    const-string v0, "app_cls"

    const-string v2, "com.adayo.androidauto.ui.AAActivity"

    invoke-virtual {p1, v0, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 236
    const/high16 v0, 0x10000000

    invoke-virtual {p1, v0}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;

    .line 237
    invoke-virtual {p0, p1}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V

    .line 238
    const-string p0, "start via launcher"

    invoke-static {v1, p0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 239
    return-void
.end method

.method private static startOnDisplay(Landroid/content/Context;Landroid/content/Intent;IZ)V
    .locals 2

    .line 98
    new-instance v0, Landroid/content/Intent;

    if-eqz p1, :cond_0

    invoke-direct {v0, p1}, Landroid/content/Intent;-><init>(Landroid/content/Intent;)V

    goto :goto_0

    :cond_0
    invoke-direct {v0}, Landroid/content/Intent;-><init>()V

    .line 99
    :goto_0
    const-string p1, "com.adayo.androidauto"

    const-string v1, "com.adayo.androidauto.ui.AAActivity"

    invoke-virtual {v0, p1, v1}, Landroid/content/Intent;->setClassName(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 100
    nop

    .line 101
    if-eqz p3, :cond_1

    const/high16 p1, 0x18000000

    goto :goto_1

    :cond_1
    const/high16 p1, 0x10000000

    .line 102
    :goto_1
    invoke-virtual {v0, p1}, Landroid/content/Intent;->setFlags(I)Landroid/content/Intent;

    .line 103
    const-string p1, "com.adayo.androidauto.patch.REDIRECTED"

    const/4 p3, 0x1

    invoke-virtual {v0, p1, p3}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Z)Landroid/content/Intent;

    .line 104
    invoke-static {}, Landroid/app/ActivityOptions;->makeBasic()Landroid/app/ActivityOptions;

    move-result-object p1

    .line 105
    invoke-virtual {p1, p2}, Landroid/app/ActivityOptions;->setLaunchDisplayId(I)Landroid/app/ActivityOptions;

    .line 106
    invoke-virtual {p0}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;

    move-result-object p3

    invoke-virtual {p1}, Landroid/app/ActivityOptions;->toBundle()Landroid/os/Bundle;

    move-result-object p1

    invoke-virtual {p3, v0, p1}, Landroid/content/Context;->startActivity(Landroid/content/Intent;Landroid/os/Bundle;)V

    .line 107
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

    .line 108
    return-void
.end method

.method public static touch(Landroid/view/SurfaceView;Landroid/graphics/Rect;Landroid/view/MotionEvent;)Landroid/view/MotionEvent;
    .locals 10

    .line 298
    invoke-static {p2}, Landroid/view/MotionEvent;->obtain(Landroid/view/MotionEvent;)Landroid/view/MotionEvent;

    move-result-object v0

    .line 299
    invoke-virtual {p0}, Landroid/view/SurfaceView;->getWidth()I

    move-result v1

    .line 300
    invoke-virtual {p0}, Landroid/view/SurfaceView;->getHeight()I

    move-result v2

    .line 301
    invoke-virtual {p1}, Landroid/graphics/Rect;->width()I

    move-result v3

    .line 302
    invoke-virtual {p1}, Landroid/graphics/Rect;->height()I

    move-result p1

    .line 303
    if-lez v1, :cond_2

    if-lez v2, :cond_2

    if-lez v3, :cond_2

    if-gtz p1, :cond_0

    goto/16 :goto_1

    .line 304
    :cond_0
    invoke-virtual {p0}, Landroid/view/SurfaceView;->getParent()Landroid/view/ViewParent;

    move-result-object p0

    check-cast p0, Landroid/view/View;

    .line 305
    invoke-virtual {p0}, Landroid/view/View;->getWidth()I

    move-result v4

    .line 306
    invoke-virtual {p0}, Landroid/view/View;->getHeight()I

    move-result p0

    .line 307
    nop

    .line 308
    nop

    .line 309
    const/4 v5, 0x0

    if-lez v4, :cond_1

    if-lez p0, :cond_1

    .line 310
    int-to-float v4, v4

    int-to-float v6, v3

    div-float v7, v4, v6

    int-to-float p0, p0

    int-to-float v8, p1

    div-float v9, p0, v8

    invoke-static {v7, v9}, Ljava/lang/Math;->max(FF)F

    move-result v7

    .line 311
    div-float/2addr v4, v7

    sub-float/2addr v6, v4

    invoke-static {v5, v6}, Ljava/lang/Math;->max(FF)F

    move-result v4

    .line 312
    div-float/2addr p0, v7

    sub-float/2addr v8, p0

    invoke-static {v5, v8}, Ljava/lang/Math;->max(FF)F

    move-result v5

    move p0, v5

    move v5, v4

    goto :goto_0

    .line 314
    :cond_1
    move p0, v5

    :goto_0
    new-instance v4, Landroid/graphics/Matrix;

    invoke-direct {v4}, Landroid/graphics/Matrix;-><init>()V

    .line 315
    int-to-float v3, v3

    int-to-float v6, v1

    div-float/2addr v3, v6

    int-to-float p1, p1

    int-to-float v6, v2

    div-float/2addr p1, v6

    invoke-virtual {v4, v3, p1}, Landroid/graphics/Matrix;->setScale(FF)V

    .line 316
    neg-float p1, v5

    const/high16 v3, 0x40000000    # 2.0f

    div-float/2addr p1, v3

    neg-float v6, p0

    div-float/2addr v6, v3

    invoke-virtual {v4, p1, v6}, Landroid/graphics/Matrix;->postTranslate(FF)Z

    .line 317
    invoke-virtual {v0, v4}, Landroid/view/MotionEvent;->transform(Landroid/graphics/Matrix;)V

    .line 318
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

    .line 321
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

    .line 318
    const-string p1, "AAWindowPatch"

    invoke-static {p1, p0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 322
    return-object v0

    .line 303
    :cond_2
    :goto_1
    return-object v0
.end method

.method public static track(Landroid/app/Activity;)V
    .locals 1

    .line 25
    new-instance v0, Ljava/lang/ref/WeakReference;

    invoke-direct {v0, p0}, Ljava/lang/ref/WeakReference;-><init>(Ljava/lang/Object;)V

    sput-object v0, Lcom/adayo/androidauto/patch/AAWindowPatch;->current:Ljava/lang/ref/WeakReference;

    .line 26
    return-void
.end method

.method public static videoHeight(Landroid/content/Context;)I
    .locals 0

    .line 61
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

    .line 69
    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->barsMode(Landroid/content/Context;)Z

    const/16 p0, 0x80

    return p0
.end method

.method public static videoWidth(Landroid/content/Context;)I
    .locals 0

    .line 57
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

    .line 65
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
    .locals 9

    .line 249
    invoke-virtual {p0}, Landroid/view/SurfaceView;->getParent()Landroid/view/ViewParent;

    move-result-object v0

    check-cast v0, Landroid/view/View;

    .line 250
    invoke-virtual {v0}, Landroid/view/View;->getWidth()I

    move-result v1

    .line 251
    invoke-virtual {v0}, Landroid/view/View;->getHeight()I

    move-result v0

    .line 252
    invoke-virtual {p1}, Landroid/graphics/Rect;->width()I

    move-result v2

    .line 253
    invoke-virtual {p1}, Landroid/graphics/Rect;->height()I

    move-result v3

    .line 254
    const/4 v4, 0x0

    if-lez v1, :cond_2

    if-lez v0, :cond_2

    if-lez v2, :cond_2

    if-gtz v3, :cond_0

    goto :goto_0

    .line 257
    :cond_0
    int-to-long v5, v0

    int-to-long v7, v2

    mul-long/2addr v5, v7

    int-to-long v2, v3

    div-long/2addr v5, v2

    long-to-int p0, v5

    .line 258
    div-int/lit8 p0, p0, 0x2

    mul-int/lit8 p0, p0, 0x2

    .line 259
    if-ge p0, v1, :cond_1

    .line 260
    move p0, v1

    .line 262
    :cond_1
    sub-int v2, v1, p0

    div-int/lit8 v2, v2, 0x2

    .line 263
    new-instance v3, Landroid/graphics/Rect;

    add-int/2addr p0, v2

    invoke-direct {v3, v2, v4, p0, v0}, Landroid/graphics/Rect;-><init>(IIII)V

    .line 264
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

    invoke-virtual {p0, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    const-string p1, "AAWindowPatch"

    invoke-static {p1, p0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 265
    return-object v3

    .line 255
    :cond_2
    :goto_0
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

    invoke-direct {p1, v4, v4, v0, p0}, Landroid/graphics/Rect;-><init>(IIII)V

    return-object p1
.end method

.method private static windowDisplay(Landroid/content/Context;)I
    .locals 5

    .line 86
    const-string v0, "display"

    invoke-virtual {p0, v0}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object p0

    check-cast p0, Landroid/hardware/display/DisplayManager;

    .line 87
    if-eqz p0, :cond_1

    .line 88
    invoke-virtual {p0}, Landroid/hardware/display/DisplayManager;->getDisplays()[Landroid/view/Display;

    move-result-object p0

    array-length v0, p0

    const/4 v1, 0x0

    :goto_0
    if-ge v1, v0, :cond_1

    aget-object v2, p0, v1

    .line 89
    invoke-virtual {v2}, Landroid/view/Display;->getDisplayId()I

    move-result v3

    if-lez v3, :cond_0

    invoke-virtual {v2}, Landroid/view/Display;->getName()Ljava/lang/String;

    move-result-object v3

    const-string v4, "ActivityViewVirtualDisplay@"

    invoke-virtual {v3, v4}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v3

    if-eqz v3, :cond_0

    .line 90
    invoke-virtual {v2}, Landroid/view/Display;->getDisplayId()I

    move-result p0

    return p0

    .line 88
    :cond_0
    add-int/lit8 v1, v1, 0x1

    goto :goto_0

    .line 94
    :cond_1
    const/4 p0, -0x1

    return p0
.end method
