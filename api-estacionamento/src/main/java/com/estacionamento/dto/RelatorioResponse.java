package com.estacionamento.dto;

public class RelatorioResponse {
    private long totalVeiculosAtendidos;
    private long vagasOcupadas;
    private double receitaTotal;
    private double tarifaHora;

    public RelatorioResponse(long totalVeiculosAtendidos, long vagasOcupadas,
                             double receitaTotal, double tarifaHora) {
        this.totalVeiculosAtendidos = totalVeiculosAtendidos;
        this.vagasOcupadas = vagasOcupadas;
        this.receitaTotal = receitaTotal;
        this.tarifaHora = tarifaHora;
    }

    public long getTotalVeiculosAtendidos() { return totalVeiculosAtendidos; }
    public long getVagasOcupadas() { return vagasOcupadas; }
    public double getReceitaTotal() { return receitaTotal; }
    public double getTarifaHora() { return tarifaHora; }
}
