package com.estacionamento.controller;

import com.estacionamento.service.CalculadoraTarifa;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class TarifaController {

    private final CalculadoraTarifa calculadoraTarifa;

    public TarifaController(CalculadoraTarifa calculadoraTarifa) {
        this.calculadoraTarifa = calculadoraTarifa;
    }

    @GetMapping("/tarifa")
    public ResponseEntity<Map<String, Object>> getTarifa() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tarifaHora", calculadoraTarifa.getTarifaHora());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/tarifa")
    public ResponseEntity<?> setTarifa(@RequestBody Map<String, Double> body) {
        try {
            Double novaTarifa = body.get("tarifaHora");
            if (novaTarifa == null) {
                throw new IllegalArgumentException("Campo 'tarifaHora' obrigatorio");
            }
            calculadoraTarifa.setTarifaHora(novaTarifa);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("mensagem", "Tarifa alterada com sucesso");
            response.put("tarifaHora", calculadoraTarifa.getTarifaHora());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new LinkedHashMap<>();
            error.put("erro", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
