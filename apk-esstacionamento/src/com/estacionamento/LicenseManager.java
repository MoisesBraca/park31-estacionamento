package com.estacionamento;

import java.io.*;
import java.net.*;
import java.util.UUID;

public class LicenseManager {
    private static final String ID_FILE = "C:/sqlite/hardware_id.txt";
    private static String cachedHardwareId;

    public static String getHardwareId() {
        if (cachedHardwareId != null) return cachedHardwareId;
        
        File file = new File(ID_FILE);
        // Garante que a pasta C:\sqlite exista antes de criar o arquivo
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                cachedHardwareId = reader.readLine();
                if (cachedHardwareId != null && !cachedHardwareId.trim().isEmpty()) {
                    return cachedHardwareId.trim();
                }
            } catch (IOException ignored) {}
        }
        
        // Se não existe, gera um ID baseado no UUID e persiste na máquina do cliente
        String newId = "PK31-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(newId);
        } catch (IOException ignored) {}
        
        cachedHardwareId = newId;
        return newId;
    }

    public static String getDeviceName() {
        String name = System.getenv("COMPUTERNAME");
        if (name == null || name.trim().isEmpty()) {
            name = System.getenv("HOSTNAME");
        }
        if (name == null || name.trim().isEmpty()) {
            try {
                name = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                name = "PC-Desktop";
            }
        }
        return name;
    }
}
