package com.wt.compoent;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import androidx.core.content.ContextCompat;
import com.bumptech.glide.load.Key;
import com.mengbo.mbconfig.MBConfigConstant;
import com.wt.compoent.utils.LogKt;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import kotlin.Metadata;
import kotlin.TypeCastException;
import kotlin.UByte;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.StringsKt;

/* compiled from: common.kt */
@Metadata(bv = {1, 0, 3}, d1 = {"\u0000*\n\u0000\n\u0002\u0010\u000e\n\u0002\b\b\n\u0002\u0010\u000b\n\u0002\b\u0013\n\u0002\u0010\t\n\u0002\b\b\n\u0002\u0018\u0002\n\u0002\b\u000e\n\u0002\u0010\u0012\n\u0002\b\u0002\u001a\u000e\u0010#\u001a\u00020\n2\u0006\u0010$\u001a\u00020\u0001\u001a\u0016\u0010%\u001a\u00020\n2\u0006\u0010&\u001a\u00020'2\u0006\u0010(\u001a\u00020\u0001\u001a\u0010\u0010)\u001a\u00020\u00012\u0006\u0010*\u001a\u00020\u001eH\u0007\u001a\u000e\u0010+\u001a\u00020\u00012\u0006\u0010&\u001a\u00020'\u001a\u000e\u0010,\u001a\u00020\u00012\u0006\u0010&\u001a\u00020'\u001a\u000e\u0010-\u001a\u00020\u00012\u0006\u0010&\u001a\u00020'\u001a\u000e\u0010.\u001a\u00020\u00012\u0006\u0010&\u001a\u00020'\u001a\u0016\u0010/\u001a\u00020\u00012\u0006\u00100\u001a\u00020\u00012\u0006\u0010\u001d\u001a\u00020\u001e\u001a\u0006\u00101\u001a\u00020\u0001\u001a\u000e\u00102\u001a\u00020\u00012\u0006\u00103\u001a\u00020\u0001\u001a\u000e\u00104\u001a\u00020\u00012\u0006\u00105\u001a\u000206\u001a\u0010\u00107\u001a\u00020\n2\u0006\u0010&\u001a\u00020'H\u0007\"\u000e\u0010\u0000\u001a\u00020\u0001X\u0086T¢\u0006\u0002\n\u0000\"\u000e\u0010\u0002\u001a\u00020\u0001X\u0086T¢\u0006\u0002\n\u0000\"\u000e\u0010\u0003\u001a\u00020\u0001X\u0086T¢\u0006\u0002\n\u0000\"\u000e\u0010\u0004\u001a\u00020\u0001X\u0086T¢\u0006\u0002\n\u0000\"\u000e\u0010\u0005\u001a\u00020\u0001X\u0086T¢\u0006\u0002\n\u0000\"\u000e\u0010\u0006\u001a\u00020\u0001X\u0086T¢\u0006\u0002\n\u0000\"\u000e\u0010\u0007\u001a\u00020\u0001X\u0086T¢\u0006\u0002\n\u0000\"\u000e\u0010\b\u001a\u00020\u0001X\u0086T¢\u0006\u0002\n\u0000\"\u001a\u0010\t\u001a\u00020\nX\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u000b\u0010\f\"\u0004\b\r\u0010\u000e\"\u000e\u0010\u000f\u001a\u00020\u0001X\u0086T¢\u0006\u0002\n\u0000\"\u000e\u0010\u0010\u001a\u00020\u0001X\u0086T¢\u0006\u0002\n\u0000\"\u000e\u0010\u0011\u001a\u00020\u0001X\u0086T¢\u0006\u0002\n\u0000\"\u000e\u0010\u0012\u001a\u00020\u0001X\u0086T¢\u0006\u0002\n\u0000\"\u000e\u0010\u0013\u001a\u00020\u0001X\u0086T¢\u0006\u0002\n\u0000\"\u000e\u0010\u0014\u001a\u00020\u0001X\u0086T¢\u0006\u0002\n\u0000\"\u000e\u0010\u0015\u001a\u00020\u0001X\u0086T¢\u0006\u0002\n\u0000\"\u000e\u0010\u0016\u001a\u00020\u0001X\u0086T¢\u0006\u0002\n\u0000\"\u000e\u0010\u0017\u001a\u00020\u0001X\u0086T¢\u0006\u0002\n\u0000\"\u000e\u0010\u0018\u001a\u00020\u0001X\u0086T¢\u0006\u0002\n\u0000\"\u000e\u0010\u0019\u001a\u00020\u0001X\u0086T¢\u0006\u0002\n\u0000\"\u000e\u0010\u001a\u001a\u00020\u0001X\u0086T¢\u0006\u0002\n\u0000\"\u000e\u0010\u001b\u001a\u00020\u0001X\u0086T¢\u0006\u0002\n\u0000\"\u000e\u0010\u001c\u001a\u00020\u0001X\u0086T¢\u0006\u0002\n\u0000\"\u001c\u0010\u001d\u001a\u00020\u001e8FX\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u001f\u0010 \"\u0004\b!\u0010\"¨\u00068"}, d2 = {"ACCESS_TOKEN_URL", "", "CLIENT_AGREEMENT", "CLIENT_KET_PASSWORD", "CLIENT_KEY_KEYSTORE", "CLIENT_KEY_MANAGER", "CLIENT_TRUST_KEYSTORE", "CLIENT_TRUST_MANAGER", "CLIENT_TRUST_PASSWORD", "DEBUG", "", "getDEBUG", "()Z", "setDEBUG", "(Z)V", "DEFAULT_DNS_API_HOST", "DEV_DNS_API_HOST", "ENCONDING", "INTELLIGENT_RECOMMEND", "PRE_DNS_API_HOST", "PRODUCT_DNS_API_HOST", "PROP_DEBUG_SWITCH", "ROOT_TRUST", "Service_PHONE", "UCS_GET_WEATHER", "UCS_HOST_URL", "UCS_TEST_HOST", CommonKt.auth_status, "emptyString", "timestamp", "", "getTimestamp", "()J", "setTimestamp", "(J)V", "checkHexStr", "sHex", "checkPermission", "context", "Landroid/content/Context;", "permission", "formatTime", "time", "getCurrentRunEvn", "getDefaultAPIDNSHost", "getHardWareVersion", "getPackageName", "getSign", "md5Str", "getSoftVersion", "sha256", "str", "toHex", "byteArray", "", "verifyStoragePermissions", "authsdk_release"}, k = 2, mv = {1, 1, 16})
/* loaded from: classes2.dex */
public final class CommonKt {
    public static final String ACCESS_TOKEN_URL = "api/hu/2.0/getAccessToken";
    public static final String CLIENT_AGREEMENT = "TLS";
    public static final String CLIENT_KET_PASSWORD = "123456";
    public static final String CLIENT_KEY_KEYSTORE = "JKS";
    public static final String CLIENT_KEY_MANAGER = "X509";
    public static final String CLIENT_TRUST_KEYSTORE = "BKS";
    public static final String CLIENT_TRUST_MANAGER = "X509";
    public static final String CLIENT_TRUST_PASSWORD = "123456";
    private static boolean DEBUG = false;
    public static final String DEFAULT_DNS_API_HOST = "https://incall.changan.com.cn/";
    public static final String DEV_DNS_API_HOST = "https://dev-incall.changan.com.cn/";
    public static final String ENCONDING = "utf-8";
    public static final String INTELLIGENT_RECOMMEND = "huapi/api/hu/2.0/intelligent_recommend";
    public static final String PRE_DNS_API_HOST = "https://pre-incall.changan.com.cn/";
    public static final String PRODUCT_DNS_API_HOST = "https://incall.changan.com.cn/";
    public static final String PROP_DEBUG_SWITCH = "persist.wt.debug";
    public static final String ROOT_TRUST = "ca.bks";
    public static final String Service_PHONE = "api/hu/2.0/getServicePhone";
    public static final String UCS_GET_WEATHER = "appserver/api/hu/2.0/getWeatherInfo";
    public static final String UCS_HOST_URL = "api/hu/2.0/getHost";
    public static final String UCS_TEST_HOST = "ucs_test_host";
    public static final String auth_status = "auth_status";
    public static final String emptyString = "";
    private static long timestamp;

