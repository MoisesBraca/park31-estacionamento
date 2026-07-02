package com.estacionamento.dto;

import com.estacionamento.model.Transacao;

public class SaidaResponse {
    private String placa;
    private String horaEntrada;
    private String horaSaida;
    private String tempoEstacionado;
    private double tarifaCobrada;
    private double valorPago;
    private double troco;

    public SaidaResponse(Transacao transacao, double troco) {
        this.placa = transacao.getPlaca();
        this.horaEntrada = formatarData(transacao.getHoraEntrada());
        this.horaSaida = formatarData(transacao.getHoraSaida());
        long minutos = (transacao.getHoraSaida() - transacao.getHoraEntrada()) / (1000 * 60);
        this.tempoEstacionado = minutos + " minutos";
        this.tarifaCobrada = transacao.getTarifaCobrada();
        this.valorPago = transacao.getValorPago();
        this.troco = troco;
    }

    private String formatarData(long millis) {
        return new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
                .format(new java.util.Date(millis));
    }

    public String getPlaca() { return placa; }
    public String getHoraEntrada() { return horaEntrada; }
    public String getHoraSaida() { return horaSaida; }
    public String getTempoEstacionado() { return tempoEstacionado; }
    public double getTarifaCobrada() { return tarifaCobrada; }
    public double getValorPago() { return valorPago; }
    public double getTroco() { return troco; }
}
