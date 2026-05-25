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
        Pattern pattern = Pattern.compile(REGEX_PLACA);
        Matcher matcher = pattern.matcher(cleanText);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }
}
