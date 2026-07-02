package com.estacionamento;

public class Transacao {
    private String placa;
    private long horaEntrada;
    private long horaSaida;
    private double valorPago;
    private double tarifaCobrada;

    public Transacao(Veiculo veiculo, double valorPago, double tarifaCobrada) {
        this.placa = veiculo.getPlaca();
        this.horaEntrada = veiculo.getHoraEntrada();
        this.horaSaida = veiculo.getHoraSaida();
        this.valorPago = valorPago;
        this.tarifaCobrada = tarifaCobrada;
    }

    public Transacao(String placa, long horaEntrada, long horaSaida,
                     double valorPago, double tarifaCobrada) {
        this.placa = placa;
        this.horaEntrada = horaEntrada;
        this.horaSaida = horaSaida;
        this.valorPago = valorPago;
        this.tarifaCobrada = tarifaCobrada;
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

    public double getValorPago() {
        return valorPago;
    }

    public double getTarifaCobrada() {
        return tarifaCobrada;
    }

    public long getTempoEstacionado() {
        return horaSaida - horaEntrada;
    }
}
