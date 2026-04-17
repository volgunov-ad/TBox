package okio;

import java.io.IOException;
import java.util.List;
import java.util.RandomAccess;
import kotlin.Metadata;
import kotlin.UByte;
import kotlin.collections.CustomCustomAbstractList;
import kotlin.jvm.JvmStatic;
import kotlin.jvm.internal.DefaultConstructorMarker;

/* compiled from: Options.kt */
@Metadata(bv = {1, 0, 3}, d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0011\n\u0000\n\u0002\u0010\u0015\n\u0002\b\u0005\n\u0002\u0010\b\n\u0002\b\b\u0018\u0000 \u00152\b\u0012\u0004\u0012\u00020\u00020\u00012\u00060\u0003j\u0002`\u0004:\u0001\u0015B\u001f\b\u0002\u0012\u000e\u0010\u0005\u001a\n\u0012\u0006\b\u0001\u0012\u00020\u00020\u0006\u0012\u0006\u0010\u0007\u001a\u00020\b¢\u0006\u0002\u0010\tJ\u0011\u0010\u0013\u001a\u00020\u00022\u0006\u0010\u0014\u001a\u00020\u000eH\u0096\u0002R\u001e\u0010\u0005\u001a\n\u0012\u0006\b\u0001\u0012\u00020\u00020\u0006X\u0080\u0004¢\u0006\n\n\u0002\u0010\f\u001a\u0004\b\n\u0010\u000bR\u0014\u0010\r\u001a\u00020\u000e8VX\u0096\u0004¢\u0006\u0006\u001a\u0004\b\u000f\u0010\u0010R\u0014\u0010\u0007\u001a\u00020\bX\u0080\u0004¢\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012¨\u0006\u0016"}, d2 = {"Lokio/Options;", "Lkotlin/collections/AbstractList;", "Lokio/ByteString;", "Ljava/util/RandomAccess;", "Lkotlin/collections/RandomAccess;", "byteStrings", "", "trie", "", "([Lokio/ByteString;[I)V", "getByteStrings$okio", "()[Lokio/ByteString;", "[Lokio/ByteString;", "size", "", "getSize", "()I", "getTrie$okio", "()[I", "get", "index", "Companion", "okio"}, k = 1, mv = {1, 1, 16})
/* loaded from: classes2.dex */
public final class Options extends CustomCustomAbstractList<ByteString> implements RandomAccess {
    public static final Companion Companion = new Companion(null);
    public static Options.Companion INSTANCE;
    private final ByteString[] byteStrings;
    private final int[] trie;

    @JvmStatic
    public static final Options of(ByteString... byteStringArr) {
        return Companion.of(byteStringArr);
    }

    public /* synthetic */ Options(ByteString[] byteStringArr, int[] iArr, DefaultConstructorMarker defaultConstructorMarker) {
        this(byteStringArr, iArr);
    }

    @Override // kotlin.collections.AbstractCollection, java.util.Collection
    public final /* bridge */ boolean contains(Object obj) {
        if (obj instanceof ByteString) {
            return contains((ByteString) obj);
        }
        return false;
    }

    public /* bridge */ boolean contains(ByteString byteString) {
        return super.contains((Object) byteString);
    }

    @Override // kotlin.collections.AbstractList, java.util.List
    public final /* bridge */ int indexOf(Object obj) {
        if (obj instanceof ByteString) {
            return indexOf((ByteString) obj);
        }
        return -1;
    }

    public /* bridge */ int indexOf(ByteString byteString) {
        return super.indexOf((Object) byteString);
    }

    @Override // kotlin.collections.AbstractList, java.util.List
    public final /* bridge */ int lastIndexOf(Object obj) {
        if (obj instanceof ByteString) {
            return lastIndexOf((ByteString) obj);
        }
        return -1;
    }

    public /* bridge */ int lastIndexOf(ByteString byteString) {
        return super.lastIndexOf((Object) byteString);
    }

    public final ByteString[] getByteStrings$okio() {
        return this.byteStrings;
    }

    public final int[] getTrie$okio() {
        return this.trie;
    }

    private Options(ByteString[] byteStringArr, int[] iArr) {
        this.byteStrings = byteStringArr;
        this.trie = iArr;
    }

    @Override // kotlin.collections.AbstractList, kotlin.collections.AbstractCollection
    public int getSize() {
        return this.byteStrings.length;
    }

    @Override // kotlin.collections.AbstractList, java.util.List
    public ByteString get(int i) {
        return this.byteStrings[i];
    }

    public Buffer[] getByteStrings() {
        return new Buffer[0];
    }

    public int[] getTrie() {
        return new int[0];
    }

    /* compiled from: Options.kt */
    @Metadata(bv = {1, 0, 3}, d1 = {"\u0000>\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\t\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\u0010\u0011\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002JT\u0010\b\u001a\u00020\t2\b\b\u0002\u0010\n\u001a\u00020\u00042\u0006\u0010\u000b\u001a\u00020\u00052\b\b\u0002\u0010\f\u001a\u00020\r2\f\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00100\u000f2\b\b\u0002\u0010\u0011\u001a\u00020\r2\b\b\u0002\u0010\u0012\u001a\u00020\r2\f\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\r0\u000fH\u0002J!\u0010\u0014\u001a\u00020\u00152\u0012\u0010\u000e\u001a\n\u0012\u0006\b\u0001\u0012\u00020\u00100\u0016\"\u00020\u0010H\u0007¢\u0006\u0002\u0010\u0017R\u0018\u0010\u0003\u001a\u00020\u0004*\u00020\u00058BX\u0082\u0004¢\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007¨\u0006\u0018"}, d2 = {"Lokio/Options$Companion;", "", "()V", "intCount", "", "Lokio/Buffer;", "getIntCount", "(Lokio/Buffer;)J", "buildTrieRecursive", "", "nodeOffset", "node", "byteStringOffset", "", "byteStrings", "", "Lokio/ByteString;", "fromIndex", "toIndex", "indexes", "of", "Lokio/Options;", "", "([Lokio/ByteString;)Lokio/Options;", "okio"}, k = 1, mv = {1, 1, 16})
    /* loaded from: classes2.dex */
    public static final class Companion {
        private Companion() {
        }

        public /* synthetic */ Companion(DefaultConstructorMarker defaultConstructorMarker) {
            this();
        }

        /* JADX WARN: Code restructure failed: missing block: B:59:0x00f9, code lost:
            continue;
         */
        @kotlin.jvm.JvmStatic
        /*
            Code decompiled incorrectly, please refer to instructions dump.
            To view partially-correct code enable 'Show inconsistent code' option in preferences
        */
        public final okio.Options of(okio.ByteString... r18) {
            /*
                Method dump skipped, instructions count: 350
                To view this dump change 'Code comments level' option to 'DEBUG'
            */
            throw new UnsupportedOperationException("Method not decompiled: okio.Options.Companion.of(okio.ByteString[]):okio.Options");
        }

        static /* synthetic */ void buildTrieRecursive$default(Companion companion, long j, Buffer buffer, int i, List list, int i2, int i3, List list2, int i4, Object obj) throws IOException {
            companion.buildTrieRecursive((i4 & 1) != 0 ? 0L : j, buffer, (i4 & 4) != 0 ? 0 : i, list, (i4 & 16) != 0 ? 0 : i2, (i4 & 32) != 0 ? list.size() : i3, list2);
        }

        private final void buildTrieRecursive(long j, Buffer buffer, int i, List<? extends ByteString> list, int i2, int i3, List<Integer> list2) throws IOException {
            int i4;
            int i5;
            int i6;
            int i7;
            Buffer buffer2;
            int i8 = i;
            if (!(i2 < i3)) {
                throw new IllegalArgumentException("Failed requirement.".toString());
            }
            for (int i9 = i2; i9 < i3; i9++) {
                if (!(list.get(i9).size() >= i8)) {
                    throw new IllegalArgumentException("Failed requirement.".toString());
                }
            }
            ByteString byteString = list.get(i2);
            ByteString byteString2 = list.get(i3 - 1);
            if (i8 == byteString.size()) {
                int intValue = list2.get(i2).intValue();
                int i10 = i2 + 1;
                ByteString byteString3 = list.get(i10);
                i4 = i10;
                i5 = intValue;
                byteString = byteString3;
            } else {
                i4 = i2;
                i5 = -1;
            }
            if (byteString.getByte(i8) != byteString2.getByte(i8)) {
                int i11 = 1;
                for (int i12 = i4 + 1; i12 < i3; i12++) {
                    if (list.get(i12 - 1).getByte(i8) != list.get(i12).getByte(i8)) {
                        i11++;
                    }
                }
                Companion companion = this;
                long intCount = j + companion.getIntCount(buffer) + 2 + (i11 * 2);
                buffer.writeInt(i11);
                buffer.writeInt(i5);
                for (int i13 = i4; i13 < i3; i13++) {
                    byte b = list.get(i13).getByte(i8);
                    if (i13 == i4 || b != list.get(i13 - 1).getByte(i8)) {
                        buffer.writeInt(b & UByte.MAX_VALUE);
                    }
                }
                Buffer buffer3 = new Buffer();
                int i14 = i4;
                while (i14 < i3) {
                    byte b2 = list.get(i14).getByte(i8);
                    int i15 = i14 + 1;
                    int i16 = i15;
                    while (true) {
                        if (i16 >= i3) {
                            i6 = i3;
                            break;
                        } else if (b2 != list.get(i16).getByte(i8)) {
                            i6 = i16;
                            break;
                        } else {
                            i16++;
                        }
                    }
                    if (i15 == i6 && i8 + 1 == list.get(i14).size()) {
                        buffer.writeInt(list2.get(i14).intValue());
                        i7 = i6;
                        buffer2 = buffer3;
                    } else {
                        buffer.writeInt(((int) (intCount + companion.getIntCount(buffer3))) * (-1));
                        i7 = i6;
                        buffer2 = buffer3;
                        companion.buildTrieRecursive(intCount, buffer3, i8 + 1, list, i14, i6, list2);
                    }
                    i14 = i7;
                    buffer3 = buffer2;
                }
                buffer.writeAll(buffer3);
                return;
            }
            int min = Math.min(byteString.size(), byteString2.size());
            int i17 = 0;
            for (int i18 = i8; i18 < min && byteString.getByte(i18) == byteString2.getByte(i18); i18++) {
                i17++;
            }
            Companion companion2 = this;
            long intCount2 = 1 + j + companion2.getIntCount(buffer) + 2 + i17;
            buffer.writeInt(-i17);
            buffer.writeInt(i5);
            int i19 = i8 + i17;
            while (i8 < i19) {
                buffer.writeInt(byteString.getByte(i8) & UByte.MAX_VALUE);
                i8++;
            }
            if (i4 + 1 == i3) {
                if (!(i19 == list.get(i4).size())) {
                    throw new IllegalStateException("Check failed.".toString());
                }
                buffer.writeInt(list2.get(i4).intValue());
                return;
            }
            Buffer buffer4 = new Buffer();
            buffer.writeInt(((int) (companion2.getIntCount(buffer4) + intCount2)) * (-1));
            companion2.buildTrieRecursive(intCount2, buffer4, i19, list, i4, i3, list2);
            buffer.writeAll(buffer4);
        }

        private final long getIntCount(Buffer buffer) {
            return buffer.size() / 4;
        }
    }
}