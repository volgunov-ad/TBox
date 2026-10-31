package com.adayo.androidauto.patch;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import java.lang.ref.WeakReference;

public final class AAWindowPatch {
    private static WeakReference<Activity> current = new WeakReference<Activity>(null);

    public static void track(Activity activity) {
        current = new WeakReference<Activity>(activity);
    }

    private static final String REDIRECTED = "com.adayo.androidauto.patch.REDIRECTED";

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

    private static void startOnDisplay(Context context, Intent intent, int display) {
        Intent target = intent != null ? new Intent(intent) : new Intent();
        target.setClassName("com.adayo.androidauto", "com.adayo.androidauto.ui.AAActivity");
        target.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        target.putExtra(REDIRECTED, true);
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(display);
        context.getApplicationContext().startActivity(target, options.toBundle());
        Log.d("AAWindowPatch", "start display=" + display);
    }

    public static boolean interceptOnCreate(Activity activity) {
        int displayId = activity.getWindowManager().getDefaultDisplay().getDisplayId();
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
            startOnDisplay(activity.getApplicationContext(), intent, targetDisplay);
            Log.d("AAWindowPatch", "interceptOnCreate display=0 -> display=" + targetDisplay);
            return true;
        } catch (RuntimeException error) {
            Log.e("AAWindowPatch", "interceptOnCreate redirect failed", error);
            track(activity);
            return false;
        }
    }

    public static void ensureWindow(Activity activity) {
        int display = activity.getWindowManager().getDefaultDisplay().getDisplayId();
        Log.d("AAWindowPatch", "activity display=" + display);
        if (display != 0 || activity.isFinishing()) return;
        if (activity.getIntent().getBooleanExtra(REDIRECTED, false)) {
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
            startOnDisplay(activity.getApplicationContext(), activity.getIntent(), targetDisplay);
        } catch (RuntimeException error) {
            Log.e("AAWindowPatch", "window redirect failed", error);
        }
    }

    public static void start(Context context, Intent intent) {
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
            startOnDisplay(context, intent, display);
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
