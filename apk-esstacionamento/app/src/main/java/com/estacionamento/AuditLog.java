package com.estacionamento;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "audit_log")
public class AuditLog {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @NonNull
    @ColumnInfo(name = "operador")
    private String operador;

    @NonNull
    @ColumnInfo(name = "acao")
    private String acao;

    @ColumnInfo(name = "timestamp")
    private long timestamp;

    @NonNull
    @ColumnInfo(name = "detalhes")
    private String detalhes;

    public AuditLog(@NonNull String operador, @NonNull String acao, long timestamp, @NonNull String detalhes) {
        this.operador = operador;
        this.acao = acao;
        this.timestamp = timestamp;
        this.detalhes = detalhes;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    @NonNull public String getOperador() { return operador; }
    public void setOperador(@NonNull String operador) { this.operador = operador; }

    @NonNull public String getAcao() { return acao; }
    public void setAcao(@NonNull String acao) { this.acao = acao; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @NonNull public String getDetalhes() { return detalhes; }
    public void setDetalhes(@NonNull String detalhes) { this.detalhes = detalhes; }
}
