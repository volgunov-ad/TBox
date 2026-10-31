package com.adayo.carplay.view.patch;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceView;
import com.adayo.carplay.view.ui.activity.MainActivity;
import java.util.Collection;

public final class WindowSurface extends SurfaceView {
    private static final String REDIRECTED = "com.adayo.carplay.view.patch.REDIRECTED";
    private static volatile long pendingSurfaceId = 0L;

    private boolean tracking;
    private int lastX;
    private int lastY;

    public WindowSurface(Context context, AttributeSet attributes) {
        super(context, attributes);
    }

    private static int findWindowDisplayId(Context context) {
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

    private static void startOnDisplay(Context context, Intent sourceIntent, int displayId) {
        Intent target = sourceIntent != null ? new Intent(sourceIntent) : new Intent(context, MainActivity.class);
        target.setClassName("com.adayo.carplay.view", "com.adayo.carplay.view.ui.activity.MainActivity");
        target.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        target.putExtra(REDIRECTED, true);
        long sid = pendingSurfaceId;
        if (sid != 0L && target.getLongExtra("surfaceId", 0L) == 0L) {
            target.putExtra("surfaceId", sid);
        }
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(displayId);
        context.getApplicationContext().startActivity(target, options.toBundle());
    }

    public static boolean interceptOnCreate(MainActivity activity) {
        int displayId = activity.getWindowManager().getDefaultDisplay().getDisplayId();
        if (displayId > 0) {
            return false;
        }
        Intent intent = activity.getIntent();
        if (intent != null && intent.getBooleanExtra(REDIRECTED, false)) {
            Log.e("CPViewPatch", "interceptOnCreate: redirect already attempted, staying on display=0");
            return false;
        }
        int targetDisplay = findWindowDisplayId(activity);
        if (targetDisplay <= 0) {
            Log.w("CPViewPatch", "interceptOnCreate: virtual display not ready, allowing display=0");
            return false;
        }
        long sid = 0L;
        if (intent != null) {
            sid = intent.getLongExtra("surfaceId", 0L);
            if (sid != 0L) {
                pendingSurfaceId = sid;
            }
        }
        activity.setNeedTake(false);
        activity.finish();
        try {
            startOnDisplay(activity.getApplicationContext(), intent, targetDisplay);
            Log.d("CPViewPatch", "interceptOnCreate display=0 -> display=" + targetDisplay + " surfaceId=" + sid);
        } catch (RuntimeException error) {
            Log.e("CPViewPatch", "interceptOnCreate redirect failed", error);
        }
        return true;
    }

    public static long resolveSurfaceId(long intentSurfaceId) {
        if (intentSurfaceId != 0L) {
            pendingSurfaceId = 0L;
            return intentSurfaceId;
        }
        long saved = pendingSurfaceId;
        pendingSurfaceId = 0L;
        if (saved != 0L) {
            Log.d("CPViewPatch", "resolveSurfaceId restored pending=" + saved);
        }
        return saved;
    }

    public static void startInWindow(Context context, Intent intent, Collection<?> activities) {
        long sid = intent != null ? intent.getLongExtra("surfaceId", 0L) : 0L;
        if (sid != 0L) {
            pendingSurfaceId = sid;
        }
        int displayId = -1;
        for (Object item : activities) {
            Activity activity = (Activity) item;
            if (!activity.isFinishing()) {
                int d = activity.getWindowManager().getDefaultDisplay().getDisplayId();
                if (d > 0) {
                    displayId = d;
                    break;
                }
            }
        }
        if (displayId > 0) {
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(displayId);
            context.startActivity(intent, options.toBundle());
        } else {
            for (Object item : activities) {
                Activity activity = (Activity) item;
                if (!activity.isFinishing()) {
                    if (activity instanceof MainActivity) {
                        ((MainActivity) activity).setNeedTake(false);
                    }
                    activity.finish();
                }
            }
            int targetDisplay = findWindowDisplayId(context);
            if (targetDisplay > 0) {
                startOnDisplay(context, intent, targetDisplay);
                displayId = targetDisplay;
            } else {
                Intent window = new Intent("com.adayo.launcher.LAUNCH_APP");
                window.setPackage("com.adayo.launcher");
                window.putExtra("app_pkg", "com.adayo.carplay.view");
                window.putExtra("app_cls", "com.adayo.carplay.view.ui.activity.MainActivity");
                window.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(window);
                displayId = -2;
            }
        }
        Log.d("CPViewPatch", "startInWindow display=" + displayId + " surfaceId=" + sid);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = MeasureSpec.getSize(heightSpec);
        if (width <= 0 || height <= 0) {
            super.onMeasure(widthSpec, heightSpec);
            return;
        }
        if ((long) width * 1080 > (long) height * 1920) {
            width = height * 1920 / 1080;
        } else {
            height = width * 1080 / 1920;
        }
        setMeasuredDimension(width, height);
        Log.d("CPViewPatch", "fit16x9=" + width + "x" + height);
    }

    public int[] mapTouch(MotionEvent event) {
        int action = event.getActionMasked();
        float x = event.getX() - getLeft();
        float y = event.getY() - getTop();
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return null;
        if (action == MotionEvent.ACTION_DOWN) {
            tracking = x >= 0 && y >= 0 && x < width && y < height;
        }
        if (!tracking) return null;
        int mappedX = Math.max(0, Math.min(1919, (int) (x * 1920 / width)));
        int mappedY = Math.max(0, Math.min(1079, (int) (y * 1080 / height)));
        int pressed;
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            tracking = false;
            pressed = 0;
        } else if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            pressed = 1;
        } else {
            return null;
        }
        if (action == MotionEvent.ACTION_MOVE && mappedX == lastX && mappedY == lastY) return null;
        lastX = mappedX;
        lastY = mappedY;
        Log.d("CPViewPatch", "action=" + action + " local=" + event.getX() + "," + event.getY()
                + " video=" + getLeft() + "," + getTop() + "," + width + "," + height
                + " mapped=" + mappedX + "," + mappedY + " pressed=" + pressed);
        return new int[] {pressed, mappedX, mappedY};
    }
}
