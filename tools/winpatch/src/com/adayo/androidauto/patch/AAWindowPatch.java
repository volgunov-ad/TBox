package com.adayo.androidauto.patch;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import java.lang.ref.WeakReference;

public final class AAWindowPatch {
    private static WeakReference<Activity> current = new WeakReference<Activity>(null);

    public static void track(Activity activity) {
        current = new WeakReference<Activity>(activity);
    }

    private static final String REDIRECTED = "com.adayo.androidauto.patch.REDIRECTED";
    /** Same key the winpatch app writes with `settings put global`. */
    static final String LAYOUT_KEY = "tbox_winpatch_layout";

    /**
     * A10 content rect between the status bar and the bottom bar:
     * DisplayFrames mContent=[0,96][1920,960] → 1920×864.
     * Physical millimetres follow the stock 285×160 panel, scaled to that height
     * (160×864/1080 = 128) so the phone lays the UI out for this rectangle.
     */
    private static final int BARS_WIDTH = 1920;
    private static final int BARS_HEIGHT = 864;
    private static final int BARS_WIDTH_MM = 285;
    private static final int BARS_HEIGHT_MM = 128;
    private static final int WINDOW_WIDTH = 1327;
    private static final int WINDOW_HEIGHT = 865;
    private static final int WINDOW_WIDTH_MM = 197;
    private static final int WINDOW_HEIGHT_MM = 128;
    /** Ignition off. SourceMng then asks for the clock and waits for the door. */
    private static final String ACC_OFF = "com.device.accoff";
    private static final String POWER_OFF = "com.device.poweroff";
    private static final String ACC_ON = "com.device.accon";
    private static final String POWER_ON = "com.device.poweron";
    private static volatile boolean suppressUi;
    private static boolean receiverRegistered;
    /** Sticky ACC_OFF from an earlier stop is delivered inside registerReceiver. Ignore that copy. */
    private static volatile boolean accWatchReady;

