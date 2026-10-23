.class Lcom/adayo/androidauto/service/AAService$16;
.super Ljava/lang/Object;
.source "AAService.java"

# interfaces
.implements Lcom/google/android/projection/transport/ServerSocketProvider;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/adayo/androidauto/service/AAService;->onCreate()V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic this$0:Lcom/adayo/androidauto/service/AAService;


# direct methods
.method constructor <init>(Lcom/adayo/androidauto/service/AAService;)V
    .locals 0

    .line 1226
    iput-object p1, p0, Lcom/adayo/androidauto/service/AAService$16;->this$0:Lcom/adayo/androidauto/service/AAService;

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public getServerSocket()Ljava/net/ServerSocket;
    .locals 5

    const-string v0, "AHU.AAS.20240704"

    const-string v1, ""

    .line 1229
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    const/4 v1, 0x0

    .line 1232
    :try_start_0
    new-instance v2, Ljava/net/ServerSocket;

    invoke-direct {v2}, Ljava/net/ServerSocket;-><init>()V
    :try_end_0
    .catch Ljava/io/IOException; {:try_start_0 .. :try_end_0} :catch_1

    const/4 v1, 0x1

    .line 1233
    :try_start_1
    invoke-virtual {v2, v1}, Ljava/net/ServerSocket;->setReuseAddress(Z)V

    new-instance v3, Ljava/net/InetSocketAddress;

    const/16 v4, 0x2537

    invoke-direct {v3, v4}, Ljava/net/InetSocketAddress;-><init>(I)V

    invoke-virtual {v2, v3}, Ljava/net/ServerSocket;->bind(Ljava/net/SocketAddress;)V

    const/16 v1, 0x3a98

    .line 1234
    invoke-virtual {v2, v1}, Ljava/net/ServerSocket;->setSoTimeout(I)V

    const/high16 v1, 0x20000

    .line 1235
    invoke-virtual {v2, v1}, Ljava/net/ServerSocket;->setReceiveBufferSize(I)V
    :try_end_1
    .catch Ljava/io/IOException; {:try_start_1 .. :try_end_1} :catch_0

    goto :goto_1

    :catch_0
    move-exception v1

    goto :goto_0

    :catch_1
    move-exception v2

    move-object v4, v2

    move-object v2, v1

    move-object v1, v4

    .line 1237
    :goto_0
    invoke-virtual {v1}, Ljava/io/IOException;->printStackTrace()V

    .line 1238
    invoke-virtual {v1}, Ljava/io/IOException;->getMessage()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V

    .line 1240
    :goto_1
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "mTransportFactory getServerSocket "

    invoke-virtual {v1, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->d(Ljava/lang/String;Ljava/lang/String;)V

    return-object v2
.end method

.method public invalidateSocket()V
    .locals 2

    const-string v0, "AHU.AAS.20240704"

    const-string v1, "invalidateSocket"

    .line 1246
    invoke-static {v0, v1}, Lcom/adayo/androidauto/utils/AALog;->e(Ljava/lang/String;Ljava/lang/String;)V

    return-void
.end method
