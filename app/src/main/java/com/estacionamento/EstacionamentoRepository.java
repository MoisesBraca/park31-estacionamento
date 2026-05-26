package com.estacionamento;

import android.app.Application;
import androidx.lifecycle.LiveData;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EstacionamentoRepository {
    private final EstacionamentoDao dao;
    private final VagaDao vagaDao;
    private final TarifaConfigDao tarifaDao;
    private final AuditLogDao auditLogDao;
    private final MensalistaDao mensalistaDao;
    private final ExecutorService executorService;
    private final android.app.Application application;

    private static EstacionamentoRepository INSTANCE;

    public static synchronized EstacionamentoRepository getInstance(android.app.Application application) {
        if (INSTANCE == null) {
            INSTANCE = new EstacionamentoRepository(application);
        }
        return INSTANCE;
    }

    private EstacionamentoRepository(android.app.Application application) {
        this.application = application;
        AppDatabase db = AppDatabase.getDatabase(application);
        dao = db.estacionamentoDao();
        vagaDao = db.vagaDao();
        tarifaDao = db.tarifaConfigDao();
        auditLogDao = db.auditLogDao();
        mensalistaDao = db.mensalistaDao();
        executorService = Executors.newFixedThreadPool(4);

        executorService.execute(vagaDao::limparVagasOrfas);
        carregarPrecosDoBanco();
        sincronizarMensalistas(null);
    }

    public void registrarAuditoria(String acao, String detalhes) {
        executorService.execute(() -> {
            String operador = SessaoManager.getInstance().getPerfil().getNome();
            long ts = System.currentTimeMillis();
            auditLogDao.insert(new AuditLog(operador, acao, ts, detalhes));

            // Sincronizar com o servidor de licenças central
            try {
                String ip = LicencaHelper.getServerIp(application);
                String urlStr;
                if (ip.contains("://")) {
                    urlStr = ip + "/api/audit/sync";
                } else if (ip.contains(".") && !ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                    urlStr = "https://" + ip + "/api/audit/sync";
                } else {
                    urlStr = "http://" + ip + ":8080/api/audit/sync";
                }
                java.net.URL url = new java.net.URL(urlStr);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                String escapedDetalhes = detalhes != null ? detalhes.replace("\"", "\\\"") : "";
                String json = "{\"operador\":\"" + operador 
                            + "\",\"acao\":\"" + acao 
                            + "\",\"timestamp\":\"" + ts 
                            + "\",\"detalhes\":\"" + escapedDetalhes + "\"}";

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {}
        });
    }

    private void carregarPrecosDoBanco() {
        executorService.execute(() -> {
            try {
                double hora = tarifaDao.getValorByTipoSync("HORA");
                if (hora > 0) CalculadoraTarifa.carregar(hora);

                double ducha = tarifaDao.getValorByTipoSync("LAVAGEM_DUCHA");
                double simples = tarifaDao.getValorByTipoSync("LAVAGEM_SIMPLES");
                double completa = tarifaDao.getValorByTipoSync("LAVAGEM_COMPLETA");
                if (ducha > 0 || simples > 0 || completa > 0) {
                    PrecosServicos.carregar(
                        ducha > 0 ? ducha : 15.0,
                        simples > 0 ? simples : 30.0,
                        completa > 0 ? completa : 50.0
                    );
                }
            } catch (Exception ignored) {}
        });
    }

    public void registrarEntrada(String placa, boolean temLavagem, String tipoLavagem,
                                  double valorLavagem, OnEntradaListener listener) {
        registrarEntrada(placa, temLavagem, tipoLavagem, valorLavagem, null, listener);
    }

    public void registrarEntrada(String placa, boolean temLavagem, String tipoLavagem,
                                  double valorLavagem, String fotoAvariaPath, OnEntradaListener listener) {
        executorService.execute(() -> {
            Veiculo existente = dao.getVeiculoEstacionado(placa.toUpperCase());
            if (existente != null) {
                if (listener != null)
                    listener.onError("Veículo " + placa.toUpperCase() + " já está estacionado!");
                return;
            }

            Veiculo veiculo = new Veiculo(placa);
            veiculo.setTemLavagem(temLavagem);
            veiculo.setTipoLavagem(tipoLavagem);
            veiculo.setValorLavagem(valorLavagem);
            veiculo.setFotoAvariaPath(fotoAvariaPath);

            Vaga vagaLivre = vagaDao.getVagaLivreByTipoSync("CARRO");
            if (vagaLivre != null) {
                veiculo.setVagaId(vagaLivre.getId());
                vagaDao.updateStatus(vagaLivre.getId(), "OCUPADA");
            }

            dao.insertVeiculo(veiculo);
            
            String logFoto = fotoAvariaPath != null ? fotoAvariaPath.replace("\\", "/") : "";
            registrarAuditoria("ENTRADA",
                "{\"placa\":\"" + veiculo.getPlaca() + "\",\"lavagem\":\"" + tipoLavagem + "\",\"vaga\":" + veiculo.getVagaId() + ",\"foto\":\"" + logFoto + "\"}");
            if (listener != null) listener.onSuccess();
        });
    }

    public void registrarSaida(String placa, double valorPago, String formaPagamento, OnSaidaListener listener) {
        executorService.execute(() -> {
            Veiculo veiculo = dao.getVeiculoEstacionado(placa.toUpperCase());
            if (veiculo != null) {
                if (veiculo.getVagaId() > 0) {
                    vagaDao.liberarVaga(veiculo.getVagaId());
                }

                veiculo.registrarSaida();
                dao.updateVeiculo(veiculo);

                double tarifaEstacionamento = CalculadoraTarifa.calcularTarifa(veiculo.getTempoEstacionado());
                double totalPagar = tarifaEstacionamento + veiculo.getValorLavagem();

                Transacao transacao = new Transacao(veiculo.getPlaca(),
                    veiculo.getHoraEntrada(), veiculo.getHoraSaida(),
                    valorPago, totalPagar, formaPagamento);
                dao.insertTransacao(transacao);
                enviarTransacaoParaServidor(transacao);
                registrarAuditoria("SAIDA",
                    "{\"placa\":\"" + veiculo.getPlaca() + "\",\"valorPago\":" + valorPago + ",\"pagamento\":\"" + formaPagamento + "\"}");
                if (listener != null) listener.onSuccess(transacao);
            } else {
                if (listener != null) listener.onError("Veículo não encontrado ou já saiu.");
            }
        });
    }

    public void marcarLavagemConcluida(String placa) {
        executorService.execute(() -> {
            Veiculo veiculo = dao.getVeiculoEstacionado(placa);
            if (veiculo != null) {
                veiculo.setLavagemConcluida(true);
                dao.updateVeiculo(veiculo);
            }
        });
    }

    public Veiculo getVeiculoByVagaIdSync(int vagaId) {
        return dao.getVeiculoByVagaId(vagaId);
    }

    public void buscarVeiculo(String placa, OnVeiculoEncontradoListener listener) {
        executorService.execute(() -> {
            Veiculo veiculo = dao.getVeiculoEstacionado(placa.toUpperCase());
            if (veiculo != null) {
                listener.onSuccess(veiculo);
            } else {
                listener.onError("Veículo não encontrado ou já saiu.");
            }
        });
    }

    public void salvarTarifaConfig(String tipo, double valorBase) {
        executorService.execute(() -> {
            TarifaConfig config = tarifaDao.getByTipoSync(tipo);
            if (config != null) {
                config.setValorBase(valorBase);
                tarifaDao.update(config);
                registrarAuditoria("TARIFA_EDITADA",
                    "{\"tipo\":\"" + tipo + "\",\"novoValor\":" + valorBase + "}");
            }
        });
    }

    public LiveData<List<Veiculo>> getVeiculosEstacionados() { return dao.getVeiculosEstacionados(); }
    public LiveData<List<Transacao>> getAllTransacoes() { return dao.getAllTransacoes(); }
    public LiveData<Double> getReceitaTotal() { return dao.getReceitaTotal(); }
    public LiveData<Integer> getTotalAtendidos() { return dao.getTotalAtendidos(); }
    public LiveData<List<Vaga>> getAllVagas() { 
        executorService.execute(vagaDao::limparVagasOrfas);
        return vagaDao.getAll(); 
    }
    public LiveData<Integer> getVagasLivres() { return vagaDao.getVagasLivres(); }
    public LiveData<Integer> getVagasOcupadas() { return vagaDao.getVagasOcupadas(); }
    public LiveData<List<TarifaConfig>> getAllTarifas() { return tarifaDao.getAll(); }

    public void liberarVagaManual(int vagaId) {
        executorService.execute(() -> vagaDao.liberarVaga(vagaId));
    }

    public LiveData<List<Transacao>> getTransacoesByPeriodo(long inicio, long fim) {
        return dao.getTransacoesByPeriodo(inicio, fim);
    }

    public void exportarCsvPeriodo(long inicio, long fim, OnExportCsvListener listener) {
        executorService.execute(() -> {
            try {
                List<Transacao> lista = dao.getTransacoesByPeriodoSync(inicio, fim);
                int count = dao.getCountByPeriodoSync(inicio, fim);
                double receita = dao.getReceitaByPeriodoSync(inicio, fim);
                listener.onSuccess(lista, count, receita);
            } catch (Exception e) {
                listener.onError(e.getMessage());
            }
        });
    }

    public Mensalista obterMensalistaSync(String placa) {
        return mensalistaDao.getByPlaca(placa.toUpperCase().trim());
    }

    public void sincronizarMensalistas(Runnable callback) {
        executorService.execute(() -> {
            try {
                String ip = LicencaHelper.getServerIp(application);
                String urlStr;
                if (ip.contains("://")) {
                    urlStr = ip + "/api/mensalistas/sync";
                } else if (ip.contains(".") && !ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                    urlStr = "https://" + ip + "/api/mensalistas/sync";
                } else {
                    urlStr = "http://" + ip + ":8080/api/mensalistas/sync";
                }

                java.net.URL url = new java.net.URL(urlStr);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);

                int code = conn.getResponseCode();
                if (code == 200) {
                    java.io.BufferedReader in = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        sb.append(line);
                    }
                    in.close();

                    String body = sb.toString().trim();
                    if (body.startsWith("[") && body.endsWith("]")) {
                        java.util.List<Mensalista> lista = new java.util.ArrayList<>();
                        int index = 0;
                        while (index < body.length()) {
                            int startObj = body.indexOf("{", index);
                            if (startObj == -1) break;
                            int endObj = body.indexOf("}", startObj);
                            if (endObj == -1) break;
                            String obj = body.substring(startObj + 1, endObj);
                            
                            String placa = "";
                            String nome = "";
                            String tel = "";
                            long venc = 0;
                            String status = "ATIVO";

                            String[] pairs = obj.split(",");
                            for (String pair : pairs) {
                                String[] kv = pair.split(":", 2);
                                if (kv.length == 2) {
                                    String k = kv[0].replace("\"", "").trim();
                                    String v = kv[1].replace("\"", "").trim();
                                    if (k.equals("placa")) placa = v;
                                    else if (k.equals("nomeCliente")) nome = v;
                                    else if (k.equals("telefone")) tel = v;
                                    else if (k.equals("vencimento")) venc = Long.parseLong(v);
                                    else if (k.equals("status")) status = v;
                                }
                            }

                            if (!placa.isEmpty()) {
                                lista.add(new Mensalista(placa, nome, tel, venc, status));
                            }
                            index = endObj + 1;
                        }

                        mensalistaDao.deleteAll();
                        if (!lista.isEmpty()) {
                            mensalistaDao.insertAll(lista);
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) {
            } finally {
                if (callback != null) callback.run();
            }
        });
    }

    public void enviarTransacaoParaServidor(Transacao t) {
        executorService.execute(() -> {
            try {
                String ip = LicencaHelper.getServerIp(application);
                String urlStr;
                if (ip.contains("://")) {
                    urlStr = ip + "/api/transacoes/sync";
                } else if (ip.contains(".") && !ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                    urlStr = "https://" + ip + "/api/transacoes/sync";
                } else {
                    urlStr = "http://" + ip + ":8080/api/transacoes/sync";
                }
                
                String hwid = LicencaHelper.getHardwareId(application);

                java.net.URL url = new java.net.URL(urlStr);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setDoOutput(true);
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                String payload = "placa=" + java.net.URLEncoder.encode(t.getPlaca(), "UTF-8")
                        + "&entrada=" + t.getHoraEntrada()
                        + "&saida=" + t.getHoraSaida()
                        + "&valorPago=" + t.getValorPago()
                        + "&tarifaCobrada=" + t.getTarifaCobrada()
                        + "&hardwareId=" + java.net.URLEncoder.encode(hwid, "UTF-8");

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    byte[] input = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {
            }
        });
    }

    public interface OnEntradaListener {
        void onSuccess();
        void onError(String message);
    }

    public interface OnSaidaListener {
        void onSuccess(Transacao transacao);
        void onError(String message);
    }

    public interface OnVeiculoEncontradoListener {
        void onSuccess(Veiculo veiculo);
        void onError(String message);
    }

    public interface OnExportCsvListener {
        void onSuccess(List<Transacao> transacoes, int totalSaidas, double receita);
        void onError(String error);
    }
}
