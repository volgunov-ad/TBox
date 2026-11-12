package vad.dashing.vdprobe;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;

/** Content that runs on the virtual display. The counter stops in onPause. */
public class GuestActivity extends Activity {
    private Stage stage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        stage = new Stage(this);
        setContentView(stage);
    }

    @Override
    protected void onResume() {
        super.onResume();
        stage.running = true;
        stage.postInvalidateOnAnimation();
    }

    @Override
    protected void onPause() {
        stage.running = false;
        super.onPause();
    }

    private static final class Stage extends View {
        boolean running;
        int frame;
        int taps;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Stage(Activity activity) {
            super(activity);
            setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, android.view.MotionEvent event) {
                    if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                        taps++;
                        invalidate();
                    }
                    return true;
                }
            });
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            canvas.drawColor(Color.BLACK);
            if (w <= 0 || h <= 0) {
                return;
            }
            int i;
            for (i = 0; i < 24; i++) {
                paint.setColor(Color.HSVToColor(new float[]{(frame + i * 15) % 360, 0.8f, 1f}));
                float x = (frame * 6 + i * 70) % w;
                float y = (float) (h * (0.5 + 0.35 * Math.sin((frame + i * 8) / 18.0)));
                canvas.drawCircle(x, y, 28, paint);
            }
            paint.setColor(Color.WHITE);
            paint.setTextSize(64);
            String state = running ? "идёт" : "пауза";
            int display = ((Activity) getContext()).getWindowManager().getDefaultDisplay().getDisplayId();
            canvas.drawText("дисплей " + display + "  кадр " + frame + "  " + state, 32, 80, paint);
            canvas.drawText("нажатий " + taps, 32, 160, paint);
            if (running) {
                frame++;
                postInvalidateOnAnimation();
            }
        }
    }
}
