package com.estacionamento;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.text.*;
import java.util.*;
import java.util.List;

public class Main {

    private static final EstacionamentoService service = new EstacionamentoService();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private static final DecimalFormat moneyFormat = new DecimalFormat("#,##0.00");

    private JFrame frame;
    private CardLayout cardLayout;
    private JPanel contentPanel;
    private List<AbstractButton> navButtons = new ArrayList<>();
    private String currentView = "dashboard";

    private JPanel statOcupadas, statAtendidos, statReceita, statTarifa;
    private DefaultTableModel veiculosTableModel;
    private DefaultTableModel transacoesTableModel;
    private JTextField entradaPlacaField;
    private JTextField saidaPlacaField;
    private JPanel saidaDetailsPanel;
    private JLabel saidaPlacaLabel, saidaEntradaLabel, saidaTempoLabel, saidaTarifaLabel;
    private JTextField saidaValorField;
    private JComboBox<String> saidaMetodoCombo;
    private double saidaTarifaAtual;
    private Veiculo veiculoSaida;
    private JTextField tarifaField;
    private JLabel statusOcupadas, statusTarifa;

    private static final Color PRIMARY_DARK = new Color(26, 26, 46);
    private static final Color PRIMARY_MID = new Color(15, 52, 96);
    private static final Color PRIMARY_LIGHT = new Color(26, 74, 138);
    private static final Color BG_COLOR = new Color(240, 242, 245);
    private static final Color CARD_BG = Color.WHITE;
    private static final Color TEXT_PRIMARY = new Color(26, 26, 46);
    private static final Color TEXT_SECONDARY = new Color(102, 102, 102);
    private static final Color SUCCESS = new Color(39, 174, 96);
    private static final Color DANGER = new Color(231, 76, 60);
    private static final Color NAV_TEXT = new Color(160, 160, 176);
    private static final Color NAV_HOVER = new Color(42, 42, 78);
    private static final Color NAV_SELECTED = new Color(15, 52, 96);

