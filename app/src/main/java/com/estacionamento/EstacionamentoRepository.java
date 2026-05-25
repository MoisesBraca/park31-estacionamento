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
    private final ExecutorService executorService;

    private static EstacionamentoRepository INSTANCE;

    public static synchronized EstacionamentoRepository getInstance(Application application) {
        if (INSTANCE == null) {
            INSTANCE = new EstacionamentoRepository(application);
        }
        return INSTANCE;
    }

    private EstacionamentoRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        dao = db.estacionamentoDao();
        vagaDao = db.vagaDao();
        tarifaDao = db.tarifaConfigDao();
        auditLogDao = db.auditLogDao();
        executorService = Executors.newFixedThreadPool(4);

        executorService.execute(vagaDao::limparVagasOrfas);
        carregarPrecosDoBanco();
    }

    private void registrarAuditoria(String acao, String detalhes) {
        executorService.execute(() -> {
            String operador = SessaoManager.getInstance().getPerfil().getNome();
            auditLogDao.insert(new AuditLog(operador, acao, System.currentTimeMillis(), detalhes));
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

            Vaga vagaLivre = vagaDao.getVagaLivreByTipoSync("CARRO");
            if (vagaLivre != null) {
                veiculo.setVagaId(vagaLivre.getId());
                vagaDao.updateStatus(vagaLivre.getId(), "OCUPADA");
            }

            dao.insertVeiculo(veiculo);
            registrarAuditoria("ENTRADA",
                "{\"placa\":\"" + veiculo.getPlaca() + "\",\"lavagem\":\"" + tipoLavagem + "\",\"vaga\":" + veiculo.getVagaId() + "}");
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
