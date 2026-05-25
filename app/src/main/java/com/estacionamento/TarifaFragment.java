package com.estacionamento;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.estacionamento.R;
import com.estacionamento.databinding.FragmentTarifaBinding;
import com.google.android.material.snackbar.Snackbar;

public class TarifaFragment extends Fragment {

    private FragmentTarifaBinding binding;
    private EstacionamentoRepository repository;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentTarifaBinding.inflate(inflater, container, false);
        repository = EstacionamentoRepository.getInstance(requireActivity().getApplication());

        carregarPrecosAtuais();
        binding.btnSalvarPrecos.setOnClickListener(v -> salvarPrecos());

        return binding.getRoot();
    }

    private void carregarPrecosAtuais() {
        binding.etTarifaEstacionamento.setText(String.valueOf(CalculadoraTarifa.getTarifaHora()));
        binding.etTarifaDucha.setText(String.valueOf(PrecosServicos.getPrecoDucha()));
        binding.etTarifaSimples.setText(String.valueOf(PrecosServicos.getPrecoSimples()));
        binding.etTarifaCompleta.setText(String.valueOf(PrecosServicos.getPrecoCompleta()));
    }

    private void salvarPrecos() {
        try {
            double h = Double.parseDouble(binding.etTarifaEstacionamento.getText().toString());
            double d = Double.parseDouble(binding.etTarifaDucha.getText().toString());
            double s = Double.parseDouble(binding.etTarifaSimples.getText().toString());
            double c = Double.parseDouble(binding.etTarifaCompleta.getText().toString());

            CalculadoraTarifa.setTarifaHora(h);
            PrecosServicos.setPrecoDucha(d);
            PrecosServicos.setPrecoSimples(s);
            PrecosServicos.setPrecoCompleta(c);

            repository.salvarTarifaConfig("HORA", h);
            repository.salvarTarifaConfig("LAVAGEM_DUCHA", d);
            repository.salvarTarifaConfig("LAVAGEM_SIMPLES", s);
            repository.salvarTarifaConfig("LAVAGEM_COMPLETA", c);

            Snackbar.make(binding.getRoot(), "Todos os preços atualizados!", Snackbar.LENGTH_SHORT).show();
        } catch (Exception e) {
            Snackbar.make(binding.getRoot(), "Erro: Verifique os valores digitados", Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
