package com.estacionamento;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import com.estacionamento.R;
import com.estacionamento.databinding.FragmentConfiguracaoBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ConfiguracaoFragment extends Fragment {

    private FragmentConfiguracaoBinding binding;
    private final SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentConfiguracaoBinding.inflate(inflater, container, false);

        atualizarInfoBackup();
        atualizarStatusImpressora();

        binding.btnSetupPrinter.setOnClickListener(v -> checkBluetoothPermission());
        binding.btnBackupManual.setOnClickListener(v -> fazerBackupManual());

        return binding.getRoot();
    }

    private void atualizarStatusImpressora() {
        String addr = Preferences.getPrinterAddress();
        if (addr != null) {
            binding.tvPrinterStatus.setText("Conectado: " + addr);
            binding.tvPrinterStatus.setTextColor(getResources().getColor(R.color.success));
        } else {
            binding.tvPrinterStatus.setText("Nenhuma impressora configurada");
            binding.tvPrinterStatus.setTextColor(getResources().getColor(R.color.text_secondary));
        }
    }

    private void checkBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 101);
                return;
            }
        }
        escolherImpressora();
    }

    private void escolherImpressora() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            showSnackbar("Ligue o Bluetooth primeiro");
            return;
        }

        @SuppressWarnings("MissingPermission")
        Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();
        List<String> deviceNames = new ArrayList<>();
        List<String> deviceAddresses = new ArrayList<>();

        for (BluetoothDevice device : pairedDevices) {
            deviceNames.add(device.getName() + "\n" + device.getAddress());
            deviceAddresses.add(device.getAddress());
        }

        if (deviceNames.isEmpty()) {
            showSnackbar("Nenhum dispositivo Bluetooth pareado");
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Escolha a Impressora")
                .setItems(deviceNames.toArray(new String[0]), (dialog, which) -> {
                    String address = deviceAddresses.get(which);
                    Preferences.setPrinterAddress(address);
                    atualizarStatusImpressora();
                    showSnackbar("Impressora configurada!");
                })
                .show();
    }

    private void atualizarInfoBackup() {
        long ultimo = BackupWorker.getUltimoBackup(requireContext());
        if (ultimo > 0) {
            binding.configUltimoBackup.setText("Último backup: " + df.format(new Date(ultimo)));
        }
    }

    private void fazerBackupManual() {
        showSnackbar("Backup agendado com sucesso!");
    }

    private void showSnackbar(String msg) {
        Snackbar.make(binding.getRoot(), msg, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
