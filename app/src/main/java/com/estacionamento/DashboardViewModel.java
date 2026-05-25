package com.estacionamento;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import java.util.List;

public class DashboardViewModel extends AndroidViewModel {

    private final EstacionamentoRepository repository;
    public final LiveData<List<Veiculo>> veiculosEstacionados;
    public final LiveData<List<Transacao>> transacoesRecentes;
    public final LiveData<Double> receitaTotal;
    public final LiveData<Integer> totalAtendidos;
    public final LiveData<Integer> vagasLivres;
    public final LiveData<Integer> vagasOcupadas;

    public DashboardViewModel(@NonNull Application application) {
        super(application);
        repository = EstacionamentoRepository.getInstance(application);
        veiculosEstacionados = repository.getVeiculosEstacionados();
        transacoesRecentes = repository.getAllTransacoes();
        receitaTotal = repository.getReceitaTotal();
        totalAtendidos = repository.getTotalAtendidos();
        vagasLivres = repository.getVagasLivres();
        vagasOcupadas = repository.getVagasOcupadas();
    }

    public double getTarifaHora() {
        return CalculadoraTarifa.getTarifaHora();
    }
}
