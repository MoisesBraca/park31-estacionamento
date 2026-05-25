package com.estacionamento;

import android.graphics.Bitmap;
import android.graphics.Color;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import java.nio.charset.StandardCharsets;

public class PixQrCode {

    private static final String MERCHANT_NAME = "Park ' 31 Estacionamento";
    private static final String MERCHANT_CITY = "SAOPAULO";

    private PixQrCode() {}

    public static Bitmap gerarQrCode(String pixKey, double valor, int size) throws WriterException {
        String payload = gerarPayload(pixKey, valor);
        BitMatrix matrix = new MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, size, size);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return bitmap;
    }

    public static String gerarPayload(String pixKey, double valor) {
        String gui = "br.gov.bcb.pix";
        String txId = "***";

        String emv = "000201"                           // Payload Format Indicator
                   + "010212"                           // Point of Initiation Method (12 = static)
                   + "26" + lenStr("00" + lenStr(gui) + gui + "01" + lenStr(pixKey) + pixKey)
                   + "52040000"                         // Merchant Category Code
                   + "5303986"                          // Transaction Currency (986 = BRL)
                   + "54" + lenStr(formatValor(valor))  // Transaction Amount
                   + "5802BR"                           // Country Code
                   + "59" + lenStr(MERCHANT_NAME)       // Merchant Name
                   + "60" + lenStr(MERCHANT_CITY)       // Merchant City
                   + "62" + lenStr("05" + lenStr(txId) + txId) // Additional Data Field (TXID)
                   + "6304";                            // CRC16 placeholder

        String crc = calcularCRC16(emv);
        return emv + crc;
    }

    private static String lenStr(String s) {
        int len = s.length();
        return String.format("%02d", len) + s;
    }

    private static String formatValor(double valor) {
        return String.format("%.2f", valor);
    }

    static String calcularCRC16(String payload) {
        int crc = 0xFFFF;
        byte[] bytes = payload.getBytes(StandardCharsets.ISO_8859_1);
        for (byte b : bytes) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x8000) != 0) {
                    crc = (crc << 1) ^ 0x1021;
                } else {
                    crc <<= 1;
                }
            }
        }
        return String.format("%04X", crc & 0xFFFF);
    }
}
