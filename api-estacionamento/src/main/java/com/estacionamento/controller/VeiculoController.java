package com.estacionamento.controller;

import com.estacionamento.dto.EntradaRequest;
import com.estacionamento.dto.SaidaRequest;
import com.estacionamento.dto.SaidaResponse;
import com.estacionamento.model.Transacao;
import com.estacionamento.model.Veiculo;
import com.estacionamento.service.EstacionamentoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/veiculos")
public class VeiculoController {

    private final EstacionamentoService service;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    public VeiculoController(EstacionamentoService service) {
        this.service = service;
    }

    @PostMapping("/entrada")
    public ResponseEntity<?> registrarEntrada(@RequestBody EntradaRequest request) {
        try {
            Veiculo veiculo = service.registrarEntrada(request.getPlaca());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("mensagem", "Entrada registrada com sucesso");
            response.put("placa", veiculo.getPlaca());
            response.put("horaEntrada", dateFormat.format(new Date(veiculo.getHoraEntrada())));
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            Map<String, String> error = new LinkedHashMap<>();
            error.put("erro", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/saida")
    public ResponseEntity<?> registrarSaida(@RequestBody SaidaRequest request) {
        try {
            if (request.getValorPago() == null || request.getPlaca() == null) {
                throw new IllegalArgumentException("Placa e valorPago sao obrigatorios");
            }
            Veiculo veiculo = service.buscarVeiculoEstacionado(request.getPlaca())
                    .orElseThrow(() -> new IllegalStateException("Veiculo nao encontrado"));

            double tarifa = service.getCalculadoraTarifa()
                    .calcularTarifa(veiculo.getTempoEstacionado());

            if (request.getValorPago() < tarifa) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("erro", "Pagamento insuficiente");
                error.put("tarifa", tarifa);
                error.put("valorPago", request.getValorPago());
                return ResponseEntity.badRequest().body(error);
            }

            Transacao transacao = service.registrarSaida(
                    request.getPlaca(), request.getValorPago());
            double troco = request.getValorPago() - tarifa;

            return ResponseEntity.ok(new SaidaResponse(transacao, troco));
        } catch (IllegalArgumentException | IllegalStateException e) {
            Map<String, String> error = new LinkedHashMap<>();
            error.put("erro", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/estacionados")
    public ResponseEntity<List<Map<String, Object>>> listarEstacionados() {
        List<Veiculo> veiculos = service.listarVeiculosEstacionados();
        List<Map<String, Object>> response = veiculos.stream().map(v -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("placa", v.getPlaca());
            map.put("horaEntrada", dateFormat.format(new Date(v.getHoraEntrada())));
            long minutos = v.getTempoEstacionado() / (1000 * 60);
            map.put("tempoEstacionadoMinutos", minutos);
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }
}
