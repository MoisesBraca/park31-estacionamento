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
                binding.rowValor.tvValue.setText("R$ " + String.format("%.2f", tarifaAtual));
                binding.etSaidaValor.setText(String.format("%.2f", tarifaAtual));
                binding.cardSaidaDetalhes.setVisibility(View.VISIBLE);
            } else if (state.isError()) {
                mostrarMensagem("Erro", state.getMessage());
                binding.cardSaidaDetalhes.setVisibility(View.GONE);
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
            mostrarQrCodePix(valorPago, formaPagamento);
        } else if (formaPagamento.contains("NFC")) {
            mostrarSimulacaoNFC(valorPago, formaPagamento);
        } else {
            processarSaidaFinal(valorPago, formaPagamento);
        }
    }

    private void mostrarQrCodePix(double valor, String forma) {
        try {
            String pixKey = Preferences.getPixKey();
            Bitmap qrBitmap = PixQrCode.gerarQrCode(pixKey, valor, 512);

            ImageView iv = new ImageView(requireContext());
            iv.setImageBitmap(qrBitmap);
            iv.setPadding(32, 32, 32, 32);
            iv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            iv.setAdjustViewBounds(true);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("PIX — QR Code")
                    .setMessage("Valor: R$ " + String.format("%.2f", tarifaAtual))
                    .setView(iv)
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Pagamento Conf.", (d, w) -> processarSaidaFinal(valor, forma))
                    .show();
        } catch (Exception e) {
            mostrarMensagem("Erro", "Erro ao gerar QR Code");
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
        binding = null;
    }
}
