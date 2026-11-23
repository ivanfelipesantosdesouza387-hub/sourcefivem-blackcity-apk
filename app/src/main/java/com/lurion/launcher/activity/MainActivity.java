package com.nexion.launcher.activity;

import android.Manifest;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Environment;
import android.os.Build;
import android.content.pm.PackageManager;
import android.os.Handler;

import android.util.Log;
import android.widget.*;

import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.content.Intent;
import android.net.Uri;

import android.view.View.OnClickListener;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.view.animation.Animation;

import com.nexion.online.R;
import com.nexion.launcher.api.Api;
import com.nexion.launcher.other.Preferences;
import com.nexion.launcher.update.LauncherUpdateChecker;
import com.nexion.launcher.update.UpdateInfo;
import com.nexion.launcher.util.Util;
import com.nexion.launcher.util.SampQueryAPI;



import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private EditText nickname;
    private String selectedServer = "Black City";
    private String serverPort = Api.GAME_SERVER_PORT;
    private static final int PERMISSION_REQUEST_CODE = 1000;
    private boolean permissionsGranted = false;
    private boolean isInitialLoad = true;
    private static final int LOADER_REQUEST_CODE = 1001;
    private static final int UPDATE_REQUEST_CODE = 1003;
    private static final int LAUNCHER_UPDATE_REQUEST_CODE = 1004;
    private static boolean isToastShown = false;
    private boolean isClientLocked = false;
    private ImageButton discordButton;
    private ImageButton instagramButton;
    private ImageButton youtubeButton;
    private TextView serverStatusView;
    private boolean serverUnderMaintenance = false;

    private AppCompatButton playButton;
    private boolean isCheckingUpdate = false;
    private String currentVersion = "1.0.0";
    private boolean isCheckingLauncherUpdate = false;
    private View launcherLoadingOverlay;
    private ProgressBar launcherLoadingProgress;
    private final android.os.Handler launcherLoadingHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private android.animation.ValueAnimator launcherLoadingProgressAnimator;
    private long launcherLoadingStartedAt;
    private boolean launcherLoadingFinishing;
    private boolean launcherUpdatePromptVisible;
    private boolean launcherUpdateDownloading;
    private boolean launcherInstallStarted;
    private boolean gameDataInstallStarted;
    private UpdateInfo pendingLauncherUpdate;
    private Thread launcherUpdateThread;
    private File downloadedLauncherApk;
    private static final long MIN_LAUNCHER_LOADING_MILLIS = 4800L;
    private static final long LAUNCHER_DOWNLOAD_UI_INTERVAL_MILLIS = 150L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Api.configureStorage(this);
        setTheme(R.style.Theme_AppCompat_Light_NoActionBar_FullScreen);
        setContentView(R.layout.activity_main);

        // Modo imersivo: esconde barra de status e barra de navegação
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);

        startLauncherLoadingScreen();
        setLauncherLoadingProgress(8);
        checkClientLockStatus();
    }

    private void startLauncherLoadingScreen() {
        launcherLoadingOverlay = findViewById(R.id.launcher_loading_overlay);
        launcherLoadingProgress = findViewById(R.id.launcher_loading_progress);
        if (launcherLoadingOverlay == null || launcherLoadingProgress == null)
            return;
        launcherLoadingStartedAt = android.os.SystemClock.uptimeMillis();
        launcherLoadingFinishing = false;
        launcherLoadingOverlay.setVisibility(View.VISIBLE);
        launcherLoadingOverlay.setAlpha(1.0f);
        setLauncherHomeVisible(false);
        launcherLoadingOverlay.bringToFront();
        launcherLoadingOverlay.post(() -> launcherLoadingOverlay.bringToFront());
        launcherLoadingProgress.setMax(100);
        launcherLoadingProgress.setProgress(0);

        // Animação do logo vindo de cima
        View logo = findViewById(R.id.launcher_loading_logo);
        if (logo != null) {
            Animation logoAnimation = AnimationUtils.loadAnimation(this, R.anim.slide_from_top);
            logo.startAnimation(logoAnimation);
        }
    }

    private void setLauncherLoadingProgress(int progress) {
        if (launcherLoadingProgress == null)
            return;
        int target = Math.max(0, Math.min(100, progress));
        if (launcherLoadingProgressAnimator != null)
            launcherLoadingProgressAnimator.cancel();
        launcherLoadingProgressAnimator = android.animation.ValueAnimator.ofInt(
                launcherLoadingProgress.getProgress(), target);
        launcherLoadingProgressAnimator.setDuration(650L);
        launcherLoadingProgressAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        launcherLoadingProgressAnimator.addUpdateListener(animation ->
                launcherLoadingProgress.setProgress((Integer) animation.getAnimatedValue()));
        launcherLoadingProgressAnimator.start();
    }

    private void finishLauncherLoadingScreen() {
        if (launcherLoadingOverlay == null || launcherLoadingOverlay.getVisibility() != View.VISIBLE ||
                launcherLoadingFinishing)
            return;
        if (launcherUpdatePromptVisible || launcherUpdateDownloading || launcherInstallStarted) {
            launcherLoadingHandler.postDelayed(this::finishLauncherLoadingScreen, 500L);
            return;
        }
        long elapsed = android.os.SystemClock.uptimeMillis() - launcherLoadingStartedAt;
        if (elapsed < MIN_LAUNCHER_LOADING_MILLIS) {
            launcherLoadingHandler.postDelayed(this::finishLauncherLoadingScreen,
                    MIN_LAUNCHER_LOADING_MILLIS - elapsed);
            return;
        }
        launcherLoadingFinishing = true;
        setLauncherLoadingProgress(100);
        launcherLoadingHandler.postDelayed(() -> {
            if (launcherLoadingOverlay == null)
                return;
            launcherLoadingOverlay.animate()
                    .alpha(0.0f)
                    .setDuration(260L)
                    .withEndAction(() -> {
                        launcherLoadingOverlay.setVisibility(View.GONE);
                        launcherLoadingOverlay.setAlpha(1.0f);
                        showLauncherHome();
                    })
                    .start();
        }, 720L);
    }

    private void checkClientLockStatus() {
        new Thread(() -> {
            try {
                String jsonString = downloadLockData();
                if (jsonString != null) {
                    JSONObject lockData = new JSONObject(jsonString);
                    isClientLocked = lockData.getBoolean("locked");

                    runOnUiThread(() -> {
                        setLauncherLoadingProgress(38);
                        if (isClientLocked) {
                            Intent lockIntent = new Intent(MainActivity.this, ClientActivity.class);
                            startActivity(lockIntent);
                            finish();
                        } else {
                            initializeMainActivity();
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        setLauncherLoadingProgress(38);
                        initializeMainActivity();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setLauncherLoadingProgress(38);
                    initializeMainActivity();
                });
            }
        }).start();
    }

    private String downloadLockData() {
        try {
            java.net.URL url = new java.net.URL(Api.CLIENT_LOCK_URL);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            InputStream inputStream = connection.getInputStream();
            java.util.Scanner scanner = new java.util.Scanner(inputStream, StandardCharsets.UTF_8.name());
            scanner.useDelimiter("\\A");
            String result = scanner.hasNext() ? scanner.next() : "";
            scanner.close();
            return result;

        } catch (Exception e) {
            return null;
        }
    }

    private void initializeMainActivity() {
        setLauncherLoadingProgress(52);
        getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );

        Animation animation = null;
        try {
            animation = AnimationUtils.loadAnimation(this, R.anim.button_click);
        } catch (Exception e) {
            animation = new Animation() {};
        }

        loadCurrentVersion();
        createNecessaryDirectories();
        enforceGameServerSettings();
        loadServerFromIni();
        replaceTestServerWithMoscow();
        checkAndRequestPermissions();
        initUIComponents(animation);
        serverStatusView = findViewById(R.id.server_status);
        checkServerStatus();
        initSocialButtons(animation);
        loadServerSelection();
        setLauncherLoadingProgress(82);

        new Handler().postDelayed(this::LoadNick, 100);
        new Handler().postDelayed(() -> setLauncherLoadingProgress(88), 160);
        new Handler().postDelayed(this::startDataDownloadIfNeeded, 600);
        new Handler().postDelayed(this::checkLauncherUpdate, 250);
        launcherLoadingHandler.postDelayed(this::finishLauncherLoadingScreen, 9000L);
    }

    private void setLauncherHomeVisible(boolean visible) {
        int state = visible ? View.VISIBLE : View.GONE;
        View social = findViewById(R.id.social_layout);
        View play = findViewById(R.id.button_play);
        View nickEdit = findViewById(R.id.edit_text_name);
        if (social != null) social.setVisibility(state);
        if (play != null) play.setVisibility(state);
        if (nickEdit != null) nickEdit.setVisibility(state);
    }

    private void showLauncherHome() {
        setLauncherHomeVisible(true);
    }

    private void updatePlayButtonState() {
        if (playButton == null)
            return;
        boolean installed = IsGameInstalled();
        playButton.setText(installed ? "JOGAR" : "ATUALIZAR");
        playButton.setContentDescription(installed ? "Jogar" : "Atualizar jogo");
        playButton.setCompoundDrawablesRelativeWithIntrinsicBounds(
                installed ? R.drawable.launcher_play_small : R.drawable.launcher_update_small,
                0,
                0,
                0);
    }





    private void checkLauncherUpdate() {
        if (isCheckingLauncherUpdate) return;
        isCheckingLauncherUpdate = true;

        LauncherUpdateChecker.checkForUpdate(this, new LauncherUpdateChecker.UpdateCheckListener() {
            @Override
            public void onUpdateAvailable(UpdateInfo updateInfo) {
                isCheckingLauncherUpdate = false;
                runOnUiThread(() -> {
                    pendingLauncherUpdate = updateInfo;
                    launcherUpdatePromptVisible = true;
                    setLauncherLoadingProgress(96);
                    setLauncherLoadingText(
                            "Atualização disponível",
                            "Nova versão: " + updateInfo.getVersion());
                    showLauncherUpdateDialog(updateInfo);
                });
            }

            @Override
            public void onNoUpdateAvailable() {
                isCheckingLauncherUpdate = false;
                runOnUiThread(() -> {
                    launcherUpdatePromptVisible = false;
                    pendingLauncherUpdate = null;
                    setLauncherLoadingProgress(100);
                    restoreLauncherLoadingText();
                    finishLauncherLoadingScreen();
                });
            }

            @Override
            public void onError(String errorMessage) {
                isCheckingLauncherUpdate = false;
                runOnUiThread(() -> {
                    launcherUpdatePromptVisible = false;
                    pendingLauncherUpdate = null;
                    setLauncherLoadingProgress(100);
                    restoreLauncherLoadingText();
                    finishLauncherLoadingScreen();
                });
            }
        });
    }

    private void showLauncherUpdateDialog(UpdateInfo updateInfo) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Atualização do launcher disponível")
                .setMessage("Uma nova versão do launcher está disponível: " + updateInfo.getVersion() +
                        "\nAtualize para receber novos recursos e correções.")
                .setPositiveButton("Atualizar", (dialog, which) -> {
                    startLauncherUpdateActivity(updateInfo);
                })
                .setNegativeButton("Mais tarde", (dialog, which) -> {
                    launcherUpdatePromptVisible = false;
                    pendingLauncherUpdate = null;
                    restoreLauncherLoadingText();
                    finishLauncherLoadingScreen();
                })
                .setCancelable(false)
                .show();
    }

    private void startLauncherUpdateActivity(UpdateInfo updateInfo) {
        if (updateInfo == null || updateInfo.getUpdateUrl() == null ||
                updateInfo.getUpdateUrl().trim().isEmpty()) {
            launcherUpdatePromptVisible = false;
            safeShowToast("URL da atualização não configurada");
            restoreLauncherLoadingText();
            finishLauncherLoadingScreen();
            return;
        }

        launcherUpdatePromptVisible = false;
        launcherUpdateDownloading = true;
        pendingLauncherUpdate = updateInfo;
        setLauncherLoadingProgressDirect(0);
        setLauncherLoadingText("Baixando atualização... 0%", "0 B / tamanho desconhecido");

        File updateDirectory = new File(getFilesDir(), "launcher_updates");
        if (!updateDirectory.exists() && !updateDirectory.mkdirs()) {
            onLauncherDownloadFailed("Não foi possível preparar o armazenamento da atualização");
            return;
        }

        String safeVersion = updateInfo.getVersion() == null ? "latest" :
                updateInfo.getVersion().replaceAll("[^A-Za-z0-9_.-]", "_");
        downloadedLauncherApk = new File(updateDirectory, "launcher_" + safeVersion + ".apk");
        if (downloadedLauncherApk.exists() && !downloadedLauncherApk.delete()) {
            onLauncherDownloadFailed("Não foi possível substituir o APK antigo");
            return;
        }

        final File targetApk = downloadedLauncherApk;
        launcherUpdateThread = new Thread(() -> downloadLauncherApk(updateInfo.getUpdateUrl(), targetApk),
                "NexionLauncherUpdate");
        launcherUpdateThread.start();
    }

    private void downloadLauncherApk(String updateUrl, File targetApk) {
        java.net.HttpURLConnection connection = null;
        long downloadedBytes = 0L;
        long totalBytes = -1L;
        long lastUiUpdate = 0L;

        try {
            java.net.URL url = new java.net.URL(updateUrl);
            connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Nexion-Online-Launcher");

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("HTTP " + responseCode);
            }

            totalBytes = connection.getContentLengthLong();
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(targetApk)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    downloadedBytes += count;

                    long now = android.os.SystemClock.uptimeMillis();
                    if (now - lastUiUpdate >= LAUNCHER_DOWNLOAD_UI_INTERVAL_MILLIS) {
                        lastUiUpdate = now;
                        int progress = totalBytes > 0
                                ? (int) Math.min(99L, downloadedBytes * 100L / totalBytes)
                                : 0;
                        long currentDownloaded = downloadedBytes;
                        long currentTotal = totalBytes;
                        runOnUiThread(() -> updateLauncherDownloadStatus(
                                progress, currentDownloaded, currentTotal));
                    }
                }
                output.flush();
            }

            final long completedBytes = downloadedBytes;
            final long completedTotal = totalBytes;
            runOnUiThread(() -> onLauncherDownloadCompleted(targetApk, completedBytes, completedTotal));
        } catch (Exception e) {
            try {
                if (targetApk.exists())
                    targetApk.delete();
            } catch (Exception ignored) {
            }
            String detail = e.getMessage() == null ? "erro desconhecido" : e.getMessage();
            runOnUiThread(() -> onLauncherDownloadFailed(detail));
        } finally {
            if (connection != null)
                connection.disconnect();
        }
    }

    private void updateLauncherDownloadStatus(int progress, long downloadedBytes, long totalBytes) {
        if (!launcherUpdateDownloading)
            return;
        String percent = totalBytes > 0 ? progress + "%" : "...";
        String total = totalBytes > 0 ? Util.bytesIntoHumanReadable(totalBytes) : "tamanho desconhecido";
        setLauncherLoadingProgressDirect(progress);
        setLauncherLoadingText(
                "Baixando atualização... " + percent,
                formatLauncherBytes(downloadedBytes) + " / " + total);
    }

    private void onLauncherDownloadCompleted(File apkFile, long downloadedBytes, long totalBytes) {
        if (!launcherUpdateDownloading)
            return;
        launcherUpdateDownloading = false;
        launcherInstallStarted = true;
        setLauncherLoadingProgressDirect(100);
        long displayTotal = totalBytes > 0 ? totalBytes : downloadedBytes;
        setLauncherLoadingText(
                "Download concluído",
                formatLauncherBytes(downloadedBytes) + " / " +
                        formatLauncherBytes(displayTotal));
        launcherLoadingHandler.postDelayed(() -> launchLauncherApkInstaller(apkFile), 900L);
    }

    private void onLauncherDownloadFailed(String detail) {
        launcherUpdateDownloading = false;
        launcherInstallStarted = false;
        launcherUpdatePromptVisible = false;
        setLauncherLoadingText("Falha no download", "Tente novamente mais tarde");
        safeShowToast("Não foi possível baixar a atualização: " + detail);
        launcherLoadingHandler.postDelayed(() -> {
            restoreLauncherLoadingText();
            finishLauncherLoadingScreen();
        }, 1300L);
    }

    private void launchLauncherApkInstaller(File apkFile) {
        if (apkFile == null || !apkFile.exists()) {
            onLauncherDownloadFailed("Arquivo APK não encontrado");
            return;
        }

        try {
            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            Uri apkUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                apkUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", apkFile);
            } else {
                apkUri = Uri.fromFile(apkFile);
            }
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            launcherInstallStarted = true;
            startActivityForResult(installIntent, LAUNCHER_UPDATE_REQUEST_CODE);
        } catch (Exception e) {
            launcherInstallStarted = false;
            onLauncherDownloadFailed("não foi possível abrir a instalação");
        }
    }

    private String formatLauncherBytes(long bytes) {
        return bytes <= 0 ? "0 B" : Util.bytesIntoHumanReadable(bytes);
    }

    private void setLauncherLoadingText(String title, String info) {
        TextView titleView = findViewById(R.id.launcher_loading_title);
        TextView infoView = findViewById(R.id.launcher_loading_terms);
        if (titleView != null)
            titleView.setText(title);
        if (infoView != null) {
            infoView.setText(info);
            infoView.setTextSize(9f);
            infoView.setVisibility(View.VISIBLE);
        }
    }

    private void restoreLauncherLoadingText() {
        TextView titleView = findViewById(R.id.launcher_loading_title);
        TextView infoView = findViewById(R.id.launcher_loading_terms);
        if (titleView != null)
            titleView.setText("Carregando o jogo");
        if (infoView != null) {
            infoView.setText("Eu já li e concordo com os Termos e Política de Privacidade");
            infoView.setTextSize(6f);
            infoView.setVisibility(View.VISIBLE);
        }
    }

    private void setLauncherLoadingProgressDirect(int progress) {
        if (launcherLoadingProgress == null)
            return;
        if (launcherLoadingProgressAnimator != null)
            launcherLoadingProgressAnimator.cancel();
        launcherLoadingProgress.setProgress(Math.max(0, Math.min(100, progress)));
    }

    private void loadCurrentVersion() {
        try {
            File versionFile = new File(Api.VERSION_FILE);
            if (versionFile.exists()) {
                Scanner scanner = new Scanner(versionFile);
                if (scanner.hasNextLine()) {
                    currentVersion = scanner.nextLine().trim();
                }
                scanner.close();
            } else {
                createVersionFile();
            }
        } catch (Exception e) {
            currentVersion = "1.0.0";
            createVersionFile();
        }
    }

    private void createVersionFile() {
        try {
            File versionFile = new File(Api.VERSION_FILE);
            if (!versionFile.getParentFile().exists()) {
                versionFile.getParentFile().mkdirs();
            }
            java.io.FileWriter writer = new java.io.FileWriter(versionFile);
            writer.write(currentVersion);
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST_CODE);
            return;
        }

        permissionsGranted = true;
        initializeApp();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            permissionsGranted = allGranted;

            if (!allGranted) {
                safeShowToast("Разрешение на микрофон не предоставлено. Голосовой чат будет недоступен.");
            }
            initializeApp();
        }
    }

    private void initializeApp() {
        isInitialLoad = false;
    }

    private void initUIComponents(Animation animation) {
        nickname = findViewById(R.id.edit_text_name);
        playButton = findViewById(R.id.button_play);
        updatePlayButtonState();

        if (nickname != null) {
            nickname.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    saveNickname();
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    v.clearFocus();
                }
                return true;
            });
            nickname.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                public void afterTextChanged(android.text.Editable s) { saveNickname(); }
            });
        }

        if (playButton != null) {
            playButton.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    if (animation != null) {
                        v.startAnimation(animation);
                    }
                    new android.os.Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            onClickPlay();
                        }
                    }, 200L);
                }
            });
        }
    }

    private void initSocialButtons(Animation animation) {
        discordButton = findViewById(R.id.button_discord);
        instagramButton = findViewById(R.id.button_instagram);
        youtubeButton = findViewById(R.id.button_youtube);

        if (discordButton != null) {
            discordButton.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    if (animation != null) {
                        v.startAnimation(animation);
                    }
                    new android.os.Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            showSocialDevelopmentNotice("Discord");
                        }
                    }, 200L);
                }
            });
        }

        if (instagramButton != null) {
            instagramButton.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    if (animation != null) {
                        v.startAnimation(animation);
                    }
                    new android.os.Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            showSocialDevelopmentNotice("Instagram");
                        }
                    }, 200L);
                }
            });
        }

        if (youtubeButton != null) {
            youtubeButton.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    if (animation != null) {
                        v.startAnimation(animation);
                    }
                    new android.os.Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            showSocialDevelopmentNotice("YouTube");
                        }
                    }, 200L);
                }
            });
        }
    }

    private void showSocialDevelopmentNotice(String network) {
        Toast.makeText(this, network + " em desenvolvimento", Toast.LENGTH_SHORT).show();
    }

    private void createNecessaryDirectories() {
        File gameDir = new File(Api.GAME_CACHE_PATH);
        if (!gameDir.exists()) {
            gameDir.mkdirs();
        }

        File sampDir = new File(Api.GAME_CACHE_PATH, "SAMP");
        if (!sampDir.exists()) {
            sampDir.mkdirs();
        }

        File texdbDir = new File(Api.GAME_TEXDB_PATH);
        if (!texdbDir.exists()) {
            texdbDir.mkdirs();
        }

        File filesDir = new File(Api.GAME_INSTALL_PATH);
        if (!filesDir.exists()) {
            filesDir.mkdirs();
        }

        File settingsFile = new File(sampDir, "settings.ini");
        if (!settingsFile.exists()) {
            try {
                writeIniValue(settingsFile, "client", "name", "");
                writeIniValue(settingsFile, "client", "server", "ТЕСТ");
                writeIniValue(settingsFile, "debug", "debug", "false");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        File serverIniFile = new File(sampDir, "serv.ini");
        if (!serverIniFile.exists()) {
            try {
                writeIniValue(serverIniFile, "server", "name", "ТЕСТ");
                writeIniValue(serverIniFile, "server", "port", "7777");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void loadServerFromIni() {
        try {
            File serverIniFile = new File(Api.GAME_CACHE_PATH, "SAMP/serv.ini");
            if (serverIniFile.exists()) {
                String serverName = readIniValue(serverIniFile, "server", "name");
                String serverPortFromIni = readIniValue(serverIniFile, "server", "port");

                if (serverName != null && !serverName.isEmpty()) {
                    selectedServer = serverName;
                }
                if (serverPortFromIni != null && !serverPortFromIni.isEmpty()) {
                    serverPort = serverPortFromIni;
                }
            }

        } catch (Exception e) {
            selectedServer = "ТЕСТ";
            serverPort = "7777";
        }
    }

    private void enforceGameServerSettings() {
        try {
            File dataSettingsFile = new File(Api.GAME_CACHE_PATH, "data/settings.ini");
            File nativeSettingsFile = new File(Api.GAME_CACHE_PATH, "SAMP/settings.ini");
            writeIniValue(dataSettingsFile, "client", "host", Api.GAME_SERVER_HOST);
            writeIniValue(dataSettingsFile, "client", "port", Api.GAME_SERVER_PORT);
            writeIniValue(nativeSettingsFile, "client", "host", Api.GAME_SERVER_HOST);
            writeIniValue(nativeSettingsFile, "client", "port", Api.GAME_SERVER_PORT);
            serverPort = Api.GAME_SERVER_PORT;
        } catch (Exception e) {
            Log.w("MainActivity", "Não foi possível atualizar o servidor padrão", e);
        }
    }

    private void enforceNativeGameServerSettings() {
        enforceGameServerSettings();
    }

    private void loadServerSelection() {
        try {
            File serverIniFile = new File(Api.GAME_CACHE_PATH, "SAMP/serv.ini");
            if (serverIniFile.exists()) {
                String serverName = readIniValue(serverIniFile, "server", "name");
                String serverPortFromIni = readIniValue(serverIniFile, "server", "port");

                if (serverName != null && !serverName.isEmpty()) {
                    selectedServer = serverName;
                }
                if (serverPortFromIni != null && !serverPortFromIni.isEmpty()) {
                    serverPort = serverPortFromIni;
                }
            }

            File settingsFile = new File(Api.GAME_CACHE_PATH, "SAMP/settings.ini");
            if (settingsFile.exists()) {
                String savedServer = readIniValue(settingsFile, "client", "server");
                if (savedServer != null && !savedServer.isEmpty() && selectedServer == null) {
                    selectedServer = savedServer;
                }
            }

        } catch (Exception e) {
            selectedServer = "ТЕСТ";
            serverPort = "7777";
        }
    }

    private void replaceTestServerWithMoscow() {
        try {
            File settingsFile = new File(Api.GAME_CACHE_PATH, "SAMP/settings.ini");
            if (settingsFile.exists()) {
                String savedServer = readIniValue(settingsFile, "client", "server");
                if (savedServer != null && savedServer.equals("ТЕСТ")) {
                    writeIniValue(settingsFile, "client", "server", "ТЕСТ");
                    selectedServer = "ТЕСТ";
                }
            }

            File serverIniFile = new File(Api.GAME_CACHE_PATH, "SAMP/serv.ini");
            if (serverIniFile.exists()) {
                String serverName = readIniValue(serverIniFile, "server", "name");
                if (serverName != null && serverName.equals("ТЕСТ")) {
                    writeIniValue(serverIniFile, "server", "name", "ТЕСТ");
                    selectedServer = "ТЕСТ";
                }
            }

            if (selectedServer != null && selectedServer.equals("ТЕСТ")) {
                selectedServer = "ТЕСТ";
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveServerSelection() {
        try {
            File settingsDir = new File(Api.GAME_CACHE_PATH, "SAMP");
            if (!settingsDir.exists()) {
                settingsDir.mkdirs();
            }

            File f = new File(settingsDir, "settings.ini");
            writeIniValue(f, "client", "server", selectedServer);
        } catch (Exception e) {
            safeShowToast("Ошибка сохранения сервера: " + e.getMessage());
        }
    }

    public void onClickPlay() {
        if (nickname != null) {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(nickname.getWindowToken(), 0);
            nickname.clearFocus();
        }

        if (isCheckingUpdate) {
            return;
        }

        if (!permissionsGranted) {
            checkAndRequestPermissions();
            return;
        }

        if (selectedServer == null || selectedServer.isEmpty()) {
            return;
        }
        if (serverUnderMaintenance) {
            safeShowToast("O servidor está em manutenção.");
            return;
        }

        if (!IsGameInstalled()) {
            ToLoad();
            return;
        }

        // A data v5 contém WEAPON.dat em maiúsculas, mas o hook Android
        // abre SAMP/weapon.dat. O armazenamento Android é case-sensitive;
        // criar apenas a cópia compatível evita crash sem apagar a data.
        ensureNativeDataCompatibility();

        String nickText = Preferences.getNick();
        if (!checkValidNick(nickText)) {
            return;
        }

        saveServerSelection();
        enforceNativeGameServerSettings();
        // Não bloquear o botão JOGAR aguardando o endpoint antigo de atualização.
        // A atualização do launcher é verificada separadamente na tela inicial.
        startGame();
    }

    private void ensureNativeDataCompatibility() {
        File sampDir = new File(Api.GAME_CACHE_PATH, "SAMP");
        File expected = new File(sampDir, "weapon.dat");
        if (expected.isFile() && expected.length() > 0) return;

        File packaged = new File(sampDir, "WEAPON.dat");
        if (!packaged.isFile() || packaged.length() <= 0) {
            Log.w("MainActivity", "SAMP/weapon.dat ausente e não foi encontrada cópia WEAPON.dat");
            return;
        }

        try (InputStream input = new FileInputStream(packaged);
             FileOutputStream output = new FileOutputStream(expected)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.flush();
            Log.i("MainActivity", "Criada compatibilidade SAMP/weapon.dat (" + expected.length() + " bytes)");
        } catch (IOException e) {
            Log.e("MainActivity", "Falha ao criar SAMP/weapon.dat", e);
        }
    }

    private void checkForUpdatesBeforePlay() {
        isCheckingUpdate = true;

        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(Api.UPDATE_JSON);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                InputStream inputStream = connection.getInputStream();
                java.util.Scanner scanner = new java.util.Scanner(inputStream, StandardCharsets.UTF_8.name());
                scanner.useDelimiter("\\A");
                String result = scanner.hasNext() ? scanner.next() : "";
                scanner.close();

                JSONObject jsonObject = new JSONObject(result);
                boolean updateAvailable = jsonObject.getBoolean("update");
                String updateUrl = jsonObject.getString("update_url");
                String newVersion = jsonObject.getString("version");

                runOnUiThread(() -> {
                    isCheckingUpdate = false;

                    if (updateAvailable && !currentVersion.equals(newVersion)) {
                        startUpdateActivity(updateUrl, newVersion);
                    } else {
                        startGame();
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    isCheckingUpdate = false;
                    startGame();
                });
            }
        }).start();
    }

    private void startUpdateActivity(String updateUrl, String newVersion) {
        try {
            Intent updateIntent = new Intent(MainActivity.this, UpdateActivity.class);
            updateIntent.putExtra("update_url", updateUrl);
            updateIntent.putExtra("new_version", newVersion);
            startActivityForResult(updateIntent, UPDATE_REQUEST_CODE);
        } catch (Exception e) {
            safeShowToast("Ошибка запуска обновления: " + e.getMessage());
            startGame();
        }
    }

    private void checkServerStatus() {
        new Thread(() -> {
            boolean maintenance = false;
            try {
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection)
                        new java.net.URL(Api.SERVER_STATUS_URL).openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                if (connection.getResponseCode() == 200) {
                    try (java.io.InputStream input = connection.getInputStream()) {
                        String json = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        org.json.JSONObject object = new org.json.JSONObject(json);
                        maintenance = object.optBoolean("maintenance", false);
                    }
                }
                connection.disconnect();
            } catch (Exception ignored) {
                // Sem override remoto: a consulta UDP decide online/offline.
            }
            if (maintenance) {
                applyServerStatus("maintenance");
                return;
            }
            boolean online = false;
            SampQueryAPI query = new SampQueryAPI(Api.GAME_SERVER_HOST,
                    Integer.parseInt(Api.GAME_SERVER_PORT));
            String[] info = query.mo7164b();
            // info[0] is the password-protected flag, not the online status.
            if (info != null && info.length >= 6) online = true;
            applyServerStatus(online ? "online" : "offline");
        }).start();
    }

    private void applyServerStatus(String status) {
        runOnUiThread(() -> {
            if (serverStatusView == null) return;
            if ("maintenance".equals(status)) {
                serverUnderMaintenance = true;
                serverStatusView.setText("● EM MANUTENÇÃO");
                serverStatusView.setTextColor(android.graphics.Color.rgb(255, 193, 7));
            } else if ("online".equals(status)) {
                serverUnderMaintenance = false;
                serverStatusView.setText("● ONLINE");
                serverStatusView.setTextColor(android.graphics.Color.rgb(76, 175, 80));
            } else {
                serverUnderMaintenance = false;
                serverStatusView.setText("● OFFLINE");
                serverStatusView.setTextColor(android.graphics.Color.rgb(244, 67, 54));
            }
        });
    }

    private void startGame() {
        try {
            Class<?> SAMPClass = Class.forName("com.nexion.online.SAMP");
            Intent gameIntent = new Intent(this, SAMPClass);
            startActivity(gameIntent);

        } catch (LinkageError e) {
            Log.e("MainActivity", "Falha ao carregar uma biblioteca nativa do jogo", e);
            safeShowToast("Falha ao carregar os componentes do jogo. Confira a instalação e a arquitetura do APK.");
        } catch (ClassNotFoundException e) {
            safeShowToast("Класс игры не найден. Запустите установку.");
            ToLoad();
        } catch (Exception e) {
            safeShowToast("Ошибка запуска игры: " + e.getMessage());
            ToLoad();
        }
    }

    private boolean IsGameInstalled() {
        boolean installComplete = getSharedPreferences(Api.INSTALL_PREFS, MODE_PRIVATE)
                .getBoolean(Api.INSTALL_COMPLETE_KEY, false);
        // Uma actualização da data deve invalidar a instalação anterior,
        // mesmo que o SharedPreferences ainda diga que ela foi concluída.
        if (installComplete && isDataVersionCurrent()) return true;
        File marker = new File(Api.GAME_DATA_MARKER);
        File fteamFile = new File(Api.FTEAM_FILE);
        File versionFile = new File(Api.VERSION_FILE);
        File gameFile = new File(Api.GAME_TEXDB_PATH, "gta3.img");
        if (!gameFile.isFile()) gameFile = findGameFile(new File(Api.GAME_CACHE_PATH), "gta3.img");
        // gta3.img é o arquivo grande e definitivo da data. Não exigir
        // SAMP/settings.ini aqui: ele pode ser criado somente ao abrir o jogo.
        boolean filesReady = gameFile.isFile() && gameFile.length() > 100_000_000L;
        boolean knownInstall = marker.isFile() || checkFteamFile(fteamFile)
                || (versionFile.isFile() && versionFile.length() > 0);
        if (filesReady && knownInstall && !marker.isFile()) {
            try (java.io.FileWriter writer = new java.io.FileWriter(marker, false)) {
                writer.write(Api.GAME_DATA_VERSION + "\n");
            } catch (Exception ignored) {
                // A presença dos arquivos continua sendo suficiente para iniciar.
            }
        }
        return filesReady && knownInstall;
    }

    private boolean isDataVersionCurrent() {
        File[] candidates = {new File(Api.VERSION_FILE), new File(Api.GAME_DATA_MARKER)};
        for (File candidate : candidates) {
            if (!candidate.isFile()) continue;
            try (Scanner scanner = new Scanner(candidate, "UTF-8")) {
                if (scanner.hasNextLine() && Api.GAME_DATA_VERSION.equals(scanner.nextLine().trim())) return true;
            } catch (Exception ignored) { }
        }
        return false;
    }

    private File findGameFile(File root, String wantedName) {
        if (root == null || !root.isDirectory()) return new File(root, wantedName);
        File[] children = root.listFiles();
        if (children == null) return new File(root, wantedName);
        for (File child : children) {
            if (child.isFile() && child.getName().equalsIgnoreCase(wantedName)) return child;
            if (child.isDirectory()) {
                File found = findGameFile(child, wantedName);
                if (found.isFile()) return found;
            }
        }
        return new File(root, wantedName);
    }

    private boolean checkFteamFile(File fteamFile) {
        try {
            if (fteamFile.exists() && fteamFile.length() > 0) {
                Scanner scanner = new Scanner(fteamFile);
                if (scanner.hasNextLine()) {
                    String firstLine = scanner.nextLine().trim();
                    scanner.close();
                    return firstLine.equals("created by fteam (ferkus team)");
                }
                scanner.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void ToLoad() {
        try {
            Intent loaderIntent = new Intent(this, InstallActivity.class);
            startActivityForResult(loaderIntent, LOADER_REQUEST_CODE);
        } catch (Exception e) {
            safeShowToast("Ошибка запуска загрузчика: " + e.getMessage());
        }
    }

    private void startDataDownloadIfNeeded() {
        if (gameDataInstallStarted || IsGameInstalled()) return;
        gameDataInstallStarted = true;
        ToLoad();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == LOADER_REQUEST_CODE) {
            gameDataInstallStarted = false;
            if (resultCode == RESULT_OK) {
                safeShowToast("Игра успешно установлена!");
                loadCurrentVersion();
                updatePlayButtonState();

                new Handler().postDelayed(() -> {
                    updatePlayButtonState();
                    if (IsGameInstalled()) {
                        safeShowToast("Игра готова к запуску!");
                    } else {
                        safeShowToast("Игра не найдена после установки. Попробуйте еще раз.");
                    }
                }, 1000);
            } else {
                safeShowToast("Установка не завершена");
            }
        }

        if (requestCode == UPDATE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                loadCurrentVersion();
                safeShowToast("Atualização instalada com sucesso!");
            } else {
                safeShowToast("Atualização não instalada. Iniciando a versão atual...");
                startGame();
            }
        }

        if (requestCode == LAUNCHER_UPDATE_REQUEST_CODE) {
            launcherInstallStarted = false;
            final String expectedVersion = pendingLauncherUpdate == null ? null :
                    pendingLauncherUpdate.getVersion();
            launcherLoadingHandler.postDelayed(() -> {
                if (expectedVersion != null && isLauncherVersionInstalled(expectedVersion)) {
                    safeShowToast("Launcher atualizado. O aplicativo será reiniciado.");
                    restartApp();
                } else {
                    safeShowToast("Instalação cancelada. O launcher atual continuará aberto.");
                    restoreLauncherLoadingText();
                    finishLauncherLoadingScreen();
                }
            }, 700L);
        }
    }

    private boolean isLauncherVersionInstalled(String expectedVersion) {
        try {
            android.content.pm.PackageInfo packageInfo = getPackageManager()
                    .getPackageInfo(getPackageName(), 0);
            String installedVersion = packageInfo.versionName;
            if (installedVersion == null || expectedVersion == null)
                return false;
            return installedVersion.equals(expectedVersion) ||
                    installedVersion.replace("build_", "").equals(expectedVersion.replace("build_", ""));
        } catch (Exception e) {
            return false;
        }
    }

    private void restartApp() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        }
    }

    public void InitLogic() {
        try {
            File f = new File(Api.GAME_CACHE_PATH, "SAMP/settings.ini");
            if (f.exists()) {
                String savedServer = readIniValue(f, "client", "server");
                if (savedServer != null && !savedServer.isEmpty()) {
                    selectedServer = savedServer;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void LoadNick() {
        try {
            File f = new File(Api.GAME_CACHE_PATH, "SAMP/settings.ini");
            if (f.exists()) {
                String savedNick = readIniValue(f, "client", "name");
                if (savedNick != null && !savedNick.isEmpty()) {
                    Preferences.setNick(savedNick);
                    if (nickname != null) nickname.setText(savedNick);
                } else {
                    if (nickname != null) nickname.setText("Name_Surname");
                    saveNickname();
                }
            } else {
                if (nickname != null) nickname.setText("Name_Surname");
                saveNickname();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (nickname != null) nickname.setText("Name_Surname");
            saveNickname();
        }
    }

    private void saveNickname() {
        try {
            if (nickname == null || nickname.getText() == null) return;
            String nickText = nickname.getText().toString().trim();
            if (checkValidNick(nickText)) {
                File settingsDir = new File(Api.GAME_CACHE_PATH, "SAMP");
                if (!settingsDir.exists()) settingsDir.mkdirs();
                File f = new File(settingsDir, "settings.ini");
                writeIniValue(f, "client", "name", nickText);
                Preferences.setNick(nickText);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean checkValidNick(String nick) {
        if (nick == null || nick.trim().isEmpty()) {
            safeShowToast("Введите никнейм");
            return false;
        }

        if (nick.length() < 3 || nick.length() > 20) {
            safeShowToast("Ник должен быть от 3 до 20 символов");
            return false;
        }

        if (!Pattern.matches("^[a-zA-Z_]+$", nick)) {
            safeShowToast("Ник может содержать только буквы и подчеркивания");
            return false;
        }

        if (!nick.contains("_")) {
            safeShowToast("Ник должен содержать символ подчеркивания (_)");
            return false;
        }

        return true;
    }

    private void safeShowToast(String message) {
        if (isToastShown) return;
        isToastShown = true;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                    new Handler().postDelayed(() -> isToastShown = false, 2000);
                } catch (Exception e) {
                    isToastShown = false;
                }
            }
        });
    }

    public void setSelectedServer(String serverName, String port) {
        this.selectedServer = serverName;
        this.serverPort = port;

        try {
            File serverIniFile = new File(Api.GAME_CACHE_PATH, "SAMP/serv.ini");
            writeIniValue(serverIniFile, "server", "name", serverName);
            writeIniValue(serverIniFile, "server", "port", port);

            File settingsFile = new File(Api.GAME_CACHE_PATH, "SAMP/settings.ini");
            writeIniValue(settingsFile, "client", "server", serverName);

            safeShowToast("Сервер изменен на: " + serverName);

        } catch (Exception e) {
            safeShowToast("Ошибка сохранения сервера");
        }
    }

    private void writeIniValue(File iniFile, String section, String key, String value) throws IOException {
        if (!iniFile.exists()) {
            iniFile.getParentFile().mkdirs();
            iniFile.createNewFile();
        }

        List<String> lines = new ArrayList<>();
        boolean fileExists = iniFile.exists();

        if (fileExists) {
            try (Scanner scanner = new Scanner(iniFile)) {
                while (scanner.hasNextLine()) {
                    lines.add(scanner.nextLine());
                }
            }
        }

        boolean sectionFound = false;
        boolean keyFound = false;
        List<String> newLines = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            if (line.equals("[" + section + "]")) {
                sectionFound = true;
                newLines.add(line);

                boolean keyAdded = false;
                for (int j = i + 1; j < lines.size(); j++) {
                    String nextLine = lines.get(j).trim();
                    if (nextLine.startsWith("[") && nextLine.endsWith("]")) {
                        if (!keyAdded) {
                            newLines.add(key + " = " + value);
                            keyFound = true;
                            keyAdded = true;
                        }
                        newLines.add(nextLine);
                        i = j;
                        break;
                    } else if (nextLine.startsWith(key + " =")) {
                        newLines.add(key + " = " + value);
                        keyFound = true;
                        keyAdded = true;
                        i = j;
                    } else if (j == lines.size() - 1) {
                        newLines.add(nextLine);
                        if (!keyAdded) {
                            newLines.add(key + " = " + value);
                            keyFound = true;
                        }
                        i = j;
                    } else {
                        newLines.add(nextLine);
                        i = j;
                    }
                }
            } else {
                newLines.add(lines.get(i));
            }
        }

        if (!sectionFound) {
            newLines.add("[" + section + "]");
            newLines.add(key + " = " + value);
            keyFound = true;
        } else if (!keyFound) {
            List<String> finalLines = new ArrayList<>();
            boolean inTargetSection = false;
            boolean keyAdded = false;

            for (String line : newLines) {
                finalLines.add(line);
                if (line.equals("[" + section + "]")) {
                    inTargetSection = true;
                } else if (inTargetSection && !keyAdded) {
                    if (line.startsWith("[") && line.endsWith("]")) {
                        finalLines.add(finalLines.size() - 1, key + " = " + value);
                        keyAdded = true;
                    }
                }
            }

            if (!keyAdded) {
                finalLines.add(key + " = " + value);
            }

            newLines = finalLines;
        }

        try (FileOutputStream fos = new FileOutputStream(iniFile)) {
            for (String line : newLines) {
                fos.write((line + "\n").getBytes());
            }
        }
    }

    private String readIniValue(File iniFile, String section, String key) throws IOException {
        if (!iniFile.exists()) {
            return null;
        }

        try (Scanner scanner = new Scanner(iniFile)) {
            boolean inTargetSection = false;

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();

                if (line.startsWith("[") && line.endsWith("]")) {
                    String currentSection = line.substring(1, line.length() - 1);
                    inTargetSection = currentSection.equals(section);
                } else if (inTargetSection && line.startsWith(key + " =")) {
                    return line.substring(key.length() + 3).trim();
                }
            }
        }

        return null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isInitialLoad) {
            LoadNick();
            loadServerSelection();
            updatePlayButtonState();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
}
