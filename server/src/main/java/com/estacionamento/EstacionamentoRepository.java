package com.estacionamento;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class EstacionamentoRepository {
    private static final String URL_CONEXAO = "jdbc:sqlite:./db_estacionamento.db";

    public EstacionamentoRepository() {
        inicializarBanco();
    }

    private Connection obterConexao() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
        }
        return DriverManager.getConnection(obterUrlConexao());
    }

    private static String obterUrlConexao() {
        String dbPathEnv = System.getenv("DB_PATH");
        if (dbPathEnv != null && !dbPathEnv.isEmpty()) {
            return "jdbc:sqlite:" + dbPathEnv;
        }
        java.io.File dataDir = new java.io.File("/data");
        if (dataDir.exists() && dataDir.isDirectory() && dataDir.canWrite()) {
            return "jdbc:sqlite:/data/db_estacionamento.db";
        }
        return "jdbc:sqlite:./db_estacionamento.db";
    }


    private void inicializarBanco() {
        String sqlVeiculos = "CREATE TABLE IF NOT EXISTS veiculos ("
                           + "  placa TEXT PRIMARY KEY,"
                           + "  hora_entrada INTEGER NOT NULL,"
                           + "  hora_saida INTEGER NOT NULL"
                           + ");";

        String sqlTransacoes = "CREATE TABLE IF NOT EXISTS transacoes ("
                             + "  id INTEGER PRIMARY KEY AUTOINCREMENT,"
                             + "  placa TEXT NOT NULL,"
                             + "  hora_entrada INTEGER NOT NULL,"
                             + "  hora_saida INTEGER NOT NULL,"
                             + "  valor_pago REAL NOT NULL,"
                             + "  tarifa_cobrada REAL NOT NULL,"
                             + "  hardware_id TEXT"
                             + ");";

        String sqlConfig = "CREATE TABLE IF NOT EXISTS configuracoes ("
                         + "  chave TEXT PRIMARY KEY,"
                         + "  valor TEXT NOT NULL"
                         + ");";

        String sqlTerminais = "CREATE TABLE IF NOT EXISTS terminais ("
                            + "  hardware_id TEXT PRIMARY KEY,"
                            + "  nome_aparelho TEXT NOT NULL,"
                            + "  so_tipo TEXT NOT NULL,"
                            + "  data_registro INTEGER NOT NULL,"
                            + "  data_expiracao INTEGER,"
                            + "  status TEXT DEFAULT 'PENDENTE',"
                            + "  nome_cliente TEXT,"
                            + "  tarifa_hora REAL DEFAULT 5.0,"
                            + "  vagas_carro INTEGER DEFAULT 20,"
                            + "  vagas_moto INTEGER DEFAULT 5"
                            + ");";

        String sqlMensalistas = "CREATE TABLE IF NOT EXISTS mensalistas ("
                              + "  id INTEGER PRIMARY KEY AUTOINCREMENT,"
                              + "  placa TEXT UNIQUE NOT NULL,"
                              + "  nome_cliente TEXT NOT NULL,"
                              + "  telefone TEXT,"
                              + "  vencimento INTEGER NOT NULL,"
                              + "  status TEXT DEFAULT 'ATIVO'"
                              + ");";

        try (Connection conn = obterConexao();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sqlVeiculos);
            stmt.execute(sqlTransacoes);
            stmt.execute(sqlConfig);
            stmt.execute(sqlTerminais);
            stmt.execute(sqlMensalistas);
        } catch (SQLException e) {
            System.err.println("Erro ao inicializar o banco de dados SQLite: " + e.getMessage());
        }

        String[] alterStatements = {
            "ALTER TABLE terminais ADD COLUMN nome_cliente TEXT;",
            "ALTER TABLE terminais ADD COLUMN tarifa_hora REAL DEFAULT 5.0;",
            "ALTER TABLE terminais ADD COLUMN vagas_carro INTEGER DEFAULT 20;",
            "ALTER TABLE terminais ADD COLUMN vagas_moto INTEGER DEFAULT 5;",
            "ALTER TABLE terminais ADD COLUMN nome_cliente_pendente TEXT;",
            "ALTER TABLE terminais ADD COLUMN tarifa_hora_pendente REAL DEFAULT -1.0;",
            "ALTER TABLE terminais ADD COLUMN vagas_carro_pendente INTEGER DEFAULT -1;",
            "ALTER TABLE terminais ADD COLUMN vagas_moto_pendente INTEGER DEFAULT -1;",
            "ALTER TABLE terminais ADD COLUMN dias_licenca_pendente INTEGER DEFAULT -1;",
            "ALTER TABLE terminais ADD COLUMN ultimo_ping INTEGER DEFAULT 0;",
            "ALTER TABLE transacoes ADD COLUMN hardware_id TEXT;"
        };
        for (String alter : alterStatements) {
            try (Connection conn = obterConexao();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(alter);
            } catch (SQLException ignored) {
            }
        }
    }

    public void salvarVeiculos(List<Veiculo> veiculos) throws IOException {
        String deleteSql = "DELETE FROM veiculos;";
        String insertSql = "INSERT INTO veiculos (placa, hora_entrada, hora_saida) VALUES (?, ?, ?);";

        try (Connection conn = obterConexao()) {
            conn.setAutoCommit(false);

            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);
                 PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

                deleteStmt.executeUpdate();

                for (Veiculo v : veiculos) {
                    insertStmt.setString(1, v.getPlaca());
                    insertStmt.setLong(2, v.getHoraEntrada());
                    insertStmt.setLong(3, v.getHoraSaida());
                    insertStmt.addBatch();
                }
                insertStmt.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IOException("Erro ao salvar veiculos no SQLite: " + e.getMessage(), e);
        }
    }

    public List<Veiculo> carregarVeiculos() throws IOException {
        List<Veiculo> veiculos = new ArrayList<>();
        String sql = "SELECT placa, hora_entrada, hora_saida FROM veiculos;";

        try (Connection conn = obterConexao();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String placa = rs.getString("placa");
                long horaEntrada = rs.getLong("hora_entrada");
                long horaSaida = rs.getLong("hora_saida");
                veiculos.add(new Veiculo(placa, horaEntrada, horaSaida));
            }
        } catch (SQLException e) {
            throw new IOException("Erro ao carregar veiculos do SQLite: " + e.getMessage(), e);
        }
        return veiculos;
    }

    public void salvarTransacoes(List<Transacao> transacoes) throws IOException {
        String deleteSql = "DELETE FROM transacoes;";
        String insertSql = "INSERT INTO transacoes (placa, hora_entrada, hora_saida, valor_pago, tarifa_cobrada) VALUES (?, ?, ?, ?, ?);";

        try (Connection conn = obterConexao()) {
            conn.setAutoCommit(false);

            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);
                 PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

                deleteStmt.executeUpdate();

                for (Transacao t : transacoes) {
                    insertStmt.setString(1, t.getPlaca());
                    insertStmt.setLong(2, t.getHoraEntrada());
                    insertStmt.setLong(3, t.getHoraSaida());
                    insertStmt.setDouble(4, t.getValorPago());
                    insertStmt.setDouble(5, t.getTarifaCobrada());
                    insertStmt.addBatch();
                }
                insertStmt.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IOException("Erro ao salvar transacoes no SQLite: " + e.getMessage(), e);
        }
    }

    public List<Transacao> carregarTransacoes() throws IOException {
        List<Transacao> transacoes = new ArrayList<>();
        String sql = "SELECT placa, hora_entrada, hora_saida, valor_pago, tarifa_cobrada FROM transacoes;";

        try (Connection conn = obterConexao();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String placa = rs.getString("placa");
                long horaEntrada = rs.getLong("hora_entrada");
                long horaSaida = rs.getLong("hora_saida");
                double valorPago = rs.getDouble("valor_pago");
                double tarifaCobrada = rs.getDouble("tarifa_cobrada");
                transacoes.add(new Transacao(placa, horaEntrada, horaSaida, valorPago, tarifaCobrada));
            }
        } catch (SQLException e) {
            throw new IOException("Erro ao carregar transacoes do SQLite: " + e.getMessage(), e);
        }
        return transacoes;
    }

    public double carregarTarifa() {
        String sql = "SELECT valor FROM configuracoes WHERE chave = 'tarifa_hora';";
        try (Connection conn = obterConexao();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return Double.parseDouble(rs.getString("valor"));
            }
        } catch (Exception e) {
            System.err.println("Erro ao carregar tarifa do SQLite: " + e.getMessage());
        }
        return 5.0;
    }

    public void salvarTarifa(double tarifa) {
        String sql = "INSERT OR REPLACE INTO configuracoes (chave, valor) VALUES ('tarifa_hora', ?);";
        try (Connection conn = obterConexao();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, String.valueOf(tarifa));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erro ao salvar tarifa no SQLite: " + e.getMessage());
        }
    }

    public void registrarTerminal(String hardwareId, String nomeAparelho, String soTipo) {
        String sql = "INSERT OR IGNORE INTO terminais (hardware_id, nome_aparelho, so_tipo, data_registro, data_expiracao, status) "
                   + "VALUES (?, ?, ?, ?, ?, ?);";
        try (Connection conn = obterConexao();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, hardwareId);
            pstmt.setString(2, nomeAparelho);
            pstmt.setString(3, soTipo);
            pstmt.setLong(4, System.currentTimeMillis());
            pstmt.setLong(5, 0);
            pstmt.setString(6, "PENDENTE");
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erro ao registrar terminal no SQLite: " + e.getMessage());
        }
    }

    public void atualizarUltimoPing(String hardwareId) {
        String sql = "UPDATE terminais SET ultimo_ping = ? WHERE hardware_id = ?;";
        try (Connection conn = obterConexao();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, System.currentTimeMillis());
            pstmt.setString(2, hardwareId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erro ao atualizar ultimo_ping: " + e.getMessage());
        }
    }

    public String verificarStatusTerminal(String hardwareId) {
        String sql = "SELECT status FROM terminais WHERE hardware_id = ?;";
        try (Connection conn = obterConexao();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, hardwareId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("status");
                }
            }
        } catch (SQLException e) {
            System.err.println("Erro ao verificar status de terminal: " + e.getMessage());
        }
        return "INEXISTENTE";
    }

    public long obterExpiracaoTerminal(String hardwareId) {
        String sql = "SELECT data_expiracao FROM terminais WHERE hardware_id = ?;";
        try (Connection conn = obterConexao();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, hardwareId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("data_expiracao");
                }
            }
        } catch (SQLException e) {
            System.err.println("Erro ao obter expiracao de terminal: " + e.getMessage());
        }
        return 0;
    }

    public void aprovarTerminal(String hardwareId, long dataExpiracao) {
        String sql = "UPDATE terminais SET status = 'ATIVO', data_expiracao = ? WHERE hardware_id = ?;";
        try (Connection conn = obterConexao();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, dataExpiracao);
            pstmt.setString(2, hardwareId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erro ao aprovar terminal no SQLite: " + e.getMessage());
        }
    }

    public void bloquearTerminal(String hardwareId) {
        String sql = "UPDATE terminais SET status = 'BLOQUEADO' WHERE hardware_id = ?;";
        try (Connection conn = obterConexao();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, hardwareId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erro ao bloquear terminal no SQLite: " + e.getMessage());
        }
    }

    public List<TerminalInfo> listarTerminais() {
        List<TerminalInfo> terminais = new ArrayList<>();
        String sql = "SELECT hardware_id, nome_aparelho, so_tipo, data_registro, data_expiracao, status, nome_cliente, tarifa_hora, vagas_carro, vagas_moto, nome_cliente_pendente, tarifa_hora_pendente, vagas_carro_pendente, vagas_moto_pendente, dias_licenca_pendente, ultimo_ping FROM terminais ORDER BY data_registro DESC;";
        try (Connection conn = obterConexao();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String nomeCli = rs.getString("nome_cliente");
                String nomeCliPend = rs.getString("nome_cliente_pendente");
                terminais.add(new TerminalInfo(
                    rs.getString("hardware_id"),
                    rs.getString("nome_aparelho"),
                    rs.getString("so_tipo"),
                    rs.getLong("data_registro"),
                    rs.getLong("data_expiracao"),
                    rs.getString("status"),
                    nomeCli != null ? nomeCli : "",
                    rs.getDouble("tarifa_hora"),
                    rs.getInt("vagas_carro"),
                    rs.getInt("vagas_moto"),
                    nomeCliPend != null ? nomeCliPend : "",
                    rs.getDouble("tarifa_hora_pendente"),
                    rs.getInt("vagas_carro_pendente"),
                    rs.getInt("vagas_moto_pendente"),
                    rs.getInt("dias_licenca_pendente"),
                    rs.getLong("ultimo_ping")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Erro ao listar terminais do SQLite: " + e.getMessage());
        }
        return terminais;
    }

    public TerminalInfo obterTerminal(String hardwareId) {
        String sql = "SELECT hardware_id, nome_aparelho, so_tipo, data_registro, data_expiracao, status, nome_cliente, tarifa_hora, vagas_carro, vagas_moto, nome_cliente_pendente, tarifa_hora_pendente, vagas_carro_pendente, vagas_moto_pendente, dias_licenca_pendente, ultimo_ping FROM terminais WHERE hardware_id = ?;";
        try (Connection conn = obterConexao();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, hardwareId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String nomeCli = rs.getString("nome_cliente");
                    String nomeCliPend = rs.getString("nome_cliente_pendente");
                    return new TerminalInfo(
                        rs.getString("hardware_id"),
                        rs.getString("nome_aparelho"),
                        rs.getString("so_tipo"),
                        rs.getLong("data_registro"),
                        rs.getLong("data_expiracao"),
                        rs.getString("status"),
                        nomeCli != null ? nomeCli : "",
                        rs.getDouble("tarifa_hora"),
                        rs.getInt("vagas_carro"),
                        rs.getInt("vagas_moto"),
                        nomeCliPend != null ? nomeCliPend : "",
                        rs.getDouble("tarifa_hora_pendente"),
                        rs.getInt("vagas_carro_pendente"),
                        rs.getInt("vagas_moto_pendente"),
                        rs.getInt("dias_licenca_pendente"),
                        rs.getLong("ultimo_ping")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("Erro ao obter terminal do SQLite: " + e.getMessage());
        }
        return null;
    }

    public void atualizarConfigTerminal(String hardwareId, String nomeCliente, double tarifaHora, int vagasCarro, int vagasMoto, int diasLicenca) {
        long dataExpiracao = System.currentTimeMillis() + (diasLicenca * 24L * 60L * 60L * 1000L);
        String sql = "UPDATE terminais SET nome_cliente = ?, tarifa_hora = ?, vagas_carro = ?, vagas_moto = ?, data_expiracao = ?, status = 'ATIVO' WHERE hardware_id = ?;";
        try (Connection conn = obterConexao();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nomeCliente);
            pstmt.setDouble(2, tarifaHora);
            pstmt.setInt(3, vagasCarro);
            pstmt.setInt(4, vagasMoto);
            pstmt.setLong(5, dataExpiracao);
            pstmt.setString(6, hardwareId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erro ao atualizar configuracoes do terminal no SQLite: " + e.getMessage());
        }
    }

    public void atualizarConfigPendenteTerminal(String hardwareId, String nomeCliente, double tarifaHora, int vagasCarro, int vagasMoto, int diasLicenca) {
        String sql = "UPDATE terminais SET nome_cliente_pendente = ?, tarifa_hora_pendente = ?, vagas_carro_pendente = ?, vagas_moto_pendente = ?, dias_licenca_pendente = ? WHERE hardware_id = ?;";
        try (Connection conn = obterConexao();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nomeCliente);
            pstmt.setDouble(2, tarifaHora);
            pstmt.setInt(3, vagasCarro);
            pstmt.setInt(4, vagasMoto);
            pstmt.setInt(5, diasLicenca);
            pstmt.setString(6, hardwareId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erro ao atualizar configuracoes pendentes do terminal no SQLite: " + e.getMessage());
        }
    }

    private boolean temConfigPendente(TerminalInfo term) {
        return term.getTarifaHoraPendente() >= 0
            || term.getVagasCarroPendente() >= 0
            || term.getVagasMotoPendente() >= 0
            || term.getDiasLicencaPendente() >= 0
            || (term.getNomeClientePendente() != null && !term.getNomeClientePendente().trim().isEmpty());
    }

    public void enviarConfigTerminal(String hardwareId) {
        TerminalInfo term = obterTerminal(hardwareId);
        if (term == null) return;

        if (temConfigPendente(term)) {
            long dataExpiracao = System.currentTimeMillis() + (term.getDiasLicencaPendente() * 24L * 60L * 60L * 1000L);
            String sql = "UPDATE terminais SET nome_cliente = ?, tarifa_hora = ?, vagas_carro = ?, vagas_moto = ?, data_expiracao = ?, status = 'ATIVO', "
                       + "nome_cliente_pendente = NULL, tarifa_hora_pendente = -1.0, vagas_carro_pendente = -1, vagas_moto_pendente = -1, dias_licenca_pendente = -1 WHERE hardware_id = ?;";
            try (Connection conn = obterConexao();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, term.getNomeClientePendente());
                pstmt.setDouble(2, term.getTarifaHoraPendente());
                pstmt.setInt(3, term.getVagasCarroPendente());
                pstmt.setInt(4, term.getVagasMotoPendente());
                pstmt.setLong(5, dataExpiracao);
                pstmt.setString(6, hardwareId);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                System.err.println("Erro ao enviar configuracoes do terminal no SQLite: " + e.getMessage());
            }
        }
    }

    public void excluirTerminal(String hardwareId) {
        String sql = "DELETE FROM terminais WHERE hardware_id = ?;";
        try (Connection conn = obterConexao();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, hardwareId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erro ao excluir terminal do SQLite: " + e.getMessage());
        }
    }

    public void adicionarMensalista(String placa, String nome, String telefone, long vencimento) {
        String sql = "INSERT OR REPLACE INTO mensalistas (placa, nome_cliente, telefone, vencimento, status) VALUES (?, ?, ?, ?, 'ATIVO');";
        try (Connection conn = obterConexao();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, placa.toUpperCase().trim());
            pstmt.setString(2, nome);
            pstmt.setString(3, telefone);
            pstmt.setLong(4, vencimento);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erro ao adicionar mensalista: " + e.getMessage());
        }
    }

    public void atualizarMensalista(String placa, String status, long vencimento) {
        String sql = "UPDATE mensalistas SET status = ?, vencimento = ? WHERE placa = ?;";
        try (Connection conn = obterConexao();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setLong(2, vencimento);
            pstmt.setString(3, placa.toUpperCase().trim());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erro ao atualizar mensalista: " + e.getMessage());
        }
    }

    public void excluirMensalista(String placa) {
        String sql = "DELETE FROM mensalistas WHERE placa = ?;";
        try (Connection conn = obterConexao();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, placa.toUpperCase().trim());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erro ao excluir mensalista: " + e.getMessage());
        }
    }

    public List<MensalistaInfo> listarMensalistas() {
        List<MensalistaInfo> lista = new ArrayList<>();
        String sql = "SELECT id, placa, nome_cliente, telefone, vencimento, status FROM mensalistas ORDER BY nome_cliente ASC;";
        try (Connection conn = obterConexao();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                lista.add(new MensalistaInfo(
                    rs.getInt("id"),
                    rs.getString("placa"),
                    rs.getString("nome_cliente"),
                    rs.getString("telefone"),
                    rs.getLong("vencimento"),
                    rs.getString("status")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Erro ao listar mensalistas: " + e.getMessage());
        }
        return lista;
    }

    public void salvarTransacaoSincronizada(Transacao t) {
        String sql = "INSERT INTO transacoes (placa, hora_entrada, hora_saida, valor_pago, tarifa_cobrada, hardware_id) VALUES (?, ?, ?, ?, ?, ?);";
        try (Connection conn = obterConexao();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, t.getPlaca());
            pstmt.setLong(2, t.getHoraEntrada());
            pstmt.setLong(3, t.getHoraSaida());
            pstmt.setDouble(4, t.getValorPago());
            pstmt.setDouble(5, t.getTarifaCobrada());
            pstmt.setString(6, t.getHardwareId());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erro ao salvar transacao sincronizada: " + e.getMessage());
        }
    }

    public List<Transacao> listarTodasTransacoes() {
        List<Transacao> lista = new ArrayList<>();
        String sql = "SELECT placa, hora_entrada, hora_saida, valor_pago, tarifa_cobrada, hardware_id FROM transacoes ORDER BY hora_saida DESC;";
        try (Connection conn = obterConexao();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                lista.add(new Transacao(
                    rs.getString("placa"),
                    rs.getLong("hora_entrada"),
                    rs.getLong("hora_saida"),
                    rs.getDouble("valor_pago"),
                    rs.getDouble("tarifa_cobrada"),
                    rs.getString("hardware_id")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Erro ao listar todas transacoes: " + e.getMessage());
        }
        return lista;
    }
}
