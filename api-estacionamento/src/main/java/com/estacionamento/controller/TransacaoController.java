package com.estacionamento.controller;

import com.estacionamento.model.Transacao;
import com.estacionamento.service.EstacionamentoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/transacoes")
public class TransacaoController {

    private final EstacionamentoService service;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    public TransacaoController(EstacionamentoService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listarTransacoes() {
        List<Transacao> transacoes = service.listarTransacoes();
        List<Map<String, Object>> response = transacoes.stream().map(t -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("placa", t.getPlaca());
            map.put("horaEntrada", dateFormat.format(new Date(t.getHoraEntrada())));
            map.put("horaSaida", dateFormat.format(new Date(t.getHoraSaida())));
            long minutos = (t.getHoraSaida() - t.getHoraEntrada()) / (1000 * 60);
            map.put("tempoEstacionadoMinutos", minutos);
            map.put("tarifaCobrada", t.getTarifaCobrada());
            map.put("valorPago", t.getValorPago());
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }
}
