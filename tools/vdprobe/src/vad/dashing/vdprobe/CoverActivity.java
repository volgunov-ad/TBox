package vad.dashing.vdprobe;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

/** Opaque blank window used to cover a parked freeform task. */
public class CoverActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View cover = new View(this);
        cover.setBackgroundColor(Color.BLACK);
        setContentView(cover);
    }
}
