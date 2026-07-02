package com.estacionamento;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface AuditLogDao {

    @Insert
    void insert(AuditLog log);

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC")
    LiveData<List<AuditLog>> getAll();

    @Query("SELECT * FROM audit_log WHERE acao = :acao ORDER BY timestamp DESC")
    LiveData<List<AuditLog>> getByAcao(String acao);

    @Query("SELECT * FROM audit_log WHERE timestamp >= :desde ORDER BY timestamp DESC")
    LiveData<List<AuditLog>> getDesde(long desde);

    @Query("SELECT COUNT(*) FROM audit_log")
    LiveData<Integer> getCount();
}
