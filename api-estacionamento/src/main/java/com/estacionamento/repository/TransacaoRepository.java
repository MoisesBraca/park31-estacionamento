package com.estacionamento.repository;

import com.estacionamento.model.Transacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface TransacaoRepository extends JpaRepository<Transacao, Long> {
    @Query("SELECT COALESCE(SUM(t.valorPago), 0) FROM Transacao t")
    Double getReceitaTotal();
}
