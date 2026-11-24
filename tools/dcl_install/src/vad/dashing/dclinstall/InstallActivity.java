package vad.dashing.dclinstall;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
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
    private TextView progress;
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
    private Button setupInstalled;
    private Button hideSelected;
    private Button changeWallpaper;
    private Button compileDcl;
    private Button restoreStock;
    private Button dealerOn;
    private Button dealerOff;
    private Button uninstallUsb;
    private TextView log;
    private String apkPath;
    private String customImagePath;
    private boolean running;
    private boolean expectReboot;
    /** Drop the wallpaper mode file only after restore actually succeeds. */
    private boolean clearModeOnOk;
    private volatile boolean stopPoll;
    private boolean nativeUnlock;
    private boolean a9Known;
    private boolean a9;
    private boolean restoring;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        nativeUnlock = launcherHasNativeUnlock();
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
        status.setPadding(0, dp(6), 0, dp(4));
        column.addView(status);
        progress = text("", 22, 0xFFE0A045);
        progress.setPadding(0, 0, 0, dp(8));
        column.addView(progress);
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
                saveChoices();
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
                        + "Климат, телефон, камера и настройки автомобиля остаются отдельными программами. "
                        + "Рядом скрывается и системный обработчик USB: при загрузке с уже вставленной флешкой "
                        + "он открывает чёрный экран поверх лаунчера."
                        + (nativeUnlock
                                ? " Лаунчер " + installedDcl() + " сам подключает флешки, отдельная программа им не нужна."
                                : "")));
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
                        + (nativeUnlock
                                ? "Флешки после этого подключает сам лаунчер."
                                : "Флешки после этого подключает отдельная программа, её установщик ставит сам. ")
                        + "Без своих обоев загрузка тогда ждёт около 30 секунд."));
        column.addView(iottube);
        column.addView(note("Телематика. Постоянно падает, потому что не находит сервер, и заполняет системный журнал."));
        column.addView(voice);
        column.addView(note("Голосовой помощник iFlytek. Пока он работает, занимает заметную долю процессора."));
        column.addView(speechClient);
        column.addView(note("Служба рядом с помощником. Если помощник скрыт, она может остаться и при этом не падает."));
        column.addView(manual);
        column.addView(note("Руководство по автомобилю. После скрытия пропадает из списка программ."));
        watch(systemUi);
        watch(iottube);
        watch(voice);
        watch(speechClient);
        watch(manual);

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
        setupInstalled = button("Настроить установленный DCL");
        setupInstalled.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSetupClicked();
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
        column.addView(setupInstalled, extraParams);
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
        column.addView(note(
                "«Настроить установленный DCL» повторяет настройку без установки APK и перезагружает ГУ. "
                        + "Разрешения применяются сразу. Обои берутся из текущего выбора. "
                        + "Компиляция занимает несколько минут. "
                        + "Штатный лаунчер и обработчик USB скрываются всегда, остальное — по галочкам. "
                        + (nativeUnlock
                                ? "Лаунчер сам подключает флешки."
                                : "Для этого лаунчера ставится программа флешек.")));

        column.addView(heading("Визит к дилеру"));
        column.addView(note(
                "Включение прячет DCL"
                        + (nativeUnlock ? "" : " и программу флешек")
                        + ", возвращает штатный лаунчер, System UI, штатные обои и обработчик USB "
                        + "и снова показывает скрытые по желанию приложения. "
                        + "Выключение прячет обратно ровно то, что было скрыто до визита. "
                        + "Оба действия перезагружают ГУ. TBox Monitor и установщик остаются."));
        dealerOn = button("Включить режим дилера");
        dealerOn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDealerOnClicked();
            }
        });
        dealerOff = button("Выключить режим дилера");
        dealerOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDealerOffClicked();
            }
        });
        column.addView(dealerOn, extraParams);
        column.addView(dealerOff, extraParams);

        column.addView(heading("Откат"));
        restoreStock = button("Удалить DCL и вернуть штатное");
        restoreStock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onRestoreClicked();
            }
        });
        column.addView(restoreStock, extraParams);
        uninstallUsb = button("Удалить программу флешек");
        uninstallUsb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onUninstallUsbClicked();
            }
        });
        column.addView(uninstallUsb, extraParams);
        column.addView(note(
                "Возврат удаляет DCL"
                        + (nativeUnlock ? "" : ", программу флешек")
                        + " и пустые обои, показывает штатные приложения и снимает режим дилера. "
                        + "Программа флешек для лаунчера 0.8.2+ не нужна: если она осталась, удалите её отдельной кнопкой."));

        log = text("", 20, 0xFFD5DDE4);
        log.setTypeface(Typeface.MONOSPACE);
        log.setPadding(0, dp(12), 0, 0);
        column.addView(log);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF121417);
        scroll.addView(column);
        setContentView(scroll);
        restoreChoices();
        updateWallpaperNote();
        refreshStatus();
        resumeRunIfNeeded();
        showLastFailure();
    }

    private boolean appOpsProbeStarted;
    private boolean resumed;
    private boolean checklistLoading;
    private String checklistShown;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable rebootWatch = new Runnable() {
        @Override
        public void run() {
            if (!alive()) return;
            log.append("\nПерезагрузка не произошла — перезагрузите ГУ вручную.");
            if (progress != null) progress.setText("Перезагрузка не произошла — перезагрузите ГУ вручную.");
            running = false;
            refreshStatus();
        }
    };
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
                final boolean ran = DclStatus.grantSelfAppOps(InstallActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!ran) appOpsProbeStarted = false;
                        if (alive()) showChecklist();
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
                final boolean dealer = new File(InstallPlan.DEALER_FLAG).isFile();
                final List<DclStatus> items = DclStatus.collect(app);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        checklistLoading = false;
                        if (alive()) renderChecklist(items, dealer);
                    }
                });
            }
        }, "dcl-status").start();
    }

    private void renderChecklist(List<DclStatus> items, boolean dealerMode) {
        StringBuilder key = new StringBuilder();
        key.append(dealerMode).append('\n');
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
        if (!dealerMode) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).optional) continue;
                if (items.get(i).unknown) {
                    unknown++;
                    continue;
                }
                required++;
                if (items.get(i).done) done++;
            }
        }
        String summaryText = dealerMode
                ? "Визит к дилеру включён"
                : "Для работы DCL: " + done + " из " + required;
        if (!dealerMode && unknown > 0) summaryText = summaryText + ", ещё проверяется " + unknown;
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
            int color = dealerMode ? 0xFFB7C0C8
                    : item.unknown ? 0xFFB7C0C8
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
            apkLabel.setText("Чтение APK…");
            final String path = apkPath;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final String text = describeApk(path);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!alive() || path == null || !path.equals(apkPath)) return;
                            apkLabel.setText(text);
                            refreshStatus();
                        }
                    });
                }
            }, "dcl-apk-label").start();
        }
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            customImagePath = data.getStringExtra("path");
            customWallpaper.setChecked(true);
            saveChoices();
            updateWallpaperNote();
            refreshStatus();
        }
    }

    @Override
    public void onBackPressed() {
        if (running) {
            String message = expectReboot
                    ? "Идёт установка, дождитесь перезагрузки."
                    : "Команда ещё выполняется.";
            log.setText(message);
            if (progress != null) progress.setText(message);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        stopPoll = true;
        handler.removeCallbacks(checklistTick);
        handler.removeCallbacks(rebootWatch);
        super.onDestroy();
    }

    private void onInstallClicked() {
        if (running || apkPath == null || !isA9()) return;
        running = true;
        refreshStatus();
        install.setEnabled(false);
        if (grantPermissions != null) grantPermissions.setEnabled(false);
        if (setupInstalled != null) setupInstalled.setEnabled(false);
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
                        if (!alive()) {
                            running = false;
                            return;
                        }
                        if (check.error != null) {
                            failUi(check.error);
                            return;
                        }
                        if (check.signatureDiffers) {
                            confirmReplace(check);
                            return;
                        }
                        begin(false, check.fileVersion);
                    }
                });
            }
        }, "dcl-apk-check").start();
    }

    private void confirmReplace(final ApkCheck check) {
        if (!alive()) {
            running = false;
            return;
        }
        install.setText("Установить и перезагрузить");
        new AlertDialog.Builder(this)
                .setTitle("Другая подпись")
                .setMessage("Сейчас установлен DCL " + check.installedVersion
                        + ", в файле " + check.fileVersion
                        + ". Подпись другая, поверх поставить нельзя. Удалить старый DCL и поставить этот? "
                        + "Настройки лаунчера сотрутся. Если установка нового файла не удастся, "
                        + "штатный лаунчер и System UI будут возвращены. TBox Monitor не затрагивается.")
                .setPositiveButton("Удалить и установить", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        begin(true, check.fileVersion);
                    }
                })
                .setNegativeButton("Остановить", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
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
        saveChoices();
        expectReboot = false;
        setBusy(true);
        grantPermissions.setText("Выдача разрешений…");
        startRoot(currentPlan(false).toGrantScript(), false);
    }

    private void onSetupClicked() {
        if (running || !isA9()) return;
        if (installedDcl() == null) {
            log.setText("DCL ещё не установлен.");
            return;
        }
        if (!wallpaperChosen()) {
            log.setText("Выберите картинку обоев: по умолчанию, чёрный фон или свой файл.");
            return;
        }
        final String usb = nativeUnlock ? "" : " Будет установлена программа флешек.";
        confirm("Настроить установленный DCL",
                "Выдать разрешения, назначить DCL кнопкой HOME, поставить обои и скрыть "
                        + hideList() + "." + usb + " Головное устройство перезагрузится.",
                "Настроить",
                new Runnable() {
                    @Override
                    public void run() {
                        saveChoices();
                        expectReboot = true;
                        setBusy(true);
                        setupInstalled.setText("Настройка… ГУ перезагрузится");
                        InstallPlan plan = currentPlan(false);
                        startPrepared(plan.toSetupScript(), true, !plan.launcherHasNativeUnlock, true, false);
                    }
                });
    }

    private void onHideClicked() {
        if (running || !isA9()) return;
        if (installedDcl() == null) {
            log.setText("Сначала установите DCL. Штатный лаунчер скрывается всегда, без DCL не останется рабочего стола.");
            return;
        }
        final boolean wallpaper = systemUi.isChecked();
        confirm("Скрыть приложения",
                "Скрыть " + hideList() + " и назначить DCL кнопкой HOME."
                        + (wallpaper ? " Будут поставлены выбранные обои." : "")
                        + " Головное устройство перезагрузится.",
                "Скрыть",
                new Runnable() {
                    @Override
                    public void run() {
                        saveChoices();
                        expectReboot = true;
                        setBusy(true);
                        hideSelected.setText("Скрытие… ГУ перезагрузится");
                        InstallPlan plan = currentPlan(false);
                        startPrepared(plan.toHideScript(), plan.hideSystemUi, false, plan.hideSystemUi, false);
                    }
                });
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
        startRoot(currentPlan(false).toCompileScript(), false);
    }

    private void onRestoreClicked() {
        if (running || !isA9()) return;
        confirm("Вернуть штатное состояние",
                "Удалить DCL"
                        + (nativeUnlock ? "" : ", программу флешек")
                        + " и пустые обои? "
                        + "Штатный лаунчер, System UI, обработчик USB, голосовой помощник, "
                        + "IoT Tube и руководство снова появятся. Режим дилера снимется. "
                        + "Головное устройство перезагрузится. "
                        + "TBox Monitor и эта программа установки останутся.",
                "Удалить и вернуть",
                new Runnable() {
                    @Override
                    public void run() {
                        saveChoices();
                        expectReboot = true;
                        clearModeOnOk = true;
                        setBusy(true);
                        restoreStock.setText("Возврат… ГУ перезагрузится");
                        startPrepared(currentPlan(false).toRestoreScript(), false, false, true, false);
                    }
                });
    }

    private void onDealerOnClicked() {
        if (running || !isA9()) return;
        if (dealerActive()) {
            log.setText("Визит к дилеру уже включён.");
            return;
        }
        if (installedDcl() == null) {
            log.setText("DCL ещё не установлен.");
            return;
        }
        confirm("Включить режим дилера",
                "Вернуть штатный лаунчер, System UI, штатные обои и обработчик USB. "
                        + "Скрытые приложения из списка по желанию снова появятся; "
                        + "установщик запомнит их и спрячет обратно после визита. "
                        + "DCL будет скрыт. Другие службы специальных возможностей останутся, если они включены. "
                        + "Головное устройство перезагрузится.",
                "Включить",
                new Runnable() {
                    @Override
                    public void run() {
                        saveChoices();
                        expectReboot = true;
                        setBusy(true);
                        dealerOn.setText("Включение… ГУ перезагрузится");
                        startPrepared(currentPlan(false).toDealerOnScript(), false, false, true, false);
                    }
                });
    }

    private void onDealerOffClicked() {
        if (running || !isA9()) return;
        if (!dealerActive()) {
            log.setText("Визит к дилеру не включён.");
            return;
        }
        if (!wallpaperChosen()) {
            log.setText("Выберите картинку обоев: по умолчанию, чёрный фон или свой файл.");
            return;
        }
        confirm("Выключить режим дилера",
                "Вернуть DCL домашним экраном, заново скрыть то, что было скрыто до визита, "
                        + "и поставить выбранные обои. Головное устройство перезагрузится.",
                "Выключить",
                new Runnable() {
                    @Override
                    public void run() {
                        saveChoices();
                        expectReboot = true;
                        setBusy(true);
                        dealerOff.setText("Выключение… ГУ перезагрузится");
                        startPrepared(currentPlan(false).toDealerOffScript(), true, false, true, false);
                    }
                });
    }

    private void onWallpaperClicked() {
        if (running || !isA9()) return;
        if (customWallpaper.isChecked() && customImagePath == null) {
            log.setText("Выберите jpg, png или webp.");
            return;
        }
        saveChoices();
        expectReboot = false;
        setBusy(true);
        changeWallpaper.setText("Смена обоев…");
        startPrepared(currentPlan(false).toWallpaperScript(), true, false, true, true);
    }

    private void onUninstallUsbClicked() {
        if (running || !isA9()) return;
        confirm("Удалить программу флешек",
                "Удалить vad.dashing.usbmount? Лаунчер 0.8.2 и новее подключает флешки сам. "
                        + "Для более старого лаунчера без этой программы флешки NTFS и exFAT не появятся.",
                "Удалить",
                new Runnable() {
                    @Override
                    public void run() {
                        expectReboot = false;
                        setBusy(true);
                        uninstallUsb.setText("Удаление…");
                        startRoot(currentPlan(false).toUninstallUsbScript(), false);
                    }
                });
    }

    private InstallPlan currentPlan(boolean uninstallExisting) {
        return new InstallPlan(
                apkPath == null ? "" : apkPath,
                uninstallExisting,
                systemUi.isChecked(),
                iottube.isChecked(),
                voice.isChecked(),
                speechClient.isChecked(),
                manual.isChecked(),
                launcherHasNativeUnlock());
    }

    /** Installed launcher, including one hidden for a dealer visit. */
    private boolean launcherHasNativeUnlock() {
        return InstallPlan.hasNativeUnlock(installedDcl());
    }

    private void setBusy(boolean busy) {
        running = busy;
        refreshStatus();
    }

    private void begin(boolean uninstallExisting, String fileVersion) {
        saveChoices();
        expectReboot = true;
        setBusy(true);
        install.setText("Установка… ГУ перезагрузится");
        final InstallPlan plan = InstallPlan.forApk(
                apkPath == null ? "" : apkPath,
                uninstallExisting,
                systemUi.isChecked(),
                iottube.isChecked(),
                voice.isChecked(),
                speechClient.isChecked(),
                manual.isChecked(),
                fileVersion);
        startPrepared(plan.toScript(), true, !plan.launcherHasNativeUnlock, true, false);
    }

    private void startRoot(final String script, final boolean reloadWallpaper) {
        showProgress("Подготовка…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    arm(script);
                    poll();
                    if (reloadWallpaper) reloadWallpaper();
                } catch (Exception e) {
                    postFail(e);
                }
            }
        }, "dcl-root").start();
    }

    private void startPrepared(
            final String script,
            final boolean wallpaper,
            final boolean usb,
            final boolean dex,
            final boolean reloadWallpaper) {
        final boolean black = blackWallpaper.isChecked();
        final boolean custom = customWallpaper.isChecked();
        final String imagePath = customImagePath;
        showProgress("Подготовка…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (wallpaper) writeWallpaper(black, custom, imagePath);
                    if (usb || dex) {
                        File dir = new File(InstallPlan.PAYLOAD_DIR);
                        if (!dir.exists() && !dir.mkdirs()) {
                            throw new IllegalStateException("Нет записи в /sdcard/dcl_install");
                        }
                        if (usb) copyAsset("usbmount.apk", new File(dir, "usbmount.apk"));
                        if (dex) copyAsset("setwallpaper.dex", new File(dir, "setwallpaper.dex"));
                    }
                    arm(script);
                    poll();
                    if (reloadWallpaper) reloadWallpaper();
                } catch (Exception e) {
                    postFail(e);
                }
            }
        }, "dcl-root").start();
    }

    private void writeWallpaper(boolean black, boolean custom, String imagePath) throws Exception {
        if (black) WallpaperStore.writeBlack(InstallActivity.this);
        else if (custom) WallpaperStore.writeImageFile(InstallActivity.this, imagePath);
        else WallpaperStore.writeImageAsset(InstallActivity.this, "default_wallpaper.jpg");
    }

    private void reloadWallpaper() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (alive()) BootWallpaperService.reload();
            }
        });
    }

    private static long bootMark() {
        return RootRun.thisBoot(SystemClock.elapsedRealtime());
    }

    private static boolean runInProgress() {
        return RootRun.inProgress(new File(InstallPlan.LOCK_FILE), System.currentTimeMillis(), bootMark());
    }

    private void arm(String script) throws Exception {
        if (!RootRun.tryAcquire(RootRun.OWNER_INSTALL, bootMark())) {
            throw new IllegalStateException("Уже выполняется другая команда");
        }
        try {
            new File(InstallPlan.RESULT_FILE).delete();
            new File(InstallPlan.LOG_FILE).delete();
            MbDaemon.run(script, MbDaemon.INSTALL_BODY);
        } catch (Exception e) {
            RootRun.release();
            throw e;
        }
    }

    private void poll() throws Exception {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (!stopPoll && System.currentTimeMillis() < deadline) {
            final String text = readTail(InstallPlan.LOG_FILE);
            String result = readTail(InstallPlan.RESULT_FILE).trim();
            final String shown = text;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (alive() && shown.length() > 0) showLog(shown);
                }
            });
            if (!result.startsWith("OK") && !result.startsWith("FAIL") && !runInProgress()) {
                result = readTail(InstallPlan.RESULT_FILE).trim();
                if (!result.startsWith("OK") && !result.startsWith("FAIL")) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            failUi("Команда прервалась. Можно начать заново.");
                        }
                    });
                    return;
                }
            }
            if (result.startsWith("OK") || result.startsWith("FAIL")) {
                final String done = result;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!alive()) return;
                        if (done.startsWith("OK")) {
                            if (clearModeOnOk) {
                                WallpaperStore.clearMode(InstallActivity.this);
                                clearModeOnOk = false;
                            }
                            if (shown.length() > 0) showLog(shown);
                            if (shown.contains("WARN")) {
                                log.append("\nЕсть предупреждения, см. строки WARN.");
                            }
                            if (expectReboot) {
                                log.append("\nГотово. Перезагрузка.");
                                if (progress != null) progress.setText("Готово. Перезагрузка.");
                                handler.removeCallbacks(rebootWatch);
                                handler.postDelayed(rebootWatch, 60_000L);
                            } else {
                                log.append("\nГотово.");
                                if (progress != null) progress.setText("Готово.");
                                running = false;
                                refreshStatus();
                                showChecklist();
                            }
                        } else {
                            failUi(shown.length() == 0 ? done : shown);
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
                failUi("Команда не ответила за 15 минут. Журнал: " + InstallPlan.LOG_FILE
                        + ". Если команда ещё идёт, закройте программу и откройте снова.");
            }
        });
    }

    private void postFail(Exception e) {
        final String message = e.getMessage() == null ? e.toString() : e.getMessage();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                failUi(message);
            }
        });
    }

    private void failUi(String message) {
        handler.removeCallbacks(rebootWatch);
        running = false;
        clearModeOnOk = false;
        if (!alive()) return;
        refreshStatus();
        showLog(message);
    }

    private void showLog(String text) {
        log.setText(text);
        if (progress == null || text == null) return;
        int end = text.length();
        while (end > 0) {
            char c = text.charAt(end - 1);
            if (c != '\n' && c != '\r') break;
            end--;
        }
        int start = text.lastIndexOf('\n', end - 1);
        String line = text.substring(start < 0 ? 0 : start + 1, end).trim();
        if (line.length() > 0) progress.setText(line);
    }

    private void showProgress(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!alive()) return;
                log.setText(text);
                if (progress != null) progress.setText(text);
            }
        });
    }

    private void showLastFailure() {
        if (running) return;
        String result = readTail(InstallPlan.RESULT_FILE).trim();
        if (!result.startsWith("FAIL")) return;
        String text = readTail(InstallPlan.LOG_FILE);
        showLog(text.length() == 0 ? result : text);
    }

    private void resumeRunIfNeeded() {
        File lock = new File(InstallPlan.LOCK_FILE);
        if (!runInProgress()) return;
        final String owner = RootRun.owner(lock);
        running = true;
        expectReboot = RootRun.OWNER_INSTALL.equals(owner);
        refreshStatus();
        showProgress(RootRun.OWNER_INSTALL.equals(owner)
                ? "Команда ещё выполняется…"
                : "Выполняется служебная команда…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (RootRun.OWNER_INSTALL.equals(owner)) {
                        poll();
                    } else {
                        long deadline = System.currentTimeMillis() + 60_000L;
                        while (!stopPoll
                                && System.currentTimeMillis() < deadline
                                && runInProgress()) {
                            Thread.sleep(300);
                        }
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!alive()) return;
                                running = false;
                                refreshStatus();
                                showChecklist();
                            }
                        });
                    }
                } catch (Exception e) {
                    postFail(e);
                }
            }
        }, "dcl-resume").start();
    }

    private void confirm(String title, String message, String positive, final Runnable action) {
        if (running || !alive()) return;
        running = true;
        refreshStatus();
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positive, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        action.run();
                    }
                })
                .setNegativeButton("Отмена", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        running = false;
                        refreshStatus();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        running = false;
                        if (alive()) refreshStatus();
                    }
                })
                .show();
    }

    private String hideList() {
        StringBuilder list = new StringBuilder("штатный лаунчер и обработчик USB");
        if (systemUi.isChecked()) list.append(", System UI");
        if (iottube.isChecked()) list.append(", IoT Tube");
        if (voice.isChecked()) list.append(", голосовой помощник");
        if (speechClient.isChecked()) list.append(", клиент голоса");
        if (manual.isChecked()) list.append(", электронное руководство");
        return list.toString();
    }

    private void watch(CheckBox box) {
        box.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                saveChoices();
            }
        });
    }

    private SharedPreferences prefs() {
        return createDeviceProtectedStorageContext().getSharedPreferences("dclinstall", MODE_PRIVATE);
    }

    private void restoreChoices() {
        restoring = true;
        SharedPreferences stored = prefs();
        systemUi.setChecked(stored.getBoolean("hideSystemUi", true));
        iottube.setChecked(stored.getBoolean("hideIottube", true));
        voice.setChecked(stored.getBoolean("hideVoice", false));
        speechClient.setChecked(stored.getBoolean("hideSpeechClient", false));
        manual.setChecked(stored.getBoolean("hideManual", false));
        String image = stored.getString("customImage", "");
        customImagePath = image == null || image.length() == 0 ? null : image;
        String mode = stored.getString("wallpaper", "picture");
        if ("black".equals(mode)) blackWallpaper.setChecked(true);
        else if ("custom".equals(mode)) customWallpaper.setChecked(true);
        else pictureWallpaper.setChecked(true);
        restoring = false;
    }

    private void saveChoices() {
        if (restoring || systemUi == null) return;
        String mode = blackWallpaper.isChecked() ? "black" : customWallpaper.isChecked() ? "custom" : "picture";
        prefs().edit()
                .putBoolean("hideSystemUi", systemUi.isChecked())
                .putBoolean("hideIottube", iottube.isChecked())
                .putBoolean("hideVoice", voice.isChecked())
                .putBoolean("hideSpeechClient", speechClient.isChecked())
                .putBoolean("hideManual", manual.isChecked())
                .putString("wallpaper", mode)
                .putString("customImage", customImagePath == null ? "" : customImagePath)
                .apply();
    }

    private boolean alive() {
        return !isFinishing() && !isDestroyed();
    }

    private void refreshStatus() {
        boolean dealer = dealerActive();
        if (!isA9()) {
            status.setText("Это не ГУ A9 (mbCAN). Установка выключена.");
            status.setTextColor(0xFFE06A6A);
            install.setEnabled(false);
            if (grantPermissions != null) grantPermissions.setEnabled(false);
            if (setupInstalled != null) setupInstalled.setEnabled(false);
            if (hideSelected != null) hideSelected.setEnabled(false);
            if (changeWallpaper != null) changeWallpaper.setEnabled(false);
            if (compileDcl != null) compileDcl.setEnabled(false);
            if (restoreStock != null) restoreStock.setEnabled(false);
            if (dealerOn != null) dealerOn.setEnabled(false);
            if (dealerOff != null) dealerOff.setEnabled(false);
            if (uninstallUsb != null) uninstallUsb.setEnabled(false);
            setChoicesEnabled(false);
            return;
        }
        status.setTextColor(0xFFB7C0C8);
        String installed = installedDcl();
        if (dealer) {
            status.setText("ГУ A9. Сейчас: штатный вид для дилера. DCL"
                    + (nativeUnlock ? "" : " и программа флешек") + " скрыты.");
        } else if (installed == null) {
            status.setText("ГУ A9. DCL ещё не установлен.");
        } else {
            status.setText("ГУ A9. Сейчас установлен DCL " + installed + ".");
        }
        install.setEnabled(!running && !dealer && canInstall());
        install.setText("Установить и перезагрузить");
        if (setupInstalled != null) {
            setupInstalled.setEnabled(!running && !dealer && installed != null && wallpaperChosen());
            setupInstalled.setText("Настроить установленный DCL");
        }
        if (grantPermissions != null) {
            grantPermissions.setEnabled(!running && !dealer && installed != null);
            grantPermissions.setText("Выдать разрешения");
        }
        if (hideSelected != null) {
            hideSelected.setEnabled(!running && !dealer && installed != null);
            hideSelected.setText("Скрыть выбранные приложения");
        }
        if (changeWallpaper != null) {
            changeWallpaper.setEnabled(!running && !dealer && wallpaperChosen());
            changeWallpaper.setText("Поменять картинку обоев");
        }
        if (compileDcl != null) {
            compileDcl.setEnabled(!running && !dealer && installed != null);
            compileDcl.setText("Скомпилировать DCL");
        }
        if (restoreStock != null) {
            restoreStock.setEnabled(!running);
            restoreStock.setText("Удалить DCL и вернуть штатное");
        }
        if (dealerOn != null) {
            dealerOn.setEnabled(!running && !dealer && installed != null);
            dealerOn.setText("Включить режим дилера");
        }
        if (dealerOff != null) {
            dealerOff.setEnabled(!running && dealer);
            dealerOff.setText("Выключить режим дилера");
        }
        if (uninstallUsb != null) {
            uninstallUsb.setEnabled(!running);
            uninstallUsb.setText("Удалить программу флешек");
        }
        setChoicesEnabled(!running);
    }

    private void setChoicesEnabled(boolean enabled) {
        if (systemUi != null) systemUi.setEnabled(enabled);
        if (iottube != null) iottube.setEnabled(enabled);
        if (voice != null) voice.setEnabled(enabled);
        if (speechClient != null) speechClient.setEnabled(enabled);
        if (manual != null) manual.setEnabled(enabled);
        if (pictureWallpaper != null) pictureWallpaper.setEnabled(enabled);
        if (blackWallpaper != null) blackWallpaper.setEnabled(enabled);
        if (customWallpaper != null) customWallpaper.setEnabled(enabled);
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
                    ? "Выберите jpg, png или webp."
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
            PackageInfo installed = getPackageManager().getPackageInfo(
                    InstallPlan.DCL_PACKAGE, flags | PackageManager.MATCH_UNINSTALLED_PACKAGES);
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
        String version = DclPackages.dclVersion(getPackageManager());
        if (version == null || version.length() == 0) return null;
        return version;
    }

    /** True while the dealer visit lasts; the script writes the flag, the installer only reads it. */
    private boolean dealerActive() {
        return new File(InstallPlan.DEALER_FLAG).isFile();
    }

    private boolean isA9() {
        if (!a9Known) {
            a9 = "9".equals(Build.VERSION.RELEASE) && "MB".equals(prop("ro.wt.solution.provider"));
            a9Known = true;
        }
        return a9;
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
