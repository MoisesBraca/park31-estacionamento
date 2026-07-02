package com.estacionamento.controller;

import com.estacionamento.dto.RelatorioResponse;
import com.estacionamento.service.EstacionamentoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/relatorio")
public class RelatorioController {

    private final EstacionamentoService service;

    public RelatorioController(EstacionamentoService service) {
        this.service = service;
    }

    @GetMapping("/receita")
    public ResponseEntity<RelatorioResponse> getRelatorio() {
        RelatorioResponse response = new RelatorioResponse(
                service.getTotalVeiculosAtendidos(),
                service.getVagasOcupadas(),
                service.getReceitaTotal(),
                service.getCalculadoraTarifa().getTarifaHora()
        );
        return ResponseEntity.ok(response);
    }
}
