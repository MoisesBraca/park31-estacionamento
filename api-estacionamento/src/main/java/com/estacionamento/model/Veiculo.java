package com.estacionamento.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "veiculos")
public class Veiculo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String placa;

    @Column(nullable = false)
    private Long horaEntrada;

    private Long horaSaida;

    public Veiculo() {}

    public Veiculo(String placa) {
        this.placa = placa.toUpperCase();
        this.horaEntrada = System.currentTimeMillis();
    }

    public void registrarSaida() {
        this.horaSaida = System.currentTimeMillis();
    }

    public long getTempoEstacionado() {
        long saida = (horaSaida == null || horaSaida == 0) ? System.currentTimeMillis() : horaSaida;
        return saida - horaEntrada;
    }

    public boolean isEstacionado() {
        return horaSaida == null || horaSaida == 0;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlaca() { return placa; }
    public void setPlaca(String placa) { this.placa = placa; }
    public Long getHoraEntrada() { return horaEntrada; }
    public void setHoraEntrada(Long horaEntrada) { this.horaEntrada = horaEntrada; }
    public Long getHoraSaida() { return horaSaida; }
    public void setHoraSaida(Long horaSaida) { this.horaSaida = horaSaida; }
}
