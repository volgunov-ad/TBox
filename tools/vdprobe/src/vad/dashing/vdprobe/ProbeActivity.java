package vad.dashing.vdprobe;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Method;

/** Host that embeds the guest with the platform ActivityView, same idea as the stock launcher. */
public class ProbeActivity extends Activity {
    private TextView status;
    private View activityView;
    private boolean started;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        status = new TextView(this);
        status.setTextSize(26);
        status.setTextColor(Color.BLACK);
        status.setPadding(24, 16, 24, 8);
        root.addView(status, match(), wrap());

        Button close = new Button(this);
        close.setText("Закрыть");
        close.setTextSize(22);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                releaseView();
                finish();
            }
        });
        root.addView(close, wrap(), wrap());

        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(12, 12, 12, 12);
        frame.setBackgroundColor(Color.rgb(180, 40, 40));
        root.addView(frame, new LinearLayout.LayoutParams(match(), 0, 1f));
        setContentView(root);

        try {
            Class<?> type = Class.forName("android.app.ActivityView");
            activityView = (View) type.getConstructor(android.content.Context.class).newInstance(this);
            frame.addView(activityView, new FrameLayout.LayoutParams(match(), match()));
            setStatus("ActivityView создан. Жду размер, затем запущу гостя.");
            activityView.post(new Runnable() {
                @Override
                public void run() {
                    launchGuest();
                }
            });
        } catch (Throwable error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            setStatus("ActivityView не создан: " + cause.getClass().getSimpleName() + " " + cause.getMessage());
        }
    }

    private void launchGuest() {
        if (started || activityView == null) {
            return;
        }
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(this, GuestActivity.class));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        try {
            Method start = activityView.getClass().getMethod("startActivity", Intent.class);
            start.invoke(activityView, intent);
            started = true;
            setStatus("Гость запущен через ActivityView. Нажмите чёрное поле: счётчик «нажатий» должен расти.");
        } catch (Throwable error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            setStatus("startActivity: " + cause.getClass().getSimpleName() + " " + cause.getMessage());
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!started) {
                        launchGuest();
                    }
                }
            }, 400);
        }
    }

    private void releaseView() {
        if (activityView == null) {
            return;
        }
        try {
            activityView.getClass().getMethod("release").invoke(activityView);
        } catch (Throwable ignored) {
            // The display goes away with the host process.
        }
    }

    @Override
    protected void onDestroy() {
        releaseView();
        super.onDestroy();
    }

    private void setStatus(String text) {
        status.setText(text);
    }

    private static int match() {
        return ViewGroup.LayoutParams.MATCH_PARENT;
    }

    private static int wrap() {
        return ViewGroup.LayoutParams.WRAP_CONTENT;
    }
}
