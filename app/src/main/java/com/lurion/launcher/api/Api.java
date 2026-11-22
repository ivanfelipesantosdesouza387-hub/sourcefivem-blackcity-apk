package com.nexion.launcher.api;

import android.content.Context;

public class Api {
    private static final String BASE_URL = "https://pride-russia-game.ru";
    private static final String DATA_TEST_URL = BASE_URL + "/data_test";

    public static final String GAME_CACHE_ZIP =
        "https://github.com/ivanfelipesantosdesouza387-hub/samp-launcher-data/releases/download/data-v4/BlackCity-Dados-Completos.zip";
    public static final long GAME_CACHE_ZIP_SIZE = 1689069820L;
    public static final String GAME_CACHE_ZIP_SHA256 =
        "ed85114aa9a096b2f3ebc9ccc8a178a82a0f3b17efa1ba3bbe9bc848e7c37818";
    public static final String GAME_SERVER_HOST = "51.38.205.167";
    public static final String GAME_SERVER_PORT = "18015";

    public static final String UPDATE_JSON = DATA_TEST_URL + "/update.json";
    public static final String SERVERS_JSON = DATA_TEST_URL + "/servers.json";

    /*
     * Diretório externo específico do aplicativo. Em Android 11+ ele pode ser
     * gravado sem READ/WRITE/MANAGE_EXTERNAL_STORAGE.
     */
    public static String STORAGE_ROOT = "/storage/emulated/0/Android/data/com.nexion.online/files";
    public static String GAME_CACHE_PATH = STORAGE_ROOT + "/";
    public static String GAME_INSTALL_PATH = GAME_CACHE_PATH + "downloand/";
    public static String GAME_TEXDB_PATH = GAME_CACHE_PATH + "texdb/";
    public static final String GAME_DATA_VERSION = "1.0.0";
    public static String GAME_DATA_MARKER = GAME_CACHE_PATH + ".blackcity-data-installed";
    public static final String INSTALL_PREFS = "blackcity_launcher_state";
    public static final String INSTALL_COMPLETE_KEY = "game_data_install_complete";

    public static String FTEAM_FILE = GAME_CACHE_PATH + "fteam.txt";
    public static String VERSION_FILE = GAME_CACHE_PATH + "version.txt";

    public static final String TELEGRAM_URL = "https://t.me/priderussiacrmp";
    public static final String VK_URL = "https://vk.com/priderussiacrmp";

    public static final String CLIENT_LOCK_URL = BASE_URL + "/lock.json";

    // Endpoint HTTPS próprio para verificar atualizações do launcher
    public static final String LAUNCHER_UPDATE_URL =
        "https://raw.githubusercontent.com/ivanfelipesantosdesouza387-hub/sourcefivem-blackcity-apk/main/launcher_update.json";

    public static String getUpdateUrl(String version) {
        return BASE_URL + "/data/update.zip";
    }

    public static synchronized void configureStorage(Context context) {
        if (context == null) return;
        java.io.File external = context.getExternalFilesDir(null);
        if (external == null) return;
        STORAGE_ROOT = external.getAbsolutePath();
        GAME_CACHE_PATH = STORAGE_ROOT + "/";
        GAME_INSTALL_PATH = GAME_CACHE_PATH + "downloand/";
        GAME_TEXDB_PATH = GAME_CACHE_PATH + "texdb/";
        GAME_DATA_MARKER = GAME_CACHE_PATH + ".blackcity-data-installed";
        FTEAM_FILE = GAME_CACHE_PATH + "fteam.txt";
        VERSION_FILE = GAME_CACHE_PATH + "version.txt";
    }

    public static boolean isStorageAvailable() {
        java.io.File gameDir = new java.io.File(GAME_CACHE_PATH);
        if (!gameDir.exists() && !gameDir.mkdirs()) {
            return false;
        }
        return gameDir.isDirectory() && gameDir.canWrite();
    }
}
