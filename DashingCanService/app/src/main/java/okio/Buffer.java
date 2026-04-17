package okio;

import com.openos.weather.WeatherEntry;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import kotlin.Deprecated;
import kotlin.DeprecationLevel;
import kotlin.Metadata;
import kotlin.ReplaceWith;
import kotlin.TypeCastException;
import kotlin.UByte;
import kotlin.collections.ArraysKt;
import kotlin.jvm.internal.ByteCompanionObject;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.LongCompanionObject;
import kotlin.jvm.internal.StringCompanionObject;
import kotlin.text.Charsets;
import kotlin.text.Typography;
import okhttp3.internal.connection.RealConnection;
import okio.internal.BufferKt;

/* compiled from: Buffer.kt */
@Metadata(bv = {1, 0, 3}, d1 = {"\u0000ª\u0001\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u001a\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0010\u0005\n\u0002\b\u0005\n\u0002\u0010\b\n\u0002\b\r\n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0018\u0002\n\u0002\u0010\u0012\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u000f\n\u0002\u0010\n\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\n\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0017\u0018\u00002\u00020\u00012\u00020\u00022\u00020\u00032\u00020\u0004:\u0002\u0090\u0001B\u0005¢\u0006\u0002\u0010\u0005J\b\u0010\u0006\u001a\u00020\u0000H\u0016J\u0006\u0010\u0011\u001a\u00020\u0012J\b\u0010\u0013\u001a\u00020\u0000H\u0016J\b\u0010\u0014\u001a\u00020\u0012H\u0016J\u0006\u0010\u0015\u001a\u00020\fJ\u0006\u0010\u0016\u001a\u00020\u0000J$\u0010\u0017\u001a\u00020\u00002\u0006\u0010\u0018\u001a\u00020\u00192\b\b\u0002\u0010\u001a\u001a\u00020\f2\b\b\u0002\u0010\u001b\u001a\u00020\fH\u0007J\u0018\u0010\u0017\u001a\u00020\u00002\u0006\u0010\u0018\u001a\u00020\u00002\b\b\u0002\u0010\u001a\u001a\u00020\fJ \u0010\u0017\u001a\u00020\u00002\u0006\u0010\u0018\u001a\u00020\u00002\b\b\u0002\u0010\u001a\u001a\u00020\f2\u0006\u0010\u001b\u001a\u00020\fJ\u0010\u0010\u001c\u001a\u00020\u001d2\u0006\u0010\u001e\u001a\u00020\u001fH\u0002J\b\u0010 \u001a\u00020\u0000H\u0016J\b\u0010!\u001a\u00020\u0000H\u0016J\u0013\u0010\"\u001a\u00020#2\b\u0010$\u001a\u0004\u0018\u00010%H\u0096\u0002J\b\u0010&\u001a\u00020#H\u0016J\b\u0010'\u001a\u00020\u0012H\u0016J\u0016\u0010(\u001a\u00020)2\u0006\u0010*\u001a\u00020\fH\u0087\u0002¢\u0006\u0002\b+J\u0015\u0010+\u001a\u00020)2\u0006\u0010,\u001a\u00020\fH\u0007¢\u0006\u0002\b-J\b\u0010.\u001a\u00020/H\u0016J\u0018\u00100\u001a\u00020\u001d2\u0006\u0010\u001e\u001a\u00020\u001f2\u0006\u00101\u001a\u00020\u001dH\u0002J\u000e\u00102\u001a\u00020\u001d2\u0006\u00101\u001a\u00020\u001dJ\u000e\u00103\u001a\u00020\u001d2\u0006\u00101\u001a\u00020\u001dJ\u000e\u00104\u001a\u00020\u001d2\u0006\u00101\u001a\u00020\u001dJ\u0010\u00105\u001a\u00020\f2\u0006\u00106\u001a\u00020)H\u0016J\u0018\u00105\u001a\u00020\f2\u0006\u00106\u001a\u00020)2\u0006\u00107\u001a\u00020\fH\u0016J \u00105\u001a\u00020\f2\u0006\u00106\u001a\u00020)2\u0006\u00107\u001a\u00020\f2\u0006\u00108\u001a\u00020\fH\u0016J\u0010\u00105\u001a\u00020\f2\u0006\u00109\u001a\u00020\u001dH\u0016J\u0018\u00105\u001a\u00020\f2\u0006\u00109\u001a\u00020\u001d2\u0006\u00107\u001a\u00020\fH\u0016J\u0010\u0010:\u001a\u00020\f2\u0006\u0010;\u001a\u00020\u001dH\u0016J\u0018\u0010:\u001a\u00020\f2\u0006\u0010;\u001a\u00020\u001d2\u0006\u00107\u001a\u00020\fH\u0016J\b\u0010<\u001a\u00020=H\u0016J\b\u0010>\u001a\u00020#H\u0016J\u0006\u0010?\u001a\u00020\u001dJ\b\u0010@\u001a\u00020\u0019H\u0016J\b\u0010A\u001a\u00020\u0001H\u0016J\u0018\u0010B\u001a\u00020#2\u0006\u0010\u001a\u001a\u00020\f2\u0006\u00109\u001a\u00020\u001dH\u0016J(\u0010B\u001a\u00020#2\u0006\u0010\u001a\u001a\u00020\f2\u0006\u00109\u001a\u00020\u001d2\u0006\u0010C\u001a\u00020/2\u0006\u0010\u001b\u001a\u00020/H\u0016J\u0010\u0010D\u001a\u00020/2\u0006\u0010E\u001a\u00020FH\u0016J\u0010\u0010D\u001a\u00020/2\u0006\u0010E\u001a\u00020GH\u0016J \u0010D\u001a\u00020/2\u0006\u0010E\u001a\u00020G2\u0006\u0010\u001a\u001a\u00020/2\u0006\u0010\u001b\u001a\u00020/H\u0016J\u0018\u0010D\u001a\u00020\f2\u0006\u0010E\u001a\u00020\u00002\u0006\u0010\u001b\u001a\u00020\fH\u0016J\u0010\u0010H\u001a\u00020\f2\u0006\u0010E\u001a\u00020IH\u0016J\u0012\u0010J\u001a\u00020K2\b\b\u0002\u0010L\u001a\u00020KH\u0007J\b\u0010M\u001a\u00020)H\u0016J\b\u0010N\u001a\u00020GH\u0016J\u0010\u0010N\u001a\u00020G2\u0006\u0010\u001b\u001a\u00020\fH\u0016J\b\u0010O\u001a\u00020\u001dH\u0016J\u0010\u0010O\u001a\u00020\u001d2\u0006\u0010\u001b\u001a\u00020\fH\u0016J\b\u0010P\u001a\u00020\fH\u0016J\u000e\u0010Q\u001a\u00020\u00002\u0006\u0010R\u001a\u00020=J\u0016\u0010Q\u001a\u00020\u00002\u0006\u0010R\u001a\u00020=2\u0006\u0010\u001b\u001a\u00020\fJ \u0010Q\u001a\u00020\u00122\u0006\u0010R\u001a\u00020=2\u0006\u0010\u001b\u001a\u00020\f2\u0006\u0010S\u001a\u00020#H\u0002J\u0010\u0010T\u001a\u00020\u00122\u0006\u0010E\u001a\u00020GH\u0016J\u0018\u0010T\u001a\u00020\u00122\u0006\u0010E\u001a\u00020\u00002\u0006\u0010\u001b\u001a\u00020\fH\u0016J\b\u0010U\u001a\u00020\fH\u0016J\b\u0010V\u001a\u00020/H\u0016J\b\u0010W\u001a\u00020/H\u0016J\b\u0010X\u001a\u00020\fH\u0016J\b\u0010Y\u001a\u00020\fH\u0016J\b\u0010Z\u001a\u00020[H\u0016J\b\u0010\\\u001a\u00020[H\u0016J\u0010\u0010]\u001a\u00020\u001f2\u0006\u0010^\u001a\u00020_H\u0016J\u0018\u0010]\u001a\u00020\u001f2\u0006\u0010\u001b\u001a\u00020\f2\u0006\u0010^\u001a\u00020_H\u0016J\u0012\u0010`\u001a\u00020K2\b\b\u0002\u0010L\u001a\u00020KH\u0007J\b\u0010a\u001a\u00020\u001fH\u0016J\u0010\u0010a\u001a\u00020\u001f2\u0006\u0010\u001b\u001a\u00020\fH\u0016J\b\u0010b\u001a\u00020/H\u0016J\n\u0010c\u001a\u0004\u0018\u00010\u001fH\u0016J\b\u0010d\u001a\u00020\u001fH\u0016J\u0010\u0010d\u001a\u00020\u001f2\u0006\u0010e\u001a\u00020\fH\u0016J\u0010\u0010f\u001a\u00020#2\u0006\u0010\u001b\u001a\u00020\fH\u0016J\u0010\u0010g\u001a\u00020\u00122\u0006\u0010\u001b\u001a\u00020\fH\u0016J\u0010\u0010h\u001a\u00020/2\u0006\u0010i\u001a\u00020jH\u0016J\u0006\u0010k\u001a\u00020\u001dJ\u0006\u0010l\u001a\u00020\u001dJ\u0006\u0010m\u001a\u00020\u001dJ\r\u0010\r\u001a\u00020\fH\u0007¢\u0006\u0002\bnJ\u0010\u0010o\u001a\u00020\u00122\u0006\u0010\u001b\u001a\u00020\fH\u0016J\u0006\u0010p\u001a\u00020\u001dJ\u000e\u0010p\u001a\u00020\u001d2\u0006\u0010\u001b\u001a\u00020/J\b\u0010q\u001a\u00020rH\u0016J\b\u0010s\u001a\u00020\u001fH\u0016J\u0015\u0010t\u001a\u00020\n2\u0006\u0010u\u001a\u00020/H\u0000¢\u0006\u0002\bvJ\u0010\u0010w\u001a\u00020/2\u0006\u0010x\u001a\u00020FH\u0016J\u0010\u0010w\u001a\u00020\u00002\u0006\u0010x\u001a\u00020GH\u0016J \u0010w\u001a\u00020\u00002\u0006\u0010x\u001a\u00020G2\u0006\u0010\u001a\u001a\u00020/2\u0006\u0010\u001b\u001a\u00020/H\u0016J\u0018\u0010w\u001a\u00020\u00122\u0006\u0010x\u001a\u00020\u00002\u0006\u0010\u001b\u001a\u00020\fH\u0016J\u0010\u0010w\u001a\u00020\u00002\u0006\u0010y\u001a\u00020\u001dH\u0016J \u0010w\u001a\u00020\u00002\u0006\u0010y\u001a\u00020\u001d2\u0006\u0010\u001a\u001a\u00020/2\u0006\u0010\u001b\u001a\u00020/H\u0016J\u0018\u0010w\u001a\u00020\u00002\u0006\u0010x\u001a\u00020z2\u0006\u0010\u001b\u001a\u00020\fH\u0016J\u0010\u0010{\u001a\u00020\f2\u0006\u0010x\u001a\u00020zH\u0016J\u0010\u0010|\u001a\u00020\u00002\u0006\u00106\u001a\u00020/H\u0016J\u0010\u0010}\u001a\u00020\u00002\u0006\u0010~\u001a\u00020\fH\u0016J\u0010\u0010\u007f\u001a\u00020\u00002\u0006\u0010~\u001a\u00020\fH\u0016J\u0012\u0010\u0080\u0001\u001a\u00020\u00002\u0007\u0010\u0081\u0001\u001a\u00020/H\u0016J\u0012\u0010\u0082\u0001\u001a\u00020\u00002\u0007\u0010\u0081\u0001\u001a\u00020/H\u0016J\u0011\u0010\u0083\u0001\u001a\u00020\u00002\u0006\u0010~\u001a\u00020\fH\u0016J\u0011\u0010\u0084\u0001\u001a\u00020\u00002\u0006\u0010~\u001a\u00020\fH\u0016J\u0012\u0010\u0085\u0001\u001a\u00020\u00002\u0007\u0010\u0086\u0001\u001a\u00020/H\u0016J\u0012\u0010\u0087\u0001\u001a\u00020\u00002\u0007\u0010\u0086\u0001\u001a\u00020/H\u0016J\u001a\u0010\u0088\u0001\u001a\u00020\u00002\u0007\u0010\u0089\u0001\u001a\u00020\u001f2\u0006\u0010^\u001a\u00020_H\u0016J,\u0010\u0088\u0001\u001a\u00020\u00002\u0007\u0010\u0089\u0001\u001a\u00020\u001f2\u0007\u0010\u008a\u0001\u001a\u00020/2\u0007\u0010\u008b\u0001\u001a\u00020/2\u0006\u0010^\u001a\u00020_H\u0016J\u001b\u0010\u008c\u0001\u001a\u00020\u00002\u0006\u0010\u0018\u001a\u00020\u00192\b\b\u0002\u0010\u001b\u001a\u00020\fH\u0007J\u0012\u0010\u008d\u0001\u001a\u00020\u00002\u0007\u0010\u0089\u0001\u001a\u00020\u001fH\u0016J$\u0010\u008d\u0001\u001a\u00020\u00002\u0007\u0010\u0089\u0001\u001a\u00020\u001f2\u0007\u0010\u008a\u0001\u001a\u00020/2\u0007\u0010\u008b\u0001\u001a\u00020/H\u0016J\u0012\u0010\u008e\u0001\u001a\u00020\u00002\u0007\u0010\u008f\u0001\u001a\u00020/H\u0016R\u0014\u0010\u0006\u001a\u00020\u00008VX\u0096\u0004¢\u0006\u0006\u001a\u0004\b\u0007\u0010\bR\u0014\u0010\t\u001a\u0004\u0018\u00010\n8\u0000@\u0000X\u0081\u000e¢\u0006\u0002\n\u0000R&\u0010\r\u001a\u00020\f2\u0006\u0010\u000b\u001a\u00020\f8G@@X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\r\u0010\u000e\"\u0004\b\u000f\u0010\u0010¨\u0006\u0091\u0001"}, d2 = {"Lokio/Buffer;", "Lokio/BufferedSource;", "Lokio/BufferedSink;", "", "Ljava/nio/channels/ByteChannel;", "()V", "buffer", "getBuffer", "()Lokio/Buffer;", "head", "Lokio/Segment;", "<set-?>", "", "size", "()J", "setSize$okio", "(J)V", "clear", "", "clone", "close", "completeSegmentByteCount", "copy", "copyTo", "out", "Ljava/io/OutputStream;", "offset", "byteCount", "digest", "Lokio/ByteString;", "algorithm", "", "emit", "emitCompleteSegments", "equals", "", "other", "", "exhausted", "flush", "get", "", "pos", "getByte", "index", "-deprecated_getByte", "hashCode", "", "hmac", "key", "hmacSha1", "hmacSha256", "hmacSha512", "indexOf", "b", "fromIndex", "toIndex", "bytes", "indexOfElement", "targetBytes", "inputStream", "Ljava/io/InputStream;", "isOpen", "md5", "outputStream", "peek", "rangeEquals", "bytesOffset", "read", "sink", "Ljava/nio/ByteBuffer;", "", "readAll", "Lokio/Sink;", "readAndWriteUnsafe", "Lokio/Buffer$UnsafeCursor;", "unsafeCursor", "readByte", "readByteArray", "readByteString", "readDecimalLong", "readFrom", "input", "forever", "readFully", "readHexadecimalUnsignedLong", "readInt", "readIntLe", "readLong", "readLongLe", "readShort", "", "readShortLe", "readString", "charset", "Ljava/nio/charset/Charset;", "readUnsafe", "readUtf8", "readUtf8CodePoint", "readUtf8Line", "readUtf8LineStrict", "limit", "request", "require", "select", "options", "Lokio/Options;", "sha1", "sha256", "sha512", "-deprecated_size", "skip", "snapshot", "timeout", "Lokio/Timeout;", "toString", "writableSegment", "minimumCapacity", "writableSegment$okio", "write", "source", "byteString", "Lokio/Source;", "writeAll", "writeByte", "writeDecimalLong", "v", "writeHexadecimalUnsignedLong", "writeInt", "i", "writeIntLe", "writeLong", "writeLongLe", "writeShort", "s", "writeShortLe", "writeString", "string", "beginIndex", "endIndex", "writeTo", "writeUtf8", "writeUtf8CodePoint", "codePoint", "UnsafeCursor", "okio"}, k = 1, mv = {1, 1, 16})
/* loaded from: classes2.dex */
public final class Buffer implements BufferedSource, BufferedSink, Cloneable, ByteChannel {
    public Segment head;
    private long size;

