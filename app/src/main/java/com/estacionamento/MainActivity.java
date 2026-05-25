package com.estacionamento;

import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.estacionamento.R;
import com.estacionamento.databinding.ActivityMainBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private NavController navController;
    private android.os.Handler syncHandler;
    private Runnable syncRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SessaoManager.getInstance().init(this);
        Preferences.init(this);

        SessaoManager.getInstance().setInactivityListener(() -> {
            if (!isFinishing()) {
                logout();
            }
        });

        syncHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        syncRunnable = new Runnable() {
            @Override
            public void run() {
                LicencaHelper.verificarLicenca(MainActivity.this, (status, expiracao, erro) -> {
                    if (erro == null) {
                        runOnUiThread(() -> {
                            if (!"ATIVO".equals(status) || (expiracao > 0 && System.currentTimeMillis() > expiracao)) {
                                logout(); // Redireciona para a tela de bloqueio do PinActivity
                            }
                        });
                    }
                });
                syncHandler.postDelayed(this, 10000); // Roda a cada 10 segundos
            }
        };

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(binding.bottomNav, navController);

            binding.bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_mais) {
                    showMaisDialog();
                    return true;
                }
                
                NavOptions navOptions = new NavOptions.Builder()
                        .setPopUpTo(navController.getGraph().getStartDestinationId(), false)
                        .setLaunchSingleTop(true)
                        .build();
                
                navController.navigate(id, null, navOptions);
                return true;
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!SessaoManager.getInstance().isSessionValid()) {
            logout();
        } else {
            SessaoManager.getInstance().reiniciarTimer();
        }
        if (syncHandler != null && syncRunnable != null) {
            syncHandler.post(syncRunnable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (syncHandler != null && syncRunnable != null) {
            syncHandler.removeCallbacks(syncRunnable);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            SessaoManager.getInstance().reiniciarTimer();
        }
        return super.dispatchTouchEvent(ev);
    }

    private void showMaisDialog() {
        String[] items = {
                "Histórico Completo",
                "Fluxo do Dia (Relatório)",
                "Análise Gráfica",
                "Tabela de Preços",
                "Configurações e Impressora",
                "Encerrar Sessão (Sair)"
        };

        new MaterialAlertDialogBuilder(this)
                .setTitle("Gestão Park ' 31")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0: navController.navigate(R.id.nav_historico); break;
                        case 1: navController.navigate(R.id.nav_relatorio); break;
                        case 2: navController.navigate(R.id.nav_graficos); break;
                        case 3: navController.navigate(R.id.nav_tarifa); break;
                        case 4: navController.navigate(R.id.nav_configuracao); break;
                        case 5: logout(); break;
                    }
                })
                .show();
    }

    private void logout() {
        SessaoManager.getInstance().encerrarSessao();
        startActivity(new Intent(this, PinActivity.class));
        finish();
    }
}
