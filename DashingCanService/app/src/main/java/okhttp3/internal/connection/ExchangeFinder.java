package okhttp3.internal.connection;

import androidx.core.app.NotificationCompat;
import java.io.IOException;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;
import okhttp3.Address;
import okhttp3.EventListener;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Route;
import okhttp3.internal.Util;
import okhttp3.internal.connection.RouteSelector;
import okhttp3.internal.http.ExchangeCodec;
import okhttp3.internal.http.RealInterceptorChain;
import okhttp3.internal.http2.ConnectionShutdownException;
import okhttp3.internal.http2.ErrorCode;
import okhttp3.internal.http2.StreamResetException;

/* compiled from: ExchangeFinder.kt */
@Metadata(bv = {1, 0, 3}, d1 = {"\u0000r\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u0001B%\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t¢\u0006\u0002\u0010\nJ\b\u0010\r\u001a\u0004\u0018\u00010\u000eJ\u0016\u0010\u0019\u001a\u00020\u001a2\u0006\u0010\u001b\u001a\u00020\u001c2\u0006\u0010\u001d\u001a\u00020\u001eJ0\u0010\u001f\u001a\u00020\u000e2\u0006\u0010 \u001a\u00020\u00102\u0006\u0010!\u001a\u00020\u00102\u0006\u0010\"\u001a\u00020\u00102\u0006\u0010#\u001a\u00020\u00102\u0006\u0010$\u001a\u00020%H\u0002J8\u0010&\u001a\u00020\u000e2\u0006\u0010 \u001a\u00020\u00102\u0006\u0010!\u001a\u00020\u00102\u0006\u0010\"\u001a\u00020\u00102\u0006\u0010#\u001a\u00020\u00102\u0006\u0010$\u001a\u00020%2\u0006\u0010'\u001a\u00020%H\u0002J\u0006\u0010(\u001a\u00020%J\b\u0010)\u001a\u00020%H\u0002J\u000e\u0010*\u001a\u00020%2\u0006\u0010+\u001a\u00020,J\u000e\u0010-\u001a\u00020.2\u0006\u0010/\u001a\u000200R\u0014\u0010\u0004\u001a\u00020\u0005X\u0080\u0004¢\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004¢\u0006\u0002\n\u0000R\u0010\u0010\r\u001a\u0004\u0018\u00010\u000eX\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u000f\u001a\u00020\u0010X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082\u0004¢\u0006\u0002\n\u0000R\u0010\u0010\u0011\u001a\u0004\u0018\u00010\u0012X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u0013\u001a\u00020\u0010X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u0014\u001a\u00020\u0010X\u0082\u000e¢\u0006\u0002\n\u0000R\u0010\u0010\u0015\u001a\u0004\u0018\u00010\u0016X\u0082\u000e¢\u0006\u0002\n\u0000R\u0010\u0010\u0017\u001a\u0004\u0018\u00010\u0018X\u0082\u000e¢\u0006\u0002\n\u0000¨\u00061"}, d2 = {"Lokhttp3/internal/connection/ExchangeFinder;", "", "connectionPool", "Lokhttp3/internal/connection/RealConnectionPool;", "address", "Lokhttp3/Address;", NotificationCompat.CATEGORY_CALL, "Lokhttp3/internal/connection/RealCall;", "eventListener", "Lokhttp3/EventListener;", "(Lokhttp3/internal/connection/RealConnectionPool;Lokhttp3/Address;Lokhttp3/internal/connection/RealCall;Lokhttp3/EventListener;)V", "getAddress$okhttp", "()Lokhttp3/Address;", "connectingConnection", "Lokhttp3/internal/connection/RealConnection;", "connectionShutdownCount", "", "nextRouteToTry", "Lokhttp3/Route;", "otherFailureCount", "refusedStreamCount", "routeSelection", "Lokhttp3/internal/connection/RouteSelector$Selection;", "routeSelector", "Lokhttp3/internal/connection/RouteSelector;", "find", "Lokhttp3/internal/http/ExchangeCodec;", "client", "Lokhttp3/OkHttpClient;", "chain", "Lokhttp3/internal/http/RealInterceptorChain;", "findConnection", "connectTimeout", "readTimeout", "writeTimeout", "pingIntervalMillis", "connectionRetryEnabled", "", "findHealthyConnection", "doExtensiveHealthChecks", "retryAfterFailure", "retryCurrentRoute", "sameHostAndPort", "url", "Lokhttp3/HttpUrl;", "trackFailure", "", "e", "Ljava/io/IOException;", "okhttp"}, k = 1, mv = {1, 1, 16})
/* loaded from: classes2.dex */
public final class ExchangeFinder {
    private final Address address;
    private final RealCall call;
    private RealConnection connectingConnection;
    private final RealConnectionPool connectionPool;
    private int connectionShutdownCount;
    private final EventListener eventListener;
    private Route nextRouteToTry;
    private int otherFailureCount;
    private int refusedStreamCount;
    private RouteSelector.Selection routeSelection;
    private RouteSelector routeSelector;

