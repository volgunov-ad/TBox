package okhttp3.internal.concurrent;

import com.mengbo.factory.constants.MBParams;

import java.io.IOException;
import java.util.Set;

import kotlin.Metadata;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import okhttp3.internal.http2.ErrorCode;
import okhttp3.internal.http2.Http2Connection;
import okhttp3.internal.http2.Http2Writer;
import okhttp3.internal.http2.PushObserver;
import okhttp3.internal.http2.Settings;

/* compiled from: Task.kt */
@Metadata(bv = {1, 0, 3}, d1 = {"\u00000\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0006\n\u0002\u0010\t\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0002\b\u0004\b&\u0018\u00002\u00020\u0001B\u0017\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u0004\u001a\u00020\u0005¢\u0006\u0002\u0010\u0006J\u0015\u0010\u0017\u001a\u00020\u00182\u0006\u0010\u0011\u001a\u00020\u0012H\u0000¢\u0006\u0002\b\u0019J\b\u0010\u001a\u001a\u00020\fH&J\b\u0010\u001b\u001a\u00020\u0003H\u0016R\u0011\u0010\u0004\u001a\u00020\u0005¢\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u0002\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u001a\u0010\u000b\u001a\u00020\fX\u0080\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\r\u0010\u000e\"\u0004\b\u000f\u0010\u0010R\u001c\u0010\u0011\u001a\u0004\u0018\u00010\u0012X\u0080\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u0013\u0010\u0014\"\u0004\b\u0015\u0010\u0016¨\u0006\u001c"}, d2 = {"Lokhttp3/internal/concurrent/Task;", "", MBParams.NAME, "", "cancelable", "", "(Ljava/lang/String;Z)V", "getCancelable", "()Z", "getName", "()Ljava/lang/String;", "nextExecuteNanoTime", "", "getNextExecuteNanoTime$okhttp", "()J", "setNextExecuteNanoTime$okhttp", "(J)V", "queue", "Lokhttp3/internal/concurrent/TaskQueue;", "getQueue$okhttp", "()Lokhttp3/internal/concurrent/TaskQueue;", "setQueue$okhttp", "(Lokhttp3/internal/concurrent/TaskQueue;)V", "initQueue", "", "initQueue$okhttp", "runOnce", "toString", "okhttp"}, k = 1, mv = {1, 1, 16})
/* loaded from: classes2.dex */
public abstract class Task {
    private final boolean cancelable;
    private final String name;
    protected PushObserver pushObserver;
    protected Set currentPushRequests;
    protected long intervalPongsReceived;
    public long intervalPingsSent;
    private long nextExecuteNanoTime;
    private TaskQueue queue;

    public Task(String str, boolean cancelable, String name) {

        this.cancelable = cancelable;
        this.name = name;
    }

    public abstract long runOnce();

    public Task(String name, boolean z) {
        Intrinsics.checkParameterIsNotNull(name, "name");
        this.name = name;
        this.cancelable = z;
        this.nextExecuteNanoTime = -1L;
    }

    public final String getName() {
        return this.name;
    }

    public /* synthetic */ Task(String str, boolean z, int i, DefaultConstructorMarker defaultConstructorMarker) {
        this(str, (i & 2) != 0 ? true : z);
    }

    public final boolean getCancelable() {
        return this.cancelable;
    }

    public final TaskQueue getQueue$okhttp() {
        return this.queue;
    }

    public final void setQueue$okhttp(TaskQueue taskQueue) {
        this.queue = taskQueue;
    }

    public final long getNextExecuteNanoTime$okhttp() {
        return this.nextExecuteNanoTime;
    }

    public final void setNextExecuteNanoTime$okhttp(long j) {
        this.nextExecuteNanoTime = j;
    }

    public final void initQueue$okhttp(TaskQueue queue) {
        Intrinsics.checkParameterIsNotNull(queue, "queue");
        TaskQueue taskQueue = this.queue;
        if (taskQueue == queue) {
            return;
        }
        if (!(taskQueue == null)) {
            throw new IllegalStateException("task is in multiple queues".toString());
        }
        this.queue = queue;
    }

    public String toString() {
        return this.name;
    }

    protected void writePingFrame() {

    }

    protected Http2Writer getWriter() {
        return null;
    }

    protected Http2Connection.Listener getListener() {
        return null;
    }

    protected String getConnectionName() {
        return "";
    }

    protected void writePing(boolean b, int i, int i2) {
    }

    protected void applyAndAckSettings(boolean z, Settings settings) {
    }

    protected void writeSynReset(int i, ErrorCode errorCode) {
    }

    protected void failConnection(IOException e) {
    }

    public long getNextExecuteNanoTime() {
        return 0;
    }
}