    public static /* synthetic */ Buffer copyTo$default(Buffer buffer, Buffer buffer2, long j, int i, Object obj) {
        if ((i & 2) != 0) {
            j = 0;
        }
        return buffer.copyTo(buffer2, j);
    }

    public static /* synthetic */ Buffer copyTo$default(Buffer buffer, Buffer buffer2, long j, long j2, int i, Object obj) {
        if ((i & 2) != 0) {
            j = 0;
        }
        return buffer.copyTo(buffer2, j, j2);
    }

    @Override // okio.BufferedSource, okio.BufferedSink
    public Buffer buffer() {
        return this;
    }

    @Override
    public Timeout getTimeout() {
        return null;
    }

    @Override // okio.Source, java.io.Closeable, java.lang.AutoCloseable
    public void close() {
    }

    public final Buffer copyTo(OutputStream outputStream) throws IOException {
        return copyTo$default(this, outputStream, 0L, 0L, 6, (Object) null);
    }

    public final Buffer copyTo(OutputStream outputStream, long j) throws IOException {
        return copyTo$default(this, outputStream, j, 0L, 4, (Object) null);
    }

    @Override // okio.BufferedSink
    public Buffer emit() {
        return this;
    }

    @Override // okio.BufferedSink
    public Buffer emitCompleteSegments() {
        return this;
    }

    @Override // okio.BufferedSink, okio.Sink, java.io.Flushable
    public void flush() {
    }

    @Override // okio.BufferedSource, okio.BufferedSink
    public Buffer getBuffer() {
        return this;
    }

    @Override // java.nio.channels.Channel
    public boolean isOpen() {
        return true;
    }

    public final UnsafeCursor readAndWriteUnsafe() {
        return readAndWriteUnsafe$default(this, null, 1, null);
    }

    public final UnsafeCursor readUnsafe() {
        return readUnsafe$default(this, null, 1, null);
    }

    public final Buffer writeTo(OutputStream outputStream) throws IOException {
        return writeTo$default(this, outputStream, 0L, 2, null);
    }

    public final void setSize$okio(long j) {
        this.size = j;
    }

    public final long size() {
        return this.size;
    }

    @Override // okio.BufferedSink
    public OutputStream outputStream() {
        return new OutputStream() { // from class: okio.Buffer$outputStream$1
            @Override // java.io.OutputStream, java.io.Closeable, java.lang.AutoCloseable
            public void close() {
            }

            @Override // java.io.OutputStream, java.io.Flushable
            public void flush() {
            }

            @Override // java.io.OutputStream
            public void write(int i) {
                Buffer.this.writeByte(i);
            }

            @Override // java.io.OutputStream
            public void write(byte[] data, int i, int i2) {
                Intrinsics.checkParameterIsNotNull(data, "data");
                Buffer.this.write(data, i, i2);
            }

            public String toString() {
                return Buffer.this + ".outputStream()";
            }
        };
    }

    @Override
    public Buffer getBufferField() {
        return null;
    }

    @Override // okio.BufferedSource
    public boolean exhausted() {
        return this.size == 0;
    }

    @Override // okio.BufferedSource
    public void require(long j) throws EOFException {
        if (this.size < j) {
            throw new EOFException();
        }
    }

    @Override // okio.BufferedSource
    public boolean request(long j) {
        return this.size >= j;
    }

    @Override // okio.BufferedSource
    public BufferedSource peek() {
        return Okio.buffer(new PeekSource(this));
    }

    @Override // okio.BufferedSource
    public InputStream inputStream() {
        return new InputStream() { // from class: okio.Buffer$inputStream$1
            @Override // java.io.InputStream, java.io.Closeable, java.lang.AutoCloseable
            public void close() {
            }

            @Override // java.io.InputStream
            public int read() throws EOFException {
                if (Buffer.this.size() > 0) {
                    return Buffer.this.readByte() & UByte.MAX_VALUE;
                }
                return -1;
            }

            @Override // java.io.InputStream
            public int read(byte[] sink, int i, int i2) {
                Intrinsics.checkParameterIsNotNull(sink, "sink");
                return Buffer.this.read(sink, i, i2);
            }

            @Override // java.io.InputStream
            public int available() {
                return (int) Math.min(Buffer.this.size(), Integer.MAX_VALUE);
            }

            public String toString() {
                return Buffer.this + ".inputStream()";
            }
        };
    }

    public static /* synthetic */ Buffer copyTo$default(Buffer buffer, OutputStream outputStream, long j, long j2, int i, Object obj) throws IOException {
        if ((i & 2) != 0) {
            j = 0;
        }
        long j3 = j;
        if ((i & 4) != 0) {
            j2 = buffer.size - j3;
        }
        return buffer.copyTo(outputStream, j3, j2);
    }

    public final Buffer copyTo(OutputStream out, long j, long j2) throws IOException {
        int i = 0;
        Intrinsics.checkParameterIsNotNull(out, "out");
        Util.checkOffsetAndCount(this.size, j, j2);
        if (j2 == 0) {
            return this;
        }
        Segment segment = this.head;
        while (true) {
            if (segment == null) {
                Intrinsics.throwNpe();
            }
            if (j < segment.limit - segment.pos) {
                break;
            }
            j -= segment.limit - segment.pos;
            segment = segment.next;
        }
        while (j2 > 0) {
            if (segment == null) {
                Intrinsics.throwNpe();
            }
            int min = (int) Math.min(segment.limit - i, j2);
            out.write(segment.data, (int) (segment.pos + j), min);
            j2 -= min;
            segment = segment.next;
            j = 0;
        }
        return this;
    }

    public final Buffer copyTo(Buffer out, long j) {
        Intrinsics.checkParameterIsNotNull(out, "out");
        return copyTo(out, j, this.size - j);
    }

    public static /* synthetic */ Buffer writeTo$default(Buffer buffer, OutputStream outputStream, long j, int i, Object obj) throws IOException {
        if ((i & 2) != 0) {
            j = buffer.size;
        }
        return buffer.writeTo(outputStream, j);
    }

    public final Buffer writeTo(OutputStream out, long j) throws IOException {
        Intrinsics.checkParameterIsNotNull(out, "out");
        Util.checkOffsetAndCount(this.size, 0L, j);
        Segment segment = this.head;
        while (j > 0) {
            if (segment == null) {
                Intrinsics.throwNpe();
            }
            int min = (int) Math.min(j, segment.limit - segment.pos);
            out.write(segment.data, segment.pos, min);
            segment.pos += min;
            long j2 = min;
            this.size -= j2;
            j -= j2;
            if (segment.pos == segment.limit) {
                Segment pop = segment.pop();
                this.head = pop;
                SegmentPool.INSTANCE.recycle(segment);
                segment = pop;
            }
        }
        return this;
    }

    public final Buffer readFrom(InputStream input) throws IOException {
        Intrinsics.checkParameterIsNotNull(input, "input");
        readFrom(input, LongCompanionObject.MAX_VALUE, true);
        return this;
    }

