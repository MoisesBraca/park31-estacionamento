package com.estacionamento;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.estacionamento.R;
import com.estacionamento.databinding.FragmentSaidaBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

public class SaidaFragment extends Fragment {

    private FragmentSaidaBinding binding;
    private SaidaViewModel viewModel;
    private Veiculo veiculoAtual;
    private double tarifaAtual;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
    private final android.os.Handler pollingHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pollingRunnable;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSaidaBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(SaidaViewModel.class);

        binding.etSaidaPlaca.addTextChangedListener(new MascaraPlaca(binding.etSaidaPlaca));

        setupRows();
        checkNfcSupport();

        viewModel.getBuscaState().observe(getViewLifecycleOwner(), state -> {
            if (state.isSuccess()) {
                Veiculo veiculo = state.getData();
                if (veiculo == null) return;
                veiculoAtual = veiculo;
                double valorEstacionamento = CalculadoraTarifa.calcularTarifa(veiculo.getTempoEstacionado());
                tarifaAtual = valorEstacionamento + veiculo.getValorLavagem();

                binding.rowPlaca.tvValue.setText(veiculo.getPlaca());
                binding.rowEntrada.tvValue.setText(dateFormat.format(new Date(veiculo.getHoraEntrada())));
                long minutos = veiculo.getTempoEstacionado() / (1000 * 60);

                String tempoTexto = minutos + " min";
                if (veiculo.isTemLavagem()) {
                    tempoTexto += " + Lavagem (" + veiculo.getTipoLavagem() + ")";
                }
                binding.rowTempo.tvValue.setText(tempoTexto);

                String avariaPath = veiculo.getFotoAvariaPath();
                if (avariaPath != null && !avariaPath.isEmpty()) {
                    binding.cardSaidaAvaria.setVisibility(View.VISIBLE);
                    binding.ivSaidaAvaria.setImageURI(android.net.Uri.fromFile(new java.io.File(avariaPath)));
                } else {
                    binding.cardSaidaAvaria.setVisibility(View.GONE);
                }

                // Check monthly subscriber status asynchronously
                new Thread(() -> {
                    try {
                        EstacionamentoRepository repo = EstacionamentoRepository.getInstance(requireActivity().getApplication());
                        Mensalista m = repo.obterMensalistaSync(veiculo.getPlaca());
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (binding == null) return;
                                boolean isMensalistaAtivo = false;
                                if (m != null) {
                                    long hoje = System.currentTimeMillis();
                                    if ("ATIVO".equalsIgnoreCase(m.getStatus()) && m.getVencimento() >= hoje) {
                                        isMensalistaAtivo = true;
                                    }
                                }

                                if (isMensalistaAtivo) {
                                    tarifaAtual = veiculo.getValorLavagem(); // zero the parking fee
                                    binding.tvMensalistaSaidaBadge.setVisibility(View.VISIBLE);
                                    binding.tvMensalistaSaidaBadge.setText("MENSALISTA ATIVO: " + m.getNomeCliente().toUpperCase() + " (SAÍDA TARIFA ZERADA)");
                                    binding.tvMensalistaSaidaBadge.setBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark));
                                    
                                    binding.rowValor.tvValue.setText("R$ " + String.format("%.2f", tarifaAtual) + " (MENSALISTA)");
                                    binding.rowValor.tvValue.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark));
                                    binding.etSaidaValor.setText(String.format("%.2f", tarifaAtual));
                                    
                                    if (tarifaAtual == 0) {
                                        binding.rgPagamento.setVisibility(View.GONE);
                                        binding.etSaidaValor.setEnabled(false);
                                    } else {
                                        binding.rgPagamento.setVisibility(View.VISIBLE);
                                        binding.etSaidaValor.setEnabled(true);
                                    }
                                } else {
                                    binding.tvMensalistaSaidaBadge.setVisibility(View.GONE);
                                    binding.rgPagamento.setVisibility(View.VISIBLE);
                                    binding.etSaidaValor.setEnabled(true);
                                    
                                    binding.rowValor.tvValue.setText("R$ " + String.format("%.2f", tarifaAtual));
                                    binding.rowValor.tvValue.setTextColor(getResources().getColor(R.color.danger));
                                    binding.etSaidaValor.setText(String.format("%.2f", tarifaAtual));
                                }
                                binding.cardSaidaDetalhes.setVisibility(View.VISIBLE);
                            });
                        }
                    } catch (Exception ignored) {}
                }).start();

            } else if (state.isError()) {
                mostrarMensagem("Erro", state.getMessage());
                binding.cardSaidaDetalhes.setVisibility(View.GONE);
                binding.cardSaidaAvaria.setVisibility(View.GONE);
                veiculoAtual = null;
            }
        });

        viewModel.getSaidaState().observe(getViewLifecycleOwner(), state -> {
            if (state.isSuccess()) {
                Transacao t = state.getData();
                if (t == null) return;

                double troco = tarifaAtual > 0
                    ? Double.parseDouble(binding.etSaidaValor.getText().toString().replace(",", ".")) - tarifaAtual
                    : 0;
                String forma = binding.rgPagamento.getCheckedRadioButtonId() != -1
                    ? ((RadioButton) binding.rgPagamento.findViewById(binding.rgPagamento.getCheckedRadioButtonId())).getText().toString()
                    : "Dinheiro";

                String msg = String.format("Saída realizada!\nTroco: R$ %.2f\nPagamento: %s", Math.max(0, troco), forma);

                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Sucesso")
                        .setMessage(msg)
                        .setPositiveButton("Comprovante", (d, w) -> mostrarDialogoComprovante(t, t.getPlaca(), troco, forma))
                        .setNeutralButton("OK", null)
                        .show();

                // FIX: Clear UI after success
                binding.etSaidaPlaca.setText("");
                binding.cardSaidaDetalhes.setVisibility(View.GONE);
                binding.cardSaidaAvaria.setVisibility(View.GONE);
                veiculoAtual = null;
                tarifaAtual = 0;
                viewModel.resetSaida();
                viewModel.resetBusca();
            } else if (state.isError()) {
                mostrarMensagem("Erro", state.getMessage());
                viewModel.resetSaida();
            }
        });

        binding.btnSaidaBuscar.setOnClickListener(v -> buscarVeiculo());
        binding.btnSaidaConfirmar.setOnClickListener(v -> confirmarSaida());

        return binding.getRoot();
    }

    private void checkNfcSupport() {
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(requireContext());
        if (nfcAdapter != null && nfcAdapter.isEnabled()) {
            binding.rbNfc.setVisibility(View.VISIBLE);
        } else {
            binding.rbNfc.setVisibility(View.GONE);
        }
    }

    private void setupRows() {
        binding.rowPlaca.tvLabel.setText("Placa");
        binding.rowEntrada.tvLabel.setText("Entrada");
        binding.rowTempo.tvLabel.setText("Tempo");
        binding.rowValor.tvLabel.setText("Total a Pagar");
        binding.rowValor.tvValue.setTextColor(getResources().getColor(R.color.danger));
    }

    private void buscarVeiculo() {
        String placa = binding.etSaidaPlaca.getText().toString().trim().toUpperCase();
        if (!MascaraPlaca.isValida(placa)) {
            mostrarMensagem("Aviso", "Informe a placa completa!");
            return;
        }
        veiculoAtual = null;
        viewModel.buscarVeiculo(placa);
    }

    private void confirmarSaida() {
        if (veiculoAtual == null) return;

        if (binding.tvMensalistaSaidaBadge.getVisibility() == View.VISIBLE) {
            double valorPago = 0.0;
            String valorStr = binding.etSaidaValor.getText().toString().trim().replace(",", ".");
            if (!valorStr.isEmpty()) {
                try {
                    valorPago = Double.parseDouble(valorStr);
                } catch (Exception ignored) {}
            }
            processarSaidaFinal(valorPago, "CORTESIA MENSALISTA");
            return;
        }

        String valorStr = binding.etSaidaValor.getText().toString().trim().replace(",", ".");
        if (valorStr.isEmpty()) {
            mostrarMensagem("Aviso", "Informe o valor recebido");
            return;
        }

        double valorPago;
        try {
            valorPago = Double.parseDouble(valorStr);
        } catch (NumberFormatException e) {
            mostrarMensagem("Erro", "Valor recebido inválido");
            return;
        }

        if (valorPago < tarifaAtual) {
            mostrarMensagem("Aviso", "Valor insuficiente");
            return;
        }

        int selectedId = binding.rgPagamento.getCheckedRadioButtonId();
        RadioButton rb = binding.rgPagamento.findViewById(selectedId);
        String formaPagamento = rb != null ? rb.getText().toString() : "Dinheiro";

        if (formaPagamento.contains("Pix")) {
            requisitarPixDinamico(valorPago, veiculoAtual.getPlaca(), formaPagamento);
        } else if (formaPagamento.contains("NFC")) {
            mostrarSimulacaoNFC(valorPago, formaPagamento);
        } else {
            processarSaidaFinal(valorPago, formaPagamento);
        }
    }

    private void requisitarPixDinamico(double valor, String placa, String forma) {
        final com.google.android.material.dialog.MaterialAlertDialogBuilder loadingDialogBuilder = 
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("PIX Dinâmico")
                .setMessage("Gerando cobrança na API central...")
                .setCancelable(false);
        final androidx.appcompat.app.AlertDialog loadingDialog = loadingDialogBuilder.show();

        new Thread(() -> {
            try {
                String ip = LicencaHelper.getServerIp(requireContext());
                String urlStr;
                if (ip.contains("://")) {
                    urlStr = ip + "/api/pix/create";
                } else if (ip.contains(".") && !ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                    urlStr = "https://" + ip + "/api/pix/create";
                } else {
                    urlStr = "http://" + ip + ":8080/api/pix/create";
                }
                java.net.URL url = new java.net.URL(urlStr);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);

                String json = "{\"placa\":\"" + placa + "\",\"valor\":\"" + valor + "\"}";
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    org.json.JSONObject resp = new org.json.JSONObject(sb.toString());
                    String txid = resp.getString("txid");
                    String payload = resp.getString("payload");

                    requireActivity().runOnUiThread(() -> {
                        loadingDialog.dismiss();
                        exibirDialogoPixDinamico(txid, payload, valor, forma);
                    });
                } else {
                    requireActivity().runOnUiThread(() -> {
                        loadingDialog.dismiss();
                        mostrarMensagem("Erro", "Erro HTTP ao criar Pix: " + code);
                    });
                }
                conn.disconnect();
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    mostrarMensagem("Erro de Conexão", "Não foi possível conectar ao servidor central: " + e.getMessage());
                });
            }
        }).start();
    }

    private void exibirDialogoPixDinamico(String txid, String payload, double valor, String forma) {
        try {
            android.graphics.Bitmap qrBitmap = PixQrCode.gerarQrCode(payload, 512);

            ImageView iv = new ImageView(requireContext());
            iv.setImageBitmap(qrBitmap);
            iv.setPadding(32, 32, 32, 32);
            iv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            iv.setAdjustViewBounds(true);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);

            final MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("PIX Dinâmico — Central de Vendas")
                    .setMessage("Valor: R$ " + String.format(Locale.US, "%.2f", tarifaAtual) + "\n\nStatus: Aguardando pagamento na API...")
                    .setView(iv)
                    .setNegativeButton("Cancelar", (dialog, which) -> {
                        pararPollingPix();
                    });
            
            final androidx.appcompat.app.AlertDialog pixDialog = dialogBuilder.show();

            iniciarPollingPix(txid, valor, forma, pixDialog);
        } catch (Exception e) {
            mostrarMensagem("Erro", "Erro ao gerar QR Code: " + e.getMessage());
        }
    }

    private void iniciarPollingPix(String txid, double valor, String forma, androidx.appcompat.app.AlertDialog dialog) {
        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                new Thread(() -> {
                    try {
                        String ip = LicencaHelper.getServerIp(requireContext());
                        String urlStr;
                        if (ip.contains("://")) {
                            urlStr = ip + "/api/pix/status";
                        } else if (ip.contains(".") && !ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                            urlStr = "https://" + ip + "/api/pix/status";
                        } else {
                            urlStr = "http://" + ip + ":8080/api/pix/status";
                        }
                        java.net.URL url = new java.net.URL(urlStr);
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                        conn.setDoOutput(true);
                        conn.setConnectTimeout(2000);
                        conn.setReadTimeout(2000);

                        String json = "{\"txid\":\"" + txid + "\"}";
                        try (java.io.OutputStream os = conn.getOutputStream()) {
                            byte[] input = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                            os.write(input, 0, input.length);
                        }

                        int code = conn.getResponseCode();
                        if (code == 200) {
                            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = br.readLine()) != null) {
                                sb.append(line);
                            }
                            org.json.JSONObject resp = new org.json.JSONObject(sb.toString());
                            String status = resp.getString("status");

                            if ("APROVADO".equals(status)) {
                                requireActivity().runOnUiThread(() -> {
                                    dialog.dismiss();
                                    pararPollingPix();
                                    processarSaidaFinal(valor, forma);
                                });
                            } else {
                                requireActivity().runOnUiThread(() -> {
                                    pollingHandler.postDelayed(pollingRunnable, 2000);
                                });
                            }
                        } else {
                            requireActivity().runOnUiThread(() -> {
                                pollingHandler.postDelayed(pollingRunnable, 3000);
                            });
                        }
                        conn.disconnect();
                    } catch (Exception e) {
                        requireActivity().runOnUiThread(() -> {
                            pollingHandler.postDelayed(pollingRunnable, 4000);
                        });
                    }
                }).start();
            }
        };
        pollingHandler.post(pollingRunnable);
    }

    private void pararPollingPix() {
        if (pollingRunnable != null) {
            pollingHandler.removeCallbacks(pollingRunnable);
            pollingRunnable = null;
        }
    }

    private void mostrarSimulacaoNFC(double valor, String forma) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Pagamento por Aproximação")
                .setMessage("Aproxime o cartão ou celular do cliente para receber R$ " + String.format("%.2f", valor))
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Simular Leitura", (dialog, which) -> processarSaidaFinal(valor, forma))
                .show();
    }

    private void processarSaidaFinal(double valorPago, String formaPagamento) {
        viewModel.registrarSaida(veiculoAtual.getPlaca(), valorPago, formaPagamento);
    }

    private void mostrarDialogoComprovante(Transacao t, String placa, double troco, String forma) {
        String operador = SessaoManager.getInstance().getPerfil().name();
        String comprovante = ComprovanteBuilder.buildText(placa, t.getHoraEntrada(),
                t.getHoraSaida(), t.getValorPago(), tarifaAtual, troco, forma, operador);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Comprovante")
                .setMessage(comprovante)
                .setPositiveButton("Compartilhar", (d, w) -> {
                    Uri pdfUri = ComprovanteBuilder.gerarPdf(requireContext(), placa,
                            t.getHoraEntrada(), t.getHoraSaida(),
                            t.getValorPago(), tarifaAtual, troco, forma, operador);
                    if (pdfUri != null) {
                        ComprovanteBuilder.compartilharPdf(requireContext(), pdfUri);
                    }
                })
                .setNeutralButton("Imprimir", (d, w) -> mostrarDispositivosBluetooth(comprovante))
                .setNegativeButton("Fechar", null)
                .show();
    }

    private void mostrarDispositivosBluetooth(String comprovante) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) return;

        @SuppressWarnings("MissingPermission")
        Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();
        if (pairedDevices.isEmpty()) {
            mostrarMensagem("Bluetooth", "Nenhuma impressora pareada");
            return;
        }

        String[] names = new String[pairedDevices.size()];
        String[] addresses = new String[pairedDevices.size()];
        int i = 0;
        for (BluetoothDevice d : pairedDevices) {
            names[i] = d.getName();
            addresses[i] = d.getAddress();
            i++;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Selecione a Impressora")
                .setItems(names, (dialog, which) -> {
                    ComprovanteBuilder.imprimirBluetooth(requireContext(), addresses[which], comprovante);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void mostrarMensagem(String title, String msg) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        pararPollingPix();
        binding = null;
    }
}
