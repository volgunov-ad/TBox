.class Lcom/adayo/androidauto/patch/AAWindowPatch$3;
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
.field final synthetic val$register:Ljava/lang/Runnable;


# direct methods
.method constructor <init>(Ljava/lang/Runnable;)V
    .locals 0
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()V"
        }
    .end annotation

    .line 98
    iput-object p1, p0, Lcom/adayo/androidauto/patch/AAWindowPatch$3;->val$register:Ljava/lang/Runnable;

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public run()V
    .locals 1

    .line 101
    iget-object v0, p0, Lcom/adayo/androidauto/patch/AAWindowPatch$3;->val$register:Ljava/lang/Runnable;

    invoke-interface {v0}, Ljava/lang/Runnable;->run()V

    .line 102
    const/4 v0, 0x1

    invoke-static {v0}, Lcom/adayo/androidauto/patch/AAWindowPatch;->access$002(Z)Z

    .line 103
    return-void
.end method
