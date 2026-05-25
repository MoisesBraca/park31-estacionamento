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

    public static void main(String[] args) {
        iniciar(8080);
        System.out.println("Servidor de Licenciamento rodando em modo stand-alone (Headless). Pressione Ctrl+C para encerrar.");
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
        return "<!DOCTYPE html>\n" +
               "<html lang=\"pt-BR\">\n" +
               "<head>\n" +
               "    <meta charset=\"UTF-8\">\n" +
               "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
               "    <title>Painel Admin - Licenciamento Park ' 31</title>\n" +
                "    <link href=\"https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;600;800&display=swap\" rel=\"stylesheet\">\n" +
                "    <script src=\"https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js\"></script>\n" +
               "    <style>\n" +
               "        :root {\n" +
               "            --bg: #0f0f1a;\n" +
               "            --surface: rgba(25, 25, 45, 0.6);\n" +
               "            --surface-hover: rgba(35, 35, 60, 0.8);\n" +
               "            --primary: #5856d6;\n" +
               "            --primary-glow: rgba(88, 86, 214, 0.4);\n" +
               "            --success: #34c759;\n" +
               "            --danger: #ff3b30;\n" +
               "            --pending: #ff9500;\n" +
               "            --text: #f0f0f5;\n" +
               "            --text-muted: #8e8e93;\n" +
               "        }\n" +
               "        * {\n" +
               "            box-sizing: border-box;\n" +
               "            margin: 0;\n" +
               "            padding: 0;\n" +
               "            font-family: 'Outfit', sans-serif;\n" +
               "        }\n" +
               "        body {\n" +
               "            background: var(--bg);\n" +
               "            color: var(--text);\n" +
               "            min-height: 100vh;\n" +
               "            padding: 30px;\n" +
               "            background-image: radial-gradient(circle at 10% 20%, rgba(88, 86, 214, 0.1) 0%, transparent 40%),\n" +
               "                              radial-gradient(circle at 90% 80%, rgba(255, 149, 0, 0.05) 0%, transparent 40%);\n" +
               "        }\n" +
               "        header {\n" +
               "            display: flex;\n" +
               "            justify-content: space-between;\n" +
               "            align-items: center;\n" +
               "            margin-bottom: 40px;\n" +
               "            border-bottom: 1px solid rgba(255,255,255,0.05);\n" +
               "            padding-bottom: 20px;\n" +
               "        }\n" +
               "        .brand {\n" +
               "            font-size: 28px;\n" +
               "            font-weight: 800;\n" +
               "            color: var(--text);\n" +
               "            display: flex;\n" +
               "            align-items: center;\n" +
               "            gap: 10px;\n" +
               "        }\n" +
               "        .brand span { color: var(--primary); }\n" +
               "        .status-badge {\n" +
               "            background: rgba(52, 199, 89, 0.15);\n" +
               "            color: var(--success);\n" +
               "            padding: 6px 12px;\n" +
               "            border-radius: 20px;\n" +
               "            font-size: 13px;\n" +
               "            font-weight: 600;\n" +
               "            border: 1px solid rgba(52, 199, 89, 0.3);\n" +
               "        }\n" +
               "        .metrics-grid {\n" +
               "            display: grid;\n" +
               "            grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));\n" +
               "            gap: 20px;\n" +
               "            margin-bottom: 40px;\n" +
               "        }\n" +
               "        .metric-card {\n" +
               "            background: var(--surface);\n" +
               "            backdrop-filter: blur(16px);\n" +
               "            border: 1px solid rgba(255,255,255,0.05);\n" +
               "            border-radius: 20px;\n" +
               "            padding: 24px;\n" +
               "            transition: transform 0.2s, box-shadow 0.2s;\n" +
               "        }\n" +
               "        .metric-card:hover {\n" +
               "            transform: translateY(-2px);\n" +
               "            border-color: rgba(88, 86, 214, 0.2);\n" +
               "            box-shadow: 0 10px 20px rgba(0,0,0,0.3);\n" +
               "        }\n" +
               "        .metric-card h3 {\n" +
               "            font-size: 14px;\n" +
               "            color: var(--text-muted);\n" +
               "            font-weight: 600;\n" +
               "            text-transform: uppercase;\n" +
               "            margin-bottom: 10px;\n" +
               "        }\n" +
               "        .metric-card .val {\n" +
               "            font-size: 36px;\n" +
               "            font-weight: 800;\n" +
               "            color: var(--text);\n" +
               "        }\n" +
               "        .panel-container {\n" +
               "            background: var(--surface);\n" +
               "            backdrop-filter: blur(16px);\n" +
               "            border: 1px solid rgba(255,255,255,0.05);\n" +
               "            border-radius: 24px;\n" +
               "            padding: 30px;\n" +
               "            box-shadow: 0 20px 40px rgba(0,0,0,0.4);\n" +
               "        }\n" +
               "        .panel-header {\n" +
               "            display: flex;\n" +
               "            justify-content: space-between;\n" +
               "            align-items: center;\n" +
               "            margin-bottom: 25px;\n" +
               "        }\n" +
               "        .panel-header h2 {\n" +
               "            font-size: 20px;\n" +
               "            font-weight: 600;\n" +
               "        }\n" +
               "        .btn-refresh {\n" +
               "            background: var(--primary);\n" +
               "            color: white;\n" +
               "            border: none;\n" +
               "            padding: 10px 20px;\n" +
               "            border-radius: 12px;\n" +
               "            font-weight: 600;\n" +
               "            cursor: pointer;\n" +
               "            transition: opacity 0.2s;\n" +
               "            box-shadow: 0 4px 12px var(--primary-glow);\n" +
               "        }\n" +
               "        .btn-refresh:hover { opacity: 0.9; }\n" +
               "        table {\n" +
               "            width: 100%;\n" +
               "            border-collapse: collapse;\n" +
               "            text-align: left;\n" +
               "        }\n" +
               "        th {\n" +
               "            color: var(--text-muted);\n" +
               "            font-size: 13px;\n" +
               "            font-weight: 600;\n" +
               "            text-transform: uppercase;\n" +
               "            padding: 15px 20px;\n" +
               "            border-bottom: 1px solid rgba(255,255,255,0.08);\n" +
               "        }\n" +
               "        td {\n" +
               "            padding: 18px 20px;\n" +
               "            border-bottom: 1px solid rgba(255,255,255,0.05);\n" +
               "            font-size: 15px;\n" +
               "            vertical-align: middle;\n" +
               "        }\n" +
               "        tr:last-child td {\n" +
               "            border-bottom: none;\n" +
               "        }\n" +
               "        tr:hover td {\n" +
               "            background: rgba(255,255,255,0.02);\n" +
               "        }\n" +
               "        .device-badge {\n" +
               "            display: flex;\n" +
               "            align-items: center;\n" +
               "            gap: 10px;\n" +
               "        }\n" +
               "        .device-icon {\n" +
               "            width: 36px;\n" +
               "            height: 36px;\n" +
               "            border-radius: 10px;\n" +
               "            background: rgba(255,255,255,0.05);\n" +
               "            display: flex;\n" +
               "            align-items: center;\n" +
               "            justify-content: center;\n" +
               "            font-size: 18px;\n" +
               "        }\n" +
               "        .status-pill {\n" +
               "            padding: 6px 12px;\n" +
               "            border-radius: 12px;\n" +
               "            font-size: 12px;\n" +
               "            font-weight: 600;\n" +
               "            display: inline-block;\n" +
               "        }\n" +
               "        .status-pill.ativo {\n" +
               "            background: rgba(52, 199, 89, 0.15);\n" +
               "            color: var(--success);\n" +
               "        }\n" +
               "        .status-pill.bloqueado {\n" +
               "            background: rgba(255, 59, 48, 0.15);\n" +
               "            color: var(--danger);\n" +
               "        }\n" +
               "        .status-pill.pendente {\n" +
               "            background: rgba(255, 149, 0, 0.15);\n" +
               "            color: var(--pending);\n" +
               "        }\n" +
               "        .actions {\n" +
               "            display: flex;\n" +
               "            gap: 10px;\n" +
               "        }\n" +
               "        .btn-action {\n" +
               "            border: none;\n" +
               "            padding: 8px 14px;\n" +
               "            border-radius: 8px;\n" +
               "            font-weight: 600;\n" +
               "            cursor: pointer;\n" +
               "            font-size: 13px;\n" +
               "            transition: opacity 0.2s;\n" +
               "        }\n" +
               "        .btn-action.approve {\n" +
               "            background: var(--success);\n" +
               "            color: white;\n" +
               "        }\n" +
               "        .btn-action.block {\n" +
               "            background: var(--danger);\n" +
               "            color: white;\n" +
               "        }\n" +
               "        .btn-action.edit {\n" +
               "            background: rgba(88, 86, 214, 0.15);\n" +
               "            color: #8a88f7;\n" +
               "            border: 1px solid rgba(88, 86, 214, 0.3);\n" +
               "        }\n" +
               "        .btn-action.delete {\n" +
               "            background: rgba(255, 59, 48, 0.15);\n" +
               "            color: #ff5b50;\n" +
               "            border: 1px solid rgba(255, 59, 48, 0.3);\n" +
               "        }\n" +
               "        .btn-action.send {\n" +
               "            background: rgba(52, 152, 219, 0.15);\n" +
               "            color: #54a0ff;\n" +
               "            border: 1px solid rgba(52, 152, 219, 0.3);\n" +
               "        }\n" +
               "        .btn-action:hover {\n" +
               "            opacity: 0.9;\n" +
               "        }\n" +
               "        .empty-state {\n" +
               "            text-align: center;\n" +
               "            padding: 40px 0;\n" +
               "            color: var(--text-muted);\n" +
               "            font-size: 16px;\n" +
               "        }\n" +
               "\n" +
               "        /* Modal Overlay and Form Card */\n" +
               "        .modal-overlay {\n" +
               "            position: fixed;\n" +
               "            top: 0;\n" +
               "            left: 0;\n" +
               "            width: 100vw;\n" +
               "            height: 100vh;\n" +
               "            background: rgba(10, 10, 20, 0.85);\n" +
               "            backdrop-filter: blur(10px);\n" +
               "            display: flex;\n" +
               "            align-items: center;\n" +
               "            justify-content: center;\n" +
               "            z-index: 1000;\n" +
               "            transition: opacity 0.3s ease;\n" +
               "        }\n" +
               "        .modal-card {\n" +
               "            background: rgba(25, 25, 45, 0.95);\n" +
               "            border: 1px solid rgba(255,255,255,0.08);\n" +
               "            border-radius: 24px;\n" +
               "            padding: 30px;\n" +
               "            width: 90%;\n" +
               "            max-width: 550px;\n" +
               "            box-shadow: 0 20px 50px rgba(0,0,0,0.6);\n" +
               "            animation: scaleUp 0.3s ease;\n" +
               "        }\n" +
               "        @keyframes scaleUp {\n" +
               "            from { transform: scale(0.9); opacity: 0; }\n" +
               "            to { transform: scale(1); opacity: 1; }\n" +
               "        }\n" +
               "        .modal-card h3 {\n" +
               "            font-size: 22px;\n" +
               "            font-weight: 600;\n" +
               "            margin-bottom: 5px;\n" +
               "        }\n" +
               "        .form-group {\n" +
               "            margin-bottom: 18px;\n" +
               "            display: flex;\n" +
               "            flex-direction: column;\n" +
               "            gap: 6px;\n" +
               "            text-align: left;\n" +
               "        }\n" +
               "        .form-group label {\n" +
               "            font-size: 13px;\n" +
               "            color: var(--text-muted);\n" +
               "            font-weight: 600;\n" +
               "            text-transform: uppercase;\n" +
               "        }\n" +
               "        .form-group input {\n" +
               "            width: 100%;\n" +
               "            background: rgba(255,255,255,0.05);\n" +
               "            border: 1px solid rgba(255,255,255,0.1);\n" +
               "            color: white;\n" +
               "            padding: 12px 16px;\n" +
               "            border-radius: 12px;\n" +
               "            font-size: 15px;\n" +
               "            outline: none;\n" +
               "            transition: border-color 0.2s;\n" +
               "        }\n" +
               "        .form-group input:focus {\n" +
               "            border-color: var(--primary);\n" +
               "        }\n" +
               "        .form-row {\n" +
               "            display: grid;\n" +
               "            grid-template-columns: 1fr;\n" +
               "            gap: 15px;\n" +
               "        }\n" +
               "        .form-grid-3 {\n" +
               "            display: grid;\n" +
               "            grid-template-columns: 1.2fr 1fr 1fr;\n" +
               "            gap: 12px;\n" +
               "        }\n" +
               "        .modal-actions {\n" +
               "            display: flex;\n" +
               "            justify-content: flex-end;\n" +
               "            gap: 12px;\n" +
               "            margin-top: 25px;\n" +
               "        }\n" +
               "        .btn-cancel {\n" +
               "            background: transparent;\n" +
               "            border: 1px solid rgba(255,255,255,0.1);\n" +
               "            color: var(--text);\n" +
               "            padding: 12px 20px;\n" +
               "            border-radius: 12px;\n" +
               "            font-weight: 600;\n" +
               "            cursor: pointer;\n" +
               "            transition: background 0.2s;\n" +
               "        }\n" +
               "        .btn-cancel:hover {\n" +
               "            background: rgba(255,255,255,0.05);\n" +
               "        }\n" +
               "        .btn-save {\n" +
               "            background: var(--primary);\n" +
               "            border: none;\n" +
               "            color: white;\n" +
               "            padding: 12px 20px;\n" +
               "            border-radius: 12px;\n" +
               "            font-weight: 600;\n" +
               "            cursor: pointer;\n" +
               "            transition: opacity 0.2s;\n" +
               "            box-shadow: 0 4px 12px var(--primary-glow);\n" +
               "        }\n" +
                "        .btn-save:hover {\n" +
                "            opacity: 0.9;\n" +
                "        }\n" +
                "\n" +
                "        /* Toast Notifications */\n" +
                "        .toast-container {\n" +
                "            position: fixed;\n" +
                "            top: 20px;\n" +
                "            right: 20px;\n" +
                "            z-index: 9999;\n" +
                "            display: flex;\n" +
                "            flex-direction: column;\n" +
                "            gap: 10px;\n" +
                "        }\n" +
                "        .toast {\n" +
                "            padding: 14px 20px;\n" +
                "            border-radius: 12px;\n" +
                "            font-weight: 600;\n" +
                "            font-size: 14px;\n" +
                "            color: #fff;\n" +
                "            backdrop-filter: blur(12px);\n" +
                "            box-shadow: 0 8px 24px rgba(0,0,0,0.3);\n" +
                "            animation: slideInRight 0.3s ease;\n" +
                "            min-width: 280px;\n" +
                "            max-width: 450px;\n" +
                "        }\n" +
                "        @keyframes slideInRight {\n" +
                "            from { transform: translateX(100%); opacity: 0; }\n" +
                "            to { transform: translateX(0); opacity: 1; }\n" +
                "        }\n" +
                "        @keyframes slideOutRight {\n" +
                "            from { transform: translateX(0); opacity: 1; }\n" +
                "            to { transform: translateX(100%); opacity: 0; }\n" +
                "        }\n" +
                "        .toast.success { background: rgba(52,199,89,0.92); }\n" +
                "        .toast.error { background: rgba(255,59,48,0.92); }\n" +
                "        .toast.info { background: rgba(88,86,214,0.92); }\n" +
                "        .toast.warning { background: rgba(255,149,0,0.92); }\n" +
                "\n" +
                "        /* Search and Filter Bar */\n" +
                "        .toolbar {\n" +
                "            display: flex;\n" +
                "            gap: 12px;\n" +
                "            margin-bottom: 20px;\n" +
                "            flex-wrap: wrap;\n" +
                "        }\n" +
                "        .search-box {\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            background: rgba(255,255,255,0.05);\n" +
                "            border: 1px solid rgba(255,255,255,0.1);\n" +
                "            border-radius: 12px;\n" +
                "            padding: 0 16px;\n" +
                "            flex: 1;\n" +
                "            min-width: 200px;\n" +
                "        }\n" +
                "        .search-box input {\n" +
                "            background: none;\n" +
                "            border: none;\n" +
                "            color: #fff;\n" +
                "            padding: 12px 10px;\n" +
                "            font-size: 14px;\n" +
                "            width: 100%;\n" +
                "            outline: none;\n" +
                "            font-family: 'Outfit', sans-serif;\n" +
                "        }\n" +
                "        .search-box input::placeholder { color: var(--text-muted); }\n" +
                "        .search-box .search-icon { color: var(--text-muted); font-size: 16px; }\n" +
                "\n" +
                "        .filter-select {\n" +
                "            background: rgba(255,255,255,0.05);\n" +
                "            border: 1px solid rgba(255,255,255,0.1);\n" +
                "            border-radius: 12px;\n" +
                "            color: #fff;\n" +
                "            padding: 12px 16px;\n" +
                "            font-size: 14px;\n" +
                "            outline: none;\n" +
                "            cursor: pointer;\n" +
                "            font-family: 'Outfit', sans-serif;\n" +
                "        }\n" +
                "        .filter-select option { background: #1a1a2e; color: #fff; }\n" +
                "\n" +
                "        .btn-outline {\n" +
                "            background: rgba(255,255,255,0.05);\n" +
                "            border: 1px solid rgba(255,255,255,0.1);\n" +
                "            color: var(--text);\n" +
                "            padding: 10px 18px;\n" +
                "            border-radius: 12px;\n" +
                "            font-weight: 600;\n" +
                "            cursor: pointer;\n" +
                "            font-size: 13px;\n" +
                "            transition: all 0.2s;\n" +
                "            font-family: 'Outfit', sans-serif;\n" +
                "        }\n" +
                "        .btn-outline:hover { background: rgba(255,255,255,0.1); border-color: rgba(255,255,255,0.2); }\n" +
                "\n" +
                "        /* Sortable table headers */\n" +
                "        th.sortable { cursor: pointer; user-select: none; }\n" +
                "        th.sortable:hover { color: var(--text); }\n" +
                "        th.sortable::after { content: \" \\2195\"; opacity: 0.4; font-size: 11px; }\n" +
                "        th.sortable.asc::after { content: \" \\2191\"; opacity: 1; }\n" +
                "        th.sortable.desc::after { content: \" \\2193\"; opacity: 1; }\n" +
                "\n" +
                "        /* Expiration badges */\n" +
                "        .exp-critical { color: var(--danger) !important; font-weight: 700 !important; }\n" +
                "        .exp-warning { color: var(--pending) !important; }\n" +
                "        .exp-ok { color: var(--success) !important; }\n" +
                "\n" +
                "        tr.critical-row td { background: rgba(255,59,48,0.05) !important; }\n" +
                "        tr.critical-row:hover td { background: rgba(255,59,48,0.1) !important; }\n" +
                "\n" +
                "        /* Skeleton loading */\n" +
                "        .skeleton-bar {\n" +
                "            height: 14px;\n" +
                "            background: rgba(255,255,255,0.06);\n" +
                "            border-radius: 6px;\n" +
                "            animation: pulse 1.5s ease-in-out infinite;\n" +
                "            max-width: 200px;\n" +
                "        }\n" +
                "        @keyframes pulse {\n" +
                "            0%, 100% { opacity: 0.4; }\n" +
                "            50% { opacity: 0.8; }\n" +
                "        }\n" +
                "\n" +
                "        /* Online indicator */\n" +
                "        .online-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 6px; }\n" +
                "        .online-dot.online { background: var(--success); }\n" +
                "        .online-dot.offline { background: var(--danger); }\n" +
                "        .online-dot.unknown { background: var(--text-muted); }\n" +
                "\n" +
                "        /* Charts section */\n" +
                "        .charts-grid {\n" +
                "            display: grid;\n" +
                "            grid-template-columns: 1fr 1fr;\n" +
                "            gap: 20px;\n" +
                "            margin-bottom: 30px;\n" +
                "        }\n" +
                "        .chart-card {\n" +
                "            background: var(--surface);\n" +
                "            backdrop-filter: blur(16px);\n" +
                "            border: 1px solid rgba(255,255,255,0.05);\n" +
                "            border-radius: 20px;\n" +
                "            padding: 24px;\n" +
                "        }\n" +
                "        .chart-card h3 {\n" +
                "            font-size: 14px;\n" +
                "            color: var(--text-muted);\n" +
                "            font-weight: 600;\n" +
                "            text-transform: uppercase;\n" +
                "            margin-bottom: 15px;\n" +
                "        }\n" +
                "        .chart-card canvas { max-height: 220px; }\n" +
                "\n" +
                "        .last-seen { font-size: 12px; color: var(--text-muted); }\n" +
                "\n" +
                "        /* Responsive */\n" +
                "        @media (max-width: 768px) {\n" +
                "            body { padding: 15px; }\n" +
                "            .metrics-grid { grid-template-columns: 1fr 1fr; }\n" +
                "            .charts-grid { grid-template-columns: 1fr; }\n" +
                "            .toolbar { flex-direction: column; }\n" +
                "            .search-box { min-width: auto; }\n" +
                "            .panel-container { padding: 15px; }\n" +
                "            table { font-size: 13px; }\n" +
                "            td, th { padding: 12px 10px; }\n" +
                "            .actions { flex-wrap: wrap; }\n" +
                "        }\n" +
                "        @media (max-width: 480px) {\n" +
                "            .metrics-grid { grid-template-columns: 1fr; }\n" +
                "            .panel-header { flex-direction: column; gap: 15px; }\n" +
                "        }\n" +
                "\n" +
                "        /* Tela de Login */\n" +
                "        #login-screen {\n" +
                "            position: fixed;\n" +
                "            top: 0;\n" +
                "            left: 0;\n" +
                "            width: 100vw;\n" +
                "            height: 100vh;\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            justify-content: center;\n" +
                "            background: var(--bg);\n" +
                "            z-index: 2000;\n" +
                "        }\n" +
                "        .login-card {\n" +
                "            background: var(--surface);\n" +
                "            backdrop-filter: blur(16px);\n" +
                "            border: 1px solid rgba(255,255,255,0.08);\n" +
                "            border-radius: 24px;\n" +
                "            padding: 40px;\n" +
                "            width: 90%;\n" +
                "            max-width: 400px;\n" +
                "            box-shadow: 0 20px 50px rgba(0,0,0,0.5);\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <!-- Tela de Login -->\n" +
                "    <div id=\"login-screen\">\n" +
                "        <div class=\"login-card\">\n" +
                "            <div class=\"brand\" style=\"justify-content:center;margin-bottom:30px\">🅿️ Park <span>' 31</span> Admin</div>\n" +
                "            <div class=\"form-group\">\n" +
                "                <label>Senha de Administrador</label>\n" +
                "                <input type=\"password\" id=\"login-senha\" placeholder=\"Digite a senha\" onkeydown=\"if(event.key==='Enter')login()\">\n" +
                "            </div>\n" +
                "            <button class=\"btn-save\" style=\"width:100%;margin-top:10px\" onclick=\"login()\">Acessar Painel</button>\n" +
                "            <p id=\"login-erro\" style=\"color:var(--danger);font-size:14px;margin-top:15px;display:none\"></p>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "\n" +
                "    <!-- Painel Admin (oculto até login) -->\n" +
                "    <div id=\"admin-panel\" style=\"display:none\">\n" +
                "    <header>\n" +
                "        <div class=\"brand\">🅿️ Park <span>' 31</span> Admin</div>\n" +
                "        <div style=\"display:flex;gap:12px;align-items:center\">\n" +
                "            <div class=\"status-badge\">Servidor Ativo</div>\n" +
                "            <button class=\"btn-action edit\" onclick=\"logout()\" style=\"padding:6px 14px\">Sair</button>\n" +
                "        </div>\n" +
                "    </header>\n" +
                "\n" +
                "    <div class=\"metrics-grid\">\n" +
                "        <div class=\"metric-card\">\n" +
                "            <h3>Total de Aparelhos</h3>\n" +
                "            <div class=\"val\" id=\"metric-total\">0</div>\n" +
                "        </div>\n" +
                "        <div class=\"metric-card\">\n" +
                "            <h3>Terminais Ativos</h3>\n" +
                "            <div class=\"val\" style=\"color: var(--success)\" id=\"metric-ativos\">0</div>\n" +
                "        </div>\n" +
                "        <div class=\"metric-card\">\n" +
                "            <h3>Pendentes de Aprovação</h3>\n" +
                "            <div class=\"val\" style=\"color: var(--pending)\" id=\"metric-pendentes\">0</div>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "\n" +
                "    <div class=\"charts-grid\">\n" +
                "        <div class=\"chart-card\">\n" +
                "            <h3>Status dos Dispositivos</h3>\n" +
                "            <canvas id=\"chart-status\"></canvas>\n" +
                "        </div>\n" +
                "        <div class=\"chart-card\">\n" +
                "            <h3>Licenças por Expirar (Próximos 30 dias)</h3>\n" +
                "            <canvas id=\"chart-expiry\"></canvas>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "\n" +
                "    <div class=\"panel-container\">\n" +
                "        <div class=\"panel-header\">\n" +
                "            <h2>Dispositivos</h2>\n" +
                "            <div style=\"display:flex;gap:10px\">\n" +
                "                <button class=\"btn-outline\" onclick=\"exportarCSV()\">📥 CSV</button>\n" +
                "                <button class=\"btn-refresh\" onclick=\"carregarTerminais()\">🔄 Atualizar</button>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        <div class=\"toolbar\">\n" +
                "            <div class=\"search-box\">\n" +
                "                <span class=\"search-icon\">🔍</span>\n" +
                "                <input type=\"text\" id=\"search-input\" placeholder=\"Buscar por cliente, aparelho ou hardware ID...\" oninput=\"filtrarTabela()\">\n" +
                "            </div>\n" +
                "            <select class=\"filter-select\" id=\"filter-status\" onchange=\"filtrarTabela()\">\n" +
                "                <option value=\"\">Todos os status</option>\n" +
                "                <option value=\"ATIVO\">Ativos</option>\n" +
                "                <option value=\"PENDENTE\">Pendentes</option>\n" +
                "                <option value=\"BLOQUEADO\">Bloqueados</option>\n" +
                "            </select>\n" +
                "        </div>\n" +
                "        <div style=\"overflow-x:auto\">\n" +
                "        <table>\n" +
                "            <thead>\n" +
                "                <tr>\n" +
                "                    <th class=\"sortable\" data-col=\"cliente\">Cliente</th>\n" +
                "                    <th class=\"sortable\" data-col=\"aparelho\">Aparelho</th>\n" +
                "                    <th class=\"sortable\" data-col=\"hwid\">Hardware ID</th>\n" +
                "                    <th class=\"sortable\" data-col=\"tarifa\">Tarifa / Vagas</th>\n" +
                "                    <th class=\"sortable\" data-col=\"expiracao\">Expiração</th>\n" +
                "                    <th class=\"sortable\" data-col=\"ultimoPing\">Último Ping</th>\n" +
                "                    <th class=\"sortable\" data-col=\"status\">Status</th>\n" +
                "                    <th style=\"text-align: right;\">Ações</th>\n" +
                "                </tr>\n" +
                "            </thead>\n" +
                "            <tbody id=\"tbody-devices\">\n" +
                "                <tr class=\"skeleton-row\">\n" +
                "                    <td><div class=\"skeleton-bar\"></div></td>\n" +
                "                    <td><div class=\"skeleton-bar\"></div></td>\n" +
                "                    <td><div class=\"skeleton-bar\"></div></td>\n" +
                "                    <td><div class=\"skeleton-bar\"></div></td>\n" +
                "                    <td><div class=\"skeleton-bar\"></div></td>\n" +
                "                    <td><div class=\"skeleton-bar\"></div></td>\n" +
                "                    <td><div class=\"skeleton-bar\"></div></td>\n" +
                "                    <td><div class=\"skeleton-bar\"></div></td>\n" +
                "                </tr>\n" +
                "            </tbody>\n" +
                "        </table>\n" +
                "        </div>\n" +
                "        <div class=\"panel-header\" style=\"margin-bottom:0;padding-top:10px\">\n" +
                "            <small style=\"color:var(--text-muted)\" id=\"table-count\">0 dispositivos</small>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "    </div>\n" +
                "\n" +
                "    <div class=\"toast-container\" id=\"toast-container\"></div>\n" +
                "\n" +
                "    <!-- Modal de Configuração do Cliente -->\n" +
                "    <div id=\"modal-edit\" class=\"modal-overlay\" style=\"display:none;\">\n" +
                "        <div class=\"modal-card\">\n" +
                "            <h3>⚙️ Editar Cliente</h3>\n" +
                "            <p id=\"modal-hwid-display\" style=\"font-size:12px; color:var(--text-muted); margin-bottom:20px; font-family:monospace;\"></p>\n" +
                "            <input type=\"hidden\" id=\"modal-hwid\">\n" +
                "            \n" +
                "            <div style=\"display: grid; grid-template-columns: 1.5fr 1fr; gap: 15px; margin-bottom: 15px;\">\n" +
                "                <div class=\"form-group\">\n" +
                "                    <label>Nome do Cliente / Estabelecimento</label>\n" +
                "                    <input type=\"text\" id=\"modal-nome-cliente\" placeholder=\"Ex: Estacionamento do Silva\">\n" +
                "                </div>\n" +
                "                <div class=\"form-group\">\n" +
                "                    <label>Tempo de Acesso (Dias)</label>\n" +
                "                    <input type=\"number\" id=\"modal-dias-licenca\" min=\"30\" value=\"30\">\n" +
                "                </div>\n" +
                "            </div>\n" +
                "            \n" +
                "            <div class=\"form-grid-3\">\n" +
                "                <div class=\"form-group\">\n" +
                "                    <label>Tarifa / Hora</label>\n" +
                "                    <input type=\"number\" step=\"0.50\" min=\"0\" id=\"modal-tarifa-hora\" value=\"5.00\">\n" +
                "                </div>\n" +
                "                <div class=\"form-group\">\n" +
                "                    <label>Vagas Carro</label>\n" +
                "                    <input type=\"number\" min=\"0\" id=\"modal-vagas-carro\" value=\"20\">\n" +
                "                </div>\n" +
                "                <div class=\"form-group\">\n" +
                "                    <label>Vagas Moto</label>\n" +
                "                    <input type=\"number\" min=\"0\" id=\"modal-vagas-moto\" value=\"5\">\n" +
                "                </div>\n" +
                "            </div>\n" +
                "            \n" +
                "            <div class=\"modal-actions\">\n" +
                "                <button class=\"btn-cancel\" onclick=\"fecharModal()\">Cancelar</button>\n" +
                "                <button class=\"btn-save\" onclick=\"salvarConfig()\">Salvar Alterações</button>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "\n" +
                "    <script>\n" +
                "        let cachedDevices = [];\n" +
                "        let adminToken = localStorage.getItem('adminToken');\n" +
                "        let sortCol = null, sortDir = 'asc';\n" +
                "        let statusChart = null, expiryChart = null;\n" +
                "        \n" +
                "        function getHeaders() {\n" +
                "            return { 'Content-Type': 'application/json', 'X-Admin-Token': adminToken };\n" +
                "        }\n" +
                "        \n" +
                "        function mostrarToast(mensagem, tipo) {\n" +
                "            tipo = tipo || 'success';\n" +
                "            const container = document.getElementById('toast-container');\n" +
                "            const toast = document.createElement('div');\n" +
                "            toast.className = 'toast ' + tipo;\n" +
                "            toast.textContent = mensagem;\n" +
                "            container.appendChild(toast);\n" +
                "            setTimeout(() => {\n" +
                "                toast.style.animation = 'slideOutRight 0.3s ease forwards';\n" +
                "                setTimeout(() => toast.remove(), 300);\n" +
                "            }, 3500);\n" +
                "        }\n" +
                "        \n" +
                "        async function login() {\n" +
                "            const senha = document.getElementById('login-senha').value;\n" +
                "            const erroEl = document.getElementById('login-erro');\n" +
                "            erroEl.style.display = 'none';\n" +
                "            try {\n" +
                "                const res = await fetch('/api/login', { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ senha }) });\n" +
                "                const data = await res.json();\n" +
                "                if (data.success) {\n" +
                "                    adminToken = data.token; localStorage.setItem('adminToken', adminToken);\n" +
                "                    document.getElementById('login-screen').style.display = 'none';\n" +
                "                    document.getElementById('admin-panel').style.display = 'block';\n" +
                "                    carregarTerminais();\n" +
                "                } else { erroEl.textContent = 'Senha incorreta!'; erroEl.style.display = 'block'; }\n" +
                "            } catch (err) { erroEl.textContent = 'Erro ao conectar com o servidor'; erroEl.style.display = 'block'; }\n" +
                "        }\n" +
                "        \n" +
                "        function logout() {\n" +
                "            adminToken = null; localStorage.removeItem('adminToken');\n" +
                "            document.getElementById('admin-panel').style.display = 'none';\n" +
                "            document.getElementById('login-screen').style.display = 'flex';\n" +
                "            document.getElementById('login-senha').value = '';\n" +
                "        }\n" +
                "        \n" +
                "        function tempoDesde(timestamp) {\n" +
                "            if (!timestamp || timestamp <= 0) return '<span class=\"online-dot unknown\"></span><span class=\"last-seen\">Nunca</span>';\n" +
                "            const diff = Date.now() - timestamp;\n" +
                "            const mins = Math.floor(diff / 60000);\n" +
                "            if (mins < 1) return '<span class=\"online-dot online\"></span><span class=\"last-seen\">Agora</span>';\n" +
                "            if (mins < 60) return '<span class=\"online-dot online\"></span><span class=\"last-seen\">Há ' + mins + 'min</span>';\n" +
                "            const hrs = Math.floor(mins / 60);\n" +
                "            if (hrs < 24) return '<span class=\"online-dot online\"></span><span class=\"last-seen\">Há ' + hrs + 'h</span>';\n" +
                "            const dias = Math.floor(hrs / 24);\n" +
                "            return '<span class=\"online-dot offline\"></span><span class=\"last-seen\">Há ' + dias + 'd</span>';\n" +
                "        }\n" +
                "        \n" +
                "        function tempoRestante(exp) {\n" +
                "            if (!exp || exp <= 0) return { text: 'Sem expiração', cls: '' };\n" +
                "            const dias = Math.ceil((exp - Date.now()) / (24 * 60 * 60 * 1000));\n" +
                "            if (dias <= 0) return { text: 'Expirada', cls: 'exp-critical' };\n" +
                "            if (dias <= 7) return { text: new Date(exp).toLocaleDateString('pt-BR') + ' (' + dias + 'd)', cls: 'exp-critical' };\n" +
                "            if (dias <= 30) return { text: new Date(exp).toLocaleDateString('pt-BR') + ' (' + dias + 'd)', cls: 'exp-warning' };\n" +
                "            return { text: new Date(exp).toLocaleDateString('pt-BR'), cls: 'exp-ok' };\n" +
                "        }\n" +
                "        \n" +
                "        function atualizarGraficos(devices) {\n" +
                "            const ativos = devices.filter(d => d.status === 'ATIVO').length;\n" +
                "            const pendentes = devices.filter(d => d.status === 'PENDENTE').length;\n" +
                "            const bloqueados = devices.filter(d => d.status === 'BLOQUEADO').length;\n" +
                "            \n" +
                "            if (statusChart) statusChart.destroy();\n" +
                "            const ctx1 = document.getElementById('chart-status').getContext('2d');\n" +
                "            statusChart = new Chart(ctx1, {\n" +
                "                type: 'doughnut',\n" +
                "                data: {\n" +
                "                    labels: ['Ativos (' + ativos + ')', 'Pendentes (' + pendentes + ')', 'Bloqueados (' + bloqueados + ')'],\n" +
                "                    datasets: [{ data: [ativos, pendentes, bloqueados], backgroundColor: ['#34c759', '#ff9500', '#ff3b30'], borderWidth: 0 }]\n" +
                "                },\n" +
                "                options: { responsive: true, maintainAspectRatio: true, cutout: '65%', plugins: { legend: { position: 'bottom', labels: { color: '#8e8e93', font: { family: 'Outfit' } } } } }\n" +
                "            });\n" +
                "            \n" +
                "            if (expiryChart) expiryChart.destroy();\n" +
                "            const hoje = Date.now();\n" +
                "            const labels = [];\n" +
                "            const data = [];\n" +
                "            for (let i = 1; i <= 4; i++) {\n" +
                "                const inicio = hoje + (i-1) * 7 * 86400000;\n" +
                "                const fim = hoje + i * 7 * 86400000;\n" +
                "                const count = devices.filter(d => d.dataExpiracao >= inicio && d.dataExpiracao < fim).length;\n" +
                "                labels.push('Semana ' + i);\n" +
                "                data.push(count);\n" +
                "            }\n" +
                "            const ctx2 = document.getElementById('chart-expiry').getContext('2d');\n" +
                "            expiryChart = new Chart(ctx2, {\n" +
                "                type: 'bar',\n" +
                "                data: { labels: labels, datasets: [{ label: 'Licenças', data: data, backgroundColor: '#5856d6', borderRadius: 6 }] },\n" +
                "                options: { responsive: true, maintainAspectRatio: true, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true, grid: { color: 'rgba(255,255,255,0.05)' }, ticks: { color: '#8e8e93' } }, x: { grid: { display: false }, ticks: { color: '#8e8e93' } } } }\n" +
                "            });\n" +
                "        }\n" +
                "        \n" +
                "        async function carregarTerminais() {\n" +
                "            try {\n" +
                "                const tbody = document.getElementById('tbody-devices');\n" +
                "                tbody.innerHTML = '<tr class=\"skeleton-row\"><td colspan=\"8\"><div class=\"skeleton-bar\" style=\"max-width:300px;margin:auto\"></div></td></tr>';\n" +
                "                const response = await fetch('/api/devices', { headers: getHeaders() });\n" +
                "                if (response.status === 401) { logout(); return; }\n" +
                "                const devices = await response.json();\n" +
                "                cachedDevices = devices;\n" +
                "                \n" +
                "                let total = devices.length, ativos = 0, pendentes = 0;\n" +
                "                devices.forEach(d => { if (d.status === 'ATIVO') ativos++; if (d.status === 'PENDENTE') pendentes++; });\n" +
                "                document.getElementById('metric-total').innerText = total;\n" +
                "                document.getElementById('metric-ativos').innerText = ativos;\n" +
                "                document.getElementById('metric-pendentes').innerText = pendentes;\n" +
                "                atualizarGraficos(devices);\n" +
                "                filtrarTabela();\n" +
                "            } catch (err) { mostrarToast('Erro ao carregar dispositivos', 'error'); }\n" +
                "        }\n" +
                "        \n" +
                "        function renderizarTabela(devices) {\n" +
                "            const tbody = document.getElementById('tbody-devices');\n" +
                "            tbody.innerHTML = '';\n" +
                "            document.getElementById('table-count').textContent = devices.length + ' dispositivo(s)';\n" +
                "            if (devices.length === 0) {\n" +
                "                tbody.innerHTML = '<tr><td colspan=\"8\" class=\"empty-state\">Nenhum dispositivo encontrado</td></tr>';\n" +
                "                return;\n" +
                "            }\n" +
                "            devices.forEach(d => {\n" +
                "                const exp = tempoRestante(d.dataExpiracao);\n" +
                "                const osIcon = d.soTipo.toLowerCase().includes('android') ? '📱' : '💻';\n" +
                "                const hasDraft = d.tarifaHoraPendente >= 0 || d.vagasCarroPendente >= 0 || d.vagasMotoPendente >= 0 || d.diasLicencaPendente >= 0 || (d.nomeClientePendente && d.nomeClientePendente.trim() !== '');\n" +
                "                const draftBadge = hasDraft ? ' <span class=\"status-pill pending\" style=\"background:rgba(243,156,18,0.15);color:#f39c12;border:1px solid rgba(243,156,18,0.3);font-size:11px;padding:2px 8px;vertical-align:middle;margin-left:8px\">Pendente</span>' : '';\n" +
                "                const clientLabel = d.nomeCliente && d.nomeCliente.trim() !== ''\n" +
                "                    ? '<strong style=\"font-size:16px;color:#fff\">' + (hasDraft ? d.nomeClientePendente : d.nomeCliente) + '</strong>' + draftBadge\n" +
                "                    : '<span style=\"color:var(--text-muted);font-style:italic\">Sem nome</span>' + draftBadge;\n" +
                "                const tr = document.createElement('tr');\n" +
                "                if (exp.cls === 'exp-critical') tr.className = 'critical-row';\n" +
                "                tr.innerHTML = '<td>' + clientLabel + '</td>' +\n" +
                "                    '<td><div class=\"device-badge\"><div class=\"device-icon\">' + osIcon + '</div><div><strong style=\"display:block\">' + d.nomeAparelho + '</strong><small style=\"color:var(--text-muted)\">' + d.soTipo + '</small></div></div></td>' +\n" +
                "                    '<td><code style=\"background:rgba(255,255,255,0.05);padding:4px 8px;border-radius:6px;font-size:13px\">' + d.hardwareId + '</code></td>' +\n" +
                "                    '<td><strong>R$ ' + d.tarifaHora.toFixed(2) + '/h</strong><br><small style=\"color:var(--text-muted)\">🚗 ' + d.vagasCarro + ' | 🏍️ ' + d.vagasMoto + ' vagas</small>' +\n" +
                "                    (hasDraft ? '<br><small style=\"color:#f39c12;font-size:11px\">↳ R$ ' + d.tarifaHoraPendente.toFixed(2) + '/h | 🚗 ' + d.vagasCarroPendente + ' | 🏍️ ' + d.vagasMotoPendente + ' (Novo)</small>' : '') + '</td>' +\n" +
                "                    '<td><span class=\"' + exp.cls + '\">' + exp.text + '</span></td>' +\n" +
                "                    '<td>' + tempoDesde(d.ultimoPing) + '</td>' +\n" +
                "                    '<td><span class=\"status-pill ' + d.status.toLowerCase() + '\">' + d.status + '</span></td>' +\n" +
                "                    '<td style=\"text-align:right\"><div class=\"actions\" style=\"justify-content:flex-end\">' +\n" +
                "                    (hasDraft || d.status === 'ATIVO' ? '<button class=\"btn-action send\" onclick=\"enviar(\\'' + d.hardwareId + '\\')\">⚡ Enviar</button>' : '') +\n" +
                "                    (d.status !== 'ATIVO' ? '<button class=\"btn-action approve\" onclick=\"aprovar(\\'' + d.hardwareId + '\\')\">Liberar</button>' : '') +\n" +
                "                    (d.status !== 'BLOQUEADO' ? '<button class=\"btn-action block\" onclick=\"bloquear(\\'' + d.hardwareId + '\\')\">Bloquear</button>' : '') +\n" +
                "                    '<button class=\"btn-action edit\" onclick=\"abrirEditar(\\'' + d.hardwareId + '\\')\">Editar</button>' +\n" +
                "                    '<button class=\"btn-action delete\" onclick=\"excluir(\\'' + d.hardwareId + '\\')\">Excluir</button></div></td>';\n" +
                "                tbody.appendChild(tr);\n" +
                "            });\n" +
                "        }\n" +
                "        \n" +
                "        function filtrarTabela() {\n" +
                "            const search = document.getElementById('search-input').value.toLowerCase();\n" +
                "            const statusFilter = document.getElementById('filter-status').value;\n" +
                "            let filtrados = cachedDevices.filter(d => {\n" +
                "                if (statusFilter && d.status !== statusFilter) return false;\n" +
                "                if (search && !d.nomeCliente.toLowerCase().includes(search) && !d.nomeAparelho.toLowerCase().includes(search) && !d.hardwareId.toLowerCase().includes(search)) return false;\n" +
                "                return true;\n" +
                "            });\n" +
                "            if (sortCol) {\n" +
                "                filtrados.sort((a, b) => {\n" +
                "                    let va, vb;\n" +
                "                    if (sortCol === 'cliente') { va = a.nomeCliente || ''; vb = b.nomeCliente || ''; }\n" +
                "                    else if (sortCol === 'aparelho') { va = a.nomeAparelho || ''; vb = b.nomeAparelho || ''; }\n" +
                "                    else if (sortCol === 'hwid') { va = a.hardwareId || ''; vb = b.hardwareId || ''; }\n" +
                "                    else if (sortCol === 'tarifa') { va = a.tarifaHora; vb = b.tarifaHora; }\n" +
                "                    else if (sortCol === 'expiracao') { va = a.dataExpiracao; vb = b.dataExpiracao; }\n" +
                "                    else if (sortCol === 'ultimoPing') { va = a.ultimoPing; vb = b.ultimoPing; }\n" +
                "                    else if (sortCol === 'status') { va = a.status; vb = b.status; }\n" +
                "                    if (typeof va === 'string') return sortDir === 'asc' ? va.localeCompare(vb) : vb.localeCompare(va);\n" +
                "                    return sortDir === 'asc' ? (va - vb) : (vb - va);\n" +
                "                });\n" +
                "            }\n" +
                "            renderizarTabela(filtrados);\n" +
                "        }\n" +
                "        \n" +
                "        document.querySelectorAll('th.sortable').forEach(th => {\n" +
                "            th.addEventListener('click', function() {\n" +
                "                const col = this.dataset.col;\n" +
                "                if (sortCol === col) { sortDir = sortDir === 'asc' ? 'desc' : 'asc'; }\n" +
                "                else { sortCol = col; sortDir = 'asc'; }\n" +
                "                document.querySelectorAll('th.sortable').forEach(t => { t.classList.remove('asc', 'desc'); });\n" +
                "                this.classList.add(sortDir);\n" +
                "                filtrarTabela();\n" +
                "            });\n" +
                "        });\n" +
                "        \n" +
                "        async function aprovar(hardwareId) {\n" +
                "            const dias = prompt('Liberar licença por quantos dias? (Padrão: 30)', '30');\n" +
                "            if (dias === null) return;\n" +
                "            try {\n" +
                "                const response = await fetch('/api/approve', { method: 'POST', headers: getHeaders(), body: JSON.stringify({ hardwareId, dias }) });\n" +
                "                if (response.status === 401) { logout(); return; }\n" +
                "                if (response.ok) { mostrarToast('Terminal liberado com sucesso!', 'success'); carregarTerminais(); }\n" +
                "            } catch (err) { mostrarToast('Erro ao aprovar terminal', 'error'); }\n" +
                "        }\n" +
                "        \n" +
                "        async function enviar(hardwareId) {\n" +
                "            const device = cachedDevices.find(d => d.hardwareId === hardwareId);\n" +
                "            const nome = device ? (device.nomeCliente || device.nomeAparelho) : 'Aparelho';\n" +
                "            try {\n" +
                "                const response = await fetch('/api/send-config', { method: 'POST', headers: getHeaders(), body: JSON.stringify({ hardwareId }) });\n" +
                "                if (response.status === 401) { logout(); return; }\n" +
                "                if (response.ok) { mostrarToast('⚡ Configurações enviadas para \"' + nome + '\"', 'success'); carregarTerminais(); }\n" +
                "                else { mostrarToast('Erro ao enviar configurações', 'error'); }\n" +
                "            } catch (err) { mostrarToast('Erro na requisição de envio', 'error'); }\n" +
                "        }\n" +
                "        \n" +
                "        async function bloquear(hardwareId) {\n" +
                "            if (!confirm('Deseja realmente bloquear/suspender este terminal?')) return;\n" +
                "            try {\n" +
                "                const response = await fetch('/api/block', { method: 'POST', headers: getHeaders(), body: JSON.stringify({ hardwareId }) });\n" +
                "                if (response.status === 401) { logout(); return; }\n" +
                "                if (response.ok) { mostrarToast('Terminal bloqueado', 'warning'); carregarTerminais(); }\n" +
                "            } catch (err) { mostrarToast('Erro ao bloquear terminal', 'error'); }\n" +
                "        }\n" +
                "        \n" +
                "        function abrirEditar(hardwareId) {\n" +
                "            const device = cachedDevices.find(d => d.hardwareId === hardwareId);\n" +
                "            if (!device) return;\n" +
                "            const hasDraft = device.tarifaHoraPendente >= 0 || device.vagasCarroPendente >= 0 || device.vagasMotoPendente >= 0 || device.diasLicencaPendente >= 0 || (device.nomeClientePendente && device.nomeClientePendente.trim() !== '');\n" +
                "            document.getElementById('modal-hwid').value = hardwareId;\n" +
                "            document.getElementById('modal-hwid-display').innerText = 'ID: ' + hardwareId;\n" +
                "            document.getElementById('modal-nome-cliente').value = hasDraft ? device.nomeClientePendente : (device.nomeCliente || '');\n" +
                "            document.getElementById('modal-tarifa-hora').value = hasDraft ? device.tarifaHoraPendente : (device.tarifaHora || 5.0);\n" +
                "            document.getElementById('modal-vagas-carro').value = hasDraft ? device.vagasCarroPendente : (device.vagasCarro || 20);\n" +
                "            document.getElementById('modal-vagas-moto').value = hasDraft ? device.vagasMotoPendente : (device.vagasMoto || 5);\n" +
                "            let dias = 30;\n" +
                "            if (hasDraft && device.diasLicencaPendente >= 30) dias = device.diasLicencaPendente;\n" +
                "            else if (device.dataExpiracao && device.dataExpiracao > Date.now()) dias = Math.ceil((device.dataExpiracao - Date.now()) / (24 * 60 * 60 * 1000));\n" +
                "            if (dias < 30) dias = 30;\n" +
                "            document.getElementById('modal-dias-licenca').value = dias;\n" +
                "            document.getElementById('modal-edit').style.display = 'flex';\n" +
                "        }\n" +
                "        \n" +
                "        function fecharModal() {\n" +
                "            document.getElementById('modal-edit').style.display = 'none';\n" +
                "        }\n" +
                "        \n" +
                "        async function salvarConfig() {\n" +
                "            const hardwareId = document.getElementById('modal-hwid').value;\n" +
                "            const nomeCliente = document.getElementById('modal-nome-cliente').value;\n" +
                "            const tarifaHora = parseFloat(document.getElementById('modal-tarifa-hora').value);\n" +
                "            const vagasCarro = parseInt(document.getElementById('modal-vagas-carro').value);\n" +
                "            const vagasMoto = parseInt(document.getElementById('modal-vagas-moto').value);\n" +
                "            const diasLicenca = parseInt(document.getElementById('modal-dias-licenca').value) || 30;\n" +
                "            if (isNaN(tarifaHora) || tarifaHora < 0) { mostrarToast('Tarifa inválida', 'error'); return; }\n" +
                "            if (isNaN(vagasCarro) || vagasCarro < 0) { mostrarToast('Vagas de carro inválidas', 'error'); return; }\n" +
                "            if (isNaN(vagasMoto) || vagasMoto < 0) { mostrarToast('Vagas de moto inválidas', 'error'); return; }\n" +
                "            if (isNaN(diasLicenca) || diasLicenca < 30) { mostrarToast('Mínimo de 30 dias de licença', 'error'); return; }\n" +
                "            try {\n" +
                "                const response = await fetch('/api/update-config', { method: 'POST', headers: getHeaders(), body: JSON.stringify({ hardwareId, nomeCliente, tarifaHora, vagasCarro, vagasMoto, diasLicenca }) });\n" +
                "                if (response.status === 401) { logout(); return; }\n" +
                "                if (response.ok) { fecharModal(); mostrarToast('Configurações salvas! Use ⚡ Enviar para aplicar.', 'success'); carregarTerminais(); }\n" +
                "                else { mostrarToast('Erro ao atualizar configurações', 'error'); }\n" +
                "            } catch (err) { mostrarToast('Erro na requisição', 'error'); }\n" +
                "        }\n" +
                "        \n" +
                "        async function excluir(hardwareId) {\n" +
                "            if (!confirm('Deseja realmente REMOVER este dispositivo de forma definitiva? O cliente perderá a licença.')) return;\n" +
                "            try {\n" +
                "                const response = await fetch('/api/delete', { method: 'POST', headers: getHeaders(), body: JSON.stringify({ hardwareId }) });\n" +
                "                if (response.status === 401) { logout(); return; }\n" +
                "                if (response.ok) { mostrarToast('🗑️ Terminal excluído com sucesso!', 'info'); carregarTerminais(); }\n" +
                "                else { mostrarToast('Erro ao remover terminal', 'error'); }\n" +
                "            } catch (err) { mostrarToast('Erro na requisição', 'error'); }\n" +
                "        }\n" +
                "        \n" +
                "        function exportarCSV() {\n" +
                "            if (!cachedDevices || cachedDevices.length === 0) { mostrarToast('Nenhum dispositivo para exportar', 'warning'); return; }\n" +
                "            let csv = '\\\"Cliente\\\";\\\"Aparelho\\\";\\\"SO\\\";\\\"Hardware ID\\\";\\\"Status\\\";\\\"Tarifa\\\";\\\"Vagas Carro\\\";\\\"Vagas Moto\\\";\\\"Expiração\\\"\\n';\n" +
                "            cachedDevices.forEach(d => {\n" +
                "                const exp = d.dataExpiracao > 0 ? new Date(d.dataExpiracao).toLocaleDateString('pt-BR') : 'Sem expiração';\n" +
                "                csv += '\\\"' + (d.nomeCliente || '') + '\\\";\\\"' + d.nomeAparelho + '\\\";\\\"' + d.soTipo + '\\\";\\\"' + d.hardwareId + '\\\";\\\"' + d.status + '\\\";' + d.tarifaHora.toFixed(2) + ';' + d.vagasCarro + ';' + d.vagasMoto + ';\\\"' + exp + '\\\"\\n';\n" +
                "            });\n" +
                "            const blob = new Blob(['\\ufeff' + csv], { type: 'text/csv;charset=utf-8' });\n" +
                "            const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = 'dispositivos_' + new Date().toISOString().slice(0,10) + '.csv'; a.click();\n" +
                "            URL.revokeObjectURL(a.href);\n" +
                "            mostrarToast('📥 CSV exportado com sucesso!', 'success');\n" +
                "        }\n" +
                "        \n" +
                "        (function() {\n" +
                "            const savedToken = localStorage.getItem('adminToken');\n" +
                "            if (savedToken) {\n" +
                "                adminToken = savedToken;\n" +
                "                document.getElementById('login-screen').style.display = 'none';\n" +
                "                document.getElementById('admin-panel').style.display = 'block';\n" +
                "                carregarTerminais();\n" +
                "            }\n" +
                "        })();\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
    }
}
