package com.estacionamento;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface EstacionamentoDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertVeiculo(Veiculo veiculo);

    @Update
    void updateVeiculo(Veiculo veiculo);

    @Query("SELECT * FROM veiculos WHERE horaSaida = 0")
    LiveData<List<Veiculo>> getVeiculosEstacionados();

    @Query("SELECT * FROM veiculos WHERE placa = :placa AND horaSaida = 0 LIMIT 1")
    Veiculo getVeiculoEstacionado(String placa);

    @Query("SELECT * FROM veiculos WHERE vagaId = :vagaId AND horaSaida = 0 LIMIT 1")
    Veiculo getVeiculoByVagaId(int vagaId);

    @Insert
    void insertTransacao(Transacao transacao);

    @Query("SELECT * FROM transacoes ORDER BY horaSaida DESC")
    LiveData<List<Transacao>> getAllTransacoes();

    @Query("SELECT SUM(valorPago) FROM transacoes")
    LiveData<Double> getReceitaTotal();

    @Query("SELECT COUNT(*) FROM transacoes")
    LiveData<Integer> getTotalAtendidos();

    @Query("SELECT * FROM transacoes WHERE horaSaida BETWEEN :inicio AND :fim ORDER BY horaSaida DESC")
    LiveData<List<Transacao>> getTransacoesByPeriodo(long inicio, long fim);

    @Query("SELECT COUNT(*) FROM transacoes WHERE horaSaida BETWEEN :inicio AND :fim")
    int getCountByPeriodoSync(long inicio, long fim);

    @Query("SELECT SUM(valorPago) FROM transacoes WHERE horaSaida BETWEEN :inicio AND :fim")
    double getReceitaByPeriodoSync(long inicio, long fim);

    @Query("SELECT * FROM transacoes WHERE horaSaida BETWEEN :inicio AND :fim ORDER BY horaSaida DESC")
    List<Transacao> getTransacoesByPeriodoSync(long inicio, long fim);
}