    public final Buffer readFrom(InputStream input, long j) throws IOException {
        Intrinsics.checkParameterIsNotNull(input, "input");
        if (!(j >= 0)) {
            throw new IllegalArgumentException(("byteCount < 0: " + j).toString());
        }
        readFrom(input, j, false);
        return this;
    }

    private final void readFrom(InputStream inputStream, long j, boolean z) throws IOException {
        while (true) {
            if (j <= 0 && !z) {
                return;
            }
            Segment writableSegment$okio = writableSegment$okio(1);
            int read = inputStream.read(writableSegment$okio.data, writableSegment$okio.limit, (int) Math.min(j, 8192 - writableSegment$okio.limit));
            if (read == -1) {
                if (writableSegment$okio.pos == writableSegment$okio.limit) {
                    this.head = writableSegment$okio.pop();
                    SegmentPool.INSTANCE.recycle(writableSegment$okio);
                }
                if (!z) {
                    throw new EOFException();
                }
                return;
            }
            writableSegment$okio.limit += read;
            long j2 = read;
            this.size += j2;
            j -= j2;
        }
    }

    @Override // okio.BufferedSource
    public short readShortLe() throws EOFException {
        return Util.reverseBytes(readShort());
    }

    @Override // okio.BufferedSource
    public int readIntLe() throws EOFException {
        return Util.reverseBytes(readInt());
    }

    @Override // okio.BufferedSource
    public long readLongLe() throws EOFException {
        return Util.reverseBytes(readLong());
    }

    @Override // okio.BufferedSource
    public String readUtf8() throws EOFException {
        return readString(this.size, Charsets.UTF_8);
    }

    @Override // okio.BufferedSource
    public String readUtf8(long j) throws EOFException {
        return readString(j, Charsets.UTF_8);
    }

    @Override // okio.BufferedSource
    public String readString(Charset charset) throws EOFException {
        Intrinsics.checkParameterIsNotNull(charset, "charset");
        return readString(this.size, charset);
    }

    @Override // okio.BufferedSource
    public String readString(long j, Charset charset) throws EOFException {
        Intrinsics.checkParameterIsNotNull(charset, "charset");
        int i = (j > 0L ? 1 : (j == 0L ? 0 : -1));
        if (!(i >= 0 && j <= ((long) Integer.MAX_VALUE))) {
            throw new IllegalArgumentException(("byteCount: " + j).toString());
        } else if (this.size >= j) {
            if (i == 0) {
                return "";
            }
            Segment segment = this.head;
            if (segment == null) {
                Intrinsics.throwNpe();
            }
            if (segment.pos + j > segment.limit) {
                return new String(readByteArray(j), charset);
            }
            int i2 = (int) j;
            String str = new String(segment.data, segment.pos, i2, charset);
            segment.pos += i2;
            this.size -= j;
            if (segment.pos == segment.limit) {
                this.head = segment.pop();
                SegmentPool.INSTANCE.recycle(segment);
            }
            return str;
        } else {
            throw new EOFException();
        }
    }

    @Override // okio.BufferedSource
    public String readUtf8LineStrict() throws EOFException {
        return readUtf8LineStrict(LongCompanionObject.MAX_VALUE);
    }

    @Override // java.nio.channels.ReadableByteChannel
    public int read(ByteBuffer sink) throws IOException {
        Intrinsics.checkParameterIsNotNull(sink, "sink");
        Segment segment = this.head;
        if (segment != null) {
            int min = Math.min(sink.remaining(), segment.limit - segment.pos);
            sink.put(segment.data, segment.pos, min);
            segment.pos += min;
            this.size -= min;
            if (segment.pos == segment.limit) {
                this.head = segment.pop();
                SegmentPool.INSTANCE.recycle(segment);
            }
            return min;
        }
        return -1;
    }

    @Override // okio.BufferedSink
    public Buffer writeUtf8(String string) {
        Intrinsics.checkParameterIsNotNull(string, "string");
        return writeUtf8(string, 0, string.length());
    }

    @Override // okio.BufferedSink
    public Buffer writeString(String string, Charset charset) {
        Intrinsics.checkParameterIsNotNull(string, "string");
        Intrinsics.checkParameterIsNotNull(charset, "charset");
        return writeString(string, 0, string.length(), charset);
    }

    @Override // okio.BufferedSink
    public Buffer writeString(String string, int i, int i2, Charset charset) {
        Intrinsics.checkParameterIsNotNull(string, "string");
        Intrinsics.checkParameterIsNotNull(charset, "charset");
        if (!(i >= 0)) {
            throw new IllegalArgumentException(("beginIndex < 0: " + i).toString());
        }
        if (!(i2 >= i)) {
            throw new IllegalArgumentException(("endIndex < beginIndex: " + i2 + " < " + i).toString());
        }
        if (!(i2 <= string.length())) {
            throw new IllegalArgumentException(("endIndex > string.length: " + i2 + " > " + string.length()).toString());
        } else if (Intrinsics.areEqual(charset, Charsets.UTF_8)) {
            return writeUtf8(string, i, i2);
        } else {
            String substring = string.substring(i, i2);
            Intrinsics.checkExpressionValueIsNotNull(substring, "(this as java.lang.Strin…ing(startIndex, endIndex)");
            if (substring == null) {
                throw new TypeCastException("null cannot be cast to non-null type java.lang.String");
            }
            byte[] bytes = substring.getBytes(charset);
            Intrinsics.checkExpressionValueIsNotNull(bytes, "(this as java.lang.String).getBytes(charset)");
            return write(bytes, 0, bytes.length);
        }
    }

    @Override // java.nio.channels.WritableByteChannel
    public int write(ByteBuffer source) throws IOException {
        Intrinsics.checkParameterIsNotNull(source, "source");
        int remaining = source.remaining();
        int i = remaining;
        while (i > 0) {
            Segment writableSegment$okio = writableSegment$okio(1);
            int min = Math.min(i, 8192 - writableSegment$okio.limit);
            source.get(writableSegment$okio.data, writableSegment$okio.limit, min);
            i -= min;
            writableSegment$okio.limit += min;
        }
        this.size += remaining;
        return remaining;
    }

    @Override // okio.BufferedSink
    public Buffer writeShortLe(int i) {
        return writeShort((int) Util.reverseBytes((short) i));
    }

    @Override // okio.BufferedSink
    public Buffer writeIntLe(int i) {
        return writeInt(Util.reverseBytes(i));
    }

    @Override // okio.BufferedSink
    public Buffer writeLongLe(long j) {
        return writeLong(Util.reverseBytes(j));
    }

    @Override // okio.BufferedSource
    public long indexOf(byte b) {
        return indexOf(b, 0L, LongCompanionObject.MAX_VALUE);
    }

    @Override // okio.BufferedSource
    public long indexOf(byte b, long j) {
        return indexOf(b, j, LongCompanionObject.MAX_VALUE);
    }

    @Override // okio.BufferedSource
    public long indexOf(ByteString bytes) throws IOException {
        Intrinsics.checkParameterIsNotNull(bytes, "bytes");
        return indexOf(bytes, 0L);
    }

    @Override // okio.BufferedSource
    public long indexOfElement(ByteString targetBytes) {
        Intrinsics.checkParameterIsNotNull(targetBytes, "targetBytes");
        return indexOfElement(targetBytes, 0L);
    }

    @Override // okio.BufferedSource
    public boolean rangeEquals(long j, ByteString bytes) {
        Intrinsics.checkParameterIsNotNull(bytes, "bytes");
        return rangeEquals(j, bytes, 0, bytes.size());
    }

    @Override // okio.Source
    public Timeout timeout() {
        return Timeout.NONE;
    }

    public final ByteString md5() throws NoSuchAlgorithmException {
        return digest("MD5");
    }

    public final ByteString sha1() throws NoSuchAlgorithmException {
        return digest("SHA-1");
    }

    public final ByteString sha256() throws NoSuchAlgorithmException {
        return digest("SHA-256");
    }

    public final ByteString sha512() throws NoSuchAlgorithmException {
        return digest("SHA-512");
    }

    private final ByteString digest(String str) throws NoSuchAlgorithmException {
        MessageDigest messageDigest = MessageDigest.getInstance(str);
        Segment segment = this.head;
        if (segment != null) {
            messageDigest.update(segment.data, segment.pos, segment.limit - segment.pos);
            Segment segment2 = segment.next;
            if (segment2 == null) {
                Intrinsics.throwNpe();
            }
            while (segment2 != segment) {
                messageDigest.update(segment2.data, segment2.pos, segment2.limit - segment2.pos);
                segment2 = segment2.next;
                if (segment2 == null) {
                    Intrinsics.throwNpe();
                }
            }
        }
        byte[] digest = messageDigest.digest();
        Intrinsics.checkExpressionValueIsNotNull(digest, "messageDigest.digest()");
        return new ByteString(digest) {
            @Override
            public ByteString digest$okio(String algorithm) throws NoSuchAlgorithmException {
                return null;
            }
        };
    }

    public final ByteString hmacSha1(ByteString key) {
        Intrinsics.checkParameterIsNotNull(key, "key");
        return hmac("HmacSHA1", key);
    }

    public final ByteString hmacSha256(ByteString key) {
        Intrinsics.checkParameterIsNotNull(key, "key");
        return hmac("HmacSHA256", key);
    }

    public final ByteString hmacSha512(ByteString key) {
        Intrinsics.checkParameterIsNotNull(key, "key");
        return hmac("HmacSHA512", key);
    }

