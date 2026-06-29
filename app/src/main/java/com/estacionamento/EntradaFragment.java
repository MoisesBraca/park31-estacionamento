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

    private final androidx.activity.result.ActivityResultLauncher<String> requestPermissionLauncher =
        registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                tirarFotoAvaria();
            } else {
                showSnackbar("Permissão de câmera necessária para tirar fotos de vistoria!");
            }
        });

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getParentFragmentManager().setFragmentResultListener("ocr_result", this, (requestKey, result) -> {
            String placa = result.getString("placa_detectada");
            if (placa != null && binding != null) {
                binding.etEntradaPlaca.setText(placa);
            }
        });
    }

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

        binding.btnFotoAvaria.setOnClickListener(v -> {
            if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                tirarFotoAvaria();
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.CAMERA);
            }
        });

        binding.btnEntradaRegistrar.setOnClickListener(v -> registrarEntrada());

        viewModel.getEntradaState().observe(getViewLifecycleOwner(), state -> {
            if (state.isSuccess()) {
                String placaRegistrada = binding.etEntradaPlaca.getText().toString().trim().toUpperCase();
                long ts = System.currentTimeMillis();
                
                showSnackbar("Entrada registrada!");
                binding.etEntradaPlaca.setText("");
                binding.cbLavagem.setChecked(false);
                binding.cardPreviewAvaria.setVisibility(View.GONE);
                fotoCaminhoLocal = null;
                viewModel.resetState();

                if (!placaRegistrada.isEmpty()) {
                    exibirDialogoTicketDigital(placaRegistrada, ts);
                }
            } else if (state.isError()) {
                showSnackbar(state.getMessage());
                viewModel.resetState();
            }
        });

        return binding.getRoot();
    }

    private void exibirDialogoTicketDigital(String placa, long entradaTimestamp) {
        try {
            String ip = LicencaHelper.getServerIp(requireContext());
            String urlStr;
            if (ip.contains("://")) {
                urlStr = ip + "/pagar?placa=" + placa + "&entrada=" + entradaTimestamp;
            } else if (ip.contains(".") && !ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                urlStr = "https://" + ip + "/pagar?placa=" + placa + "&entrada=" + entradaTimestamp;
            } else {
                urlStr = "http://" + ip + ":8080/pagar?placa=" + placa + "&entrada=" + entradaTimestamp;
            }

            android.graphics.Bitmap qrBitmap = PixQrCode.gerarQrCode(urlStr, 512);

            android.widget.ImageView iv = new android.widget.ImageView(requireContext());
            iv.setImageBitmap(qrBitmap);
            iv.setPadding(32, 32, 32, 32);
            iv.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            iv.setAdjustViewBounds(true);
            iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);

            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Ticket Digital — Autoatendimento")
                    .setMessage("Peça para o cliente escanear o QR Code para acompanhar o tempo e pagar pelo celular:\n\nPlaca: " + placa)
                    .setView(iv)
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Exception e) {
            showSnackbar("Erro ao gerar QR Code: " + e.getMessage());
        }
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

            // Grant write and read permissions explicitly to the Camera packages to avoid Permission Denial crash
            android.content.Intent intent = new android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            java.util.List<android.content.pm.ResolveInfo> resInfoList = requireContext().getPackageManager()
                .queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            for (android.content.pm.ResolveInfo resolveInfo : resInfoList) {
                String packageName = resolveInfo.activityInfo.packageName;
                requireContext().grantUriPermission(packageName, fotoUri, 
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION | android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }

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
