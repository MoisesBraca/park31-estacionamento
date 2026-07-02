package com.estacionamento;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "mensalistas")
public class Mensalista {
    @PrimaryKey
    @NonNull
    private String placa;
    private String nomeCliente;
    private String telefone;
    private long vencimento;
    private String status; // "ATIVO", "SUSPENSO", "VENCIDO"

    public Mensalista(@NonNull String placa, String nomeCliente, String telefone, long vencimento, String status) {
        this.placa = placa.trim().toUpperCase();
        this.nomeCliente = nomeCliente;
        this.telefone = telefone;
        this.vencimento = vencimento;
        this.status = status != null ? status : "ATIVO";
    }

    @NonNull
    public String getPlaca() {
        return placa;
    }

    public void setPlaca(@NonNull String placa) {
        this.placa = placa.trim().toUpperCase();
    }

    public String getNomeCliente() {
        return nomeCliente;
    }

    public void setNomeCliente(String nomeCliente) {
        this.nomeCliente = nomeCliente;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public long getVencimento() {
        return vencimento;
    }

    public void setVencimento(long vencimento) {
        this.vencimento = vencimento;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isAtivo() {
        return "ATIVO".equalsIgnoreCase(status) && vencimento >= System.currentTimeMillis();
    }
}
