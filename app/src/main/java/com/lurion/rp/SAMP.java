package com.nexion.online;

import android.os.Bundle;
import android.util.Log;
import android.content.Intent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;

import android.view.ViewGroup;
import android.widget.FrameLayout;


import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.nexion.online.ui.CustomKeyboard;
import com.nexion.online.ui.LoadingScreen;
import com.nexion.online.ui.dialog.DialogManager;


// @Obfuscate // This line is commented out to prevent method renaming/removal
public class SAMP extends GTASA implements CustomKeyboard.InputListener, HeightProvider.HeightListener {
    private static final String TAG = "SAMP";
    private static SAMP instance;

    private DialogManager mDialog;

    private CustomKeyboard mKeyboard;
    private HeightProvider mHeightProvider;

    private LoadingScreen mLoadingScreen;
    private boolean mMusicStarted = false;




    public static SAMP getInstance() {
        return instance;
    }

    private void showTab()
    {

    }

    private void hideTab()
    {

    }

    private void setTab(int id, String name, int score, int ping)
    {

    }

    private void clearTab()
    {

    }

    /**
     * Este método é chamado pelo código nativo (C++) para exibir a tela de carregamento.
     * Nós o executamos na thread principal da UI para que as animações funcionem corretamente.
     */
    private void showLoadingScreen()
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mLoadingScreen != null) {
                    mLoadingScreen.show();
                }
            }
        });
    }

    /**
     * Pula direto para "Autenticando..." quando o C++ termina a inicialização.
     */
    private void skipToAuthenticating()
    {
        if (mLoadingScreen != null) {
            mLoadingScreen.skipToAuthenticating();
        }
    }

    /**
     * Chamado quando o C++ termina de carregar os recursos do jogo.
     */
    private void onGameReady()
    {
        if (mLoadingScreen != null) {
            mLoadingScreen.onGameReady();
        }
    }

    /**
     * Chamado quando o C++ inicia a conexão de rede.
     */
    private void onNetworkReady()
    {
        if (mLoadingScreen != null) {
            mLoadingScreen.onNetworkReady();
        }
    }

    /**
     * Este método é chamado pelo código nativo (C++) para ocultar a tela de carregamento.
     */
    private void hideLoadingScreen()
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mLoadingScreen != null) {
                    mLoadingScreen.stopMusic();
                    mLoadingScreen.hide();
                }
            }
        });
    }

    /**
     * Atualiza o texto de status do carregamento. Chamado pelo código nativo (C++).
     */
    private void updateLoadingStatus(final String status)
    {
        if (mLoadingScreen != null) {
            mLoadingScreen.updateStatus(status);
        }
    }

    /**
     * Inicia a sequência automática de status de carregamento.
     */
    private void startLoadingSequence()
    {
        if (mLoadingScreen != null) {
            mLoadingScreen.startLoadingSequence();
        }
    }

    /**
     * Configura a playlist de música e começa a tocar. Chamado pelo código nativo (C++).
     */
    private void setMusicPlaylist(final String[] tracks, final String title)
    {
        if (mLoadingScreen != null) {
            mLoadingScreen.setPlaylist(tracks, title);
        }
    }

    /**
     * Para a música da tela de carregamento. Chamado pelo código nativo (C++).
     */
    private void stopLoadingMusic()
    {
        if (mLoadingScreen != null) {
            mLoadingScreen.stopMusic();
        }
    }

    public void setPauseState(boolean pause) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (pause) {
                    mDialog.hideWithoutReset();
                }
                else {
                    if(mDialog.isShow)
                        mDialog.showWithOldContent();
                }
            }
        });
    }



    public void exitGame(){
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false);

        finishAndRemoveTask();
        System.exit(0);
    }

    /**
     * Chamado pelo código nativo (C++) quando o jogador toca em
     * "CLIQUE AQUI PARA REINICIAR" na tela de conexão perdida.
     * Volta para a tela inicial do launcher.
     */
    public void goToLauncher(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Esconde a superficie do jogo e deixa a janela preta ANTES de
                    // abrir a launcher: impede que o ultimo frame do GTA (ainda
                    // renderizando) apareca atras da tela inicial.
                    try {
                        android.view.SurfaceView surfaceView = GetSurfaceView();
                        if (surfaceView != null) {
                            surfaceView.setVisibility(android.view.View.GONE);
                        }
                        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK));
                    } catch (Exception ignored) {
                    }

                    // Traz a MainActivity (tela inicial do launcher) para o topo.
                    Intent launcherIntent = new Intent(SAMP.this, com.nexion.launcher.activity.MainActivity.class);
                    launcherIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(launcherIntent);

                    // Mata o processo IMEDIATAMENTE, antes que o onDestroy rode o
                    // cleanup nativo do GTASA (systemCleanup -> cleanup() destrói o
                    // mutex da fila de eventos EGL enquanto a thread NVEvent ainda
                    // espera eventos -> pthread_mutex_lock on destroyed mutex ->
                    // SIGABRT). O exitGame() original nunca crashava justamente
                    // porque faz System.exit(0) antes do cleanup. O Android
                    // restaura a MainActivity (launcher) quando o processo voltar.
                    finishAndRemoveTask();
                    System.exit(0);
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao voltar para o launcher: " + e.getMessage());
                }
            }
        });
    }

    public void showDialog(int dialogId, int dialogTypeId, byte[] bArr, byte[] bArr2, byte[] bArr3, byte[] bArr4) {
        final String caption = new String(bArr);
        final String content = new String(bArr2);
        final String leftBtnText = new String(bArr3);
        final String rightBtnText = new String(bArr4);
        runOnUiThread(() -> { this.mDialog.show(dialogId, dialogTypeId, caption, content, leftBtnText, rightBtnText); });
    }

    public void showEditObject()
    {
        Log.d("SAMP_JNI_FIX", "showEditObject() called from native code. Doing nothing.");
    }

    public void hideEditObject()
    {
        Log.d("SAMP_JNI_FIX", "hideEditObject() called from native code. Doing nothing.");
    }

    // Native functions that need empty counterparts in Java
    private native void onInputEnd(byte[] str);

    // Empty methods to satisfy native calls
    private void showKeyboard()
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d("AXL", "showKeyboard()");
                mKeyboard.ShowInputLayout();
            }
        });
    }

    private void hideKeyboard()
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mKeyboard.HideInputLayout();
            }
        });
    }

    public void OnInputEnd(String str) {
        // This function is potentially called by a UI element, which then calls the native onInputEnd
        byte[] toReturn = str.getBytes();

        try {
            onInputEnd(toReturn);
        }
        catch (UnsatisfiedLinkError e5) {
            Log.e(TAG, e5.getMessage());
        }
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {

        Log.i(TAG, "**** onCreate");
        super.onCreate(savedInstanceState);

        //if(!SignatureChecker.isSignatureValid(this, getPackageName()))
        //{
        //Toast.makeText(this, "Use original launcher! No remake", Toast.LENGTH_LONG).show();
        //return;
        //}

        //mHeightProvider = new HeightProvider(this);

        mDialog = new DialogManager(this);

        mKeyboard = new CustomKeyboard(this);

        mLoadingScreen = new LoadingScreen(this);

        // --- A solução final está aqui ---
        // Assim que a activity é criada, exibimos a tela de carregamento.
        // Não esperamos mais pela chamada vinda do C++.
        showLoadingScreen();

        // Inicia a música de fundo automaticamente
        // A playlist será configurada pelo C++ quando os recursos estiverem prontos
        // Por enquanto, apenas atualizamos o status inicial
        if (mLoadingScreen != null) {
            mLoadingScreen.updateStatus(LoadingScreen.STATUS_RESOURCES);
        }

        instance = this;

        try {
            initializeSAMP();
        } catch (UnsatisfiedLinkError e5) {
            Log.e(TAG, e5.getMessage());
        }



    }

    private native void initializeSAMP();



    @Override
    public void onStart() {
        Log.i(TAG, "**** onStart");
        super.onStart();
    }

    @Override
    public void onRestart() {
        Log.i(TAG, "**** onRestart");
        super.onRestart();
    }

    @Override
    public void onResume() {
        Log.i(TAG, "**** onResume");
        super.onResume();
        //mHeightProvider.init(view);
    }

    @Override
    public void onPostResume() {
        super.onPostResume();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
    }

    public native void onEventBackPressed();

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        onEventBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_BACK)
        {
            onEventBackPressed();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onPause() {
        Log.i(TAG, "**** onPause");
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.i(TAG, "**** onStop");
        super.onStop();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "**** onDestroy");
        if (mLoadingScreen != null) {
            mLoadingScreen.releaseMusic();
        }
        super.onDestroy();
    }

    @Override
    public void onHeightChanged(int orientation, int height) {
        //mDialog.onHeightChanged(height);
    }

    public void setKeyboardOffset(int offset) {
        nativeSetKeyboardOffset(offset);
    }

    private native void nativeSetKeyboardOffset(int offset);

    public void updateKeyboardInput(String text) {
        nativeUpdateKeyboardInput(text);
    }

    private native void nativeUpdateKeyboardInput(String text);



}

