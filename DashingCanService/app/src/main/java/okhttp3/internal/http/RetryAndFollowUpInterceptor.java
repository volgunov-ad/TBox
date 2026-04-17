package okhttp3.internal.http;

import androidx.core.app.NotificationCompat;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.security.cert.CertificateException;
import java.util.Collection;
import java.util.List;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import kotlin.Metadata;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.Regex;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.Route;
import okhttp3.internal.Util;
import okhttp3.internal.connection.Exchange;
import okhttp3.internal.connection.RealCall;
import okhttp3.internal.connection.RealConnection;
import okhttp3.internal.connection.RouteException;
import okhttp3.internal.http2.ConnectionShutdownException;

/* compiled from: RetryAndFollowUpInterceptor.kt */
@Metadata(bv = {1, 0, 3}, d1 = {"\u0000R\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0003\u0018\u0000 \u001e2\u00020\u0001:\u0001\u001eB\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003¢\u0006\u0002\u0010\u0004J\u001a\u0010\u0005\u001a\u0004\u0018\u00010\u00062\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\nH\u0002J\u001c\u0010\u000b\u001a\u0004\u0018\u00010\u00062\u0006\u0010\u0007\u001a\u00020\b2\b\u0010\f\u001a\u0004\u0018\u00010\rH\u0002J\u0010\u0010\u000e\u001a\u00020\b2\u0006\u0010\u000f\u001a\u00020\u0010H\u0016J\u0018\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00020\u0012H\u0002J(\u0010\u0016\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0017\u001a\u00020\u00182\u0006\u0010\u0019\u001a\u00020\u00062\u0006\u0010\u0015\u001a\u00020\u0012H\u0002J\u0018\u0010\u001a\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0019\u001a\u00020\u0006H\u0002J\u0018\u0010\u001b\u001a\u00020\u001c2\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\u001d\u001a\u00020\u001cH\u0002R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004¢\u0006\u0002\n\u0000¨\u0006\u001f"}, d2 = {"Lokhttp3/internal/http/RetryAndFollowUpInterceptor;", "Lokhttp3/Interceptor;", "client", "Lokhttp3/OkHttpClient;", "(Lokhttp3/OkHttpClient;)V", "buildRedirectRequest", "Lokhttp3/Request;", "userResponse", "Lokhttp3/Response;", "method", "", "followUpRequest", "exchange", "Lokhttp3/internal/connection/Exchange;", "intercept", "chain", "Lokhttp3/Interceptor$Chain;", "isRecoverable", "", "e", "Ljava/io/IOException;", "requestSendStarted", "recover", NotificationCompat.CATEGORY_CALL, "Lokhttp3/internal/connection/RealCall;", "userRequest", "requestIsOneShot", "retryAfter", "", "defaultDelay", "Companion", "okhttp"}, k = 1, mv = {1, 1, 16})
/* loaded from: classes2.dex */
public final class RetryAndFollowUpInterceptor implements Interceptor {
    private static final int MAX_FOLLOW_UPS = 20;
    private final OkHttpClient client;

    public RetryAndFollowUpInterceptor(OkHttpClient client) {
        Intrinsics.checkParameterIsNotNull(client, "client");
        this.client = client;
    }

    @Override // okhttp3.Interceptor
    public Response intercept(Chain chain) throws Throwable {
        Exchange interceptorScopedExchange = null;
        Request followUpRequest = null;
        Intrinsics.checkParameterIsNotNull(chain, "chain");
        RealInterceptorChain realInterceptorChain = (RealInterceptorChain) chain;
        Request request = realInterceptorChain.getRequest();
        RealCall call = realInterceptorChain.getCall();
        Response response = (Response) null;
        List emptyList = CollectionsKt.emptyList();
        boolean z = true;
        int i = 0;
        while (true) {
            call.enterNetworkInterceptorExchange(request, z);
            try {
                if (call.isCanceled()) {
                    throw new IOException("Canceled");
                }
                try {
                    try {
                        Response proceed = realInterceptorChain.proceed(request);
                        if (response != null) {
                            proceed = proceed.newBuilder().priorResponse(response.newBuilder().body(null).build()).build();
                        }
                        response = proceed;
                        interceptorScopedExchange = call.getInterceptorScopedExchange();
                        followUpRequest = followUpRequest(response, interceptorScopedExchange);
                    } catch (IOException e) {
                        if (!recover(e, call, request, !(e instanceof ConnectionShutdownException))) {
                            throw Util.withSuppressed(e, emptyList);
                        }
                        emptyList = CollectionsKt.plus((Collection<? extends IOException>) emptyList, e);
                        call.exitNetworkInterceptorExchange$okhttp(true);
                        z = false;
                    }
                } catch (RouteException e2) {
                    if (!recover(e2.getLastConnectException(), call, request, false)) {
                        throw Util.withSuppressed(e2.getFirstConnectException(), emptyList);
                    }
                    emptyList = CollectionsKt.plus((Collection<? extends IOException>) emptyList, e2.getFirstConnectException());
                    call.exitNetworkInterceptorExchange$okhttp(true);
                    z = false;
                }
                if (followUpRequest == null) {
                    if (interceptorScopedExchange != null && interceptorScopedExchange.getIsDuplex()) {
                        call.timeoutEarlyExit();
                    }
                    call.exitNetworkInterceptorExchange$okhttp(false);
                    return response;
                }
                RequestBody body = followUpRequest.body();
                if (body != null && body.isOneShot()) {
                    call.exitNetworkInterceptorExchange$okhttp(false);
                    return response;
                }
                ResponseBody body2 = response.body();
                if (body2 != null) {
                    Util.closeQuietly(body2);
                }
                i++;
                if (i > 20) {
                    throw new ProtocolException("Too many follow-up requests: " + i);
                }
                call.exitNetworkInterceptorExchange$okhttp(true);
                request = followUpRequest;
                z = true;
            } catch (Throwable th) {
                call.exitNetworkInterceptorExchange$okhttp(true);
                throw th;
            }
        }
    }