    public ExchangeFinder(RealConnectionPool connectionPool, Address address, RealCall call, EventListener eventListener) {
        Intrinsics.checkParameterIsNotNull(connectionPool, "connectionPool");
        Intrinsics.checkParameterIsNotNull(address, "address");
        Intrinsics.checkParameterIsNotNull(call, "call");
        Intrinsics.checkParameterIsNotNull(eventListener, "eventListener");
        this.connectionPool = connectionPool;
        this.address = address;
        this.call = call;
        this.eventListener = eventListener;
    }

    /* renamed from: getAddress$okhttp, reason: from getter */
    public final Address getAddress() {
        return this.address;
    }

    public final ExchangeCodec find(OkHttpClient client, RealInterceptorChain chain) {
        Intrinsics.checkParameterIsNotNull(client, "client");
        Intrinsics.checkParameterIsNotNull(chain, "chain");
        try {
            return findHealthyConnection(chain.getConnectTimeoutMillis$okhttp(), chain.getReadTimeoutMillis(), chain.getWriteTimeoutMillis(), client.pingIntervalMillis(), client.retryOnConnectionFailure(), !Intrinsics.areEqual(chain.getRequest().method(), "GET")).newCodec$okhttp(client, chain);
        } catch (IOException e) {
            trackFailure(e);
            throw new RouteException(e);
        } catch (RouteException e2) {
            trackFailure(e2.getLastConnectException());
            throw e2;
        }
    }

