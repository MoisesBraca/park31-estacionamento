package com.estacionamento;

import androidx.annotation.NonNull;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OcrHelper {
    
    private final TextRecognizer recognizer;
    // Regex para Placas Mercosul e Antigas
    private static final String REGEX_PLACA = "[A-Z]{3}[0-9][A-Z0-9][0-9]{2}";

    public OcrHelper() {
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    public interface OcrListener {
        void onPlacaDetected(String placa);
    }

    public void processImage(InputImage image, OcrListener listener) {
        recognizer.process(image)
            .addOnSuccessListener(visionText -> {
                String placa = extractPlaca(visionText.getText());
                if (placa != null) {
                    listener.onPlacaDetected(placa);
                }
            });
    }

    private String extractPlaca(String text) {
        String cleanText = text.toUpperCase().replaceAll("[^A-Z0-9]", "");
        
        Pattern any7 = Pattern.compile("[A-Z0-9]{7}");
        Matcher m = any7.matcher(cleanText);
        
        while (m.find()) {
            String candidate = m.group();
            String corrected = correctOcrMistakes(candidate);
            
            if (corrected.matches("[A-Z]{3}[0-9][A-Z0-9][0-9]{2}")) {
                return corrected;
            }
        }
        return null;
    }

    private String correctOcrMistakes(String placa) {
        char[] chars = placa.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (i < 3) {
                chars[i] = numberToLetter(chars[i]);
            } else if (i == 3) {
                chars[i] = letterToNumber(chars[i]);
            } else if (i > 4) {
                chars[i] = letterToNumber(chars[i]);
            }
        }
        return new String(chars);
    }

    private char numberToLetter(char c) {
        switch (c) {
            case '0': return 'O';
            case '1': return 'I';
            case '2': return 'Z';
            case '5': return 'S';
            case '8': return 'B';
            default: return c;
        }
    }

    private char letterToNumber(char c) {
        switch (c) {
            case 'O': case 'Q': case 'D': return '0';
            case 'I': case 'L': case 'T': return '1';
            case 'Z': return '2';
            case 'S': return '5';
            case 'B': return '8';
            case 'G': return '6';
            default: return c;
        }
    }
}
