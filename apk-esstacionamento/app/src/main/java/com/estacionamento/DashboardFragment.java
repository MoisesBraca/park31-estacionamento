package com.estacionamento;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.estacionamento.R;
import com.estacionamento.databinding.FragmentDashboardBinding;

public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;
    private DashboardViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(DashboardViewModel.class);

        setupObservers();
        setupClickListeners();

        return binding.getRoot();
    }

    private void setupObservers() {
        viewModel.veiculosEstacionados.observe(getViewLifecycleOwner(), veiculos -> {
            int ocupadas = veiculos.size();
            binding.dashValorOcupadas.setText(String.valueOf(ocupadas));
            
            // Calculando total com vagasLivre se tivermos valor
            Integer livres = viewModel.vagasLivres.getValue();
            int total = ocupadas + (livres != null ? livres : 0);
            binding.dashValorCapacidade.setText("Ocupação total: " + total);
        });

        // Observe vagas livres para atualizar a capacidade em tempo real
        viewModel.vagasLivres.observe(getViewLifecycleOwner(), livres -> {
            Integer ocupadas = viewModel.vagasOcupadas.getValue();
            int o = (ocupadas != null) ? ocupadas : 0;
            int total = o + (livres != null ? livres : 0);
            binding.dashValorCapacidade.setText("Ocupação total: " + total);
        });

        viewModel.totalAtendidos.observe(getViewLifecycleOwner(), total -> {
            int count = (total != null) ? total : 0;
            binding.dashValorOsPagas.setText(String.valueOf(count));
        });

        viewModel.receitaTotal.observe(getViewLifecycleOwner(), receita -> {
            double rec = (receita != null) ? receita : 0.0;
            String recStr = String.format("%.2f", rec);
            binding.dashValorOsPagasTotal.setText("Total: R$ " + recStr);
            binding.dashValorReceita.setText("R$ " + recStr);
            
            // Mock de Despesa e Lucro
            double despesa = 0.0; // TODO: Implementar lógica de despesas no futuro
            double lucro = rec - despesa;
            binding.dashValorDespesa.setText("R$ " + String.format("%.2f", despesa));
            binding.dashValorLucro.setText("R$ " + String.format("%.2f", lucro));
        });
        
        // Mock de OS Canceladas (até que o backend suporte)
        binding.dashValorOsCanceladas.setText("0");
        binding.dashValorOsCanceladasTotal.setText("Total: R$ 0,00");
    }

    private void setupClickListeners() {
        // Clicar em "Pátio" vai para a lista de veículos estacionados
        binding.cardStatsPatio.setOnClickListener(v -> 
            Navigation.findNavController(v).navigate(R.id.nav_listar));

        binding.cardBtnHistorico.setOnClickListener(v -> 
            Navigation.findNavController(v).navigate(R.id.nav_historico));
            
        binding.cardBtnRelatorio.setOnClickListener(v -> 
            Navigation.findNavController(v).navigate(R.id.nav_relatorio));
            
        binding.cardBtnTarifa.setOnClickListener(v -> 
            Navigation.findNavController(v).navigate(R.id.nav_tarifa));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
