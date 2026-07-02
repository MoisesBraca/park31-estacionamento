package com.estacionamento;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "veiculos")
public class Veiculo {
    @PrimaryKey
    @NonNull
    private String placa;
    private long horaEntrada;
    private long horaSaida;
    
    private boolean temLavagem;
    private String tipoLavagem;
    private double valorLavagem;
    private boolean lavagemConcluida;
    
    private int vagaId; // Campo necessário para o repositório Pro
    private String fotoAvariaPath;
    private String pixTxId;

    @Ignore
    public Veiculo(@NonNull String placa) {
        this.placa = placa.trim().toUpperCase();
        this.horaEntrada = System.currentTimeMillis();
        this.horaSaida = 0;
        this.temLavagem = false;
        this.lavagemConcluida = false;
        this.vagaId = 0;
    }

    public Veiculo(@NonNull String placa, long horaEntrada, long horaSaida, 
                   boolean temLavagem, String tipoLavagem, double valorLavagem, 
                   boolean lavagemConcluida, int vagaId) {
        this.placa = placa;
        this.horaEntrada = horaEntrada;
        this.horaSaida = horaSaida;
        this.temLavagem = temLavagem;
        this.tipoLavagem = tipoLavagem;
        this.valorLavagem = valorLavagem;
        this.lavagemConcluida = lavagemConcluida;
        this.vagaId = vagaId;
    }

    public void registrarSaida() {
        this.horaSaida = System.currentTimeMillis();
    }

    public long getTempoEstacionado() {
        long saida = (horaSaida == 0) ? System.currentTimeMillis() : horaSaida;
        return saida - horaEntrada;
    }

    @NonNull public String getPlaca() { return placa; }
    public void setPlaca(@NonNull String placa) { this.placa = placa; }
    public long getHoraEntrada() { return horaEntrada; }
    public void setHoraEntrada(long horaEntrada) { this.horaEntrada = horaEntrada; }
    public long getHoraSaida() { return horaSaida; }
    public void setHoraSaida(long horaSaida) { this.horaSaida = horaSaida; }
    public boolean isTemLavagem() { return temLavagem; }
    public void setTemLavagem(boolean temLavagem) { this.temLavagem = temLavagem; }
    public String getTipoLavagem() { return tipoLavagem; }
    public void setTipoLavagem(String tipoLavagem) { this.tipoLavagem = tipoLavagem; }
    public double getValorLavagem() { return valorLavagem; }
    public void setValorLavagem(double valorLavagem) { this.valorLavagem = valorLavagem; }
    public boolean isLavagemConcluida() { return lavagemConcluida; }
    public void setLavagemConcluida(boolean lavagemConcluida) { this.lavagemConcluida = lavagemConcluida; }
    public int getVagaId() { return vagaId; }
    public void setVagaId(int vagaId) { this.vagaId = vagaId; }
    public boolean isEstacionado() { return horaSaida == 0; }

    public String getFotoAvariaPath() { return fotoAvariaPath; }
    public void setFotoAvariaPath(String fotoAvariaPath) { this.fotoAvariaPath = fotoAvariaPath; }
    public String getPixTxId() { return pixTxId; }
    public void setPixTxId(String pixTxId) { this.pixTxId = pixTxId; }
}
