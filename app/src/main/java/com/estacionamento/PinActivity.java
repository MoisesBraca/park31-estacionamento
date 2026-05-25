package com.estacionamento;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.estacionamento.R;
import java.util.Arrays;

public class PinActivity extends AppCompatActivity {

    private static final int PIN_LENGTH = 6;
    private final StringBuilder pinDigitado = new StringBuilder();
    private boolean modoCriacao = false;
    private String primeiroPin = null;
    private CountDownTimer lockoutTimer;

    private TextView[] dots;
    private TextView tvErro, tvLockout, tvTitulo, tvSubtitulo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);

        SessaoManager.getInstance().init(this);

        dots = new TextView[]{
            findViewById(R.id.dot_0), findViewById(R.id.dot_1),
            findViewById(R.id.dot_2), findViewById(R.id.dot_3),
            findViewById(R.id.dot_4), findViewById(R.id.dot_5)
        };
        tvErro = findViewById(R.id.pin_erro);
        tvLockout = findViewById(R.id.pin_lockout);
        tvTitulo = findViewById(R.id.pin_titulo);
        tvSubtitulo = findViewById(R.id.pin_subtitulo);

        modoCriacao = !SessaoManager.getInstance().possuiPin();
        if (modoCriacao) {
            tvTitulo.setText("Criar PIN");
            tvSubtitulo.setText("Defina um PIN de " + PIN_LENGTH + " dígitos");
        }

        setupNumberPad();
        verificarLockout();
        executarVerificacaoLicenca();
    }

    private void setupNumberPad() {
        int[] ids = {R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4,
                     R.id.btn_5, R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9};
        for (int id : ids) {
            findViewById(id).setOnClickListener(v -> {
                SessaoManager.getInstance().reiniciarTimer();
                digitar(((TextView) v).getText().toString());
            });
        }
        findViewById(R.id.btn_apagar).setOnClickListener(v -> apagar());
    }

    private void digitar(String digito) {
        if (SessaoManager.getInstance().estaBloqueado()) return;
        if (pinDigitado.length() >= PIN_LENGTH) return;

        pinDigitado.append(digito);
        atualizarDots();

        if (pinDigitado.length() == PIN_LENGTH) {
            validar();
        }
    }

    private void apagar() {
        if (pinDigitado.length() > 0) {
            pinDigitado.deleteCharAt(pinDigitado.length() - 1);
            atualizarDots();
        }
    }

    private void atualizarDots() {
        for (int i = 0; i < PIN_LENGTH; i++) {
            dots[i].setBackgroundResource(i < pinDigitado.length()
                ? R.drawable.bg_pin_dot_filled : R.drawable.bg_pin_dot_empty);
        }
    }

    private void validar() {
        String pin = pinDigitado.toString();

        if (modoCriacao) {
            if (primeiroPin == null) {
                primeiroPin = pin;
                pinDigitado.setLength(0);
                atualizarDots();
                tvTitulo.setText("Confirmar PIN");
                tvSubtitulo.setText("Digite o mesmo PIN novamente");
                return;
            }

            if (pin.equals(primeiroPin)) {
                SessaoManager.getInstance().criarPin(pin, Perfil.ADMIN);
                SessaoManager.getInstance().iniciarSessao();
                abrirMain();
            } else {
                mostrarErro("PINs não coincidem. Tente novamente.");
                pinDigitado.setLength(0);
                atualizarDots();
                primeiroPin = null;
                tvTitulo.setText("Criar PIN");
                tvSubtitulo.setText("Defina um PIN de " + PIN_LENGTH + " dígitos");
            }
            return;
        }

        if (SessaoManager.getInstance().validarPin(pin)) {
            SessaoManager.getInstance().iniciarSessao();
            abrirMain();
        } else {
            SessaoManager.getInstance().registrarTentativaFalha();
            int restantes = SessaoManager.getInstance().getTentativasRestantes();

            if (SessaoManager.getInstance().estaBloqueado()) {
                mostrarErro("");
                iniciarLockout();
            } else {
                mostrarErro("PIN inválido. " + restantes + " tentativa(s) restante(s).");
                pinDigitado.setLength(0);
                atualizarDots();
            }
        }
    }

    private void mostrarErro(String msg) {
        tvErro.setText(msg);
        tvErro.setVisibility(msg.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void verificarLockout() {
        if (SessaoManager.getInstance().estaBloqueado()) {
            iniciarLockout();
        }
    }

    private void iniciarLockout() {
        for (int i = 0; i < PIN_LENGTH; i++) {
            dots[i].setBackgroundResource(R.drawable.bg_pin_dot_empty);
        }
        pinDigitado.setLength(0);

        if (lockoutTimer != null) lockoutTimer.cancel();

        lockoutTimer = new CountDownTimer(
            SessaoManager.getInstance().getTempoRestanteBloqueio(), 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvLockout.setVisibility(View.VISIBLE);
                tvLockout.setText("Bloqueado por " + (millisUntilFinished / 1000) + "s");
            }
            @Override
            public void onFinish() {
                tvLockout.setVisibility(View.GONE);
                tvLockout.setText("");
                mostrarErro("");
            }
        }.start();
    }

    private void abrirMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (lockoutTimer != null) lockoutTimer.cancel();
    }

    private void executarVerificacaoLicenca() {
        // Exibe um diálogo de carregamento
        androidx.appcompat.app.AlertDialog progress = new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Park ' 31")
            .setMessage("Conectando ao servidor de licenciamento...")
            .setCancelable(false)
            .create();
        
        progress.show();

        LicencaHelper.verificarLicenca(this, (status, expiracao, erro) -> {
            runOnUiThread(() -> {
                progress.dismiss();
                if (status.equals("CONEXAO_FALHOU")) {
                    exibirDialogoConfiguracaoIp();
                } else if (status.equals("ATIVO")) {
                    if (expiracao > 0 && System.currentTimeMillis() > expiracao) {
                        exibirMensagemBloqueio("Licença expirou!", "Por favor, renove sua assinatura com o administrador.");
                    } else {
                        // Aprovado! Libera o uso
                    }
                } else if (status.equals("PENDENTE")) {
                    exibirMensagemBloqueio("Aparelho Pendente de Liberação", 
                        "Este celular foi registrado e aguarda liberação.\n\nNome: " + LicencaHelper.getDeviceName() + "\nID: " + LicencaHelper.getHardwareId(this));
                } else if (status.equals("BLOQUEADO")) {
                    exibirMensagemBloqueio("Acesso Bloqueado", "Este aparelho foi suspenso pelo desenvolvedor. Entre em contato para liberação.");
                }
            });
        });
    }

    private void exibirDialogoConfiguracaoIp() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setText(LicencaHelper.getServerIp(this));
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        
        // Adiciona margens/padding no campo
        int margin = (int) (16 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = margin;
        params.rightMargin = margin;
        input.setLayoutParams(params);
        container.addView(input);

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Configurar Servidor Central")
            .setMessage("Não foi possível conectar ao seu PC servidor. Digite o endereço IP do seu PC:")
            .setView(container)
            .setCancelable(false)
            .setPositiveButton("Salvar e Conectar", (dialog, which) -> {
                String ip = input.getText().toString().trim();
                if (!ip.isEmpty()) {
                    LicencaHelper.setServerIp(this, ip);
                    executarVerificacaoLicenca();
                }
            })
            .setNegativeButton("Fechar", (dialog, which) -> {
                finish();
                System.exit(0);
            })
            .show();
    }

    private void exibirMensagemBloqueio(String titulo, String mensagem) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(titulo)
            .setMessage(mensagem)
            .setCancelable(false)
            .setPositiveButton("Verificar Novamente", (dialog, which) -> {
                executarVerificacaoLicenca();
            })
            .setNegativeButton("Fechar", (dialog, which) -> {
                finish();
                System.exit(0);
            })
            .show();
    }
}
