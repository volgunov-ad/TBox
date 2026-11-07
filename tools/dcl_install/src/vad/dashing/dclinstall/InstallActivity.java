package vad.dashing.dclinstall;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
/**
 * On-HU installer for Dashing Club Launcher on A9.
 * Root steps go through mb_daemon (/sdcard/autorun.sh), the same path as UsbMount.
 */
public class InstallActivity extends Activity {
    private static final int PICK_APK = 42;
    private static final int PICK_IMAGE = 43;
    private static final long TIMEOUT_MS = 15 * 60 * 1000L;
    private static final long CHECKLIST_REFRESH_MS = 3000L;

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
    private Button grantPermissions;
    private Button hideSelected;
    private Button changeWallpaper;
    private Button compileDcl;
    private Button restoreStock;
    private TextView log;
    private String apkPath;
    private String customImagePath;
    private boolean running;
    private boolean expectReboot;
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

        TextView title = text("Установка DCL для A9", 32, 0xFFF4F6F8);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        column.addView(title);
        status = text("", 22, 0xFFB7C0C8);
        status.setPadding(0, dp(6), 0, dp(8));
        column.addView(status);
        statusList = new LinearLayout(this);
        statusList.setOrientation(LinearLayout.VERTICAL);
        column.addView(statusList);

