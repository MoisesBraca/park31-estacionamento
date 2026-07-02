package com.estacionamento;

public class CalculadoraTarifa {
    private static final long MILISSEGUNDOS_POR_HORA = 1000 * 60 * 60;
    private static double tarifaHora = 5.0;

    private CalculadoraTarifa() {}

    public static double getTarifaHora() {
        return tarifaHora;
    }

    static void carregar(double valor) {
        tarifaHora = valor;
    }

    public static void setTarifaHora(double tarifa) {
        if (tarifa <= 0) {
            throw new IllegalArgumentException("Tarifa deve ser positiva");
        }
        tarifaHora = tarifa;
    }

    public static double calcularTarifa(long tempoEstacionadoMillis) {
        if (tempoEstacionadoMillis < 0) {
            throw new IllegalArgumentException("Tempo estacionado nao pode ser negativo");
        }
        long horas = (tempoEstacionadoMillis + MILISSEGUNDOS_POR_HORA - 1) / MILISSEGUNDOS_POR_HORA;
        if (horas == 0) {
            horas = 1;
        }
        return horas * tarifaHora;
    }
}
