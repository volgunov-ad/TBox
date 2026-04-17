package okhttp3.internal.http;

import java.io.EOFException;
import java.util.ArrayList;
import java.util.List;
import kotlin.Deprecated;
import kotlin.DeprecationLevel;
import kotlin.Metadata;
import kotlin.ReplaceWith;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.StringsKt;
import okhttp3.Challenge;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Response;
import okhttp3.internal.Util;
import okhttp3.internal.platform.Platform;
import okio.Buffer;
import okio.ByteString;

/* compiled from: HttpHeaders.kt */
@Metadata(bv = {1, 0, 3}, d1 = {"\u0000R\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010!\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0005\n\u0000\u001a\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0007\u001a\u0018\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\b*\u00020\n2\u0006\u0010\u000b\u001a\u00020\f\u001a\n\u0010\r\u001a\u00020\u0004*\u00020\u0006\u001a\u001a\u0010\u000e\u001a\u00020\u000f*\u00020\u00102\f\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\t0\u0012H\u0002\u001a\u000e\u0010\u0013\u001a\u0004\u0018\u00010\f*\u00020\u0010H\u0002\u001a\u000e\u0010\u0014\u001a\u0004\u0018\u00010\f*\u00020\u0010H\u0002\u001a\u001a\u0010\u0015\u001a\u00020\u000f*\u00020\u00162\u0006\u0010\u0017\u001a\u00020\u00182\u0006\u0010\u0019\u001a\u00020\n\u001a\f\u0010\u001a\u001a\u00020\u0004*\u00020\u0010H\u0002\u001a\u0014\u0010\u001b\u001a\u00020\u0004*\u00020\u00102\u0006\u0010\u001c\u001a\u00020\u001dH\u0002\"\u000e\u0010\u0000\u001a\u00020\u0001X\u0082\u0004¢\u0006\u0002\n\u0000\"\u000e\u0010\u0002\u001a\u00020\u0001X\u0082\u0004¢\u0006\u0002\n\u0000¨\u0006\u001e"}, d2 = {"QUOTED_STRING_DELIMITERS", "Lokio/ByteString;", "TOKEN_DELIMITERS", "hasBody", "", "response", "Lokhttp3/Response;", "parseChallenges", "", "Lokhttp3/Challenge;", "Lokhttp3/Headers;", "headerName", "", "promisesBody", "readChallengeHeader", "", "Lokio/Buffer;", "result", "", "readQuotedString", "readToken", "receiveHeaders", "Lokhttp3/CookieJar;", "url", "Lokhttp3/HttpUrl;", "headers", "skipCommasAndWhitespace", "startsWith", "prefix", "", "okhttp"}, k = 2, mv = {1, 1, 16})
/* loaded from: classes2.dex */
public final class HttpHeaders {
    private static final ByteString QUOTED_STRING_DELIMITERS = ByteString.INSTANCE.encodeUtf8("\"\\");
    private static final ByteString TOKEN_DELIMITERS = ByteString.INSTANCE.encodeUtf8("\t ,=");

    public static final List<Challenge> parseChallenges(Headers parseChallenges, String headerName) {
        Intrinsics.checkParameterIsNotNull(parseChallenges, "$this$parseChallenges");
        Intrinsics.checkParameterIsNotNull(headerName, "headerName");
        ArrayList arrayList = new ArrayList();
        int size = parseChallenges.size();
        for (int i = 0; i < size; i++) {
            if (StringsKt.equals(headerName, parseChallenges.name(i), true)) {
                try {
                    readChallengeHeader(new Buffer().writeUtf8(parseChallenges.value(i)), arrayList);
                } catch (EOFException e) {
                    Platform.INSTANCE.get().log("Unable to parse challenge", 5, e);
                }
            }
        }
        return arrayList;
    }