    public static void main(String[] args) {
        // Inicializa o servidor local de licenciamento na porta 8080
        LicencaServer.iniciar(8080);

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new Main().createAndShowGUI();
        });
    }

    private void createAndShowGUI() {
        frame = new JFrame("Sistema de Estacionamento");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1050, 700);
        frame.setMinimumSize(new Dimension(850, 600));
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());

        frame.add(createSidebar(), BorderLayout.WEST);
        frame.add(createContentArea(), BorderLayout.CENTER);
        frame.add(createStatusBar(), BorderLayout.SOUTH);

        showView("dashboard");
        frame.setVisible(true);

        // Validador de Licenciamento: Bloqueia se o status não for ATIVO
        EstacionamentoRepository repo = new EstacionamentoRepository();
        String hwId = LicenseManager.getHardwareId();
        
        // Garante o registro do terminal no banco central
        repo.registrarTerminal(hwId, LicenseManager.getDeviceName(), "Windows Desktop");
        
        // Carrega configurações iniciais (Tarifa/Vagas) salvas no painel admin
        TerminalInfo startupInfo = repo.obterTerminal(hwId);
        if (startupInfo != null) {
            CalculadoraTarifa.setTarifaHora(startupInfo.getTarifaHora());
            repo.salvarTarifa(startupInfo.getTarifaHora());
        }

        String status = repo.verificarStatusTerminal(hwId);
        long exp = repo.obterExpiracaoTerminal(hwId);
        if (!status.equals("ATIVO") || (exp > 0 && System.currentTimeMillis() > exp)) {
            TelaBloqueioDialog dialog = new TelaBloqueioDialog(frame);
            dialog.setVisible(true);
        }

        // Sincronizador de configurações e licença em tempo real (a cada 4 segundos)
        new javax.swing.Timer(4000, e -> {
            String id = LicenseManager.getHardwareId();
            TerminalInfo info = repo.obterTerminal(id);
            if (info != null) {
                // 1. Sincronizar Tarifa em Tempo Real
                if (CalculadoraTarifa.getTarifaHora() != info.getTarifaHora()) {
                    CalculadoraTarifa.setTarifaHora(info.getTarifaHora());
                    repo.salvarTarifa(info.getTarifaHora());
                    
                    updateStatusBar();
                    refreshDashboard();
                    
                    if (tarifaField != null) {
                        tarifaField.setText(moneyFormat.format(info.getTarifaHora()));
                    }
                    if (veiculoSaida != null) {
                        saidaTarifaAtual = CalculadoraTarifa.calcularTarifa(veiculoSaida.getTempoEstacionado());
                        saidaTarifaLabel.setText("R$ " + moneyFormat.format(saidaTarifaAtual));
                        saidaValorField.setText(moneyFormat.format(saidaTarifaAtual));
                    }
                }
                
                // 2. Sincronizar Status da Licença (Bloqueio em Tempo Real)
                String st = info.getStatus();
                long expTime = info.getDataExpiracao();
                if (!st.equals("ATIVO") || (expTime > 0 && System.currentTimeMillis() > expTime)) {
                    boolean dialogAberta = false;
                    for (Window w : Window.getWindows()) {
                        if (w instanceof TelaBloqueioDialog && w.isVisible()) {
                            dialogAberta = true;
                            break;
                        }
                    }
                    if (!dialogAberta) {
                        TelaBloqueioDialog dialog = new TelaBloqueioDialog(frame);
                        dialog.setVisible(true);
                    }
                }
            }
        }).start();
    }

    private JPanel createSidebar() {
        JPanel panel = new JPanel();
        panel.setBackground(PRIMARY_DARK);
        panel.setPreferredSize(new Dimension(200, 0));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("ESTACIONAMENTO");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setBorder(new EmptyBorder(25, 20, 25, 20));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(title);

        panel.add(new JSeparator() {{
            setForeground(new Color(60, 60, 80));
            setMaximumSize(new Dimension(180, 1));
            setAlignmentX(Component.CENTER_ALIGNMENT);
        }});

        panel.add(Box.createVerticalStrut(10));

        String[][] navItems = {
            {"dashboard", "  \uD83D\uDCCB  Dashboard"},
            {"entrada", "  \uD83D\uDE97  Registrar Entrada"},
            {"saida", "  \uD83D\uDE99  Registrar Sa\u00EDda"},
            {"listar", "  \uD83D\uDCC4  Ve\u00EDculos Estacionados"},
            {"historico", "  \uD83D\uDCDC  Hist\u00F3rico"},
            {"relatorio", "  \uD83D\uDCB0  Relat\u00F3rio"},
            {"tarifa", "  \u2699  Alterar Tarifa"}
        };

        ButtonGroup group = new ButtonGroup();

        for (String[] item : navItems) {
            JToggleButton btn = new JToggleButton(item[1]);
            btn.setActionCommand(item[0]);
            btn.setBackground(PRIMARY_DARK);
            btn.setForeground(NAV_TEXT);
            btn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            btn.setBorder(new EmptyBorder(12, 20, 12, 20));
            btn.setFocusPainted(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.setMaximumSize(new Dimension(200, 42));
            btn.setAlignmentX(Component.CENTER_ALIGNMENT);
            btn.setHorizontalAlignment(SwingConstants.LEFT);

            btn.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) {
                    if (!btn.isSelected()) btn.setBackground(NAV_HOVER);
                }
                public void mouseExited(MouseEvent e) {
                    if (!btn.isSelected()) btn.setBackground(PRIMARY_DARK);
                }
            });

            btn.addActionListener(e -> {
                for (AbstractButton b : navButtons) {
                    b.setBackground(PRIMARY_DARK);
                    b.setForeground(NAV_TEXT);
                }
                btn.setBackground(NAV_SELECTED);
                btn.setForeground(Color.WHITE);
                showView(btn.getActionCommand());
            });

            group.add(btn);
            navButtons.add(btn);
            panel.add(btn);
        }

        if (!navButtons.isEmpty()) {
            navButtons.get(0).setBackground(NAV_SELECTED);
            navButtons.get(0).setForeground(Color.WHITE);
        }

        panel.add(Box.createVerticalGlue());

        JLabel footer = new JLabel("v1.0 - Swing");
        footer.setForeground(new Color(80, 80, 100));
        footer.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        footer.setBorder(new EmptyBorder(10, 20, 15, 20));
        footer.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(footer);

        return panel;
    }

    private JPanel createContentArea() {
        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        contentPanel.setBackground(BG_COLOR);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        contentPanel.add(createDashboardView(), "dashboard");
        contentPanel.add(createEntradaView(), "entrada");
        contentPanel.add(createSaidaView(), "saida");
        contentPanel.add(createListarView(), "listar");
        contentPanel.add(createHistoricoView(), "historico");
        contentPanel.add(createRelatorioView(), "relatorio");
        contentPanel.add(createTarifaView(), "tarifa");

        return contentPanel;
    }

    private void showView(String name) {
        currentView = name;
        cardLayout.show(contentPanel, name);
        refreshCurrentView();
    }

    private void refreshCurrentView() {
        switch (currentView) {
            case "dashboard": refreshDashboard(); break;
            case "listar": refreshListarView(); break;
            case "historico": refreshHistoricoView(); break;
            case "relatorio": refreshRelatorioView(); break;
        }
        updateStatusBar();
    }

    // ─────────────────────── DASHBOARD ───────────────────────

    private JPanel createDashboardView() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(BG_COLOR);
        wrapper.setBorder(new EmptyBorder(25, 25, 25, 25));

        JLabel title = new JLabel("Dashboard");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(TEXT_PRIMARY);
        wrapper.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridBagLayout());
        center.setBackground(BG_COLOR);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        statOcupadas = createStatCard("\uD83D\uDE97", "Vagas Ocupadas", "0");
        statAtendidos = createStatCard("\uD83D\uDCCA", "Total Atendidos", "0");
        statReceita = createStatCard("\uD83D\uDCB0", "Receita Total", "R$ 0,00");
        statTarifa = createStatCard("\u2699", "Tarifa / Hora", "R$ 5,00");

        gbc.gridx = 0; gbc.gridy = 0;
        center.add(statOcupadas, gbc);
        gbc.gridx = 1;
        center.add(statAtendidos, gbc);
        gbc.gridx = 0; gbc.gridy = 1;
        center.add(statReceita, gbc);
        gbc.gridx = 1;
        center.add(statTarifa, gbc);

        wrapper.add(center, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel createStatCard(String icon, String label, String value) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
            new EmptyBorder(15, 20, 15, 20),
            BorderFactory.createLineBorder(new Color(230, 230, 235), 1, true)
        ));

        JLabel title = new JLabel(icon + "  " + label);
        title.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        title.setForeground(TEXT_SECONDARY);

        JLabel valLabel = new JLabel(value);
        valLabel.setFont(new Font("Segoe UI", Font.BOLD, 32));
        valLabel.setForeground(PRIMARY_MID);
        valLabel.setName(value); // store value reference

        card.add(title, BorderLayout.NORTH);
        card.add(valLabel, BorderLayout.CENTER);

        return card;
    }

    private void refreshDashboard() {
        updateStatCard(statOcupadas, String.valueOf(service.getVagasOcupadas()));
        updateStatCard(statAtendidos, String.valueOf(service.getTotalVeiculosAtendidos()));
        updateStatCard(statReceita, "R$ " + moneyFormat.format(service.getReceitaTotal()));
        updateStatCard(statTarifa, "R$ " + moneyFormat.format(CalculadoraTarifa.getTarifaHora()));
    }

    private void updateStatCard(JPanel card, String value) {
        for (Component c : ((JPanel)card).getComponents()) {
            if (c instanceof JLabel) {
                JLabel label = (JLabel) c;
                if (label.getFont().getSize() >= 28) {
                    label.setText(value);
                }
            }
        }
    }

    // ─────────────────────── REGISTRAR ENTRADA ───────────────────────

    private JPanel createEntradaView() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(BG_COLOR);
        wrapper.setBorder(new EmptyBorder(25, 25, 25, 25));

        JLabel title = new JLabel("Registrar Entrada");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(TEXT_PRIMARY);
        wrapper.add(title, BorderLayout.NORTH);

        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
            new EmptyBorder(30, 30, 30, 30),
            BorderFactory.createLineBorder(new Color(230, 230, 235), 1, true)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel placaLabel = new JLabel("Placa do Ve\u00EDculo:");
        placaLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));

        entradaPlacaField = new JTextField(15);
        ((javax.swing.text.AbstractDocument) entradaPlacaField.getDocument()).setDocumentFilter(new PlacaDocumentFilter());
        entradaPlacaField.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        entradaPlacaField.setPreferredSize(new Dimension(250, 38));
        entradaPlacaField.setToolTipText("Ex: ABC-1234");

        JButton registrarBtn = new JButton("Registrar Entrada");
        styleButton(registrarBtn, PRIMARY_MID, Color.WHITE);
        registrarBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        registrarBtn.addActionListener(e -> registrarEntrada());

        JButton limparBtn = new JButton("Limpar");
        styleButton(limparBtn, new Color(150, 150, 150), Color.WHITE);
        limparBtn.addActionListener(e -> entradaPlacaField.setText(""));

        entradaPlacaField.addActionListener(e -> registrarEntrada());

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        card.add(placaLabel, gbc);
        gbc.gridy = 1;
        card.add(entradaPlacaField, gbc);
        gbc.gridy = 2; gbc.gridwidth = 1;
        gbc.insets = new Insets(15, 8, 8, 8);
        card.add(registrarBtn, gbc);
        gbc.gridx = 1;
        card.add(limparBtn, gbc);

        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setBackground(BG_COLOR);
        centerWrapper.add(card);
        wrapper.add(centerWrapper, BorderLayout.CENTER);

        return wrapper;
    }

    private void registrarEntrada() {
        String placa = entradaPlacaField.getText().trim().toUpperCase();
        if (!PlacaDocumentFilter.isValida(placa)) {
            JOptionPane.showMessageDialog(frame, "Informe uma placa completa e valida!\nEx: ABC-1234 ou ABC-1D23", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (service.registrarEntrada(placa)) {
            JOptionPane.showMessageDialog(frame,
                "Entrada registrada para ve\u00EDculo " + placa,
                "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            entradaPlacaField.setText("");
            updateStatusBar();
        } else {
            JOptionPane.showMessageDialog(frame,
                "Ve\u00EDculo " + placa + " j\u00E1 est\u00E1 estacionado ou placa inv\u00E1lida!",
                "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ─────────────────────── REGISTRAR SAÍDA ───────────────────────

    private JPanel createSaidaView() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(BG_COLOR);
        wrapper.setBorder(new EmptyBorder(25, 25, 25, 25));

        JLabel title = new JLabel("Registrar Sa\u00EDda");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(TEXT_PRIMARY);
        wrapper.add(title, BorderLayout.NORTH);

        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setBackground(BG_COLOR);

        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
            new EmptyBorder(30, 30, 30, 30),
            BorderFactory.createLineBorder(new Color(230, 230, 235), 1, true)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel placaLabel = new JLabel("Placa do Ve\u00EDculo:");
        placaLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));

        saidaPlacaField = new JTextField(15);
        ((javax.swing.text.AbstractDocument) saidaPlacaField.getDocument()).setDocumentFilter(new PlacaDocumentFilter());
        saidaPlacaField.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        saidaPlacaField.setPreferredSize(new Dimension(250, 38));
        saidaPlacaField.addActionListener(e -> buscarVeiculoSaida());

        JButton buscarBtn = new JButton("Buscar Ve\u00EDculo");
        styleButton(buscarBtn, PRIMARY_MID, Color.WHITE);
        buscarBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        buscarBtn.addActionListener(e -> buscarVeiculoSaida());

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        card.add(placaLabel, gbc);
        gbc.gridy = 1;
        card.add(saidaPlacaField, gbc);
        gbc.gridy = 2; gbc.gridwidth = 1;
        gbc.insets = new Insets(15, 8, 8, 8);
        card.add(buscarBtn, gbc);

        saidaDetailsPanel = new JPanel(new GridBagLayout());
        saidaDetailsPanel.setBackground(CARD_BG);
        saidaDetailsPanel.setVisible(false);

        gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel detalhesTitle = new JLabel("Dados do Ve\u00EDculo");
        detalhesTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        detalhesTitle.setForeground(PRIMARY_MID);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3;
        saidaDetailsPanel.add(detalhesTitle, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 1; gbc.gridx = 0;
        saidaDetailsPanel.add(createInfoLabel("Placa:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2;
        saidaPlacaLabel = createInfoValue("");
        saidaDetailsPanel.add(saidaPlacaLabel, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 2; gbc.gridx = 0;
        saidaDetailsPanel.add(createInfoLabel("Entrada:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2;
        saidaEntradaLabel = createInfoValue("");
        saidaDetailsPanel.add(saidaEntradaLabel, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 3; gbc.gridx = 0;
        saidaDetailsPanel.add(createInfoLabel("Tempo:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2;
        saidaTempoLabel = createInfoValue("");
        saidaDetailsPanel.add(saidaTempoLabel, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 4; gbc.gridx = 0;
        saidaDetailsPanel.add(createInfoLabel("Tarifa:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2;
        saidaTarifaLabel = createInfoValue("");
        saidaTarifaLabel.setForeground(DANGER);
        saidaDetailsPanel.add(saidaTarifaLabel, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 5; gbc.gridx = 0;
        saidaDetailsPanel.add(new JLabel("Forma de Pagamento:") {{
            setFont(new Font("Segoe UI", Font.BOLD, 14));
            setForeground(TEXT_SECONDARY);
        }}, gbc);
        gbc.gridx = 1;
        saidaMetodoCombo = new JComboBox<>(new String[]{"Dinheiro", "Cartão (Débito/Crédito)", "Pix"});
        saidaMetodoCombo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        saidaMetodoCombo.setPreferredSize(new Dimension(150, 38));
        saidaMetodoCombo.addActionListener(e -> {
            String forma = (String) saidaMetodoCombo.getSelectedItem();
            if ("Pix".equals(forma) || "Cartão (Débito/Crédito)".equals(forma)) {
                saidaValorField.setText(moneyFormat.format(saidaTarifaAtual));
                saidaValorField.setEnabled(false);
            } else {
                saidaValorField.setText("");
                saidaValorField.setEnabled(true);
                saidaValorField.requestFocus();
            }
        });
        saidaDetailsPanel.add(saidaMetodoCombo, gbc);

        gbc.gridy = 6; gbc.gridx = 0;
        saidaDetailsPanel.add(new JLabel("Valor Recebido:") {{
            setFont(new Font("Segoe UI", Font.BOLD, 14));
            setForeground(TEXT_SECONDARY);
        }}, gbc);
        gbc.gridx = 1;
        saidaValorField = new JTextField(10);
        saidaValorField.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        saidaValorField.setPreferredSize(new Dimension(150, 38));
        saidaDetailsPanel.add(saidaValorField, gbc);

        JButton confirmarBtn = new JButton("Confirmar Sa\u00EDda");
        styleButton(confirmarBtn, SUCCESS, Color.WHITE);
        confirmarBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        confirmarBtn.addActionListener(e -> confirmarSaida());
        gbc.gridx = 2; gbc.gridwidth = 1;
        saidaDetailsPanel.add(confirmarBtn, gbc);

        saidaValorField.addActionListener(e -> confirmarSaida());

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        gbc.insets = new Insets(20, 8, 8, 8);
        card.add(saidaDetailsPanel, gbc);

        centerWrapper.add(card);
        wrapper.add(centerWrapper, BorderLayout.CENTER);

        return wrapper;
    }

    private JLabel createInfoLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 14));
        label.setForeground(TEXT_SECONDARY);
        return label;
    }

    private JLabel createInfoValue(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        label.setForeground(TEXT_PRIMARY);
        return label;
    }

    private void buscarVeiculoSaida() {
        String placa = saidaPlacaField.getText().trim().toUpperCase();
        if (!PlacaDocumentFilter.isValida(placa)) {
            JOptionPane.showMessageDialog(frame, "Informe uma placa completa e valida!\nEx: ABC-1234 ou ABC-1D23", "Aviso", JOptionPane.WARNING_MESSAGE);
            saidaDetailsPanel.setVisible(false);
            frame.revalidate();
            return;
        }

        Optional<Veiculo> opt = service.buscarVeiculoEstacionado(placa);
        if (opt.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Ve\u00EDculo n\u00E3o encontrado ou j\u00E1 saiu!", "Aviso", JOptionPane.WARNING_MESSAGE);
            saidaDetailsPanel.setVisible(false);
            frame.revalidate();
            return;
        }

        veiculoSaida = opt.get();
        saidaPlacaLabel.setText(veiculoSaida.getPlaca());
        saidaEntradaLabel.setText(dateFormat.format(new Date(veiculoSaida.getHoraEntrada())));
        long minutos = veiculoSaida.getTempoEstacionado() / (1000 * 60);
        saidaTempoLabel.setText(minutos + " minutos");
        saidaTarifaAtual = CalculadoraTarifa.calcularTarifa(veiculoSaida.getTempoEstacionado());
        saidaTarifaLabel.setText("R$ " + moneyFormat.format(saidaTarifaAtual));
        saidaMetodoCombo.setSelectedIndex(0);
        saidaValorField.setEnabled(true);
        saidaValorField.setText("");
        saidaDetailsPanel.setVisible(true);
        saidaValorField.requestFocus();
        frame.revalidate();
    }

    private void confirmarSaida() {
        if (veiculoSaida == null) {
            JOptionPane.showMessageDialog(frame, "Busque um ve\u00EDculo primeiro.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String valorStr = saidaValorField.getText().trim().replace(",", ".");
        double valorPago;
        try {
            valorPago = Double.parseDouble(valorStr);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(frame, "Valor inv\u00E1lido!", "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (valorPago < 0) {
            JOptionPane.showMessageDialog(frame, "Valor n\u00E3o pode ser negativo!", "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (valorPago < saidaTarifaAtual) {
            JOptionPane.showMessageDialog(frame,
                "Pagamento insuficiente!\nNecess\u00E1rio: R$ " + moneyFormat.format(saidaTarifaAtual) +
                "\nRecebido: R$ " + moneyFormat.format(valorPago),
                "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String forma = (String) saidaMetodoCombo.getSelectedItem();
        if ("Pix".equals(forma)) {
            mostrarPixDialog(valorPago);
        } else if ("Cartão (Débito/Crédito)".equals(forma)) {
            mostrarCartaoDialog(valorPago);
        } else {
            processarSaidaFinal(valorPago, forma);
        }
    }

    private void processarSaidaFinal(double valorPago, String formaPagamento) {
        Transacao transacao = service.registrarSaida(veiculoSaida.getPlaca(), valorPago);
        if (transacao != null) {
            double troco = valorPago - saidaTarifaAtual;
            String msg = String.format(
                "Sa\u00EDda registrada com sucesso!\n\nPlaca: %s\nValor pago: R$ %s\nTroco: R$ %s\nForma de Pagamento: %s",
                veiculoSaida.getPlaca(), moneyFormat.format(valorPago), moneyFormat.format(troco), formaPagamento);

            JOptionPane.showMessageDialog(frame, msg, "Pagamento Realizado", JOptionPane.INFORMATION_MESSAGE);

            saidaPlacaField.setText("");
            saidaDetailsPanel.setVisible(false);
            veiculoSaida = null;
            updateStatusBar();
        } else {
            JOptionPane.showMessageDialog(frame, "Erro ao registrar sa\u00EDda!", "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void mostrarPixDialog(double valor) {
        JDialog dialog = new JDialog(frame, "Pagamento via Pix", true);
        dialog.setSize(380, 520);
        dialog.setLocationRelativeTo(frame);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(BG_COLOR);

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        topPanel.setBackground(BG_COLOR);
        topPanel.setBorder(new EmptyBorder(15, 10, 5, 10));
        JLabel titleLabel = new JLabel("Escaneie o QR Code Pix");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(TEXT_PRIMARY);
        topPanel.add(titleLabel);

        JPanel qreContainer = new JPanel(new FlowLayout(FlowLayout.CENTER));
        qreContainer.setBackground(BG_COLOR);
        JPanel qrPanel = createSimulatedQRCodePanel();
        qrPanel.setPreferredSize(new Dimension(240, 240));
        qreContainer.add(qrPanel);

        String payload = gerarPayloadPix("11999999999", valor);

        JPanel bottomPanel = new JPanel(new GridBagLayout());
        bottomPanel.setBackground(BG_COLOR);
        bottomPanel.setBorder(new EmptyBorder(5, 20, 20, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.weightx = 1.0;

        JLabel valLabel = new JLabel("Valor: R$ " + moneyFormat.format(valor), SwingConstants.CENTER);
        valLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        valLabel.setForeground(SUCCESS);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        bottomPanel.add(valLabel, gbc);

        JTextField copyText = new JTextField(payload);
        copyText.setEditable(false);
        copyText.setFont(new Font("Monospaced", Font.PLAIN, 11));
        copyText.setCaretPosition(0);
        gbc.gridy = 1;
        bottomPanel.add(copyText, gbc);

        JButton btnCopiar = new JButton("Copiar Pix Copia e Cola");
        styleButton(btnCopiar, PRIMARY_MID, Color.WHITE);
        btnCopiar.addActionListener(e -> {
            java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(payload);
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            JOptionPane.showMessageDialog(dialog, "Código Pix copiado para a área de transferência!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
        });
        gbc.gridy = 2; gbc.gridwidth = 1;
        bottomPanel.add(btnCopiar, gbc);

        JButton btnConfirmar = new JButton("Simular Recebimento");
        styleButton(btnConfirmar, SUCCESS, Color.WHITE);
        btnConfirmar.addActionListener(e -> {
            dialog.dispose();
            processarSaidaFinal(valor, "Pix");
        });
        gbc.gridx = 1;
        bottomPanel.add(btnConfirmar, gbc);

        JButton btnCancelar = new JButton("Cancelar");
        styleButton(btnCancelar, new Color(150, 150, 150), Color.WHITE);
        btnCancelar.addActionListener(e -> dialog.dispose());
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        bottomPanel.add(btnCancelar, gbc);

        dialog.add(topPanel, BorderLayout.NORTH);
        dialog.add(qreContainer, BorderLayout.CENTER);
        dialog.add(bottomPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void mostrarCartaoDialog(double valor) {
        JDialog dialog = new JDialog(frame, "Pagamento via Cartão", true);
        dialog.setSize(380, 240);
        dialog.setLocationRelativeTo(frame);
        dialog.setLayout(new GridBagLayout());
        dialog.getContentPane().setBackground(CARD_BG);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 20, 10, 20);
        gbc.weightx = 1.0;

        JLabel mainLabel = new JLabel("Aproxime ou insira o cartão...", SwingConstants.CENTER);
        mainLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        mainLabel.setForeground(TEXT_PRIMARY);
        gbc.gridx = 0; gbc.gridy = 0;
        dialog.add(mainLabel, gbc);

        JLabel subLabel = new JLabel("Valor: R$ " + moneyFormat.format(valor), SwingConstants.CENTER);
        subLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        subLabel.setForeground(PRIMARY_LIGHT);
        gbc.gridy = 1;
        dialog.add(subLabel, gbc);

        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setPreferredSize(new Dimension(300, 24));
        progressBar.setForeground(SUCCESS);
        progressBar.setStringPainted(true);
        gbc.gridy = 2;
        dialog.add(progressBar, gbc);

        JButton btnCancelar = new JButton("Cancelar Transação");
        styleButton(btnCancelar, DANGER, Color.WHITE);
        gbc.gridy = 3;
        dialog.add(btnCancelar, gbc);

        javax.swing.Timer timer = new javax.swing.Timer(100, null);
        final int[] progress = {0};
        timer.addActionListener(e -> {
            progress[0] += 5;
            progressBar.setValue(progress[0]);
            if (progress[0] == 25) {
                mainLabel.setText("Lendo dados do cartão...");
            } else if (progress[0] == 55) {
                mainLabel.setText("Conectando à rede bancária...");
            } else if (progress[0] == 80) {
                mainLabel.setText("Processando autorização...");
            } else if (progress[0] >= 100) {
                timer.stop();
                mainLabel.setText("Pagamento Aprovado!");
                progressBar.setString("Sucesso!");
                new javax.swing.Timer(500, e2 -> {
                    dialog.dispose();
                    processarSaidaFinal(valor, "Cartão");
                }) {{ setRepeats(false); }}.start();
            }
        });

        btnCancelar.addActionListener(e -> {
            timer.stop();
            dialog.dispose();
        });

        timer.start();
        dialog.setVisible(true);
    }

    private JPanel createSimulatedQRCodePanel() {
        return new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                g2d.setColor(Color.WHITE);
                g2d.fillRoundRect(10, 10, w - 20, h - 20, 20, 20);
                g2d.setColor(new Color(220, 224, 230));
                g2d.drawRoundRect(10, 10, w - 20, h - 20, 20, 20);

                g2d.setColor(new Color(15, 23, 42));

                drawQREye(g2d, 30, 30, 45);
                drawQREye(g2d, w - 75, 30, 45);
                drawQREye(g2d, 30, h - 75, 45);
                drawQREye(g2d, w - 60, h - 60, 20);

                Random rand = new Random(42);
                int cellSize = 6;
                int startX = 30;
                int startY = 30;
                int gridW = (w - 60) / cellSize;
                int gridH = (h - 60) / cellSize;

                for (int x = 0; x < gridW; x++) {
                    for (int y = 0; y < gridH; y++) {
                        boolean inTopLeftEye = (x * cellSize < 50 && y * cellSize < 50);
                        boolean inTopRightEye = (x * cellSize > (w - 60) - 50 && y * cellSize < 50);
                        boolean inBottomLeftEye = (x * cellSize < 50 && y * cellSize > (h - 60) - 50);
                        boolean inBottomRightEye = (x * cellSize > (w - 60) - 30 && y * cellSize > (h - 60) - 30);

                        if (!inTopLeftEye && !inTopRightEye && !inBottomLeftEye && !inBottomRightEye) {
                            if (rand.nextBoolean()) {
                                g2d.fillRect(startX + x * cellSize, startY + y * cellSize, cellSize - 1, cellSize - 1);
                            }
                        }
                    }
                }
            }

            private void drawQREye(Graphics2D g2, int x, int y, int size) {
                g2.fillRect(x, y, size, size);
                g2.setColor(Color.WHITE);
                g2.fillRect(x + size/6, y + size/6, size - 2*(size/6), size - 2*(size/6));
                g2.setColor(new Color(15, 23, 42));
                g2.fillRect(x + size/3, y + size/3, size - 2*(size/3), size - 2*(size/3));
            }
        };
    }

    private String gerarPayloadPix(String pixKey, double valor) {
        String gui = "br.gov.bcb.pix";
        String txId = "***";
        String valorStr = String.format(Locale.US, "%.2f", valor);

        String emv = "000201"
                   + "010212"
                   + "26" + lenStrEMV("00" + lenStrEMV(gui) + gui + "01" + lenStrEMV(pixKey) + pixKey)
                   + "52040000"
                   + "5303986"
                   + "54" + lenStrEMV(valorStr)
                   + "5802BR"
                   + "59" + lenStrEMV("Park ' 31 Estacionamento")
                   + "60" + lenStrEMV("SAOPAULO")
                   + "62" + lenStrEMV("05" + lenStrEMV(txId) + txId)
                   + "6304";

        String crc = calcularCRC16EMV(emv);
        return emv + crc;
    }

    private String lenStrEMV(String s) {
        int len = s.length();
        return String.format("%02d", len) + s;
    }

    private String calcularCRC16EMV(String payload) {
        int crc = 0xFFFF;
        byte[] bytes = payload.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
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

    // ─────────────────────── LISTAR VEÍCULOS ───────────────────────

    private JPanel createListarView() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(BG_COLOR);
        wrapper.setBorder(new EmptyBorder(25, 25, 25, 25));

        JLabel title = new JLabel("Ve\u00EDculos Estacionados");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(TEXT_PRIMARY);
        wrapper.add(title, BorderLayout.NORTH);

        String[] columns = {"Placa", "Entrada", "Tempo (min)"};
        veiculosTableModel = new DefaultTableModel(columns, 0) {
            public boolean isCellEditable(int row, int col) { return false; }
        };

        JTable table = new JTable(veiculosTableModel);
        styleTable(table);
        table.getColumnModel().getColumn(0).setPreferredWidth(150);
        table.getColumnModel().getColumn(1).setPreferredWidth(250);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 225), 1));
        scroll.getViewport().setBackground(CARD_BG);

        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(CARD_BG);
        center.setBorder(BorderFactory.createCompoundBorder(
            new EmptyBorder(15, 0, 0, 0),
            BorderFactory.createLineBorder(new Color(230, 230, 235), 1, true)
        ));
        center.add(scroll, BorderLayout.CENTER);

        wrapper.add(center, BorderLayout.CENTER);
        return wrapper;
    }

    private void refreshListarView() {
        veiculosTableModel.setRowCount(0);
        for (Veiculo v : service.listarVeiculosEstacionados()) {
            long minutos = v.getTempoEstacionado() / (1000 * 60);
            veiculosTableModel.addRow(new Object[]{
                v.getPlaca(),
                dateFormat.format(new Date(v.getHoraEntrada())),
                minutos
            });
        }
    }

    // ─────────────────────── HISTÓRICO ───────────────────────

    private JPanel createHistoricoView() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(BG_COLOR);
        wrapper.setBorder(new EmptyBorder(25, 25, 25, 25));

        JLabel title = new JLabel("Hist\u00F3rico de Transa\u00E7\u00F5es");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(TEXT_PRIMARY);
        wrapper.add(title, BorderLayout.NORTH);

        String[] columns = {"Placa", "Entrada", "Sa\u00EDda", "Tarifa", "Valor Pago"};
        transacoesTableModel = new DefaultTableModel(columns, 0) {
            public boolean isCellEditable(int row, int col) { return false; }
        };

        JTable table = new JTable(transacoesTableModel);
        styleTable(table);
        table.getColumnModel().getColumn(0).setPreferredWidth(120);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(2).setPreferredWidth(200);
        table.getColumnModel().getColumn(3).setPreferredWidth(100);
        table.getColumnModel().getColumn(4).setPreferredWidth(100);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 225), 1));
        scroll.getViewport().setBackground(CARD_BG);

        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(CARD_BG);
        center.setBorder(BorderFactory.createCompoundBorder(
            new EmptyBorder(15, 0, 0, 0),
            BorderFactory.createLineBorder(new Color(230, 230, 235), 1, true)
        ));
        center.add(scroll, BorderLayout.CENTER);

        wrapper.add(center, BorderLayout.CENTER);
        return wrapper;
    }

    private void refreshHistoricoView() {
        transacoesTableModel.setRowCount(0);
        for (Transacao t : service.getTransacoes()) {
            transacoesTableModel.addRow(new Object[]{
                t.getPlaca(),
                dateFormat.format(new Date(t.getHoraEntrada())),
                dateFormat.format(new Date(t.getHoraSaida())),
                "R$ " + moneyFormat.format(t.getTarifaCobrada()),
                "R$ " + moneyFormat.format(t.getValorPago())
            });
        }
    }

    // ─────────────────────── RELATÓRIO ───────────────────────

    private JPanel createRelatorioView() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(BG_COLOR);
        wrapper.setBorder(new EmptyBorder(25, 25, 25, 25));

        JLabel title = new JLabel("Relat\u00F3rio de Receita");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(TEXT_PRIMARY);
        wrapper.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridBagLayout());
        center.setBackground(BG_COLOR);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(12, 12, 12, 12);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JPanel card1 = createReportCard("\uD83D\uDE97", "Ve\u00EDculos Atendidos", "0", PRIMARY_MID);
        JPanel card2 = createReportCard("\uD83D\uDEE3", "Vagas Ocupadas", "0", PRIMARY_LIGHT);
        JPanel card3 = createReportCard("\uD83D\uDCB5", "Receita Total", "R$ 0,00", SUCCESS);
        JPanel card4 = createReportCard("\u2699", "Tarifa por Hora", "R$ 5,00", new Color(155, 89, 182));

        gbc.gridx = 0; gbc.gridy = 0;
        center.add(card1, gbc);
        gbc.gridx = 1;
        center.add(card2, gbc);
        gbc.gridx = 0; gbc.gridy = 1;
        center.add(card3, gbc);
        gbc.gridx = 1;
        center.add(card4, gbc);

        wrapper.add(center, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel createReportCard(String icon, String label, String value, Color accent) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
            new EmptyBorder(20, 25, 20, 25),
            BorderFactory.createLineBorder(new Color(230, 230, 235), 1, true)
        ));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setBackground(CARD_BG);
        JLabel iconLabel = new JLabel(icon);
        iconLabel.setFont(new Font("Segoe UI", Font.PLAIN, 24));
        JLabel titleLabel = new JLabel("  " + label);
        titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        titleLabel.setForeground(TEXT_SECONDARY);
        topRow.add(iconLabel, BorderLayout.WEST);
        topRow.add(titleLabel, BorderLayout.CENTER);

        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 30));
        valueLabel.setForeground(accent);
        valueLabel.setName("reportValue");

        card.add(topRow, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);

        return card;
    }

    private void refreshRelatorioView() {
        Container wrapper = (Container) contentPanel.getComponent(5);
        JPanel center = (JPanel) ((BorderLayout)wrapper.getLayout()).getLayoutComponent(BorderLayout.CENTER);
        Component[] cards = center.getComponents();
        if (cards.length >= 4) {
            updateReportCardValue((JPanel) cards[0], String.valueOf(service.getTotalVeiculosAtendidos()));
            updateReportCardValue((JPanel) cards[1], String.valueOf(service.getVagasOcupadas()));
            updateReportCardValue((JPanel) cards[2], "R$ " + moneyFormat.format(service.getReceitaTotal()));
            updateReportCardValue((JPanel) cards[3], "R$ " + moneyFormat.format(CalculadoraTarifa.getTarifaHora()));
        }
    }

    private void updateReportCardValue(JPanel card, String value) {
        for (Component c : card.getComponents()) {
            if (c instanceof JLabel && "reportValue".equals(((JLabel)c).getName())) {
                ((JLabel) c).setText(value);
            }
        }
    }

    // ─────────────────────── ALTERAR TARIFA ───────────────────────

    private JPanel createTarifaView() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(BG_COLOR);
        wrapper.setBorder(new EmptyBorder(25, 25, 25, 25));

        JLabel title = new JLabel("Alterar Tarifa");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(TEXT_PRIMARY);
        wrapper.add(title, BorderLayout.NORTH);

        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setBackground(BG_COLOR);

        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
            new EmptyBorder(30, 30, 30, 30),
            BorderFactory.createLineBorder(new Color(230, 230, 235), 1, true)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel currentLabel = new JLabel("Tarifa atual: R$ " + moneyFormat.format(CalculadoraTarifa.getTarifaHora()) + "/hora");
        currentLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        currentLabel.setForeground(PRIMARY_MID);
        currentLabel.setName("currentRateLabel");
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        card.add(currentLabel, gbc);

        JLabel newLabel = new JLabel("Nova tarifa por hora (R$):");
        newLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        gbc.gridy = 1; gbc.gridwidth = 2;
        card.add(newLabel, gbc);

        tarifaField = new JTextField(10);
        tarifaField.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        tarifaField.setPreferredSize(new Dimension(200, 38));
        tarifaField.addActionListener(e -> alterarTarifa());
        gbc.gridy = 2; gbc.gridwidth = 1;
        card.add(tarifaField, gbc);

        JButton alterarBtn = new JButton("Alterar Tarifa");
        styleButton(alterarBtn, PRIMARY_MID, Color.WHITE);
        alterarBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        alterarBtn.addActionListener(e -> alterarTarifa());
        gbc.gridx = 1; gbc.insets = new Insets(8, 0, 8, 8);
        card.add(alterarBtn, gbc);

        centerWrapper.add(card);
        wrapper.add(centerWrapper, BorderLayout.CENTER);

        return wrapper;
    }

    private void alterarTarifa() {
        String valorStr = tarifaField.getText().trim().replace(",", ".");
        try {
            double novaTarifa = Double.parseDouble(valorStr);
            service.alterarTarifa(novaTarifa);
            JOptionPane.showMessageDialog(frame,
                "Tarifa alterada para R$ " + moneyFormat.format(novaTarifa) + "/hora",
                "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            tarifaField.setText("");
            updateStatusBar();
            updateTarifaLabel();
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(frame, "Valor inv\u00E1lido!", "Erro", JOptionPane.ERROR_MESSAGE);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(frame, e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateTarifaLabel() {
        Container wrapper = (Container) contentPanel.getComponent(6);
        JPanel centerWrapper = (JPanel) ((BorderLayout)wrapper.getLayout()).getLayoutComponent(BorderLayout.CENTER);
        JPanel card = (JPanel) centerWrapper.getComponent(0);
        for (Component c : card.getComponents()) {
            if (c instanceof JLabel && "currentRateLabel".equals(((JLabel)c).getName())) {
                ((JLabel) c).setText("Tarifa atual: R$ " + moneyFormat.format(CalculadoraTarifa.getTarifaHora()) + "/hora");
            }
        }
    }

    // ─────────────────────── STATUS BAR ───────────────────────

    private JPanel createStatusBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 3));
        bar.setBackground(PRIMARY_DARK);

        statusOcupadas = new JLabel("\uD83D\uDE97  Vagas ocupadas: " + service.getVagasOcupadas());
        statusOcupadas.setForeground(Color.WHITE);
        statusOcupadas.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        statusTarifa = new JLabel("\u2699  Tarifa: R$ " + moneyFormat.format(CalculadoraTarifa.getTarifaHora()) + "/hora");
        statusTarifa.setForeground(Color.WHITE);
        statusTarifa.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        bar.add(statusOcupadas);
        bar.add(statusTarifa);
        return bar;
    }

    private void updateStatusBar() {
        statusOcupadas.setText("\uD83D\uDE97  Vagas ocupadas: " + service.getVagasOcupadas());
        statusTarifa.setText("\u2699  Tarifa: R$ " + moneyFormat.format(CalculadoraTarifa.getTarifaHora()) + "/hora");
    }

    // ─────────────────────── UTILS ───────────────────────

    private void styleButton(JButton btn, Color bg, Color fg) {
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(10, 20, 10, 20));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void styleTable(JTable table) {
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setRowHeight(32);
        table.setShowGrid(true);
        table.setGridColor(new Color(235, 235, 240));
        table.setBackground(CARD_BG);
        table.setSelectionBackground(new Color(220, 230, 245));
        table.setSelectionForeground(TEXT_PRIMARY);

        JTableHeader header = table.getTableHeader();
        header.setBackground(PRIMARY_MID);
        header.setForeground(Color.WHITE);
        header.setFont(new Font("Segoe UI", Font.BOLD, 13));
        header.setPreferredSize(new Dimension(0, 36));
    }
}
