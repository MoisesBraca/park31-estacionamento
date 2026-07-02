package com.estacionamento;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "vagas")
public class Vaga {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @NonNull
    @ColumnInfo(name = "numero")
    private String numero;

    @NonNull
    @ColumnInfo(name = "andar", defaultValue = "Térreo")
    private String andar;

    @NonNull
    @ColumnInfo(name = "tipo", defaultValue = "CARRO")
    private String tipo;

    @NonNull
    @ColumnInfo(name = "status", defaultValue = "LIVRE")
    private String status;

    public Vaga(@NonNull String numero, @NonNull String andar, @NonNull String tipo) {
        this.numero = numero;
        this.andar = andar;
        this.tipo = tipo;
        this.status = "LIVRE";
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    @NonNull public String getNumero() { return numero; }
    public void setNumero(@NonNull String numero) { this.numero = numero; }

    @NonNull public String getAndar() { return andar; }
    public void setAndar(@NonNull String andar) { this.andar = andar; }

    @NonNull public String getTipo() { return tipo; }
    public void setTipo(@NonNull String tipo) { this.tipo = tipo; }

    @NonNull public String getStatus() { return status; }
    public void setStatus(@NonNull String status) { this.status = status; }

    public boolean isLivre() { return "LIVRE".equals(status); }
    public boolean isOcupada() { return "OCUPADA".equals(status); }
}
