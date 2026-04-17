package okhttp3;

import androidx.core.app.NotificationCompat;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;

/* compiled from: EventListener.kt */
@Metadata(bv = {1, 0, 3}, d1 = {"\u0000|\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\t\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0004\b&\u0018\u0000 :2\u00020\u0001:\u0002:;B\u0005¢\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016J\u0018\u0010\u0007\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\b\u001a\u00020\tH\u0016J\u0010\u0010\n\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016J\u0010\u0010\u000b\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016J*\u0010\f\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u00102\b\u0010\u0011\u001a\u0004\u0018\u00010\u0012H\u0016J2\u0010\u0013\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u00102\b\u0010\u0011\u001a\u0004\u0018\u00010\u00122\u0006\u0010\b\u001a\u00020\tH\u0016J \u0010\u0014\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u0010H\u0016J\u0018\u0010\u0015\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0016\u001a\u00020\u0017H\u0016J\u0018\u0010\u0018\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0016\u001a\u00020\u0017H\u0016J+\u0010\u0019\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u001a\u001a\u00020\u001b2\u0011\u0010\u001c\u001a\r\u0012\t\u0012\u00070\u001e¢\u0006\u0002\b\u001f0\u001dH\u0016J\u0018\u0010 \u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u001a\u001a\u00020\u001bH\u0016J+\u0010!\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\"\u001a\u00020#2\u0011\u0010$\u001a\r\u0012\t\u0012\u00070\u0010¢\u0006\u0002\b\u001f0\u001dH\u0016J\u0018\u0010%\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\"\u001a\u00020#H\u0016J\u0018\u0010&\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010'\u001a\u00020(H\u0016J\u0010\u0010)\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016J\u0018\u0010*\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\b\u001a\u00020\tH\u0016J\u0018\u0010+\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010,\u001a\u00020-H\u0016J\u0010\u0010.\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016J\u0018\u0010/\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010'\u001a\u00020(H\u0016J\u0010\u00100\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016J\u0018\u00101\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\b\u001a\u00020\tH\u0016J\u0018\u00102\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u00103\u001a\u000204H\u0016J\u0010\u00105\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016J\u001a\u00106\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\b\u00107\u001a\u0004\u0018\u000108H\u0016J\u0010\u00109\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016¨\u0006<"}, d2 = {"Lokhttp3/EventListener;", "", "()V", "callEnd", "", NotificationCompat.CATEGORY_CALL, "Lokhttp3/Call;", "callFailed", "ioe", "Ljava/io/IOException;", "callStart", "canceled", "connectEnd", "inetSocketAddress", "Ljava/net/InetSocketAddress;", "proxy", "Ljava/net/Proxy;", "protocol", "Lokhttp3/Protocol;", "connectFailed", "connectStart", "connectionAcquired", "connection", "Lokhttp3/Connection;", "connectionReleased", "dnsEnd", "domainName", "", "inetAddressList", "", "Ljava/net/InetAddress;", "Lkotlin/jvm/JvmSuppressWildcards;", "dnsStart", "proxySelectEnd", "url", "Lokhttp3/HttpUrl;", "proxies", "proxySelectStart", "requestBodyEnd", "byteCount", "", "requestBodyStart", "requestFailed", "requestHeadersEnd", "request", "Lokhttp3/Request;", "requestHeadersStart", "responseBodyEnd", "responseBodyStart", "responseFailed", "responseHeadersEnd", "response", "Lokhttp3/Response;", "responseHeadersStart", "secureConnectEnd", "handshake", "Lokhttp3/Handshake;", "secureConnectStart", "Companion", "Factory", "okhttp"}, k = 1, mv = {1, 1, 16})
/* loaded from: classes2.dex */
public abstract class EventListener {
    public static final EventListener NONE = new EventListener() { // from class: okhttp3.EventListener$Companion$NONE$1
    };

    /* compiled from: EventListener.kt */
    @Metadata(bv = {1, 0, 3}, d1 = {"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\bf\u0018\u00002\u00020\u0001J\u0010\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H&¨\u0006\u0006"}, d2 = {"Lokhttp3/EventListener$Factory;", "", "create", "Lokhttp3/EventListener;", NotificationCompat.CATEGORY_CALL, "Lokhttp3/Call;", "okhttp"}, k = 1, mv = {1, 1, 16})
    /* loaded from: classes2.dex */
    public interface Factory {
        EventListener create(Call r1);
    }

    public void callEnd(Call r2) {
        Intrinsics.checkParameterIsNotNull(r2, "call");
    }

    public void callFailed(Call r2, IOException ioe) {
        Intrinsics.checkParameterIsNotNull(r2, "call");
        Intrinsics.checkParameterIsNotNull(ioe, "ioe");
    }

