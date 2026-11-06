package vad.dashing.dclinstall;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Lists folders and APK or image files on internal storage and USB volumes. */
public class ApkPickerActivity extends ListActivity {
    static final String EXTRA_IMAGES = "images";

    private final List<File> roots = new ArrayList<File>();
    private final List<Row> rows = new ArrayList<Row>();
    private File current;
    private TextView title;
    private boolean images;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        images = getIntent().getBooleanExtra(EXTRA_IMAGES, false);
        roots.add(new File("/sdcard"));
        File storage = new File("/storage");
        File[] volumes = storage.listFiles();
        if (volumes != null) {
            for (int i = 0; i < volumes.length; i++) {
                File volume = volumes[i];
                String name = volume.getName();
                if (!volume.isDirectory() || !volume.canRead()) continue;
                if ("emulated".equals(name) || "self".equals(name)) continue;
                roots.add(volume);
            }
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF121417);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        title = new TextView(this);
        title.setTextColor(0xFFF4F6F8);
        title.setTextSize(22);
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title);
        ListView list = new ListView(this);
        list.setId(android.R.id.list);
        list.setCacheColorHint(0);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        showRoots();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Row row = rows.get(position);
        if (row.up) {
            up();
            return;
        }
        if (row.file.isDirectory()) {
            current = row.file;
            showDir();
            return;
        }
        Intent data = new Intent();
        data.putExtra("path", row.file.getAbsolutePath());
        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (current == null) {
            finish();
            return;
        }
        up();
    }

    private void up() {
        if (current == null || isRoot(current)) {
            current = null;
            showRoots();
            return;
        }
        current = current.getParentFile();
        if (current == null) showRoots();
        else showDir();
    }

    private void showRoots() {
        current = null;
        title.setText(images ? "Где лежит картинка" : "Где лежит APK DCL");
        rows.clear();
        for (int i = 0; i < roots.size(); i++) {
            File root = roots.get(i);
            rows.add(new Row(false, root, root.getAbsolutePath()));
        }
        bind();
    }

    private void showDir() {
        title.setText(current.getAbsolutePath());
        rows.clear();
        rows.add(new Row(true, current, ".."));
        File[] files = current.listFiles();
        List<File> dirs = new ArrayList<File>();
        List<File> apks = new ArrayList<File>();
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                File file = files[i];
                if (file.getName().startsWith(".")) continue;
                if (file.isDirectory()) dirs.add(file);
                else if (wanted(file)) apks.add(file);
            }
        }
        Collections.sort(dirs, NAME);
        Collections.sort(apks, NAME);
        for (int i = 0; i < dirs.size(); i++) {
            rows.add(new Row(false, dirs.get(i), dirs.get(i).getName() + "/"));
        }
        for (int i = 0; i < apks.size(); i++) {
            File apk = apks.get(i);
            rows.add(new Row(false, apk, apk.getName() + "  (" + size(apk.length()) + ")"));
        }
        if (files == null) {
            title.setText(current.getAbsolutePath() + "  — нет доступа");
        }
        bind();
    }

    private boolean wanted(File file) {
        String name = file.getName().toLowerCase();
        if (images) {
            return name.endsWith(".jpg") || name.endsWith(".jpeg")
                    || name.endsWith(".png") || name.endsWith(".webp");
        }
        return name.endsWith(".apk");
    }

    private void bind() {
        setListAdapter(new ArrayAdapter<Row>(this, android.R.layout.simple_list_item_1, rows) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                view.setBackgroundColor(0xFF121417);
                TextView text = (TextView) view.findViewById(android.R.id.text1);
                text.setTextColor(0xFFF4F6F8);
                text.setTextSize(20);
                text.setPadding(dp(8), dp(14), dp(8), dp(14));
                return view;
            }
        });
    }

    private boolean isRoot(File dir) {
        for (int i = 0; i < roots.size(); i++) {
            if (roots.get(i).getAbsolutePath().equals(dir.getAbsolutePath())) return true;
        }
        return false;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String size(long bytes) {
        if (bytes >= 1024L * 1024L) return (bytes / (1024L * 1024L)) + " МБ";
        return (bytes / 1024L) + " КБ";
    }

    private static final Comparator<File> NAME = new Comparator<File>() {
        @Override
        public int compare(File a, File b) {
            return a.getName().compareToIgnoreCase(b.getName());
        }
    };

    private static final class Row {
        final boolean up;
        final File file;
        final String label;

        Row(boolean up, File file, String label) {
            this.up = up;
            this.file = file;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