    private final ByteString hmac(String str, ByteString byteString) {
        try {
            Mac mac = Mac.getInstance(str);
            mac.init(new SecretKeySpec(byteString.internalArray$okio(), str));
            Segment segment = this.head;
            if (segment != null) {
                mac.update(segment.data, segment.pos, segment.limit - segment.pos);
                Segment segment2 = segment.next;
                if (segment2 == null) {
                    Intrinsics.throwNpe();
                }
                while (segment2 != segment) {
                    mac.update(segment2.data, segment2.pos, segment2.limit - segment2.pos);
                    segment2 = segment2.next;
                    if (segment2 == null) {
                        Intrinsics.throwNpe();
                    }
                }
            }
            byte[] doFinal = mac.doFinal();
            Intrinsics.checkExpressionValueIsNotNull(doFinal, "mac.doFinal()");
            return new ByteString(doFinal) {
                @Override
                public ByteString digest$okio(String algorithm) throws NoSuchAlgorithmException {
                    return null;
                }
            };
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public String toString() {
        return snapshot().toString();
    }

    public Buffer clone() {
        return copy();
    }

    public static /* synthetic */ UnsafeCursor readUnsafe$default(Buffer buffer, UnsafeCursor unsafeCursor, int i, Object obj) {
        if ((i & 1) != 0) {
            unsafeCursor = new UnsafeCursor();
        }
        return buffer.readUnsafe(unsafeCursor);
    }

    public final UnsafeCursor readUnsafe(UnsafeCursor unsafeCursor) {
        Intrinsics.checkParameterIsNotNull(unsafeCursor, "unsafeCursor");
        if (!(unsafeCursor.buffer == null)) {
            throw new IllegalStateException("already attached to a buffer".toString());
        }
        unsafeCursor.buffer = this;
        unsafeCursor.readWrite = false;
        return unsafeCursor;
    }

    public static /* synthetic */ UnsafeCursor readAndWriteUnsafe$default(Buffer buffer, UnsafeCursor unsafeCursor, int i, Object obj) {
        if ((i & 1) != 0) {
            unsafeCursor = new UnsafeCursor();
        }
        return buffer.readAndWriteUnsafe(unsafeCursor);
    }

    public final UnsafeCursor readAndWriteUnsafe(UnsafeCursor unsafeCursor) {
        Intrinsics.checkParameterIsNotNull(unsafeCursor, "unsafeCursor");
        if (!(unsafeCursor.buffer == null)) {
            throw new IllegalStateException("already attached to a buffer".toString());
        }
        unsafeCursor.buffer = this;
        unsafeCursor.readWrite = true;
        return unsafeCursor;
    }

    @Deprecated(level = DeprecationLevel.ERROR, message = "moved to operator function", replaceWith = @ReplaceWith(expression = "this[index]", imports = {}))
    /* renamed from: -deprecated_getByte  reason: not valid java name */
    public final byte m1160deprecated_getByte(long j) {
        return getByte(j);
    }

    @Deprecated(level = DeprecationLevel.ERROR, message = "moved to val", replaceWith = @ReplaceWith(expression = "size", imports = {}))
    /* renamed from: -deprecated_size  reason: not valid java name */
    public final long m1161deprecated_size() {
        return this.size;
    }

    /* compiled from: Buffer.kt */
    @Metadata(bv = {1, 0, 3}, d1 = {"\u0000:\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0012\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0007\u0018\u00002\u00020\u0001B\u0005¢\u0006\u0002\u0010\u0002J\b\u0010\u0010\u001a\u00020\u0011H\u0016J\u000e\u0010\u0012\u001a\u00020\n2\u0006\u0010\u0013\u001a\u00020\bJ\u0006\u0010\u0014\u001a\u00020\bJ\u000e\u0010\u0015\u001a\u00020\n2\u0006\u0010\u0016\u001a\u00020\nJ\u000e\u0010\u0017\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\nR\u0014\u0010\u0003\u001a\u0004\u0018\u00010\u00048\u0006@\u0006X\u0087\u000e¢\u0006\u0002\n\u0000R\u0014\u0010\u0005\u001a\u0004\u0018\u00010\u00068\u0006@\u0006X\u0087\u000e¢\u0006\u0002\n\u0000R\u0012\u0010\u0007\u001a\u00020\b8\u0006@\u0006X\u0087\u000e¢\u0006\u0002\n\u0000R\u0012\u0010\t\u001a\u00020\n8\u0006@\u0006X\u0087\u000e¢\u0006\u0002\n\u0000R\u0012\u0010\u000b\u001a\u00020\f8\u0006@\u0006X\u0087\u000e¢\u0006\u0002\n\u0000R\u0010\u0010\r\u001a\u0004\u0018\u00010\u000eX\u0082\u000e¢\u0006\u0002\n\u0000R\u0012\u0010\u000f\u001a\u00020\b8\u0006@\u0006X\u0087\u000e¢\u0006\u0002\n\u0000¨\u0006\u0018"}, d2 = {"Lokio/Buffer$UnsafeCursor;", "Ljava/io/Closeable;", "()V", "buffer", "Lokio/Buffer;", WeatherEntry.COLUMN_NAME_WEATHER_DATA, "", "end", "", "offset", "", "readWrite", "", "segment", "Lokio/Segment;", "start", "close", "", "expandBuffer", "minByteCount", "next", "resizeBuffer", "newSize", "seek", "okio"}, k = 1, mv = {1, 1, 16})
    /* loaded from: classes2.dex */
    public static final class UnsafeCursor implements Closeable {
        public Buffer buffer;
        public byte[] data;
        public boolean readWrite;
        private Segment segment;
        public long offset = -1;
        public int start = -1;
        public int end = -1;

        public final int next() {
            long j = this.offset;
            Buffer buffer = this.buffer;
            if (buffer == null) {
                Intrinsics.throwNpe();
            }
            if (!(j != buffer.size())) {
                throw new IllegalStateException("no more bytes".toString());
            }
            long j2 = this.offset;
            return seek(j2 == -1 ? 0L : j2 + (this.end - this.start));
        }

        public final int seek(long j) {
            Buffer buffer = this.buffer;
            if (buffer == null) {
                throw new IllegalStateException("not attached to a buffer".toString());
            }
            if (j < -1 || j > buffer.size()) {
                StringCompanionObject stringCompanionObject = StringCompanionObject.INSTANCE;
                String format = String.format("offset=%s > size=%s", Arrays.copyOf(new Object[]{Long.valueOf(j), Long.valueOf(buffer.size())}, 2));
                Intrinsics.checkExpressionValueIsNotNull(format, "java.lang.String.format(format, *args)");
                throw new ArrayIndexOutOfBoundsException(format);
            } else if (j == -1 || j == buffer.size()) {
                this.segment = null;
                this.offset = j;
                this.data = null;
                this.start = -1;
                this.end = -1;
                return -1;
            } else {
                long j2 = 0;
                long size = buffer.size();
                Segment segment = buffer.head;
                Segment segment2 = buffer.head;
                Segment segment3 = this.segment;
                if (segment3 != null) {
                    long j3 = this.offset;
                    int i = this.start;
                    if (segment3 == null) {
                        Intrinsics.throwNpe();
                    }
                    long j4 = j3 - (i - segment3.pos);
                    if (j4 > j) {
                        segment2 = this.segment;
                        size = j4;
                    } else {
                        segment = this.segment;
                        j2 = j4;
                    }
                }
                if (size - j > j - j2) {
                    while (true) {
                        if (segment == null) {
                            Intrinsics.throwNpe();
                        }
                        if (j < (segment.limit - segment.pos) + j2) {
                            break;
                        }
                        j2 += segment.limit - segment.pos;
                        segment = segment.next;
                    }
                } else {
                    while (size > j) {
                        if (segment2 == null) {
                            Intrinsics.throwNpe();
                        }
                        segment2 = segment2.prev;
                        if (segment2 == null) {
                            Intrinsics.throwNpe();
                        }
                        size -= segment2.limit - segment2.pos;
                    }
                    j2 = size;
                    segment = segment2;
                }
                if (this.readWrite) {
                    if (segment == null) {
                        Intrinsics.throwNpe();
                    }
                    if (segment.shared) {
                        Segment unsharedCopy = segment.unsharedCopy();
                        if (buffer.head == segment) {
                            buffer.head = unsharedCopy;
                        }
                        segment = segment.push(unsharedCopy);
                        Segment segment4 = segment.prev;
                        if (segment4 == null) {
                            Intrinsics.throwNpe();
                        }
                        segment4.pop();
                    }
                }
                this.segment = segment;
                this.offset = j;
                if (segment == null) {
                    Intrinsics.throwNpe();
                }
                this.data = segment.data;
                this.start = segment.pos + ((int) (j - j2));
                int i2 = segment.limit;
                this.end = i2;
                return i2 - this.start;
            }
        }

        public final long resizeBuffer(long j) {
            Buffer buffer = this.buffer;
            if (buffer == null) {
                throw new IllegalStateException("not attached to a buffer".toString());
            }
            if (!this.readWrite) {
                throw new IllegalStateException("resizeBuffer() only permitted for read/write buffers".toString());
            }
            long size = buffer.size();
            int i = (j > size ? 1 : (j == size ? 0 : -1));
            int i2 = 1;
            if (i <= 0) {
                if (!(j >= 0)) {
                    throw new IllegalArgumentException(("newSize < 0: " + j).toString());
                }
                long j2 = size - j;
                while (true) {
                    if (j2 > 0) {
                        Segment segment = buffer.head;
                        if (segment == null) {
                            Intrinsics.throwNpe();
                        }
                        Segment segment2 = segment.prev;
                        if (segment2 == null) {
                            Intrinsics.throwNpe();
                        }
                        long j3 = segment2.limit - segment2.pos;
                        if (j3 <= j2) {
                            buffer.head = segment2.pop();
                            SegmentPool.INSTANCE.recycle(segment2);
                            j2 -= j3;
                        } else {
                            segment2.limit -= (int) j2;
                            break;
                        }
                    } else {
                        break;
                    }
                }
                this.segment = null;
                this.offset = j;
                this.data = null;
                this.start = -1;
                this.end = -1;
            } else if (i > 0) {
                long j4 = j - size;
                boolean z = true;
                while (j4 > 0) {
                    Segment writableSegment$okio = buffer.writableSegment$okio(i2);
                    int min = (int) Math.min(j4, 8192 - writableSegment$okio.limit);
                    writableSegment$okio.limit += min;
                    j4 -= min;
                    if (z) {
                        this.segment = writableSegment$okio;
                        this.offset = size;
                        this.data = writableSegment$okio.data;
                        this.start = writableSegment$okio.limit - min;
                        this.end = writableSegment$okio.limit;
                        z = false;
                    }
                    i2 = 1;
                }
            }
            buffer.setSize$okio(j);
            return size;
        }

        public final long expandBuffer(int i) {
            if (!(i > 0)) {
                throw new IllegalArgumentException(("minByteCount <= 0: " + i).toString());
            }
            if (!(i <= 8192)) {
                throw new IllegalArgumentException(("minByteCount > Segment.SIZE: " + i).toString());
            }
            Buffer buffer = this.buffer;
            if (buffer == null) {
                throw new IllegalStateException("not attached to a buffer".toString());
            }
            if (!this.readWrite) {
                throw new IllegalStateException("expandBuffer() only permitted for read/write buffers".toString());
            }
            long size = buffer.size();
            Segment writableSegment$okio = buffer.writableSegment$okio(i);
            int i2 = 8192 - writableSegment$okio.limit;
            writableSegment$okio.limit = 8192;
            long j = i2;
            buffer.setSize$okio(size + j);
            this.segment = writableSegment$okio;
            this.offset = size;
            this.data = writableSegment$okio.data;
            this.start = 8192 - i2;
            this.end = 8192;
            return j;
        }

        @Override // java.io.Closeable, java.lang.AutoCloseable
        public void close() {
            if (!(this.buffer != null)) {
                throw new IllegalStateException("not attached to a buffer".toString());
            }
            this.buffer = null;
            this.segment = null;
            this.offset = -1L;
            this.data = null;
            this.start = -1;
            this.end = -1;
        }
    }

    public final Buffer copyTo(Buffer out, long j, long j2) {
        Intrinsics.checkParameterIsNotNull(out, "out");
        Util.checkOffsetAndCount(size(), j, j2);
        if (j2 != 0) {
            out.setSize$okio(out.size() + j2);
            Segment segment = this.head;
            while (true) {
                if (segment == null) {
                    Intrinsics.throwNpe();
                }
                if (j < segment.limit - segment.pos) {
                    break;
                }
                j -= segment.limit - segment.pos;
                segment = segment.next;
            }
            while (j2 > 0) {
                if (segment == null) {
                    Intrinsics.throwNpe();
                }
                Segment sharedCopy = segment.sharedCopy();
                sharedCopy.pos += (int) j;
                sharedCopy.limit = Math.min(sharedCopy.pos + ((int) j2), sharedCopy.limit);
                Segment segment2 = out.head;
                if (segment2 == null) {
                    sharedCopy.prev = sharedCopy;
                    sharedCopy.next = sharedCopy.prev;
                    out.head = sharedCopy.next;
                } else {
                    if (segment2 == null) {
                        Intrinsics.throwNpe();
                    }
                    Segment segment3 = segment2.prev;
                    if (segment3 == null) {
                        Intrinsics.throwNpe();
                    }
                    segment3.push(sharedCopy);
                }
                j2 -= sharedCopy.limit - sharedCopy.pos;
                segment = segment.next;
                j = 0;
            }
        }
        return this;
    }

    public final long completeSegmentByteCount() {
        long size = size();
        if (size == 0) {
            return 0L;
        }
        Segment segment = this.head;
        if (segment == null) {
            Intrinsics.throwNpe();
        }
        Segment segment2 = segment.prev;
        if (segment2 == null) {
            Intrinsics.throwNpe();
        }
        if (segment2.limit < 8192 && segment2.owner) {
            size -= segment2.limit - segment2.pos;
        }
        return size;
    }

    @Override // okio.BufferedSource
    public byte readByte() throws EOFException {
        if (size() == 0) {
            throw new EOFException();
        }
        Segment segment = this.head;
        if (segment == null) {
            Intrinsics.throwNpe();
        }
        int i = segment.pos;
        int i2 = segment.limit;
        int i3 = i + 1;
        byte b = segment.data[i];
        setSize$okio(size() - 1);
        if (i3 == i2) {
            this.head = segment.pop();
            SegmentPool.INSTANCE.recycle(segment);
        } else {
            segment.pos = i3;
        }
        return b;
    }

    public final byte getByte(long j) {
        Util.checkOffsetAndCount(size(), j, 1L);
        Segment segment = this.head;
        if (segment == null) {
            Segment segment2 = null;
            Intrinsics.throwNpe();
            return segment2.data[(int) ((segment2.pos + j) - (-1))];
        } else if (size() - j < j) {
            long size = size();
            while (size > j) {
                segment = segment.prev;
                if (segment == null) {
                    Intrinsics.throwNpe();
                }
                size -= segment.limit - segment.pos;
            }
            if (segment == null) {
                Intrinsics.throwNpe();
            }
            return segment.data[(int) ((segment.pos + j) - size)];
        } else {
            long j2 = 0;
            while (true) {
                long j3 = (segment.limit - segment.pos) + j2;
                if (j3 > j) {
                    break;
                }
                segment = segment.next;
                if (segment == null) {
                    Intrinsics.throwNpe();
                }
                j2 = j3;
            }
            if (segment == null) {
                Intrinsics.throwNpe();
            }
            return segment.data[(int) ((segment.pos + j) - j2)];
        }
    }

    @Override // okio.BufferedSource
    public short readShort() throws EOFException {
        if (size() < 2) {
            throw new EOFException();
        }
        Segment segment = this.head;
        if (segment == null) {
            Intrinsics.throwNpe();
        }
        int i = segment.pos;
        int i2 = segment.limit;
        if (i2 - i < 2) {
            return (short) (((readByte() & UByte.MAX_VALUE) << 8) | (readByte() & UByte.MAX_VALUE));
        }
        byte[] bArr = segment.data;
        int i3 = i + 1;
        int i4 = i3 + 1;
        int i5 = ((bArr[i] & UByte.MAX_VALUE) << 8) | (bArr[i3] & UByte.MAX_VALUE);
        setSize$okio(size() - 2);
        if (i4 == i2) {
            this.head = segment.pop();
            SegmentPool.INSTANCE.recycle(segment);
        } else {
            segment.pos = i4;
        }
        return (short) i5;
    }

    @Override // okio.BufferedSource
    public int readInt() throws EOFException {
        if (size() < 4) {
            throw new EOFException();
        }
        Segment segment = this.head;
        if (segment == null) {
            Intrinsics.throwNpe();
        }
        int i = segment.pos;
        int i2 = segment.limit;
        if (i2 - i < 4) {
            return ((readByte() & UByte.MAX_VALUE) << 24) | ((readByte() & UByte.MAX_VALUE) << 16) | ((readByte() & UByte.MAX_VALUE) << 8) | (readByte() & UByte.MAX_VALUE);
        }
        byte[] bArr = segment.data;
        int i3 = i + 1;
        int i4 = i3 + 1;
        int i5 = ((bArr[i] & UByte.MAX_VALUE) << 24) | ((bArr[i3] & UByte.MAX_VALUE) << 16);
        int i6 = i4 + 1;
        int i7 = i5 | ((bArr[i4] & UByte.MAX_VALUE) << 8);
        int i8 = i6 + 1;
        int i9 = i7 | (bArr[i6] & UByte.MAX_VALUE);
        setSize$okio(size() - 4);
        if (i8 == i2) {
            this.head = segment.pop();
            SegmentPool.INSTANCE.recycle(segment);
        } else {
            segment.pos = i8;
        }
        return i9;
    }

    @Override // okio.BufferedSource
    public long readLong() throws EOFException {
        if (size() < 8) {
            throw new EOFException();
        }
        Segment segment = this.head;
        if (segment == null) {
            Intrinsics.throwNpe();
        }
        int i = segment.pos;
        int i2 = segment.limit;
        if (i2 - i < 8) {
            return ((readInt() & 4294967295L) << 32) | (4294967295L & readInt());
        }
        byte[] bArr = segment.data;
        int i3 = i + 1;
        int i4 = i3 + 1;
        int i5 = i4 + 1;
        int i6 = i5 + 1;
        int i7 = i6 + 1;
        int i8 = i7 + 1;
        long j = ((bArr[i] & 255) << 56) | ((bArr[i3] & 255) << 48) | ((bArr[i4] & 255) << 40) | ((bArr[i5] & 255) << 32) | ((bArr[i6] & 255) << 24) | ((bArr[i7] & 255) << 16);
        int i9 = i8 + 1;
        int i10 = i9 + 1;
        long j2 = j | ((bArr[i8] & 255) << 8) | (bArr[i9] & 255);
        setSize$okio(size() - 8);
        if (i10 == i2) {
            this.head = segment.pop();
            SegmentPool.INSTANCE.recycle(segment);
        } else {
            segment.pos = i10;
        }
        return j2;
    }

    /* JADX WARN: Removed duplicated region for block: B:39:0x00b3  */
    /* JADX WARN: Removed duplicated region for block: B:40:0x00c1  */
    /* JADX WARN: Removed duplicated region for block: B:42:0x00c7  */
    /* JADX WARN: Removed duplicated region for block: B:51:0x00cb A[EDGE_INSN: B:51:0x00cb->B:44:0x00cb ?: BREAK  , SYNTHETIC] */
    @Override // okio.BufferedSource
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    public long readDecimalLong() throws EOFException {
        /*
            Method dump skipped, instructions count: 225
            To view this dump change 'Code comments level' option to 'DEBUG'
        */
        throw new UnsupportedOperationException("Method not decompiled: okio.Buffer.readDecimalLong():long");
    }

    /* JADX WARN: Removed duplicated region for block: B:35:0x00a2  */
    /* JADX WARN: Removed duplicated region for block: B:36:0x00ae  */
    /* JADX WARN: Removed duplicated region for block: B:38:0x00b2  */
    /* JADX WARN: Removed duplicated region for block: B:45:0x00b6 A[EDGE_INSN: B:45:0x00b6->B:40:0x00b6 ?: BREAK  , SYNTHETIC] */
    @Override // okio.BufferedSource
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    public long readHexadecimalUnsignedLong() throws EOFException {
        /*
            r14 = this;
            long r0 = r14.size()
            r2 = 0
            int r0 = (r0 > r2 ? 1 : (r0 == r2 ? 0 : -1))
            if (r0 == 0) goto Lc0
            r0 = 0
            r1 = r0
            r4 = r2
        Ld:
            okio.Segment r6 = r14.head
            if (r6 != 0) goto L14
            kotlin.jvm.internal.Intrinsics.throwNpe()
        L14:
            byte[] r7 = r6.data
            int r8 = r6.pos
            int r9 = r6.limit
        L1a:
            if (r8 >= r9) goto La0
            r10 = r7[r8]
            r11 = 48
            byte r11 = (byte) r11
            if (r10 < r11) goto L2b
            r12 = 57
            byte r12 = (byte) r12
            if (r10 > r12) goto L2b
            int r11 = r10 - r11
            goto L45
        L2b:
            r11 = 97
            byte r11 = (byte) r11
            if (r10 < r11) goto L3a
            r12 = 102(0x66, float:1.43E-43)
            byte r12 = (byte) r12
            if (r10 > r12) goto L3a
        L35:
            int r11 = r10 - r11
            int r11 = r11 + 10
            goto L45
        L3a:
            r11 = 65
            byte r11 = (byte) r11
            if (r10 < r11) goto L7f
            r12 = 70
            byte r12 = (byte) r12
            if (r10 > r12) goto L7f
            goto L35
        L45:
            r12 = -1152921504606846976(0xf000000000000000, double:-3.105036184601418E231)
            long r12 = r12 & r4
            int r12 = (r12 > r2 ? 1 : (r12 == r2 ? 0 : -1))
            if (r12 != 0) goto L55
            r10 = 4
            long r4 = r4 << r10
            long r10 = (long) r11
            long r4 = r4 | r10
            int r8 = r8 + 1
            int r0 = r0 + 1
            goto L1a
        L55:
            okio.Buffer r0 = new okio.Buffer
            r0.<init>()
            okio.Buffer r0 = r0.writeHexadecimalUnsignedLong(r4)
            okio.Buffer r0 = r0.writeByte(r10)
            java.lang.NumberFormatException r1 = new java.lang.NumberFormatException
            java.lang.StringBuilder r2 = new java.lang.StringBuilder
            r2.<init>()
            java.lang.String r3 = "Number too large: "
            r2.append(r3)
            java.lang.String r0 = r0.readUtf8()
            r2.append(r0)
            java.lang.String r0 = r2.toString()
            r1.<init>(r0)
            java.lang.Throwable r1 = (java.lang.Throwable) r1
            throw r1
        L7f:
            if (r0 == 0) goto L83
            r1 = 1
            goto La0
        L83:
            java.lang.NumberFormatException r0 = new java.lang.NumberFormatException
            java.lang.StringBuilder r1 = new java.lang.StringBuilder
            r1.<init>()
            java.lang.String r2 = "Expected leading [0-9a-fA-F] character but was 0x"
            r1.append(r2)
            java.lang.String r2 = okio.Util.toHexString(r10)
            r1.append(r2)
            java.lang.String r1 = r1.toString()
            r0.<init>(r1)
            java.lang.Throwable r0 = (java.lang.Throwable) r0
            throw r0
        La0:
            if (r8 != r9) goto Lae
            okio.Segment r7 = r6.pop()
            r14.head = r7
            okio.SegmentPool r7 = okio.SegmentPool.INSTANCE
            r7.recycle(r6)
            goto Lb0
        Lae:
            r6.pos = r8
        Lb0:
            if (r1 != 0) goto Lb6
            okio.Segment r6 = r14.head
            if (r6 != 0) goto Ld
        Lb6:
            long r1 = r14.size()
            long r6 = (long) r0
            long r1 = r1 - r6
            r14.setSize$okio(r1)
            return r4
        Lc0:
            java.io.EOFException r0 = new java.io.EOFException
            r0.<init>()
            java.lang.Throwable r0 = (java.lang.Throwable) r0
            throw r0
        */
        throw new UnsupportedOperationException("Method not decompiled: okio.Buffer.readHexadecimalUnsignedLong():long");
    }

    @Override // okio.BufferedSource
    public ByteString readByteString() throws EOFException {
        return readByteString(size());
    }

    @Override // okio.BufferedSource
    public ByteString readByteString(long j) throws EOFException {
        if (!(j >= 0 && j <= ((long) Integer.MAX_VALUE))) {
            throw new IllegalArgumentException(("byteCount: " + j).toString());
        } else if (size() >= j) {
            if (j >= 4096) {
                ByteString snapshot = snapshot((int) j);
                skip(j);
                return snapshot;
            }
            return new ByteString(readByteArray(j)) {
                @Override
                public ByteString digest$okio(String algorithm) throws NoSuchAlgorithmException {
                    return null;
                }
            };
        } else {
            throw new EOFException();
        }
    }

    @Override // okio.BufferedSource
    public int select(Options options) throws EOFException {
        Intrinsics.checkParameterIsNotNull(options, "options");
        int selectPrefix$default = BufferKt.selectPrefix$default(this, options, false, 2, null);
        if (selectPrefix$default == -1) {
            return -1;
        }
        skip(options.getByteStrings()[selectPrefix$default].size());
        return selectPrefix$default;
    }

    @Override // okio.BufferedSource
    public void readFully(Buffer sink, long j) throws EOFException {
        Intrinsics.checkParameterIsNotNull(sink, "sink");
        if (size() < j) {
            sink.write(this, size());
            throw new EOFException();
        } else {
            sink.write(this, j);
        }
    }

    @Override // okio.BufferedSource
    public long readAll(Sink sink) throws Throwable {
        Intrinsics.checkParameterIsNotNull(sink, "sink");
        long size = size();
        if (size > 0) {
            sink.write(this, size);
        }
        return size;
    }

    @Override // okio.BufferedSource
    public String readUtf8Line() throws EOFException {
        long indexOf = indexOf((byte) 10);
        if (indexOf != -1) {
            return BufferKt.readUtf8Line(this, indexOf);
        }
        if (size() != 0) {
            return readUtf8(size());
        }
        return null;
    }

    @Override // okio.BufferedSource
    public String readUtf8LineStrict(long j) throws EOFException {
        if (!(j >= 0)) {
            throw new IllegalArgumentException(("limit < 0: " + j).toString());
        }
        long j2 = LongCompanionObject.MAX_VALUE;
        if (j != LongCompanionObject.MAX_VALUE) {
            j2 = j + 1;
        }
        byte b = (byte) 10;
        long indexOf = indexOf(b, 0L, j2);
        if (indexOf != -1) {
            return BufferKt.readUtf8Line(this, indexOf);
        }
        if (j2 < size() && getByte(j2 - 1) == ((byte) 13) && getByte(j2) == b) {
            return BufferKt.readUtf8Line(this, j2);
        }
        Buffer buffer = new Buffer();
        copyTo(buffer, 0L, Math.min(32, size()));
        throw new EOFException("\\n not found: limit=" + Math.min(size(), j) + " content=" + buffer.readByteString().hex() + Typography.ellipsis);
    }

    @Override // okio.BufferedSource
    public int readUtf8CodePoint() throws EOFException {
        int i;
        int i2;
        int i3;
        if (size() == 0) {
            throw new EOFException();
        }
        byte b = getByte(0L);
        if ((b & ByteCompanionObject.MIN_VALUE) == 0) {
            i = b & ByteCompanionObject.MAX_VALUE;
            i3 = 0;
            i2 = 1;
        } else if ((b & 224) == 192) {
            i = b & 31;
            i2 = 2;
            i3 = 128;
        } else if ((b & 240) == 224) {
            i = b & 15;
            i2 = 3;
            i3 = 2048;
        } else if ((b & 248) != 240) {
            skip(1L);
            return Utf8.REPLACEMENT_CODE_POINT;
        } else {
            i = b & 7;
            i2 = 4;
            i3 = 65536;
        }
        long j = i2;
        if (size() < j) {
            throw new EOFException("size < " + i2 + ": " + size() + " (to read code point prefixed 0x" + Util.toHexString(b) + ')');
        }
        for (int i4 = 1; i4 < i2; i4++) {
            long j2 = i4;
            byte b2 = getByte(j2);
            if ((b2 & 192) != 128) {
                skip(j2);
                return Utf8.REPLACEMENT_CODE_POINT;
            }
            i = (i << 6) | (b2 & Utf8.REPLACEMENT_BYTE);
        }
        skip(j);
        return i > 1114111 ? Utf8.REPLACEMENT_CODE_POINT : ((55296 <= i && 57343 >= i) || i < i3) ? Utf8.REPLACEMENT_CODE_POINT : i;
    }

    @Override // okio.BufferedSource
    public byte[] readByteArray() throws EOFException {
        return readByteArray(size());
    }

    @Override // okio.BufferedSource
    public byte[] readByteArray(long j) throws EOFException {
        if (!(j >= 0 && j <= ((long) Integer.MAX_VALUE))) {
            throw new IllegalArgumentException(("byteCount: " + j).toString());
        } else if (size() < j) {
            throw new EOFException();
        } else {
            byte[] bArr = new byte[(int) j];
            readFully(bArr);
            return bArr;
        }
    }

    @Override // okio.BufferedSource
    public int read(byte[] sink) {
        Intrinsics.checkParameterIsNotNull(sink, "sink");
        return read(sink, 0, sink.length);
    }

    @Override // okio.BufferedSource
    public void readFully(byte[] sink) throws EOFException {
        Intrinsics.checkParameterIsNotNull(sink, "sink");
        int i = 0;
        while (i < sink.length) {
            int read = read(sink, i, sink.length - i);
            if (read == -1) {
                throw new EOFException();
            }
            i += read;
        }
    }

    @Override // okio.BufferedSource
    public int read(byte[] sink, int i, int i2) {
        Intrinsics.checkParameterIsNotNull(sink, "sink");
        Util.checkOffsetAndCount(sink.length, i, i2);
        Segment segment = this.head;
        if (segment != null) {
            int min = Math.min(i2, segment.limit - segment.pos);
            ArraysKt.copyInto(segment.data, sink, i, segment.pos, segment.pos + min);
            segment.pos += min;
            setSize$okio(size() - min);
            if (segment.pos == segment.limit) {
                this.head = segment.pop();
                SegmentPool.INSTANCE.recycle(segment);
                return min;
            }
            return min;
        }
        return -1;
    }

    public final void clear() throws EOFException {
        skip(size());
    }

    @Override // okio.BufferedSource
    public void skip(long j) throws EOFException {
        while (j > 0) {
            Segment segment = this.head;
            if (segment == null) {
                throw new EOFException();
            }
            int min = (int) Math.min(j, segment.limit - segment.pos);
            long j2 = min;
            setSize$okio(size() - j2);
            j -= j2;
            segment.pos += min;
            if (segment.pos == segment.limit) {
                this.head = segment.pop();
                SegmentPool.INSTANCE.recycle(segment);
            }
        }
    }

    @Override // okio.BufferedSink
    public Buffer write(ByteString byteString) {
        Intrinsics.checkParameterIsNotNull(byteString, "byteString");
        byteString.write$okio(this, 0, byteString.size());
        return this;
    }

    @Override // okio.BufferedSink
    public Buffer write(ByteString byteString, int i, int i2) {
        Intrinsics.checkParameterIsNotNull(byteString, "byteString");
        byteString.write$okio(this, i, i2);
        return this;
    }

    @Override // okio.BufferedSink
    public Buffer writeUtf8(String string, int i, int i2) {
        int i3 = 0;
        Intrinsics.checkParameterIsNotNull(string, "string");
        if (!(i >= 0)) {
            throw new IllegalArgumentException(("beginIndex < 0: " + i).toString());
        }
        if (!(i2 >= i)) {
            throw new IllegalArgumentException(("endIndex < beginIndex: " + i2 + " < " + i).toString());
        }
        if (!(i2 <= string.length())) {
            throw new IllegalArgumentException(("endIndex > string.length: " + i2 + " > " + string.length()).toString());
        }
        while (i < i2) {
            char charAt = string.charAt(i);
            if (charAt < 128) {
                Segment writableSegment$okio = writableSegment$okio(1);
                byte[] bArr = writableSegment$okio.data;
                int i4 = writableSegment$okio.limit - i;
                int min = Math.min(i2, 8192 - i4);
                i3 = i + 1;
                bArr[i + i4] = (byte) charAt;
                while (i3 < min) {
                    char charAt2 = string.charAt(i3);
                    if (charAt2 >= 128) {
                        break;
                    }
                    bArr[i3 + i4] = (byte) charAt2;
                    i3++;
                }
                int i5 = (i4 + i3) - writableSegment$okio.limit;
                writableSegment$okio.limit += i5;
                setSize$okio(size() + i5);
            } else {
                if (charAt < 2048) {
                    Segment writableSegment$okio2 = writableSegment$okio(2);
                    writableSegment$okio2.data[writableSegment$okio2.limit] = (byte) ((charAt >> 6) | 192);
                    writableSegment$okio2.data[writableSegment$okio2.limit + 1] = (byte) ((charAt & '?') | 128);
                    writableSegment$okio2.limit += 2;
                    setSize$okio(size() + 2);
                } else if (charAt < 55296 || charAt > 57343) {
                    Segment writableSegment$okio3 = writableSegment$okio(3);
                    writableSegment$okio3.data[writableSegment$okio3.limit] = (byte) ((charAt >> '\f') | 224);
                    writableSegment$okio3.data[writableSegment$okio3.limit + 1] = (byte) ((63 & (charAt >> 6)) | 128);
                    writableSegment$okio3.data[writableSegment$okio3.limit + 2] = (byte) ((charAt & '?') | 128);
                    writableSegment$okio3.limit += 3;
                    setSize$okio(size() + 3);
                } else {
                    i3 = i + 1;
                    char charAt3 = i3 < i2 ? string.charAt(i3) : (char) 0;
                    if (charAt > 56319 || 56320 > charAt3 || 57343 < charAt3) {
                        writeByte(63);
                    } else {
                        int i6 = (((charAt & 1023) << 10) | (charAt3 & 1023)) + 65536;
                        Segment writableSegment$okio4 = writableSegment$okio(4);
                        writableSegment$okio4.data[writableSegment$okio4.limit] = (byte) ((i6 >> 18) | 240);
                        writableSegment$okio4.data[writableSegment$okio4.limit + 1] = (byte) (((i6 >> 12) & 63) | 128);
                        writableSegment$okio4.data[writableSegment$okio4.limit + 2] = (byte) (((i6 >> 6) & 63) | 128);
                        writableSegment$okio4.data[writableSegment$okio4.limit + 3] = (byte) ((i6 & 63) | 128);
                        writableSegment$okio4.limit += 4;
                        setSize$okio(size() + 4);
                        i += 2;
                    }
                }
                i++;
            }
            i = i3;
        }
        return this;
    }

    @Override // okio.BufferedSink
    public Buffer writeUtf8CodePoint(int i) {
        if (i < 128) {
            writeByte(i);
        } else if (i < 2048) {
            Segment writableSegment$okio = writableSegment$okio(2);
            writableSegment$okio.data[writableSegment$okio.limit] = (byte) ((i >> 6) | 192);
            writableSegment$okio.data[writableSegment$okio.limit + 1] = (byte) ((i & 63) | 128);
            writableSegment$okio.limit += 2;
            setSize$okio(size() + 2);
        } else if (55296 <= i && 57343 >= i) {
            writeByte(63);
        } else if (i < 65536) {
            Segment writableSegment$okio2 = writableSegment$okio(3);
            writableSegment$okio2.data[writableSegment$okio2.limit] = (byte) ((i >> 12) | 224);
            writableSegment$okio2.data[writableSegment$okio2.limit + 1] = (byte) (((i >> 6) & 63) | 128);
            writableSegment$okio2.data[writableSegment$okio2.limit + 2] = (byte) ((i & 63) | 128);
            writableSegment$okio2.limit += 3;
            setSize$okio(size() + 3);
        } else if (i <= 1114111) {
            Segment writableSegment$okio3 = writableSegment$okio(4);
            writableSegment$okio3.data[writableSegment$okio3.limit] = (byte) ((i >> 18) | 240);
            writableSegment$okio3.data[writableSegment$okio3.limit + 1] = (byte) (((i >> 12) & 63) | 128);
            writableSegment$okio3.data[writableSegment$okio3.limit + 2] = (byte) (((i >> 6) & 63) | 128);
            writableSegment$okio3.data[writableSegment$okio3.limit + 3] = (byte) ((i & 63) | 128);
            writableSegment$okio3.limit += 4;
            setSize$okio(size() + 4);
        } else {
            throw new IllegalArgumentException("Unexpected code point: 0x" + Util.toHexString(i));
        }
        return this;
    }

    @Override // okio.BufferedSink
    public Buffer write(byte[] source) {
        Intrinsics.checkParameterIsNotNull(source, "source");
        return write(source, 0, source.length);
    }

    @Override // okio.BufferedSink
    public Buffer write(byte[] source, int i, int i2) {
        Intrinsics.checkParameterIsNotNull(source, "source");
        long j = i2;
        Util.checkOffsetAndCount(source.length, i, j);
        int i3 = i2 + i;
        while (i < i3) {
            Segment writableSegment$okio = writableSegment$okio(1);
            int min = Math.min(i3 - i, 8192 - writableSegment$okio.limit);
            int i4 = i + min;
            ArraysKt.copyInto(source, writableSegment$okio.data, writableSegment$okio.limit, i, i4);
            writableSegment$okio.limit += min;
            i = i4;
        }
        setSize$okio(size() + j);
        return this;
    }

    @Override // okio.BufferedSink
    public long writeAll(Source source) throws IOException {
        Intrinsics.checkParameterIsNotNull(source, "source");
        long j = 0;
        while (true) {
            long read = source.read(this, 8192);
            if (read == -1) {
                return j;
            }
            j += read;
        }
    }

    @Override // okio.BufferedSink
    public Buffer write(Source source, long j) throws IOException {
        Intrinsics.checkParameterIsNotNull(source, "source");
        while (j > 0) {
            long read = source.read(this, j);
            if (read == -1) {
                throw new EOFException();
            }
            j -= read;
        }
        return this;
    }

    @Override // okio.BufferedSink
    public Buffer writeByte(int i) {
        Segment writableSegment$okio = writableSegment$okio(1);
        byte[] bArr = writableSegment$okio.data;
        int i2 = writableSegment$okio.limit;
        writableSegment$okio.limit = i2 + 1;
        bArr[i2] = (byte) i;
        setSize$okio(size() + 1);
        return this;
    }

    @Override // okio.BufferedSink
    public Buffer writeShort(int i) {
        Segment writableSegment$okio = writableSegment$okio(2);
        byte[] bArr = writableSegment$okio.data;
        int i2 = writableSegment$okio.limit;
        int i3 = i2 + 1;
        bArr[i2] = (byte) ((i >>> 8) & 255);
        bArr[i3] = (byte) (i & 255);
        writableSegment$okio.limit = i3 + 1;
        setSize$okio(size() + 2);
        return this;
    }

    @Override // okio.BufferedSink
    public Buffer writeInt(int i) {
        Segment writableSegment$okio = writableSegment$okio(4);
        byte[] bArr = writableSegment$okio.data;
        int i2 = writableSegment$okio.limit;
        int i3 = i2 + 1;
        bArr[i2] = (byte) ((i >>> 24) & 255);
        int i4 = i3 + 1;
        bArr[i3] = (byte) ((i >>> 16) & 255);
        int i5 = i4 + 1;
        bArr[i4] = (byte) ((i >>> 8) & 255);
        bArr[i5] = (byte) (i & 255);
        writableSegment$okio.limit = i5 + 1;
        setSize$okio(size() + 4);
        return this;
    }

    @Override // okio.BufferedSink
    public Buffer writeLong(long j) {
        Segment writableSegment$okio = writableSegment$okio(8);
        byte[] bArr = writableSegment$okio.data;
        int i = writableSegment$okio.limit;
        int i2 = i + 1;
        bArr[i] = (byte) ((j >>> 56) & 255);
        int i3 = i2 + 1;
        bArr[i2] = (byte) ((j >>> 48) & 255);
        int i4 = i3 + 1;
        bArr[i3] = (byte) ((j >>> 40) & 255);
        int i5 = i4 + 1;
        bArr[i4] = (byte) ((j >>> 32) & 255);
        int i6 = i5 + 1;
        bArr[i5] = (byte) ((j >>> 24) & 255);
        int i7 = i6 + 1;
        bArr[i6] = (byte) ((j >>> 16) & 255);
        int i8 = i7 + 1;
        bArr[i7] = (byte) ((j >>> 8) & 255);
        bArr[i8] = (byte) (j & 255);
        writableSegment$okio.limit = i8 + 1;
        setSize$okio(size() + 8);
        return this;
    }

    @Override // okio.BufferedSink
    public Buffer writeDecimalLong(long j) {
        int i = 0;
        int i2 = (j > 0L ? 1 : (j == 0L ? 0 : -1));
        if (i2 == 0) {
            return writeByte(48);
        }
        boolean z = false;
        int i3 = 1;
        if (i2 < 0) {
            j = -j;
            if (j < 0) {
                return writeUtf8("-9223372036854775808");
            }
            z = true;
        }
        if (j < 100000000) {
            if (j >= 10000) {
                i = j < 1000000 ? j < 100000 ? 5 : 6 : j < 10000000 ? 7 : 8;
            } else if (j >= 100) {
                i = j < 1000 ? 3 : 4;
            } else if (j >= 10) {
                i3 = 2;
            }
            i3 = i;
        } else if (j < 1000000000000L) {
            if (j < RealConnection.IDLE_CONNECTION_HEALTHY_NS) {
                i3 = j < 1000000000 ? 9 : 10;
            } else {
                i = j < 100000000000L ? 11 : 12;
                i3 = i;
            }
        } else if (j >= 1000000000000000L) {
            i3 = j < 100000000000000000L ? j < 10000000000000000L ? 16 : 17 : j < 1000000000000000000L ? 18 : 19;
        } else if (j < 10000000000000L) {
            i3 = 13;
        } else {
            i = j < 100000000000000L ? 14 : 15;
            i3 = i;
        }
        if (z) {
            i3++;
        }
        Segment writableSegment$okio = writableSegment$okio(i3);
        byte[] bArr = writableSegment$okio.data;
        int i4 = writableSegment$okio.limit + i3;
        while (j != 0) {
            long j2 = 10;
            i4--;
            bArr[i4] = BufferKt.getHEX_DIGIT_BYTES()[(int) (j % j2)];
            j /= j2;
        }
        if (z) {
            bArr[i4 - 1] = (byte) 45;
        }
        writableSegment$okio.limit += i3;
        setSize$okio(size() + i3);
        return this;
    }

    @Override // okio.BufferedSink
    public Buffer writeHexadecimalUnsignedLong(long j) {
        if (j == 0) {
            return writeByte(48);
        }
        long j2 = (j >>> 1) | j;
        long j3 = j2 | (j2 >>> 2);
        long j4 = j3 | (j3 >>> 4);
        long j5 = j4 | (j4 >>> 8);
        long j6 = j5 | (j5 >>> 16);
        long j7 = j6 | (j6 >>> 32);
        long j8 = j7 - ((j7 >>> 1) & 6148914691236517205L);
        long j9 = ((j8 >>> 2) & 3689348814741910323L) + (j8 & 3689348814741910323L);
        long j10 = ((j9 >>> 4) + j9) & 1085102592571150095L;
        long j11 = j10 + (j10 >>> 8);
        long j12 = j11 + (j11 >>> 16);
        int i = (int) ((((j12 & 63) + ((j12 >>> 32) & 63)) + 3) / 4);
        Segment writableSegment$okio = writableSegment$okio(i);
        byte[] bArr = writableSegment$okio.data;
        int i2 = writableSegment$okio.limit;
        for (int i3 = (writableSegment$okio.limit + i) - 1; i3 >= i2; i3--) {
            bArr[i3] = BufferKt.getHEX_DIGIT_BYTES()[(int) (15 & j)];
            j >>>= 4;
        }
        writableSegment$okio.limit += i;
        setSize$okio(size() + i);
        return this;
    }

    public final Segment writableSegment$okio(int i) {
        boolean z = true;
        if (!((i < 1 || i > 8192) ? false : false)) {
            throw new IllegalArgumentException("unexpected capacity".toString());
        }
        Segment segment = this.head;
        if (segment == null) {
            Segment take = SegmentPool.INSTANCE.take();
            this.head = take;
            take.prev = take;
            take.next = take;
            return take;
        }
        if (segment == null) {
            Intrinsics.throwNpe();
        }
        Segment segment2 = segment.prev;
        if (segment2 == null) {
            Intrinsics.throwNpe();
        }
        return (segment2.limit + i > 8192 || !segment2.owner) ? segment2.push(SegmentPool.INSTANCE.take()) : segment2;
    }

    @Override // okio.Sink
    public void write(Buffer source, long j) {
        Segment segment = null;
        Segment segment2;
        Intrinsics.checkParameterIsNotNull(source, "source");
        if (!(source != this)) {
            throw new IllegalArgumentException("source == this".toString());
        }
        Util.checkOffsetAndCount(source.size(), 0L, j);
        while (j > 0) {
            Segment segment3 = source.head;
            if (segment3 == null) {
                Intrinsics.throwNpe();
            }
            int i = segment3.limit;
            if (source.head == null) {
                Intrinsics.throwNpe();
            }
            if (j < i - segment.pos) {
                Segment segment4 = this.head;
                if (segment4 != null) {
                    if (segment4 == null) {
                        Intrinsics.throwNpe();
                    }
                    segment2 = segment4.prev;
                } else {
                    segment2 = null;
                }
                if (segment2 != null && segment2.owner) {
                    if ((segment2.limit + j) - (segment2.shared ? 0 : segment2.pos) <= 8192) {
                        Segment segment5 = source.head;
                        if (segment5 == null) {
                            Intrinsics.throwNpe();
                        }
                        segment5.writeTo(segment2, (int) j);
                        source.setSize$okio(source.size() - j);
                        setSize$okio(size() + j);
                        return;
                    }
                }
                Segment segment6 = source.head;
                if (segment6 == null) {
                    Intrinsics.throwNpe();
                }
                source.head = segment6.split((int) j);
            }
            Segment segment7 = source.head;
            if (segment7 == null) {
                Intrinsics.throwNpe();
            }
            long j2 = segment7.limit - segment7.pos;
            source.head = segment7.pop();
            Segment segment8 = this.head;
            if (segment8 == null) {
                this.head = segment7;
                segment7.prev = segment7;
                segment7.next = segment7.prev;
            } else {
                if (segment8 == null) {
                    Intrinsics.throwNpe();
                }
                Segment segment9 = segment8.prev;
                if (segment9 == null) {
                    Intrinsics.throwNpe();
                }
                segment9.push(segment7).compact();
            }
            source.setSize$okio(source.size() - j2);
            setSize$okio(size() + j2);
            j -= j2;
        }
    }

    @Override // okio.Source
    public long read(Buffer sink, long j) {
        Intrinsics.checkParameterIsNotNull(sink, "sink");
        if (!(j >= 0)) {
            throw new IllegalArgumentException(("byteCount < 0: " + j).toString());
        } else if (size() == 0) {
            return -1L;
        } else {
            if (j > size()) {
                j = size();
            }
            sink.write(this, j);
            return j;
        }
    }

    @Override // okio.BufferedSource
    public long indexOf(byte b, long j, long j2) {
        Segment segment;
        int i = 0;
        long j3 = 0;
        if (!(0 <= j && j2 >= j)) {
            throw new IllegalArgumentException(("size=" + size() + " fromIndex=" + j + " toIndex=" + j2).toString());
        }
        if (j2 > size()) {
            j2 = size();
        }
        if (j != j2 && (segment = this.head) != null) {
            if (size() - j < j) {
                j3 = size();
                while (j3 > j) {
                    segment = segment.prev;
                    if (segment == null) {
                        Intrinsics.throwNpe();
                    }
                    j3 -= segment.limit - segment.pos;
                }
                if (segment != null) {
                    while (j3 < j2) {
                        byte[] bArr = segment.data;
                        int min = (int) Math.min(segment.limit, (segment.pos + j2) - j3);
                        i = (int) ((segment.pos + j) - j3);
                        while (i < min) {
                            if (bArr[i] != b) {
                                i++;
                            }
                        }
                        j3 += segment.limit - segment.pos;
                        segment = segment.next;
                        if (segment == null) {
                            Intrinsics.throwNpe();
                        }
                        j = j3;
                    }
                    return -1L;
                }
                return -1L;
            }
            while (true) {
                long j4 = (segment.limit - segment.pos) + j3;
                if (j4 > j) {
                    break;
                }
                segment = segment.next;
                if (segment == null) {
                    Intrinsics.throwNpe();
                }
                j3 = j4;
            }
            if (segment != null) {
                while (j3 < j2) {
                    byte[] bArr2 = segment.data;
                    int min2 = (int) Math.min(segment.limit, (segment.pos + j2) - j3);
                    i = (int) ((segment.pos + j) - j3);
                    while (i < min2) {
                        if (bArr2[i] != b) {
                            i++;
                        }
                    }
                    j3 += segment.limit - segment.pos;
                    segment = segment.next;
                    if (segment == null) {
                        Intrinsics.throwNpe();
                    }
                    j = j3;
                }
                return -1L;
            }

            return (i - segment.pos) + j3;
        }
        return -1L;
    }

    @Override // okio.BufferedSource
    public long indexOf(ByteString bytes, long j) throws IOException {
        long j2 = j;
        Intrinsics.checkParameterIsNotNull(bytes, "bytes");
        if (bytes.size() > 0) {
            long j3 = 0;
            if (!(j2 >= 0)) {
                throw new IllegalArgumentException(("fromIndex < 0: " + j2).toString());
            }
            Segment segment = this.head;
            if (segment != null) {
                if (size() - j2 < j2) {
                    long size = size();
                    while (size > j2) {
                        segment = segment.prev;
                        if (segment == null) {
                            Intrinsics.throwNpe();
                        }
                        size -= segment.limit - segment.pos;
                    }
                    if (segment != null) {
                        byte[] internalArray$okio = bytes.internalArray$okio();
                        byte b = internalArray$okio[0];
                        int size2 = bytes.size();
                        long size3 = (size() - size2) + 1;
                        while (size < size3) {
                            byte[] bArr = segment.data;
                            long j4 = size;
                            int min = (int) Math.min(segment.limit, (segment.pos + size3) - size);
                            for (int i = (int) ((segment.pos + j2) - j4); i < min; i++) {
                                if (bArr[i] == b && BufferKt.rangeEquals(segment, i + 1, internalArray$okio, 1, size2)) {
                                    return (i - segment.pos) + j4;
                                }
                            }
                            size = j4 + (segment.limit - segment.pos);
                            segment = segment.next;
                            if (segment == null) {
                                Intrinsics.throwNpe();
                            }
                            j2 = size;
                        }
                    }
                } else {
                    while (true) {
                        long j5 = (segment.limit - segment.pos) + j3;
                        if (j5 > j2) {
                            break;
                        }
                        segment = segment.next;
                        if (segment == null) {
                            Intrinsics.throwNpe();
                        }
                        j3 = j5;
                    }
                    if (segment != null) {
                        byte[] internalArray$okio2 = bytes.internalArray$okio();
                        byte b2 = internalArray$okio2[0];
                        int size4 = bytes.size();
                        long size5 = (size() - size4) + 1;
                        while (j3 < size5) {
                            byte[] bArr2 = segment.data;
                            long j6 = size5;
                            int min2 = (int) Math.min(segment.limit, (segment.pos + size5) - j3);
                            for (int i2 = (int) ((segment.pos + j2) - j3); i2 < min2; i2++) {
                                if (bArr2[i2] == b2 && BufferKt.rangeEquals(segment, i2 + 1, internalArray$okio2, 1, size4)) {
                                    return (i2 - segment.pos) + j3;
                                }
                            }
                            j3 += segment.limit - segment.pos;
                            segment = segment.next;
                            if (segment == null) {
                                Intrinsics.throwNpe();
                            }
                            j2 = j3;
                            size5 = j6;
                        }
                    }
                }
            }
            return -1L;
        }
        throw new IllegalArgumentException("bytes is empty".toString());
    }

    @Override // okio.BufferedSource
    public long indexOfElement(ByteString targetBytes, long j) {
        int i = 0;
        int i2 = 0;
        Intrinsics.checkParameterIsNotNull(targetBytes, "targetBytes");
        long j2 = 0;
        if (!(j >= 0)) {
            throw new IllegalArgumentException(("fromIndex < 0: " + j).toString());
        }
        Segment segment = this.head;
        if (segment != null) {
            if (size() - j < j) {
                j2 = size();
                while (j2 > j) {
                    segment = segment.prev;
                    if (segment == null) {
                        Intrinsics.throwNpe();
                    }
                    j2 -= segment.limit - segment.pos;
                }
                if (segment != null) {
                    if (targetBytes.size() == 2) {
                        byte b = targetBytes.getByte(0);
                        byte b2 = targetBytes.getByte(1);
                        while (j2 < size()) {
                            byte[] bArr = segment.data;
                            i = (int) ((segment.pos + j) - j2);
                            int i3 = segment.limit;
                            while (i < i3) {
                                byte b3 = bArr[i];
                                if (b3 != b && b3 != b2) {
                                    i++;
                                }
                                i2 = segment.pos;
                            }
                            j2 += segment.limit - segment.pos;
                            segment = segment.next;
                            if (segment == null) {
                                Intrinsics.throwNpe();
                            }
                            j = j2;
                        }
                        return -1L;
                    }
                    byte[] internalArray$okio = targetBytes.internalArray$okio();
                    while (j2 < size()) {
                        byte[] bArr2 = segment.data;
                        i = (int) ((segment.pos + j) - j2);
                        int i4 = segment.limit;
                        while (i < i4) {
                            byte b4 = bArr2[i];
                            for (byte b5 : internalArray$okio) {
                                if (b4 == b5) {
                                    i2 = segment.pos;
                                }
                            }
                            i++;
                        }
                        j2 += segment.limit - segment.pos;
                        segment = segment.next;
                        if (segment == null) {
                            Intrinsics.throwNpe();
                        }
                        j = j2;
                    }
                    return -1L;
                }
                return -1L;
            }
            while (true) {
                long j3 = (segment.limit - segment.pos) + j2;
                if (j3 > j) {
                    break;
                }
                segment = segment.next;
                if (segment == null) {
                    Intrinsics.throwNpe();
                }
                j2 = j3;
            }
            if (segment != null) {
                if (targetBytes.size() == 2) {
                    byte b6 = targetBytes.getByte(0);
                    byte b7 = targetBytes.getByte(1);
                    while (j2 < size()) {
                        byte[] bArr3 = segment.data;
                        i = (int) ((segment.pos + j) - j2);
                        int i5 = segment.limit;
                        while (i < i5) {
                            byte b8 = bArr3[i];
                            if (b8 != b6 && b8 != b7) {
                                i++;
                            }
                            i2 = segment.pos;
                        }
                        j2 += segment.limit - segment.pos;
                        segment = segment.next;
                        if (segment == null) {
                            Intrinsics.throwNpe();
                        }
                        j = j2;
                    }
                    return -1L;
                }
                byte[] internalArray$okio2 = targetBytes.internalArray$okio();
                while (j2 < size()) {
                    byte[] bArr4 = segment.data;
                    i = (int) ((segment.pos + j) - j2);
                    int i6 = segment.limit;
                    while (i < i6) {
                        byte b9 = bArr4[i];
                        for (byte b10 : internalArray$okio2) {
                            if (b9 == b10) {
                                i2 = segment.pos;
                            }
                        }
                        i++;
                    }
                    j2 += segment.limit - segment.pos;
                    segment = segment.next;
                    if (segment == null) {
                        Intrinsics.throwNpe();
                    }
                    j = j2;
                }
                return -1L;
            }

            return (i - i2) + j2;
        }
        return -1L;
    }

    @Override // okio.BufferedSource
    public boolean rangeEquals(long j, ByteString bytes, int i, int i2) {
        Intrinsics.checkParameterIsNotNull(bytes, "bytes");
        if (j < 0 || i < 0 || i2 < 0 || size() - j < i2 || bytes.size() - i < i2) {
            return false;
        }
        for (int i3 = 0; i3 < i2; i3++) {
            if (getByte(i3 + j) != bytes.getByte(i + i3)) {
                return false;
            }
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (this != obj) {
            if (!(obj instanceof Buffer)) {
                return false;
            }
            Buffer buffer = (Buffer) obj;
            if (size() != buffer.size()) {
                return false;
            }
            if (size() != 0) {
                Segment segment = this.head;
                if (segment == null) {
                    Intrinsics.throwNpe();
                }
                Segment segment2 = buffer.head;
                if (segment2 == null) {
                    Intrinsics.throwNpe();
                }
                int i = segment.pos;
                int i2 = segment2.pos;
                long j = 0;
                while (j < size()) {
                    long min = Math.min(segment.limit - i, segment2.limit - i2);
                    long j2 = 0;
                    while (j2 < min) {
                        int i3 = i + 1;
                        int i4 = i2 + 1;
                        if (segment.data[i] != segment2.data[i2]) {
                            return false;
                        }
                        j2++;
                        i = i3;
                        i2 = i4;
                    }
                    if (i == segment.limit) {
                        segment = segment.next;
                        if (segment == null) {
                            Intrinsics.throwNpe();
                        }
                        i = segment.pos;
                    }
                    if (i2 == segment2.limit) {
                        segment2 = segment2.next;
                        if (segment2 == null) {
                            Intrinsics.throwNpe();
                        }
                        i2 = segment2.pos;
                    }
                    j += min;
                }
            }
        }
        return true;
    }

    public int hashCode() {
        Segment segment = this.head;
        if (segment != null) {
            int i = 1;
            do {
                int i2 = segment.limit;
                for (int i3 = segment.pos; i3 < i2; i3++) {
                    i = (i * 31) + segment.data[i3];
                }
                segment = segment.next;
                if (segment == null) {
                    Intrinsics.throwNpe();
                }
            } while (segment != this.head);
            return i;
        }
        return 0;
    }

    public final Buffer copy() {
        Buffer buffer = new Buffer();
        if (size() != 0) {
            Segment segment = this.head;
            if (segment == null) {
                Intrinsics.throwNpe();
            }
            Segment sharedCopy = segment.sharedCopy();
            buffer.head = sharedCopy;
            sharedCopy.prev = sharedCopy;
            sharedCopy.next = sharedCopy.prev;
            for (Segment segment2 = segment.next; segment2 != segment; segment2 = segment2.next) {
                Segment segment3 = sharedCopy.prev;
                if (segment3 == null) {
                    Intrinsics.throwNpe();
                }
                if (segment2 == null) {
                    Intrinsics.throwNpe();
                }
                segment3.push(segment2.sharedCopy());
            }
            buffer.setSize$okio(size());
        }
        return buffer;
    }

    public final ByteString snapshot() {
        if (!(size() <= ((long) Integer.MAX_VALUE))) {
            throw new IllegalStateException(("size > Int.MAX_VALUE: " + size()).toString());
        }
        return snapshot((int) size());
    }

    public final ByteString snapshot(int i) {
        if (i == 0) {
            return ByteString.EMPTY;
        }
        Util.checkOffsetAndCount(size(), 0L, i);
        Segment segment = this.head;
        int i2 = 0;
        int i3 = 0;
        int i4 = 0;
        while (i3 < i) {
            if (segment == null) {
                Intrinsics.throwNpe();
            }
            if (segment.limit == segment.pos) {
                throw new AssertionError("s.limit == s.pos");
            }
            i3 += segment.limit - segment.pos;
            i4++;
            segment = segment.next;
        }
        byte[][] bArr = new byte[][]{new byte[i4]};
        int[] iArr = new int[i4 * 2];
        Segment segment2 = this.head;
        int i5 = 0;
        while (i2 < i) {
            if (segment2 == null) {
                Intrinsics.throwNpe();
            }
            bArr[i5] = segment2.data;
            i2 += segment2.limit - segment2.pos;
            iArr[i5] = Math.min(i2, i);
            iArr[bArr.length + i5] = segment2.pos;
            segment2.shared = true;
            i5++;
            segment2 = segment2.next;
        }
        return new SegmentedByteString(bArr, iArr);
    }
}