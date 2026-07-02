package com.estacionamento;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RelatorioViewModel extends AndroidViewModel {

    private final EstacionamentoRepository repository;
    public final LiveData<Integer> totalAtendidos;
    public final LiveData<List<Veiculo>> veiculosEstacionados;
    public final LiveData<Double> receitaTotal;

    private final MutableLiveData<List<Transacao>> transacoesFiltradas = new MutableLiveData<>();
    private final MutableLiveData<Double> ticketMedio = new MutableLiveData<>(0.0);
    private final MutableLiveData<Integer> saidasNoPeriodo = new MutableLiveData<>(0);
    private final MutableLiveData<Double> receitaNoPeriodo = new MutableLiveData<>(0.0);
    private final MutableLiveData<UiState<String>> exportState = new MutableLiveData<>(UiState.idle());

    public RelatorioViewModel(@NonNull Application application) {
        super(application);
        repository = EstacionamentoRepository.getInstance(application);
        totalAtendidos = repository.getTotalAtendidos();
        veiculosEstacionados = repository.getVeiculosEstacionados();
        receitaTotal = repository.getReceitaTotal();
    }

    public double getTarifaHora() { return CalculadoraTarifa.getTarifaHora(); }

    public LiveData<List<Transacao>> getTransacoesFiltradas() { return transacoesFiltradas; }
    public LiveData<Double> getTicketMedio() { return ticketMedio; }
    public LiveData<Integer> getSaidasNoPeriodo() { return saidasNoPeriodo; }
    public LiveData<Double> getReceitaNoPeriodo() { return receitaNoPeriodo; }
    public LiveData<UiState<String>> getExportState() { return exportState; }
    public void resetExportState() { exportState.setValue(UiState.idle()); }

    public LiveData<List<Transacao>> getTransacoesByPeriodo(long inicio, long fim) {
        return repository.getTransacoesByPeriodo(inicio, fim);
    }

    public void filtrarPeriodo(long inicio, long fim) {
        repository.getTransacoesByPeriodo(inicio, fim).observeForever(new androidx.lifecycle.Observer<List<Transacao>>() {
            @Override
            public void onChanged(List<Transacao> transacoes) {
                if (transacoes != null) {
                    transacoesFiltradas.setValue(transacoes);
                    saidasNoPeriodo.setValue(transacoes.size());
                    double receita = 0;
                    for (Transacao t : transacoes) receita += t.getValorPago();
                    receitaNoPeriodo.setValue(receita);
                    ticketMedio.setValue(transacoes.size() > 0 ? receita / transacoes.size() : 0.0);
                }
            }
        });
    }

    public void exportarCsv(long inicio, long fim) {
        exportState.setValue(UiState.loading());
        repository.exportarCsvPeriodo(inicio, fim, new EstacionamentoRepository.OnExportCsvListener() {
            @Override
            public void onSuccess(List<Transacao> transacoes, int totalSaidas, double receita) {
                try {
                    SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                    StringBuilder csv = new StringBuilder();
                    csv.append("Placa;Entrada;Saída;Valor Pago;Tarifa;Forma Pagamento\n");
                    for (Transacao t : transacoes) {
                        csv.append(t.getPlaca()).append(";")
                           .append(df.format(new Date(t.getHoraEntrada()))).append(";")
                           .append(df.format(new Date(t.getHoraSaida()))).append(";")
                           .append(String.format("%.2f", t.getValorPago())).append(";")
                           .append(String.format("%.2f", t.getTarifaCobrada())).append(";")
                           .append(t.getFormaPagamento()).append("\n");
                    }
                    csv.append("\nResumo;\n");
                    csv.append("Total Saídas;").append(totalSaidas).append("\n");
                    csv.append("Receita;").append(String.format("%.2f", receita)).append("\n");
                    csv.append("Ticket Médio;").append(String.format("%.2f", transacoes.isEmpty() ? 0 : receita / transacoes.size())).append("\n");

                    exportState.postValue(UiState.success(csv.toString()));
                } catch (Exception e) {
                    exportState.postValue(UiState.error(e.getMessage()));
                }
            }

            @Override
            public void onError(String error) {
                exportState.postValue(UiState.error(error));
            }
        });
    }
}
