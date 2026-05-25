package com.estacionamento;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Environment;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class ComprovanteBuilder {

    private static final int PDF_WIDTH = 280;
    private static final int PDF_HEIGHT = 600;
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public static String buildText(String placa, long entrada, long saida,
                                    double totalPago, double tarifa, double troco,
                                    String formaPagamento, String operador) {
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        long minutos = (saida - entrada) / 60000;

        StringBuilder sb = new StringBuilder();
        sb.append("================================\n");
        sb.append("         PARK ' 31\n");
        sb.append("   Estacionamento Inteligente\n");
        sb.append("================================\n");
        sb.append("\n");
        sb.append("Placa:      ").append(placa).append("\n");
        sb.append("Entrada:    ").append(df.format(new Date(entrada))).append("\n");
        sb.append("Saída:      ").append(df.format(new Date(saida))).append("\n");
        sb.append("Tempo:      ").append(minutos).append(" min\n");
        sb.append("--------------------------------\n");
        sb.append("Tarifa:     R$ ").append(String.format("%.2f", tarifa)).append("\n");
        sb.append("Pago:       R$ ").append(String.format("%.2f", totalPago)).append("\n");
        if (troco > 0) {
            sb.append("Troco:      R$ ").append(String.format("%.2f", troco)).append("\n");
        }
        sb.append("Pagamento:  ").append(formaPagamento).append("\n");
        sb.append("--------------------------------\n");
        sb.append("Operador:   ").append(operador).append("\n");
        sb.append("================================\n");
        sb.append("    Obrigado pela preferência!\n");
        sb.append("================================\n");
        sb.append("\n\n\n");

        return sb.toString();
    }

    public static Uri gerarPdf(Context context, String placa, long entrada, long saida,
                                double totalPago, double tarifa, double troco,
                                String formaPagamento, String operador) {
        try {
            SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            long minutos = (saida - entrada) / 60000;

            PdfDocument doc = new PdfDocument();
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PDF_WIDTH, PDF_HEIGHT, 1).create();
            PdfDocument.Page page = doc.startPage(pageInfo);
            Canvas c = canvas(page);
            Paint p = new Paint();
            int y = 20;

            p.setTypeface(Typeface.MONOSPACE);
            p.setTextSize(14);
            p.setFakeBoldText(true);
            y = drawCenter(c, p, "PARK ' 31", y);
            p.setFakeBoldText(false);
            p.setTextSize(10);
            y = drawCenter(c, p, "Estacionamento Inteligente", y);
            y = drawLine(c, p, y);
            y += 8;

            p.setTextSize(11);
            y = drawRow(c, p, "Placa:", placa, y);
            y = drawRow(c, p, "Entrada:", df.format(new Date(entrada)), y);
            y = drawRow(c, p, "Saida:", df.format(new Date(saida)), y);
            y = drawRow(c, p, "Tempo:", minutos + " min", y);
            y = drawLine(c, p, y);
            y = drawRow(c, p, "Tarifa:", "R$ " + String.format("%.2f", tarifa), y);
            y = drawRow(c, p, "Pago:", "R$ " + String.format("%.2f", totalPago), y);
            if (troco > 0) {
                y = drawRow(c, p, "Troco:", "R$ " + String.format("%.2f", troco), y);
            }
            y = drawRow(c, p, "Pagamento:", formaPagamento, y);
            y = drawLine(c, p, y);
            y = drawRow(c, p, "Operador:", operador, y);
            y = drawLine(c, p, y);
            y += 8;

            p.setFakeBoldText(true);
            y = drawCenter(c, p, "Obrigado pela preferencia!", y);
            p.setFakeBoldText(false);

            doc.finishPage(page);

            File dir = new File(context.getCacheDir(), "comprovantes");
            dir.mkdirs();
            File file = new File(dir, "comprovante_" + placa.replace("-", "") + ".pdf");
            FileOutputStream fos = new FileOutputStream(file);
            doc.writeTo(fos);
            doc.close();

            return FileProvider.getUriForFile(context, "com.estacionamento.fileprovider", file);
        } catch (Exception e) {
            return null;
        }
    }

    public static void compartilharPdf(Context context, Uri pdfUri) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/pdf");
        share.putExtra(Intent.EXTRA_STREAM, pdfUri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(share, "Compartilhar Comprovante"));
    }

    public static void imprimirBluetooth(Context context, String deviceAddress, String text) {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) return;

            BluetoothDevice device = adapter.getRemoteDevice(deviceAddress);
            BluetoothSocket socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            adapter.cancelDiscovery();
            socket.connect();
            OutputStream out = socket.getOutputStream();

            out.write(new byte[]{0x1B, 0x40});          // Initialize printer
            out.write(text.getBytes("CP437"));           // Text
            out.write(new byte[]{0x1B, 0x64, 0x03});     // Feed 3 lines
            out.write(new byte[]{0x1D, 0x56, 0x00});     // Cut paper

            out.close();
            socket.close();
        } catch (Exception ignored) {}
    }

    private static int drawCenter(Canvas c, Paint p, String text, int y) {
        float x = (PDF_WIDTH - p.measureText(text)) / 2;
        c.drawText(text, x, y, p);
        return y + 18;
    }

    private static int drawRow(Canvas c, Paint p, String label, String value, int y) {
        c.drawText(label, 10, y, p);
        c.drawText(value, PDF_WIDTH - 10 - p.measureText(value), y, p);
        return y + 16;
    }

    private static int drawLine(Canvas c, Paint p, int y) {
        y += 4;
        c.drawLine(10, y, PDF_WIDTH - 10, y, p);
        return y + 8;
    }

    @SuppressWarnings("deprecation")
    private static Canvas canvas(PdfDocument.Page page) {
        return page.getCanvas();
    }
}
