package com.nexion.launcher.activity;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.appcompat.app.AppCompatActivity;

import com.akexorcist.roundcornerprogressbar.RoundCornerProgressBar;
import com.nexion.online.R;
import com.nexion.launcher.api.Api;
import com.nexion.launcher.util.Util;

import com.liulishuo.filedownloader.BaseDownloadTask;
import com.liulishuo.filedownloader.FileDownloadSampleListener;
import com.liulishuo.filedownloader.FileDownloader;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class InstallActivity extends AppCompatActivity {
    private static final String CHANNEL_ID = "game_download_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final int PERMISSION_REQUEST_CODE = 1001;

    RoundCornerProgressBar progressbar;
    File folder;
    private NotificationManagerCompat notificationManager;
    private NotificationCompat.Builder notificationBuilder;
    private Handler mainHandler;
    private boolean isDownloadInProgress = false;
    private static boolean isToastShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainHandler = new Handler(Looper.getMainLooper());

        createNotificationChannel();
        notificationManager = NotificationManagerCompat.from(this);

        initializeUI();
    }

    

    private void initializeUI() {
        setContentView(R.layout.activity_install);
        progressbar = findViewById(R.id.progress_bar);

        FileDownloader.setup(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        showStartNotification();
        startDownload();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Baixando jogo",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Notificações de download e instalação do jogo");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void showStartNotification() {
        Intent intent = new Intent(this, InstallActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Baixando jogo")
                .setContentText("Preparando o download...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(0, 0, true);

        if (notificationManager.areNotificationsEnabled()) {
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
        }
    }

    private void updateNotification(String title, String text, int progress, int maxProgress, boolean indeterminate) {
        if (notificationBuilder != null) {
            notificationBuilder
                    .setContentTitle(title)
                    .setContentText(text)
                    .setProgress(maxProgress, progress, indeterminate);

            if (notificationManager.areNotificationsEnabled()) {
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
            }
        }
    }

    private void showCompletionNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Instalação concluída")
                .setContentText("Jogo instalado com sucesso!")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        if (notificationManager.areNotificationsEnabled()) {
            notificationManager.notify(NOTIFICATION_ID + 1, builder.build());
        }
    }

    private void returnToMainActivity() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private String formatBytes(long bytes) {
        return bytes <= 0 ? "0 MB" : Util.bytesIntoHumanReadable(bytes);
    }

    private void updateInstallProgress(String stage, String fileName, int progress,
                                       long downloadedBytes, long totalBytes) {
        mainHandler.post(() -> {
            TextView stageView = findViewById(R.id.loading_text);
            TextView fileView = findViewById(R.id.loading_file);
            TextView sizeView = findViewById(R.id.loading_percent);
            RoundCornerProgressBar bar = findViewById(R.id.progress_bar);

            if (stageView != null)
                stageView.setText(stage);
            if (fileView != null) {
                String visibleName = fileName == null || fileName.trim().isEmpty()
                        ? "preparando..." : fileName;
                fileView.setText("Baixando: " + visibleName + " (" +
                        Math.max(0, Math.min(100, progress)) + "%)");
            }
            if (sizeView != null) {
                sizeView.setText(formatBytes(downloadedBytes) + " de " +
                        formatBytes(totalBytes));
            }
            if (bar != null)
                bar.setProgress(Math.max(0, Math.min(100, progress)));
        });
    }

    private void updateExtractionProgress(String fileName, int progress,
                                          long extractedBytes, long totalBytes) {
        updateInstallProgress("Extraindo arquivos", fileName, progress,
                extractedBytes, totalBytes);
    }

    public void startDownload() {
        if (isDownloadInProgress) {
            return;
        }
        isDownloadInProgress = true;

        if (!Api.isStorageAvailable()) {
            safeShowToast("Armazenamento externo indisponível");
            returnToMainActivity();
            return;
        }

        folder = new File(Api.GAME_CACHE_PATH);
        if (!folder.exists() && !folder.mkdirs()) {
            safeShowToast("Não foi possível criar a pasta de download");
            returnToMainActivity();
            return;
        }

        String url = Api.GAME_CACHE_ZIP;
        File existingArchive = new File(folder, "cache.zip");
        if (isGameDataInstalled()) {
            updateInstallProgress("Data já instalada", "concluído", 100, 1, 1);
            afterDownload();
            return;
        }
        if (existingArchive.isFile() && existingArchive.length() == Api.GAME_CACHE_ZIP_SIZE) {
            try {
                verifyDownloadedArchive(existingArchive);
                updateInstallProgress("Retomando instalação", "cache.zip completo", 100, existingArchive.length(), existingArchive.length());
                UnZipCache();
                return;
            } catch (Exception ignored) {
                existingArchive.delete();
            }
        }
        updateInstallProgress("Baixando data", "cache.zip", 0, existingArchive.length(), Api.GAME_CACHE_ZIP_SIZE);
        createDownloadTask(url, folder.getPath()).start();

        updateNotification("Baixando jogo", "Iniciando download...", 0, 100, true);
    }

    private BaseDownloadTask createDownloadTask(String url, String path) {
        return FileDownloader.getImpl().create(url)
                .setPath(path + "/cache.zip")
                .setCallbackProgressTimes(100)
                .setMinIntervalUpdateSpeed(100)
                .setListener(new FileDownloadSampleListener() {

                    @Override
                    protected void pending(BaseDownloadTask task, int soFarBytes, int totalBytes) {
                        updateNotification("Baixando jogo", "Preparando o download...", 0, 100, true);
                    }

                    @Override
                    protected void progress(BaseDownloadTask task, int soFarBytes, int totalBytes) {
                        if (totalBytes <= 0) return;

                        long progressPercent = soFarBytes * 100L / totalBytes;
                        updateInstallProgress("Atualizando arquivos", "cache.zip",
                                (int) progressPercent, soFarBytes, totalBytes);

                        updateNotification("Baixando jogo",
                                "Download: " + formatBytes(soFarBytes) + " / " + formatBytes(totalBytes),
                                (int) progressPercent, 100, false);
                    }

                    @Override
                    protected void error(BaseDownloadTask task, Throwable e) {
                        isDownloadInProgress = false;
                        safeShowToast("Ocorreu um erro. Inicie a instalação novamente");
                        updateNotification("Erro", "Erro no download", 0, 100, false);
                        returnToMainActivity();
                    }

                    @Override
                    protected void connected(BaseDownloadTask task, String et, boolean isContinue, int soFarBytes, int totalBytes) {
                        updateNotification("Baixando jogo", "Conectado ao servidor", 0, 100, true);
                    }

                    @Override
                    protected void completed(BaseDownloadTask task) {
                        isDownloadInProgress = false;
                        try {
                            verifyDownloadedArchive(new File(task.getPath()));
                        } catch (Exception e) {
                            safeShowToast("Data inválida ou incompleta: " + e.getMessage());
                            updateNotification("Erro", "Arquivo inválido", 0, 100, false);
                            returnToMainActivity();
                            return;
                        }
                        updateInstallProgress("Atualizando arquivos", "preparando arquivos", 0, 0, 0);
                        updateNotification("Baixando jogo", "Download concluído, extraindo...", 100, 100, false);
                        UnZipCache();
                    }
                });
    }

    public void UnZipCache(){
        final String inputFilePath = Api.GAME_CACHE_PATH + "/cache.zip";
        final String outputPath = Api.GAME_CACHE_PATH;

        new Thread(() -> {
            try {
                File zipFile = new File(inputFilePath);
                if (!zipFile.exists()) {
                    throw new Exception("Arquivo ZIP não encontrado: " + inputFilePath);
                }

                File outputDir = new File(outputPath);
                if (!outputDir.exists() && !outputDir.mkdirs()) {
                    throw new Exception("Não foi possível criar o diretório de destino: " + outputPath);
                }

                long extractedTotalBytes = extractZipWithProgress(zipFile, outputDir);

                // Algumas versões da data não possuem a pasta "downloand";
                // validar pelo arquivo real do jogo, que é o critério confiável.
                File extractedGameFile = new File(Api.GAME_TEXDB_PATH, "gta3.img");
                if (!extractedGameFile.isFile() || extractedGameFile.length() <= 600_000_000L) {
                    throw new Exception("Os arquivos da data não foram extraídos corretamente");
                }

                enforceGameServerSettings(outputDir);
                enforceNativeGameServerSettings(outputDir);
                createFteamFile(outputPath);
                createVersionFile(outputPath);
                createInstallMarker();
                if (zipFile.exists() && !zipFile.delete()) {
                    android.util.Log.w("InstallActivity", "Não foi possível remover o cache.zip após a extração");
                }

                mainHandler.post(() -> {
                    updateInstallProgress("Atualização concluída", "Download concluído", 100,
                            extractedTotalBytes, extractedTotalBytes);
                    updateNotification("Sucesso", "Extração concluída", 100, 100, false);
                    afterDownload();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    safeShowToast("Erro na extração: " + e.getMessage());
                    updateNotification("Erro", "Erro na extração", 0, 100, false);
                    returnToMainActivity();
                });
            }
        }, "NexionCacheExtraction").start();
    }

    private long extractZipWithProgress(File zipFile, File outputDir) throws Exception {
        String outputRoot = outputDir.getCanonicalPath() + File.separator;
        List<ZipEntry> entries = new ArrayList<>();
        long totalBytes = 0L;

        try (ZipFile archive = new ZipFile(zipFile)) {
            java.util.Enumeration<? extends ZipEntry> enumeration = archive.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                entries.add(entry);
                if (!entry.isDirectory() && entry.getSize() > 0)
                    totalBytes += entry.getSize();
            }

            String archiveRoot = detectSingleArchiveRoot(entries);

            long extractedBytes = 0L;
            int lastProgress = -1;
            for (ZipEntry entry : entries) {
                String entryName = stripArchiveRoot(entry.getName(), archiveRoot);
                if (entryName.isEmpty()) continue;
                File target = new File(outputDir, entryName);
                String targetPath = target.getCanonicalPath();
                if (!targetPath.equals(outputDir.getCanonicalPath()) &&
                        !targetPath.startsWith(outputRoot)) {
                    throw new IOException("Entrada ZIP inválida: " + entryName);
                }

                if (entry.isDirectory()) {
                    if (!target.exists() && !target.mkdirs())
                        throw new IOException("Não foi possível criar: " + targetPath);
                    continue;
                }

                File parent = target.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs())
                    throw new IOException("Não foi possível criar: " + parent.getAbsolutePath());

                try (InputStream input = new BufferedInputStream(archive.getInputStream(entry));
                     OutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                        extractedBytes += count;
                        int progress = totalBytes > 0
                                ? (int) Math.min(99L, extractedBytes * 100L / totalBytes)
                                : 0;
                        if (progress != lastProgress) {
                            lastProgress = progress;
                            String visibleName = entryName.replace('\\', '/');
                            updateExtractionProgress(visibleName, progress,
                                    extractedBytes, totalBytes);
                            updateNotification("Extração", "Baixando: " + visibleName,
                                    progress, 100, false);
                        }
                    }
                    output.flush();
                }
            }

            updateExtractionProgress("concluindo arquivos", 100, totalBytes, totalBytes);
            updateNotification("Extração", "Arquivos extraídos", 100, 100, false);
            return totalBytes;
        }
    }

    private String detectSingleArchiveRoot(List<ZipEntry> entries) {
        String root = null;
        for (ZipEntry entry : entries) {
            String name = entry.getName().replace('\\', '/');
            int slash = name.indexOf('/');
            if (slash <= 0) return "";
            String candidate = name.substring(0, slash + 1);
            if (root == null) root = candidate;
            else if (!root.equals(candidate)) return "";
        }
        return root == null ? "" : root;
    }

    private String stripArchiveRoot(String name, String root) {
        String normalized = name.replace('\\', '/');
        if (!root.isEmpty() && normalized.startsWith(root))
            normalized = normalized.substring(root.length());
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        return normalized;
    }

    private void enforceNativeGameServerSettings(File outputDir) throws IOException {
        File settingsFile = new File(outputDir, "SAMP/settings.ini");
        if (!settingsFile.exists()) {
            File parent = settingsFile.getParentFile();
            if (parent != null) parent.mkdirs();
            settingsFile.createNewFile();
        }
        java.util.List<String> lines = new java.util.ArrayList<>();
        try (java.util.Scanner scanner = new java.util.Scanner(settingsFile, "UTF-8")) {
            while (scanner.hasNextLine()) lines.add(scanner.nextLine());
        }
        java.util.List<String> updated = new java.util.ArrayList<>();
        boolean inClient=false, host=false, port=false;
        for (String line: lines) {
            String t=line.trim();
            if (t.startsWith("[") && t.endsWith("]")) {
                if (inClient) { if (!host) updated.add("host = " + Api.GAME_SERVER_HOST); if (!port) updated.add("port = " + Api.GAME_SERVER_PORT); }
                inClient=t.equalsIgnoreCase("[client]"); host=false; port=false; updated.add(line); continue;
            }
            if (inClient && (t.startsWith("host=") || t.startsWith("host ="))) { updated.add("host = " + Api.GAME_SERVER_HOST); host=true; }
            else if (inClient && (t.startsWith("port=") || t.startsWith("port ="))) { updated.add("port = " + Api.GAME_SERVER_PORT); port=true; }
            else updated.add(line);
        }
        if (inClient) { if (!host) updated.add("host = " + Api.GAME_SERVER_HOST); if (!port) updated.add("port = " + Api.GAME_SERVER_PORT); }
        if (!inClient) { updated.add("[client]"); updated.add("host = " + Api.GAME_SERVER_HOST); updated.add("port = " + Api.GAME_SERVER_PORT); }
        try (java.io.FileWriter writer = new java.io.FileWriter(settingsFile, false)) { for (String line: updated) writer.write(line + System.lineSeparator()); }
    }

    private void createInstallMarker() throws IOException {
        try (java.io.FileWriter writer = new java.io.FileWriter(new File(Api.GAME_DATA_MARKER), false)) {
            writer.write(Api.GAME_DATA_VERSION + "\n" + Api.GAME_CACHE_ZIP_SHA256 + "\n");
        }
    }

    private boolean isGameDataInstalled() {
        File marker = new File(Api.GAME_DATA_MARKER);
        File fteam = new File(Api.FTEAM_FILE);
        File version = new File(Api.VERSION_FILE);
        File gta3 = new File(Api.GAME_TEXDB_PATH, "gta3.img");
        boolean filesReady = gta3.isFile() && gta3.length() > 600_000_000L;
        boolean knownInstall = marker.isFile() || fteam.isFile() || version.isFile();
        if (filesReady && knownInstall && !marker.isFile()) {
            try (java.io.FileWriter writer = new java.io.FileWriter(marker, false)) {
                writer.write(Api.GAME_DATA_VERSION + "\n");
            } catch (Exception ignored) { }
        }
        return filesReady && knownInstall;
    }

    private void enforceGameServerSettings(File outputDir) throws IOException {
        File settingsFile = new File(outputDir, "data/settings.ini");
        if (!settingsFile.isFile()) return;

        List<String> lines = new ArrayList<>();
        try (java.util.Scanner scanner = new java.util.Scanner(settingsFile, "UTF-8")) {
            while (scanner.hasNextLine()) lines.add(scanner.nextLine());
        }

        List<String> updated = new ArrayList<>();
        boolean inClient = false;
        boolean hostWritten = false;
        boolean portWritten = false;
        for (String original : lines) {
            String trimmed = original.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                if (inClient) {
                    if (!hostWritten) updated.add("host = " + Api.GAME_SERVER_HOST);
                    if (!portWritten) updated.add("port = " + Api.GAME_SERVER_PORT);
                }
                inClient = trimmed.equalsIgnoreCase("[client]");
                hostWritten = false;
                portWritten = false;
                updated.add(original);
                continue;
            }
            if (inClient && (trimmed.startsWith("host =") || trimmed.startsWith("host="))) {
                updated.add("host = " + Api.GAME_SERVER_HOST);
                hostWritten = true;
            } else if (inClient && (trimmed.startsWith("port =") || trimmed.startsWith("port="))) {
                updated.add("port = " + Api.GAME_SERVER_PORT);
                portWritten = true;
            } else {
                updated.add(original);
            }
        }
        if (inClient) {
            if (!hostWritten) updated.add("host = " + Api.GAME_SERVER_HOST);
            if (!portWritten) updated.add("port = " + Api.GAME_SERVER_PORT);
        }

        try (FileWriter writer = new FileWriter(settingsFile, false)) {
            for (String line : updated) writer.write(line + System.lineSeparator());
        }
    }

    private void verifyDownloadedArchive(File archive) throws Exception {
        if (!archive.isFile()) throw new IOException("ZIP não encontrado");
        if (archive.length() != Api.GAME_CACHE_ZIP_SIZE)
            throw new IOException("tamanho diferente do esperado");

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(archive)) {
            byte[] buffer = new byte[1024 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1)
                digest.update(buffer, 0, count);
        }

        StringBuilder actual = new StringBuilder();
        for (byte value : digest.digest())
            actual.append(String.format(java.util.Locale.ROOT, "%02x", value));
        if (!Api.GAME_CACHE_ZIP_SHA256.equalsIgnoreCase(actual.toString()))
            throw new IOException("SHA-256 não confere");
    }

    private void createFteamFile(String outputPath) {
        try {
            File fteamFile = new File(outputPath, "fteam.txt");
            java.io.FileWriter writer = new java.io.FileWriter(fteamFile);
            writer.write("created by fteam (ferkus team)");
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createVersionFile(String outputPath) {
        try {
            File versionFile = new File(outputPath, "version.txt");
            java.io.FileWriter writer = new java.io.FileWriter(versionFile);
            writer.write(Api.GAME_DATA_VERSION);
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void afterDownload(){
        
        String gamePath = Api.GAME_CACHE_PATH;
        createFteamFile(gamePath);
        createVersionFile(gamePath);

        showCompletionNotification();
        safeShowToast("Jogo instalado com sucesso!");
        mainHandler.postDelayed(() -> {
            setResult(RESULT_OK);
            returnToMainActivity();
        }, 1000);
    }

    private void safeShowToast(String message) {
        if (isToastShown) return;
        isToastShown = true;

        mainHandler.post(() -> {
            try {
                Toast.makeText(InstallActivity.this, message, Toast.LENGTH_SHORT).show();
                mainHandler.postDelayed(() -> isToastShown = false, 2000);
            } catch (Exception e) {
                isToastShown = false;
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isDownloadInProgress) {
            FileDownloader.getImpl().pauseAll();
        }
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
    }
}
