package com.estacionamento;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Veiculo.class, Transacao.class, Vaga.class, TarifaConfig.class, AuditLog.class, Mensalista.class}, 
          version = 13, 
          exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract EstacionamentoDao estacionamentoDao();
    public abstract VagaDao vagaDao();
    public abstract TarifaConfigDao tarifaConfigDao();
    public abstract AuditLogDao auditLogDao();
    public abstract MensalistaDao mensalistaDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "estacionamento_db")
                            .fallbackToDestructiveMigration()
                            .addCallback(SEED_CALLBACK)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    private static final RoomDatabase.Callback SEED_CALLBACK = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            for (int i = 1; i <= 20; i++) {
                String num = "C" + (i < 10 ? "0" + i : i);
                db.execSQL("INSERT OR IGNORE INTO vagas (numero, andar, tipo, status) VALUES (?, 'Térreo', 'CARRO', 'LIVRE')",
                           new Object[]{num});
            }
            for (int i = 1; i <= 5; i++) {
                String num = "M0" + i;
                db.execSQL("INSERT OR IGNORE INTO vagas (numero, andar, tipo, status) VALUES (?, 'Térreo', 'MOTO', 'LIVRE')",
                           new Object[]{num});
            }
            db.execSQL("INSERT OR IGNORE INTO tarifa_config (tipo, valorBase, incremento, descricao) VALUES ('HORA', 5.0, 5.0, 'Tarifa por hora')");
            db.execSQL("INSERT OR IGNORE INTO tarifa_config (tipo, valorBase, incremento, descricao) VALUES ('DIARIA', 50.0, 0.0, 'Diária')");
            db.execSQL("INSERT OR IGNORE INTO tarifa_config (tipo, valorBase, incremento, descricao) VALUES ('LAVAGEM_DUCHA', 15.0, 0.0, 'Lavagem Ducha')");
            db.execSQL("INSERT OR IGNORE INTO tarifa_config (tipo, valorBase, incremento, descricao) VALUES ('LAVAGEM_SIMPLES', 30.0, 0.0, 'Lavagem Simples')");
            db.execSQL("INSERT OR IGNORE INTO tarifa_config (tipo, valorBase, incremento, descricao) VALUES ('LAVAGEM_COMPLETA', 50.0, 0.0, 'Lavagem Completa')");
        }
    };
}
