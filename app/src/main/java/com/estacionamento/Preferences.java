package com.estacionamento;

import android.content.Context;
import android.content.SharedPreferences;

public class Preferences {
    private static SharedPreferences prefs;

    public static void init(Context context) {
        prefs = context.getSharedPreferences("estacionamento_prefs", Context.MODE_PRIVATE);

        CalculadoraTarifa.carregar(prefs.getFloat("tarifa_hora", 5.0f));
        PrecosServicos.carregar(
            prefs.getFloat("preco_ducha", 15.0f),
            prefs.getFloat("preco_simples", 30.0f),
            prefs.getFloat("preco_completa", 50.0f)
        );
    }

    public static void salvarTarifaHora(double valor) {
        if (prefs != null) prefs.edit().putFloat("tarifa_hora", (float) valor).apply();
    }

    public static void salvarPrecosLavagem(double ducha, double simples, double completa) {
        if (prefs != null) prefs.edit()
            .putFloat("preco_ducha", (float) ducha)
            .putFloat("preco_simples", (float) simples)
            .putFloat("preco_completa", (float) completa)
            .apply();
    }

    public static String getPixKey() {
        return prefs != null ? prefs.getString("pix_key", "11999999999") : "11999999999";
    }

    public static void setPixKey(String key) {
        if (prefs != null) prefs.edit().putString("pix_key", key).apply();
    }

    public static String getPrinterAddress() {
        return prefs != null ? prefs.getString("printer_address", null) : null;
    }

    public static void setPrinterAddress(String address) {
        if (prefs != null) prefs.edit().putString("printer_address", address).apply();
    }

    public static String getEmpresaNome() {
        return prefs != null ? prefs.getString("empresa_nome", "Park ' 31") : "Park ' 31";
    }

    public static void setEmpresaNome(String nome) {
        if (prefs != null) prefs.edit().putString("empresa_nome", nome).apply();
    }
}
