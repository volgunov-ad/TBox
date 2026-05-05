package com.mengbo.mbconfig.utils;

import android.util.Log;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/* loaded from: classes2.dex */
public class CMDUtil {
    private static final String TAG = CMDUtil.class.getSimpleName();

    public static void setProp(String str, String str2) {
        try {
            execCmd("setprop " + str + " " + str2);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getProp(String str) {
        String str2 = null;
        try {
            str2 = execCmd("getprop " + str);
            String str3 = TAG;
            Log.i(str3, "getProp: result = " + str2);
            return str2;
        } catch (Exception e) {
            e.printStackTrace();
            return str2;
        }
    }

    public static String execCmd(String str) throws Exception {
        Process process;
        BufferedReader bufferedReader;
        Log.d(TAG, "execCmd: " + str);
        StringBuilder sb = new StringBuilder();
        BufferedReader bufferedReader2 = null;
        Throwable th5;
        try {
            process = Runtime.getRuntime().exec(str);
            try {
                process.waitFor();
                BufferedReader bufferedReader3 = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                try {
                    bufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
                    while (true) {
                        try {
                            String readLine = bufferedReader3.readLine();
                            if (readLine == null) {
                                break;
                            }
                            sb.append(readLine);
                            sb.append('\n');
                        } catch (Throwable th) {
                            th = th;
                            bufferedReader2 = bufferedReader3;
                            closeStream(bufferedReader2);
                            closeStream(bufferedReader);
                            if (process != null) {
                                process.destroy();
                            }
                            throw th;
                        }
                    }
                    while (true) {
                        String readLine2 = bufferedReader.readLine();
                        if (readLine2 == null) {
                            break;
                        }
                        sb.append(readLine2);
                        sb.append('\n');
                    }
                    closeStream(bufferedReader3);
                    closeStream(bufferedReader);
                    if (process != null) {
                        process.destroy();
                    }
                    Log.d(TAG, "execCmd: " + sb.toString());
                    return sb.toString();
                } catch (Throwable th2) {
                    th5 = th2;
                    bufferedReader = null;
                }
            } catch (Throwable th3) {
                th5 = th3;
                bufferedReader = null;
            }
        } catch (Throwable th4) {
            th5 = th4;
            process = null;
            bufferedReader = null;
        }
        return th5.getMessage();
    }

    private static void closeStream(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception unused) {
            }
        }
    }
}