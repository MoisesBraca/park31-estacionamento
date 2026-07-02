package com.estacionamento;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class EstacionamentoService {
    private final List<Veiculo> veiculos;
    private final List<Transacao> transacoes;
    private final EstacionamentoRepository repository;

    public EstacionamentoService() {
        this.repository = new EstacionamentoRepository();
        this.veiculos = new ArrayList<>();
        this.transacoes = new ArrayList<>();
        carregarDados();
    }

    private void carregarDados() {
        try {
            veiculos.addAll(repository.carregarVeiculos());
            transacoes.addAll(repository.carregarTransacoes());
            CalculadoraTarifa.carregar(repository.carregarTarifa());
        } catch (IOException e) {
            System.err.println("Aviso: nao foi possivel carregar dados salvos. " + e.getMessage());
        }
    }

    private void salvarDados() {
        try {
            repository.salvarVeiculos(veiculos);
            repository.salvarTransacoes(transacoes);
        } catch (IOException e) {
            System.err.println("Erro ao salvar dados: " + e.getMessage());
        }
    }

    public boolean registrarEntrada(String placa) {
        if (placa == null || placa.trim().isEmpty()) {
            return false;
        }
        String placaFormatada = placa.trim().toUpperCase();
        if (buscarVeiculoEstacionado(placaFormatada).isPresent()) {
            return false;
        }
        Veiculo veiculo = new Veiculo(placaFormatada);
        veiculos.add(veiculo);
        salvarDados();
        return true;
    }

    public Optional<Veiculo> buscarVeiculoEstacionado(String placa) {
        return veiculos.stream()
                .filter(v -> v.isEstacionado() && v.getPlaca().equals(placa.toUpperCase()))
                .findFirst();
    }

    public Transacao registrarSaida(String placa, double valorPago) {
        Optional<Veiculo> opt = buscarVeiculoEstacionado(placa);
        if (opt.isEmpty()) {
            return null;
        }
        Veiculo veiculo = opt.get();
        veiculo.registrarSaida();

        double tarifa = CalculadoraTarifa.calcularTarifa(veiculo.getTempoEstacionado());
        Pagamento pagamento = new Pagamento(valorPago);

        if (!pagamento.validarPagamento(tarifa)) {
            return null;
        }

        Transacao transacao = new Transacao(veiculo, valorPago, tarifa);
        transacoes.add(transacao);
        salvarDados();
        return transacao;
    }

    public List<Veiculo> listarVeiculosEstacionados() {
        return veiculos.stream()
                .filter(Veiculo::isEstacionado)
                .collect(Collectors.toList());
    }

    public List<Transacao> getTransacoes() {
        return new ArrayList<>(transacoes);
    }

    public double getReceitaTotal() {
        return transacoes.stream()
                .mapToDouble(Transacao::getValorPago)
                .sum();
    }

    public long getTotalVeiculosAtendidos() {
        return transacoes.size();
    }

    public int getVagasOcupadas() {
        return (int) veiculos.stream().filter(Veiculo::isEstacionado).count();
    }

    public void alterarTarifa(double novaTarifa) {
        CalculadoraTarifa.setTarifaHora(novaTarifa);
        repository.salvarTarifa(novaTarifa);
    }
}
