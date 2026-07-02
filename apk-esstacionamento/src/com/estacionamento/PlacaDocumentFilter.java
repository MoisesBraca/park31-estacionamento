package com.estacionamento;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

public class PlacaDocumentFilter extends DocumentFilter {

    @Override
    public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
        if (string == null) return;
        replace(fb, offset, 0, string, attr);
    }

    @Override
    public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
        if (text == null) text = "";

        // Obter o texto atual
        String currentText = fb.getDocument().getText(0, fb.getDocument().getLength());
        
        // Montar o texto resultante provisório da edição
        StringBuilder sb = new StringBuilder(currentText);
        sb.replace(offset, offset + length, text);

        // Limpar o input mantendo apenas letras e números, convertendo para maiúsculo
        String cleanInput = sb.toString().replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        
        // Limitar o comprimento absoluto do código da placa a 7 caracteres alfanuméricos
        if (cleanInput.length() > 7) {
            cleanInput = cleanInput.substring(0, 7);
        }

        // Formatar o texto com o traço após o 3º caractere (ex: ABC-1234 ou ABC-1D23)
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < cleanInput.length(); i++) {
            formatted.append(cleanInput.charAt(i));
            if (i == 2 && cleanInput.length() > 3) {
                formatted.append("-");
            }
        }

        // Substituir todo o conteúdo do documento pelo texto limpo e formatado
        super.replace(fb, 0, fb.getDocument().getLength(), formatted.toString(), attrs);
    }

    public static boolean isValida(String placa) {
        if (placa == null) return false;
        // Valida placa Mercosul (ABC-1D23) ou Tradicional (ABC-1234)
        return placa.matches("^[A-Z]{3}-[0-9][A-Z0-9][0-9]{2}$");
    }
}
