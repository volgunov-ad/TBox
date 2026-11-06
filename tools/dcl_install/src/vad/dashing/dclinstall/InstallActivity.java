package vad.dashing.dclinstall;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * On-HU installer for Dashing Club Launcher on A9.
 * Root steps go through mb_daemon (/sdcard/autorun.sh), the same path as UsbMount.
 */
public class InstallActivity extends Activity {
    private static final int PICK_APK = 42;
    private static final int PICK_IMAGE = 43;
    private static final long TIMEOUT_MS = 5 * 60 * 1000L;

    private TextView status;
    private LinearLayout statusList;
    private TextView apkLabel;
    private TextView wallpaperNote;
    private RadioButton pictureWallpaper;
    private RadioButton blackWallpaper;
    private RadioButton customWallpaper;
    private CheckBox systemUi;
    private CheckBox iottube;
    private CheckBox voice;
    private CheckBox speechClient;
    private CheckBox manual;
    private Button install;
    private TextView log;
    private String apkPath;
    private String customImagePath;
    private boolean running;
    private volatile boolean stopPoll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        int pad = dp(16);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(pad, pad, pad, pad);
        column.setBackgroundColor(0xFF121417);

        TextView title = text("Установка DCL для A9", 26, 0xFFF4F6F8);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        column.addView(title);
        status = text("", 16, 0xFFB7C0C8);
        status.setPadding(0, dp(6), 0, dp(8));
        column.addView(status);
        statusList = new LinearLayout(this);
        statusList.setOrientation(LinearLayout.VERTICAL);
        column.addView(statusList);

