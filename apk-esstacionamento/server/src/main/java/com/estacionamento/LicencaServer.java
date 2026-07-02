package com.estacionamento;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
public class LicencaServer {
    private static HttpServer server;
    private static final EstacionamentoRepository repository = new EstacionamentoRepository();
    private static final String SENHA_ADMIN = "estacionamento31";
    private static final String TOKEN_ADMIN = "token_" + UUID.randomUUID().toString().substring(0, 8);
    private static final Map<String, Long> pixCriados = new java.util.concurrent.ConcurrentHashMap<>();
    private static final List<String> auditLogs = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        // Railway define a porta na variável de ambiente PORT
        String portEnv = System.getenv("PORT");
        int porta = 8080;
        if (portEnv != null && !portEnv.isEmpty()) {
            porta = Integer.parseInt(portEnv);
        }
        iniciar(porta);
        System.out.println("Servidor de Licenciamento Park ' 31 rodando na porta " + porta);
        try {
            Object lock = new Object();
            synchronized (lock) {
                lock.wait();
            }
        } catch (InterruptedException ignored) {}
    }

    public static void iniciar(int porta) {
        if (server != null) return;
        try {
            server = HttpServer.create(new InetSocketAddress(porta), 0);
            
            // Endpoint principal: Painel Administrativo Web
            server.createContext("/", new AdminHandler());
            
            server.createContext("/api/devices", new DevicesHandler());
            server.createContext("/api/approve", new ApproveHandler());
            server.createContext("/api/block", new BlockHandler());
            server.createContext("/api/check", new CheckHandler());
            server.createContext("/api/login", new LoginHandler());
            server.createContext("/api/update-config", new UpdateConfigHandler());
            server.createContext("/api/delete", new DeleteHandler());
            server.createContext("/api/send-config", new SendConfigHandler());
            server.createContext("/api/pix/create", new PixCreateHandler());
            server.createContext("/api/pix/status", new PixStatusHandler());
            server.createContext("/api/audit/sync", new AuditSyncHandler());
            server.createContext("/api/audit-logs", new AuditLogsGetHandler());
            server.createContext("/api/transacoes/sync", new TransacoesSyncHandler());
            server.createContext("/api/mensalistas/sync", new MensalistasSyncHandler());
            server.createContext("/api/mensalistas/add", new MensalistaAddHandler());
            server.createContext("/api/mensalistas/edit", new MensalistaEditHandler());
            server.createContext("/api/mensalistas/delete", new MensalistaDeleteHandler());
            server.createContext("/api/mensalistas/list", new MensalistaListHandler());
            server.createContext("/api/faturamento", new FaturamentoGetHandler());
            server.createContext("/pagar", new AutoatendimentoPagarHandler());
            server.createContext("/api/autoatendimento/status", new AutoatendimentoStatusHandler());

            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool()); // Execução multi-thread assíncrona
            server.start();
            System.out.println("Servidor de Licenciamento Park ' 31 iniciado na porta " + porta);
        } catch (IOException e) {
            System.err.println("Erro ao iniciar o Servidor de Licenciamento: " + e.getMessage());
        }
    }

    public static void parar() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    // Handler para servir a página HTML do Painel Admin
    private static class AdminHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String html = obterHtmlDashboard();
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static boolean autenticarRequisicao(HttpExchange exchange) {
        String token = exchange.getRequestHeaders().getFirst("X-Admin-Token");
        return TOKEN_ADMIN.equals(token);
    }

    private static void recusarNaoAutorizado(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = "{\"error\":\"Não autorizado\"}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(401, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // Handler para login
    private static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            Map<String, String> params = parseRequestBody(exchange.getRequestBody());
            String senha = params.get("senha");
            String response;
            if (SENHA_ADMIN.equals(senha)) {
                response = "{\"success\":true,\"token\":\"" + TOKEN_ADMIN + "\"}";
            } else {
                response = "{\"success\":false}";
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // Handler para retornar todos os dispositivos em formato JSON
    private static class DevicesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");

            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            if (!autenticarRequisicao(exchange)) {
                recusarNaoAutorizado(exchange);
                return;
            }

            List<TerminalInfo> terminais = repository.listarTerminais();
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < terminais.size(); i++) {
                TerminalInfo t = terminais.get(i);
                json.append(String.format(Locale.US,
                    "{\"hardwareId\":\"%s\",\"nomeAparelho\":\"%s\",\"soTipo\":\"%s\",\"dataRegistro\":%d,\"dataExpiracao\":%d,\"status\":\"%s\",\"nomeCliente\":\"%s\",\"tarifaHora\":%.2f,\"vagasCarro\":%d,\"vagasMoto\":%d,"
                    + "\"nomeClientePendente\":\"%s\",\"tarifaHoraPendente\":%.2f,\"vagasCarroPendente\":%d,\"vagasMotoPendente\":%d,\"diasLicencaPendente\":%d,\"ultimoPing\":%d}",
                    t.getHardwareId(), t.getNomeAparelho(), t.getSoTipo(), t.getDataRegistro(), t.getDataExpiracao(), t.getStatus(),
                    t.getNomeCliente(), t.getTarifaHora(), t.getVagasCarro(), t.getVagasMoto(),
                    t.getNomeClientePendente() != null ? t.getNomeClientePendente() : "",
                    t.getTarifaHoraPendente(), t.getVagasCarroPendente(), t.getVagasMotoPendente(), t.getDiasLicencaPendente(),
                    t.getUltimoPing()
                ));
                if (i < terminais.size() - 1) json.append(",");
            }
            json.append("]");

            byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // Handler para aprovar um terminal
    private static class ApproveHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            if (!autenticarRequisicao(exchange)) {
                recusarNaoAutorizado(exchange);
                return;
            }

            Map<String, String> params = parseRequestBody(exchange.getRequestBody());
            String hardwareId = params.get("hardwareId");
            String diasStr = params.get("dias");

            if (hardwareId != null && !hardwareId.trim().isEmpty()) {
                int dias = diasStr != null ? Integer.parseInt(diasStr) : 30;
                long exp = System.currentTimeMillis() + ((long) dias * 24 * 60 * 60 * 1000);
                repository.aprovarTerminal(hardwareId, exp);

                String response = "{\"success\":true}";
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                exchange.sendResponseHeaders(400, -1);
            }
        }
    }

    // Handler para bloquear um terminal
    private static class BlockHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            if (!autenticarRequisicao(exchange)) {
                recusarNaoAutorizado(exchange);
                return;
            }

            Map<String, String> params = parseRequestBody(exchange.getRequestBody());
            String hardwareId = params.get("hardwareId");

            if (hardwareId != null && !hardwareId.trim().isEmpty()) {
                repository.bloquearTerminal(hardwareId);
                String response = "{\"success\":true}";
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                exchange.sendResponseHeaders(400, -1);
            }
        }
    }

    // Handler para verificação e registro automático de pings
    private static class CheckHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            Map<String, String> params = parseRequestBody(exchange.getRequestBody());
            String hardwareId = params.get("hardwareId");
            String nomeAparelho = params.get("nomeAparelho");
            String soTipo = params.get("soTipo");

            if (hardwareId == null || hardwareId.trim().isEmpty()) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            // Garante auto-registro
            repository.registrarTerminal(hardwareId, nomeAparelho != null ? nomeAparelho : "Desconhecido", soTipo != null ? soTipo : "Android");
            repository.atualizarUltimoPing(hardwareId);

            String status = repository.verificarStatusTerminal(hardwareId);
            long expiracao = repository.obterExpiracaoTerminal(hardwareId);

            // Verifica se a licença já expirou por tempo
            if (status.equals("ATIVO") && expiracao > 0 && System.currentTimeMillis() > expiracao) {
                repository.bloquearTerminal(hardwareId);
                status = "BLOQUEADO";
            }

            TerminalInfo info = repository.obterTerminal(hardwareId);
            String response;
            if (info != null) {
                response = String.format(Locale.US,
                    "{\"status\":\"%s\",\"expiracao\":%d,\"tarifaHora\":%.2f,\"vagasCarro\":%d,\"vagasMoto\":%d}",
                    status, expiracao, info.getTarifaHora(), info.getVagasCarro(), info.getVagasMoto()
                );
            } else {
                response = String.format(Locale.US,
                    "{\"status\":\"%s\",\"expiracao\":%d,\"tarifaHora\":5.00,\"vagasCarro\":20,\"vagasMoto\":5}",
                    status, expiracao
                );
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // Handler para atualizar configurações de um terminal
    private static class UpdateConfigHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            if (!autenticarRequisicao(exchange)) {
                recusarNaoAutorizado(exchange);
                return;
            }

            Map<String, String> params = parseRequestBody(exchange.getRequestBody());
            String hardwareId = params.get("hardwareId");
            String nomeCliente = params.get("nomeCliente");
            String tarifaHoraStr = params.get("tarifaHora");
            String vagasCarroStr = params.get("vagasCarro");
            String vagasMotoStr = params.get("vagasMoto");
            String diasLicencaStr = params.get("diasLicenca");

            if (hardwareId != null && !hardwareId.trim().isEmpty()) {
                double tarifaHora = tarifaHoraStr != null ? Double.parseDouble(tarifaHoraStr) : 5.0;
                int vagasCarro = vagasCarroStr != null ? Integer.parseInt(vagasCarroStr) : 20;
                int vagasMoto = vagasMotoStr != null ? Integer.parseInt(vagasMotoStr) : 5;
                int diasLicenca = diasLicencaStr != null ? Integer.parseInt(diasLicencaStr) : 30;
                if (diasLicenca < 30) diasLicenca = 30;
                if (tarifaHora < 0) tarifaHora = 0;
                if (vagasCarro < 0) vagasCarro = 0;
                if (vagasMoto < 0) vagasMoto = 0;
                
                repository.atualizarConfigPendenteTerminal(hardwareId, nomeCliente != null ? nomeCliente : "", tarifaHora, vagasCarro, vagasMoto, diasLicenca);

                String response = "{\"success\":true}";
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                exchange.sendResponseHeaders(400, -1);
            }
        }
    }

    // Handler para excluir um terminal
    private static class DeleteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            if (!autenticarRequisicao(exchange)) {
                recusarNaoAutorizado(exchange);
                return;
            }

            Map<String, String> params = parseRequestBody(exchange.getRequestBody());
            String hardwareId = params.get("hardwareId");

            if (hardwareId != null && !hardwareId.trim().isEmpty()) {
                repository.excluirTerminal(hardwareId);
                String response = "{\"success\":true}";
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                exchange.sendResponseHeaders(400, -1);
            }
        }
    }

    // Handler para enviar configurações pendentes para o terminal ativo
    private static class SendConfigHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            if (!autenticarRequisicao(exchange)) {
                recusarNaoAutorizado(exchange);
                return;
            }

            Map<String, String> params = parseRequestBody(exchange.getRequestBody());
            String hardwareId = params.get("hardwareId");

            if (hardwareId != null && !hardwareId.trim().isEmpty()) {
                repository.enviarConfigTerminal(hardwareId);
                String response = "{\"success\":true}";
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                exchange.sendResponseHeaders(400, -1);
            }
        }
    }

    // Handler para criar Pix Dinâmico
    private static class PixCreateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            Map<String, String> params = parseRequestBody(exchange.getRequestBody());
            String placa = params.get("placa");
            String valorStr = params.get("valor");
            double valor = valorStr != null ? Double.parseDouble(valorStr) : 0.0;

            String txid = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            pixCriados.put(txid, System.currentTimeMillis());

            // Cria um payload Pix fictício mas formalmente válido para exibição do QR Code
            String payload = "00020101021226830014br.gov.bcb.pix0136suporte@estacionamento31.com.br52040000530398654" 
                + String.format(Locale.US, "%02d%.2f", String.format(Locale.US, "%.2f", valor).length(), valor) 
                + "5802BR5925Park 31 Estacionamento6009SAOPAULO62290525" + txid + "6304";
            
            // Simular cálculo do CRC16 para o mock
            int crc = 0xFFFF;
            byte[] bytesPayload = payload.getBytes(StandardCharsets.ISO_8859_1);
            for (byte b : bytesPayload) {
                crc ^= (b & 0xFF) << 8;
                for (int i = 0; i < 8; i++) {
                    if ((crc & 0x8000) != 0) {
                        crc = (crc << 1) ^ 0x1021;
                    } else {
                        crc <<= 1;
                    }
                }
            }
            String crcStr = String.format("%04X", crc & 0xFFFF);
            payload = payload + crcStr;

            String response = String.format(Locale.US, "{\"success\":true,\"txid\":\"%s\",\"payload\":\"%s\"}", txid, payload);
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // Handler para checar status de Pix Dinâmico (simulador de sandbox)
    private static class PixStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            Map<String, String> params = parseRequestBody(exchange.getRequestBody());
            String txid = params.get("txid");
            String status = "PENDENTE";

            if (txid != null && pixCriados.containsKey(txid)) {
                long criadoEm = pixCriados.get(txid);
                // Se passou mais de 6 segundos, simular status pago (PAID) para demonstração automática
                if (System.currentTimeMillis() - criadoEm > 6000) {
                    status = "APROVADO";
                    try {
                        EstacionamentoRepository.AutoatendimentoInfo info = repository.obterAutoatendimentoPorTxid(txid);
                        if (info != null && "PENDENTE".equals(info.getStatus())) {
                            repository.atualizarStatusAutoatendimentoPorTxid(txid, "PAGO");
                            auditLogs.add(String.format("[%s] [PAGAMENTO] Autoatendimento PAGO via Pix placa %s: R$ %.2f",
                                    new SimpleDateFormat("dd/MM HH:mm:ss").format(new Date()), info.getPlaca(), info.getValorPago()));
                        }
                    } catch (Exception ignored) {}
                }
            }

            String response = String.format("{\"status\":\"%s\"}", status);
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // Handler para receber e sincronizar logs de auditoria dos terminais
    private static class AuditSyncHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            Map<String, String> params = parseRequestBody(exchange.getRequestBody());
            String operador = params.get("operador");
            String acao = params.get("acao");
            String timestampStr = params.get("timestamp");
            String detalhes = params.get("detalhes");

            if (operador != null && acao != null) {
                long ts = timestampStr != null ? Long.parseLong(timestampStr) : System.currentTimeMillis();
                String dataStr = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date(ts));
                String logLine = String.format("[%s] Operador: %s | Ação: %s | Detalhes: %s", dataStr, operador, acao, detalhes != null ? detalhes : "");
                
                auditLogs.add(0, logLine); // Adiciona no início da lista (mais recentes primeiro)
                if (auditLogs.size() > 100) {
                    auditLogs.remove(auditLogs.size() - 1);
                }
            }

            String response = "{\"success\":true}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // Handler para retornar todos os logs de auditoria para o painel web admin
    private static class AuditLogsGetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");

            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            if (!autenticarRequisicao(exchange)) {
                recusarNaoAutorizado(exchange);
                return;
            }

            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < auditLogs.size(); i++) {
                // Escapar aspas duplas da linha do log para que o JSON fique válido
                String escapedLog = auditLogs.get(i).replace("\"", "\\\"");
                json.append("\"").append(escapedLog).append("\"");
                if (i < auditLogs.size() - 1) json.append(",");
            }
            json.append("]");

            byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // Handler para sincronizar transações individuais enviadas pelos terminais
    private static class TransacoesSyncHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            Map<String, String> params = parseRequestBody(exchange.getRequestBody());
            String placa = params.get("placa");
            String entradaStr = params.get("entrada");
            String saidaStr = params.get("saida");
            String valorPagoStr = params.get("valorPago");
            String tarifaCobradaStr = params.get("tarifaCobrada");
            String hardwareId = params.get("hardwareId");

            if (placa != null && entradaStr != null && saidaStr != null && hardwareId != null) {
                long entrada = Long.parseLong(entradaStr);
                long saida = Long.parseLong(saidaStr);
                double valorPago = Double.parseDouble(valorPagoStr);
                double tarifaCobrada = Double.parseDouble(tarifaCobradaStr);

                repository.salvarTransacaoSincronizada(new Transacao(placa, entrada, saida, valorPago, tarifaCobrada, hardwareId));
                
                String response = "{\"success\":true}";
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                exchange.sendResponseHeaders(400, -1);
            }
        }
    }

    // Handler para retornar mensalistas em lote para sincronização local nos celulares
    private static class MensalistasSyncHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");

            if (!exchange.getRequestMethod().equalsIgnoreCase("GET") && !exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            List<MensalistaInfo> mensalistas = repository.listarMensalistas();
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < mensalistas.size(); i++) {
                MensalistaInfo m = mensalistas.get(i);
                json.append(String.format(Locale.US,
                    "{\"placa\":\"%s\",\"nomeCliente\":\"%s\",\"telefone\":\"%s\",\"vencimento\":%d,\"status\":\"%s\"}",
                    m.getPlaca(), m.getNomeCliente(), m.getTelefone(), m.getVencimento(), m.getStatus()
                ));
                if (i < mensalistas.size() - 1) json.append(",");
            }
            json.append("]");

            byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // Handler para cadastrar mensalistas via web console
    private static class MensalistaAddHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            if (!autenticarRequisicao(exchange)) {
                recusarNaoAutorizado(exchange);
                return;
            }

            Map<String, String> params = parseRequestBody(exchange.getRequestBody());
            String placa = params.get("placa");
            String nome = params.get("nome");
            String telefone = params.get("telefone");
            String vencimentoStr = params.get("vencimento");

            if (placa != null && nome != null && vencimentoStr != null) {
                long vencimento = Long.parseLong(vencimentoStr);
                repository.adicionarMensalista(placa, nome, telefone != null ? telefone : "", vencimento);
                
                String response = "{\"success\":true}";
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                exchange.sendResponseHeaders(400, -1);
            }
        }
    }

    // Handler para editar mensalistas via web console
    private static class MensalistaEditHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            if (!autenticarRequisicao(exchange)) {
                recusarNaoAutorizado(exchange);
                return;
            }

            Map<String, String> params = parseRequestBody(exchange.getRequestBody());
            String placa = params.get("placa");
            String status = params.get("status");
            String vencimentoStr = params.get("vencimento");

            if (placa != null && status != null && vencimentoStr != null) {
                long vencimento = Long.parseLong(vencimentoStr);
                repository.atualizarMensalista(placa, status, vencimento);
                
                String response = "{\"success\":true}";
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                exchange.sendResponseHeaders(400, -1);
            }
        }
    }

    // Handler para excluir mensalistas via web console
    private static class MensalistaDeleteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            if (!autenticarRequisicao(exchange)) {
                recusarNaoAutorizado(exchange);
                return;
            }

            Map<String, String> params = parseRequestBody(exchange.getRequestBody());
            String placa = params.get("placa");

            if (placa != null) {
                repository.excluirMensalista(placa);
                
                String response = "{\"success\":true}";
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                exchange.sendResponseHeaders(400, -1);
            }
        }
    }

    // Handler para listar mensalistas para a console web
    private static class MensalistaListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");

            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            if (!autenticarRequisicao(exchange)) {
                recusarNaoAutorizado(exchange);
                return;
            }

            List<MensalistaInfo> mensalistas = repository.listarMensalistas();
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < mensalistas.size(); i++) {
                MensalistaInfo m = mensalistas.get(i);
                json.append(String.format(Locale.US,
                    "{\"id\":%d,\"placa\":\"%s\",\"nomeCliente\":\"%s\",\"telefone\":\"%s\",\"vencimento\":%d,\"status\":\"%s\"}",
                    m.getId(), m.getPlaca(), m.getNomeCliente(), m.getTelefone(), m.getVencimento(), m.getStatus()
                ));
                if (i < mensalistas.size() - 1) json.append(",");
            }
            json.append("]");

            byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // Handler para buscar todas as transações para os gráficos e estatísticas
    private static class FaturamentoGetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");

            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            if (!autenticarRequisicao(exchange)) {
                recusarNaoAutorizado(exchange);
                return;
            }

            List<Transacao> transacoes = repository.listarTodasTransacoes();
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < transacoes.size(); i++) {
                Transacao t = transacoes.get(i);
                json.append(String.format(Locale.US,
                    "{\"placa\":\"%s\",\"horaEntrada\":%d,\"horaSaida\":%d,\"valorPago\":%.2f,\"tarifaCobrada\":%.2f,\"hardwareId\":\"%s\"}",
                    t.getPlaca(), t.getHoraEntrada(), t.getHoraSaida(), t.getValorPago(), t.getTarifaCobrada(), t.getHardwareId() != null ? t.getHardwareId() : ""
                ));
                if (i < transacoes.size() - 1) json.append(",");
            }
            json.append("]");

            byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // Método utilitário para ler parâmetros JSON/Form do request body
    private static Map<String, String> parseRequestBody(InputStream is) throws IOException {
        Map<String, String> map = new HashMap<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        String body = sb.toString().trim();
        
        if (body.startsWith("{") && body.endsWith("}")) {
            // Parsing JSON robusto - lida com valores contendo vírgulas, dois-pontos e aspas
            int i = 1; // skip '{'
            while (i < body.length() - 1) {
                // Pular espaços em branco
                while (i < body.length() - 1 && Character.isWhitespace(body.charAt(i))) i++;
                if (i >= body.length() - 1) break;
                
                // Ler key
                if (body.charAt(i) != '"') break;
                i++; // skip opening quote of key
                int keyStart = i;
                while (i < body.length() - 1 && body.charAt(i) != '"') {
                    if (body.charAt(i) == '\\') i++; // skip escaped char
                    i++;
                }
                String key = body.substring(keyStart, i);
                i++; // skip closing quote of key
                
                // Pular ':'
                while (i < body.length() - 1 && Character.isWhitespace(body.charAt(i))) i++;
                if (body.charAt(i) != ':') break;
                i++;
                while (i < body.length() - 1 && Character.isWhitespace(body.charAt(i))) i++;
                
                // Ler value
                String value;
                if (body.charAt(i) == '"') {
                    i++; // skip opening quote of value
                    int valStart = i;
                    while (i < body.length() - 1 && body.charAt(i) != '"') {
                        if (body.charAt(i) == '\\') i++;
                        i++;
                    }
                    value = body.substring(valStart, i);
                    i++; // skip closing quote of value
                } else {
                    // Valor numérico ou booleano
                    int valStart = i;
                    while (i < body.length() - 1 && body.charAt(i) != ',' && body.charAt(i) != '}') i++;
                    value = body.substring(valStart, i).trim();
                }
                
                map.put(key, value);
                
                // Pular ','
                while (i < body.length() - 1 && Character.isWhitespace(body.charAt(i))) i++;
                if (body.charAt(i) == ',') i++;
            }
        } else {
            // Parsing de form urlencoded
            String[] pairs = body.split("&");
            for (String pair : pairs) {
                String[] kv = pair.split("=");
                if (kv.length == 2) {
                    map.put(URLDecoder.decode(kv[0], "UTF-8"), URLDecoder.decode(kv[1], "UTF-8"));
                }
            }
        }
        return map;
    }

    // HTML/CSS/JS Embutido para renderizar a interface de controle no navegador
    private static String obterHtmlDashboard() {
        return obterHtmlDashboardPart1() + obterHtmlDashboardPart2();
    }

    private static String obterHtmlDashboardPart1() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"pt-BR\">\n");
        sb.append("<head>\n");
        sb.append("    <meta charset=\"UTF-8\">\n");
        sb.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("    <title>Park ' 31 - Console Admin SaaS</title>\n");
        sb.append("    <link href=\"https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600;700;800&display=swap\" rel=\"stylesheet\">\n");
        sb.append("    <link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">\n");
        sb.append("    <script src=\"https://cdn.jsdelivr.net/npm/chart.js\"></script>\n");
        sb.append("    <style>\n");
        sb.append("        :root {\n");
        sb.append("            --primary: #6366f1;\n");
        sb.append("            --primary-hover: #4f46e5;\n");
        sb.append("            --primary-glow: rgba(99, 102, 241, 0.3);\n");
        sb.append("            --success: #10b981;\n");
        sb.append("            --success-glow: rgba(16, 185, 129, 0.2);\n");
        sb.append("            --danger: #ef4444;\n");
        sb.append("            --danger-glow: rgba(239, 68, 68, 0.2);\n");
        sb.append("            --warning: #f59e0b;\n");
        sb.append("            --warning-glow: rgba(245, 158, 11, 0.2);\n");
        sb.append("            --background: #090d16;\n");
        sb.append("            --card-bg: rgba(17, 24, 39, 0.7);\n");
        sb.append("            --border: rgba(255, 255, 255, 0.08);\n");
        sb.append("            --text: #f3f4f6;\n");
        sb.append("            --text-muted: #9ca3af;\n");
        sb.append("            --sidebar-width: 260px;\n");
        sb.append("        }\n");
        sb.append("        * {\n");
        sb.append("            margin: 0; padding: 0; box-sizing: border-box; font-family: 'Outfit', sans-serif;\n");
        sb.append("        }\n");
        sb.append("        body {\n");
        sb.append("            background-color: var(--background);\n");
        sb.append("            background-image: \n");
        sb.append("                radial-gradient(circle at 10% 20%, rgba(99, 102, 241, 0.15) 0%, transparent 40%),\n");
        sb.append("                radial-gradient(circle at 90% 80%, rgba(139, 92, 246, 0.15) 0%, transparent 40%);\n");
        sb.append("            color: var(--text);\n");
        sb.append("            min-height: 100vh;\n");
        sb.append("            overflow-x: hidden;\n");
        sb.append("        }\n");
        sb.append("        /* Login Panel Styling */\n");
        sb.append("        #login-panel {\n");
        sb.append("            display: flex; align-items: center; justify-content: center;\n");
        sb.append("            position: fixed; top: 0; left: 0; right: 0; bottom: 0;\n");
        sb.append("            background: rgba(9, 13, 22, 0.95); z-index: 1000;\n");
        sb.append("            backdrop-filter: blur(12px);\n");
        sb.append("        }\n");
        sb.append("        .login-card {\n");
        sb.append("            background: var(--card-bg);\n");
        sb.append("            border: 1px solid var(--border);\n");
        sb.append("            border-radius: 24px;\n");
        sb.append("            padding: 40px;\n");
        sb.append("            width: 100%; max-width: 420px;\n");
        sb.append("            box-shadow: 0 20px 50px rgba(0, 0, 0, 0.5);\n");
        sb.append("            text-align: center;\n");
        sb.append("            animation: fadeInUp 0.6s ease;\n");
        sb.append("        }\n");
        sb.append("        .login-card i.logo-icon {\n");
        sb.append("            font-size: 48px; color: var(--primary);\n");
        sb.append("            margin-bottom: 16px; filter: drop-shadow(0 0 10px var(--primary-glow));\n");
        sb.append("        }\n");
        sb.append("        .login-card h2 { font-size: 28px; font-weight: 700; margin-bottom: 8px; }\n");
        sb.append("        .login-card p { color: var(--text-muted); font-size: 14px; margin-bottom: 30px; }\n");
        sb.append("        .form-group {\n");
        sb.append("            margin-bottom: 20px; text-align: left;\n");
        sb.append("        }\n");
        sb.append("        .form-group label {\n");
        sb.append("            display: block; font-size: 13px; text-transform: uppercase;\n");
        sb.append("            color: var(--text-muted); font-weight: 600; letter-spacing: 1px; margin-bottom: 8px;\n");
        sb.append("        }\n");
        sb.append("        .form-control {\n");
        sb.append("            width: 100%; padding: 14px 16px;\n");
        sb.append("            background: rgba(255, 255, 255, 0.04);\n");
        sb.append("            border: 1px solid var(--border);\n");
        sb.append("            border-radius: 12px; color: var(--text);\n");
        sb.append("            font-size: 15px; transition: 0.3s;\n");
        sb.append("        }\n");
        sb.append("        .form-control:focus {\n");
        sb.append("            outline: none; border-color: var(--primary);\n");
        sb.append("            background: rgba(255, 255, 255, 0.08);\n");
        sb.append("            box-shadow: 0 0 0 4px var(--primary-glow);\n");
        sb.append("        }\n");
        sb.append("        .btn {\n");
        sb.append("            display: inline-flex; align-items: center; justify-content: center; gap: 8px;\n");
        sb.append("            width: 100%; padding: 14px 20px; border-radius: 12px; font-size: 15px; font-weight: 600;\n");
        sb.append("            cursor: pointer; transition: 0.2s; border: none;\n");
        sb.append("        }\n");
        sb.append("        .btn-primary {\n");
        sb.append("            background: linear-gradient(135deg, var(--primary), #8b5cf6);\n");
        sb.append("            color: white; box-shadow: 0 4px 15px var(--primary-glow);\n");
        sb.append("        }\n");
        sb.append("        .btn-primary:hover {\n");
        sb.append("            transform: translateY(-2px);\n");
        sb.append("            box-shadow: 0 6px 20px rgba(99, 102, 241, 0.5);\n");
        sb.append("        }\n");
        sb.append("        .btn-secondary {\n");
        sb.append("            background: rgba(255, 255, 255, 0.08); color: var(--text);\n");
        sb.append("            border: 1px solid var(--border);\n");
        sb.append("        }\n");
        sb.append("        .btn-secondary:hover {\n");
        sb.append("            background: rgba(255, 255, 255, 0.15);\n");
        sb.append("        }\n");
        sb.append("        /* Main layout styling */\n");
        sb.append("        #app-layout {\n");
        sb.append("            display: flex; min-height: 100vh; opacity: 0; transition: opacity 0.5s ease;\n");
        sb.append("        }\n");
        sb.append("        #app-layout.active { opacity: 1; }\n");
        sb.append("        /* Sidebar */\n");
        sb.append("        .sidebar {\n");
        sb.append("            width: var(--sidebar-width); background: rgba(10, 15, 30, 0.8);\n");
        sb.append("            backdrop-filter: blur(20px); border-right: 1px solid var(--border);\n");
        sb.append("            padding: 30px 20px; display: flex; flex-direction: column;\n");
        sb.append("            position: fixed; height: 100vh; left: 0; top: 0; z-index: 100;\n");
        sb.append("        }\n");
        sb.append("        .brand {\n");
        sb.append("            display: flex; align-items: center; gap: 12px; margin-bottom: 40px; padding: 0 10px;\n");
        sb.append("        }\n");
        sb.append("        .brand i { font-size: 28px; color: var(--primary); filter: drop-shadow(0 0 8px var(--primary-glow)); }\n");
        sb.append("        .brand h1 { font-size: 22px; font-weight: 800; tracking-spacing: -0.5px; }\n");
        sb.append("        .brand h1 span { color: var(--primary); }\n");
        sb.append("        .menu-list {\n");
        sb.append("            list-style: none; display: flex; flex-direction: column; gap: 8px; flex-grow: 1;\n");
        sb.append("        }\n");
        sb.append("        .menu-item {\n");
        sb.append("            display: flex; align-items: center; gap: 12px; padding: 12px 16px;\n");
        sb.append("            border-radius: 12px; color: var(--text-muted); text-decoration: none;\n");
        sb.append("            font-weight: 500; font-size: 15px; cursor: pointer; transition: 0.2s;\n");
        sb.append("        }\n");
        sb.append("        .menu-item:hover, .menu-item.active {\n");
        sb.append("            color: var(--text); background: rgba(255, 255, 255, 0.06);\n");
        sb.append("        }\n");
        sb.append("        .menu-item.active {\n");
        sb.append("            background: rgba(99, 102, 241, 0.15); border-left: 3px solid var(--primary);\n");
        sb.append("            color: white;\n");
        sb.append("        }\n");
        sb.append("        .sidebar-footer {\n");
        sb.append("            padding: 20px 10px 0; border-top: 1px solid var(--border);\n");
        sb.append("        }\n");
        sb.append("        /* Main Content Panel */\n");
        sb.append("        .main-content {\n");
        sb.append("            margin-left: var(--sidebar-width); flex-grow: 1; padding: 40px;\n");
        sb.append("            min-height: 100vh; display: flex; flex-direction: column; gap: 30px;\n");
        sb.append("        }\n");
        sb.append("        .header {\n");
        sb.append("            display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px;\n");
        sb.append("        }\n");
        sb.append("        .header h2 { font-size: 26px; font-weight: 700; }\n");
        sb.append("        .header p { color: var(--text-muted); font-size: 14px; margin-top: 4px; }\n");
        sb.append("        /* Glassmorphic Card Container */\n");
        sb.append("        .card {\n");
        sb.append("            background: var(--card-bg); border: 1px solid var(--border);\n");
        sb.append("            border-radius: 20px; padding: 24px; box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.2);\n");
        sb.append("            backdrop-filter: blur(16px);\n");
        sb.append("        }\n");
        sb.append("        /* Metrics Layout */\n");
        sb.append("        .metrics-grid {\n");
        sb.append("            display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 20px;\n");
        sb.append("        }\n");
        sb.append("        .metric-card {\n");
        sb.append("            display: flex; align-items: center; gap: 20px;\n");
        sb.append("        }\n");
        sb.append("        .metric-icon {\n");
        sb.append("            width: 56px; height: 56px; border-radius: 16px;\n");
        sb.append("            display: flex; align-items: center; justify-content: center;\n");
        sb.append("            font-size: 22px; color: white;\n");
        sb.append("        }\n");
        sb.append("        .metric-info h4 {\n");
        sb.append("            font-size: 13px; text-transform: uppercase; color: var(--text-muted); letter-spacing: 0.5px; font-weight: 600;\n");
        sb.append("        }\n");
        sb.append("        .metric-info h3 {\n");
        sb.append("            font-size: 24px; font-weight: 700; margin-top: 4px;\n");
        sb.append("        }\n");
        sb.append("        /* Interactive Tables */\n");
        sb.append("        .table-responsive {\n");
        sb.append("            overflow-x: auto; margin-top: 15px;\n");
        sb.append("        }\n");
        sb.append("        table {\n");
        sb.append("            width: 100%; border-collapse: collapse; text-align: left;\n");
        sb.append("        }\n");
        sb.append("        th {\n");
        sb.append("            padding: 16px; border-bottom: 1px solid var(--border);\n");
        sb.append("            font-size: 13px; text-transform: uppercase; color: var(--text-muted); font-weight: 600; letter-spacing: 0.5px;\n");
        sb.append("        }\n");
        sb.append("        td {\n");
        sb.append("            padding: 16px; border-bottom: 1px solid rgba(255, 255, 255, 0.03);\n");
        sb.append("            font-size: 14px; color: var(--text); vertical-align: middle;\n");
        sb.append("        }\n");
        sb.append("        tr:last-child td { border-bottom: none; }\n");
        sb.append("        tr:hover td { background: rgba(255, 255, 255, 0.01); }\n");
        sb.append("        /* Badges */\n");
        sb.append("        .badge {\n");
        sb.append("            display: inline-flex; align-items: center; padding: 4px 10px; border-radius: 8px;\n");
        sb.append("            font-size: 12px; font-weight: 600; text-transform: uppercase; gap: 4px;\n");
        sb.append("        }\n");
        sb.append("        .badge-success {\n");
        sb.append("            background: rgba(16, 185, 129, 0.15); color: var(--success);\n");
        sb.append("            border: 1px solid rgba(16, 185, 129, 0.3);\n");
        sb.append("        }\n");
        sb.append("        .badge-danger {\n");
        sb.append("            background: rgba(239, 68, 68, 0.15); color: var(--danger);\n");
        sb.append("            border: 1px solid rgba(239, 68, 68, 0.3);\n");
        sb.append("        }\n");
        sb.append("        .badge-warning {\n");
        sb.append("            background: rgba(245, 158, 11, 0.15); color: var(--warning);\n");
        sb.append("            border: 1px solid rgba(245, 158, 11, 0.3);\n");
        sb.append("        }\n");
        sb.append("        /* Layout tabs */\n");
        sb.append("        .tab-panel {\n");
        sb.append("            display: none;\n");
        sb.append("            animation: fadeIn 0.4s ease;\n");
        sb.append("        }\n");
        sb.append("        .tab-panel.active {\n");
        sb.append("            display: flex; flex-direction: column; gap: 30px;\n");
        sb.append("        }\n");
        sb.append("        /* Charts layout */\n");
        sb.append("        .charts-row {\n");
        sb.append("            display: grid; grid-template-columns: 2fr 1fr; gap: 24px;\n");
        sb.append("        }\n");
        sb.append("@media (max-width: 1024px) {\n");
        sb.append("    .charts-row { grid-template-columns: 1fr; }\n");
        sb.append("}\n");
        sb.append("        .chart-container {\n");
        sb.append("            position: relative; width: 100%; height: 320px;\n");
        sb.append("        }\n");
        sb.append("        /* Filters */\n");
        sb.append("        .filter-row {\n");
        sb.append("            display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 20px;\n");
        sb.append("        }\n");
        sb.append("        .filter-group {\n");
        sb.append("            display: flex; align-items: center; gap: 12px;\n");
        sb.append("        }\n");
        sb.append("        .preset-buttons {\n");
        sb.append("            display: flex; background: rgba(255, 255, 255, 0.03); border: 1px solid var(--border);\n");
        sb.append("            border-radius: 12px; padding: 4px;\n");
        sb.append("        }\n");
        sb.append("        .preset-btn {\n");
        sb.append("            padding: 8px 16px; border: none; background: transparent; color: var(--text-muted);\n");
        sb.append("            font-weight: 500; font-size: 14px; border-radius: 8px; cursor: pointer; transition: 0.2s;\n");
        sb.append("        }\n");
        sb.append("        .preset-btn.active, .preset-btn:hover {\n");
        sb.append("            color: white; background: rgba(255, 255, 255, 0.08);\n");
        sb.append("        }\n");
        sb.append("        .date-picker-group {\n");
        sb.append("            display: flex; align-items: center; gap: 8px; background: rgba(255, 255, 255, 0.03);\n");
        sb.append("            border: 1px solid var(--border); padding: 6px 12px; border-radius: 12px;\n");
        sb.append("        }\n");
        sb.append("        .date-picker-group input {\n");
        sb.append("            background: transparent; border: none; color: white; font-size: 14px; outline: none;\n");
        sb.append("        }\n");
        sb.append("        /* Modals style */\n");
        sb.append("        .modal-overlay {\n");
        sb.append("            position: fixed; top: 0; left: 0; right: 0; bottom: 0;\n");
        sb.append("            background: rgba(0, 0, 0, 0.7); z-index: 1100;\n");
        sb.append("            display: none; align-items: center; justify-content: center;\n");
        sb.append("            backdrop-filter: blur(8px);\n");
        sb.append("        }\n");
        sb.append("        .modal-card {\n");
        sb.append("            background: #0d121f;\n");
        sb.append("            border: 1px solid var(--border);\n");
        sb.append("            border-radius: 20px; padding: 30px; width: 100%; max-width: 480px;\n");
        sb.append("            box-shadow: 0 10px 40px rgba(0, 0, 0, 0.6);\n");
        sb.append("            animation: fadeInUp 0.4s ease;\n");
        sb.append("        }\n");
        sb.append("        .modal-header {\n");
        sb.append("            display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px;\n");
        sb.append("        }\n");
        sb.append("        .modal-header h3 { font-size: 20px; font-weight: 700; }\n");
        sb.append("        .modal-header i { font-size: 20px; color: var(--text-muted); cursor: pointer; transition: 0.2s; }\n");
        sb.append("        .modal-header i:hover { color: white; }\n");
        sb.append("        .modal-footer {\n");
        sb.append("            display: flex; gap: 12px; justify-content: flex-end; margin-top: 30px;\n");
        sb.append("        }\n");
        sb.append("        .modal-footer .btn {\n");
        sb.append("            width: auto; padding: 12px 24px;\n");
        sb.append("        }\n");
        sb.append("        /* Search bar */\n");
        sb.append("        .search-bar-container {\n");
        sb.append("            position: relative; width: 100%; max-width: 320px;\n");
        sb.append("        }\n");
        sb.append("        .search-bar-container i {\n");
        sb.append("            position: absolute; left: 16px; top: 50%; transform: translateY(-50%); color: var(--text-muted);\n");
        sb.append("        }\n");
        sb.append("        .search-bar-container input {\n");
        sb.append("            width: 100%; padding: 12px 16px 12px 48px; border-radius: 12px;\n");
        sb.append("            background: rgba(255, 255, 255, 0.04); border: 1px solid var(--border);\n");
        sb.append("            color: white; font-size: 14px; outline: none; transition: 0.2s;\n");
        sb.append("        }\n");
        sb.append("        .search-bar-container input:focus { border-color: var(--primary); background: rgba(255, 255, 255, 0.08); }\n");
        sb.append("        /* Logs styling */\n");
        sb.append("        .logs-box {\n");
        sb.append("            background: rgba(0, 0, 0, 0.3); border: 1px solid var(--border); border-radius: 12px;\n");
        sb.append("            padding: 16px; height: 400px; overflow-y: auto; font-family: monospace; font-size: 13px;\n");
        sb.append("            color: #10b981; line-height: 1.6; display: flex; flex-direction: column-reverse; gap: 4px;\n");
        sb.append("        }\n");
        sb.append("        /* Toast notifications */\n");
        sb.append("        .toast {\n");
        sb.append("            position: fixed; bottom: 30px; right: 30px; padding: 16px 24px; border-radius: 12px;\n");
        sb.append("            background: rgba(17, 24, 39, 0.9); border: 1px solid var(--border); color: white;\n");
        sb.append("            display: flex; align-items: center; gap: 12px; box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);\n");
        sb.append("            z-index: 10000; animation: slideInRight 0.3s ease; font-weight: 500;\n");
        sb.append("        }\n");
        sb.append("        .toast.success { border-left: 4px solid var(--success); }\n");
        sb.append("        .toast.error { border-left: 4px solid var(--danger); }\n");
        sb.append("        /* Keyframes */\n");
        sb.append("        @keyframes fadeInUp {\n");
        sb.append("            from { opacity: 0; transform: translateY(20px); }\n");
        sb.append("            to { opacity: 1; transform: translateY(0); }\n");
        sb.append("        }\n");
        sb.append("        @keyframes fadeIn {\n");
        sb.append("            from { opacity: 0; }\n");
        sb.append("            to { opacity: 1; }\n");
        sb.append("        }\n");
        sb.append("        @keyframes slideInRight {\n");
        sb.append("            from { transform: translateX(100%); opacity: 0; }\n");
        sb.append("            to { transform: translateX(0); opacity: 1; }\n");
        sb.append("        }\n");
        sb.append("    </style>\n");
        sb.append("</head>\n");
        return sb.toString();
    }

    private static String obterHtmlDashboardPart2() {
        StringBuilder sb = new StringBuilder();
        sb.append("<body>\n");
        sb.append("    <!-- Login Modal -->\n");
        sb.append("    <div id=\"login-panel\">\n");
        sb.append("        <div class=\"login-card\">\n");
        sb.append("            <i class=\"fa-solid fa-square-parking logo-icon\"></i>\n");
        sb.append("            <h2>Console Park ' 31</h2>\n");
        sb.append("            <p>Insira a senha do administrador para acessar o painel central SaaS</p>\n");
        sb.append("            <div class=\"form-group\">\n");
        sb.append("                <label for=\"senha-input\">Senha Admin</label>\n");
        sb.append("                <input type=\"password\" id=\"senha-input\" class=\"form-control\" placeholder=\"••••••••\" onkeydown=\"if(event.key === 'Enter') realizarLogin()\">\n");
        sb.append("            </div>\n");
        sb.append("            <button class=\"btn btn-primary\" onclick=\"realizarLogin()\">\n");
        sb.append("                <i class=\"fa-solid fa-right-to-bracket\"></i> Acessar Painel\n");
        sb.append("            </button>\n");
        sb.append("        </div>\n");
        sb.append("    </div>\n");
        sb.append("\n");
        sb.append("    <!-- Main Dashboard Layout -->\n");
        sb.append("    <div id=\"app-layout\">\n");
        sb.append("        <!-- Sidebar -->\n");
        sb.append("        <div class=\"sidebar\">\n");
        sb.append("            <div class=\"brand\">\n");
        sb.append("                <i class=\"fa-solid fa-square-parking\"></i>\n");
        sb.append("                <h1>Park '<span>31</span></h1>\n");
        sb.append("            </div>\n");
        sb.append("            <ul class=\"menu-list\">\n");
        sb.append("                <li><div class=\"menu-item active\" onclick=\"mudarAba('tab-faturamento')\" id=\"btn-tab-faturamento\"><i class=\"fa-solid fa-chart-line\"></i> Faturamento & Caixa</div></li>\n");
        sb.append("                <li><div class=\"menu-item\" onclick=\"mudarAba('tab-mensalistas')\" id=\"btn-tab-mensalistas\"><i class=\"fa-solid fa-users-line\"></i> Gestão de Mensalistas</div></li>\n");
        sb.append("                <li><div class=\"menu-item\" onclick=\"mudarAba('tab-terminais')\" id=\"btn-tab-terminais\"><i class=\"fa-solid fa-tablet-screen-button\"></i> Terminais & Pátios</div></li>\n");
        sb.append("                <li><div class=\"menu-item\" onclick=\"mudarAba('tab-auditoria')\" id=\"btn-tab-auditoria\"><i class=\"fa-solid fa-shield-halved\"></i> Logs de Auditoria</div></li>\n");
        sb.append("            </ul>\n");
        sb.append("            <div class=\"sidebar-footer\">\n");
        sb.append("                <button class=\"btn btn-secondary\" style=\"padding: 10px; font-size: 13px;\" onclick=\"efetuarLogout()\">\n");
        sb.append("                    <i class=\"fa-solid fa-right-from-bracket\"></i> Sair da Console\n");
        sb.append("                </button>\n");
        sb.append("            </div>\n");
        sb.append("        </div>\n");
        sb.append("\n");
        sb.append("        <!-- Main Content -->\n");
        sb.append("        <div class=\"main-content\">\n");
        sb.append("            <!-- TAB: FATURAMENTO & CAIXA -->\n");
        sb.append("            <div id=\"tab-faturamento\" class=\"tab-panel active\">\n");
        sb.append("                <div class=\"header\">\n");
        sb.append("                    <div>\n");
        sb.append("                        <h2>Faturamento & Caixa Consolidado</h2>\n");
        sb.append("                        <p>Receitas unificadas de todos os pátios e terminais móveis associados</p>\n");
        sb.append("                    </div>\n");
        sb.append("                </div>\n");
        sb.append("\n");
        sb.append("                <!-- Metric cards -->\n");
        sb.append("                <div class=\"metrics-grid\">\n");
        sb.append("                    <div class=\"card metric-card\">\n");
        sb.append("                        <div class=\"metric-icon\" style=\"background: linear-gradient(135deg, #10b981, #059669);\"><i class=\"fa-solid fa-dollar-sign\"></i></div>\n");
        sb.append("                        <div class=\"metric-info\">\n");
        sb.append("                            <h4>Faturamento Total</h4>\n");
        sb.append("                            <h3 id=\"total-faturamento\">R$ 0,00</h3>\n");
        sb.append("                        </div>\n");
        sb.append("                    </div>\n");
        sb.append("                    <div class=\"card metric-card\">\n");
        sb.append("                        <div class=\"metric-icon\" style=\"background: linear-gradient(135deg, #6366f1, #4f46e5);\"><i class=\"fa-solid fa-clock\"></i></div>\n");
        sb.append("                        <div class=\"metric-info\">\n");
        sb.append("                            <h4>Permanência Média</h4>\n");
        sb.append("                            <h3 id=\"media-permanencia\">0 min</h3>\n");
        sb.append("                        </div>\n");
        sb.append("                    </div>\n");
        sb.append("                    <div class=\"card metric-card\">\n");
        sb.append("                        <div class=\"metric-icon\" style=\"background: linear-gradient(135deg, #f59e0b, #d97706);\"><i class=\"fa-solid fa-ticket\"></i></div>\n");
        sb.append("                        <div class=\"metric-info\">\n");
        sb.append("                            <h4>Ticket Médio</h4>\n");
        sb.append("                            <h3 id=\"ticket-medio\">R$ 0,00</h3>\n");
        sb.append("                        </div>\n");
        sb.append("                    </div>\n");
        sb.append("                    <div class=\"card metric-card\">\n");
        sb.append("                        <div class=\"metric-icon\" style=\"background: linear-gradient(135deg, #ec4899, #db2777);\"><i class=\"fa-solid fa-mobile-screen-button\"></i></div>\n");
        sb.append("                        <div class=\"metric-info\">\n");
        sb.append("                            <h4>Terminais Ativos</h4>\n");
        sb.append("                            <h3 id=\"count-terminais\">0</h3>\n");
        sb.append("                        </div>\n");
        sb.append("                    </div>\n");
        sb.append("                </div>\n");
        sb.append("\n");
        sb.append("                <!-- Filter Panel -->\n");
        sb.append("                <div class=\"card filter-row\">\n");
        sb.append("                    <div class=\"filter-group\">\n");
        sb.append("                        <span style=\"font-size: 14px; font-weight: 600; color: var(--text-muted);\"><i class=\"fa-solid fa-calendar-days\"></i> Período:</span>\n");
        sb.append("                        <div class=\"preset-buttons\">\n");
        sb.append("                            <button class=\"preset-btn active\" onclick=\"setPresetPeriod(7, this)\">7 Dias</button>\n");
        sb.append("                            <button class=\"preset-btn\" onclick=\"setPresetPeriod(30, this)\">30 Dias</button>\n");
        sb.append("                            <button class=\"preset-btn\" onclick=\"setPresetPeriod(0, this)\">Ver Tudo</button>\n");
        sb.append("                        </div>\n");
        sb.append("                    </div>\n");
        sb.append("                    <div class=\"filter-group\">\n");
        sb.append("                        <div class=\"date-picker-group\">\n");
        sb.append("                            <input type=\"date\" id=\"filtro-data-inicio\" onchange=\"aplicarFiltrosData()\">\n");
        sb.append("                            <span style=\"color: var(--text-muted); font-size: 12px;\">até</span>\n");
        sb.append("                            <input type=\"date\" id=\"filtro-data-fim\" onchange=\"aplicarFiltrosData()\">\n");
        sb.append("                        </div>\n");
        sb.append("                    </div>\n");
        sb.append("                </div>\n");
        sb.append("\n");
        sb.append("                <!-- Charts Row -->\n");
        sb.append("                <div class=\"charts-row\">\n");
        sb.append("                    <div class=\"card\">\n");
        sb.append("                        <h3 style=\"font-size: 16px; font-weight: 600; margin-bottom: 20px;\"><i class=\"fa-solid fa-chart-area\"></i> Receita Diária Consolidada</h3>\n");
        sb.append("                        <div class=\"chart-container\">\n");
        sb.append("                            <canvas id=\"chart-receita-diaria\"></canvas>\n");
        sb.append("                        </div>\n");
        sb.append("                    </div>\n");
        sb.append("                    <div class=\"card\">\n");
        sb.append("                        <h3 style=\"font-size: 16px; font-weight: 600; margin-bottom: 20px;\"><i class=\"fa-solid fa-chart-pie\"></i> Receita por Terminal</h3>\n");
        sb.append("                        <div class=\"chart-container\">\n");
        sb.append("                            <canvas id=\"chart-receita-terminal\"></canvas>\n");
        sb.append("                        </div>\n");
        sb.append("                    </div>\n");
        sb.append("                </div>\n");
        sb.append("\n");
        sb.append("                <!-- Transactions List -->\n");
        sb.append("                <div class=\"card\">\n");
        sb.append("                    <div class=\"header\" style=\"margin-bottom: 20px;\">\n");
        sb.append("                        <h3>Transações Recentes</h3>\n");
        sb.append("                        <div class=\"search-bar-container\">\n");
        sb.append("                            <i class=\"fa-solid fa-magnifying-glass\"></i>\n");
        sb.append("                            <input type=\"text\" placeholder=\"Buscar placa...\" id=\"busca-transacao\" oninput=\"filtrarTabelaTransacoes()\">\n");
        sb.append("                        </div>\n");
        sb.append("                    </div>\n");
        sb.append("                    <div class=\"table-responsive\">\n");
        sb.append("                        <table id=\"tabela-transacoes\">\n");
        sb.append("                            <thead>\n");
        sb.append("                                <tr>\n");
        sb.append("                                    <th>Placa</th>\n");
        sb.append("                                    <th>Horário Entrada</th>\n");
        sb.append("                                    <th>Horário Saída</th>\n");
        sb.append("                                    <th>Permanência</th>\n");
        sb.append("                                    <th>Valor Pago</th>\n");
        sb.append("                                    <th>Terminal</th>\n");
        sb.append("                                </tr>\n");
        sb.append("                            </thead>\n");
        sb.append("                            <tbody>\n");
        sb.append("                                <!-- Inserido via JS -->\n");
        sb.append("                            </tbody>\n");
        sb.append("                        </table>\n");
        sb.append("                    </div>\n");
        sb.append("                </div>\n");
        sb.append("            </div>\n");
        sb.append("\n");
        sb.append("            <!-- TAB: GESTAO DE MENSALISTAS -->\n");
        sb.append("            <div id=\"tab-mensalistas\" class=\"tab-panel\">\n");
        sb.append("                <div class=\"header\">\n");
        sb.append("                    <div>\n");
        sb.append("                        <h2>Gestão de Mensalistas</h2>\n");
        sb.append("                        <p>Controle de assinantes recorrentes com sincronização offline com os pátios</p>\n");
        sb.append("                    </div>\n");
        sb.append("                    <button class=\"btn btn-primary\" style=\"width: auto;\" onclick=\"abrirModalNovoMensalista()\">\n");
        sb.append("                        <i class=\"fa-solid fa-user-plus\"></i> Novo Mensalista\n");
        sb.append("                    </button>\n");
        sb.append("                </div>\n");
        sb.append("\n");
        sb.append("                <div class=\"card\">\n");
        sb.append("                    <div class=\"header\" style=\"margin-bottom: 20px;\">\n");
        sb.append("                        <h3>Assinantes Cadastrados</h3>\n");
        sb.append("                        <div class=\"search-bar-container\">\n");
        sb.append("                            <i class=\"fa-solid fa-magnifying-glass\"></i>\n");
        sb.append("                            <input type=\"text\" placeholder=\"Buscar por nome ou placa...\" id=\"busca-mensalista\" oninput=\"filtrarTabelaMensalistas()\">\n");
        sb.append("                        </div>\n");
        sb.append("                    </div>\n");
        sb.append("                    <div class=\"table-responsive\">\n");
        sb.append("                        <table id=\"tabela-mensalistas\">\n");
        sb.append("                            <thead>\n");
        sb.append("                                <tr>\n");
        sb.append("                                    <th>Nome do Cliente</th>\n");
        sb.append("                                    <th>Placa do Veículo</th>\n");
        sb.append("                                    <th>Telefone</th>\n");
        sb.append("                                    <th>Vencimento</th>\n");
        sb.append("                                    <th>Status</th>\n");
        sb.append("                                    <th style=\"text-align: right;\">Ações</th>\n");
        sb.append("                                </tr>\n");
        sb.append("                            </thead>\n");
        sb.append("                            <tbody>\n");
        sb.append("                                <!-- Inserido via JS -->\n");
        sb.append("                            </tbody>\n");
        sb.append("                        </table>\n");
        sb.append("                    </div>\n");
        sb.append("                </div>\n");
        sb.append("            </div>\n");
        sb.append("\n");
        sb.append("            <!-- TAB: TERMINAIS -->\n");
        sb.append("            <div id=\"tab-terminais\" class=\"tab-panel\">\n");
        sb.append("                <div class=\"header\">\n");
        sb.append("                    <div>\n");
        sb.append("                        <h2>Terminais & Pátios de Estacionamento</h2>\n");
        sb.append("                        <p>Autorize novos aparelhos Android/Swing e controle limites de vagas centralizados</p>\n");
        sb.append("                    </div>\n");
        sb.append("                </div>\n");
        sb.append("\n");
        sb.append("                <div class=\"card\">\n");
        sb.append("                    <h3>Terminais Identificados</h3>\n");
        sb.append("                    <div class=\"table-responsive\">\n");
        sb.append("                        <table id=\"tabela-terminais\">\n");
        sb.append("                            <thead>\n");
        sb.append("                                <tr>\n");
        sb.append("                                    <th>Aparelho / OS</th>\n");
        sb.append("                                    <th>Hardware ID</th>\n");
        sb.append("                                    <th>Pátio / Cliente</th>\n");
        sb.append("                                    <th>Tarifa</th>\n");
        sb.append("                                    <th>Vagas (C/M)</th>\n");
        sb.append("                                    <th>Expiração Licença</th>\n");
        sb.append("                                    <th>Status</th>\n");
        sb.append("                                    <th style=\"text-align: right;\">Ações</th>\n");
        sb.append("                                </tr>\n");
        sb.append("                            </thead>\n");
        sb.append("                            <tbody>\n");
        sb.append("                                <!-- Inserido via JS -->\n");
        sb.append("                            </tbody>\n");
        sb.append("                        </table>\n");
        sb.append("                    </div>\n");
        sb.append("                </div>\n");
        sb.append("            </div>\n");
        sb.append("\n");
        sb.append("            <!-- TAB: AUDITORIA -->\n");
        sb.append("            <div id=\"tab-auditoria\" class=\"tab-panel\">\n");
        sb.append("                <div class=\"header\">\n");
        sb.append("                    <div>\n");
        sb.append("                        <h2>Logs de Auditoria de Segurança</h2>\n");
        sb.append("                        <p>Registro imutável em tempo real de todas as ações sensíveis realizadas nos terminais</p>\n");
        sb.append("                    </div>\n");
        sb.append("                    <div style=\"display: flex; align-items: center; gap: 10px;\">\n");
        sb.append("                        <input type=\"checkbox\" id=\"check-autorefresh\" checked style=\"width: 18px; height: 18px; cursor: pointer;\">\n");
        sb.append("                        <label for=\"check-autorefresh\" style=\"font-size: 14px; font-weight: 500; cursor: pointer;\">Atualização Automática</label>\n");
        sb.append("                    </div>\n");
        sb.append("                </div>\n");
        sb.append("\n");
        sb.append("                <div class=\"card\">\n");
        sb.append("                    <div class=\"logs-box\" id=\"box-logs\">\n");
        sb.append("                        <!-- Inserido via JS -->\n");
        sb.append("                    </div>\n");
        sb.append("                </div>\n");
        sb.append("            </div>\n");
        sb.append("        </div>\n");
        sb.append("    </div>\n");
        sb.append("\n");
        sb.append("    <!-- MODALS SECTION -->\n");
        sb.append("    <!-- Modal: Adicionar Mensalista -->\n");
        sb.append("    <div class=\"modal-overlay\" id=\"modal-novo-mensalista\">\n");
        sb.append("        <div class=\"modal-card\">\n");
        sb.append("            <div class=\"modal-header\">\n");
        sb.append("                <h3>Novo Mensalista</h3>\n");
        sb.append("                <i class=\"fa-solid fa-xmark\" onclick=\"fecharModais()\"></i>\n");
        sb.append("            </div>\n");
        sb.append("            <div class=\"form-group\">\n");
        sb.append("                <label>Nome Completo</label>\n");
        sb.append("                <input type=\"text\" id=\"input-m-nome\" class=\"form-control\" placeholder=\"ex. João da Silva\">\n");
        sb.append("            </div>\n");
        sb.append("            <div class=\"form-group\">\n");
        sb.append("                <label>Placa do Veículo</label>\n");
        sb.append("                <input type=\"text\" id=\"input-m-placa\" class=\"form-control\" placeholder=\"ex. ABC1D23\" style=\"text-transform: uppercase;\">\n");
        sb.append("            </div>\n");
        sb.append("            <div class=\"form-group\">\n");
        sb.append("                <label>Telefone para Contato</label>\n");
        sb.append("                <input type=\"text\" id=\"input-m-telefone\" class=\"form-control\" placeholder=\"ex. (31) 98765-4321\">\n");
        sb.append("            </div>\n");
        sb.append("            <div class=\"form-group\">\n");
        sb.append("                <label>Data de Vencimento</label>\n");
        sb.append("                <input type=\"date\" id=\"input-m-vencimento\" class=\"form-control\">\n");
        sb.append("            </div>\n");
        sb.append("            <div class=\"modal-footer\">\n");
        sb.append("                <button class=\"btn btn-secondary\" onclick=\"fecharModais()\">Cancelar</button>\n");
        sb.append("                <button class=\"btn btn-primary\" onclick=\"salvarNovoMensalista()\">Salvar Mensalista</button>\n");
        sb.append("            </div>\n");
        sb.append("        </div>\n");
        sb.append("    </div>\n");
        sb.append("\n");
        sb.append("    <!-- Modal: Renovar / Editar Mensalista -->\n");
        sb.append("    <div class=\"modal-overlay\" id=\"modal-editar-mensalista\">\n");
        sb.append("        <div class=\"modal-card\">\n");
        sb.append("            <div class=\"modal-header\">\n");
        sb.append("                <h3>Renovar Assinatura</h3>\n");
        sb.append("                <i class=\"fa-solid fa-xmark\" onclick=\"fecharModais()\"></i>\n");
        sb.append("            </div>\n");
        sb.append("            <input type=\"hidden\" id=\"editar-m-placa\">\n");
        sb.append("            <div class=\"form-group\">\n");
        sb.append("                <label>Nome do Cliente</label>\n");
        sb.append("                <input type=\"text\" id=\"editar-m-nome\" class=\"form-control\" readonly style=\"background: rgba(255,255,255,0.02); opacity: 0.7;\">\n");
        sb.append("            </div>\n");
        sb.append("            <div class=\"form-group\">\n");
        sb.append("                <label>Nova Data de Vencimento</label>\n");
        sb.append("                <input type=\"date\" id=\"editar-m-vencimento\" class=\"form-control\">\n");
        sb.append("            </div>\n");
        sb.append("            <div class=\"form-group\">\n");
        sb.append("                <label>Status</label>\n");
        sb.append("                <select id=\"editar-m-status\" class=\"form-control\">\n");
        sb.append("                    <option value=\"ATIVO\">ATIVO</option>\n");
        sb.append("                    <option value=\"SUSPENSO\">SUSPENSO</option>\n");
        sb.append("                </select>\n");
        sb.append("            </div>\n");
        sb.append("            <div class=\"modal-footer\">\n");
        sb.append("                <button class=\"btn btn-secondary\" onclick=\"fecharModais()\">Cancelar</button>\n");
        sb.append("                <button class=\"btn btn-primary\" onclick=\"salvarEdicaoMensalista()\">Confirmar Renovação</button>\n");
        sb.append("            </div>\n");
        sb.append("        </div>\n");
        sb.append("    </div>\n");
        sb.append("\n");
        sb.append("    <!-- Modal: Aprovar Licença de Terminal -->\n");
        sb.append("    <div class=\"modal-overlay\" id=\"modal-aprovar-terminal\">\n");
        sb.append("        <div class=\"modal-card\">\n");
        sb.append("            <div class=\"modal-header\">\n");
        sb.append("                <h3>Aprovar Dispositivo</h3>\n");
        sb.append("                <i class=\"fa-solid fa-xmark\" onclick=\"fecharModais()\"></i>\n");
        sb.append("            </div>\n");
        sb.append("            <input type=\"hidden\" id=\"aprovar-t-hwid\">\n");
        sb.append("            <div class=\"form-group\">\n");
        sb.append("                <label>Dias de Licença Autorizados</label>\n");
        sb.append("                <input type=\"number\" id=\"aprovar-t-dias\" class=\"form-control\" value=\"30\" min=\"1\">\n");
        sb.append("            </div>\n");
        sb.append("            <div class=\"modal-footer\">\n");
        sb.append("                <button class=\"btn btn-secondary\" onclick=\"fecharModais()\">Cancelar</button>\n");
        sb.append("                <button class=\"btn btn-primary\" onclick=\"salvarAprovacaoTerminal()\">Liberar Terminal</button>\n");
        sb.append("            </div>\n");
        sb.append("        </div>\n");
        sb.append("    </div>\n");
        sb.append("\n");
        sb.append("    <!-- Modal: Configurar Terminal -->\n");
        sb.append("    <div class=\"modal-overlay\" id=\"modal-configurar-terminal\">\n");
        sb.append("        <div class=\"modal-card\">\n");
        sb.append("            <div class=\"modal-header\">\n");
        sb.append("                <h3>Configurar Pátio/Terminal</h3>\n");
        sb.append("                <i class=\"fa-solid fa-xmark\" onclick=\"fecharModais()\"></i>\n");
        sb.append("            </div>\n");
        sb.append("            <input type=\"hidden\" id=\"config-t-hwid\">\n");
        sb.append("            <div class=\"form-group\">\n");
        sb.append("                <label>Nome do Cliente / Pátio</label>\n");
        sb.append("                <input type=\"text\" id=\"config-t-nome\" class=\"form-control\">\n");
        sb.append("            </div>\n");
        sb.append("            <div class=\"form-grid-3\">\n");
        sb.append("                <div class=\"form-group\">\n");
        sb.append("                    <label>Tarifa Hora (R$)</label>\n");
        sb.append("                    <input type=\"number\" step=\"0.50\" id=\"config-t-tarifa\" class=\"form-control\" value=\"5.00\" min=\"0\">\n");
        sb.append("                </div>\n");
        sb.append("                <div class=\"form-group\">\n");
        sb.append("                    <label>Vagas Carro</label>\n");
        sb.append("                    <input type=\"number\" id=\"config-t-carro\" class=\"form-control\" value=\"20\" min=\"0\">\n");
        sb.append("                </div>\n");
        sb.append("                <div class=\"form-group\">\n");
        sb.append("                    <label>Vagas Moto</label>\n");
        sb.append("                    <input type=\"number\" id=\"config-t-moto\" class=\"form-control\" value=\"5\" min=\"0\">\n");
        sb.append("                </div>\n");
        sb.append("            </div>\n");
        sb.append("            <div class=\"modal-footer\">\n");
        sb.append("                <button class=\"btn btn-secondary\" onclick=\"fecharModais()\">Cancelar</button>\n");
        sb.append("                <button class=\"btn btn-primary\" onclick=\"salvarConfigTerminal()\">Salvar Configurações</button>\n");
        sb.append("            </div>\n");
        sb.append("        </div>\n");
        sb.append("    </div>\n");
        sb.append("\n");
        sb.append("    <!-- JAVASCRIPT AJAX & CORE LOGIC -->\n");
        sb.append("    <script>\n");
        sb.append("        let cachedToken = localStorage.getItem('park31_token');\n");
        sb.append("        let transacoesOriginais = [];\n");
        sb.append("        let mensalistasOriginais = [];\n");
        sb.append("        let chartReceita = null;\n");
        sb.append("        let chartTerminal = null;\n");
        sb.append("\n");
        sb.append("        // Inicialização\n");
        sb.append("        window.addEventListener('load', () => {\n");
        sb.append("            if (cachedToken) {\n");
        sb.append("                document.getElementById('login-panel').style.display = 'none';\n");
        sb.append("                document.getElementById('app-layout').classList.add('active');\n");
        sb.append("                carregarTudo();\n");
        sb.append("                iniciarAutoRefresh();\n");
        sb.append("            } else {\n");
        sb.append("                document.getElementById('login-panel').style.display = 'flex';\n");
        sb.append("            }\n");
        sb.append("            // Iniciar com datas padrão no filtro: últimos 30 dias\n");
        sb.append("            const hoje = new Date();\n");
        sb.append("            const trintaDiasAtras = new Date(hoje.getTime() - (30 * 24 * 60 * 60 * 1000));\n");
        sb.append("            document.getElementById('filtro-data-inicio').value = trintaDiasAtras.toISOString().split('T')[0];\n");
        sb.append("            document.getElementById('filtro-data-fim').value = hoje.toISOString().split('T')[0];\n");
        sb.append("        });\n");
        sb.append("\n");
        sb.append("        function mostrarToast(mensagem, tipo = 'success') {\n");
        sb.append("            const t = document.createElement('div');\n");
        sb.append("            t.className = `toast ${tipo}`;\n");
        sb.append("            t.innerHTML = `<i class=\"fa-solid ${tipo === 'success' ? 'fa-circle-check' : 'fa-circle-exclamation'}\"></i> <span>${mensagem}</span>`;\n");
        sb.append("            document.body.appendChild(t);\n");
        sb.append("            setTimeout(() => { t.remove(); }, 3000);\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        function realizarLogin() {\n");
        sb.append("            const senha = document.getElementById('senha-input').value;\n");
        sb.append("            if (!senha) return;\n");
        sb.append("            fetch('/api/login', {\n");
        sb.append("                method: 'POST',\n");
        sb.append("                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },\n");
        sb.append("                body: 'senha=' + encodeURIComponent(senha)\n");
        sb.append("            })\n");
        sb.append("            .then(r => r.json())\n");
        sb.append("            .then(data => {\n");
        sb.append("                if (data.success) {\n");
        sb.append("                    cachedToken = data.token;\n");
        sb.append("                    localStorage.setItem('park31_token', cachedToken);\n");
        sb.append("                    document.getElementById('login-panel').style.display = 'none';\n");
        sb.append("                    document.getElementById('app-layout').classList.add('active');\n");
        sb.append("                    carregarTudo();\n");
        sb.append("                    iniciarAutoRefresh();\n");
        sb.append("                    mostrarToast('Login efetuado com sucesso!');\n");
        sb.append("                } else {\n");
        sb.append("                    mostrarToast('Senha incorreta!', 'error');\n");
        sb.append("                }\n");
        sb.append("            })\n");
        sb.append("            .catch(err => mostrarToast('Erro ao conectar ao servidor', 'error'));\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        function efetuarLogout() {\n");
        sb.append("            localStorage.removeItem('park31_token');\n");
        sb.append("            cachedToken = null;\n");
        sb.append("            document.getElementById('login-panel').style.display = 'flex';\n");
        sb.append("            document.getElementById('app-layout').classList.remove('active');\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        function carregarTudo() {\n");
        sb.append("            carregarTransacoes();\n");
        sb.append("            carregarMensalistas();\n");
        sb.append("            carregarTerminais();\n");
        sb.append("            carregarLogs();\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        function iniciarAutoRefresh() {\n");
        sb.append("            setInterval(() => {\n");
        sb.append("                if (cachedToken && document.getElementById('check-autorefresh').checked) {\n");
        sb.append("                    carregarLogs();\n");
        sb.append("                }\n");
        sb.append("            }, 4000);\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        function mudarAba(tabId) {\n");
        sb.append("            document.querySelectorAll('.tab-panel').forEach(t => t.classList.remove('active'));\n");
        sb.append("            document.querySelectorAll('.menu-item').forEach(m => m.classList.remove('active'));\n");
        sb.append("            document.getElementById(tabId).classList.add('active');\n");
        sb.append("            document.getElementById('btn-' + tabId).classList.add('active');\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        function fecharModais() {\n");
        sb.append("            document.querySelectorAll('.modal-overlay').forEach(m => m.style.display = 'none');\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        // --- GESTÃO DE MENSALISTAS ---\n");
        sb.append("        function carregarMensalistas() {\n");
        sb.append("            fetch('/api/mensalistas/list', {\n");
        sb.append("                headers: { 'X-Admin-Token': cachedToken }\n");
        sb.append("            })\n");
        sb.append("            .then(r => {\n");
        sb.append("                if (r.status === 401) { efetuarLogout(); return []; }\n");
        sb.append("                return r.json();\n");
        sb.append("            })\n");
        sb.append("            .then(data => {\n");
        sb.append("                mensalistasOriginais = data;\n");
        sb.append("                renderizarTabelaMensalistas(data);\n");
        sb.append("            });\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        function renderizarTabelaMensalistas(lista) {\n");
        sb.append("            const tbody = document.querySelector('#tabela-mensalistas tbody');\n");
        sb.append("            tbody.innerHTML = '';\n");
        sb.append("            if (lista.length === 0) {\n");
        sb.append("                tbody.innerHTML = '<tr><td colspan=\"6\" style=\"text-align: center; color: var(--text-muted); padding: 30px;\">Nenhum mensalista cadastrado</td></tr>';\n");
        sb.append("                return;\n");
        sb.append("            }\n");
        sb.append("            const hoje = Date.now();\n");
        sb.append("            lista.forEach(m => {\n");
        sb.append("                const dataVenc = new Date(m.vencimento);\n");
        sb.append("                const formatada = dataVenc.toLocaleDateString('pt-BR');\n");
        sb.append("                let status = m.status ? m.status.toUpperCase() : 'ATIVO';\n");
        sb.append("                if (status === 'ATIVO' && m.vencimento < hoje) {\n");
        sb.append("                    status = 'VENCIDO';\n");
        sb.append("                }\n");
        sb.append("                let badgeClass = 'badge-success';\n");
        sb.append("                if (status === 'VENCIDO') badgeClass = 'badge-danger';\n");
        sb.append("                if (status === 'SUSPENSO') badgeClass = 'badge-warning';\n");
        sb.append("                \n");
        sb.append("                const row = document.createElement('tr');\n");
        sb.append("                row.innerHTML = `\n");
        sb.append("                    <td style=\"font-weight: 600;\">${m.nomeCliente}</td>\n");
        sb.append("                    <td><span class=\"badge btn-secondary\" style=\"font-family: monospace; font-size: 13px;\">${m.placa}</span></td>\n");
        sb.append("                    <td>${m.telefone || '-'}</td>\n");
        sb.append("                    <td>${formatada}</td>\n");
        sb.append("                    <td><span class=\"badge ${badgeClass}\"><i class=\"fa-solid ${status === 'ATIVO' ? 'fa-check' : 'fa-triangle-exclamation'}\"></i> ${status}</span></td>\n");
        sb.append("                    <td style=\"text-align: right; display: flex; gap: 8px; justify-content: flex-end;\">\n");
        sb.append("                        <button class=\"btn btn-secondary\" style=\"padding: 8px 12px; font-size: 12px;\" onclick=\"abrirModalEditarMensalista('${m.placa}', '${m.nomeCliente}', ${m.vencimento}, '${m.status}')\"><i class=\"fa-solid fa-calendar-plus\"></i> Renovar</button>\n");
        sb.append("                        <button class=\"btn ${m.status === 'ATIVO' ? 'btn-secondary' : 'btn-primary'}\" style=\"padding: 8px 12px; font-size: 12px;\" onclick=\"toggleStatusMensalista('${m.placa}', '${m.status}', ${m.vencimento})\">${m.status === 'ATIVO' ? '<i class=\"fa-solid fa-ban\"></i> Suspender' : '<i class=\"fa-solid fa-check\"></i> Ativar'}</button>\n");
        sb.append("                        <button class=\"btn btn-secondary\" style=\"padding: 8px 12px; font-size: 12px; color: var(--danger); border-color: rgba(239,68,68,0.2);\" onclick=\"deletarMensalista('${m.placa}')\"><i class=\"fa-solid fa-trash-can\"></i></button>\n");
        sb.append("                    </td>\n");
        sb.append("                `;\n");
        sb.append("                tbody.appendChild(row);\n");
        sb.append("            });\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        function filtrarTabelaMensalistas() {\n");
        sb.append("            const query = document.getElementById('busca-mensalista').value.toLowerCase();\n");
        sb.append("            const filtered = mensalistasOriginais.filter(m => \n");
        sb.append("                m.nomeCliente.toLowerCase().includes(query) || \n");
        sb.append("                m.placa.toLowerCase().includes(query)\n");
        sb.append("            );\n");
        sb.append("            renderizarTabelaMensalistas(filtered);\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        function abrirModalNovoMensalista() {\n");
        sb.append("            document.getElementById('input-m-nome').value = '';\n");
        sb.append("            document.getElementById('input-m-placa').value = '';\n");
        sb.append("            document.getElementById('input-m-telefone').value = '';\n");
        sb.append("            const trintaDias = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];\n");
        sb.append("            document.getElementById('input-m-vencimento').value = trintaDias;\n");
        sb.append("            document.getElementById('modal-novo-mensalista').style.display = 'flex';\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        function salvarNovoMensalista() {\n");
        sb.append("            const nome = document.getElementById('input-m-nome').value;\n");
        sb.append("            const placa = document.getElementById('input-m-placa').value.trim().toUpperCase();\n");
        sb.append("            const telefone = document.getElementById('input-m-telefone').value;\n");
        sb.append("            const vencimentoStr = document.getElementById('input-m-vencimento').value;\n");
        sb.append("            if (!nome || !placa || !vencimentoStr) { mostrarToast('Preencha os campos obrigatórios!', 'error'); return; }\n");
        sb.append("            const timestamp = new Date(vencimentoStr + 'T23:59:59').getTime();\n");
        sb.append("            fetch('/api/mensalistas/add', {\n");
        sb.append("                method: 'POST',\n");
        sb.append("                headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-Admin-Token': cachedToken },\n");
        sb.append("                body: `placa=${placa}&nome=${encodeURIComponent(nome)}&telefone=${encodeURIComponent(telefone)}&vencimento=${timestamp}`\n");
        sb.append("            })\n");
        sb.append("            .then(r => r.json())\n");
        sb.append("            .then(data => {\n");
        sb.append("                if (data.success) {\n");
        sb.append("                    fecharModais();\n");
        sb.append("                    carregarMensalistas();\n");
        sb.append("                    mostrarToast('Mensalista cadastrado com sucesso!');\n");
        sb.append("                } else { mostrarToast('Erro ao cadastrar mensalista', 'error'); }\n");
        sb.append("            });\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        function abrirModalEditarMensalista(placa, nome, vencimentoMs, status) {\n");
        sb.append("            document.getElementById('editar-m-placa').value = placa;\n");
        sb.append("            document.getElementById('editar-m-nome').value = nome;\n");
        sb.append("            document.getElementById('editar-m-vencimento').value = new Date(vencimentoMs).toISOString().split('T')[0];\n");
        sb.append("            document.getElementById('editar-m-status').value = status;\n");
        sb.append("            document.getElementById('modal-editar-mensalista').style.display = 'flex';\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        function salvarEdicaoMensalista() {\n");
        sb.append("            const placa = document.getElementById('editar-m-placa').value;\n");
        sb.append("            const status = document.getElementById('editar-m-status').value;\n");
        sb.append("            const vencimentoStr = document.getElementById('editar-m-vencimento').value;\n");
        sb.append("            const timestamp = new Date(vencimentoStr + 'T23:59:59').getTime();\n");
        sb.append("            fetch('/api/mensalistas/edit', {\n");
        sb.append("                method: 'POST',\n");
        sb.append("                headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-Admin-Token': cachedToken },\n");
        sb.append("                body: `placa=${placa}&status=${status}&vencimento=${timestamp}`\n");
        sb.append("            })\n");
        sb.append("            .then(r => r.json())\n");
        sb.append("            .then(data => {\n");
        sb.append("                if (data.success) {\n");
        sb.append("                    fecharModais();\n");
        sb.append("                    carregarMensalistas();\n");
        sb.append("                    mostrarToast('Assinatura atualizada com sucesso!');\n");
        sb.append("                } else { mostrarToast('Erro ao atualizar assinatura', 'error'); }\n");
        sb.append("            });\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        function toggleStatusMensalista(placa, statusAtual, vencimentoMs) {\n");
        sb.append("            const novoStatus = statusAtual === 'ATIVO' ? 'SUSPENSO' : 'ATIVO';\n");
        sb.append("            fetch('/api/mensalistas/edit', {\n");
        sb.append("                method: 'POST',\n");
        sb.append("                headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-Admin-Token': cachedToken },\n");
        sb.append("                body: `placa=${placa}&status=${novoStatus}&vencimento=${vencimentoMs}`\n");
        sb.append("            })\n");
        sb.append("            .then(r => r.json())\n");
        sb.append("            .then(data => {\n");
        sb.append("                if (data.success) {\n");
        sb.append("                    carregarMensalistas();\n");
        sb.append("                    mostrarToast(`Mensalista ${novoStatus === 'ATIVO' ? 'reativado' : 'suspenso'}!`);\n");
        sb.append("                } else { mostrarToast('Erro ao alterar status', 'error'); }\n");
        sb.append("            });\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        function deletarMensalista(placa) {\n");
        sb.append("            if (!confirm(`Deseja realmente excluir o mensalista de placa ${placa}?`)) return;\n");
        sb.append("            fetch('/api/mensalistas/delete', {\n");
        sb.append("                method: 'POST',\n");
        sb.append("                headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-Admin-Token': cachedToken },\n");
        sb.append("                body: `placa=${placa}`\n");
        sb.append("            })\n");
        sb.append("            .then(r => r.json())\n");
        sb.append("            .then(data => {\n");
        sb.append("                if (data.success) {\n");
        sb.append("                    carregarMensalistas();\n");
        sb.append("                    mostrarToast('Mensalista excluído com sucesso!');\n");
        sb.append("                } else { mostrarToast('Erro ao excluir mensalista', 'error'); }\n");
        sb.append("            });\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        // --- FATURAMENTO & CAIXA --- \n");
        sb.append("        function carregarTransacoes() {\n");
        sb.append("            fetch('/api/faturamento', {\n");
        sb.append("                headers: { 'X-Admin-Token': cachedToken }\n");
        sb.append("            })\n");
        sb.append("            .then(r => r.json())\n");
        sb.append("            .then(data => {\n");
        sb.append("                transacoesOriginais = data;\n");
        sb.append("                aplicarFiltrosData();\n");
        sb.append("            });\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        function setPresetPeriod(dias, btnElement) {\n");
        sb.append("            const parent = btnElement.parentNode;\n");
        sb.append("            parent.querySelectorAll('.preset-btn').forEach(b => b.classList.remove('active'));\n");
        sb.append("            btnElement.classList.add('active');\n");
        sb.append("            const hoje = new Date();\n");
        sb.append("            if (dias === 0) {\n");
        sb.append("                document.getElementById('filtro-data-inicio').value = '';\n");
        sb.append("                document.getElementById('filtro-data-fim').value = '';\n");
        sb.append("            } else {\n");
        sb.append("                const anterior = new Date(hoje.getTime() - (dias * 24 * 60 * 60 * 1000));\n");
        sb.append("                document.getElementById('filtro-data-inicio').value = anterior.toISOString().split('T')[0];\n");
        sb.append("                document.getElementById('filtro-data-fim').value = hoje.toISOString().split('T')[0];\n");
        sb.append("            }\n");
        sb.append("            aplicarFiltrosData();\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        function aplicarFiltrosData() {\n");
        sb.append("            const inicioStr = document.getElementById('filtro-data-inicio').value;\n");
        sb.append("            const fimStr = document.getElementById('filtro-data-fim').value;\n");
        sb.append("            let filtradas = transacoesOriginais;\n");
        sb.append("            if (inicioStr) {\n");
        sb.append("                const start = new Date(inicioStr + 'T00:00:00').getTime();\n");
        sb.append("                filtradas = filtradas.filter(t => t.horaSaida >= start);\n");
        sb.append("            }\n");
        sb.append("            if (fimStr) {\n");
        sb.append("                const end = new Date(fimStr + 'T23:59:59').getTime();\n");
        sb.append("                filtradas = filtradas.filter(t => t.horaSaida <= end);\n");
        sb.append("            }\n");
        sb.append("            renderizarAnalisesFaturamento(filtradas);\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        function renderizarAnalisesFaturamento(lista) {\n");
        sb.append("            let total = 0;\n");
        sb.append("            let permanenciaTotal = 0;\n");
        sb.append("            let ticketsValidos = 0;\n");
        sb.append("            const receitaDia = {};\n");
        sb.append("            const receitaTerminal = {};\n");
        sb.append("            const tbody = document.querySelector('#tabela-transacoes tbody');\n");
        sb.append("            tbody.innerHTML = '';\n");
        sb.append("\n");
        sb.append("            lista.forEach(t => {\n");
        sb.append("                total += t.valorPago;\n");
        sb.append("                const permanenciaMin = Math.round((t.horaSaida - t.horaEntrada) / (1000 * 60));\n");
        sb.append("                if (permanenciaMin > 0) {\n");
        sb.append("                    permanenciaTotal += permanenciaMin;\n");
        sb.append("                    ticketsValidos++;\n");
        sb.append("                }\n");
        sb.append("                // Agrupar dia\n");
        sb.append("                const diaStr = new Date(t.horaSaida).toLocaleDateString('pt-BR');\n");
        sb.append("                receitaDia[diaStr] = (receitaDia[diaStr] || 0) + t.valorPago;\n");
        sb.append("                // Agrupar terminal\n");
        sb.append("                const term = t.hardwareId || 'Terminal Desconhecido';\n");
        sb.append("                receitaTerminal[term] = (receitaTerminal[term] || 0) + t.valorPago;\n");
        sb.append("\n");
        sb.append("                // Inserir tabela\n");
        sb.append("                const ent = new Date(t.horaEntrada).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }) + ' ' + new Date(t.horaEntrada).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' });\n");
        sb.append("                const sai = new Date(t.horaSaida).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }) + ' ' + new Date(t.horaSaida).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' });\n");
        sb.append("                const r = document.createElement('tr');\n");
        sb.append("                r.innerHTML = `\n");
        sb.append("                    <td><span class=\"badge btn-secondary\" style=\"font-family: monospace; font-size: 13px;\">${t.placa}</span></td>\n");
        sb.append("                    <td>${ent}</td>\n");
        sb.append("                    <td>${sai}</td>\n");
        sb.append("                    <td>${formatarMinutos(permanenciaMin)}</td>\n");
        sb.append("                    <td style=\"font-weight: 600; color: var(--success);\">R$ ${t.valorPago.toFixed(2).replace('.', ',')}</td>\n");
        sb.append("                    <td><span class=\"badge btn-secondary\" style=\"font-size: 11px;\">${t.hardwareId.substring(0,8) || '-'}</span></td>\n");
        sb.append("                `;\n");
        sb.append("                tbody.appendChild(r);\n");
        sb.append("            });\n");
        sb.append("\n");
        sb.append("            if (lista.length === 0) {\n");
        sb.append("                tbody.innerHTML = '<tr><td colspan=\"6\" style=\"text-align: center; color: var(--text-muted); padding: 30px;\">Nenhuma transação encontrada no período</td></tr>';\n");
        sb.append("            }\n");
        sb.append("\n");
        sb.append("            // Atualizar KPIs\n");
        sb.append("            document.getElementById('total-faturamento').textContent = 'R$ ' + total.toFixed(2).replace('.', ',');\n");
        sb.append("            const permMedia = ticketsValidos > 0 ? Math.round(permanenciaTotal / ticketsValidos) : 0;\n");
        sb.append("            document.getElementById('media-permanencia').textContent = formatarMinutos(permMedia);\n");
        sb.append("            const tickMedio = ticketsValidos > 0 ? (total / ticketsValidos) : 0;\n");
        sb.append("            document.getElementById('ticket-medio').textContent = 'R$ ' + tickMedio.toFixed(2).replace('.', ',');\n");
        sb.append("\n");
        sb.append("            // Renderizar gráficos\n");
        sb.append("            renderizarGraficoLinha(receitaDia);\n");
        sb.append("            renderizarGraficoRosca(receitaTerminal);\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        function formatarMinutos(minutos) {\n");
        sb.append("            if (minutos < 60) return `${minutos} min`;\n");
        sb.append("            const h = Math.floor(minutos / 60);\n");
        sb.append("            const m = minutos % 60;\n");
        sb.append("            return m > 0 ? `${h}h ${m}m` : `${h}h`;\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        function filtrarTabelaTransacoes() {\n");
        sb.append("            const query = document.getElementById('busca-transacao').value.toLowerCase();\n");
        sb.append("            const trs = document.querySelectorAll('#tabela-transacoes tbody tr');\n");
        sb.append("            trs.forEach(tr => {\n");
        sb.append("                const text = tr.cells[0] ? tr.cells[0].textContent.toLowerCase() : '';\n");
        sb.append("                tr.style.display = text.includes(query) ? '' : 'none';\n");
        sb.append("            });\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        function renderizarGraficoLinha(dados) {\n");
        sb.append("            const ctx = document.getElementById('chart-receita-diaria').getContext('2d');\n");
        sb.append("            const labels = Object.keys(dados).reverse();\n");
        sb.append("            const valores = Object.values(dados).reverse();\n");
        sb.append("            if (chartReceita) chartReceita.destroy();\n");
        sb.append("            chartReceita = new Chart(ctx, {\n");
        sb.append("                type: 'line',\n");
        sb.append("                data: {\n");
        sb.append("                    labels: labels,\n");
        sb.append("                    datasets: [{\n");
        sb.append("                        label: 'Faturamento R$',\n");
        sb.append("                        data: valores,\n");
        sb.append("                        borderColor: '#6366f1',\n");
        sb.append("                        backgroundColor: 'rgba(99, 102, 241, 0.1)',\n");
        sb.append("                        borderWidth: 3,\n");
        sb.append("                        fill: true,\n");
        sb.append("                        tension: 0.3\n");
        sb.append("                    }]\n");
        sb.append("                },\n");
        sb.append("                options: {\n");
        sb.append("                    responsive: true, maintainAspectRatio: false,\n");
        sb.append("                    plugins: { legend: { display: false } },\n");
        sb.append("                    scales: {\n");
        sb.append("                        y: { grid: { color: 'rgba(255,255,255,0.05)' }, ticks: { color: '#9ca3af' } },\n");
        sb.append("                        x: { grid: { display: false }, ticks: { color: '#9ca3af' } }\n");
        sb.append("                    }\n");
        sb.append("                }\n");
        sb.append("            });\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        function renderizarGraficoRosca(dados) {\n");
        sb.append("            const ctx = document.getElementById('chart-receita-terminal').getContext('2d');\n");
        sb.append("            const labels = Object.keys(dados).map(k => k.substring(0,8));\n");
        sb.append("            const valores = Object.values(dados);\n");
        sb.append("            if (chartTerminal) chartTerminal.destroy();\n");
        sb.append("            chartTerminal = new Chart(ctx, {\n");
        sb.append("                type: 'doughnut',\n");
        sb.append("                data: {\n");
        sb.append("                    labels: labels,\n");
        sb.append("                    datasets: [{\n");
        sb.append("                        data: valores,\n");
        sb.append("                        backgroundColor: ['#6366f1', '#10b981', '#f59e0b', '#ec4899', '#3b82f6', '#8b5cf6'],\n");
        sb.append("                        borderWidth: 0\n");
        sb.append("                    }]\n");
        sb.append("                },\n");
        sb.append("                options: {\n");
        sb.append("                    responsive: true, maintainAspectRatio: false,\n");
        sb.append("                    plugins: { legend: { position: 'bottom', labels: { color: '#f3f4f6', boxWidth: 12 } } }\n");
        sb.append("                }\n");
        sb.append("            });\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        // --- TERMINAIS & DISPOSITIVOS ---\n");
        sb.append("        function carregarTerminais() {\n");
        sb.append("            fetch('/api/devices', {\n");
        sb.append("                headers: { 'X-Admin-Token': cachedToken }\n");
        sb.append("            })\n");
        sb.append("            .then(r => r.json())\n");
        sb.append("            .then(data => {\n");
        sb.append("                document.getElementById('count-terminais').textContent = data.filter(t => t.status === 'APROVADO').length;\n");
        sb.append("                renderizarTerminais(data);\n");
        sb.append("            });\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        function renderizarTerminais(lista) {\n");
        sb.append("            const tbody = document.querySelector('#tabela-terminais tbody');\n");
        sb.append("            tbody.innerHTML = '';\n");
        sb.append("            if (lista.length === 0) {\n");
        sb.append("                tbody.innerHTML = '<tr><td colspan=\"8\" style=\"text-align: center; color: var(--text-muted); padding: 30px;\">Nenhum terminal identificado</td></tr>';\n");
        sb.append("                return;\n");
        sb.append("            }\n");
        sb.append("            lista.forEach(t => {\n");
        sb.append("                const expStr = t.dataExpiracao > 0 ? new Date(t.dataExpiracao).toLocaleDateString('pt-BR') : '-';\n");
        sb.append("                const status = t.status ? t.status.toUpperCase() : 'PENDENTE';\n");
        sb.append("                let badge = 'badge-warning';\n");
        sb.append("                if (status === 'ATIVO') badge = 'badge-success';\n");
        sb.append("                if (status === 'BLOQUEADO') badge = 'badge-danger';\n");
        sb.append("\n");
        sb.append("                const r = document.createElement('tr');\n");
        sb.append("                r.innerHTML = `\n");
        sb.append("                    <td>\n");
        sb.append("                        <div style=\"font-weight: 600;\">${t.nomeAparelho}</div>\n");
        sb.append("                        <div style=\"font-size: 12px; color: var(--text-muted);\"><i class=\"fa-brands ${t.soTipo === 'Android' ? 'fa-android' : 'fa-windows'}\"></i> ${t.soTipo}</div>\n");
        sb.append("                    </td>\n");
        sb.append("                    <td style=\"font-family: monospace; font-size: 13px;\">${t.hardwareId}</td>\n");
        sb.append("                    <td>\n");
        sb.append("                        <div style=\"font-weight: 500;\">${t.nomeCliente || 'Pátio não configurado'}</div>\n");
        sb.append("                        ${t.nomeClientePendente ? `<div style=\"font-size:11px; color:var(--warning);\">Pendente: ${t.nomeClientePendente}</div>` : ''}\n");
        sb.append("                    </td>\n");
        sb.append("                    <td>R$ ${t.tarifaHora.toFixed(2).replace('.', ',')}</td>\n");
        sb.append("                    <td>🚗 ${t.vagasCarro} / 🏍️ ${t.vagasMoto}</td>\n");
        sb.append("                    <td>${expStr}</td>\n");
        sb.append("                    <td><span class=\"badge ${badge}\">${status}</span></td>\n");
        sb.append("                    <td style=\"text-align: right; display: flex; gap: 8px; justify-content: flex-end;\">\n");
        sb.append("                        ${status !== 'ATIVO' ? `<button class=\"btn btn-primary\" style=\"padding: 8px 12px; font-size: 12px;\" onclick=\"abrirModalAprovarTerminal('${t.hardwareId}', ${t.diasLicencaPendente || 30})\"><i class=\"fa-solid fa-key\"></i> Liberar</button>` : ''}\n");
        sb.append("                        ${status === 'ATIVO' ? `<button class=\"btn btn-secondary\" style=\"padding: 8px 12px; font-size: 12px;\" onclick=\"abrirModalConfigTerminal('${t.hardwareId}', '${t.nomeCliente || ''}', ${t.tarifaHora}, ${t.vagasCarro}, ${t.vagasMoto})\"><i class=\"fa-solid fa-gear\"></i> Configurar</button>` : ''}\n");
        sb.append("                        ${status === 'ATIVO' ? `<button class=\"btn btn-secondary\" style=\"padding: 8px 12px; font-size: 12px; color: var(--danger); border-color: rgba(239,68,68,0.2);\" onclick=\"bloquearTerminal('${t.hardwareId}')\"><i class=\"fa-solid fa-ban\"></i> Bloquear</button>` : ''}\n");
        sb.append("                    </td>\n");
        sb.append("                `;\n");
        sb.append("                tbody.appendChild(r);\n");
        sb.append("            });\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        function abrirModalAprovarTerminal(hwid, dias) {\n");
        sb.append("            document.getElementById('aprovar-t-hwid').value = hwid;\n");
        sb.append("            document.getElementById('aprovar-t-dias').value = dias || 30;\n");
        sb.append("            document.getElementById('modal-aprovar-terminal').style.display = 'flex';\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        function salvarAprovacaoTerminal() {\n");
        sb.append("            const hwid = document.getElementById('aprovar-t-hwid').value;\n");
        sb.append("            const dias = document.getElementById('aprovar-t-dias').value;\n");
        sb.append("            fetch('/api/approve', {\n");
        sb.append("                method: 'POST',\n");
        sb.append("                headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-Admin-Token': cachedToken },\n");
        sb.append("                body: `hardwareId=${hwid}&dias=${dias}`\n");
        sb.append("            })\n");
        sb.append("            .then(r => r.json())\n");
        sb.append("            .then(data => {\n");
        sb.append("                if (data.success) {\n");
        sb.append("                    fecharModais();\n");
        sb.append("                    carregarTerminais();\n");
        sb.append("                    mostrarToast('Terminal aprovado com sucesso!');\n");
        sb.append("                } else { mostrarToast('Erro ao aprovar terminal', 'error'); }\n");
        sb.append("            });\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        function abrirModalConfigTerminal(hwid, nome, tarifa, vagasCarro, vagasMoto) {\n");
        sb.append("            document.getElementById('config-t-hwid').value = hwid;\n");
        sb.append("            document.getElementById('config-t-nome').value = nome;\n");
        sb.append("            document.getElementById('config-t-tarifa').value = tarifa.toFixed(2);\n");
        sb.append("            document.getElementById('config-t-carro').value = vagasCarro;\n");
        sb.append("            document.getElementById('config-t-moto').value = vagasMoto;\n");
        sb.append("            document.getElementById('modal-configurar-terminal').style.display = 'flex';\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        function salvarConfigTerminal() {\n");
        sb.append("            const hwid = document.getElementById('config-t-hwid').value;\n");
        sb.append("            const nome = document.getElementById('config-t-nome').value;\n");
        sb.append("            const tarifa = document.getElementById('config-t-tarifa').value;\n");
        sb.append("            const vagasCarro = document.getElementById('config-t-carro').value;\n");
        sb.append("            const vagasMoto = document.getElementById('config-t-moto').value;\n");
        sb.append("            fetch('/api/update-config', {\n");
        sb.append("                method: 'POST',\n");
        sb.append("                headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-Admin-Token': cachedToken },\n");
        sb.append("                body: 'hardwareId=' + encodeURIComponent(hwid) + '&nomeCliente=' + encodeURIComponent(nome) + '&tarifaHora=' + encodeURIComponent(tarifa) + '&vagasCarro=' + encodeURIComponent(vagasCarro) + '&vagasMoto=' + encodeURIComponent(vagasMoto)\n");
        sb.append("            }).then(() => {\n");
        sb.append("                return fetch('/api/send-config', {\n");
        sb.append("                    method: 'POST',\n");
        sb.append("                    headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-Admin-Token': cachedToken },\n");
        sb.append("                    body: 'hardwareId=' + encodeURIComponent(hwid)\n");
        sb.append("                });\n");
        sb.append("            }).then(() => {\n");
        sb.append("                fecharModais();\n");
        sb.append("                carregarTerminais();\n");
        sb.append("                mostrarToast('Configurações salvas e aplicadas no terminal!');\n");
        sb.append("            });\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        function bloquearTerminal(hwid) {\n");
        sb.append("            if (!confirm(`Deseja realmente bloquear a licença do terminal ${hwid}?`)) return;\n");
        sb.append("            fetch('/api/block', {\n");
        sb.append("                method: 'POST',\n");
        sb.append("                headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-Admin-Token': cachedToken },\n");
        sb.append("                body: `hardwareId=${hwid}`\n");
        sb.append("            })\n");
        sb.append("            .then(r => r.json())\n");
        sb.append("            .then(data => {\n");
        sb.append("                if (data.success) {\n");
        sb.append("                    carregarTerminais();\n");
        sb.append("                    mostrarToast('Terminal bloqueado com sucesso!');\n");
        sb.append("                } else { mostrarToast('Erro ao bloquear terminal', 'error'); }\n");
        sb.append("            });\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        // --- LOGS DE AUDITORIA DE SEGURANÇA ---\n");
        sb.append("        function carregarLogs() {\n");
        sb.append("            fetch('/api/audit-logs', {\n");
        sb.append("                headers: { 'X-Admin-Token': cachedToken }\n");
        sb.append("            })\n");
        sb.append("            .then(r => r.json())\n");
        sb.append("            .then(data => {\n");
        sb.append("                const box = document.getElementById('box-logs');\n");
        sb.append("                box.innerHTML = '';\n");
        sb.append("                if (data.length === 0) {\n");
        sb.append("                    box.innerHTML = '<div style=\"color: var(--text-muted); text-align: center; padding: 20px;\">Nenhum log de auditoria registrado ainda.</div>';\n");
        sb.append("                    return;\n");
        sb.append("                }\n");
        sb.append("                data.slice().reverse().forEach(log => {\n");
        sb.append("                    const d = document.createElement('div');\n");
        sb.append("                    d.style.marginBottom = '6px';\n");
        sb.append("                    // Destacar alertas importantes\n");
        sb.append("                    if (log.includes('[CRITICAL]') || log.includes('AVARIA') || log.includes('INCOMPATIBILIDADE')) {\n");
        sb.append("                        d.style.color = '#ef4444';\n");
        sb.append("                        d.innerHTML = `<i class=\"fa-solid fa-triangle-exclamation\"></i> ${log}`;\n");
        sb.append("                    } else if (log.includes('[PAGAMENTO]') || log.includes('SUCESSO')) {\n");
        sb.append("                        d.style.color = '#10b981';\n");
        sb.append("                        d.innerHTML = `<i class=\"fa-solid fa-circle-check\"></i> ${log}`;\n");
        sb.append("                    } else {\n");
        sb.append("                        d.style.color = '#9ca3af';\n");
        sb.append("                        d.innerHTML = `<i class=\"fa-solid fa-circle-info\"></i> ${log}`;\n");
        sb.append("                    }\n");
        sb.append("                    box.appendChild(d);\n");
        sb.append("                });\n");
        sb.append("            });\n");
        sb.append("        }\n");
        sb.append("    </script>\n");
        sb.append("</body>\n");
        sb.append("</html>\n");
        return sb.toString();
    }

    // --- ENDPOINTS DE AUTOATENDIMENTO B2C ---

    private static class AutoatendimentoPagarHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "*");
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");

            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String html = obterHtmlPagar();
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private String obterHtmlPagar() {
            StringBuilder sb = new StringBuilder();
            sb.append("<!DOCTYPE html>\n");
            sb.append("<html lang=\"pt-BR\">\n");
            sb.append("<head>\n");
            sb.append("    <meta charset=\"UTF-8\">\n");
            sb.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
            sb.append("    <title>Autoatendimento — Park '31</title>\n");
            sb.append("    <link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">\n");
            sb.append("    <style>\n");
            sb.append("        @import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;600;800&display=swap');\n");
            sb.append("        * {\n");
            sb.append("            box-sizing: border-box;\n");
            sb.append("            margin: 0;\n");
            sb.append("            padding: 0;\n");
            sb.append("        }\n");
            sb.append("        body {\n");
            sb.append("            font-family: 'Inter', sans-serif;\n");
            sb.append("            background-color: #0F172A;\n");
            sb.append("            color: #F8FAFC;\n");
            sb.append("            display: flex;\n");
            sb.append("            justify-content: center;\n");
            sb.append("            align-items: center;\n");
            sb.append("            min-height: 100vh;\n");
            sb.append("            padding: 20px;\n");
            sb.append("            transition: background-color 0.5s ease;\n");
            sb.append("        }\n");
            sb.append("        .container {\n");
            sb.append("            background-color: #1E293B;\n");
            sb.append("            width: 100%;\n");
            sb.append("            max-width: 440px;\n");
            sb.append("            border-radius: 24px;\n");
            sb.append("            padding: 30px;\n");
            sb.append("            border: 1px solid #334155;\n");
            sb.append("            box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);\n");
            sb.append("            text-align: center;\n");
            sb.append("            position: relative;\n");
            sb.append("            overflow: hidden;\n");
            sb.append("        }\n");
            sb.append("        .header {\n");
            sb.append("            margin-bottom: 24px;\n");
            sb.append("        }\n");
            sb.append("        .logo {\n");
            sb.append("            font-weight: 800;\n");
            sb.append("            font-size: 24px;\n");
            sb.append("            color: #38BDF8;\n");
            sb.append("            letter-spacing: -1px;\n");
            sb.append("            margin-bottom: 6px;\n");
            sb.append("        }\n");
            sb.append("        .logo span {\n");
            sb.append("            color: #818CF8;\n");
            sb.append("        }\n");
            sb.append("        .subtitle {\n");
            sb.append("            color: #94A3B8;\n");
            sb.append("            font-size: 14px;\n");
            sb.append("        }\n");
            sb.append("        .plate-box {\n");
            sb.append("            background: #FFFFFF;\n");
            sb.append("            border: 4px solid #475569;\n");
            sb.append("            border-radius: 12px;\n");
            sb.append("            padding: 12px;\n");
            sb.append("            display: inline-flex;\n");
            sb.append("            flex-direction: column;\n");
            sb.append("            align-items: center;\n");
            sb.append("            justify-content: center;\n");
            sb.append("            width: 100%;\n");
            sb.append("            max-width: 260px;\n");
            sb.append("            margin: 16px auto;\n");
            sb.append("            box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.3);\n");
            sb.append("        }\n");
            sb.append("        .plate-header {\n");
            sb.append("            background: #1E40AF;\n");
            sb.append("            color: #FFFFFF;\n");
            sb.append("            font-size: 10px;\n");
            sb.append("            font-weight: 800;\n");
            sb.append("            letter-spacing: 2px;\n");
            sb.append("            width: 100%;\n");
            sb.append("            text-align: center;\n");
            sb.append("            padding: 2px 0;\n");
            sb.append("            border-radius: 4px;\n");
            sb.append("            margin-bottom: 6px;\n");
            sb.append("        }\n");
            sb.append("        .plate-text {\n");
            sb.append("            color: #0F172A;\n");
            sb.append("            font-size: 32px;\n");
            sb.append("            font-weight: 800;\n");
            sb.append("            letter-spacing: 4px;\n");
            sb.append("        }\n");
            sb.append("        .details-box {\n");
            sb.append("            background-color: #0F172A;\n");
            sb.append("            border-radius: 16px;\n");
            sb.append("            padding: 16px;\n");
            sb.append("            margin-bottom: 24px;\n");
            sb.append("            border: 1px solid #334155;\n");
            sb.append("            text-align: left;\n");
            sb.append("        }\n");
            sb.append("        .detail-row {\n");
            sb.append("            display: flex;\n");
            sb.append("            justify-content: space-between;\n");
            sb.append("            margin-bottom: 12px;\n");
            sb.append("            font-size: 14px;\n");
            sb.append("        }\n");
            sb.append("        .detail-row:last-child {\n");
            sb.append("            margin-bottom: 0;\n");
            sb.append("            padding-top: 12px;\n");
            sb.append("            border-top: 1px dashed #334155;\n");
            sb.append("        }\n");
            sb.append("        .detail-label {\n");
            sb.append("            color: #94A3B8;\n");
            sb.append("        }\n");
            sb.append("        .detail-value {\n");
            sb.append("            color: #F8FAFC;\n");
            sb.append("            font-weight: 600;\n");
            sb.append("        }\n");
            sb.append("        .total-value {\n");
            sb.append("            color: #F59E0B;\n");
            sb.append("            font-size: 20px;\n");
            sb.append("            font-weight: 800;\n");
            sb.append("        }\n");
            sb.append("        .btn {\n");
            sb.append("            background-color: #38BDF8;\n");
            sb.append("            color: #0F172A;\n");
            sb.append("            font-weight: 700;\n");
            sb.append("            font-size: 16px;\n");
            sb.append("            padding: 16px;\n");
            sb.append("            width: 100%;\n");
            sb.append("            border: none;\n");
            sb.append("            border-radius: 14px;\n");
            sb.append("            cursor: pointer;\n");
            sb.append("            transition: all 0.2s ease;\n");
            sb.append("            box-shadow: 0 4px 14px 0 rgba(56, 189, 248, 0.4);\n");
            sb.append("            display: flex;\n");
            sb.append("            justify-content: center;\n");
            sb.append("            align-items: center;\n");
            sb.append("            gap: 10px;\n");
            sb.append("        }\n");
            sb.append("        .btn:hover {\n");
            sb.append("            transform: translateY(-2px);\n");
            sb.append("            box-shadow: 0 6px 20px 0 rgba(56, 189, 248, 0.6);\n");
            sb.append("            background-color: #7DD3FC;\n");
            sb.append("        }\n");
            sb.append("        .qr-section {\n");
            sb.append("            display: none;\n");
            sb.append("            margin-top: 24px;\n");
            sb.append("            padding-top: 24px;\n");
            sb.append("            border-top: 1px solid #334155;\n");
            sb.append("            animation: fadeIn 0.4s ease forwards;\n");
            sb.append("        }\n");
            sb.append("        .qr-code-container {\n");
            sb.append("            width: 200px;\n");
            sb.append("            height: 200px;\n");
            sb.append("            border-radius: 12px;\n");
            sb.append("            padding: 10px;\n");
            sb.append("            background: white;\n");
            sb.append("            margin: 16px auto;\n");
            sb.append("            box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.3);\n");
            sb.append("        }\n");
            sb.append("        .pix-copia-cola {\n");
            sb.append("            background-color: #0F172A;\n");
            sb.append("            border: 1px solid #334155;\n");
            sb.append("            border-radius: 10px;\n");
            sb.append("            padding: 12px;\n");
            sb.append("            font-size: 12px;\n");
            sb.append("            width: 100%;\n");
            sb.append("            text-overflow: ellipsis;\n");
            sb.append("            white-space: nowrap;\n");
            sb.append("            overflow: hidden;\n");
            sb.append("            margin-bottom: 12px;\n");
            sb.append("            color: #94A3B8;\n");
            sb.append("            font-family: monospace;\n");
            sb.append("            cursor: pointer;\n");
            sb.append("            text-align: center;\n");
            sb.append("        }\n");
            sb.append("        .status-badge {\n");
            sb.append("            display: inline-flex;\n");
            sb.append("            align-items: center;\n");
            sb.append("            gap: 8px;\n");
            sb.append("            padding: 8px 16px;\n");
            sb.append("            border-radius: 20px;\n");
            sb.append("            font-size: 13px;\n");
            sb.append("            font-weight: 600;\n");
            sb.append("            background-color: rgba(245, 158, 11, 0.1);\n");
            sb.append("            color: #FBBF24;\n");
            sb.append("            border: 1px solid rgba(245, 158, 11, 0.3);\n");
            sb.append("        }\n");
            sb.append("        .status-badge.pago {\n");
            sb.append("            background-color: rgba(34, 197, 94, 0.1);\n");
            sb.append("            color: #4ADE80;\n");
            sb.append("            border: 1px solid rgba(34, 197, 94, 0.3);\n");
            sb.append("        }\n");
            sb.append("        .success-screen {\n");
            sb.append("            display: none;\n");
            sb.append("            text-align: center;\n");
            sb.append("            animation: scaleIn 0.5s cubic-bezier(0.34, 1.56, 0.64, 1) forwards;\n");
            sb.append("        }\n");
            sb.append("        .success-icon {\n");
            sb.append("            font-size: 64px;\n");
            sb.append("            color: #22C55E;\n");
            sb.append("            margin-bottom: 20px;\n");
            sb.append("            filter: drop-shadow(0 0 15px rgba(34, 197, 94, 0.4));\n");
            sb.append("        }\n");
            sb.append("        @keyframes fadeIn {\n");
            sb.append("            from { opacity: 0; transform: translateY(10px); }\n");
            sb.append("            to { opacity: 1; transform: translateY(0); }\n");
            sb.append("        }\n");
            sb.append("        @keyframes scaleIn {\n");
            sb.append("            from { opacity: 0; transform: scale(0.9); }\n");
            sb.append("            to { opacity: 1; transform: scale(1); }\n");
            sb.append("        }\n");
            sb.append("    </style>\n");
            sb.append("</head>\n");
            sb.append("<body>\n");
            sb.append("    <div class=\"container\" id=\"main-container\">\n");
            sb.append("        <div id=\"payment-screen\">\n");
            sb.append("            <div class=\"header\">\n");
            sb.append("                <div class=\"logo\">Park <span>'31</span></div>\n");
            sb.append("                <div class=\"subtitle\">Autoatendimento B2C — Ticket Digital</div>\n");
            sb.append("            </div>\n");
            sb.append("            <div class=\"plate-box\">\n");
            sb.append("                <div class=\"plate-header\">MERCOSUL</div>\n");
            sb.append("                <div class=\"plate-text\" id=\"lbl-placa\">---</div>\n");
            sb.append("            </div>\n");
            sb.append("            <div class=\"details-box\">\n");
            sb.append("                <div class=\"detail-row\">\n");
            sb.append("                    <span class=\"detail-label\">Entrada</span>\n");
            sb.append("                    <span class=\"detail-value\" id=\"lbl-entrada\">--:--:--</span>\n");
            sb.append("                </div>\n");
            sb.append("                <div class=\"detail-row\">\n");
            sb.append("                    <span class=\"detail-label\">Tempo Decorrido</span>\n");
            sb.append("                    <span class=\"detail-value\" id=\"lbl-tempo\">-- min</span>\n");
            sb.append("                </div>\n");
            sb.append("                <div class=\"detail-row\">\n");
            sb.append("                    <span class=\"detail-label\">Valor por Hora</span>\n");
            sb.append("                    <span class=\"detail-value\" id=\"lbl-tarifa-hora\">R$ --</span>\n");
            sb.append("                </div>\n");
            sb.append("                <div class=\"detail-row\">\n");
            sb.append("                    <span class=\"detail-label\">Total Acumulado</span>\n");
            sb.append("                    <span class=\"total-value\" id=\"lbl-total\">R$ --</span>\n");
            sb.append("                </div>\n");
            sb.append("            </div>\n");
            sb.append("            <button class=\"btn\" id=\"btn-gerar-pix\" onclick=\"gerarCobrancaPix()\">\n");
            sb.append("                <i class=\"fa-brands fa-pix\"></i> Pagar com Pix\n");
            sb.append("            </button>\n");
            sb.append("            <div class=\"qr-section\" id=\"qr-section\">\n");
            sb.append("                <div class=\"status-badge\" id=\"status-badge\">\n");
            sb.append("                    <i class=\"fa-solid fa-spinner fa-spin\"></i> Aguardando Pagamento...\n");
            sb.append("                </div>\n");
            sb.append("                <div class=\"qr-code-container\">\n");
            sb.append("                    <img id=\"img-qrcode\" src=\"\" alt=\"QR Code Pix\" style=\"width: 100%; height: 100%;\">\n");
            sb.append("                </div>\n");
            sb.append("                <div class=\"pix-copia-cola\" id=\"pix-raw\" onclick=\"copiarPix()\">\n");
            sb.append("                    Clique aqui para copiar chave Pix Copia e Cola\n");
            sb.append("                </div>\n");
            sb.append("                <div style=\"font-size: 11px; color: #64748B;\">\n");
            sb.append("                    O sandbox aprovará automaticamente em 6 segundos após a geração.\n");
            sb.append("                </div>\n");
            sb.append("            </div>\n");
            sb.append("        </div>\n");
            sb.append("        <div class=\"success-screen\" id=\"success-screen\">\n");
            sb.append("            <i class=\"fa-solid fa-circle-check success-icon\"></i>\n");
            sb.append("            <h2 style=\"font-size: 24px; font-weight: 800; color: #4ADE80; margin-bottom: 12px;\">PAGAMENTO APROVADO!</h2>\n");
            sb.append("            <p style=\"color: #94A3B8; font-size: 15px; margin-bottom: 24px; line-height: 1.5;\">\n");
            sb.append("                Obrigado! A saída do veículo <strong style=\"color: #F8FAFC;\" id=\"success-placa\">---</strong> foi liberada no sistema. Você pode se dirigir à cancela de saída imediatamente.\n");
            sb.append("            </p>\n");
            sb.append("            <div class=\"status-badge pago\">\n");
            sb.append("                <i class=\"fa-solid fa-lock-open\"></i> Saída Liberada\n");
            sb.append("            </div>\n");
            sb.append("        </div>\n");
            sb.append("    </div>\n");
            sb.append("    <script>\n");
            sb.append("        const params = new URLSearchParams(window.location.search);\n");
            sb.append("        const placa = params.get('placa') ? params.get('placa').toUpperCase() : '';\n");
            sb.append("        const entrada = parseInt(params.get('entrada') || Date.now());\n");
            sb.append("        document.getElementById('lbl-placa').innerText = placa || 'PLACA-BR';\n");
            sb.append("        document.getElementById('lbl-entrada').innerText = new Date(entrada).toLocaleString('pt-BR');\n");
            sb.append("        let valorCalculado = 0;\n");
            sb.append("        let pollingInterval = null;\n");
            sb.append("        async function atualizarTempoEValor() {\n");
            sb.append("            const agora = Date.now();\n");
            sb.append("            const elapsed = agora - entrada;\n");
            sb.append("            const minutos = Math.max(1, Math.floor(elapsed / 60000));\n");
            sb.append("            const horas = Math.ceil(minutos / 60);\n");
            sb.append("            let tarifaHora = 5.0;\n");
            sb.append("            try {\n");
            sb.append("                const tRes = await fetch('/api/faturamento');\n");
            sb.append("                const tData = await tRes.json();\n");
            sb.append("                // O faturamento ou config pode conter a tarifa ativa\n");
            sb.append("            } catch(e) {}\n");
            sb.append("            valorCalculado = horas * tarifaHora;\n");
            sb.append("            document.getElementById('lbl-tempo').innerText = `${minutos} min (${horas}h)`;\n");
            sb.append("            document.getElementById('lbl-tarifa-hora').innerText = `R$ ${tarifaHora.toFixed(2)}`;\n");
            sb.append("            document.getElementById('lbl-total').innerText = `R$ ${valorCalculado.toFixed(2)}`;\n");
            sb.append("        }\n");
            sb.append("        atualizarTempoEValor();\n");
            sb.append("        setInterval(atualizarTempoEValor, 10000);\n");
            sb.append("        async function gerarCobrancaPix() {\n");
            sb.append("            const btn = document.getElementById('btn-gerar-pix');\n");
            sb.append("            btn.disabled = true;\n");
            sb.append("            btn.innerHTML = '<i class=\"fa-solid fa-circle-notch fa-spin\"></i> Gerando...';\n");
            sb.append("            try {\n");
            sb.append("                const res = await fetch('/api/pix/create', {\n");
            sb.append("                    method: 'POST',\n");
            sb.append("                    headers: { 'Content-Type': 'application/json' },\n");
            sb.append("                    body: JSON.stringify({ placa: placa, valor: valorCalculado })\n");
            sb.append("                });\n");
            sb.append("                const data = await res.json();\n");
            sb.append("                if (data.success) {\n");
            sb.append("                    const payload = data.payload;\n");
            sb.append("                    const txid = data.txid;\n");
            sb.append("                    await fetch('/api/autoatendimento/status', {\n");
            sb.append("                        method: 'POST',\n");
            sb.append("                        headers: { 'Content-Type': 'application/json' },\n");
            sb.append("                        body: JSON.stringify({\n");
            sb.append("                            acao: 'REGISTRAR',\n");
            sb.append("                            placa: placa,\n");
            sb.append("                            entrada: entrada,\n");
            sb.append("                            valor: valorCalculado,\n");
            sb.append("                            txid: txid\n");
            sb.append("                        })\n");
            sb.append("                    });\n");
            sb.append("                    document.getElementById('img-qrcode').src = `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(payload)}`;\n");
            sb.append("                    document.getElementById('pix-raw').setAttribute('data-payload', payload);\n");
            sb.append("                    document.getElementById('qr-section').style.display = 'block';\n");
            sb.append("                    btn.style.display = 'none';\n");
            sb.append("                    iniciarPollingStatus(txid);\n");
            sb.append("                } else {\n");
            sb.append("                    alert('Erro ao gerar cobrança. Tente novamente.');\n");
            sb.append("                    btn.disabled = false;\n");
            sb.append("                    btn.innerHTML = '<i class=\"fa-brands fa-pix\"></i> Pagar com Pix';\n");
            sb.append("                }\n");
            sb.append("            } catch (err) {\n");
            sb.append("                alert('Erro de conexão com o servidor de pagamento: ' + err.message);\n");
            sb.append("                btn.disabled = false;\n");
            sb.append("                btn.innerHTML = '<i class=\"fa-brands fa-pix\"></i> Pagar com Pix';\n");
            sb.append("            }\n");
            sb.append("        }\n");
            sb.append("        function copiarPix() {\n");
            sb.append("            const payload = document.getElementById('pix-raw').getAttribute('data-payload');\n");
            sb.append("            navigator.clipboard.writeText(payload);\n");
            sb.append("            const rawBox = document.getElementById('pix-raw');\n");
            sb.append("            rawBox.innerText = 'Copiado para a área de transferência!';\n");
            sb.append("            rawBox.style.color = '#4ADE80';\n");
            sb.append("            setTimeout(() => {\n");
            sb.append("                rawBox.innerText = 'Clique aqui para copiar chave Pix Copia e Cola';\n");
            sb.append("                rawBox.style.color = '#94A3B8';\n");
            sb.append("            }, 2500);\n");
            sb.append("        }\n");
            sb.append("        function iniciarPollingStatus(txid) {\n");
            sb.append("            pollingInterval = setInterval(async () => {\n");
            sb.append("                try {\n");
            sb.append("                    const res = await fetch('/api/pix/status', {\n");
            sb.append("                        method: 'POST',\n");
            sb.append("                        headers: { 'Content-Type': 'application/json' },\n");
            sb.append("                        body: JSON.stringify({ txid: txid })\n");
            sb.append("                    });\n");
            sb.append("                    const data = await res.json();\n");
            sb.append("                    if (data.status === 'APROVADO') {\n");
            sb.append("                        clearInterval(pollingInterval);\n");
            sb.append("                        exibirSucesso();\n");
            sb.append("                    }\n");
            sb.append("                } catch (err) {\n");
            sb.append("                    console.error('Erro de polling Pix:', err);\n");
            sb.append("                }\n");
            sb.append("            }, 2000);\n");
            sb.append("        }\n");
            sb.append("        function exibirSucesso() {\n");
            sb.append("            document.getElementById('payment-screen').style.display = 'none';\n");
            sb.append("            document.getElementById('success-placa').innerText = placa || 'PLACA-BR';\n");
            sb.append("            document.getElementById('success-screen').style.display = 'block';\n");
            sb.append("            document.body.style.backgroundColor = '#064E3B';\n");
            sb.append("        }\n");
            sb.append("    </script>\n");
            sb.append("</body>\n");
            sb.append("</html>\n");
            return sb.toString();
        }
    }

    private static class AutoatendimentoStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                // Obter status de pagamento por placa
                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                String placa = params.get("placa");
                String responseJson = "{\"status\":\"NENHUM\"}";
                
                if (placa != null && !placa.isEmpty()) {
                    try {
                        EstacionamentoRepository.AutoatendimentoInfo info = repository.obterAutoatendimento(placa);
                        if (info != null) {
                            responseJson = String.format(Locale.US,
                                "{\"status\":\"%s\",\"valor\":%.2f,\"entrada\":%d,\"saida\":%d}",
                                info.getStatus(), info.getValorPago(), info.getHoraEntrada(), info.getHoraSaida()
                            );
                        }
                    } catch (Exception ignored) {}
                }
                
                byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                // Registrar autoatendimento
                Map<String, String> params = parseRequestBody(exchange.getRequestBody());
                String acao = params.get("acao");
                String responseJson = "{\"success\":false}";

                if ("REGISTRAR".equals(acao)) {
                    String placa = params.get("placa");
                    String entradaStr = params.get("entrada");
                    String valorStr = params.get("valor");
                    String txid = params.get("txid");

                    if (placa != null && entradaStr != null && valorStr != null && txid != null) {
                        try {
                            long entrada = Long.parseLong(entradaStr);
                            double valor = Double.parseDouble(valorStr);
                            repository.registrarAutoatendimento(placa, entrada, valor, "PENDENTE", txid);
                            responseJson = "{\"success\":true}";
                            auditLogs.add(String.format("[%s] [INFO] Cobrança autoatendimento gerada para placa %s: R$ %.2f",
                                    new SimpleDateFormat("dd/MM HH:mm:ss").format(new Date()), placa, valor));
                        } catch (Exception ignored) {}
                    }
                }

                byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            try {
                String k = URLDecoder.decode(kv[0], "UTF-8");
                String v = kv.length > 1 ? URLDecoder.decode(kv[1], "UTF-8") : "";
                params.put(k, v);
            } catch (Exception ignored) {}
        }
        return params;
    }
}
