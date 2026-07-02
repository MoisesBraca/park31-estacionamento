package com.estacionamento;

import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.widget.EditText;

public class MascaraPlaca implements TextWatcher {
    private final EditText editText;
    private boolean isUpdating = false;

    public MascaraPlaca(EditText editText) {
        this.editText = editText;
        // Limita a 8 caracteres (7 da placa + 1 do traço)
        this.editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (isUpdating) {
            isUpdating = false;
            return;
        }

        String str = s.toString().replaceAll("[^A-Z0-9]", "").toUpperCase();
        StringBuilder formatted = new StringBuilder();

        for (int i = 0; i < str.length(); i++) {
            formatted.append(str.charAt(i));
            if (i == 2) { // Adiciona o traço após o 3º caractere
                formatted.append("-");
            }
        }

        isUpdating = true;
        editText.setText(formatted.toString());
        editText.setSelection(formatted.length());
    }

    @Override
    public void afterTextChanged(Editable s) {}

    public static boolean isValida(String placa) {
        // Placa válida tem que ter 8 caracteres (incluindo o traço)
        return placa != null && placa.length() == 8 && placa.contains("-");
    }
}
