package com.estacionamento;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import java.util.List;

public class MainViewModel extends AndroidViewModel {
    private final EstacionamentoRepository repository;
    private final LiveData<List<Veiculo>> veiculosEstacionados;
    private final LiveData<List<Transacao>> allTransacoes;

    public MainViewModel(@NonNull Application application) {
        super(application);
        repository = EstacionamentoRepository.getInstance(application);
        veiculosEstacionados = repository.getVeiculosEstacionados();
        allTransacoes = repository.getAllTransacoes();
    }

    public LiveData<List<Veiculo>> getVeiculosEstacionados() { return veiculosEstacionados; }
    public LiveData<List<Transacao>> getAllTransacoes() { return allTransacoes; }

    public void registrarEntrada(String placa, boolean temLavagem, String tipoLavagem, double valorLavagem) {
        repository.registrarEntrada(placa, temLavagem, tipoLavagem, valorLavagem, null);
    }

    public void buscarVeiculo(String placa, EstacionamentoRepository.OnVeiculoEncontradoListener listener) {
        repository.buscarVeiculo(placa, listener);
    }

    public void registrarSaida(String placa, double valorPago, String formaPagamento, EstacionamentoRepository.OnSaidaListener listener) {
        repository.registrarSaida(placa, valorPago, formaPagamento, listener);
    }

    public void marcarLavagemConcluida(String placa) {
        repository.marcarLavagemConcluida(placa);
    }

    public LiveData<List<Transacao>> getTransacoesByPeriodo(long inicio, long fim) {
        return repository.getTransacoesByPeriodo(inicio, fim);
    }
}
