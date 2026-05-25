package com.estacionamento;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DashboardGraficosHelper {

    public static void configurarGraficoReceita(BarChart chart, List<Transacao> transacoes) {
        long seteDias = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;

        Map<Integer, Double> receitaPorDia = new HashMap<>();
        String[] dias = {"Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb"};

        Calendar cal = Calendar.getInstance();
        for (int i = 6; i >= 0; i--) {
            cal.setTimeInMillis(System.currentTimeMillis());
            cal.add(Calendar.DAY_OF_YEAR, -i);
            receitaPorDia.put(cal.get(Calendar.DAY_OF_WEEK), 0.0);
        }

        for (Transacao t : transacoes) {
            if (t.getHoraSaida() >= seteDias) {
                cal.setTimeInMillis(t.getHoraSaida());
                int diaSemana = cal.get(Calendar.DAY_OF_WEEK);
                Double atual = receitaPorDia.get(diaSemana);
                if (atual != null) {
                    receitaPorDia.put(diaSemana, atual + t.getValorPago());
                }
            }
        }

        ArrayList<BarEntry> entries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        int idx = 0;
        for (int i = 6; i >= 0; i--) {
            cal.setTimeInMillis(System.currentTimeMillis());
            cal.add(Calendar.DAY_OF_YEAR, -i);
            int dia = cal.get(Calendar.DAY_OF_WEEK);
            entries.add(new BarEntry(idx, receitaPorDia.get(dia).floatValue()));
            labels.add(dias[dia - 1]);
            idx++;
        }

        BarDataSet dataSet = new BarDataSet(entries, "Receita (R$)");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setValueTextSize(10f);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.6f);

        chart.setData(data);
        chart.getDescription().setEnabled(false);
        chart.setFitBars(true);
        chart.setPinchZoom(false);
        chart.setScaleEnabled(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.getLegend().setEnabled(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(7);

        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisRight().setEnabled(false);
        chart.animateY(600);
        chart.invalidate();
    }

    public static void configurarGraficoOcupacao(LineChart chart, List<Transacao> transacoes) {
        long seteDias = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;

        int[] entradasPorHora = new int[24];
        int[] saidasPorHora = new int[24];

        for (Transacao t : transacoes) {
            if (t.getHoraSaida() >= seteDias) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(t.getHoraEntrada());
                entradasPorHora[cal.get(Calendar.HOUR_OF_DAY)]++;

                cal.setTimeInMillis(t.getHoraSaida());
                saidasPorHora[cal.get(Calendar.HOUR_OF_DAY)]++;
            }
        }

        ArrayList<Entry> entradas = new ArrayList<>();
        ArrayList<Entry> saidas = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            entradas.add(new Entry(i, entradasPorHora[i]));
            saidas.add(new Entry(i, saidasPorHora[i]));
        }

        LineDataSet lineEntradas = new LineDataSet(entradas, "Entradas");
        lineEntradas.setColor(0xFF2563EB);
        lineEntradas.setCircleColor(0xFF2563EB);
        lineEntradas.setLineWidth(2f);
        lineEntradas.setCircleRadius(3f);
        lineEntradas.setDrawValues(false);
        lineEntradas.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineDataSet lineSaidas = new LineDataSet(saidas, "Saídas");
        lineSaidas.setColor(0xFFDC2626);
        lineSaidas.setCircleColor(0xFFDC2626);
        lineSaidas.setLineWidth(2f);
        lineSaidas.setCircleRadius(3f);
        lineSaidas.setDrawValues(false);
        lineSaidas.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineData data = new LineData(lineEntradas, lineSaidas);
        chart.setData(data);
        chart.getDescription().setEnabled(false);
        chart.setPinchZoom(false);
        chart.setScaleEnabled(false);
        chart.setDoubleTapToZoomEnabled(false);

        String[] horas = new String[24];
        for (int i = 0; i < 24; i++) horas[i] = String.format("%02d", i);

        XAxis xAxis = chart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(horas));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(6);

        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisRight().setEnabled(false);
        chart.getLegend().setTextSize(11f);
        chart.animateX(600);
        chart.invalidate();
    }
}
