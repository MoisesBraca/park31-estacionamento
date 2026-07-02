package com.estacionamento;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.estacionamento.R;
import com.estacionamento.databinding.FragmentListarBinding;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ListarFragment extends Fragment {

    private FragmentListarBinding binding;
    private MainViewModel viewModel;
    private VeiculoAdapter adapter;
    private List<Veiculo> listaOriginal = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentListarBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        binding.rvListarVeiculos.setLayoutManager(new LinearLayoutManager(getContext()));

        setupSearch();
        setupObservers();

        return binding.getRoot();
    }

    private void setupObservers() {
        viewModel.getVeiculosEstacionados().observe(getViewLifecycleOwner(), veiculos -> {
            listaOriginal = veiculos;
            updateList(veiculos);
        });
    }

    private void setupSearch() {
        binding.etListarBusca.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filtrar(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void filtrar(String texto) {
        if (texto.isEmpty()) {
            updateList(listaOriginal);
        } else {
            String busca = texto.toUpperCase();
            List<Veiculo> filtrada = new ArrayList<>();
            for (Veiculo v : listaOriginal) {
                if (v.getPlaca().contains(busca)) {
                    filtrada.add(v);
                }
            }
            updateList(filtrada);
        }
    }

    private void updateList(List<Veiculo> veiculos) {
        if (veiculos.isEmpty()) {
            binding.tvListarVazia.setVisibility(View.VISIBLE);
            binding.rvListarVeiculos.setVisibility(View.GONE);
        } else {
            binding.tvListarVazia.setVisibility(View.GONE);
            binding.rvListarVeiculos.setVisibility(View.VISIBLE);
            if (adapter == null) {
                adapter = new VeiculoAdapter(veiculos, viewModel);
                binding.rvListarVeiculos.setAdapter(adapter);
            } else {
                adapter.atualizar(veiculos);
            }
        }
    }

    private static class VeiculoAdapter extends RecyclerView.Adapter<VeiculoAdapter.ViewHolder> {
        private List<Veiculo> veiculos;
        private final MainViewModel viewModel;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        VeiculoAdapter(List<Veiculo> veiculos, MainViewModel viewModel) {
            this.veiculos = veiculos;
            this.viewModel = viewModel;
        }

        void atualizar(List<Veiculo> novos) {
            this.veiculos = novos;
            notifyDataSetChanged();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_veiculo, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Veiculo v = veiculos.get(position);
            holder.tvPlaca.setText(v.getPlaca());
            holder.tvEntrada.setText("Entrada: " + dateFormat.format(new Date(v.getHoraEntrada())));

            long minutos = v.getTempoEstacionado() / (1000 * 60);
            holder.tvTempo.setText(minutos + " min");

            if (v.isTemLavagem()) {
                holder.tvServico.setVisibility(View.VISIBLE);
                holder.tvServico.setText("Lavagem: " + v.getTipoLavagem());

                if (v.isLavagemConcluida()) {
                    holder.tvLavagemOk.setVisibility(View.VISIBLE);
                    holder.btnConcluir.setVisibility(View.GONE);
                } else {
                    holder.tvLavagemOk.setVisibility(View.GONE);
                    holder.btnConcluir.setVisibility(View.VISIBLE);
                    holder.btnConcluir.setOnClickListener(view -> viewModel.marcarLavagemConcluida(v.getPlaca()));
                }
            } else {
                holder.tvServico.setVisibility(View.GONE);
                holder.tvLavagemOk.setVisibility(View.GONE);
                holder.btnConcluir.setVisibility(View.GONE);
            }
        }

        @Override public int getItemCount() { return veiculos.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvPlaca, tvEntrada, tvTempo, tvServico, tvLavagemOk;
            View btnConcluir;
            ViewHolder(View itemView) {
                super(itemView);
                tvPlaca = itemView.findViewById(R.id.item_veiculo_placa);
                tvEntrada = itemView.findViewById(R.id.item_veiculo_entrada);
                tvTempo = itemView.findViewById(R.id.item_veiculo_tempo);
                tvServico = itemView.findViewById(R.id.item_veiculo_servico);
                tvLavagemOk = itemView.findViewById(R.id.tv_lavagem_ok);
                btnConcluir = itemView.findViewById(R.id.btn_concluir_lavagem);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
