.class Lcom/adayo/androidauto/patch/AAWindowPatch$2;
.super Ljava/lang/Object;
.source "AAWindowPatch.java"

# interfaces
.implements Ljava/lang/Runnable;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/adayo/androidauto/patch/AAWindowPatch;->watchAccOff(Landroid/content/Context;)V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic val$context:Landroid/content/Context;

.field final synthetic val$filter:Landroid/content/IntentFilter;

.field final synthetic val$receiver:Landroid/content/BroadcastReceiver;


# direct methods
.method constructor <init>(Landroid/content/Context;Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)V
    .locals 0
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()V"
        }
    .end annotation

    .line 88
    iput-object p1, p0, Lcom/adayo/androidauto/patch/AAWindowPatch$2;->val$context:Landroid/content/Context;

    iput-object p2, p0, Lcom/adayo/androidauto/patch/AAWindowPatch$2;->val$receiver:Landroid/content/BroadcastReceiver;

    iput-object p3, p0, Lcom/adayo/androidauto/patch/AAWindowPatch$2;->val$filter:Landroid/content/IntentFilter;

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public run()V
    .locals 3

    .line 91
    iget-object v0, p0, Lcom/adayo/androidauto/patch/AAWindowPatch$2;->val$context:Landroid/content/Context;

    invoke-virtual {v0}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;

    move-result-object v0

    iget-object v1, p0, Lcom/adayo/androidauto/patch/AAWindowPatch$2;->val$receiver:Landroid/content/BroadcastReceiver;

    iget-object v2, p0, Lcom/adayo/androidauto/patch/AAWindowPatch$2;->val$filter:Landroid/content/IntentFilter;

    invoke-virtual {v0, v1, v2}, Landroid/content/Context;->registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;

    .line 92
    return-void
.end method
