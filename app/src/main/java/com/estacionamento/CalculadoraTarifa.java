package com.estacionamento;

public class CalculadoraTarifa {
    private static final long MILISSEGUNDOS_POR_HORA = 1000 * 60 * 60;
    private static double tarifaHora = 5.0;

    private CalculadoraTarifa() {}

    static void carregar(double valor) {
        tarifaHora = valor;
    }

    public static double getTarifaHora() { return tarifaHora; }

    public static void setTarifaHora(double tarifa) {
        if (tarifa <= 0) {
            throw new IllegalArgumentException("Tarifa deve ser positiva");
        }
        tarifaHora = tarifa;
        Preferences.salvarTarifaHora(tarifa);
    }

    public static double calcularTarifa(long tempoEstacionadoMillis) {
        if (tempoEstacionadoMillis < 0) {
            throw new IllegalArgumentException("Tempo estacionado nao pode ser negativo");
        }
        long horas = (tempoEstacionadoMillis + MILISSEGUNDOS_POR_HORA - 1) / MILISSEGUNDOS_POR_HORA;
        if (horas == 0) horas = 1;
        return horas * tarifaHora;
    }
}
