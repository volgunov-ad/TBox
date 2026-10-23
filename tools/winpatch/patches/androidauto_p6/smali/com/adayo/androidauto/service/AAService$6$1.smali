.class Lcom/adayo/androidauto/service/AAService$6$1;
.super Ljava/lang/Object;
.source "AAService.java"

# interfaces
.implements Ljava/lang/Runnable;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/adayo/androidauto/service/AAService$6;->onSourceVideoConfigChanged(IILcom/google/android/projection/proto/Protos$VideoConfiguration;)V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic this$1:Lcom/adayo/androidauto/service/AAService$6;


# direct methods
.method constructor <init>(Lcom/adayo/androidauto/service/AAService$6;)V
    .locals 0

    .line 686
    iput-object p1, p0, Lcom/adayo/androidauto/service/AAService$6$1;->this$1:Lcom/adayo/androidauto/service/AAService$6;

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public run()V
    .locals 4

    .line 690
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService$6$1;->this$1:Lcom/adayo/androidauto/service/AAService$6;

    iget-object v0, v0, Lcom/adayo/androidauto/service/AAService$6;->this$0:Lcom/adayo/androidauto/service/AAService;

    invoke-static {v0}, Lcom/adayo/androidauto/service/AAService;->access$600(Lcom/adayo/androidauto/service/AAService;)Lcom/adayo/androidauto/video/AAProjectionRenderer;

    move-result-object v0

    if-nez v0, :cond_0

    const-string v0, "AHU.AAS.20240704"

    const-string v1, "onSourceVideoConfigChanged,runOnUiThread mProjectionRenderer==null"

    .line 691
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->w(Ljava/lang/String;Ljava/lang/String;)V

    return-void

    .line 695
    :cond_0
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService$6$1;->this$1:Lcom/adayo/androidauto/service/AAService$6;

    iget-object v0, v0, Lcom/adayo/androidauto/service/AAService$6;->this$0:Lcom/adayo/androidauto/service/AAService;

    invoke-static {v0}, Lcom/adayo/androidauto/service/AAService;->access$600(Lcom/adayo/androidauto/service/AAService;)Lcom/adayo/androidauto/video/AAProjectionRenderer;

    move-result-object v0

    invoke-virtual {v0}, Lcom/adayo/androidauto/video/AAProjectionRenderer;->start()V

    .line 696
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService$6$1;->this$1:Lcom/adayo/androidauto/service/AAService$6;

    iget-object v0, v0, Lcom/adayo/androidauto/service/AAService$6;->this$0:Lcom/adayo/androidauto/service/AAService;

    iget-object v0, v0, Lcom/adayo/androidauto/service/AAService;->mMainHandler:Landroid/os/Handler;

    const/16 v1, 0x65

    invoke-virtual {v0, v1}, Landroid/os/Handler;->sendEmptyMessage(I)Z

    .line 697
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService$6$1;->this$1:Lcom/adayo/androidauto/service/AAService$6;

    iget-object v0, v0, Lcom/adayo/androidauto/service/AAService$6;->this$0:Lcom/adayo/androidauto/service/AAService;

    iget-object v0, v0, Lcom/adayo/androidauto/service/AAService;->mInstructHandler:Landroid/os/Handler;

    const/16 v1, 0x3e9

    invoke-virtual {v0, v1}, Landroid/os/Handler;->removeMessages(I)V

    .line 698
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService$6$1;->this$1:Lcom/adayo/androidauto/service/AAService$6;

    iget-object v0, v0, Lcom/adayo/androidauto/service/AAService$6;->this$0:Lcom/adayo/androidauto/service/AAService;

    iget-object v0, v0, Lcom/adayo/androidauto/service/AAService;->mInstructHandler:Landroid/os/Handler;

    const/16 v1, 0x3ea

    invoke-virtual {v0, v1}, Landroid/os/Handler;->sendEmptyMessage(I)Z

    .line 699
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService$6$1;->this$1:Lcom/adayo/androidauto/service/AAService$6;

    iget-object v0, v0, Lcom/adayo/androidauto/service/AAService$6;->this$0:Lcom/adayo/androidauto/service/AAService;

    invoke-static {v0}, Lcom/adayo/androidauto/service/AAService;->access$200(Lcom/adayo/androidauto/service/AAService;)Landroid/view/SurfaceView;

    move-result-object v0

    if-nez v0, :cond_1

    .line 700
    iget-object v0, p0, Lcom/adayo/androidauto/service/AAService$6$1;->this$1:Lcom/adayo/androidauto/service/AAService$6;

    iget-object v0, v0, Lcom/adayo/androidauto/service/AAService$6;->this$0:Lcom/adayo/androidauto/service/AAService;

    iget-object v0, v0, Lcom/adayo/androidauto/service/AAService;->mMainHandler:Landroid/os/Handler;

    const/16 v1, 0x66

    const-wide/16 v2, 0x1f4

    nop

    :cond_1
    return-void
.end method
