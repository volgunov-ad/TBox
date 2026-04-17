package com.wt.compoent.utils;

import android.util.Log;
import androidx.core.app.NotificationCompat;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;

/* compiled from: log.kt */
@Metadata(bv = {1, 0, 3}, d1 = {"\u0000\u001e\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0002\b\u0006\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0002\b\b\u001a\u0006\u0010\b\u001a\u00020\u0001\u001a\u0010\u0010\b\u001a\u00020\u00012\b\u0010\t\u001a\u0004\u0018\u00010\n\u001a\u000e\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u0001\u001a\u0016\u0010\u000b\u001a\u00020\f2\u0006\u0010\u000e\u001a\u00020\u00012\u0006\u0010\r\u001a\u00020\u0001\u001a\u000e\u0010\u000f\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u0001\u001a\u0016\u0010\u000f\u001a\u00020\f2\u0006\u0010\u000e\u001a\u00020\u00012\u0006\u0010\r\u001a\u00020\u0001\u001a\u000e\u0010\u0010\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u0001\u001a\u0016\u0010\u0010\u001a\u00020\f2\u0006\u0010\u000e\u001a\u00020\u00012\u0006\u0010\r\u001a\u00020\u0001\u001a\u0016\u0010\u0011\u001a\u00020\f2\u0006\u0010\u000e\u001a\u00020\u00012\u0006\u0010\r\u001a\u00020\u0001\u001a\u000e\u0010\u0012\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u0001\u001a\u0016\u0010\u0012\u001a\u00020\f2\u0006\u0010\u000e\u001a\u00020\u00012\u0006\u0010\r\u001a\u00020\u0001\u001a\u000e\u0010\u0013\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u0001\u001a\u0016\u0010\u0013\u001a\u00020\f2\u0006\u0010\u000e\u001a\u00020\u00012\u0006\u0010\r\u001a\u00020\u0001\"\u000e\u0010\u0000\u001a\u00020\u0001X\u0082\u000e¢\u0006\u0002\n\u0000\"\u001a\u0010\u0002\u001a\u00020\u0003X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u0004\u0010\u0005\"\u0004\b\u0006\u0010\u0007¨\u0006\u0014"}, d2 = {"DEFAULT_TAG", "", "LOG_LEVEL", "", "getLOG_LEVEL", "()I", "setLOG_LEVEL", "(I)V", "createTag", "o", "", "d", "", NotificationCompat.CATEGORY_MESSAGE, "tag", "e", "i", "ignore", "v", "w", "authsdk_release"}, k = 2, mv = {1, 1, 16})
/* loaded from: classes2.dex */
public final class LogKt {
    private static String DEFAULT_TAG = "wt_auth";
    private static int LOG_LEVEL = 2;

    public static final int getLOG_LEVEL() {
        return LOG_LEVEL;
    }

    public static final void setLOG_LEVEL(int i) {
        LOG_LEVEL = i;
    }

    public static final String createTag(Object obj) {
        if (obj == null) {
            return createTag();
        }
        return DEFAULT_TAG + obj.getClass().getSimpleName();
    }

    public static final String createTag() {
        return DEFAULT_TAG;
    }

    public static final void v(String msg) {
        Intrinsics.checkParameterIsNotNull(msg, "msg");
        if (2 >= LOG_LEVEL) {
            Log.v(DEFAULT_TAG, msg);
        }
    }

    public static final void d(String msg) {
        Intrinsics.checkParameterIsNotNull(msg, "msg");
        if (3 >= LOG_LEVEL) {
            Log.d(DEFAULT_TAG, msg);
        }
    }

    public static final void i(String msg) {
        Intrinsics.checkParameterIsNotNull(msg, "msg");
        if (4 >= LOG_LEVEL) {
            Log.i(DEFAULT_TAG, msg);
        }
    }

    public static final void w(String msg) {
        Intrinsics.checkParameterIsNotNull(msg, "msg");
        if (5 >= LOG_LEVEL) {
            Log.w(DEFAULT_TAG, msg);
        }
    }

    public static final void e(String msg) {
        Intrinsics.checkParameterIsNotNull(msg, "msg");
        if (6 >= LOG_LEVEL) {
            e(DEFAULT_TAG, msg);
        }
    }

    public static final void v(String tag, String msg) {
        Intrinsics.checkParameterIsNotNull(tag, "tag");
        Intrinsics.checkParameterIsNotNull(msg, "msg");
        if (2 >= LOG_LEVEL) {
            Log.v(tag, msg);
        }
    }

    public static final void d(String tag, String msg) {
        Intrinsics.checkParameterIsNotNull(tag, "tag");
        Intrinsics.checkParameterIsNotNull(msg, "msg");
        if (3 >= LOG_LEVEL) {
            Log.d(tag, msg);
        }
    }

    public static final void i(String tag, String msg) {
        Intrinsics.checkParameterIsNotNull(tag, "tag");
        Intrinsics.checkParameterIsNotNull(msg, "msg");
        if (4 >= LOG_LEVEL) {
            Log.i(tag, msg);
        }
    }

    public static final void w(String tag, String msg) {
        Intrinsics.checkParameterIsNotNull(tag, "tag");
        Intrinsics.checkParameterIsNotNull(msg, "msg");
        if (5 >= LOG_LEVEL) {
            Log.w(tag, msg);
        }
    }

    public static final void e(String tag, String msg) {
        Intrinsics.checkParameterIsNotNull(tag, "tag");
        Intrinsics.checkParameterIsNotNull(msg, "msg");
        if (6 >= LOG_LEVEL) {
            Log.e(tag, msg);
        }
    }

    public static final void ignore(String tag, String msg) {
        Intrinsics.checkParameterIsNotNull(tag, "tag");
        Intrinsics.checkParameterIsNotNull(msg, "msg");
        if (2 >= LOG_LEVEL) {
            Log.v(tag, "ignore_" + msg);
        }
    }
}