    /* JADX WARN: Code restructure failed: missing block: B:22:0x003b, code lost:
    
        throw new java.io.IOException("exhausted all routes");
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    private final RealConnection findHealthyConnection(int r4, int r5, int r6, int r7, boolean r8, boolean r9) throws IOException {
        /*
            r3 = this;
        L0:
            okhttp3.internal.connection.RealConnection r0 = r3.findConnection(r4, r5, r6, r7, r8)
            boolean r1 = r0.isHealthy(r9)
            if (r1 == 0) goto Lb
            return r0
        Lb:
            r0.noNewExchanges()
            okhttp3.internal.connection.RealConnectionPool r0 = r3.connectionPool
            monitor-enter(r0)
            okhttp3.Route r1 = r3.nextRouteToTry     // Catch: java.lang.Throwable -> L3c
            if (r1 == 0) goto L16
            goto L2e
        L16:
            okhttp3.internal.connection.RouteSelector$Selection r1 = r3.routeSelection     // Catch: java.lang.Throwable -> L3c
            r2 = 1
            if (r1 == 0) goto L20
            boolean r1 = r1.hasNext()     // Catch: java.lang.Throwable -> L3c
            goto L21
        L20:
            r1 = r2
        L21:
            if (r1 == 0) goto L24
            goto L2e
        L24:
            okhttp3.internal.connection.RouteSelector r1 = r3.routeSelector     // Catch: java.lang.Throwable -> L3c
            if (r1 == 0) goto L2c
            boolean r2 = r1.hasNext()     // Catch: java.lang.Throwable -> L3c
        L2c:
            if (r2 == 0) goto L32
        L2e:
            kotlin.Unit r1 = kotlin.Unit.INSTANCE     // Catch: java.lang.Throwable -> L3c
            monitor-exit(r0)
            goto L0
        L32:
            java.io.IOException r4 = new java.io.IOException     // Catch: java.lang.Throwable -> L3c
            java.lang.String r5 = "exhausted all routes"
            r4.<init>(r5)     // Catch: java.lang.Throwable -> L3c
            java.lang.Throwable r4 = (java.lang.Throwable) r4     // Catch: java.lang.Throwable -> L3c
            throw r4     // Catch: java.lang.Throwable -> L3c
        L3c:
            r4 = move-exception
            monitor-exit(r0)
            throw r4
        */
        throw new UnsupportedOperationException("Method not decompiled: okhttp3.internal.connection.ExchangeFinder.findHealthyConnection(int, int, int, int, boolean, boolean):okhttp3.internal.connection.RealConnection");
    }

    /* JADX WARN: Code restructure failed: missing block: B:47:0x00cc, code lost:
    
        if (r4.hasNext() == false) goto L190;
     */
    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Removed duplicated region for block: B:54:0x00fa A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /* JADX WARN: Type inference failed for: r6v3, types: [T, okhttp3.internal.connection.RealConnection] */
    /* JADX WARN: Type inference failed for: r7v4, types: [T, okhttp3.internal.connection.RealConnection] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    private final RealConnection findConnection(int r19, int r20, int r21, int r22, boolean r23) throws IOException {
        /*
            Method dump skipped, instructions count: 526
            To view this dump change 'Code comments level' option to 'DEBUG'
        */
        throw new UnsupportedOperationException("Method not decompiled: okhttp3.internal.connection.ExchangeFinder.findConnection(int, int, int, int, boolean):okhttp3.internal.connection.RealConnection");
    }

    public final RealConnection connectingConnection() {
        RealConnectionPool realConnectionPool = this.connectionPool;
        if (!Util.assertionsEnabled || Thread.holdsLock(realConnectionPool)) {
            return this.connectingConnection;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Thread ");
        Thread currentThread = Thread.currentThread();
        Intrinsics.checkExpressionValueIsNotNull(currentThread, "Thread.currentThread()");
        sb.append(currentThread.getName());
        sb.append(" MUST hold lock on ");
        sb.append(realConnectionPool);
        throw new AssertionError(sb.toString());
    }

    public final void trackFailure(IOException e) {
        Intrinsics.checkParameterIsNotNull(e, "e");
        RealConnectionPool realConnectionPool = this.connectionPool;
        if (!Util.assertionsEnabled || !Thread.holdsLock(realConnectionPool)) {
            synchronized (this.connectionPool) {
                this.nextRouteToTry = (Route) null;
                if ((e instanceof StreamResetException) && ((StreamResetException) e).errorCode == ErrorCode.REFUSED_STREAM) {
                    this.refusedStreamCount++;
                } else if (e instanceof ConnectionShutdownException) {
                    this.connectionShutdownCount++;
                } else {
                    this.otherFailureCount++;
                }
            }
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Thread ");
        Thread currentThread = Thread.currentThread();
        Intrinsics.checkExpressionValueIsNotNull(currentThread, "Thread.currentThread()");
        sb.append(currentThread.getName());
        sb.append(" MUST NOT hold lock on ");
        sb.append(realConnectionPool);
        throw new AssertionError(sb.toString());
    }

    public final boolean retryAfterFailure() {
        synchronized (this.connectionPool) {
            if (this.refusedStreamCount == 0 && this.connectionShutdownCount == 0 && this.otherFailureCount == 0) {
                return false;
            }
            if (this.nextRouteToTry != null) {
                return true;
            }
            if (retryCurrentRoute()) {
                RealConnection connection = this.call.getConnection();
                if (connection == null) {
                    Intrinsics.throwNpe();
                }
                this.nextRouteToTry = connection.getRoute();
                return true;
            }
            RouteSelector.Selection selection = this.routeSelection;
            if (selection != null && selection.hasNext()) {
                return true;
            }
            RouteSelector routeSelector = this.routeSelector;
            if (routeSelector == null) {
                return true;
            }
            return routeSelector.hasNext();
        }
    }

    private final boolean retryCurrentRoute() {
        RealConnection connection;
        return this.refusedStreamCount <= 1 && this.connectionShutdownCount <= 1 && this.otherFailureCount <= 0 && (connection = this.call.getConnection()) != null && connection.getRouteFailureCount() == 0 && Util.canReuseConnectionFor(connection.getRoute().address().url(), this.address.url());
    }

    public final boolean sameHostAndPort(HttpUrl url) {
        Intrinsics.checkParameterIsNotNull(url, "url");
        HttpUrl url2 = this.address.url();
        return url.port() == url2.port() && Intrinsics.areEqual(url.host(), url2.host());
    }
}
