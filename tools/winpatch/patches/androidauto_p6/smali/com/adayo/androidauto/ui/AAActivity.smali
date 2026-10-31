.class public final Lcom/adayo/androidauto/ui/AAActivity;
.super Landroid/app/Activity;
.source "AAActivity.java"


# static fields
.field private static final TAG:Ljava/lang/String; = "AHU.ANDROIDAUTO_TAG"

.field private static final mCounter:Ljava/util/concurrent/atomic/AtomicInteger;


# instance fields
.field private mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

.field private final mHandler:Landroid/os/Handler;

.field private mIsShowing:Z

.field private mLayout:Landroid/widget/RelativeLayout;

.field mUiCallback:Lcom/adayo/androidauto/ui/IUiCallback;


# direct methods
.method static constructor <clinit>()V
    .locals 2

    .line 33
    new-instance v0, Ljava/util/concurrent/atomic/AtomicInteger;

    const/4 v1, 0x0

    invoke-direct {v0, v1}, Ljava/util/concurrent/atomic/AtomicInteger;-><init>(I)V

    sput-object v0, Lcom/adayo/androidauto/ui/AAActivity;->mCounter:Ljava/util/concurrent/atomic/AtomicInteger;

    return-void
.end method

.method public constructor <init>()V
    .locals 1

    .line 31
    invoke-direct {p0}, Landroid/app/Activity;-><init>()V

    .line 35
    new-instance v0, Landroid/os/Handler;

    invoke-direct {v0}, Landroid/os/Handler;-><init>()V

    iput-object v0, p0, Lcom/adayo/androidauto/ui/AAActivity;->mHandler:Landroid/os/Handler;

    const/4 v0, 0x0

    .line 38
    iput-boolean v0, p0, Lcom/adayo/androidauto/ui/AAActivity;->mIsShowing:Z

    .line 181
    new-instance v0, Lcom/adayo/androidauto/ui/AAActivity$1;

    invoke-direct {v0, p0}, Lcom/adayo/androidauto/ui/AAActivity$1;-><init>(Lcom/adayo/androidauto/ui/AAActivity;)V

    iput-object v0, p0, Lcom/adayo/androidauto/ui/AAActivity;->mUiCallback:Lcom/adayo/androidauto/ui/IUiCallback;

    return-void
.end method

.method static synthetic access$000(Lcom/adayo/androidauto/ui/AAActivity;)Landroid/os/Handler;
    .locals 0

    .line 31
    iget-object p0, p0, Lcom/adayo/androidauto/ui/AAActivity;->mHandler:Landroid/os/Handler;

    return-object p0
.end method

.method static synthetic access$100(Lcom/adayo/androidauto/ui/AAActivity;)V
    .locals 0

    .line 31
    invoke-direct {p0}, Lcom/adayo/androidauto/ui/AAActivity;->finishAAActivity()V

    return-void
.end method

.method private finishAAActivity()V
    .locals 2

    const-string v0, "AHU.ANDROIDAUTO_TAG"

    const-string v1, "finishAAActivity!"

    .line 203
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 204
    invoke-virtual {p0}, Lcom/adayo/androidauto/ui/AAActivity;->finish()V

    return-void
.end method

.method private handleKeyEvent(ILandroid/view/KeyEvent;)Z
    .locals 1

    .line 165
    iget-object v0, p0, Lcom/adayo/androidauto/ui/AAActivity;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    invoke-virtual {v0, p1, p2}, Lcom/adayo/androidauto/service/AAServiceManager;->handleKeyEvent(ILandroid/view/KeyEvent;)Z

    move-result p1

    return p1
.end method

.method private initializeViews()V
    .locals 2

    .line 120
    invoke-virtual {p0}, Lcom/adayo/androidauto/ui/AAActivity;->getWindow()Landroid/view/Window;

    move-result-object v0

    const/16 v1, 0x80

    invoke-virtual {v0, v1}, Landroid/view/Window;->addFlags(I)V

    const/high16 v0, 0x7f060000

    .line 122
    invoke-virtual {p0, v0}, Lcom/adayo/androidauto/ui/AAActivity;->setContentView(I)V

    const v0, 0x7f050011

    .line 123
    invoke-virtual {p0, v0}, Lcom/adayo/androidauto/ui/AAActivity;->findViewById(I)Landroid/view/View;

    move-result-object v0

    check-cast v0, Landroid/widget/RelativeLayout;

    iput-object v0, p0, Lcom/adayo/androidauto/ui/AAActivity;->mLayout:Landroid/widget/RelativeLayout;

    return-void
.end method


