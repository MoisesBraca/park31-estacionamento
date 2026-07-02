package com.estacionamento;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;

public class TelaBloqueioDialog extends JDialog {
    private final EstacionamentoRepository repository = new EstacionamentoRepository();
    private final String hardwareId;
    private final String deviceName;
    private JLabel statusLabel;

    public TelaBloqueioDialog(JFrame parent) {
        super(parent, "PARK ' 31 - Licenciamento Requerido", true);
        this.hardwareId = LicenseManager.getHardwareId();
        this.deviceName = LicenseManager.getDeviceName();

        // Registra automaticamente o terminal na base para aparecer no painel
        repository.registrarTerminal(hardwareId, deviceName, "Windows Desktop");

        initComponents();
    }

    private void initComponents() {
        setSize(550, 420);
        setResizable(false);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE); // Impede fechar pelo X

        // Impede Alt+F4
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.out.println("Por favor, ative a licença para usar o sistema.");
            }
        });

        JPanel mainPanel = new JPanel();
        mainPanel.setBackground(new Color(15, 15, 26));
        mainPanel.setLayout(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(30, 40, 30, 40));

        // Topo - Ícone e Título
        JPanel headerPanel = new JPanel();
        headerPanel.setOpaque(false);
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));

        JLabel iconLabel = new JLabel("🅿️", SwingConstants.CENTER);
        iconLabel.setFont(new Font("Segoe UI", Font.PLAIN, 48));
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(iconLabel);

        headerPanel.add(Box.createVerticalStrut(10));

        JLabel titleLabel = new JLabel("PARK ' 31 - SISTEMA BLOQUEADO", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(titleLabel);

        headerPanel.add(Box.createVerticalStrut(5));

        JLabel descLabel = new JLabel("Este dispositivo aguarda aprovação administrativa.", SwingConstants.CENTER);
        descLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        descLabel.setForeground(new Color(160, 160, 180));
        descLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(descLabel);

        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Centro - Detalhes do Terminal
        JPanel centerPanel = new JPanel();
        centerPanel.setOpaque(false);
        centerPanel.setLayout(new GridLayout(4, 1, 10, 10));
        centerPanel.setBorder(new EmptyBorder(25, 10, 25, 10));

        centerPanel.add(criarLinhaInfo("Nome do Aparelho:", deviceName));
        centerPanel.add(criarLinhaInfo("Código do Terminal:", hardwareId));
        centerPanel.add(criarLinhaInfo("Sistema Operacional:", "Windows (Desktop Swing)"));

        String statusAtual = repository.verificarStatusTerminal(hardwareId);
        statusLabel = new JLabel(statusAtual, SwingConstants.RIGHT);
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        atualizarEstiloStatus(statusAtual);

        JPanel statusRow = new JPanel(new BorderLayout());
        statusRow.setOpaque(false);
        JLabel lbl = new JLabel("Status da Ativação:");
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lbl.setForeground(new Color(200, 200, 210));
        statusRow.add(lbl, BorderLayout.WEST);
        statusRow.add(statusLabel, BorderLayout.EAST);
        centerPanel.add(statusRow);

        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // Botão de Ação no Rodapé
        JPanel footerPanel = new JPanel(new BorderLayout(15, 0));
        footerPanel.setOpaque(false);

        JButton btnVerificar = new JButton("Verificar Liberação");
        btnVerificar.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnVerificar.setBackground(new Color(88, 86, 214));
        btnVerificar.setForeground(Color.WHITE);
        btnVerificar.setFocusPainted(false);
        btnVerificar.setPreferredSize(new Dimension(0, 45));
        btnVerificar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        btnVerificar.addActionListener(e -> {
            String status = repository.verificarStatusTerminal(hardwareId);
            atualizarEstiloStatus(status);
            if (status.equals("ATIVO")) {
                long exp = repository.obterExpiracaoTerminal(hardwareId);
                if (exp == 0 || System.currentTimeMillis() < exp) {
                    JOptionPane.showMessageDialog(this, "Aparelho liberado com sucesso!", "Park ' 31", JOptionPane.INFORMATION_MESSAGE);
                    dispose(); // Fecha o diálogo de bloqueio e libera o app
                } else {
                    JOptionPane.showMessageDialog(this, "Esta licença está expirada. Renove sua assinatura.", "Acesso Negado", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this, "Aparelho ainda PENDENTE ou BLOQUEADO pelo administrador.", "Aguardando Aprovação", JComponent.WHEN_IN_FOCUSED_WINDOW);
            }
        });

        JButton btnSair = new JButton("Fechar Sistema");
        btnSair.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btnSair.setBackground(new Color(40, 40, 50));
        btnSair.setForeground(new Color(220, 220, 220));
        btnSair.setFocusPainted(false);
        btnSair.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnSair.addActionListener(e -> System.exit(0));

        footerPanel.add(btnVerificar, BorderLayout.CENTER);
        footerPanel.add(btnSair, BorderLayout.EAST);

        mainPanel.add(footerPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private JPanel criarLinhaInfo(String label, String value) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);

        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lbl.setForeground(new Color(200, 200, 210));

        JLabel val = new JLabel(value, SwingConstants.RIGHT);
        val.setFont(new Font("Segoe UI", Font.BOLD, 13));
        val.setForeground(Color.WHITE);

        row.add(lbl, BorderLayout.WEST);
        row.add(val, BorderLayout.EAST);
        return row;
    }

    private void atualizarEstiloStatus(String status) {
        statusLabel.setText(status);
        if (status.equals("ATIVO")) {
            statusLabel.setForeground(new Color(46, 204, 113));
        } else if (status.equals("BLOQUEADO")) {
            statusLabel.setForeground(new Color(231, 76, 60));
        } else {
            statusLabel.setForeground(new Color(241, 196, 15));
        }
    }
}