        column.addView(heading("APK лаунчера"));
        column.addView(note(
                "Здесь выбирается установочный файл Dashing Club Launcher, "
                        + "например DCLv077ru.apk из папки «Загрузки». "
                        + "Это сам лаунчер, не программа установки и не TBox Monitor."));
        Button pick = button("Выбрать APK лаунчера");
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!running) startActivityForResult(new Intent(InstallActivity.this, ApkPickerActivity.class), PICK_APK);
            }
        });
        column.addView(pick);
        apkLabel = text("APK лаунчера не выбран", 22, 0xFFF4F6F8);
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
        blackWallpaper = radio("Чёрный фон");
        customWallpaper = radio("Своя картинка");
        pictureWallpaper.setId(View.generateViewId());
        blackWallpaper.setId(View.generateViewId());
        customWallpaper.setId(View.generateViewId());
        wallpapers.addView(pictureWallpaper);
        wallpapers.addView(blackWallpaper);
        wallpapers.addView(customWallpaper);
        wallpapers.check(pictureWallpaper.getId());
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
        wallpaperNote = text("", 22, 0xFFE0A045);
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
        grantPermissions = button("Выдать разрешения");
        grantPermissions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onGrantClicked();
            }
        });
        hideSelected = button("Скрыть выбранные приложения");
        hideSelected.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onHideClicked();
            }
        });
        LinearLayout.LayoutParams extraParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        extraParams.topMargin = dp(8);
        column.addView(grantPermissions, extraParams);
        column.addView(hideSelected, extraParams);
        changeWallpaper = button("Поменять картинку обоев");
        changeWallpaper.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onWallpaperClicked();
            }
        });
        column.addView(changeWallpaper, extraParams);
        compileDcl = button("Скомпилировать DCL");
        compileDcl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onCompileClicked();
            }
        });
        column.addView(compileDcl, extraParams);
        restoreStock = button("Удалить DCL и вернуть штатное");
        restoreStock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onRestoreClicked();
            }
        });
        column.addView(restoreStock, extraParams);
        column.addView(note(
                "Эти кнопки нужны, если DCL уже установлен. "
                        + "Разрешения применяются сразу. "
                        + "Картинка обоев берётся из текущего выбора: по умолчанию, чёрный фон или свой файл. "
                        + "Компиляция собирает код лаунчера целиком и занимает несколько минут. "
                        + "На этом ГУ сама она не запускается, поэтому после установки лаунчер тормозит. "
                        + "Скрытие останавливает постоянные пакеты после перезагрузки. "
                        + "Штатный лаунчер скрывается всегда, остальные пакеты — по галочкам. "
                        + "Возврат удаляет DCL, программу флешек и старые пустые обои, "
                        + "показывает штатный лаунчер, System UI, голос, IoT Tube и руководство "
                        + "и ставит штатные обои. Сама программа установки остаётся."));

        log = text("", 20, 0xFFD5DDE4);
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
    private boolean resumed;
    private boolean checklistLoading;
    private String checklistShown;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable checklistTick = new Runnable() {
        @Override
        public void run() {
            if (!resumed) return;
            if (!running) showChecklist();
            handler.postDelayed(this, CHECKLIST_REFRESH_MS);
        }
    };

    @Override
    protected void onPause() {
        resumed = false;
        handler.removeCallbacks(checklistTick);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        handler.removeCallbacks(checklistTick);
        handler.post(checklistTick);
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

    /** Reads the checklist off the main thread; appops queries start processes. */
    private void showChecklist() {
        if (statusList == null || checklistLoading) return;
        checklistLoading = true;
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<DclStatus> items = DclStatus.collect(app);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        checklistLoading = false;
                        if (!isFinishing()) renderChecklist(items);
                    }
                });
            }
        }, "dcl-status").start();
    }

    private void renderChecklist(List<DclStatus> items) {
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            DclStatus item = items.get(i);
            key.append(item.title).append(item.done).append(item.unknown).append(item.detail).append('\n');
        }
        if (key.toString().equals(checklistShown)) return;
        checklistShown = key.toString();
        statusList.removeAllViews();
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
        TextView summary = text(summaryText, 24, 0xFFF4F6F8);
        summary.setTypeface(Typeface.DEFAULT_BOLD);
        summary.setPadding(0, dp(8), 0, dp(4));
        statusList.addView(summary);
        boolean optionalHeader = false;
        for (int i = 0; i < items.size(); i++) {
            DclStatus item = items.get(i);
            if (item.optional && !optionalHeader) {
                TextView header = text("По желанию", 24, 0xFFF4F6F8);
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
            TextView row = text(line, 22, color);
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
        handler.removeCallbacks(checklistTick);
        super.onDestroy();
    }

    private void onInstallClicked() {
        if (running || apkPath == null || !isA9()) return;
        running = true;
        refreshStatus();
        install.setEnabled(false);
        if (grantPermissions != null) grantPermissions.setEnabled(false);
        if (hideSelected != null) hideSelected.setEnabled(false);
        if (changeWallpaper != null) changeWallpaper.setEnabled(false);
        if (compileDcl != null) compileDcl.setEnabled(false);
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
                        refreshStatus();
                        log.setText("Остановлено. Старый DCL на месте.");
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void onGrantClicked() {
        if (running || !isA9()) return;
        if (installedDcl() == null) {
            log.setText("DCL ещё не установлен.");
            return;
        }
        expectReboot = false;
        setBusy(true);
        grantPermissions.setText("Выдача разрешений…");
        startRoot(currentPlan(false).toGrantScript());
    }

    private void onHideClicked() {
        if (running || !isA9()) return;
        if (installedDcl() == null) {
            log.setText("Сначала установите DCL. Штатный лаунчер скрывается всегда, без DCL не останется рабочего стола.");
            return;
        }
        expectReboot = true;
        setBusy(true);
        hideSelected.setText("Скрытие… ГУ перезагрузится");
        startRoot(currentPlan(false).toHideScript());
    }

    private void onCompileClicked() {
        if (running || !isA9()) return;
        if (installedDcl() == null) {
            log.setText("DCL ещё не установлен.");
            return;
        }
        expectReboot = false;
        setBusy(true);
        compileDcl.setText("Компиляция… несколько минут");
        startRoot(currentPlan(false).toCompileScript());
    }

    private void onRestoreClicked() {
        if (running || !isA9()) return;
        new AlertDialog.Builder(this)
                .setTitle("Вернуть штатное состояние")
                .setMessage("Удалить DCL, программу флешек и пустые обои? "
                        + "Штатный лаунчер, System UI, голосовой помощник, IoT Tube и руководство "
                        + "снова появятся. Головное устройство перезагрузится. "
                        + "TBox Monitor и эта программа установки останутся.")
                .setPositiveButton("Удалить и вернуть", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        expectReboot = true;
                        setBusy(true);
                        restoreStock.setText("Возврат… ГУ перезагрузится");
                        log.setText("Подготовка…");
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    File dir = new File(InstallPlan.PAYLOAD_DIR);
                                    if (!dir.exists() && !dir.mkdirs()) {
                                        throw new IllegalStateException("Нет записи в /sdcard/dcl_install");
                                    }
                                    copyAsset("setwallpaper.dex", new File(dir, "setwallpaper.dex"));
                                    arm(currentPlan(false).toRestoreScript());
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
                        }, "dcl-restore").start();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void onWallpaperClicked() {
        if (running || !isA9()) return;
        if (customWallpaper.isChecked() && customImagePath == null) {
            log.setText("Выберите jpg или png.");
            return;
        }
        expectReboot = false;
        setBusy(true);
        changeWallpaper.setText("Смена обоев…");
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
                    File dir = new File(InstallPlan.PAYLOAD_DIR);
                    if (!dir.exists() && !dir.mkdirs()) {
                        throw new IllegalStateException("Нет записи в /sdcard/dcl_install");
                    }
                    copyAsset("setwallpaper.dex", new File(dir, "setwallpaper.dex"));
                    arm(currentPlan(false).toWallpaperScript());
                    poll();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            BootWallpaperService.reload();
                        }
                    });
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
        }, "dcl-wallpaper").start();
    }

    private InstallPlan currentPlan(boolean uninstallExisting) {
        return new InstallPlan(
                apkPath == null ? "" : apkPath,
                uninstallExisting,
                systemUi.isChecked(),
                iottube.isChecked(),
                voice.isChecked(),
                speechClient.isChecked(),
                manual.isChecked());
    }

    private void setBusy(boolean busy) {
        running = busy;
        refreshStatus();
    }

    private void begin(boolean uninstallExisting) {
        expectReboot = true;
        setBusy(true);
        install.setText("Установка… ГУ перезагрузится");
        final InstallPlan plan = currentPlan(uninstallExisting);
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
                    File dir = new File(InstallPlan.PAYLOAD_DIR);
                    if (!dir.exists() && !dir.mkdirs()) {
                        throw new IllegalStateException("Нет записи в /sdcard/dcl_install");
                    }
                    copyAsset("usbmount.apk", new File(dir, "usbmount.apk"));
                    copyAsset("setwallpaper.dex", new File(dir, "setwallpaper.dex"));
                    arm(plan.toScript());
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

    private void startRoot(final String script) {
        log.setText("Подготовка…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    arm(script);
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
        }, "dcl-root").start();
    }

    private void arm(String script) throws Exception {
        new File(InstallPlan.RESULT_FILE).delete();
        new File(InstallPlan.LOG_FILE).delete();
        MbDaemon.run(script, MbDaemon.INSTALL_BODY);
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
                            if (expectReboot) {
                                log.append("\nГотово. Перезагрузка.");
                            } else {
                                log.append("\nГотово.");
                                running = false;
                                refreshStatus();
                                showChecklist();
                            }
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
                failUi("Команда не ответила за 15 минут. Журнал: " + InstallPlan.LOG_FILE);
            }
        });
    }

    private void failUi(String message) {
        running = false;
        refreshStatus();
        log.setText(message);
    }

    private void refreshStatus() {
        if (!isA9()) {
            status.setText("Это не ГУ A9 (mbCAN). Установка выключена.");
            status.setTextColor(0xFFE06A6A);
            install.setEnabled(false);
            if (grantPermissions != null) grantPermissions.setEnabled(false);
            if (hideSelected != null) hideSelected.setEnabled(false);
            if (changeWallpaper != null) changeWallpaper.setEnabled(false);
            if (compileDcl != null) compileDcl.setEnabled(false);
            if (restoreStock != null) restoreStock.setEnabled(false);
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
        install.setText("Установить и перезагрузить");
        if (grantPermissions != null) {
            grantPermissions.setEnabled(!running && installed != null);
            grantPermissions.setText("Выдать разрешения");
        }
        if (hideSelected != null) {
            hideSelected.setEnabled(!running && installed != null);
            hideSelected.setText("Скрыть выбранные приложения");
        }
        if (changeWallpaper != null) {
            changeWallpaper.setEnabled(!running && wallpaperChosen());
            changeWallpaper.setText("Поменять картинку обоев");
        }
        if (compileDcl != null) {
            compileDcl.setEnabled(!running && installed != null);
            compileDcl.setText("Скомпилировать DCL");
        }
        if (restoreStock != null) {
            restoreStock.setEnabled(!running);
            restoreStock.setText("Удалить DCL и вернуть штатное");
        }
    }

    private boolean wallpaperChosen() {
        return !customWallpaper.isChecked() || customImagePath != null;
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
        TextView view = text(value, 26, 0xFFF4F6F8);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, dp(10), 0, dp(4));
        return view;
    }

    private TextView note(String value) {
        TextView view = text(value, 22, 0xFFB7C0C8);
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
        view.setTextSize(24);
        view.setAllCaps(false);
        return view;
    }

    private RadioButton radio(String label) {
        RadioButton view = new RadioButton(this);
        view.setText(label);
        view.setTextColor(0xFFF4F6F8);
        view.setTextSize(24);
        return view;
    }

    private CheckBox check(String label, boolean checked) {
        CheckBox view = new CheckBox(this);
        view.setText(label);
        view.setTextColor(0xFFF4F6F8);
        view.setTextSize(24);
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
