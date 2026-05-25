package com.estacionamento;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tarifa_config")
public class TarifaConfig {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @NonNull
    @ColumnInfo(name = "tipo")
    private String tipo;

    @ColumnInfo(name = "valorBase", defaultValue = "0.0")
    private double valorBase;

    @ColumnInfo(name = "incremento", defaultValue = "0.0")
    private double incremento;

    @NonNull
    @ColumnInfo(name = "descricao", defaultValue = "")
    private String descricao;

    public TarifaConfig(@NonNull String tipo, double valorBase, double incremento, @NonNull String descricao) {
        this.tipo = tipo;
        this.valorBase = valorBase;
        this.incremento = incremento;
        this.descricao = descricao;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    @NonNull public String getTipo() { return tipo; }
    public void setTipo(@NonNull String tipo) { this.tipo = tipo; }

    public double getValorBase() { return valorBase; }
    public void setValorBase(double valorBase) { this.valorBase = valorBase; }

    public double getIncremento() { return incremento; }
    public void setIncremento(double incremento) { this.incremento = incremento; }

    @NonNull public String getDescricao() { return descricao; }
    public void setDescricao(@NonNull String descricao) { this.descricao = descricao; }
}
