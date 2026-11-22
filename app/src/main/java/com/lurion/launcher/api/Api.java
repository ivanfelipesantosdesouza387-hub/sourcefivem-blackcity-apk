package com.nexion.launcher.api;

public class Api {
    private static final String BASE_URL = "https://pride-russia-game.ru";
    private static final String DATA_TEST_URL = BASE_URL + "/data_test";

    public static final String GAME_CACHE_ZIP =
        "https://github.com/ivanfelipesantosdesouza387-hub/samp-launcher-data/releases/download/data-v1/BlackCity-Dados-Completos.zip";
    public static final long GAME_CACHE_ZIP_SIZE = 834371253L;
    public static final String GAME_CACHE_ZIP_SHA256 =
        "36a7401188acebb6dbe4d3b534fb71052048bdf2ccd4afe8047879cf14c2b4d5";
    public static final String GAME_SERVER_HOST = "51.38.205.167";
    public static final String GAME_SERVER_PORT = "18015";

    public static final String UPDATE_JSON = DATA_TEST_URL + "/update.json";
    public static final String SERVERS_JSON = DATA_TEST_URL + "/servers.json";

    /*
     * Diretório externo específico do aplicativo. Em Android 11+ ele pode ser
     * gravado sem READ/WRITE/MANAGE_EXTERNAL_STORAGE.
     */
    public static final String STORAGE_ROOT = "/storage/emulated/0/Android/data/com.nexion.online/files";
    public static final String GAME_CACHE_PATH = STORAGE_ROOT + "/";
    public static final String GAME_INSTALL_PATH = GAME_CACHE_PATH + "downloand/";
    public static final String GAME_TEXDB_PATH = GAME_CACHE_PATH + "texdb/";
    public static final String GAME_DATA_VERSION = "1.0.0";
    public static final String GAME_DATA_MARKER = GAME_CACHE_PATH + ".blackcity-data-installed";

    public static final String FTEAM_FILE = GAME_CACHE_PATH + "fteam.txt";
    public static final String VERSION_FILE = GAME_CACHE_PATH + "version.txt";

    public static final String TELEGRAM_URL = "https://t.me/priderussiacrmp";
    public static final String VK_URL = "https://vk.com/priderussiacrmp";

    public static final String CLIENT_LOCK_URL = BASE_URL + "/lock.json";

    // Endpoint HTTPS próprio para verificar atualizações do launcher
    public static final String LAUNCHER_UPDATE_URL =
        "https://raw.githubusercontent.com/ivanfelipesantosdesouza387-hub/sourcefivem-blackcity-apk/main/launcher_update.json";

    public static String getUpdateUrl(String version) {
        return BASE_URL + "/data/update.zip";
    }

    public static boolean isStorageAvailable() {
        java.io.File gameDir = new java.io.File(GAME_CACHE_PATH);
        if (!gameDir.exists() && !gameDir.mkdirs()) {
            return false;
        }
        return gameDir.isDirectory() && gameDir.canWrite();
    }
}
