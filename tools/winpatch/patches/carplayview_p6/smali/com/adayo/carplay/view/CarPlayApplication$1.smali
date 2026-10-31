.class Lcom/adayo/carplay/view/CarPlayApplication$1;
.super Lcom/adayo/carplay/proxy/ICarPlayCallback$Stub;
.source "CarPlayApplication.java"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/adayo/carplay/view/CarPlayApplication;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic this$0:Lcom/adayo/carplay/view/CarPlayApplication;


# direct methods
.method constructor <init>(Lcom/adayo/carplay/view/CarPlayApplication;)V
    .locals 0

    .line 52
    iput-object p1, p0, Lcom/adayo/carplay/view/CarPlayApplication$1;->this$0:Lcom/adayo/carplay/view/CarPlayApplication;

    invoke-direct {p0}, Lcom/adayo/carplay/proxy/ICarPlayCallback$Stub;-><init>()V

    return-void
.end method


# virtual methods
.method public onCommEvent(Lcom/adayo/carplay/proxy/Session;IIILjava/lang/String;)V
    .locals 0
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Landroid/os/RemoteException;
        }
    .end annotation

    const-string p1, "onCommEvent "

    .line 55
    invoke-static {p1}, Lcom/adayo/utility/Logger;->i(Ljava/lang/String;)V

    return-void
.end method

.method public onDeviceStatusChange(ILcom/adayo/carplay/proxy/Device;)V
    .locals 0
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Landroid/os/RemoteException;
        }
    .end annotation

    const-string p1, "onDeviceStatusChange "

    .line 156
    invoke-static {p1}, Lcom/adayo/utility/Logger;->i(Ljava/lang/String;)V

    return-void
.end method

.method public onRequestCertificateData()[B
    .locals 1
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Landroid/os/RemoteException;
        }
    .end annotation

    const/4 v0, 0x0

    return-object v0
.end method

.method public onRequestCertificateSN()[B
    .locals 1
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Landroid/os/RemoteException;
        }
    .end annotation

    const/4 v0, 0x0

    return-object v0
.end method

.method public onRequestChallengeResponse([B)[B
    .locals 0
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Landroid/os/RemoteException;
        }
    .end annotation

    const/4 p1, 0x0

    return-object p1
.end method

.method public onServerStatusChange(ILcom/adayo/carplay/proxy/Server;)V
    .locals 0
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Landroid/os/RemoteException;
        }
    .end annotation

    return-void
.end method

.method public onSessionStatusChange(ILcom/adayo/carplay/proxy/Session;)V
    .locals 0
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Landroid/os/RemoteException;
        }
    .end annotation

    const-string p1, "onSessionStatusChange "

    .line 161
    invoke-static {p1}, Lcom/adayo/utility/Logger;->i(Ljava/lang/String;)V

    if-eqz p2, :cond_0

    .line 163
    iget-object p1, p0, Lcom/adayo/carplay/view/CarPlayApplication$1;->this$0:Lcom/adayo/carplay/view/CarPlayApplication;

    invoke-virtual {p2}, Lcom/adayo/carplay/proxy/Session;->getTransportType()I

    move-result p2

    invoke-static {p1, p2}, Lcom/adayo/carplay/view/CarPlayApplication;->access$502(Lcom/adayo/carplay/view/CarPlayApplication;I)I

    :cond_0
    return-void
.end method

