package com.estacionamento;

public class MensalistaInfo {
    private final int id;
    private final String placa;
    private final String nomeCliente;
    private final String telefone;
    private final long vencimento;
    private final String status;

    public MensalistaInfo(int id, String placa, String nomeCliente, String telefone, long vencimento, String status) {
        this.id = id;
        this.placa = placa;
        this.nomeCliente = nomeCliente;
        this.telefone = telefone;
        this.vencimento = vencimento;
        this.status = status;
    }

    public int getId() { return id; }
    public String getPlaca() { return placa; }
    public String getNomeCliente() { return nomeCliente; }
    public String getTelefone() { return telefone; }
    public long getVencimento() { return vencimento; }
    public String getStatus() { return status; }
}
