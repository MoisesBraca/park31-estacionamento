package com.estacionamento;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EntradaViewModel extends AndroidViewModel {

    private final EstacionamentoRepository repository;
    private final ExecutorService executor;
    private final MutableLiveData<UiState<Void>> entradaState = new MutableLiveData<>(UiState.idle());

    public EntradaViewModel(@NonNull Application application) {
        super(application);
        repository = EstacionamentoRepository.getInstance(application);
        executor = Executors.newSingleThreadExecutor();
    }

    public LiveData<UiState<Void>> getEntradaState() {
        return entradaState;
    }

    public void registrarEntrada(String placa, boolean temLavagem, String tipoLavagem, double valorLavagem) {
        entradaState.setValue(UiState.loading());
        repository.registrarEntrada(placa.toUpperCase().trim(), temLavagem, tipoLavagem, valorLavagem,
            new EstacionamentoRepository.OnEntradaListener() {
                @Override
                public void onSuccess() {
                    entradaState.postValue(UiState.success(null));
                }
                @Override
                public void onError(String message) {
                    entradaState.postValue(UiState.error(message));
                }
            });
    }

    public void resetState() {
        entradaState.setValue(UiState.idle());
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