    /**
     * While Android Auto is the foreground app, SourceMng cannot enter the stock
     * fake-shutdown (clock until the door opens) and the head unit powers off at once.
     * Leaving the screen on ignition-off restores that wait.
     */
    private static void watchAccOff(Context context) {
        if (receiverRegistered) return;
        receiverRegistered = true;
        final IntentFilter filter = new IntentFilter();
        filter.addAction(ACC_OFF);
        filter.addAction(POWER_OFF);
        filter.addAction(ACC_ON);
        filter.addAction(POWER_ON);
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context app, Intent intent) {
                if (!accWatchReady) return;
                String action = intent == null ? null : intent.getAction();
                if (ACC_ON.equals(action) || POWER_ON.equals(action)) {
                    suppressUi = false;
                    Log.d("AAWindowPatch", "ui allowed " + action);
                    return;
                }
                suppressUi = true;
                Log.d("AAWindowPatch", "release ui " + action);
                releaseUi(app);
            }
        };
        Runnable register = new Runnable() {
            @Override
            public void run() {
                context.getApplicationContext().registerReceiver(receiver, filter);
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            register.run();
            accWatchReady = true;
        } else {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    register.run();
                    accWatchReady = true;
                }
            });
        }
    }

    private static void releaseUi(Context context) {
        Activity activity = current.get();
        if (activity != null && !activity.isFinishing()) {
            activity.finish();
        }
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.setPackage("com.adayo.launcher");
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(home);
        } catch (RuntimeException error) {
            Log.e("AAWindowPatch", "home after acc off failed", error);
        }
    }

    public static boolean barsMode(Context context) {
        try {
            return "bars".equals(Settings.Global.getString(context.getContentResolver(), LAYOUT_KEY));
        } catch (RuntimeException error) {
            Log.e("AAWindowPatch", "layout read failed", error);
            return false;
        }
    }

    public static int videoWidth(Context context) {
        return barsMode(context) ? BARS_WIDTH : WINDOW_WIDTH;
    }

    public static int videoHeight(Context context) {
        return barsMode(context) ? BARS_HEIGHT : WINDOW_HEIGHT;
    }

    public static int videoWidthMm(Context context) {
        return barsMode(context) ? BARS_WIDTH_MM : WINDOW_WIDTH_MM;
    }

    public static int videoHeightMm(Context context) {
        return barsMode(context) ? BARS_HEIGHT_MM : WINDOW_HEIGHT_MM;
    }

    /** Keep the status bar and the bottom bar when the activity is on the main display. */
    public static void keepBars(Activity activity) {
        if (!barsMode(activity)) return;
        if (activity.getWindowManager().getDefaultDisplay().getDisplayId() != 0) return;
        Window window = activity.getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        Log.d("AAWindowPatch", "keepBars");
    }

    private static int windowDisplay(Context context) {
        DisplayManager manager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (manager != null) {
            for (Display display : manager.getDisplays()) {
                if (display.getDisplayId() > 0 && display.getName().startsWith("ActivityViewVirtualDisplay@")) {
                    return display.getDisplayId();
                }
            }
        }
        return -1;
    }

    private static void startOnDisplay(Context context, Intent intent, int display, boolean multipleTask) {
        Intent target = intent != null ? new Intent(intent) : new Intent();
        target.setClassName("com.adayo.androidauto", "com.adayo.androidauto.ui.AAActivity");
        int flags = Intent.FLAG_ACTIVITY_NEW_TASK;
        if (multipleTask) flags |= Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
        target.setFlags(flags);
        target.putExtra(REDIRECTED, true);
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(display);
        context.getApplicationContext().startActivity(target, options.toBundle());
        Log.d("AAWindowPatch", "start display=" + display + " bars=" + barsMode(context));
    }

    private static boolean alreadyRedirected(Activity activity) {
        Intent intent = activity.getIntent();
        return intent != null && intent.getBooleanExtra(REDIRECTED, false);
    }

    public static boolean interceptOnCreate(Activity activity) {
        watchAccOff(activity);
        if (suppressUi) {
            Log.d("AAWindowPatch", "interceptOnCreate suppressed, acc off");
            activity.finish();
            return true;
        }
        int displayId = activity.getWindowManager().getDefaultDisplay().getDisplayId();
        if (barsMode(activity)) {
            if (displayId == 0) {
                keepBars(activity);
                track(activity);
                return false;
            }
            if (alreadyRedirected(activity)) {
                Log.e("AAWindowPatch", "interceptOnCreate: bars redirect did not reach the main display");
                track(activity);
                return false;
            }
            try {
                activity.finish();
                startOnDisplay(activity.getApplicationContext(), activity.getIntent(), 0, false);
                Log.d("AAWindowPatch", "interceptOnCreate display=" + displayId + " -> main");
                return true;
            } catch (RuntimeException error) {
                Log.e("AAWindowPatch", "interceptOnCreate bars redirect failed", error);
                track(activity);
                return false;
            }
        }
        if (displayId > 0) {
            track(activity);
            return false;
        }
        Intent intent = activity.getIntent();
        if (intent != null && intent.getBooleanExtra(REDIRECTED, false)) {
            Log.e("AAWindowPatch", "interceptOnCreate: redirect did not reach window; refusing retry");
            track(activity);
            return false;
        }
        int targetDisplay = windowDisplay(activity);
        if (targetDisplay <= 0) {
            Log.w("AAWindowPatch", "interceptOnCreate: launcher window unavailable; allowing display=0");
            track(activity);
            return false;
        }
        try {
            activity.finish();
            startOnDisplay(activity.getApplicationContext(), intent, targetDisplay, true);
            Log.d("AAWindowPatch", "interceptOnCreate display=0 -> display=" + targetDisplay);
            return true;
        } catch (RuntimeException error) {
            Log.e("AAWindowPatch", "interceptOnCreate redirect failed", error);
            track(activity);
            return false;
        }
    }

    public static void ensureWindow(Activity activity) {
        watchAccOff(activity);
        if (suppressUi) {
            if (!activity.isFinishing()) activity.finish();
            return;
        }
        int display = activity.getWindowManager().getDefaultDisplay().getDisplayId();
        Log.d("AAWindowPatch", "activity display=" + display + " bars=" + barsMode(activity));
        if (barsMode(activity)) {
            if (display == 0) {
                keepBars(activity);
                return;
            }
            if (activity.isFinishing() || alreadyRedirected(activity)) {
                Log.e("AAWindowPatch", "bars redirect did not reach the main display");
                return;
            }
            try {
                activity.finish();
                startOnDisplay(activity.getApplicationContext(), activity.getIntent(), 0, false);
            } catch (RuntimeException error) {
                Log.e("AAWindowPatch", "bars redirect failed", error);
            }
            return;
        }
        if (display != 0 || activity.isFinishing()) return;
        if (alreadyRedirected(activity)) {
            Log.e("AAWindowPatch", "redirect did not reach window; refusing retry");
            return;
        }
        int targetDisplay = windowDisplay(activity);
        if (targetDisplay < 0) {
            Log.w("AAWindowPatch", "launcher window unavailable; refusing redirect");
            return;
        }
        try {
            activity.finish();
            startOnDisplay(activity.getApplicationContext(), activity.getIntent(), targetDisplay, true);
        } catch (RuntimeException error) {
            Log.e("AAWindowPatch", "window redirect failed", error);
        }
    }

    public static void start(Context context, Intent intent) {
        watchAccOff(context);
        if (suppressUi) {
            Log.d("AAWindowPatch", "start suppressed, acc off");
            return;
        }
        if (barsMode(context)) {
            Activity existing = current.get();
            if (existing != null && !existing.isFinishing()) {
                int d = existing.getWindowManager().getDefaultDisplay().getDisplayId();
                if (d != 0) existing.finish();
            }
            startOnDisplay(context, intent, 0, false);
            return;
        }
        Activity existing = current.get();
        if (existing != null && !existing.isFinishing()) {
            int d = existing.getWindowManager().getDefaultDisplay().getDisplayId();
            if (d > 0) {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLaunchDisplayId(d);
                context.getApplicationContext().startActivity(intent, options.toBundle());
                Log.d("AAWindowPatch", "start existing window display=" + d);
                return;
            }
            existing.finish();
        }
        int display = windowDisplay(context);
        if (display > 0) {
            startOnDisplay(context, intent, display, true);
            return;
        }
        Intent window = new Intent("com.adayo.launcher.LAUNCH_APP");
        window.setPackage("com.adayo.launcher");
        window.putExtra("app_pkg", "com.adayo.androidauto");
        window.putExtra("app_cls", "com.adayo.androidauto.ui.AAActivity");
        window.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(window);
        Log.d("AAWindowPatch", "start via launcher");
    }

    /**
     * With native widthMargin configured for the 1327x865 window aspect ratio, the phone renders
     * the active content into the center of the 16:9 frame and places letterbox bars on the left
     * and right. Expanding the SurfaceView horizontally so its height is the full 865 px and its
     * width is height * vw / vh (1536x865 at left=-104) pushes those side bars outside the clipped
     * parent container, filling the entire 1327x865 window with zero distortion and zero bars.
     */
    public static Rect viewport(SurfaceView surface, Rect video) {
        View parent = (View) surface.getParent();
        int width = parent.getWidth();
        int height = parent.getHeight();
        int vw = video.width();
        int vh = video.height();
        if (width <= 0 || height <= 0 || vw <= 0 || vh <= 0) {
            return new Rect(0, 0, Math.max(1, surface.getWidth()), Math.max(1, surface.getHeight()));
        }
        int fullWidth = (int) ((long) height * vw / vh);
        fullWidth = (fullWidth / 2) * 2;
        if (fullWidth < width) {
            fullWidth = width;
        }
        int left = (width - fullWidth) / 2;
        Rect result = new Rect(left, 0, left + fullWidth, height);
        Log.d("AAWindowPatch", "parent=" + width + "x" + height + " video=" + video + " viewport=" + result);
        return result;
    }

    /**
     * Both left and right margins must be negative so RelativeLayout's available width
     * is parentWidth - leftMargin - rightMargin = full 16:9 width (1536), not 1327+104.
     */
    public static void applyViewport(SurfaceView surface, Rect viewport) {
        ViewGroup parent = (ViewGroup) surface.getParent();
        parent.setClipChildren(true);
        parent.setClipToPadding(true);
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) surface.getLayoutParams();
        int parentW = parent.getWidth();
        int parentH = parent.getHeight();
        lp.width = viewport.width();
        lp.height = viewport.height();
        lp.leftMargin = viewport.left;
        lp.topMargin = viewport.top;
        lp.rightMargin = parentW - viewport.right;
        lp.bottomMargin = parentH - viewport.bottom;
        surface.setLayoutParams(lp);
        Log.d("AAWindowPatch", "applyViewport parent=" + parentW + "x" + parentH
                + " viewport=" + viewport
                + " margins=" + lp.leftMargin + "," + lp.topMargin + "," + lp.rightMargin + "," + lp.bottomMargin);
    }

    /**
     * SurfaceView local coordinates include the off-screen side bars (left is negative).
     * Scaling onto the full video frame lands inside the phone's widthMargin. The phone lays
     * the UI out from the content origin, so half of that margin is subtracted — the same
     * inset DimensionUtils.calSreen reports as mWidthMargin for 1327x865.
     */
    public static MotionEvent touch(SurfaceView surface, Rect video, MotionEvent source) {
        MotionEvent event = MotionEvent.obtain(source);
        int width = surface.getWidth();
        int height = surface.getHeight();
        int videoW = video.width();
        int videoH = video.height();
        if (width <= 0 || height <= 0 || videoW <= 0 || videoH <= 0) return event;
        View parent = (View) surface.getParent();
        int parentW = parent.getWidth();
        int parentH = parent.getHeight();
        float marginX = 0f;
        float marginY = 0f;
        if (parentW > 0 && parentH > 0) {
            float scale = Math.max((float) parentW / videoW, (float) parentH / videoH);
            marginX = Math.max(0f, videoW - parentW / scale);
            marginY = Math.max(0f, videoH - parentH / scale);
        }
        Matrix matrix = new Matrix();
        matrix.setScale((float) videoW / width, (float) videoH / height);
        matrix.postTranslate(-marginX / 2f, -marginY / 2f);
        event.transform(matrix);
        Log.d("AAWindowPatch", "action=" + source.getActionMasked() + " local=" + source.getX() + "," + source.getY()
                + " surface=" + width + "x" + height
                + " margin=" + marginX + "," + marginY
                + " mapped=" + event.getX() + "," + event.getY());
        return event;
    }
}