# virtual methods
.method public dispatchGenericMotionEvent(Landroid/view/MotionEvent;)Z
    .locals 1

    .line 170
    iget-object v0, p0, Lcom/adayo/androidauto/ui/AAActivity;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    invoke-virtual {v0, p1}, Lcom/adayo/androidauto/service/AAServiceManager;->dispatchGenericMotionEvent(Landroid/view/MotionEvent;)Z

    move-result p1

    return p1
.end method

.method public getLayout()Landroid/widget/RelativeLayout;
    .locals 1

    .line 178
    iget-object v0, p0, Lcom/adayo/androidauto/ui/AAActivity;->mLayout:Landroid/widget/RelativeLayout;

    return-object v0
.end method

.method protected hideBottomUIMenu()V
    .locals 0

    return-void
.end method

.method protected legacyHideBottomUIMenu()V
    .locals 3

    const-string v0, "AHU.ANDROIDAUTO_TAG"

    const-string v1, "hideBottomUIMenu"

    .line 81
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 82
    invoke-virtual {p0}, Lcom/adayo/androidauto/ui/AAActivity;->getWindow()Landroid/view/Window;

    move-result-object v0

    invoke-virtual {v0}, Landroid/view/Window;->getDecorView()Landroid/view/View;

    move-result-object v0

    const/16 v1, 0x1006

    .line 85
    invoke-virtual {v0, v1}, Landroid/view/View;->setSystemUiVisibility(I)V

    .line 87
    invoke-virtual {p0}, Lcom/adayo/androidauto/ui/AAActivity;->getWindow()Landroid/view/Window;

    move-result-object v0

    .line 88
    invoke-virtual {v0}, Landroid/view/Window;->getAttributes()Landroid/view/WindowManager$LayoutParams;

    move-result-object v1

    const/16 v2, 0x802

    .line 89
    iput v2, v1, Landroid/view/WindowManager$LayoutParams;->systemUiVisibility:I

    .line 90
    invoke-virtual {v0, v1}, Landroid/view/Window;->setAttributes(Landroid/view/WindowManager$LayoutParams;)V

    return-void
.end method

.method public isShowing()Z
    .locals 1

    .line 93
    iget-boolean v0, p0, Lcom/adayo/androidauto/ui/AAActivity;->mIsShowing:Z

    return v0
.end method

.method protected onAAPConnected()V
    .locals 2

    const-string v0, "AHU.ANDROIDAUTO_TAG"

    const-string v1, "onConnected:"

    .line 174
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    return-void
.end method

.method public onBackPressed()V
    .locals 1

    .line 145
    iget-object v0, p0, Lcom/adayo/androidauto/ui/AAActivity;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    invoke-virtual {v0}, Lcom/adayo/androidauto/service/AAServiceManager;->onBackPressed()V

    return-void
.end method