.method public onVideoStatusChange(Lcom/adayo/carplay/proxy/Session;IJI)V
    .locals 2
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Landroid/os/RemoteException;
        }
    .end annotation

    .line 65
    invoke-static {p2}, Lcom/adayo/carplay/proxy/CarPlayProxyConstants$VideoStatusType;->fromValue(I)Lcom/adayo/carplay/proxy/CarPlayProxyConstants$VideoStatusType;

    move-result-object p2

    if-nez p2, :cond_0

    const-string p1, "Error statusType"

    .line 67
    invoke-static {p1}, Lcom/adayo/utility/Logger;->e(Ljava/lang/String;)V

    return-void

    .line 70
    :cond_0
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "onVideoStatusChange state: "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 71
    invoke-virtual {p2}, Lcom/adayo/carplay/proxy/CarPlayProxyConstants$VideoStatusType;->name()Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v1, " id: "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p3, p4}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;

    const-string v1, " action: "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p5}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    .line 70
    invoke-static {v0}, Lcom/adayo/utility/Logger;->i(Ljava/lang/String;)V

    .line 75
    sget-object v0, Lcom/adayo/carplay/view/CarPlayApplication$2;->$SwitchMap$com$adayo$carplay$proxy$CarPlayProxyConstants$VideoStatusType:[I

    invoke-virtual {p2}, Lcom/adayo/carplay/proxy/CarPlayProxyConstants$VideoStatusType;->ordinal()I

    move-result p2

    aget p2, v0, p2

    packed-switch p2, :pswitch_data_0

    goto/16 :goto_2

    .line 96
    :pswitch_0
    invoke-static {p5}, Lcom/adayo/carplay/proxy/CarPlayProxyConstants$VideoActionType;->fromValue(I)Lcom/adayo/carplay/proxy/CarPlayProxyConstants$VideoActionType;

    move-result-object p2

    if-nez p2, :cond_1

    const-string p1, "Error actionType"

    .line 98
    invoke-static {p1}, Lcom/adayo/utility/Logger;->e(Ljava/lang/String;)V

    return-void

    .line 101
    :cond_1
    new-instance p5, Ljava/lang/StringBuilder;

    invoke-direct {p5}, Ljava/lang/StringBuilder;-><init>()V

    const-string v0, "onVideoStatusChange actionType:"

    invoke-virtual {p5, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p5, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {p5}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p5

    invoke-static {p5}, Lcom/adayo/utility/Logger;->i(Ljava/lang/String;)V

    .line 102
    iget-object p5, p0, Lcom/adayo/carplay/view/CarPlayApplication$1;->this$0:Lcom/adayo/carplay/view/CarPlayApplication;

    invoke-static {p5}, Lcom/adayo/carplay/view/CarPlayApplication;->access$100(Lcom/adayo/carplay/view/CarPlayApplication;)Ljava/util/Map;

    move-result-object p5

    invoke-static {p3, p4}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;

    move-result-object v0

    invoke-interface {p5, v0}, Ljava/util/Map;->containsKey(Ljava/lang/Object;)Z

    move-result p5

    if-eqz p5, :cond_2

    .line 103
    iget-object p5, p0, Lcom/adayo/carplay/view/CarPlayApplication$1;->this$0:Lcom/adayo/carplay/view/CarPlayApplication;

    invoke-static {p5}, Lcom/adayo/carplay/view/CarPlayApplication;->access$100(Lcom/adayo/carplay/view/CarPlayApplication;)Ljava/util/Map;

    move-result-object p5

    invoke-static {p3, p4}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;

    move-result-object p3

    invoke-interface {p5, p3}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p3

    check-cast p3, Lcom/adayo/carplay/view/ui/activity/MainActivity;

    if-eqz p3, :cond_3

    .line 106
    sget-object p4, Lcom/adayo/carplay/view/CarPlayApplication$2;->$SwitchMap$com$adayo$carplay$proxy$CarPlayProxyConstants$VideoActionType:[I

    invoke-virtual {p2}, Lcom/adayo/carplay/proxy/CarPlayProxyConstants$VideoActionType;->ordinal()I

    move-result p5

    aget p4, p4, p5

    const/4 p5, 0x0

    packed-switch p4, :pswitch_data_1

    goto :goto_0

    .line 119
    :pswitch_1
    invoke-virtual {p3}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->finish()V

    .line 120
    iget-object p3, p0, Lcom/adayo/carplay/view/CarPlayApplication$1;->this$0:Lcom/adayo/carplay/view/CarPlayApplication;

    invoke-static {p3}, Lcom/adayo/carplay/view/CarPlayApplication;->access$100(Lcom/adayo/carplay/view/CarPlayApplication;)Ljava/util/Map;

    move-result-object p3

    invoke-virtual {p1}, Lcom/adayo/carplay/proxy/Session;->getId()J

    move-result-wide p4

    invoke-static {p4, p5}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;

    move-result-object p1

    invoke-interface {p3, p1}, Ljava/util/Map;->remove(Ljava/lang/Object;)Ljava/lang/Object;

    goto :goto_0

    .line 113
    :pswitch_2
    invoke-virtual {p3, p5}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->setNeedTake(Z)V

    .line 114
    invoke-virtual {p3}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->finish()V

    .line 115
    iget-object p3, p0, Lcom/adayo/carplay/view/CarPlayApplication$1;->this$0:Lcom/adayo/carplay/view/CarPlayApplication;

    invoke-static {p3}, Lcom/adayo/carplay/view/CarPlayApplication;->access$100(Lcom/adayo/carplay/view/CarPlayApplication;)Ljava/util/Map;

    move-result-object p3

    invoke-virtual {p1}, Lcom/adayo/carplay/proxy/Session;->getId()J

    move-result-wide p4

    invoke-static {p4, p5}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;

    move-result-object p1

    invoke-interface {p3, p1}, Ljava/util/Map;->remove(Ljava/lang/Object;)Ljava/lang/Object;

    goto :goto_0

    .line 108
    :pswitch_3
    invoke-virtual {p3, p5}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->setNeedTake(Z)V

    goto :goto_0

    .line 130
    :cond_2
    iget-object p1, p0, Lcom/adayo/carplay/view/CarPlayApplication$1;->this$0:Lcom/adayo/carplay/view/CarPlayApplication;

    invoke-static {p1}, Lcom/adayo/carplay/view/CarPlayApplication;->access$300(Lcom/adayo/carplay/view/CarPlayApplication;)Ljava/util/Map;

    move-result-object p1

    invoke-static {p3, p4}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;

    move-result-object p5

    invoke-interface {p1, p5}, Ljava/util/Map;->containsKey(Ljava/lang/Object;)Z

    move-result p1

    if-eqz p1, :cond_3

    .line 131
    iget-object p1, p0, Lcom/adayo/carplay/view/CarPlayApplication$1;->this$0:Lcom/adayo/carplay/view/CarPlayApplication;

    invoke-static {p1}, Lcom/adayo/carplay/view/CarPlayApplication;->access$300(Lcom/adayo/carplay/view/CarPlayApplication;)Ljava/util/Map;

    move-result-object p1

    invoke-static {p3, p4}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;

    move-result-object p5

    invoke-interface {p1, p5}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Lcom/adayo/carplay/proxy/CarPlayProxyConstants$ActivityStatus;

    .line 132
    sget-object p5, Lcom/adayo/carplay/proxy/CarPlayProxyConstants$ActivityStatus;->Creating:Lcom/adayo/carplay/proxy/CarPlayProxyConstants$ActivityStatus;

    invoke-virtual {p5, p1}, Lcom/adayo/carplay/proxy/CarPlayProxyConstants$ActivityStatus;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-eqz p1, :cond_3

    .line 133
    iget-object p1, p0, Lcom/adayo/carplay/view/CarPlayApplication$1;->this$0:Lcom/adayo/carplay/view/CarPlayApplication;

    invoke-static {p1}, Lcom/adayo/carplay/view/CarPlayApplication;->access$300(Lcom/adayo/carplay/view/CarPlayApplication;)Ljava/util/Map;

    move-result-object p1

    invoke-static {p3, p4}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;

    move-result-object p3

    sget-object p4, Lcom/adayo/carplay/proxy/CarPlayProxyConstants$ActivityStatus;->Destroying:Lcom/adayo/carplay/proxy/CarPlayProxyConstants$ActivityStatus;

    invoke-interface {p1, p3, p4}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    .line 137
    :cond_3
    :goto_0
    sget-object p1, Lcom/adayo/carplay/proxy/CarPlayProxyConstants$VideoActionType;->SESSION_END:Lcom/adayo/carplay/proxy/CarPlayProxyConstants$VideoActionType;

    invoke-virtual {p1, p2}, Lcom/adayo/carplay/proxy/CarPlayProxyConstants$VideoActionType;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-eqz p1, :cond_6

    .line 138
    iget-object p1, p0, Lcom/adayo/carplay/view/CarPlayApplication$1;->this$0:Lcom/adayo/carplay/view/CarPlayApplication;

    invoke-static {p1}, Lcom/adayo/carplay/view/CarPlayApplication;->access$400(Lcom/adayo/carplay/view/CarPlayApplication;)Landroid/os/Handler;

    move-result-object p1

    new-instance p2, Lcom/adayo/carplay/view/CarPlayApplication$1$1;

    invoke-direct {p2, p0}, Lcom/adayo/carplay/view/CarPlayApplication$1$1;-><init>(Lcom/adayo/carplay/view/CarPlayApplication$1;)V

    const-wide/16 p3, 0x1f4

    invoke-virtual {p1, p2, p3, p4}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z

    goto/16 :goto_2

    .line 77
    :pswitch_4
    iget-object p1, p0, Lcom/adayo/carplay/view/CarPlayApplication$1;->this$0:Lcom/adayo/carplay/view/CarPlayApplication;

    invoke-static {p1}, Lcom/adayo/carplay/view/CarPlayApplication;->access$100(Lcom/adayo/carplay/view/CarPlayApplication;)Ljava/util/Map;

    move-result-object p1

    invoke-static {p3, p4}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;

    move-result-object p2

    invoke-interface {p1, p2}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Lcom/adayo/carplay/view/ui/activity/MainActivity;

    if-eqz p1, :cond_4

    const/4 p2, 0x1

    .line 79
    invoke-virtual {p1, p2}, Lcom/adayo/carplay/view/ui/activity/MainActivity;->setNeedTake(Z)V

    goto :goto_2

    .line 82
    :cond_4
    invoke-static {}, Lcom/adayo/carplay/view/config/LoadJson;->getInstance()Lcom/adayo/carplay/view/config/LoadJson;

    move-result-object p1

    invoke-virtual {p1}, Lcom/adayo/carplay/view/config/LoadJson;->getConfig()Lcom/adayo/carplay/view/config/bean/Config;

    move-result-object p1

    invoke-virtual {p1}, Lcom/adayo/carplay/view/config/bean/Config;->getFeatures()Lcom/adayo/carplay/view/config/bean/Features;

    move-result-object p1

    if-eqz p1, :cond_5

    invoke-static {}, Lcom/adayo/carplay/view/config/LoadJson;->getInstance()Lcom/adayo/carplay/view/config/LoadJson;

    move-result-object p1

    invoke-virtual {p1}, Lcom/adayo/carplay/view/config/LoadJson;->getConfig()Lcom/adayo/carplay/view/config/bean/Config;

    move-result-object p1

    invoke-virtual {p1}, Lcom/adayo/carplay/view/config/bean/Config;->getFeatures()Lcom/adayo/carplay/view/config/bean/Features;

    move-result-object p1

    iget-boolean p1, p1, Lcom/adayo/carplay/view/config/bean/Features;->autoLauncherActivity:Z

    if-nez p1, :cond_5

    const-string p1, "launcher activity use third"

    .line 83
    invoke-static {p1}, Lcom/adayo/utility/Logger;->i(Ljava/lang/String;)V

    goto :goto_1

    .line 85
    :cond_5
    new-instance p1, Landroid/content/Intent;

    iget-object p2, p0, Lcom/adayo/carplay/view/CarPlayApplication$1;->this$0:Lcom/adayo/carplay/view/CarPlayApplication;

    invoke-static {p2}, Lcom/adayo/carplay/view/CarPlayApplication;->access$200(Lcom/adayo/carplay/view/CarPlayApplication;)Landroid/content/Context;

    move-result-object p2

    const-class p5, Lcom/adayo/carplay/view/ui/activity/MainActivity;

    invoke-direct {p1, p2, p5}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    const/high16 p2, 0x30000000

    .line 86
    invoke-virtual {p1, p2}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;

    const-string p2, "surfaceId"

    .line 87
    invoke-virtual {p1, p2, p3, p4}, Landroid/content/Intent;->putExtra(Ljava/lang/String;J)Landroid/content/Intent;

    .line 88
    new-instance p2, Ljava/lang/StringBuilder;

    invoke-direct {p2}, Ljava/lang/StringBuilder;-><init>()V

    const-string p5, "putExtra surfaceId = "

    invoke-virtual {p2, p5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p2, p3, p4}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;

    invoke-virtual {p2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p2

    invoke-static {p2}, Lcom/adayo/utility/Logger;->i(Ljava/lang/String;)V

    .line 89
    iget-object p2, p0, Lcom/adayo/carplay/view/CarPlayApplication$1;->this$0:Lcom/adayo/carplay/view/CarPlayApplication;

    invoke-static {p2}, Lcom/adayo/carplay/view/CarPlayApplication;->access$200(Lcom/adayo/carplay/view/CarPlayApplication;)Landroid/content/Context;

    move-result-object p2

    iget-object p5, p0, Lcom/adayo/carplay/view/CarPlayApplication$1;->this$0:Lcom/adayo/carplay/view/CarPlayApplication;

    invoke-static {p5}, Lcom/adayo/carplay/view/CarPlayApplication;->access$100(Lcom/adayo/carplay/view/CarPlayApplication;)Ljava/util/Map;

    move-result-object p5

    invoke-interface {p5}, Ljava/util/Map;->values()Ljava/util/Collection;

    move-result-object p5

    invoke-static {p2, p1, p5}, Lcom/adayo/carplay/view/patch/WindowSurface;->startInWindow(Landroid/content/Context;Landroid/content/Intent;Ljava/util/Collection;)V

    .line 91
    :goto_1
    iget-object p1, p0, Lcom/adayo/carplay/view/CarPlayApplication$1;->this$0:Lcom/adayo/carplay/view/CarPlayApplication;

    invoke-static {p1}, Lcom/adayo/carplay/view/CarPlayApplication;->access$300(Lcom/adayo/carplay/view/CarPlayApplication;)Ljava/util/Map;

    move-result-object p1

    invoke-static {p3, p4}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;

    move-result-object p2

    sget-object p3, Lcom/adayo/carplay/proxy/CarPlayProxyConstants$ActivityStatus;->Creating:Lcom/adayo/carplay/proxy/CarPlayProxyConstants$ActivityStatus;

    invoke-interface {p1, p2, p3}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    :cond_6
    :goto_2
    return-void

    nop

    :pswitch_data_0
    .packed-switch 0x1
        :pswitch_4
        :pswitch_0
    .end packed-switch

    :pswitch_data_1
    .packed-switch 0x1
        :pswitch_3
        :pswitch_2
        :pswitch_2
        :pswitch_1
    .end packed-switch
.end method
