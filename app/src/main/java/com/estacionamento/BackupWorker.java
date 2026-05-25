package com.estacionamento;

import android.content.Context;
import android.os.Environment;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BackupWorker extends Worker {

    private static final String DB_NAME = "estacionamento_db";

    public BackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            File dbFile = getApplicationContext().getDatabasePath(DB_NAME);
            if (!dbFile.exists()) return Result.failure();

            File backupDir;
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                backupDir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), "Park ' 31");
            } else {
                backupDir = new File(getApplicationContext().getCacheDir(), "backup");
            }
            backupDir.mkdirs();

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File backupFile = new File(backupDir, "backup_" + DB_NAME + "_" + timestamp);

            FileInputStream in = new FileInputStream(dbFile);
            FileOutputStream out = new FileOutputStream(backupFile);

            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.close();

            String lastBackupKey = BackupWorker.class.getName() + "_last";
            getApplicationContext()
                    .getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putLong(lastBackupKey, System.currentTimeMillis())
                    .putString("last_path", backupFile.getAbsolutePath())
                    .apply();

            return Result.success();
        } catch (Exception e) {
            return Result.failure();
        }
    }

    public static long getUltimoBackup(Context context) {
        return context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
                .getLong(BackupWorker.class.getName() + "_last", 0);
    }

    public static String getUltimoCaminho(Context context) {
        return context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
                .getString("last_path", null);
    }
}
