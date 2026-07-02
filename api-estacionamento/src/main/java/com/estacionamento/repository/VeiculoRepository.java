package com.estacionamento.repository;

import com.estacionamento.model.Veiculo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VeiculoRepository extends JpaRepository<Veiculo, Long> {
    List<Veiculo> findByHoraSaidaIsNullOrHoraSaidaEquals(Long horaSaida);
    Optional<Veiculo> findByPlacaAndHoraSaidaIsNullOrHoraSaidaEquals(String placa, Long horaSaida);
}