.method protected onCreate(Landroid/os/Bundle;)V
    .locals 3

    .line 42
    invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V

    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->interceptOnCreate(Landroid/app/Activity;)Z

    move-result p1

    if-eqz p1, :cond_aa_win_ok

    return-void

    :cond_aa_win_ok

    .line 43
    new-instance p1, Ljava/lang/StringBuilder;

    invoke-direct {p1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v0, "onCreate start:"

    invoke-virtual {p1, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p0}, Lcom/adayo/androidauto/ui/AAActivity;->getIntent()Landroid/content/Intent;

    move-result-object v0

    invoke-virtual {p1, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {p1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    const-string v0, "AHU.ANDROIDAUTO_TAG"

    invoke-static {v0, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 45
    new-instance p1, Landroid/util/DisplayMetrics;

    invoke-direct {p1}, Landroid/util/DisplayMetrics;-><init>()V

    .line 46
    invoke-virtual {p0}, Lcom/adayo/androidauto/ui/AAActivity;->getWindowManager()Landroid/view/WindowManager;

    move-result-object v1

    invoke-interface {v1}, Landroid/view/WindowManager;->getDefaultDisplay()Landroid/view/Display;

    move-result-object v1

    invoke-virtual {v1, p1}, Landroid/view/Display;->getMetrics(Landroid/util/DisplayMetrics;)V

    .line 48
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "onCreate density"

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget v2, p1, Landroid/util/DisplayMetrics;->density:F

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    const-string v2, " densityDpi"

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget v2, p1, Landroid/util/DisplayMetrics;->densityDpi:I

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v2, " widthPixels"

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget v2, p1, Landroid/util/DisplayMetrics;->widthPixels:I

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v2, " heightPixels"

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget v2, p1, Landroid/util/DisplayMetrics;->heightPixels:I

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v2, " xdpi="

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget v2, p1, Landroid/util/DisplayMetrics;->xdpi:F

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    const-string v2, " ydpi="

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget p1, p1, Landroid/util/DisplayMetrics;->ydpi:F

    invoke-virtual {v1, p1}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-static {v0, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 50
    sget-object p1, Lcom/adayo/androidauto/ui/AAActivity;->mCounter:Ljava/util/concurrent/atomic/AtomicInteger;

    invoke-virtual {p1}, Ljava/util/concurrent/atomic/AtomicInteger;->incrementAndGet()I

    .line 51
    invoke-direct {p0}, Lcom/adayo/androidauto/ui/AAActivity;->initializeViews()V

    .line 52
    invoke-static {}, Lcom/adayo/androidauto/service/AAServiceManager;->getInstance()Lcom/adayo/androidauto/service/AAServiceManager;

    move-result-object p1

    iput-object p1, p0, Lcom/adayo/androidauto/ui/AAActivity;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    .line 53
    iget-object v0, p0, Lcom/adayo/androidauto/ui/AAActivity;->mUiCallback:Lcom/adayo/androidauto/ui/IUiCallback;

    invoke-virtual {p1, v0}, Lcom/adayo/androidauto/service/AAServiceManager;->registerIUiCallback(Lcom/adayo/androidauto/ui/IUiCallback;)V

    .line 54
    iget-object p1, p0, Lcom/adayo/androidauto/ui/AAActivity;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    invoke-virtual {p1, p0}, Lcom/adayo/androidauto/service/AAServiceManager;->onActivityCreate(Lcom/adayo/androidauto/ui/AAActivity;)V

    return-void
.end method

.method protected onDestroy()V
    .locals 4

    const-string v0, "AHU.ANDROIDAUTO_TAG"

    const-string v1, "onDestroy:start"

    .line 128
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 129
    iget-object v1, p0, Lcom/adayo/androidauto/ui/AAActivity;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    if-nez v1, :cond_aa_mgr_ok

    invoke-super {p0}, Landroid/app/Activity;->onDestroy()V

    return-void

    :cond_aa_mgr_ok
    iget-object v2, p0, Lcom/adayo/androidauto/ui/AAActivity;->mUiCallback:Lcom/adayo/androidauto/ui/IUiCallback;

    invoke-virtual {v1, v2}, Lcom/adayo/androidauto/service/AAServiceManager;->unregisterIUiCallback(Lcom/adayo/androidauto/ui/IUiCallback;)V

    .line 131
    sget-object v1, Lcom/adayo/androidauto/ui/AAActivity;->mCounter:Ljava/util/concurrent/atomic/AtomicInteger;

    invoke-virtual {v1}, Ljava/util/concurrent/atomic/AtomicInteger;->decrementAndGet()I

    move-result v1

    .line 132
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "onDestroy:onActivityonDestroy="

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-static {v0, v2}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    if-nez v1, :cond_0

    const-string v1, "onDestroy:onActivityonDestroy"

    .line 134
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 135
    iget-object v1, p0, Lcom/adayo/androidauto/ui/AAActivity;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    invoke-virtual {v1}, Lcom/adayo/androidauto/service/AAServiceManager;->onActivityonDestroy()V

    :cond_0
    const/4 v1, 0x0

    .line 137
    iput-object v1, p0, Lcom/adayo/androidauto/ui/AAActivity;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    const-string v1, "onDestroy:end"

    .line 139
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 140
    invoke-super {p0}, Landroid/app/Activity;->onDestroy()V

    return-void
.end method

.method public onKeyDown(ILandroid/view/KeyEvent;)Z
    .locals 0

    .line 150
    invoke-direct {p0, p1, p2}, Lcom/adayo/androidauto/ui/AAActivity;->handleKeyEvent(ILandroid/view/KeyEvent;)Z

    move-result p1

    return p1
.end method

.method public onKeyUp(ILandroid/view/KeyEvent;)Z
    .locals 2

    const/4 v0, 0x4

    if-ne p1, v0, :cond_0

    .line 157
    iget-object v0, p0, Lcom/adayo/androidauto/ui/AAActivity;->mHandler:Landroid/os/Handler;

    const/4 v1, 0x0

    invoke-virtual {v0, v1}, Landroid/os/Handler;->removeCallbacksAndMessages(Ljava/lang/Object;)V

    const-string v0, "AHU.ANDROIDAUTO_TAG"

    const-string v1, "KeyEvent.KEYCODE_BACK onDisconnected,call uifinish!"

    .line 158
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 159
    invoke-direct {p0}, Lcom/adayo/androidauto/ui/AAActivity;->finishAAActivity()V

    .line 161
    :cond_0
    invoke-direct {p0, p1, p2}, Lcom/adayo/androidauto/ui/AAActivity;->handleKeyEvent(ILandroid/view/KeyEvent;)Z

    move-result p1

    return p1
.end method

.method protected onNewIntent(Landroid/content/Intent;)V
    .locals 3

    .line 59
    invoke-super {p0, p1}, Landroid/app/Activity;->onNewIntent(Landroid/content/Intent;)V

    .line 60
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "onNewIntent:"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p0}, Lcom/adayo/androidauto/ui/AAActivity;->getIntent()Landroid/content/Intent;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    const-string v1, "AHU.ANDROIDAUTO_TAG"

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 62
    iget-object v0, p0, Lcom/adayo/androidauto/ui/AAActivity;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    invoke-virtual {v0, p1}, Lcom/adayo/androidauto/service/AAServiceManager;->onActivityNewIntent(Landroid/content/Intent;)V

    const-string v0, "stop"

    const/4 v2, 0x0

    .line 63
    invoke-virtual {p1, v0, v2}, Landroid/content/Intent;->getBooleanExtra(Ljava/lang/String;Z)Z

    move-result p1

    if-eqz p1, :cond_0

    const-string p1, "onNewIntent call uifinish!"

    .line 64
    invoke-static {v1, p1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 65
    invoke-direct {p0}, Lcom/adayo/androidauto/ui/AAActivity;->finishAAActivity()V

    :cond_0
    return-void
.end method

.method protected onPause()V
    .locals 2

    .line 107
    invoke-super {p0}, Landroid/app/Activity;->onPause()V

    const-string v0, "AHU.ANDROIDAUTO_TAG"

    const-string v1, "onPause:"

    .line 108
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    const/4 v0, 0x0

    .line 109
    iput-boolean v0, p0, Lcom/adayo/androidauto/ui/AAActivity;->mIsShowing:Z

    .line 110
    iget-object v0, p0, Lcom/adayo/androidauto/ui/AAActivity;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    invoke-virtual {v0}, Lcom/adayo/androidauto/service/AAServiceManager;->onActivityPause()V

    return-void
.end method

.method protected onResume()V
    .locals 2

    .line 71
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "onResume:"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p0}, Lcom/adayo/androidauto/ui/AAActivity;->getIntent()Landroid/content/Intent;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    const-string v1, "AHU.ANDROIDAUTO_TAG"

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 72
    invoke-super {p0}, Landroid/app/Activity;->onResume()V

    .line 73
    iget-object v0, p0, Lcom/adayo/androidauto/ui/AAActivity;->mAAServiceManager:Lcom/adayo/androidauto/service/AAServiceManager;

    invoke-virtual {v0}, Lcom/adayo/androidauto/service/AAServiceManager;->onActivityResume()V

    .line 74
    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v1, 0x12

    if-le v0, v1, :cond_0

    .line 75
    new-instance v0, Lcom/google/android/projection/sink/ui/SystemUiHider;

    iget-object v1, p0, Lcom/adayo/androidauto/ui/AAActivity;->mLayout:Landroid/widget/RelativeLayout;

    invoke-direct {v0, v1}, Lcom/google/android/projection/sink/ui/SystemUiHider;-><init>(Landroid/view/View;)V

    .line 76
    nop

    :cond_0
    const/4 v0, 0x1

    .line 78
    iput-boolean v0, p0, Lcom/adayo/androidauto/ui/AAActivity;->mIsShowing:Z

    invoke-static {p0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->ensureWindow(Landroid/app/Activity;)V

    return-void
.end method

.method protected onStop()V
    .locals 2

    .line 115
    invoke-super {p0}, Landroid/app/Activity;->onStop()V

    const-string v0, "AHU.ANDROIDAUTO_TAG"

    const-string v1, "onStop:"

    .line 116
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    return-void
.end method

.method public onWindowFocusChanged(Z)V
    .locals 2

    .line 98
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "onWindowFocusChanged focus = "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    const-string v1, "AHU.ANDROIDAUTO_TAG"

    invoke-static {v1, v0}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    if-eqz p1, :cond_0

    .line 100
    invoke-virtual {p0}, Lcom/adayo/androidauto/ui/AAActivity;->hideBottomUIMenu()V

    .line 102
    :cond_0
    invoke-super {p0, p1}, Landroid/app/Activity;->onWindowFocusChanged(Z)V

    return-void
.end method
