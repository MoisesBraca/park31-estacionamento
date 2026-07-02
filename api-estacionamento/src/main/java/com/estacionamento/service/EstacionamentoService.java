package com.estacionamento.service;

import com.estacionamento.model.Transacao;
import com.estacionamento.model.Veiculo;
import com.estacionamento.repository.TransacaoRepository;
import com.estacionamento.repository.VeiculoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class EstacionamentoService {

    private final VeiculoRepository veiculoRepository;
    private final TransacaoRepository transacaoRepository;
    private final CalculadoraTarifa calculadoraTarifa;

    public EstacionamentoService(VeiculoRepository veiculoRepository,
                                 TransacaoRepository transacaoRepository,
                                 CalculadoraTarifa calculadoraTarifa) {
        this.veiculoRepository = veiculoRepository;
        this.transacaoRepository = transacaoRepository;
        this.calculadoraTarifa = calculadoraTarifa;
    }

    @Transactional
    public Veiculo registrarEntrada(String placa) {
        if (placa == null || placa.trim().isEmpty()) {
            throw new IllegalArgumentException("Placa nao pode ser vazia");
        }
        String placaFormatada = placa.trim().toUpperCase();
        if (buscarVeiculoEstacionado(placaFormatada).isPresent()) {
            throw new IllegalStateException("Veiculo ja esta estacionado");
        }
        Veiculo veiculo = new Veiculo(placaFormatada);
        return veiculoRepository.save(veiculo);
    }

    public Optional<Veiculo> buscarVeiculoEstacionado(String placa) {
        return veiculoRepository.findByPlacaAndHoraSaidaIsNullOrHoraSaidaEquals(
                placa.toUpperCase(), 0L);
    }

    @Transactional
    public Transacao registrarSaida(String placa, double valorPago) {
        Optional<Veiculo> opt = buscarVeiculoEstacionado(placa);
        if (opt.isEmpty()) {
            throw new IllegalStateException("Veiculo nao encontrado ou ja saiu");
        }
        Veiculo veiculo = opt.get();
        veiculo.registrarSaida();

        double tarifa = calculadoraTarifa.calcularTarifa(veiculo.getTempoEstacionado());

        if (valorPago < tarifa) {
            throw new IllegalArgumentException(
                "Pagamento insuficiente. Necessario: R$ " + String.format("%.2f", tarifa));
        }

        veiculoRepository.save(veiculo);
        Transacao transacao = new Transacao(veiculo, valorPago, tarifa);
        return transacaoRepository.save(transacao);
    }

    public List<Veiculo> listarVeiculosEstacionados() {
        return veiculoRepository.findByHoraSaidaIsNullOrHoraSaidaEquals(0L);
    }

    public List<Transacao> listarTransacoes() {
        return transacaoRepository.findAll();
    }

    public double getReceitaTotal() {
        Double receita = transacaoRepository.getReceitaTotal();
        return receita != null ? receita : 0.0;
    }

    public long getTotalVeiculosAtendidos() {
        return transacaoRepository.count();
    }

    public long getVagasOcupadas() {
        return veiculoRepository.findByHoraSaidaIsNullOrHoraSaidaEquals(0L).size();
    }

    public CalculadoraTarifa getCalculadoraTarifa() {
        return calculadoraTarifa;
    }
}
