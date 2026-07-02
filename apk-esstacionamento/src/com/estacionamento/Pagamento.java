package com.estacionamento;

public class Pagamento {
    private double valorPago;

    public Pagamento(double valorPago) {
        if (valorPago < 0) {
            throw new IllegalArgumentException("Valor do pagamento nao pode ser negativo");
        }
        this.valorPago = valorPago;
    }

    public boolean validarPagamento(double tarifa) {
        return valorPago >= tarifa;
    }

    public double getValorPago() {
        return valorPago;
    }

    public double getTroco(double tarifa) {
        if (!validarPagamento(tarifa)) {
            throw new IllegalStateException("Pagamento insuficiente");
        }
        return valorPago - tarifa;
    }
}
