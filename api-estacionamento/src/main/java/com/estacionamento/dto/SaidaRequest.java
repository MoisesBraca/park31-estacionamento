package com.estacionamento.dto;

public class SaidaRequest {
    private String placa;
    private Double valorPago;

    public String getPlaca() { return placa; }
    public void setPlaca(String placa) { this.placa = placa; }
    public Double getValorPago() { return valorPago; }
    public void setValorPago(Double valorPago) { this.valorPago = valorPago; }
}
