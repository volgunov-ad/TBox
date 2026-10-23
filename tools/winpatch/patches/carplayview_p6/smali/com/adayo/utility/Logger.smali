.class public Lcom/adayo/utility/Logger;
.super Ljava/lang/Object;
.source "Logger.java"


# static fields
.field private static logLevel:I = 0x0

.field private static logTag:Ljava/lang/String; = "Logger"

.field private static showFileName:Z

.field private static showLineNumber:Z

.field private static writeLogAble:Z

.field private static writer:Ljava/io/PrintWriter;


# direct methods
.method static constructor <clinit>()V
    .locals 0

    return-void
.end method

.method public constructor <init>()V
    .locals 0

    .line 20
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method private static baseLog(ILjava/lang/String;Ljava/lang/String;)V
    .locals 4

    .line 125
    sget v0, Lcom/adayo/utility/Logger;->logLevel:I

    if-ge p0, v0, :cond_0

    return-void

    .line 132
    :cond_0
    invoke-static {}, Lcom/adayo/utility/Logger;->getTargetStackTraceElement()Ljava/lang/StackTraceElement;

    move-result-object v0

    .line 134
    sget-boolean v1, Lcom/adayo/utility/Logger;->showFileName:Z

    const/4 v2, 0x0

    if-eqz v1, :cond_1

    .line 135
    invoke-virtual {v0}, Ljava/lang/StackTraceElement;->getFileName()Ljava/lang/String;

    move-result-object v1

    goto :goto_0

    :cond_1
    move-object v1, v2

    .line 138
    :goto_0
    sget-boolean v3, Lcom/adayo/utility/Logger;->showLineNumber:Z

    if-eqz v3, :cond_2

    .line 139
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "("

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StackTraceElement;->getLineNumber()I

    move-result v0

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v0, "):"

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    .line 142
    :cond_2
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    if-nez v1, :cond_3

    const-string v3, ""

    goto :goto_1

    :cond_3
    move-object v3, v1

    :goto_1
    invoke-virtual {v0, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    if-nez v2, :cond_4

    const-string v3, ""

    goto :goto_2

    :cond_4
    move-object v3, v2

    :goto_2
    invoke-virtual {v0, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {p0, p1, v0}, Landroid/util/Log;->println(ILjava/lang/String;Ljava/lang/String;)I

    .line 144
    sget-boolean p1, Lcom/adayo/utility/Logger;->writeLogAble:Z

    if-eqz p1, :cond_8

    .line 145
    new-instance p1, Ljava/text/SimpleDateFormat;

    const-string v0, "yyyy-MM-dd:HH-mm-sss"

    invoke-static {}, Ljava/util/Locale;->getDefault()Ljava/util/Locale;

    move-result-object v3

    invoke-direct {p1, v0, v3}, Ljava/text/SimpleDateFormat;-><init>(Ljava/lang/String;Ljava/util/Locale;)V

    new-instance v0, Ljava/util/Date;

    invoke-direct {v0}, Ljava/util/Date;-><init>()V

    invoke-virtual {p1, v0}, Ljava/text/SimpleDateFormat;->format(Ljava/util/Date;)Ljava/lang/String;

    move-result-object p1

    packed-switch p0, :pswitch_data_0

    const-string p0, " V "

    goto :goto_3

    :pswitch_0
    const-string p0, " E "

    goto :goto_3

    :pswitch_1
    const-string p0, " W "

    goto :goto_3

    :pswitch_2
    const-string p0, " I "

    goto :goto_3

    :pswitch_3
    const-string p0, " D "

    goto :goto_3

    :pswitch_4
    const-string p0, " V "

    .line 175
    :goto_3
    :try_start_0
    sget-object v0, Lcom/adayo/utility/Logger;->writer:Ljava/io/PrintWriter;

    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    if-nez p1, :cond_5

    const-string p1, ""

    :cond_5
    invoke-virtual {v3, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string p1, ": "

    invoke-virtual {v3, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-static {}, Landroid/os/Process;->myPid()I

    move-result p1

    invoke-virtual {v3, p1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v3, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    if-nez v1, :cond_6

    const-string v1, ""

    :cond_6
    invoke-virtual {v3, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    if-nez v2, :cond_7

    const-string v2, ""

    :cond_7
    invoke-virtual {v3, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string p0, "\r\n"

    invoke-virtual {v3, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    invoke-virtual {v0, p0}, Ljava/io/PrintWriter;->write(Ljava/lang/String;)V

    .line 176
    sget-object p0, Lcom/adayo/utility/Logger;->writer:Ljava/io/PrintWriter;

    invoke-virtual {p0}, Ljava/io/PrintWriter;->flush()V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_4

    :catch_0
    move-exception p0

    .line 178
    invoke-virtual {p0}, Ljava/lang/Exception;->printStackTrace()V

    .line 179
    sget-object p0, Lcom/adayo/utility/Logger;->writer:Ljava/io/PrintWriter;

    if-eqz p0, :cond_8

    .line 180
    sget-object p0, Lcom/adayo/utility/Logger;->writer:Ljava/io/PrintWriter;

    invoke-virtual {p0}, Ljava/io/PrintWriter;->flush()V

    .line 181
    sget-object p0, Lcom/adayo/utility/Logger;->writer:Ljava/io/PrintWriter;

    invoke-virtual {p0}, Ljava/io/PrintWriter;->close()V

    :cond_8
    :goto_4
    return-void

    :pswitch_data_0
    .packed-switch 0x2
        :pswitch_4
        :pswitch_3
        :pswitch_2
        :pswitch_1
        :pswitch_0
    .end packed-switch
.end method

.method private static createLogFile(Ljava/lang/String;)Z
    .locals 4

    const/4 v0, 0x0

    if-nez p0, :cond_0

    return v0

    .line 57
    :cond_0
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    sget-object v2, Lcom/adayo/utility/Logger;->logTag:Ljava/lang/String;

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v2, ".log"

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    .line 58
    new-instance v2, Ljava/io/File;

    invoke-direct {v2, p0}, Ljava/io/File;-><init>(Ljava/lang/String;)V

    .line 59
    invoke-virtual {v2}, Ljava/io/File;->exists()Z

    move-result v3

    if-nez v3, :cond_1

    .line 60
    invoke-virtual {v2}, Ljava/io/File;->mkdirs()Z

    move-result v3

    if-nez v3, :cond_1

    return v0

    .line 64
    :cond_1
    invoke-virtual {v2}, Ljava/io/File;->exists()Z

    move-result v3

    if-eqz v3, :cond_4

    invoke-virtual {v2}, Ljava/io/File;->canWrite()Z

    move-result v2

    if-nez v2, :cond_2

    goto :goto_0

    .line 67
    :cond_2
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v2, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    sget-object p0, Ljava/io/File;->separator:Ljava/lang/String;

    invoke-virtual {v2, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    .line 68
    sget-object v1, Lcom/adayo/utility/Logger;->logTag:Ljava/lang/String;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "logFullPath: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-static {v1, v2}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    .line 69
    new-instance v1, Ljava/io/File;

    invoke-direct {v1, p0}, Ljava/io/File;-><init>(Ljava/lang/String;)V

    .line 72
    :try_start_0
    new-instance p0, Ljava/io/PrintWriter;

    const-string v2, "UTF_8"

    invoke-direct {p0, v1, v2}, Ljava/io/PrintWriter;-><init>(Ljava/io/File;Ljava/lang/String;)V

    sput-object p0, Lcom/adayo/utility/Logger;->writer:Ljava/io/PrintWriter;
    :try_end_0
    .catch Ljava/io/IOException; {:try_start_0 .. :try_end_0} :catch_0

    const/4 p0, 0x1

    return p0

    :catch_0
    move-exception p0

    .line 74
    invoke-virtual {p0}, Ljava/io/IOException;->printStackTrace()V

    .line 75
    sget-object p0, Lcom/adayo/utility/Logger;->writer:Ljava/io/PrintWriter;

    if-eqz p0, :cond_3

    .line 76
    sget-object p0, Lcom/adayo/utility/Logger;->writer:Ljava/io/PrintWriter;

    invoke-virtual {p0}, Ljava/io/PrintWriter;->flush()V

    .line 77
    sget-object p0, Lcom/adayo/utility/Logger;->writer:Ljava/io/PrintWriter;

    invoke-virtual {p0}, Ljava/io/PrintWriter;->close()V

    :cond_3
    return v0

    :cond_4
    :goto_0
    return v0
.end method

.method public static d(Ljava/lang/String;)V
    .locals 1

    .line 89
    sget-object v0, Lcom/adayo/utility/Logger;->logTag:Ljava/lang/String;

    invoke-static {v0, p0}, Lcom/adayo/utility/Logger;->d(Ljava/lang/String;Ljava/lang/String;)V

    return-void
.end method

.method public static d(Ljava/lang/String;Ljava/lang/String;)V
    .locals 1

    const/4 v0, 0x3

    .line 109
    invoke-static {v0, p0, p1}, Lcom/adayo/utility/Logger;->baseLog(ILjava/lang/String;Ljava/lang/String;)V

    return-void
.end method

.method public static e(Ljava/lang/String;)V
    .locals 1

    .line 101
    sget-object v0, Lcom/adayo/utility/Logger;->logTag:Ljava/lang/String;

    invoke-static {v0, p0}, Lcom/adayo/utility/Logger;->e(Ljava/lang/String;Ljava/lang/String;)V

    return-void
.end method

.method public static e(Ljava/lang/String;Ljava/lang/String;)V
    .locals 1

    const/4 v0, 0x6

    .line 121
    invoke-static {v0, p0, p1}, Lcom/adayo/utility/Logger;->baseLog(ILjava/lang/String;Ljava/lang/String;)V

    return-void
.end method

.method private static getTargetStackTraceElement()Ljava/lang/StackTraceElement;
    .locals 7

    .line 190
    invoke-static {}, Ljava/lang/Thread;->currentThread()Ljava/lang/Thread;

    move-result-object v0

    invoke-virtual {v0}, Ljava/lang/Thread;->getStackTrace()[Ljava/lang/StackTraceElement;

    move-result-object v0

    .line 191
    array-length v1, v0

    const/4 v2, 0x0

    const/4 v3, 0x0

    :goto_0
    if-ge v2, v1, :cond_1

    aget-object v4, v0, v2

    .line 192
    invoke-virtual {v4}, Ljava/lang/StackTraceElement;->getClassName()Ljava/lang/String;

    move-result-object v5

    const-class v6, Lcom/adayo/utility/Logger;

    invoke-virtual {v6}, Ljava/lang/Class;->getName()Ljava/lang/String;

    move-result-object v6

    invoke-virtual {v5, v6}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v5

    if-eqz v3, :cond_0

    if-nez v5, :cond_0

    goto :goto_1

    :cond_0
    add-int/lit8 v2, v2, 0x1

    move v3, v5

    goto :goto_0

    :cond_1
    const/4 v4, 0x0

    :goto_1
    return-object v4
.end method

.method public static i(Ljava/lang/String;)V
    .locals 1

    .line 93
    sget-object v0, Lcom/adayo/utility/Logger;->logTag:Ljava/lang/String;

    invoke-static {v0, p0}, Lcom/adayo/utility/Logger;->i(Ljava/lang/String;Ljava/lang/String;)V

    return-void
.end method

.method public static i(Ljava/lang/String;Ljava/lang/String;)V
    .locals 1

    const/4 v0, 0x4

    .line 113
    invoke-static {v0, p0, p1}, Lcom/adayo/utility/Logger;->baseLog(ILjava/lang/String;Ljava/lang/String;)V

    return-void
.end method

.method public static setLogFileSavePath(Ljava/lang/String;)V
    .locals 0

    if-eqz p0, :cond_0

    .line 49
    invoke-static {p0}, Lcom/adayo/utility/Logger;->createLogFile(Ljava/lang/String;)Z

    move-result p0

    sput-boolean p0, Lcom/adayo/utility/Logger;->writeLogAble:Z

    :cond_0
    return-void
.end method

.method public static setLogLevel(I)V
    .locals 0

    .line 31
    sput p0, Lcom/adayo/utility/Logger;->logLevel:I

    return-void
.end method

.method public static setLogTag(Ljava/lang/String;)V
    .locals 0

    .line 27
    sput-object p0, Lcom/adayo/utility/Logger;->logTag:Ljava/lang/String;

    return-void
.end method

.method public static setShowFileName(Z)V
    .locals 0

    .line 35
    sput-boolean p0, Lcom/adayo/utility/Logger;->showFileName:Z

    return-void
.end method

.method public static setShowLineNumber(Z)V
    .locals 0

    .line 39
    sput-boolean p0, Lcom/adayo/utility/Logger;->showLineNumber:Z

    return-void
.end method

.method public static v(Ljava/lang/String;)V
    .locals 1

    .line 85
    sget-object v0, Lcom/adayo/utility/Logger;->logTag:Ljava/lang/String;

    invoke-static {v0, p0}, Lcom/adayo/utility/Logger;->v(Ljava/lang/String;Ljava/lang/String;)V

    return-void
.end method

.method public static v(Ljava/lang/String;Ljava/lang/String;)V
    .locals 1

    const/4 v0, 0x2

    .line 105
    invoke-static {v0, p0, p1}, Lcom/adayo/utility/Logger;->baseLog(ILjava/lang/String;Ljava/lang/String;)V

    return-void
.end method

.method public static w(Ljava/lang/String;)V
    .locals 1

    .line 97
    sget-object v0, Lcom/adayo/utility/Logger;->logTag:Ljava/lang/String;

    invoke-static {v0, p0}, Lcom/adayo/utility/Logger;->w(Ljava/lang/String;Ljava/lang/String;)V

    return-void
.end method

.method public static w(Ljava/lang/String;Ljava/lang/String;)V
    .locals 1

    const/4 v0, 0x5

    .line 117
    invoke-static {v0, p0, p1}, Lcom/adayo/utility/Logger;->baseLog(ILjava/lang/String;Ljava/lang/String;)V

    return-void
.end method
