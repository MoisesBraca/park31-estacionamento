package com.estacionamento;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.estacionamento.R;
import com.estacionamento.databinding.FragmentRelatorioBinding;
import com.estacionamento.databinding.ItemTransacaoBinding;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RelatorioFragment extends Fragment {

    private FragmentRelatorioBinding binding;
    private MainViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentRelatorioBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        binding.rvRelatorioDia.setLayoutManager(new LinearLayoutManager(getContext()));

        setupObservers();

        return binding.getRoot();
    }

    private void setupObservers() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long inicioDia = cal.getTimeInMillis();
        
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        long fimDia = cal.getTimeInMillis();

        viewModel.getTransacoesByPeriodo(inicioDia, fimDia).observe(getViewLifecycleOwner(), transacoes -> {
            binding.rvRelatorioDia.setAdapter(new TransacaoAdapter(transacoes));
            
            double total = 0;
            for (Transacao t : transacoes) total += t.getValorPago();
            
            binding.tvResumoDia.setText(String.format(Locale.getDefault(), 
                "Faturamento Hoje: R$ %.2f | %d veículos", total, transacoes.size()));
        });
    }

    private static class TransacaoAdapter extends RecyclerView.Adapter<TransacaoAdapter.ViewHolder> {
        private final List<Transacao> transacoes;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        TransacaoAdapter(List<Transacao> transacoes) { this.transacoes = transacoes; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemTransacaoBinding b = ItemTransacaoBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(b);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Transacao t = transacoes.get(position);
            holder.binding.itemTransacaoPlaca.setText(t.getPlaca());
            holder.binding.itemTransacaoValor.setText(String.format("R$ %.2f", t.getValorPago()));
            holder.binding.itemTransacaoEntrada.setText("Ent: " + dateFormat.format(new Date(t.getHoraEntrada())));
            holder.binding.itemTransacaoSaida.setText("Sai: " + dateFormat.format(new Date(t.getHoraSaida())));
        }

        @Override public int getItemCount() { return transacoes.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ItemTransacaoBinding binding;
            ViewHolder(ItemTransacaoBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