    public static final boolean getDEBUG() {
        return DEBUG;
    }

    public static final void setDEBUG(boolean z) {
        DEBUG = z;
    }

    public static final void setTimestamp(long j) {
        timestamp = j;
    }

    public static final long getTimestamp() {
        return System.currentTimeMillis();
    }

    public static final String getSign(String md5Str, long j) {
        Intrinsics.checkParameterIsNotNull(md5Str, "md5Str");
        StringBuilder sb = new StringBuilder();
        String upperCase = md5Str.toUpperCase();
        Intrinsics.checkExpressionValueIsNotNull(upperCase, "(this as java.lang.String).toUpperCase()");
        sb.append(upperCase);
        sb.append(j);
        return sha256(sb.toString());
    }

    public static final String sha256(String str) {
        Intrinsics.checkParameterIsNotNull(str, "str");
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            Intrinsics.checkExpressionValueIsNotNull(messageDigest, "MessageDigest.getInstance(\"SHA-256\")");
            Charset forName = Charset.forName(Key.STRING_CHARSET_NAME);
            Intrinsics.checkExpressionValueIsNotNull(forName, "Charset.forName(charsetName)");
            byte[] bytes = str.getBytes(forName);
            Intrinsics.checkExpressionValueIsNotNull(bytes, "(this as java.lang.String).getBytes(charset)");
            messageDigest.update(bytes);
            byte[] digest = messageDigest.digest();
            Intrinsics.checkExpressionValueIsNotNull(digest, "messageDigest.digest()");
            return toHex(digest);
        } catch (NoSuchAlgorithmException e2) {
            e2.printStackTrace();
            return "";
        }
    }

    public static final String toHex(byte[] byteArray) {
        Intrinsics.checkParameterIsNotNull(byteArray, "byteArray");
        StringBuilder sb = new StringBuilder();
        for (byte b : byteArray) {
            String hexString = Integer.toHexString(b & UByte.MAX_VALUE);
            if (hexString.length() == 1) {
                sb.append(MBConfigConstant.MB_X50_LARGE_SCREEN_SIZE_128);
                sb.append(hexString);
            } else {
                sb.append(hexString);
            }
        }
        String sb2 = sb.toString();
        Intrinsics.checkExpressionValueIsNotNull(sb2, "toString()");
        Intrinsics.checkExpressionValueIsNotNull(sb2, "with(StringBuilder()) {\n…\n        toString()\n    }");
        return sb2;
    }

    public static final String formatTime(long j) {
        String format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(Long.valueOf(j));
        Intrinsics.checkExpressionValueIsNotNull(format, "template.format(time)");
        return format;
    }

    public static final boolean checkPermission(Context context, String permission) {
        Intrinsics.checkParameterIsNotNull(context, "context");
        Intrinsics.checkParameterIsNotNull(permission, "permission");
        int checkSelfPermission = ContextCompat.checkSelfPermission(context, permission);
        return checkSelfPermission != -1 && checkSelfPermission == 0;
    }

    public static final String getPackageName(Context context) {
        Intrinsics.checkParameterIsNotNull(context, "context");
        try {
            String str = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).packageName;
            Intrinsics.checkExpressionValueIsNotNull(str, "packageInfo.packageName");
            return str;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static final String getCurrentRunEvn(Context context) {
        Intrinsics.checkParameterIsNotNull(context, "context");
        String string = Settings.Global.getString(context.getContentResolver(), UCS_TEST_HOST);
        return string != null ? string : MBConfigConstant.MB_X50_LARGE_SCREEN_SIZE_128;
    }

    /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
    /* JADX WARN: Failed to find 'out' block for switch in B:7:0x0032. Please report as an issue. */
    public static final String getDefaultAPIDNSHost(Context context) {
        Intrinsics.checkParameterIsNotNull(context, "context");
        String currentRunEvn = getCurrentRunEvn(context);
        LogKt.d("Default Host URL " + currentRunEvn);
        if (TextUtils.isEmpty(currentRunEvn)) {
            LogKt.d("默认正式环境: https://incall.changan.com.cn/api/hu/2.0/getHost");
            return "https://incall.changan.com.cn/api/hu/2.0/getHost";
        }
        switch (currentRunEvn.hashCode()) {
            case 48:
                if (currentRunEvn.equals(MBConfigConstant.MB_X50_LARGE_SCREEN_SIZE_128)) {
                    LogKt.d("生产环境: https://incall.changan.com.cn/api/hu/2.0/getHost");
                    return "https://incall.changan.com.cn/api/hu/2.0/getHost";
                }
                LogKt.d("默认正式环境: https://incall.changan.com.cn//api/hu/2.0/getHost");
                return "https://incall.changan.com.cn//api/hu/2.0/getHost";
            case 49:
                if (currentRunEvn.equals("1")) {
                    LogKt.d("预生产环境: https://pre-incall.changan.com.cn/api/hu/2.0/getHost");
                    return "https://pre-incall.changan.com.cn/api/hu/2.0/getHost";
                }
                LogKt.d("默认正式环境: https://incall.changan.com.cn//api/hu/2.0/getHost");
                return "https://incall.changan.com.cn//api/hu/2.0/getHost";
            case 50:
                if (currentRunEvn.equals("2")) {
                    LogKt.d("测试环境: https://dev-incall.changan.com.cn/api/hu/2.0/getHost");
                    return "https://dev-incall.changan.com.cn/api/hu/2.0/getHost";
                }
                LogKt.d("默认正式环境: https://incall.changan.com.cn//api/hu/2.0/getHost");
                return "https://incall.changan.com.cn//api/hu/2.0/getHost";
            default:
                LogKt.d("默认正式环境: https://incall.changan.com.cn//api/hu/2.0/getHost");
                return "https://incall.changan.com.cn//api/hu/2.0/getHost";
        }
    }

    public static final boolean verifyStoragePermissions(Context context) {
        Intrinsics.checkParameterIsNotNull(context, "context");
        int checkSelfPermission = context.checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE");
        return checkSelfPermission != -1 && checkSelfPermission == 0;
    }

    public static final String getSoftVersion() {
        String str = Build.VERSION.RELEASE;
        Intrinsics.checkExpressionValueIsNotNull(str, "Build.VERSION.RELEASE");
        return str;
    }

    public static final String getHardWareVersion(Context context) {
        Intrinsics.checkParameterIsNotNull(context, "context");
        String string = Settings.System.getString(context.getContentResolver(), "HW_VERSION");
        return string != null ? string : "";
    }

    public static final boolean checkHexStr(String sHex) {
        Intrinsics.checkParameterIsNotNull(sHex, "sHex");
        String str = sHex;
        int length = str.length() - 1;
        int i = 0;
        boolean z = false;
        while (i <= length) {
            boolean z2 = str.charAt(!z ? i : length) <= ' ';
            if (z) {
                if (!z2) {
                    break;
                }
                length--;
            } else if (z2) {
                i++;
            } else {
                z = true;
            }
        }
        String replace$default = StringsKt.replace(str.subSequence(i, length + 1).toString(), " ", "", false);
        Locale locale = Locale.US;
        Intrinsics.checkExpressionValueIsNotNull(locale, "Locale.US");
        if (replace$default == null) {
            throw new TypeCastException("null cannot be cast to non-null type java.lang.String");
        }
        String upperCase = replace$default.toUpperCase(locale);
        Intrinsics.checkExpressionValueIsNotNull(upperCase, "(this as java.lang.String).toUpperCase(locale)");
        int length2 = upperCase.length();
        if (length2 <= 1 || length2 % 2 != 0) {
            return false;
        }
        int i2 = 0;
        while (i2 < length2) {
            int i3 = i2 + 1;
            if (upperCase == null) {
                throw new TypeCastException("null cannot be cast to non-null type java.lang.String");
            }
            String substring = upperCase.substring(i2, i3);
            Intrinsics.checkExpressionValueIsNotNull(substring, "(this as java.lang.Strin…ing(startIndex, endIndex)");
            if (!StringsKt.contains((CharSequence) " ", (CharSequence) substring, false)) {
                return false;
            }
            i2 = i3;
        }
        return true;
    }
}
