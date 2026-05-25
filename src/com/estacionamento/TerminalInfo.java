package com.estacionamento;

public class TerminalInfo {
    private final String hardwareId;
    private final String nomeAparelho;
    private final String soTipo;
    private final long dataRegistro;
    private final long dataExpiracao;
    private final String status;
    private final String nomeCliente;
    private final double tarifaHora;
    private final int vagasCarro;
    private final int vagasMoto;
    private final String nomeClientePendente;
    private final double tarifaHoraPendente;
    private final int vagasCarroPendente;
    private final int vagasMotoPendente;
    private final int diasLicencaPendente;
    private final long ultimoPing;

    public TerminalInfo(String hardwareId, String nomeAparelho, String soTipo, long dataRegistro, long dataExpiracao, String status, String nomeCliente, double tarifaHora, int vagasCarro, int vagasMoto, String nomeClientePendente, double tarifaHoraPendente, int vagasCarroPendente, int vagasMotoPendente, int diasLicencaPendente, long ultimoPing) {
        this.hardwareId = hardwareId;
        this.nomeAparelho = nomeAparelho;
        this.soTipo = soTipo;
        this.dataRegistro = dataRegistro;
        this.dataExpiracao = dataExpiracao;
        this.status = status;
        this.nomeCliente = nomeCliente;
        this.tarifaHora = tarifaHora;
        this.vagasCarro = vagasCarro;
        this.vagasMoto = vagasMoto;
        this.nomeClientePendente = nomeClientePendente;
        this.tarifaHoraPendente = tarifaHoraPendente;
        this.vagasCarroPendente = vagasCarroPendente;
        this.vagasMotoPendente = vagasMotoPendente;
        this.diasLicencaPendente = diasLicencaPendente;
        this.ultimoPing = ultimoPing;
    }

    public String getHardwareId() { return hardwareId; }
    public String getNomeAparelho() { return nomeAparelho; }
    public String getSoTipo() { return soTipo; }
    public long getDataRegistro() { return dataRegistro; }
    public long getDataExpiracao() { return dataExpiracao; }
    public String getStatus() { return status; }
    public String getNomeCliente() { return nomeCliente; }
    public double getTarifaHora() { return tarifaHora; }
    public int getVagasCarro() { return vagasCarro; }
    public int getVagasMoto() { return vagasMoto; }
    public String getNomeClientePendente() { return nomeClientePendente; }
    public double getTarifaHoraPendente() { return tarifaHoraPendente; }
    public int getVagasCarroPendente() { return vagasCarroPendente; }
    public int getVagasMotoPendente() { return vagasMotoPendente; }
    public int getDiasLicencaPendente() { return diasLicencaPendente; }
    public long getUltimoPing() { return ultimoPing; }
}
