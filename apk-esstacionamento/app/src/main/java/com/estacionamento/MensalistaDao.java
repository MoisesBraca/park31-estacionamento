package com.estacionamento;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Delete;
import java.util.List;

@Dao
public interface MensalistaDao {
    @Query("SELECT * FROM mensalistas ORDER BY nomeCliente ASC")
    List<Mensalista> getAllList();

    @Query("SELECT * FROM mensalistas WHERE placa = :placa LIMIT 1")
    Mensalista getByPlaca(String placa);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Mensalista mensalista);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Mensalista> mensalistas);

    @Update
    void update(Mensalista mensalista);

    @Delete
    void delete(Mensalista mensalista);

    @Query("DELETE FROM mensalistas WHERE placa = :placa")
    void deleteByPlaca(String placa);

    @Query("DELETE FROM mensalistas")
    void deleteAll();
}