    /* JADX WARN: Code restructure failed: missing block: B:24:0x008c, code lost:
    
        continue;
     */
    /* JADX WARN: Code restructure failed: missing block: B:55:0x008c, code lost:
    
        continue;
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    private static final void readChallengeHeader(okio.Buffer r8, List<okhttp3.Challenge> r9) throws EOFException {
        /*
            r0 = 0
            r1 = r0
            java.lang.String r1 = (java.lang.String) r1
        L4:
            r2 = r1
        L5:
            if (r2 != 0) goto L11
            skipCommasAndWhitespace(r8)
            java.lang.String r2 = readToken(r8)
            if (r2 != 0) goto L11
            return
        L11:
            boolean r3 = skipCommasAndWhitespace(r8)
            java.lang.String r4 = readToken(r8)
            if (r4 != 0) goto L2f
            boolean r8 = r8.exhausted()
            if (r8 != 0) goto L22
            return
        L22:
            okhttp3.Challenge r8 = new okhttp3.Challenge
            java.util.Map r0 = kotlin.collections.MapsKt.emptyMap()
            r8.<init>(r2, r0)
            r9.add(r8)
            return
        L2f:
            r5 = 61
            byte r5 = (byte) r5
            int r6 = okhttp3.internal.Util.skipAll(r8, r5)
            boolean r7 = skipCommasAndWhitespace(r8)
            if (r3 != 0) goto L6d
            if (r7 != 0) goto L44
            boolean r3 = r8.exhausted()
            if (r3 == 0) goto L6d
        L44:
            okhttp3.Challenge r3 = new okhttp3.Challenge
            java.lang.StringBuilder r5 = new java.lang.StringBuilder
            r5.<init>()
            r5.append(r4)
            java.lang.String r4 = "="
            java.lang.CharSequence r4 = (java.lang.CharSequence) r4
            java.lang.String r4 = kotlin.text.StringsKt.repeat(r4, r6)
            r5.append(r4)
            java.lang.String r4 = r5.toString()
            java.util.Map r4 = java.util.Collections.singletonMap(r0, r4)
            java.lang.String r5 = "Collections.singletonMap…ek + \"=\".repeat(eqCount))"
            kotlin.jvm.internal.Intrinsics.checkExpressionValueIsNotNull(r4, r5)
            r3.<init>(r2, r4)
            r9.add(r3)
            goto L4
        L6d:
            java.util.LinkedHashMap r3 = new java.util.LinkedHashMap
            r3.<init>()
            java.util.Map r3 = (java.util.Map) r3
            int r7 = okhttp3.internal.Util.skipAll(r8, r5)
            int r6 = r6 + r7
        L79:
            if (r4 != 0) goto L8a
            java.lang.String r4 = readToken(r8)
            boolean r6 = skipCommasAndWhitespace(r8)
            if (r6 == 0) goto L86
            goto L8c
        L86:
            int r6 = okhttp3.internal.Util.skipAll(r8, r5)
        L8a:
            if (r6 != 0) goto L97
        L8c:
            okhttp3.Challenge r5 = new okhttp3.Challenge
            r5.<init>(r2, r3)
            r9.add(r5)
            r2 = r4
            goto L5
        L97:
            r7 = 1
            if (r6 <= r7) goto L9b
            return
        L9b:
            boolean r7 = skipCommasAndWhitespace(r8)
            if (r7 == 0) goto La2
            return
        La2:
            r7 = 34
            byte r7 = (byte) r7
            boolean r7 = startsWith(r8, r7)
            if (r7 == 0) goto Lb0
            java.lang.String r7 = readQuotedString(r8)
            goto Lb4
        Lb0:
            java.lang.String r7 = readToken(r8)
        Lb4:
            if (r7 == 0) goto Lce
            java.lang.Object r4 = r3.put(r4, r7)
            java.lang.String r4 = (java.lang.String) r4
            if (r4 == 0) goto Lbf
            return
        Lbf:
            boolean r4 = skipCommasAndWhitespace(r8)
            if (r4 != 0) goto Lcc
            boolean r4 = r8.exhausted()
            if (r4 != 0) goto Lcc
            return
        Lcc:
            r4 = r1
            goto L79
        Lce:
            return
        */
        throw new UnsupportedOperationException("Method not decompiled: okhttp3.internal.http.HttpHeaders.readChallengeHeader(okio.Buffer, java.util.List):void");
    }

    private static final boolean skipCommasAndWhitespace(Buffer buffer) throws EOFException {
        boolean z = false;
        while (!buffer.exhausted()) {
            byte b = buffer.getByte(0L);
            if (b == 9 || b == 32) {
                buffer.readByte();
            } else {
                if (b != 44) {
                    break;
                }
                buffer.readByte();
                z = true;
            }
        }
        return z;
    }

    private static final boolean startsWith(Buffer buffer, byte b) {
        return !buffer.exhausted() && buffer.getByte(0L) == b;
    }

    private static final String readQuotedString(Buffer buffer) throws EOFException {
        byte b = (byte) 34;
        if (!(buffer.readByte() == b)) {
            throw new IllegalArgumentException("Failed requirement.".toString());
        }
        Buffer buffer2 = new Buffer();
        while (true) {
            long indexOfElement = buffer.indexOfElement(QUOTED_STRING_DELIMITERS);
            if (indexOfElement == -1) {
                return null;
            }
            if (buffer.getByte(indexOfElement) == b) {
                buffer2.write(buffer, indexOfElement);
                buffer.readByte();
                return buffer2.readUtf8();
            }
            if (buffer.size() == indexOfElement + 1) {
                return null;
            }
            buffer2.write(buffer, indexOfElement);
            buffer.readByte();
            buffer2.write(buffer, 1L);
        }
    }

    private static final String readToken(Buffer buffer) throws EOFException {
        long indexOfElement = buffer.indexOfElement(TOKEN_DELIMITERS);
        if (indexOfElement == -1) {
            indexOfElement = buffer.size();
        }
        if (indexOfElement != 0) {
            return buffer.readUtf8(indexOfElement);
        }
        return null;
    }

    public static final void receiveHeaders(CookieJar receiveHeaders, HttpUrl url, Headers headers) {
        Intrinsics.checkParameterIsNotNull(receiveHeaders, "$this$receiveHeaders");
        Intrinsics.checkParameterIsNotNull(url, "url");
        Intrinsics.checkParameterIsNotNull(headers, "headers");
        if (receiveHeaders == CookieJar.NO_COOKIES) {
            return;
        }
        List<Cookie> parseAll = Cookie.INSTANCE.parseAll(url, headers);
        if (parseAll.isEmpty()) {
            return;
        }
        receiveHeaders.saveFromResponse(url, parseAll);
    }

    public static final boolean promisesBody(Response promisesBody) {
        Intrinsics.checkParameterIsNotNull(promisesBody, "$this$promisesBody");
        if (Intrinsics.areEqual(promisesBody.request().method(), "HEAD")) {
            return false;
        }
        int code = promisesBody.code();
        return (((code >= 100 && code < 200) || code == 204 || code == 304) && Util.headersContentLength(promisesBody) == -1 && !StringsKt.equals("chunked", Response.header$default(promisesBody, "Transfer-Encoding", null, 2, null), true)) ? false : true;
    }

    @Deprecated(level = DeprecationLevel.ERROR, message = "No longer supported", replaceWith = @ReplaceWith(expression = "response.promisesBody()", imports = {}))
    public static final boolean hasBody(Response response) {
        Intrinsics.checkParameterIsNotNull(response, "response");
        return promisesBody(response);
    }
}
