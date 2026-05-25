package com.estacionamento;

public enum Perfil {
    OPERADOR("Operador", 1),
    SUPERVISOR("Supervisor", 2),
    ADMIN("Administrador", 3);

    private final String nome;
    private final int nivel;

    Perfil(String nome, int nivel) {
        this.nome = nome;
        this.nivel = nivel;
    }

    public String getNome() { return nome; }
    public int getNivel() { return nivel; }

    public boolean podeAlterarTarifa() { return this != OPERADOR; }
    public boolean podeVerRelatorios() { return true; }
    public boolean podeVerAuditoria() { return this == ADMIN; }
    public boolean podeGerenciarOperadores() { return this == ADMIN; }
    public boolean podeAcessarConfig() { return this != OPERADOR; }
}
