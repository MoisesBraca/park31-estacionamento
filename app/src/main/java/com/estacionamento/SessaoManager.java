package com.estacionamento;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.mindrot.jbcrypt.BCrypt;

public class SessaoManager {

    private static final String PREFS_NAME = "sessao_prefs";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_PERFIL = "perfil";
    private static final String KEY_TENTATIVAS = "tentativas";
    private static final String KEY_LOCKOUT_ATE = "lockout_ate";
    private static final String KEY_ULTIMA_ATIVIDADE = "ultima_atividade";

    private static final int MAX_TENTATIVAS = 3;
    private static final long LOCKOUT_DURATION_MS = 30_000;
    private static final long INACTIVIDADE_TIMEOUT_MS = 600_000;

    private static SessaoManager instance;
    private SharedPreferences prefs;
    private Handler inactivityHandler;
    private Runnable inactivityRunnable;
    private InactivityListener listener;

    public interface InactivityListener {
        void onSessionExpired();
    }

    public static synchronized SessaoManager getInstance() {
        if (instance == null) {
            instance = new SessaoManager();
        }
        return instance;
    }

    private SessaoManager() {
        inactivityHandler = new Handler(Looper.getMainLooper());
        inactivityRunnable = () -> {
            encerrarSessao();
            if (listener != null) listener.onSessionExpired();
        };
    }

    public void init(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // --- Gerenciamento de PIN ---

    public boolean possuiPin() {
        return prefs != null && prefs.contains(KEY_PIN_HASH);
    }

    public void criarPin(String pin, Perfil perfil) {
        String hash = BCrypt.hashpw(pin, BCrypt.gensalt());
        prefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .putString(KEY_PERFIL, perfil.name())
            .putInt(KEY_TENTATIVAS, 0)
            .remove(KEY_LOCKOUT_ATE)
            .commit();
    }

    public boolean validarPin(String pin) {
        String hash = prefs.getString(KEY_PIN_HASH, null);
        if (hash == null) return false;
        return BCrypt.checkpw(pin, hash);
    }

    // --- Bloqueio por tentativas ---

    public boolean estaBloqueado() {
        long lockoutAte = prefs.getLong(KEY_LOCKOUT_ATE, 0);
        if (lockoutAte == 0) return false;
        if (System.currentTimeMillis() >= lockoutAte) {
            prefs.edit().remove(KEY_LOCKOUT_ATE).putInt(KEY_TENTATIVAS, 0).commit();
            return false;
        }
        return true;
    }

    public long getTempoRestanteBloqueio() {
        long lockoutAte = prefs.getLong(KEY_LOCKOUT_ATE, 0);
        return Math.max(0, lockoutAte - System.currentTimeMillis());
    }

    public void registrarTentativaFalha() {
        int tentativas = prefs.getInt(KEY_TENTATIVAS, 0) + 1;
        if (tentativas >= MAX_TENTATIVAS) {
            long lockoutAte = System.currentTimeMillis() + LOCKOUT_DURATION_MS;
            prefs.edit()
                .putInt(KEY_TENTATIVAS, tentativas)
                .putLong(KEY_LOCKOUT_ATE, lockoutAte)
                .commit();
        } else {
            prefs.edit().putInt(KEY_TENTATIVAS, tentativas).commit();
        }
    }

    public int getTentativasRestantes() {
        return MAX_TENTATIVAS - prefs.getInt(KEY_TENTATIVAS, 0);
    }

    // --- Sessão ativa ---

    public void iniciarSessao() {
        prefs.edit().putLong(KEY_ULTIMA_ATIVIDADE, System.currentTimeMillis()).commit();
    }

    public boolean isSessionValid() {
        long ultima = prefs.getLong(KEY_ULTIMA_ATIVIDADE, 0);
        return ultima > 0 && (System.currentTimeMillis() - ultima) < INACTIVIDADE_TIMEOUT_MS;
    }

    public void encerrarSessao() {
        prefs.edit().remove(KEY_ULTIMA_ATIVIDADE).commit();
        pararTimer();
    }

    public void reiniciarTimer() {
        if (isSessionValid()) {
            prefs.edit().putLong(KEY_ULTIMA_ATIVIDADE, System.currentTimeMillis()).commit();
        }
        pararTimer();
        inactivityHandler.postDelayed(inactivityRunnable, INACTIVIDADE_TIMEOUT_MS);
    }

    private void pararTimer() {
        inactivityHandler.removeCallbacks(inactivityRunnable);
    }

    public void setInactivityListener(InactivityListener listener) {
        this.listener = listener;
    }

    // --- Perfil ---

    public Perfil getPerfil() {
        try {
            return Perfil.valueOf(prefs.getString(KEY_PERFIL, "OPERADOR"));
        } catch (Exception e) {
            return Perfil.OPERADOR;
        }
    }
}
