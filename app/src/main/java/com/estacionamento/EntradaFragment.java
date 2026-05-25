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
import com.estacionamento.databinding.FragmentEntradaBinding;
import com.google.android.material.snackbar.Snackbar;

public class EntradaFragment extends Fragment {

    private FragmentEntradaBinding binding;
    private EntradaViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentEntradaBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(EntradaViewModel.class);

        binding.etEntradaPlaca.addTextChangedListener(new MascaraPlaca(binding.etEntradaPlaca));

        binding.cbLavagem.setOnCheckedChangeListener((buttonView, isChecked) -> {
            binding.layoutServicos.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        atualizarTextosLavagem();
        
        binding.btnScan.setOnClickListener(v -> 
            Navigation.findNavController(v).navigate(R.id.nav_ocr));

        binding.btnEntradaRegistrar.setOnClickListener(v -> registrarEntrada());

        viewModel.getEntradaState().observe(getViewLifecycleOwner(), state -> {
            if (state.isSuccess()) {
                showSnackbar("Entrada registrada!");
                binding.etEntradaPlaca.setText("");
                binding.cbLavagem.setChecked(false);
                viewModel.resetState();
            } else if (state.isError()) {
                showSnackbar(state.getMessage());
                viewModel.resetState();
            }
        });

        return binding.getRoot();
    }

    private void atualizarTextosLavagem() {
        binding.rbDucha.setText("Ducha (R$ " + String.format("%.2f", PrecosServicos.getPrecoDucha()) + ")");
        binding.rbSimples.setText("Simples (R$ " + String.format("%.2f", PrecosServicos.getPrecoSimples()) + ")");
        binding.rbCompleta.setText("Completa (R$ " + String.format("%.2f", PrecosServicos.getPrecoCompleta()) + ")");
    }

    private void registrarEntrada() {
        String placa = binding.etEntradaPlaca.getText().toString().trim().toUpperCase();
        if (!MascaraPlaca.isValida(placa)) {
            showSnackbar("Placa incompleta ou inválida!");
            return;
        }

        boolean temLavagem = binding.cbLavagem.isChecked();
        String tipoLavagem = "";
        double valorLavagem = 0.0;

        if (temLavagem) {
            int selectedId = binding.rgTipoLavagem.getCheckedRadioButtonId();
            if (selectedId == R.id.rb_ducha) {
                tipoLavagem = "Ducha";
                valorLavagem = PrecosServicos.getPrecoDucha();
            } else if (selectedId == R.id.rb_simples) {
                tipoLavagem = "Simples";
                valorLavagem = PrecosServicos.getPrecoSimples();
            } else if (selectedId == R.id.rb_completa) {
                tipoLavagem = "Completa";
                valorLavagem = PrecosServicos.getPrecoCompleta();
            }
        }

        viewModel.registrarEntrada(placa, temLavagem, tipoLavagem, valorLavagem);
    }

    private void showSnackbar(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
