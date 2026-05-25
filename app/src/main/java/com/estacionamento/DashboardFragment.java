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
        viewModel.veiculosEstacionados.observe(getViewLifecycleOwner(), veiculos -> 
            binding.dashValorOcupadas.setText(String.valueOf(veiculos.size())));

        viewModel.totalAtendidos.observe(getViewLifecycleOwner(), total -> 
            binding.dashValorAtendidos.setText(String.valueOf(total != null ? total : 0)));

        viewModel.receitaTotal.observe(getViewLifecycleOwner(), receita -> 
            binding.dashValorReceita.setText("R$ " + String.format("%.2f", receita != null ? receita : 0.0)));

        binding.dashValorTarifa.setText("R$ " + String.format("%.2f", viewModel.getTarifaHora()));
    }

    private void setupClickListeners() {
        // Clicar em "Pátio" vai para a lista de veículos estacionados
        binding.cardStatsPatio.setOnClickListener(v -> 
            Navigation.findNavController(v).navigate(R.id.nav_listar));

        // Clicar em "Atendidos" vai para o relatório do dia
        binding.cardStatsAtendidos.setOnClickListener(v -> 
            Navigation.findNavController(v).navigate(R.id.nav_relatorio));

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