    private final boolean recover(IOException e, RealCall r4, Request userRequest, boolean requestSendStarted) {
        if (this.client.retryOnConnectionFailure()) {
            return !(requestSendStarted && requestIsOneShot(e, userRequest)) && isRecoverable(e, requestSendStarted) && r4.retryAfterFailure();
        }
        return false;
    }

    private final boolean requestIsOneShot(IOException e, Request userRequest) {
        RequestBody body = userRequest.body();
        return (body != null && body.isOneShot()) || (e instanceof FileNotFoundException);
    }

    private final boolean isRecoverable(IOException e, boolean requestSendStarted) {
        if (e instanceof ProtocolException) {
            return false;
        }
        return e instanceof InterruptedIOException ? (e instanceof SocketTimeoutException) && !requestSendStarted : (((e instanceof SSLHandshakeException) && (e.getCause() instanceof CertificateException)) || (e instanceof SSLPeerUnverifiedException)) ? false : true;
    }

    private final Request followUpRequest(Response userResponse, Exchange exchange) throws IOException {
        RealConnection connection;
        Route route = (exchange == null || (connection = exchange.getConnection()) == null) ? null : connection.getRoute();
        int code = userResponse.code();
        String method = userResponse.request().method();
        if (code == 307 || code == 308) {
            if ((!Intrinsics.areEqual(method, "GET")) && (!Intrinsics.areEqual(method, "HEAD"))) {
                return null;
            }
            return buildRedirectRequest(userResponse, method);
        }
        if (code == 401) {
            return this.client.authenticator().authenticate(route, userResponse);
        }
        if (code == 421) {
            RequestBody body = userResponse.request().body();
            if ((body != null && body.isOneShot()) || exchange == null || !exchange.isCoalescedConnection$okhttp()) {
                return null;
            }
            exchange.getConnection().noCoalescedConnections();
            return userResponse.request();
        }
        if (code == 503) {
            Response priorResponse = userResponse.priorResponse();
            if ((priorResponse == null || priorResponse.code() != 503) && retryAfter(userResponse, Integer.MAX_VALUE) == 0) {
                return userResponse.request();
            }
            return null;
        }
        if (code == 407) {
            if (route == null) {
                Intrinsics.throwNpe();
            }
            if (route.proxy().type() != Proxy.Type.HTTP) {
                throw new ProtocolException("Received HTTP_PROXY_AUTH (407) code while not using proxy");
            }
            return this.client.proxyAuthenticator().authenticate(route, userResponse);
        }
        if (code != 408) {
            switch (code) {
                case 300:
                case 301:
                case 302:
                case 303:
                    return buildRedirectRequest(userResponse, method);
                default:
                    return null;
            }
        }
        if (!this.client.retryOnConnectionFailure()) {
            return null;
        }
        RequestBody body2 = userResponse.request().body();
        if (body2 != null && body2.isOneShot()) {
            return null;
        }
        Response priorResponse2 = userResponse.priorResponse();
        if ((priorResponse2 == null || priorResponse2.code() != 408) && retryAfter(userResponse, 0) <= 0) {
            return userResponse.request();
        }
        return null;
    }

    private final Request buildRedirectRequest(Response userResponse, String method) throws EOFException {
        String header$default;
        HttpUrl resolve;
        if (!this.client.followRedirects() || (header$default = Response.header$default(userResponse, "Location", null, 2, null)) == null || (resolve = userResponse.request().url().resolve(header$default)) == null) {
            return null;
        }
        if (!Intrinsics.areEqual(resolve.scheme(), userResponse.request().url().scheme()) && !this.client.followSslRedirects()) {
            return null;
        }
        Request.Builder newBuilder = userResponse.request().newBuilder();
        if (HttpMethod.permitsRequestBody(method)) {
            boolean redirectsWithBody = HttpMethod.INSTANCE.redirectsWithBody(method);
            if (HttpMethod.INSTANCE.redirectsToGet(method)) {
                newBuilder.method("GET", null);
            } else {
                newBuilder.method(method, redirectsWithBody ? userResponse.request().body() : null);
            }
            if (!redirectsWithBody) {
                newBuilder.removeHeader("Transfer-Encoding");
                newBuilder.removeHeader("Content-Length");
                newBuilder.removeHeader("Content-Type");
            }
        }
        if (!Util.canReuseConnectionFor(userResponse.request().url(), resolve)) {
            newBuilder.removeHeader("Authorization");
        }
        return newBuilder.url(resolve).build();
    }

    private final int retryAfter(Response userResponse, int defaultDelay) {
        String header$default = Response.header$default(userResponse, "Retry-After", null, 2, null);
        if (header$default == null) {
            return defaultDelay;
        }
        if (!new Regex("\\d+").matches(header$default)) {
            return Integer.MAX_VALUE;
        }
        Integer valueOf = Integer.valueOf(header$default);
        Intrinsics.checkExpressionValueIsNotNull(valueOf, "Integer.valueOf(header)");
        return valueOf.intValue();
    }
}
