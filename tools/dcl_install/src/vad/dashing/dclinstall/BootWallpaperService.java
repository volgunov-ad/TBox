package vad.dashing.dclinstall;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;
import java.io.File;
import java.io.FileInputStream;

/**
 * Direct-boot wallpaper. SystemUI's ImageWallpaper is gone while SystemUI is hidden,
 * so this service has to draw before the user is unlocked.
 */
public class BootWallpaperService extends WallpaperService {
    private static final java.util.List<BootEngine> ENGINES = new java.util.ArrayList<BootEngine>();

    static void reload() {
        synchronized (ENGINES) {
            for (int i = 0; i < ENGINES.size(); i++) {
                ENGINES.get(i).reload();
            }
        }
    }

    @Override
    public Engine onCreateEngine() {
        return new BootEngine();
    }

    private final class BootEngine extends Engine {
        private Bitmap bitmap;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setTouchEventsEnabled(false);
            setOffsetNotificationsEnabled(false);
            synchronized (ENGINES) {
                ENGINES.add(this);
            }
            bitmap = load();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            draw(holder);
        }

        @Override
        public void onSurfaceRedrawNeeded(SurfaceHolder holder) {
            super.onSurfaceRedrawNeeded(holder);
            draw(holder);
        }

        @Override
        public void onDestroy() {
            synchronized (ENGINES) {
                ENGINES.remove(this);
            }
            if (bitmap != null) {
                bitmap.recycle();
                bitmap = null;
            }
            super.onDestroy();
        }

        private void reload() {
            Bitmap next = load();
            Bitmap previous = bitmap;
            bitmap = next;
            if (previous != null && previous != next) previous.recycle();
            draw(getSurfaceHolder());
        }

        private void draw(SurfaceHolder holder) {
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas == null) return;
                canvas.drawColor(Color.BLACK);
                Bitmap image = bitmap;
                if (image == null || image.isRecycled()) return;
                float scale = Math.max(
                        canvas.getWidth() / (float) image.getWidth(),
                        canvas.getHeight() / (float) image.getHeight());
                float dw = image.getWidth() * scale;
                float dh = image.getHeight() * scale;
                float left = (canvas.getWidth() - dw) / 2f;
                float top = (canvas.getHeight() - dh) / 2f;
                canvas.drawBitmap(image, null, new RectF(left, top, left + dw, top + dh), paint);
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas);
            }
        }

        private Bitmap load() {
            try {
                File mode = WallpaperStore.modeFile(BootWallpaperService.this);
                File image = WallpaperStore.imageFile(BootWallpaperService.this);
                if (!WallpaperStore.MODE_IMAGE.equals(readMode(mode)) || !image.isFile()) return null;
                return BitmapFactory.decodeFile(image.getAbsolutePath());
            } catch (Throwable ignored) {
                return null;
            }
        }

        private String readMode(File file) throws Exception {
            if (!file.isFile()) return "";
            FileInputStream in = new FileInputStream(file);
            try {
                byte[] buf = new byte[32];
                int n = in.read(buf);
                if (n <= 0) return "";
                return new String(buf, 0, n, "UTF-8").trim();
            } finally {
                in.close();
            }
        }
    }
}
