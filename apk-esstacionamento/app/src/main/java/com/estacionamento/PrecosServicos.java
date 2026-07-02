package com.estacionamento;

public class PrecosServicos {
    private static double precoDucha = 15.0;
    private static double precoSimples = 30.0;
    private static double precoCompleta = 50.0;

    private PrecosServicos() {}

    static void carregar(double ducha, double simples, double completa) {
        precoDucha = ducha;
        precoSimples = simples;
        precoCompleta = completa;
    }

    public static double getPrecoDucha() { return precoDucha; }
    public static double getPrecoSimples() { return precoSimples; }
    public static double getPrecoCompleta() { return precoCompleta; }

    public static void setPrecoDucha(double preco) {
        if (preco <= 0) throw new IllegalArgumentException("Preço da Ducha deve ser positivo");
        precoDucha = preco;
        Preferences.salvarPrecosLavagem(precoDucha, precoSimples, precoCompleta);
    }

    public static void setPrecoSimples(double preco) {
        if (preco <= 0) throw new IllegalArgumentException("Preço da Simples deve ser positivo");
        precoSimples = preco;
        Preferences.salvarPrecosLavagem(precoDucha, precoSimples, precoCompleta);
    }

    public static void setPrecoCompleta(double preco) {
        if (preco <= 0) throw new IllegalArgumentException("Preço da Completa deve ser positivo");
        precoCompleta = preco;
        Preferences.salvarPrecosLavagem(precoDucha, precoSimples, precoCompleta);
    }

    public static double getPrecoPorTipo(String tipo) {
        switch (tipo) {
            case "Ducha": return precoDucha;
            case "Simples": return precoSimples;
            case "Completa": return precoCompleta;
            default: return 0.0;
        }
    }
}
