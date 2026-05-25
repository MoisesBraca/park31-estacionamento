package com.estacionamento;

public class Veiculo {
    private String placa;
    private long horaEntrada;
    private long horaSaida;

    public Veiculo(String placa) {
        setPlaca(placa);
        this.horaEntrada = System.currentTimeMillis();
        this.horaSaida = 0;
    }

    public Veiculo(String placa, long horaEntrada, long horaSaida) {
        setPlaca(placa);
        this.horaEntrada = horaEntrada;
        this.horaSaida = horaSaida;
    }

    public void registrarSaida() {
        this.horaSaida = System.currentTimeMillis();
    }

    public long getTempoEstacionado() {
        long saida = (horaSaida == 0) ? System.currentTimeMillis() : horaSaida;
        return saida - horaEntrada;
    }

    public String getPlaca() {
        return placa;
    }

    public long getHoraEntrada() {
        return horaEntrada;
    }

    public long getHoraSaida() {
        return horaSaida;
    }

    public boolean isEstacionado() {
        return horaSaida == 0;
    }

    private void setPlaca(String placa) {
        if (placa == null || placa.trim().isEmpty()) {
            throw new IllegalArgumentException("Placa nao pode ser vazia");
        }
        this.placa = placa.trim().toUpperCase();
    }
}
