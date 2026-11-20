package vad.dashing.dclinstall;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Wallpaper bytes live in device-protected storage. That directory is readable
 * before the user is unlocked, which is when the boot wallpaper has to draw.
 */
final class WallpaperStore {
    static final String MODE_IMAGE = "image";
    static final String MODE_BLACK = "black";
    private static final String MODE_FILE = "wallpaper_mode.txt";
    private static final String IMAGE_FILE = "wallpaper.jpg";
    private static final int MAX_EDGE = 1920;

    private WallpaperStore() {}

    static File imageFile(Context context) {
        return new File(directory(context), IMAGE_FILE);
    }

    static File modeFile(Context context) {
        return new File(directory(context), MODE_FILE);
    }

    static void writeBlack(Context context) throws Exception {
        File dir = directory(context);
        imageFile(context).delete();
        writeMode(dir, MODE_BLACK);
    }

    static void writeImageFile(Context context, String path) throws Exception {
        Bitmap bitmap = decodeFile(path);
        if (bitmap == null) throw new IllegalStateException("Картинка не читается");
        try {
            bitmap = rotate(bitmap, exifDegrees(path));
            writeJpeg(context, bitmap);
        } finally {
            if (!bitmap.isRecycled()) bitmap.recycle();
        }
    }

    static void writeImageAsset(Context context, String asset) throws Exception {
        InputStream in = context.getAssets().open(asset);
        File dir = directory(context);
        File raw = new File(dir, "wallpaper_raw.jpg");
        try {
            FileOutputStream out = new FileOutputStream(raw);
            try {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
        try {
            writeImageFile(context, raw.getAbsolutePath());
        } finally {
            raw.delete();
        }
    }

    private static void writeJpeg(Context context, Bitmap bitmap) throws Exception {
        File dir = directory(context);
        File tmp = new File(dir, "wallpaper.jpg.tmp");
        FileOutputStream out = new FileOutputStream(tmp);
        try {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) {
                throw new IllegalStateException("Не удалось сохранить картинку");
            }
        } finally {
            out.close();
        }
        File image = imageFile(context);
        if (image.exists() && !image.delete()) {
            throw new IllegalStateException("Не удалось заменить картинку обоев");
        }
        if (!tmp.renameTo(image)) {
            throw new IllegalStateException("Не удалось записать картинку обоев");
        }
        writeMode(dir, MODE_IMAGE);
    }

    private static File directory(Context context) {
        File dir = context.createDeviceProtectedStorageContext().getFilesDir();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Нет папки для обоев");
        }
        return dir;
    }

    private static void writeMode(File dir, String mode) throws Exception {
        FileOutputStream out = new FileOutputStream(new File(dir, MODE_FILE));
        try {
            out.write(mode.getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    private static Bitmap decodeFile(String path) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);
        if (bitmap == null) return null;
        int edge = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (edge <= MAX_EDGE) return bitmap;
        float scale = MAX_EDGE / (float) edge;
        Bitmap scaled = Bitmap.createScaledBitmap(
                bitmap,
                Math.max(1, Math.round(bitmap.getWidth() * scale)),
                Math.max(1, Math.round(bitmap.getHeight() * scale)),
                true);
        if (scaled != bitmap) bitmap.recycle();
        return scaled;
    }

    private static int sampleSize(int width, int height) {
        int sample = 1;
        while (width / sample > MAX_EDGE || height / sample > MAX_EDGE) sample *= 2;
        return sample;
    }

    private static int exifDegrees(String path) {
        try {
            ExifInterface exif = new ExifInterface(path);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) return 90;
            if (orientation == ExifInterface.ORIENTATION_ROTATE_180) return 180;
            if (orientation == ExifInterface.ORIENTATION_ROTATE_270) return 270;
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static Bitmap rotate(Bitmap source, int degrees) {
        if (degrees == 0) return source;
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(
                source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        if (rotated != source) source.recycle();
        return rotated;
    }
}
