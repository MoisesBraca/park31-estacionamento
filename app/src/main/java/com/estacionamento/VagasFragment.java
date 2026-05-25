package com.estacionamento;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.estacionamento.R;
import com.estacionamento.databinding.FragmentVagasBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.card.MaterialCardView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class VagasFragment extends Fragment {

    private FragmentVagasBinding binding;
    private EstacionamentoRepository repository;
    private List<Vaga> ultimasVagas;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentVagasBinding.inflate(inflater, container, false);
        repository = EstacionamentoRepository.getInstance(requireActivity().getApplication());

        repository.getAllVagas().observe(getViewLifecycleOwner(), vagas -> {
            ultimasVagas = vagas;
            atualizarGrid(vagas);
        });

        return binding.getRoot();
    }

    private void atualizarGrid(List<Vaga> vagas) {
        if (vagas == null || binding == null) return;

        int livres = 0;
        for (Vaga v : vagas) { if (v != null && v.isLivre()) livres++; }

        binding.vagasResumo.setText(livres + "/" + vagas.size() + " livres");

        binding.gridCarros.removeAllViews();
        binding.gridMotos.removeAllViews();

        boolean temCarro = false, temMoto = false;
        for (Vaga v : vagas) {
            if (v == null) continue;
            if ("CARRO".equals(v.getTipo())) temCarro = true;
            if ("MOTO".equals(v.getTipo())) temMoto = true;
        }

        binding.vagasSectionCarro.setVisibility(temCarro ? View.VISIBLE : View.GONE);
        binding.gridCarros.setVisibility(temCarro ? View.VISIBLE : View.GONE);
        binding.vagasSectionMoto.setVisibility(temMoto ? View.VISIBLE : View.GONE);
        binding.gridMotos.setVisibility(temMoto ? View.VISIBLE : View.GONE);

        for (Vaga v : vagas) {
            if (v == null) continue;
            try {
                View card = criarCardVaga(v);
                if ("CARRO".equals(v.getTipo())) {
                    binding.gridCarros.addView(card);
                } else {
                    binding.gridMotos.addView(card);
                }
            } catch (Exception ignored) {}
        }
    }

    private View criarCardVaga(Vaga vaga) {
        MaterialCardView card = new MaterialCardView(requireContext());
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.setMargins(6, 6, 6, 6);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
        card.setLayoutParams(params);
        card.setRadius(16f);
        card.setCardElevation(0f);
        card.setClickable(true);
        card.setFocusable(true);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.util.TypedValue tv = new android.util.TypedValue();
            requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            if (tv.resourceId != 0) {
                card.setForeground(requireContext().getDrawable(tv.resourceId));
            }
        }

        TextView tv = new TextView(requireContext());
        tv.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (int) (90 * getResources().getDisplayMetrics().density)));
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setText(vaga.getNumero());
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(18);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);

        int bgColor;
        switch (vaga.getStatus()) {
            case "OCUPADA": bgColor = 0xFFF44336; break;
            case "LAVAGEM": bgColor = 0xFFFFC107; tv.setTextColor(0xFF000000); break;
            default: bgColor = 0xFF4CAF50; break;
        }
        card.setCardBackgroundColor(bgColor);
        card.addView(tv);

        if (vaga.isOcupada()) {
            card.setOnLongClickListener(v -> {
                if (binding == null) return false;
                new Thread(() -> {
                    try {
                        Veiculo veiculo = repository.getVeiculoByVagaIdSync(vaga.getId());
                        if (isAdded()) {
                            if (veiculo != null) {
                                requireActivity().runOnUiThread(() -> mostrarDialogVaga(vaga, veiculo));
                            } else {
                                // Vaga fantasma detectada
                                requireActivity().runOnUiThread(() -> 
                                    new MaterialAlertDialogBuilder(requireContext())
                                        .setTitle("Vaga Presa")
                                        .setMessage("Esta vaga consta como ocupada mas não há veículo vinculado. Deseja liberar?")
                                        .setPositiveButton("Liberar", (d, w) -> {
                                            new Thread(() -> repository.liberarVagaManual(vaga.getId())).start();
                                        })
                                        .setNegativeButton("Cancelar", null)
                                        .show()
                                );
                            }
                        }
                    } catch (Exception ignored) {}
                }).start();
                return true;
            });
        }

        return card;
    }

    private void mostrarDialogVaga(Vaga vaga, Veiculo veiculo) {
        if (!isAdded() || binding == null) return;
        SimpleDateFormat df = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
        String msg = "Placa: " + veiculo.getPlaca() + "\n"
                   + "Entrada: " + df.format(new Date(veiculo.getHoraEntrada())) + "\n"
                   + "Vaga: " + vaga.getNumero() + "\n"
                   + "Andar: " + vaga.getAndar();

        if (veiculo.isTemLavagem()) {
            msg += "\nLavagem: " + veiculo.getTipoLavagem()
                 + (veiculo.isLavagemConcluida() ? " (✓)" : " (pendente)");
        }

        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Vaga " + vaga.getNumero())
            .setMessage(msg)
            .setNeutralButton("Fechar", null)
            .setPositiveButton("Registrar Saída", (d, w) -> {
                if (getView() != null) {
                    Navigation.findNavController(getView()).navigate(R.id.nav_saida);
                }
            })
            .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
