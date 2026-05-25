package com.estacionamento;

import android.os.Bundle;
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
import com.estacionamento.databinding.FragmentHistoricoBinding;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoricoFragment extends Fragment {

    private FragmentHistoricoBinding binding;
    private MainViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHistoricoBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        binding.rvHistorico.setLayoutManager(new LinearLayoutManager(getContext()));

        viewModel.getAllTransacoes().observe(getViewLifecycleOwner(), transacoes -> {
            if (transacoes.isEmpty()) {
                binding.tvHistoricoVazia.setVisibility(View.VISIBLE);
                binding.rvHistorico.setVisibility(View.GONE);
            } else {
                binding.tvHistoricoVazia.setVisibility(View.GONE);
                binding.rvHistorico.setVisibility(View.VISIBLE);
                binding.rvHistorico.setAdapter(new TransacaoAdapter(transacoes));
            }
        });

        return binding.getRoot();
    }

    private static class TransacaoAdapter extends RecyclerView.Adapter<TransacaoAdapter.ViewHolder> {
        private final List<Transacao> transacoes;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

        TransacaoAdapter(List<Transacao> transacoes) { this.transacoes = transacoes; }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transacao, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Transacao t = transacoes.get(position);
            holder.tvPlaca.setText(t.getPlaca());
            holder.tvPagamento.setText(t.getFormaPagamento());
            holder.tvValor.setText("R$ " + String.format("%.2f", t.getValorPago()));
            holder.tvEntrada.setText("Ent: " + dateFormat.format(new Date(t.getHoraEntrada())));
            holder.tvSaida.setText("Sai: " + dateFormat.format(new Date(t.getHoraSaida())));
        }

        @Override
        public int getItemCount() { return transacoes.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvPlaca, tvPagamento, tvValor, tvEntrada, tvSaida;
            ViewHolder(View itemView) {
                super(itemView);
                tvPlaca = itemView.findViewById(R.id.item_transacao_placa);
                tvPagamento = itemView.findViewById(R.id.item_transacao_pagamento);
                tvValor = itemView.findViewById(R.id.item_transacao_valor);
                tvEntrada = itemView.findViewById(R.id.item_transacao_entrada);
                tvSaida = itemView.findViewById(R.id.item_transacao_saida);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
