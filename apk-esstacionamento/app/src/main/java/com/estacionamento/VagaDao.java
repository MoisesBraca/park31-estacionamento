package com.estacionamento;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface VagaDao {

    @Query("SELECT * FROM vagas ORDER BY numero ASC")
    LiveData<List<Vaga>> getAll();

    @Query("SELECT * FROM vagas WHERE id = :id LIMIT 1")
    LiveData<Vaga> getById(int id);

    @Query("SELECT * FROM vagas WHERE status = :status ORDER BY numero ASC")
    LiveData<List<Vaga>> getByStatus(String status);

    @Query("SELECT COUNT(*) FROM vagas WHERE status = 'LIVRE'")
    LiveData<Integer> getVagasLivres();

    @Query("SELECT COUNT(*) FROM vagas WHERE status = 'OCUPADA'")
    LiveData<Integer> getVagasOcupadas();

    @Update
    void update(Vaga vaga);

    @Query("UPDATE vagas SET status = :status WHERE id = :id")
    void updateStatus(int id, String status);

    @Query("SELECT * FROM vagas WHERE status = 'LIVRE' AND tipo = :tipo ORDER BY numero ASC LIMIT 1")
    Vaga getVagaLivreByTipoSync(String tipo);

    @Query("UPDATE vagas SET status = 'LIVRE' WHERE id = :id")
    void liberarVaga(int id);

    @Query("UPDATE vagas SET status = 'LIVRE' WHERE id NOT IN (SELECT vagaId FROM veiculos WHERE horaSaida = 0) AND status = 'OCUPADA'")
    void limparVagasOrfas();

    @Query("SELECT * FROM vagas WHERE tipo = :tipo ORDER BY numero ASC")
    List<Vaga> getVagasByTipoSync(String tipo);

    @Query("DELETE FROM vagas WHERE id = :id")
    void deleteVagaSync(int id);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(Vaga vaga);
}