        Button pick = button("Выбрать APK");
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!running) startActivityForResult(new Intent(InstallActivity.this, ApkPickerActivity.class), PICK_APK);
            }
        });
        column.addView(pick);
        apkLabel = text("APK не выбран", 18, 0xFFF4F6F8);
        apkLabel.setPadding(0, dp(8), 0, dp(8));
        column.addView(apkLabel);

        column.addView(heading("Обои"));
        column.addView(note(
                "Штатные обои находятся внутри System UI. Когда System UI скрыт, их нет, "
                        + "и каждое включение ждёт окно обоев около 30 секунд. "
                        + "Свои обои готовы ещё до входа в систему, поэтому экран открывается сразу. "
                        + "Картинка видна в конце загрузки, пока лаунчер не нарисовал свой экран, "
                        + "и там, где окно не закрывает фон."));
        RadioGroup wallpapers = new RadioGroup(this);
        pictureWallpaper = radio("Картинка по умолчанию");
        pictureWallpaper.setChecked(true);
        blackWallpaper = radio("Чёрный фон");
        customWallpaper = radio("Своя картинка");
        wallpapers.addView(pictureWallpaper);
        wallpapers.addView(blackWallpaper);
        wallpapers.addView(customWallpaper);
        wallpapers.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                updateWallpaperNote();
                refreshStatus();
            }
        });
        column.addView(wallpapers);
        Button pickImage = button("Выбрать картинку");
        pickImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (running) return;
                customWallpaper.setChecked(true);
                Intent intent = new Intent(InstallActivity.this, ApkPickerActivity.class);
                intent.putExtra(ApkPickerActivity.EXTRA_IMAGES, true);
                startActivityForResult(intent, PICK_IMAGE);
            }
        });
        column.addView(pickImage);
        wallpaperNote = text("", 16, 0xFFE0A045);
        wallpaperNote.setPadding(0, dp(4), 0, dp(8));
        column.addView(wallpaperNote);

        column.addView(heading("Всегда скрывается"));
        column.addView(note(
                "Штатный лаунчер — рабочий стол с карточками: карта, музыка, климат, ADAS. "
                        + "Его нужно именно скрыть. Если только выключить, процесс всё равно стартует. "
                        + "Кнопка HOME после этого открывает DCL. "
                        + "Климат, телефон, камера и настройки автомобиля остаются отдельными программами."));
        column.addView(heading("Скрыть по желанию"));
        column.addView(note("Снятая галочка пакет не трогает."));
        systemUi = check("System UI", true);
        iottube = check("IoT Tube", true);
        voice = check("Штатный голосовой помощник", false);
        speechClient = check("Клиент голоса", false);
        manual = check("Электронное руководство", false);
        column.addView(systemUi);
        column.addView(note(
                "Плавающая кнопка «Домой» и шторка сверху принадлежат System UI. "
                        + "Скрытие убирает их с экрана лаунчера. "
                        + "Вместе с ними пропадают уведомления, всплывающая громкость "
                        + "и системные вопросы, например доступ программы к USB-устройству. "
                        + "Флешки после этого подключает отдельная программа, её установщик ставит сам. "
                        + "Без своих обоев загрузка тогда ждёт около 30 секунд."));
        column.addView(iottube);
        column.addView(note("Телематика. Постоянно падает, потому что не находит сервер, и заполняет системный журнал."));
        column.addView(voice);
        column.addView(note("Голосовой помощник iFlytek. Пока он работает, занимает заметную долю процессора."));
        column.addView(speechClient);
        column.addView(note("Служба рядом с помощником. Если помощник скрыт, она может остаться и при этом не падает."));
        column.addView(manual);
        column.addView(note("Руководство по автомобилю. После скрытия пропадает из списка программ."));

        install = button("Установить и перезагрузить");
        install.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onInstallClicked();
            }
        });
        LinearLayout.LayoutParams installParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        installParams.topMargin = dp(12);
        column.addView(install, installParams);

        log = text("", 14, 0xFFD5DDE4);
        log.setTypeface(Typeface.MONOSPACE);
        log.setPadding(0, dp(12), 0, 0);
        column.addView(log);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF121417);
        scroll.addView(column);
        setContentView(scroll);
        updateWallpaperNote();
        refreshStatus();
    }

    private boolean appOpsProbeStarted;

    @Override
    protected void onResume() {
        super.onResume();
        showChecklist();
        if (running || appOpsProbeStarted) return;
        if (getPackageManager().checkPermission(
                "android.permission.GET_APP_OPS_STATS", getPackageName())
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }
        appOpsProbeStarted = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                DclStatus.grantSelfAppOps(InstallActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showChecklist();
                    }
                });
            }
        }, "dcl-appops").start();
    }

    private void showChecklist() {
        if (statusList == null) return;
        statusList.removeAllViews();
        List<DclStatus> items = DclStatus.collect(this);
        int done = 0;
        int required = 0;
        int unknown = 0;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).optional) continue;
            if (items.get(i).unknown) {
                unknown++;
                continue;
            }
            required++;
            if (items.get(i).done) done++;
        }
        String summaryText = "Для работы DCL: " + done + " из " + required;
        if (unknown > 0) summaryText = summaryText + ", ещё проверяется " + unknown;
        TextView summary = text(summaryText, 18, 0xFFF4F6F8);
        summary.setTypeface(Typeface.DEFAULT_BOLD);
        summary.setPadding(0, dp(8), 0, dp(4));
        statusList.addView(summary);
        boolean optionalHeader = false;
        for (int i = 0; i < items.size(); i++) {
            DclStatus item = items.get(i);
            if (item.optional && !optionalHeader) {
                TextView header = text("По желанию", 18, 0xFFF4F6F8);
                header.setTypeface(Typeface.DEFAULT_BOLD);
                header.setPadding(0, dp(8), 0, dp(4));
                statusList.addView(header);
                optionalHeader = true;
            }
            int color = item.unknown ? 0xFFB7C0C8
                    : item.done ? 0xFF8FCB7A
                    : (item.optional ? 0xFFB7C0C8 : 0xFFE07A7A);
            String mark = item.done ? "● " : "○ ";
            String line = mark + item.title;
            if (item.detail != null && item.detail.length() > 0) line = line + " — " + item.detail;
            TextView row = text(line, 16, color);
            row.setPadding(0, dp(2), 0, dp(2));
            statusList.addView(row);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_APK && resultCode == RESULT_OK && data != null) {
            apkPath = data.getStringExtra("path");
            apkLabel.setText(describeApk(apkPath));
            refreshStatus();
        }
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            customImagePath = data.getStringExtra("path");
            customWallpaper.setChecked(true);
            updateWallpaperNote();
            refreshStatus();
        }
    }

    @Override
    protected void onDestroy() {
        stopPoll = true;
        super.onDestroy();
    }

    private void onInstallClicked() {
        if (running || apkPath == null || !isA9()) return;
        running = true;
        install.setEnabled(false);
        install.setText("Проверка APK…");
        final String path = apkPath;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ApkCheck check = inspect(path);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (check.error != null) {
                            failUi(check.error);
                            return;
                        }
                        if (check.signatureDiffers) {
                            confirmReplace(check);
                            return;
                        }
                        begin(false);
                    }
                });
            }
        }, "dcl-apk-check").start();
    }

    private void confirmReplace(ApkCheck check) {
        install.setText("Установить и перезагрузить");
        new AlertDialog.Builder(this)
                .setTitle("Другая подпись")
                .setMessage("Сейчас установлен DCL " + check.installedVersion
                        + ", в файле " + check.fileVersion
                        + ". Подпись другая, поверх поставить нельзя. Удалить старый DCL и поставить этот? "
                        + "Настройки лаунчера сотрутся. TBox Monitor не затрагивается.")
                .setPositiveButton("Удалить и установить", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        begin(true);
                    }
                })
                .setNegativeButton("Остановить", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        running = false;
                        install.setEnabled(canInstall());
                        log.setText("Остановлено. Старый DCL на месте.");
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void begin(boolean uninstallExisting) {
        install.setEnabled(false);
        install.setText("Установка… ГУ перезагрузится");
        final InstallPlan plan = new InstallPlan(
                apkPath,
                uninstallExisting,
                systemUi.isChecked(),
                iottube.isChecked(),
                voice.isChecked(),
                speechClient.isChecked(),
                manual.isChecked());
        final boolean black = blackWallpaper.isChecked();
        final boolean custom = customWallpaper.isChecked();
        final String imagePath = customImagePath;
        log.setText("Подготовка…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (black) WallpaperStore.writeBlack(InstallActivity.this);
                    else if (custom) WallpaperStore.writeImageFile(InstallActivity.this, imagePath);
                    else WallpaperStore.writeImageAsset(InstallActivity.this, "default_wallpaper.jpg");
                    prepare(plan);
                    poll();
                } catch (Exception e) {
                    final String message = e.getMessage() == null ? e.toString() : e.getMessage();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            failUi(message);
                        }
                    });
                }
            }
        }, "dcl-install").start();
    }

    private void prepare(InstallPlan plan) throws Exception {
        new File(InstallPlan.RESULT_FILE).delete();
        new File(InstallPlan.LOG_FILE).delete();
        File dir = new File(InstallPlan.PAYLOAD_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Нет записи в /sdcard/dcl_install");
        }
        copyAsset("usbmount.apk", new File(dir, "usbmount.apk"));
        copyAsset("setwallpaper.dex", new File(dir, "setwallpaper.dex"));
        FileOutputStream script = new FileOutputStream(InstallPlan.AUTORUN);
        try {
            script.write(plan.toScript().getBytes("UTF-8"));
        } finally {
            script.close();
        }
        exec(new String[] {"chmod", "777", InstallPlan.AUTORUN});
        new File(InstallPlan.FLAG).createNewFile();
        exec(new String[] {"setprop", InstallPlan.PROP, ""});
        Thread.sleep(300);
        exec(new String[] {"setprop", InstallPlan.PROP, InstallPlan.FLAG});
    }

    private void poll() throws Exception {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (!stopPoll && System.currentTimeMillis() < deadline) {
            final String text = readTail(InstallPlan.LOG_FILE);
            final String result = readTail(InstallPlan.RESULT_FILE).trim();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (text.length() > 0) log.setText(text);
                }
            });
            if (result.startsWith("OK") || result.startsWith("FAIL")) {
                final String done = result;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (done.startsWith("OK")) {
                            log.append("\nГотово. Перезагрузка.");
                        } else {
                            failUi(text.length() == 0 ? done : text);
                        }
                    }
                });
                return;
            }
            Thread.sleep(500);
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                failUi("mb_daemon не ответил за 5 минут. Журнал: " + InstallPlan.LOG_FILE);
            }
        });
    }

    private void failUi(String message) {
        running = false;
        install.setEnabled(canInstall());
        install.setText("Установить и перезагрузить");
        log.setText(message);
    }

    private void refreshStatus() {
        if (!isA9()) {
            status.setText("Это не ГУ A9 (mbCAN). Установка выключена.");
            status.setTextColor(0xFFE06A6A);
            install.setEnabled(false);
            return;
        }
        status.setTextColor(0xFFB7C0C8);
        String installed = installedDcl();
        if (installed == null) {
            status.setText("ГУ A9. DCL ещё не установлен.");
        } else {
            status.setText("ГУ A9. Сейчас установлен DCL " + installed + ".");
        }
        install.setEnabled(!running && canInstall());
    }

    private boolean canInstall() {
        if (!isA9() || apkPath == null) return false;
        return !customWallpaper.isChecked() || customImagePath != null;
    }

    private void updateWallpaperNote() {
        if (blackWallpaper.isChecked()) {
            wallpaperNote.setText("Чёрный фон.");
        } else if (customWallpaper.isChecked()) {
            wallpaperNote.setText(customImagePath == null
                    ? "Выберите jpg или png."
                    : customImagePath);
        } else {
            wallpaperNote.setText("Встроенная картинка.");
        }
    }

    private String describeApk(String path) {
        PackageInfo info = getPackageManager().getPackageArchiveInfo(path, 0);
        if (info == null) return path + "\nФайл не читается как APK";
        return info.packageName + "  " + info.versionName + "\n" + path;
    }

    private ApkCheck inspect(String path) {
        ApkCheck check = new ApkCheck();
        int flags = PackageManager.GET_SIGNATURES;
        if (Build.VERSION.SDK_INT >= 28) flags |= PackageManager.GET_SIGNING_CERTIFICATES;
        PackageInfo archive = getPackageManager().getPackageArchiveInfo(path, flags);
        if (archive == null) {
            check.error = "Файл не читается как APK";
            return check;
        }
        if (!InstallPlan.DCL_PACKAGE.equals(archive.packageName)) {
            check.error = "Это не DCL. В файле пакет " + archive.packageName;
            return check;
        }
        check.fileVersion = archive.versionName == null ? "?" : archive.versionName;
        Signature[] archiveSigns = signatures(archive);
        if (archiveSigns == null || archiveSigns.length == 0) {
            check.error = "Не удалось прочитать подпись APK";
            return check;
        }
        try {
            PackageInfo installed = getPackageManager().getPackageInfo(InstallPlan.DCL_PACKAGE, flags);
            check.installedVersion = installed.versionName == null ? "?" : installed.versionName;
            Signature[] installedSigns = signatures(installed);
            check.signatureDiffers = !sameSignatures(archiveSigns, installedSigns);
        } catch (PackageManager.NameNotFoundException notInstalled) {
            check.signatureDiffers = false;
        }
        return check;
    }

    private static Signature[] signatures(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) {
            Signature[] current = info.signingInfo.getApkContentsSigners();
            if (current != null && current.length > 0) return current;
        }
        return info.signatures;
    }

    private static boolean sameSignatures(Signature[] a, Signature[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            boolean found = false;
            for (int j = 0; j < b.length; j++) {
                if (a[i].equals(b[j])) found = true;
            }
            if (!found) return false;
        }
        return true;
    }

    private String installedDcl() {
        try {
            return getPackageManager().getPackageInfo(InstallPlan.DCL_PACKAGE, 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private boolean isA9() {
        return "9".equals(Build.VERSION.RELEASE) && "MB".equals(prop("ro.wt.solution.provider"));
    }

    private void copyAsset(String name, File dest) throws Exception {
        InputStream in = getAssets().open(name);
        try {
            FileOutputStream out = new FileOutputStream(dest);
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
    }

    private static void exec(String[] cmd) throws Exception {
        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        if (!process.waitFor(8, TimeUnit.SECONDS)) {
            process.destroy();
            throw new IllegalStateException("Таймаут: " + cmd[0]);
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException(cmd[0] + " завершилась с кодом " + process.exitValue());
        }
    }

    private static String prop(String key) {
        try {
            Process process = new ProcessBuilder("getprop", key).start();
            InputStream in = process.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[256];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            process.waitFor();
            return new String(out.toByteArray(), "UTF-8").trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String readTail(String path) {
        try {
            File file = new File(path);
            if (!file.exists()) return "";
            FileInputStream in = new FileInputStream(file);
            try {
                long skip = file.length() - 12000;
                if (skip > 0) in.skip(skip);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[2048];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return new String(out.toByteArray(), "UTF-8");
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return "";
        }
    }

    private TextView heading(String value) {
        TextView view = text(value, 20, 0xFFF4F6F8);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, dp(10), 0, dp(4));
        return view;
    }

    private TextView note(String value) {
        TextView view = text(value, 15, 0xFFB7C0C8);
        view.setPadding(0, 0, 0, dp(6));
        return view;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private Button button(String label) {
        Button view = new Button(this);
        view.setText(label);
        view.setTextSize(18);
        view.setAllCaps(false);
        return view;
    }

    private RadioButton radio(String label) {
        RadioButton view = new RadioButton(this);
        view.setText(label);
        view.setTextColor(0xFFF4F6F8);
        view.setTextSize(18);
        return view;
    }

    private CheckBox check(String label, boolean checked) {
        CheckBox view = new CheckBox(this);
        view.setText(label);
        view.setTextColor(0xFFF4F6F8);
        view.setTextSize(18);
        view.setChecked(checked);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class ApkCheck {
        String error;
        String fileVersion;
        String installedVersion;
        boolean signatureDiffers;
    }
}
