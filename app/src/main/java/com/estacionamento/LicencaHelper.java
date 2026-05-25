package com.estacionamento;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public class LicencaHelper {
    private static final String PREFS_NAME = "Park31LicPrefs";
    private static final String KEY_SERVER_IP = "server_ip";
    private static final String DEFAULT_IP = "192.168.1.100";

    public static String getServerIp(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SERVER_IP, DEFAULT_IP);
    }

    public static void setServerIp(Context context, String ip) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SERVER_IP, ip).apply();
    }

    public static String getHardwareId(Context context) {
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId == null || androidId.trim().isEmpty()) {
            androidId = "AND-" + android.os.Build.SERIAL;
        }
        return androidId;
    }

    public static String getDeviceName() {
        String manufacturer = android.os.Build.MANUFACTURER;
        String model = android.os.Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return model;
        } else {
            return manufacturer + " " + model;
        }
    }

    public interface LicenseCallback {
        void onResult(String status, long expiracao, String erro);
    }

    public static void verificarLicenca(Context context, LicenseCallback callback) {
        String ip = getServerIp(context);
        String hardwareId = getHardwareId(context);
        String deviceName = getDeviceName();

        new Thread(() -> {
            try {
                URL url = new URL("http://" + ip + ":8080/api/check");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);

                // Payload JSON com nome do aparelho e SO
                String json = "{\"hardwareId\":\"" + hardwareId + "\",\"nomeAparelho\":\"" + deviceName + "\",\"soTipo\":\"Android Phone\"}";

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    
                    JSONObject jsonObj = new JSONObject(response.toString());
                    String status = jsonObj.getString("status");
                    long expiracao = jsonObj.getLong("expiracao");

                    if (jsonObj.has("tarifaHora")) {
                        double tarifaHora = jsonObj.getDouble("tarifaHora");
                        int vagasCarro = jsonObj.getInt("vagasCarro");
                        int vagasMoto = jsonObj.getInt("vagasMoto");
                        sincronizarConfiguracoes(context, tarifaHora, vagasCarro, vagasMoto);
                    }

                    callback.onResult(status, expiracao, null);
                } else {
                    callback.onResult("CONEXAO_FALHOU", 0, "Código HTTP: " + code);
                }
            } catch (Exception e) {
                callback.onResult("CONEXAO_FALHOU", 0, e.getMessage());
            }
        }).start();
    }

    private static void sincronizarConfiguracoes(Context context, double tarifaHora, int vagasCarro, int vagasMoto) {
        try {
            AppDatabase db = AppDatabase.getDatabase(context);
            
            // 1. Sincronizar Tarifa HORA
            TarifaConfig configHora = db.tarifaConfigDao().getByTipoSync("HORA");
            if (configHora != null) {
                if (configHora.getValorBase() != tarifaHora) {
                    configHora.setValorBase(tarifaHora);
                    configHora.setIncremento(tarifaHora);
                    db.tarifaConfigDao().update(configHora);
                    android.util.Log.d("LicencaHelper", "Tarifa HORA sincronizada para: R$ " + tarifaHora);
                }
            } else {
                TarifaConfig novo = new TarifaConfig("HORA", tarifaHora, tarifaHora, "Tarifa por hora");
                db.tarifaConfigDao().insert(novo);
            }
            CalculadoraTarifa.carregar(tarifaHora);

            // 2. Sincronizar Capacidade de Vagas Carro
            java.util.List<Vaga> vagasCarros = db.vagaDao().getVagasByTipoSync("CARRO");
            int atualCarro = vagasCarros.size();
            if (atualCarro < vagasCarro) {
                // Adiciona novas vagas
                for (int i = atualCarro + 1; i <= vagasCarro; i++) {
                    String num = "C" + (i < 10 ? "0" + i : i);
                    Vaga v = new Vaga(num, "Térreo", "CARRO");
                    db.vagaDao().insert(v);
                }
                android.util.Log.d("LicencaHelper", "Adicionadas vagas de CARRO para atingir: " + vagasCarro);
            } else if (atualCarro > vagasCarro) {
                // Remove vagas excessivas que estão LIVRES
                int removidas = 0;
                int metaRemover = atualCarro - vagasCarro;
                for (int i = vagasCarros.size() - 1; i >= 0; i--) {
                    if (removidas >= metaRemover) break;
                    Vaga v = vagasCarros.get(i);
                    if (v.isLivre()) {
                        db.vagaDao().deleteVagaSync(v.getId());
                        removidas++;
                    }
                }
                android.util.Log.d("LicencaHelper", "Removidas " + removidas + " vagas de CARRO excessivas.");
            }

            // 3. Sincronizar Capacidade de Vagas Moto
            java.util.List<Vaga> vagasMotos = db.vagaDao().getVagasByTipoSync("MOTO");
            int atualMoto = vagasMotos.size();
            if (atualMoto < vagasMoto) {
                // Adiciona novas vagas
                for (int i = atualMoto + 1; i <= vagasMoto; i++) {
                    String num = "M" + (i < 10 ? "0" + i : i);
                    Vaga v = new Vaga(num, "Térreo", "MOTO");
                    db.vagaDao().insert(v);
                }
                android.util.Log.d("LicencaHelper", "Adicionadas vagas de MOTO para atingir: " + vagasMoto);
            } else if (atualMoto > vagasMoto) {
                // Remove vagas excessivas que estão LIVRES
                int removidas = 0;
                int metaRemover = atualMoto - vagasMoto;
                for (int i = vagasMotos.size() - 1; i >= 0; i--) {
                    if (removidas >= metaRemover) break;
                    Vaga v = vagasMotos.get(i);
                    if (v.isLivre()) {
                        db.vagaDao().deleteVagaSync(v.getId());
                        removidas++;
                    }
                }
                android.util.Log.d("LicencaHelper", "Removidas " + removidas + " vagas de MOTO excessivas.");
            }
        } catch (Exception e) {
            android.util.Log.e("LicencaHelper", "Erro ao sincronizar configurações locais: " + e.getMessage());
        }
    }
}