    public void callStart(Call r2) {
        Intrinsics.checkParameterIsNotNull(r2, "call");
    }

    public void canceled(Call r2) {
        Intrinsics.checkParameterIsNotNull(r2, "call");
    }

    public void connectEnd(Call r1, InetSocketAddress inetSocketAddress, Proxy proxy, Protocol protocol) {
        Intrinsics.checkParameterIsNotNull(r1, "call");
        Intrinsics.checkParameterIsNotNull(inetSocketAddress, "inetSocketAddress");
        Intrinsics.checkParameterIsNotNull(proxy, "proxy");
    }

    public void connectFailed(Call r1, InetSocketAddress inetSocketAddress, Proxy proxy, Protocol protocol, IOException ioe) {
        Intrinsics.checkParameterIsNotNull(r1, "call");
        Intrinsics.checkParameterIsNotNull(inetSocketAddress, "inetSocketAddress");
        Intrinsics.checkParameterIsNotNull(proxy, "proxy");
        Intrinsics.checkParameterIsNotNull(ioe, "ioe");
    }

    public void connectStart(Call r2, InetSocketAddress inetSocketAddress, Proxy proxy) {
        Intrinsics.checkParameterIsNotNull(r2, "call");
        Intrinsics.checkParameterIsNotNull(inetSocketAddress, "inetSocketAddress");
        Intrinsics.checkParameterIsNotNull(proxy, "proxy");
    }

    public void connectionAcquired(Call r2, Connection connection) {
        Intrinsics.checkParameterIsNotNull(r2, "call");
        Intrinsics.checkParameterIsNotNull(connection, "connection");
    }

    public void connectionReleased(Call r2, Connection connection) {
        Intrinsics.checkParameterIsNotNull(r2, "call");
        Intrinsics.checkParameterIsNotNull(connection, "connection");
    }

    public void dnsEnd(Call r2, String domainName, List<InetAddress> inetAddressList) {
        Intrinsics.checkParameterIsNotNull(r2, "call");
        Intrinsics.checkParameterIsNotNull(domainName, "domainName");
        Intrinsics.checkParameterIsNotNull(inetAddressList, "inetAddressList");
    }

    public void dnsStart(Call r2, String domainName) {
        Intrinsics.checkParameterIsNotNull(r2, "call");
        Intrinsics.checkParameterIsNotNull(domainName, "domainName");
    }

    public void proxySelectEnd(Call r2, HttpUrl url, List<Proxy> proxies) {
        Intrinsics.checkParameterIsNotNull(r2, "call");
        Intrinsics.checkParameterIsNotNull(url, "url");
        Intrinsics.checkParameterIsNotNull(proxies, "proxies");
    }

    public void proxySelectStart(Call r2, HttpUrl url) {
        Intrinsics.checkParameterIsNotNull(r2, "call");
        Intrinsics.checkParameterIsNotNull(url, "url");
    }

    public void requestBodyEnd(Call r1, long byteCount) {
        Intrinsics.checkParameterIsNotNull(r1, "call");
    }

    public void requestBodyStart(Call r2) {
        Intrinsics.checkParameterIsNotNull(r2, "call");
    }

    public void requestFailed(Call r2, IOException ioe) {
        Intrinsics.checkParameterIsNotNull(r2, "call");
        Intrinsics.checkParameterIsNotNull(ioe, "ioe");
    }

    public void requestHeadersEnd(Call r2, Request request) {
        Intrinsics.checkParameterIsNotNull(r2, "call");
        Intrinsics.checkParameterIsNotNull(request, "request");
    }

    public void requestHeadersStart(Call r2) {
        Intrinsics.checkParameterIsNotNull(r2, "call");
    }

    public void responseBodyEnd(Call r1, long byteCount) {
        Intrinsics.checkParameterIsNotNull(r1, "call");
    }

    public void responseBodyStart(Call r2) {
        Intrinsics.checkParameterIsNotNull(r2, "call");
    }

    public void responseFailed(Call r2, IOException ioe) {
        Intrinsics.checkParameterIsNotNull(r2, "call");
        Intrinsics.checkParameterIsNotNull(ioe, "ioe");
    }

    public void responseHeadersEnd(Call r2, Response response) {
        Intrinsics.checkParameterIsNotNull(r2, "call");
        Intrinsics.checkParameterIsNotNull(response, "response");
    }

    public void responseHeadersStart(Call r2) {
        Intrinsics.checkParameterIsNotNull(r2, "call");
    }

    public void secureConnectEnd(Call r1, Handshake handshake) {
        Intrinsics.checkParameterIsNotNull(r1, "call");
    }

    public void secureConnectStart(Call r2) {
        Intrinsics.checkParameterIsNotNull(r2, "call");
    }
}
