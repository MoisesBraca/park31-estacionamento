package com.estacionamento;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SaidaViewModel extends AndroidViewModel {

    private final EstacionamentoRepository repository;
    private final ExecutorService executor;

    private final MutableLiveData<UiState<Veiculo>> buscaState = new MutableLiveData<>(UiState.idle());
    private final MutableLiveData<UiState<Transacao>> saidaState = new MutableLiveData<>(UiState.idle());

    public SaidaViewModel(@NonNull Application application) {
        super(application);
        repository = EstacionamentoRepository.getInstance(application);
        executor = Executors.newSingleThreadExecutor();
    }

    public LiveData<UiState<Veiculo>> getBuscaState() { return buscaState; }
    public LiveData<UiState<Transacao>> getSaidaState() { return saidaState; }

    public void buscarVeiculo(String placa) {
        buscaState.setValue(UiState.loading());
        repository.buscarVeiculo(placa.toUpperCase().trim(),
            new EstacionamentoRepository.OnVeiculoEncontradoListener() {
                @Override
                public void onSuccess(Veiculo veiculo) {
                    buscaState.postValue(UiState.success(veiculo));
                }
                @Override
                public void onError(String message) {
                    buscaState.postValue(UiState.error(message));
                }
            });
    }

    public void registrarSaida(String placa, double valorPago, String formaPagamento) {
        saidaState.setValue(UiState.loading());
        repository.registrarSaida(placa.toUpperCase().trim(), valorPago, formaPagamento,
            new EstacionamentoRepository.OnSaidaListener() {
                @Override
                public void onSuccess(Transacao transacao) {
                    saidaState.postValue(UiState.success(transacao));
                }
                @Override
                public void onError(String message) {
                    saidaState.postValue(UiState.error(message));
                }
            });
    }

    public void resetBusca() { buscaState.setValue(UiState.idle()); }
    public void resetSaida() { saidaState.setValue(UiState.idle()); }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
