package com.estacionamento;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "transacoes")
public class Transacao {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String placa;
    private long horaEntrada;
    private long horaSaida;
    private double valorPago;
    private double tarifaCobrada;
    private String formaPagamento;

    public Transacao(String placa, long horaEntrada, long horaSaida,
                     double valorPago, double tarifaCobrada, String formaPagamento) {
        this.placa = placa;
        this.horaEntrada = horaEntrada;
        this.horaSaida = horaSaida;
        this.valorPago = valorPago;
        this.tarifaCobrada = tarifaCobrada;
        this.formaPagamento = formaPagamento;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getPlaca() { return placa; }
    public void setPlaca(String placa) { this.placa = placa; }

    public long getHoraEntrada() { return horaEntrada; }
    public void setHoraEntrada(long horaEntrada) { this.horaEntrada = horaEntrada; }

    public long getHoraSaida() { return horaSaida; }
    public void setHoraSaida(long horaSaida) { this.horaSaida = horaSaida; }

    public double getValorPago() { return valorPago; }
    public void setValorPago(double valorPago) { this.valorPago = valorPago; }

    public double getTarifaCobrada() { return tarifaCobrada; }
    public void setTarifaCobrada(double tarifaCobrada) { this.tarifaCobrada = tarifaCobrada; }

    public String getFormaPagamento() { return formaPagamento; }
    public void setFormaPagamento(String formaPagamento) { this.formaPagamento = formaPagamento; }

    public long getTempoEstacionado() { return horaSaida - horaEntrada; }
}
