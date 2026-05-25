package com.estacionamento;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.estacionamento.databinding.FragmentGraficosBinding;

public class GraficosFragment extends Fragment {

    private FragmentGraficosBinding binding;
    private DashboardViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentGraficosBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(DashboardViewModel.class);

        setupObservers();

        return binding.getRoot();
    }

    private void setupObservers() {
        viewModel.transacoesRecentes.observe(getViewLifecycleOwner(), transacoes -> {
            if (transacoes != null) {
                DashboardGraficosHelper.configurarGraficoReceita(binding.chartReceitaPro, transacoes);
                DashboardGraficosHelper.configurarGraficoOcupacao(binding.chartFluxoPro, transacoes);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
