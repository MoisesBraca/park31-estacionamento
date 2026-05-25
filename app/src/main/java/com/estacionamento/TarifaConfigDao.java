package com.estacionamento;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface TarifaConfigDao {

    @Query("SELECT * FROM tarifa_config")
    LiveData<List<TarifaConfig>> getAll();

    @Query("SELECT * FROM tarifa_config WHERE tipo = :tipo LIMIT 1")
    LiveData<TarifaConfig> getByTipo(String tipo);

    @Query("SELECT * FROM tarifa_config WHERE tipo = :tipo LIMIT 1")
    TarifaConfig getByTipoSync(String tipo);

    @Update
    void update(TarifaConfig config);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(TarifaConfig config);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAll(TarifaConfig... configs);

    @Query("SELECT valorBase FROM tarifa_config WHERE tipo = :tipo LIMIT 1")
    double getValorByTipoSync(String tipo);
}
