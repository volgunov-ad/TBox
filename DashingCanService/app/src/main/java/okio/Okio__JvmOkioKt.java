package okio;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Arrays;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.StringsKt;

/* compiled from: JvmOkio.kt */
@Metadata(bv = {1, 0, 3}, d1 = {"\u0000D\n\u0000\n\u0002\u0010\u000b\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0011\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\u001a\n\u0010\u0005\u001a\u00020\u0006*\u00020\u0007\u001a\u0016\u0010\b\u001a\u00020\u0006*\u00020\u00072\b\b\u0002\u0010\t\u001a\u00020\u0001H\u0007\u001a\n\u0010\b\u001a\u00020\u0006*\u00020\n\u001a\n\u0010\b\u001a\u00020\u0006*\u00020\u000b\u001a%\u0010\b\u001a\u00020\u0006*\u00020\f2\u0012\u0010\r\u001a\n\u0012\u0006\b\u0001\u0012\u00020\u000f0\u000e\"\u00020\u000fH\u0007¢\u0006\u0002\u0010\u0010\u001a\n\u0010\u0011\u001a\u00020\u0012*\u00020\u0007\u001a\n\u0010\u0011\u001a\u00020\u0012*\u00020\u0013\u001a\n\u0010\u0011\u001a\u00020\u0012*\u00020\u000b\u001a%\u0010\u0011\u001a\u00020\u0012*\u00020\f2\u0012\u0010\r\u001a\n\u0012\u0006\b\u0001\u0012\u00020\u000f0\u000e\"\u00020\u000fH\u0007¢\u0006\u0002\u0010\u0014\"\u001c\u0010\u0000\u001a\u00020\u0001*\u00060\u0002j\u0002`\u00038@X\u0080\u0004¢\u0006\u0006\u001a\u0004\b\u0000\u0010\u0004¨\u0006\u0015"}, d2 = {"isAndroidGetsocknameError", "", "Ljava/lang/AssertionError;", "Lkotlin/AssertionError;", "(Ljava/lang/AssertionError;)Z", "appendingSink", "Lokio/Sink;", "Ljava/io/File;", "sink", "append", "Ljava/io/OutputStream;", "Ljava/net/Socket;", "Ljava/nio/file/Path;", "options", "", "Ljava/nio/file/OpenOption;", "(Ljava/nio/file/Path;[Ljava/nio/file/OpenOption;)Lokio/Sink;", "source", "Lokio/Source;", "Ljava/io/InputStream;", "(Ljava/nio/file/Path;[Ljava/nio/file/OpenOption;)Lokio/Source;", "okio"}, k = 5, mv = {1, 1, 16}, xs = "okio/Okio")
/* loaded from: classes2.dex */
public final /* synthetic */ class Okio__JvmOkioKt {
    public static final Sink sink(File file) throws FileNotFoundException {
        return sink$default(file, false, 1, null);
    }

    public static final Sink sink(OutputStream sink) {
        Intrinsics.checkParameterIsNotNull(sink, "$this$sink");
        return new OutputStreamSink(sink, new Timeout() {
            @Override
            public boolean getHasDeadline() {
                return false;
            }
        });
    }

    public static final Source source(InputStream source) {
        Intrinsics.checkParameterIsNotNull(source, "$this$source");
        return new InputStreamSource(source, new Timeout() {
            @Override
            public boolean getHasDeadline() {
                return false;
            }
        });
    }

    public static final Sink sink(Socket sink) throws IOException {
        Intrinsics.checkParameterIsNotNull(sink, "$this$sink");
        SocketAsyncTimeout socketAsyncTimeout = new SocketAsyncTimeout(sink);
        OutputStream outputStream = sink.getOutputStream();
        Intrinsics.checkExpressionValueIsNotNull(outputStream, "getOutputStream()");
        return socketAsyncTimeout.sink(new OutputStreamSink(outputStream, socketAsyncTimeout));
    }

    public static final Source source(Socket source) throws IOException {
        Intrinsics.checkParameterIsNotNull(source, "$this$source");
        SocketAsyncTimeout socketAsyncTimeout = new SocketAsyncTimeout(source);
        InputStream inputStream = source.getInputStream();
        Intrinsics.checkExpressionValueIsNotNull(inputStream, "getInputStream()");
        return socketAsyncTimeout.source(new InputStreamSource(inputStream, socketAsyncTimeout));
    }

    public static final Sink sink(File sink, boolean z) throws FileNotFoundException {
        Intrinsics.checkParameterIsNotNull(sink, "$this$sink");
        return Okio.sink(new FileOutputStream(sink, z));
    }

    public static /* synthetic */ Sink sink$default(File file, boolean z, int i, Object obj) throws FileNotFoundException {
        if ((i & 1) != 0) {
            z = false;
        }
        return Okio.sink(file, z);
    }

    public static final Sink appendingSink(File appendingSink) throws FileNotFoundException {
        Intrinsics.checkParameterIsNotNull(appendingSink, "$this$appendingSink");
        return Okio.sink(new FileOutputStream(appendingSink, true));
    }

    public static final Source source(File source) throws FileNotFoundException {
        Intrinsics.checkParameterIsNotNull(source, "$this$source");
        return Okio.source(new FileInputStream(source));
    }

    public static final Sink sink(Path sink, OpenOption... options) throws IOException {
        Intrinsics.checkParameterIsNotNull(sink, "$this$sink");
        Intrinsics.checkParameterIsNotNull(options, "options");
        OutputStream newOutputStream = Files.newOutputStream(sink, (OpenOption[]) Arrays.copyOf(options, options.length));
        Intrinsics.checkExpressionValueIsNotNull(newOutputStream, "Files.newOutputStream(this, *options)");
        return Okio.sink(newOutputStream);
    }

    public static final Source source(Path source, OpenOption... options) throws IOException {
        Intrinsics.checkParameterIsNotNull(source, "$this$source");
        Intrinsics.checkParameterIsNotNull(options, "options");
        InputStream newInputStream = Files.newInputStream(source, (OpenOption[]) Arrays.copyOf(options, options.length));
        Intrinsics.checkExpressionValueIsNotNull(newInputStream, "Files.newInputStream(this, *options)");
        return Okio.source(newInputStream);
    }

    public static final boolean isAndroidGetsocknameError(AssertionError isAndroidGetsocknameError) {
        Intrinsics.checkParameterIsNotNull(isAndroidGetsocknameError, "$this$isAndroidGetsocknameError");
        if (isAndroidGetsocknameError.getCause() != null) {
            String message = isAndroidGetsocknameError.getMessage();
            return message != null ? StringsKt.contains((CharSequence) message, (CharSequence) "getsockname failed", false) : false;
        }
        return false;
    }
}