package com.estacionamento.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CalculadoraTarifa {

    private static final long MILISSEGUNDOS_POR_HORA = 1000 * 60 * 60;

    private double tarifaHora;

    public CalculadoraTarifa(@Value("${app.tarifa.hora:5.0}") double tarifaHora) {
        this.tarifaHora = tarifaHora;
    }

    public double getTarifaHora() {
        return tarifaHora;
    }

    public void setTarifaHora(double tarifa) {
        if (tarifa <= 0) {
            throw new IllegalArgumentException("Tarifa deve ser positiva");
        }
        this.tarifaHora = tarifa;
    }

    public double calcularTarifa(long tempoEstacionadoMillis) {
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
