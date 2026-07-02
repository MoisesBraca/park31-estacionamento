package com.estacionamento.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "transacoes")
public class Transacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String placa;

    @Column(nullable = false)
    private Long horaEntrada;

    @Column(nullable = false)
    private Long horaSaida;

    private Double valorPago;

    private Double tarifaCobrada;

    public Transacao() {}

    public Transacao(Veiculo veiculo, double valorPago, double tarifaCobrada) {
        this.placa = veiculo.getPlaca();
        this.horaEntrada = veiculo.getHoraEntrada();
        this.horaSaida = veiculo.getHoraSaida();
        this.valorPago = valorPago;
        this.tarifaCobrada = tarifaCobrada;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlaca() { return placa; }
    public void setPlaca(String placa) { this.placa = placa; }
    public Long getHoraEntrada() { return horaEntrada; }
    public void setHoraEntrada(Long horaEntrada) { this.horaEntrada = horaEntrada; }
    public Long getHoraSaida() { return horaSaida; }
    public void setHoraSaida(Long horaSaida) { this.horaSaida = horaSaida; }
    public Double getValorPago() { return valorPago; }
    public void setValorPago(Double valorPago) { this.valorPago = valorPago; }
    public Double getTarifaCobrada() { return tarifaCobrada; }
    public void setTarifaCobrada(Double tarifaCobrada) { this.tarifaCobrada = tarifaCobrada; }
}
