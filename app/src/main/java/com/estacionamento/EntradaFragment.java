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
    private android.net.Uri fotoUri;
    private String fotoCaminhoLocal = null;

    private final androidx.activity.result.ActivityResultLauncher<android.net.Uri> takePictureLauncher =
        registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.TakePicture(), success -> {
            if (success) {
                binding.cardPreviewAvaria.setVisibility(View.VISIBLE);
                binding.ivPreviewAvaria.setImageURI(fotoUri);
            } else {
                fotoCaminhoLocal = null;
                binding.cardPreviewAvaria.setVisibility(View.GONE);
            }
        });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentEntradaBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(EntradaViewModel.class);

        binding.etEntradaPlaca.addTextChangedListener(new MascaraPlaca(binding.etEntradaPlaca));
        binding.etEntradaPlaca.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                String placa = s.toString().trim().toUpperCase().replace("-", "");
                if (placa.length() == 7) {
                    verificarMensalista(placa);
                } else {
                    binding.cardMensalistaBadge.setVisibility(View.GONE);
                }
            }
        });

        binding.cbLavagem.setOnCheckedChangeListener((buttonView, isChecked) -> {
            binding.layoutServicos.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        atualizarTextosLavagem();
        
        binding.btnScan.setOnClickListener(v -> 
            Navigation.findNavController(v).navigate(R.id.nav_ocr));

        binding.btnFotoAvaria.setOnClickListener(v -> tirarFotoAvaria());

        binding.btnEntradaRegistrar.setOnClickListener(v -> registrarEntrada());

        viewModel.getEntradaState().observe(getViewLifecycleOwner(), state -> {
            if (state.isSuccess()) {
                showSnackbar("Entrada registrada!");
                binding.etEntradaPlaca.setText("");
                binding.cbLavagem.setChecked(false);
                binding.cardPreviewAvaria.setVisibility(View.GONE);
                fotoCaminhoLocal = null;
                viewModel.resetState();
            } else if (state.isError()) {
                showSnackbar(state.getMessage());
                viewModel.resetState();
            }
        });

        return binding.getRoot();
    }

    private void verificarMensalista(String placa) {
        new Thread(() -> {
            try {
                EstacionamentoRepository repo = EstacionamentoRepository.getInstance(requireActivity().getApplication());
                Mensalista m = repo.obterMensalistaSync(placa);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (binding == null) return;
                        if (m != null) {
                            binding.cardMensalistaBadge.setVisibility(View.VISIBLE);
                            long hoje = System.currentTimeMillis();
                            if ("ATIVO".equalsIgnoreCase(m.getStatus()) && m.getVencimento() >= hoje) {
                                binding.cardMensalistaBadge.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark));
                                binding.cardMensalistaBadge.setStrokeColor(androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.holo_green_light));
                                binding.tvMensalistaBadgeTexto.setText("MENSALISTA ATIVO: " + m.getNomeCliente().toUpperCase());
                                binding.tvMensalistaBadgeTexto.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.white));
                            } else {
                                binding.cardMensalistaBadge.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                                binding.cardMensalistaBadge.setStrokeColor(androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.holo_red_light));
                                String motivo = "SUSPENSO".equalsIgnoreCase(m.getStatus()) ? "SUSPENSO" : "VENCIDO";
                                binding.tvMensalistaBadgeTexto.setText("MENSALISTA INADIMPLENTE: " + m.getNomeCliente().toUpperCase() + " (" + motivo + ")");
                                binding.tvMensalistaBadgeTexto.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.white));
                            }
                        } else {
                            binding.cardMensalistaBadge.setVisibility(View.GONE);
                        }
                    });
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void tirarFotoAvaria() {
        try {
            java.io.File storageDir = requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);
            java.io.File imageFile = java.io.File.createTempFile(
                "avaria_" + System.currentTimeMillis() + "_",
                ".jpg",
                storageDir
            );
            fotoCaminhoLocal = imageFile.getAbsolutePath();
            fotoUri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "com.estacionamento.fileprovider",
                imageFile
            );
            takePictureLauncher.launch(fotoUri);
        } catch (Exception e) {
            showSnackbar("Erro ao abrir a câmera: " + e.getMessage());
        }
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

        viewModel.registrarEntrada(placa, temLavagem, tipoLavagem, valorLavagem, fotoCaminhoLocal);
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
