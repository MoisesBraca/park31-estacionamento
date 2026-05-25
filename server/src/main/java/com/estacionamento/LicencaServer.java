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
                "                <input type=\"password\" id=\"senha-input\" placeholder=\"Digite a senha...\" autocomplete=\"off\" />\n" +
                "            </div>\n" +
                "            <div style=\"margin-top:25px\">\n" +
                "                <button class=\"btn-save\" id=\"login-btn\" style=\"width:100%;padding:14px\">Acessar Painel</button>\n" +
                "            </div>\n" +
                "            <div id=\"login-error\" style=\"margin-top:12px;color:var(--danger);font-size:14px;text-align:center;display:none\">Senha incorreta!</div>\n" +
                "        </div>\n" +
                "    </div>\n" +

               "    <!-- Dashboard Container -->\n" +
               "    <div id=\"dashboard\" style=\"display:none\">\n" +
               "        <header>\n" +
               "            <div class=\"brand\">🅿️ Park <span>' 31</span> <span style=\"font-size:16px;font-weight:400;color:var(--text-muted);margin-left:8px\">Painel de Licenciamento</span></div>\n" +
               "            <div style=\"display:flex;align-items:center;gap:15px\">\n" +
               "                <span class=\"status-badge\" id=\"server-status\">● Online</span>\n" +
               "                <button class=\"btn-outline\" id=\"logout-btn\">Sair</button>\n" +
               "            </div>\n" +
               "        </header>\n" +
               "\n" +
               "        <div class=\"metrics-grid\" id=\"metrics-grid\">\n" +
               "            <div class=\"metric-card\"><h3>📱 Total Dispositivos</h3><div class=\"val\" id=\"total-devices\">0</div></div>\n" +
               "            <div class=\"metric-card\"><h3>✅ Ativos</h3><div class=\"val\" id=\"active-devices\" style=\"color:var(--success)\">0</div></div>\n" +
               "            <div class=\"metric-card\"><h3>⛔ Bloqueados</h3><div class=\"val\" id=\"blocked-devices\" style=\"color:var(--danger)\">0</div></div>\n" +
               "            <div class=\"metric-card\"><h3>⏳ Pendentes</h3><div class=\"val\" id=\"pending-devices\" style=\"color:var(--pending)\">0</div></div>\n" +
               "        </div>\n" +
               "\n" +
               "        <div class=\"charts-grid\">\n" +
               "            <div class=\"chart-card\">\n" +
               "                <h3>📊 Status dos Dispositivos</h3>\n" +
               "                <canvas id=\"statusChart\"></canvas>\n" +
               "            </div>\n" +
               "            <div class=\"chart-card\">\n" +
               "                <h3>📈 Registros por Dia</h3>\n" +
               "                <canvas id=\"timelineChart\"></canvas>\n" +
               "            </div>\n" +
               "        </div>\n" +
               "\n" +
               "        <div class=\"panel-container\">\n" +
               "            <div class=\"panel-header\">\n" +
               "                <h2>📋 Dispositivos</h2>\n" +
               "                <button class=\"btn-refresh\" id=\"refresh-btn\">↻ Atualizar</button>\n" +
               "            </div>\n" +
               "            <div class=\"toolbar\">\n" +
               "                <div class=\"search-box\">\n" +
               "                    <span class=\"search-icon\">🔍</span>\n" +
               "                    <input type=\"text\" id=\"search-input\" placeholder=\"Buscar por hardware ID, cliente, dispositivo...\" />\n" +
               "                </div>\n" +
               "                <select class=\"filter-select\" id=\"filter-select\">\n" +
               "                    <option value=\"todos\">Todos</option>\n" +
               "                    <option value=\"ATIVO\">✅ Ativos</option>\n" +
               "                    <option value=\"BLOQUEADO\">⛔ Bloqueados</option>\n" +
               "                    <option value=\"PENDENTE\">⏳ Pendentes</option>\n" +
               "                </select>\n" +
               "            </div>\n" +
               "            <table>\n" +
               "                <thead>\n" +
               "                    <tr>\n" +
               "                        <th class=\"sortable\" data-col=\"hardwareId\">Hardware ID</th>\n" +
               "                        <th class=\"sortable\" data-col=\"nomeAparelho\">Dispositivo</th>\n" +
               "                        <th class=\"sortable\" data-col=\"soTipo\">SO</th>\n" +
               "                        <th class=\"sortable\" data-col=\"nomeCliente\">Cliente</th>\n" +
               "                        <th class=\"sortable\" data-col=\"dataRegistro\">Registro</th>\n" +
               "                        <th class=\"sortable\" data-col=\"dataExpiracao\">Expiração</th>\n" +
               "                        <th class=\"sortable\" data-col=\"status\">Status</th>\n" +
               "                        <th class=\"sortable\" data-col=\"ultimoPing\">Último Ping</th>\n" +
               "                        <th>Ações</th>\n" +
               "                    </tr>\n" +
               "                </thead>\n" +
               "                <tbody id=\"devices-tbody\">\n" +
               "                    <tr><td colspan=\"9\" class=\"empty-state\">🔄 Carregando...</td></tr>\n" +
               "                </tbody>\n" +
               "            </table>\n" +
               "        </div>\n" +
               "    </div>\n" +
               "\n" +
               "    <div class=\"toast-container\" id=\"toast-container\"></div>\n" +
               "\n" +
               "    <script>\n" +
               "        // --- GLOBAIS ---\n" +
               "        let adminToken = localStorage.getItem('adminToken');\n" +
               "        let devices = [];\n" +
               "        let sortState = { col: 'dataRegistro', dir: 'desc' };\n" +
               "        let statusChartInstance = null;\n" +
               "        let timelineChartInstance = null;\n" +
               "\n" +
               "        // --- TOAST ---\n" +
               "        function showToast(msg, type = 'info') {\n" +
               "            const container = document.getElementById('toast-container');\n" +
               "            const toast = document.createElement('div');\n" +
               "            toast.className = 'toast ' + type;\n" +
               "            toast.textContent = msg;\n" +
               "            container.appendChild(toast);\n" +
               "            setTimeout(() => { toast.style.animation = 'slideOutRight 0.3s ease forwards'; setTimeout(() => toast.remove(), 300); }, 3000);\n" +
               "        }\n" +
               "\n" +
               "        // --- LOGIN ---\n" +
               "        document.getElementById('login-btn').addEventListener('click', () => {\n" +
               "            const senha = document.getElementById('senha-input').value;\n" +
               "            fetch('/api/login', {\n" +
               "                method: 'POST',\n" +
               "                headers: { 'Content-Type': 'application/json' },\n" +
               "                body: JSON.stringify({ senha })\n" +
               "            }).then(r => r.json()).then(d => {\n" +
               "                if (d.success) {\n" +
               "                    adminToken = d.token;\n" +
               "                    localStorage.setItem('adminToken', adminToken);\n" +
               "                    document.getElementById('login-screen').style.display = 'none';\n" +
               "                    document.getElementById('dashboard').style.display = 'block';\n" +
               "                    carregarDados();\n" +
               "                } else {\n" +
               "                    document.getElementById('login-error').style.display = 'block';\n" +
               "                    setTimeout(() => document.getElementById('login-error').style.display = 'none', 3000);\n" +
               "                }\n" +
               "            }).catch(() => showToast('Erro de conexão com o servidor', 'error'));\n" +
               "        });\n" +
               "\n" +
               "        document.getElementById('senha-input').addEventListener('keydown', e => {\n" +
               "            if (e.key === 'Enter') document.getElementById('login-btn').click();\n" +
               "        });\n" +
               "\n" +
               "        // Autologin se token válido existir\n" +
               "        if (adminToken) {\n" +
               "            fetch('/api/devices', { headers: { 'X-Admin-Token': adminToken } })\n" +
               "                .then(r => {\n" +
               "                    if (r.status === 200) {\n" +
               "                        document.getElementById('login-screen').style.display = 'none';\n" +
               "                        document.getElementById('dashboard').style.display = 'block';\n" +
               "                        carregarDados();\n" +
               "                    } else {\n" +
               "                        localStorage.removeItem('adminToken');\n" +
               "                    }\n" +
               "                }).catch(() => {});\n" +
               "        }\n" +
               "\n" +
               "        document.getElementById('logout-btn').addEventListener('click', () => {\n" +
               "            adminToken = null;\n" +
               "            localStorage.removeItem('adminToken');\n" +
               "            document.getElementById('dashboard').style.display = 'none';\n" +
               "            document.getElementById('login-screen').style.display = 'flex';\n" +
               "            document.getElementById('senha-input').value = '';\n" +
               "        });\n" +
               "\n" +
               "        // --- DADOS ---\n" +
               "        function carregarDados() {\n" +
               "            const headers = { 'X-Admin-Token': adminToken };\n" +
               "            fetch('/api/devices', { headers })\n" +
               "                .then(r => r.json())\n" +
               "                .then(d => {\n" +
               "                    devices = d;\n" +
               "                    atualizarTabela();\n" +
               "                    atualizarMetricas();\n" +
               "                    atualizarGraficos();\n" +
               "                }).catch(() => showToast('Erro ao carregar dispositivos', 'error'));\n" +
               "        }\n" +
               "\n" +
               "        document.getElementById('refresh-btn').addEventListener('click', carregarDados);\n" +
               "\n" +
               "        // --- ATUALIZAR MÉTRICAS ---\n" +
               "        function atualizarMetricas() {\n" +
               "            const total = devices.length;\n" +
               "            const ativos = devices.filter(d => d.status === 'ATIVO').length;\n" +
               "            const bloqueados = devices.filter(d => d.status === 'BLOQUEADO').length;\n" +
               "            const pendentes = devices.filter(d => d.status === 'PENDENTE').length;\n" +
               "            document.getElementById('total-devices').textContent = total;\n" +
               "            document.getElementById('active-devices').textContent = ativos;\n" +
               "            document.getElementById('blocked-devices').textContent = bloqueados;\n" +
               "            document.getElementById('pending-devices').textContent = pendentes;\n" +
               "        }\n" +
               "\n" +
               "        // --- TABELA ---\n" +
               "        function atualizarTabela() {\n" +
               "            const search = document.getElementById('search-input').value.toLowerCase();\n" +
               "            const filter = document.getElementById('filter-select').value;\n" +
               "\n" +
               "            let filtered = devices.filter(d => {\n" +
               "                if (filter !== 'todos' && d.status !== filter) return false;\n" +
               "                return (d.hardwareId && d.hardwareId.toLowerCase().includes(search)) ||\n" +
               "                       (d.nomeAparelho && d.nomeAparelho.toLowerCase().includes(search)) ||\n" +
               "                       (d.nomeCliente && d.nomeCliente.toLowerCase().includes(search));\n" +
               "            });\n" +
               "\n" +
               "            const sorted = [...filtered].sort((a, b) => {\n" +
               "                let va = a[sortState.col], vb = b[sortState.col];\n" +
               "                if (typeof va === 'string') va = va.toLowerCase();\n" +
               "                if (typeof vb === 'string') vb = vb.toLowerCase();\n" +
               "                if (va < vb) return sortState.dir === 'asc' ? -1 : 1;\n" +
               "                if (va > vb) return sortState.dir === 'asc' ? 1 : -1;\n" +
               "                return 0;\n" +
               "            });\n" +
               "\n" +
               "            const tbody = document.getElementById('devices-tbody');\n" +
               "            if (sorted.length === 0) {\n" +
               "                tbody.innerHTML = '<tr><td colspan=\"9\" class=\"empty-state\">Nenhum dispositivo encontrado</td></tr>';\n" +
               "                return;\n" +
               "            }\n" +
               "\n" +
               "            tbody.innerHTML = sorted.map(d => {\n" +
               "                const isOnline = (Date.now() - d.ultimoPing) < 60000;\n" +
               "                const regDate = new Date(d.dataRegistro).toLocaleString('pt-BR');\n" +
               "                const expDate = d.dataExpiracao > 0 ? new Date(d.dataExpiracao).toLocaleString('pt-BR') : '—';\n" +
               "                const expClass = d.dataExpiracao > 0 ? (d.dataExpiracao - Date.now() < 86400000 ? 'exp-critical' : (d.dataExpiracao - Date.now() < 604800000 ? 'exp-warning' : 'exp-ok')) : '';\n" +
               "                const lastPingStr = d.ultimoPing > 0 ? new Date(d.ultimoPing).toLocaleString('pt-BR') : '—';\n" +
               "                const sopIcon = d.soTipo && d.soTipo.toLowerCase().includes('android') ? '🤖' : '💻';\n" +
               "                return `<tr class=\"${d.dataExpiracao > 0 && d.dataExpiracao - Date.now() < 86400000 ? 'critical-row' : ''}\">\n" +
               "                    <td><div class=\"device-badge\"><span class=\"online-dot ${isOnline ? 'online' : 'offline'}\"></span><code style=\"background:rgba(255,255,255,0.05);padding:4px 8px;border-radius:6px;font-size:13px\">${d.hardwareId}</code></div></td>\n" +
               "                    <td>${sopIcon} ${d.nomeAparelho}</td>\n" +
               "                    <td>${d.soTipo}</td>\n" +
               "                    <td>${d.nomeCliente || '—'}</td>\n" +
               "                    <td style=\"font-size:13px;color:var(--text-muted)\">${regDate}</td>\n" +
               "                    <td class=\"${expClass}\" style=\"font-size:13px\">${expDate}</td>\n" +
               "                    <td><span class=\"status-pill ${d.status.toLowerCase()}\">${d.status}</span></td>\n" +
               "                    <td><span class=\"last-seen\">${lastPingStr} ${isOnline ? '<span style=\"color:var(--success)\">●</span>' : '<span style=\"color:var(--danger)\">○</span>'}</span></td>\n" +
               "                    <td>\n" +
               "                        <div class=\"actions\">\n" +
               "                            ${d.status === 'PENDENTE' ? `<button class=\"btn-action approve\" onclick=\"aprovar('${d.hardwareId}')\">✅ Aprovar</button>` : ''}\n" +
               "                            ${d.status === 'ATIVO' ? `<button class=\"btn-action block\" onclick=\"bloquear('${d.hardwareId}')\">⛔ Bloquear</button>` : ''}\n" +
               "                            <button class=\"btn-action edit\" onclick=\"abrirEdicao('${d.hardwareId}')\">✎ Editar</button>\n" +
               "                            <button class=\"btn-action send\" onclick=\"enviarConfig('${d.hardwareId}')\">📤 Enviar</button>\n" +
               "                            <button class=\"btn-action delete\" onclick=\"excluir('${d.hardwareId}')\">🗑 Excluir</button>\n" +
               "                        </div>\n" +
               "                    </td>\n" +
               "                </tr>`;\n" +
               "            }).join('');\n" +
               "        }\n" +
               "\n" +
               "        document.getElementById('search-input').addEventListener('input', atualizarTabela);\n" +
               "        document.getElementById('filter-select').addEventListener('change', atualizarTabela);\n" +
               "\n" +
               "        // Sortable headers\n" +
               "        document.querySelectorAll('th.sortable').forEach(th => {\n" +
               "            th.addEventListener('click', () => {\n" +
               "                const col = th.dataset.col;\n" +
               "                if (sortState.col === col) sortState.dir = sortState.dir === 'asc' ? 'desc' : 'asc';\n" +
               "                else { sortState.col = col; sortState.dir = 'asc'; }\n" +
               "                document.querySelectorAll('th.sortable').forEach(h => h.classList.remove('asc', 'desc'));\n" +
               "                th.classList.add(sortState.dir);\n" +
               "                atualizarTabela();\n" +
               "            });\n" +
               "        });\n" +
               "\n" +
               "        // --- GRÁFICOS ---\n" +
               "        function atualizarGraficos() {\n" +
               "            const ativos = devices.filter(d => d.status === 'ATIVO').length;\n" +
               "            const bloqueados = devices.filter(d => d.status === 'BLOQUEADO').length;\n" +
               "            const pendentes = devices.filter(d => d.status === 'PENDENTE').length;\n" +
               "\n" +
               "            if (statusChartInstance) statusChartInstance.destroy();\n" +
               "            const ctx1 = document.getElementById('statusChart').getContext('2d');\n" +
               "            statusChartInstance = new Chart(ctx1, {\n" +
               "                type: 'doughnut',\n" +
               "                data: {\n" +
               "                    labels: ['Ativos', 'Bloqueados', 'Pendentes'],\n" +
               "                    datasets: [{\n" +
               "                        data: [ativos, bloqueados, pendentes],\n" +
               "                        backgroundColor: ['#34c759', '#ff3b30', '#ff9500'],\n" +
               "                        borderWidth: 0\n" +
               "                    }]\n" +
               "                },\n" +
               "                options: {\n" +
               "                    responsive: true,\n" +
               "                    plugins: { legend: { labels: { color: '#8e8e93', font: { family: 'Outfit' } } } }\n" +
               "                }\n" +
               "            });\n" +
               "\n" +
               "            // Timeline chart — agrupando por dia\n" +
               "            if (timelineChartInstance) timelineChartInstance.destroy();\n" +
               "            const ctx2 = document.getElementById('timelineChart').getContext('2d');\n" +
               "            const grupos = {};\n" +
               "            const hoje = new Date();\n" +
               "            for (let i = 13; i >= 0; i--) {\n" +
               "                const d = new Date(hoje);\n" +
               "                d.setDate(d.getDate() - i);\n" +
               "                const key = d.toLocaleDateString('pt-BR');\n" +
               "                grupos[key] = 0;\n" +
               "            }\n" +
               "            devices.forEach(d => {\n" +
               "                const dt = new Date(d.dataRegistro).toLocaleDateString('pt-BR');\n" +
               "                if (grupos[dt] !== undefined) grupos[dt]++;\n" +
               "            });\n" +
               "            const labels = Object.keys(grupos);\n" +
               "            const valores = Object.values(grupos);\n" +
               "            timelineChartInstance = new Chart(ctx2, {\n" +
               "                type: 'bar',\n" +
               "                data: {\n" +
               "                    labels,\n" +
               "                    datasets: [{\n" +
               "                        label: 'Registros',\n" +
               "                        data: valores,\n" +
               "                        backgroundColor: '#5856d6',\n" +
               "                        borderRadius: 4\n" +
               "                    }]\n" +
               "                },\n" +
               "                options: {\n" +
               "                    responsive: true,\n" +
               "                    plugins: { legend: { display: false } },\n" +
               "                    scales: {\n" +
               "                        x: { ticks: { color: '#8e8e93', font: { family: 'Outfit' } }, grid: { display: false } },\n" +
               "                        y: { ticks: { color: '#8e8e93', font: { family: 'Outfit' } }, grid: { color: 'rgba(255,255,255,0.05)' } }\n" +
               "                    }\n" +
               "                }\n" +
               "            });\n" +
               "        }\n" +
               "\n" +
               "        // --- AÇÕES ---\n" +
               "        function aprovar(hardwareId) {\n" +
               "            const dias = prompt('Dias de licença (mínimo 30):', '30');\n" +
               "            if (!dias) return;\n" +
               "            fetch('/api/approve', {\n" +
               "                method: 'POST',\n" +
               "                headers: { 'Content-Type': 'application/json', 'X-Admin-Token': adminToken },\n" +
               "                body: JSON.stringify({ hardwareId, dias })\n" +
               "            }).then(r => r.json()).then(d => {\n" +
               "                if (d.success) { showToast('✅ Dispositivo aprovado!', 'success'); carregarDados(); }\n" +
               "                else showToast('Erro ao aprovar', 'error');\n" +
               "            }).catch(() => showToast('Erro de conexão', 'error'));\n" +
               "        }\n" +
               "\n" +
               "        function bloquear(hardwareId) {\n" +
               "            if (!confirm('Bloquear este dispositivo?')) return;\n" +
               "            fetch('/api/block', {\n" +
               "                method: 'POST',\n" +
               "                headers: { 'Content-Type': 'application/json', 'X-Admin-Token': adminToken },\n" +
               "                body: JSON.stringify({ hardwareId })\n" +
               "            }).then(r => r.json()).then(d => {\n" +
               "                if (d.success) { showToast('⛔ Dispositivo bloqueado!', 'warning'); carregarDados(); }\n" +
               "                else showToast('Erro ao bloquear', 'error');\n" +
               "            }).catch(() => showToast('Erro de conexão', 'error'));\n" +
               "        }\n" +
               "\n" +
               "        function excluir(hardwareId) {\n" +
               "            if (!confirm('Excluir permanentemente este dispositivo?')) return;\n" +
               "            fetch('/api/delete', {\n" +
               "                method: 'POST',\n" +
               "                headers: { 'Content-Type': 'application/json', 'X-Admin-Token': adminToken },\n" +
               "                body: JSON.stringify({ hardwareId })\n" +
               "            }).then(r => r.json()).then(d => {\n" +
               "                if (d.success) { showToast('🗑 Dispositivo excluído!', 'info'); carregarDados(); }\n" +
               "                else showToast('Erro ao excluir', 'error');\n" +
               "            }).catch(() => showToast('Erro de conexão', 'error'));\n" +
               "        }\n" +
               "\n" +
               "        function enviarConfig(hardwareId) {\n" +
               "            if (!confirm('Enviar configurações pendentes para este dispositivo?')) return;\n" +
               "            fetch('/api/send-config', {\n" +
               "                method: 'POST',\n" +
               "                headers: { 'Content-Type': 'application/json', 'X-Admin-Token': adminToken },\n" +
               "                body: JSON.stringify({ hardwareId })\n" +
               "            }).then(r => r.json()).then(d => {\n" +
               "                if (d.success) { showToast('📤 Configurações enviadas!', 'success'); carregarDados(); }\n" +
               "                else showToast('Erro ao enviar', 'error');\n" +
               "            }).catch(() => showToast('Erro de conexão', 'error'));\n" +
               "        }\n" +
               "\n" +
               "        function abrirEdicao(hardwareId) {\n" +
               "            const device = devices.find(d => d.hardwareId === hardwareId);\n" +
               "            if (!device) return;\n" +
               "            document.body.insertAdjacentHTML('beforeend', `\n" +
               "                <div class=\"modal-overlay\" id=\"edit-modal\">\n" +
               "                    <div class=\"modal-card\">\n" +
               "                        <h3>✎ Editar: <code style=\"color:var(--primary);font-size:16px\">${hardwareId}</code></h3>\n" +
               "                        <p style=\"color:var(--text-muted);margin:5px 0 20px;font-size:14px\">Configure os dados e clique em Salvar</p>\n" +
               "                        <div class=\"form-group\">\n" +
               "                            <label>Nome do Cliente</label>\n" +
               "                            <input id=\"edit-nome\" value=\"${device.nomeCliente || ''}\" placeholder=\"Ex: Estacionamento Centro\" />\n" +
               "                        </div>\n" +
               "                        <div class=\"form-grid-3\">\n" +
               "                            <div class=\"form-group\">\n" +
               "                                <label>Tarifa/Hora (R$)</label>\n" +
               "                                <input id=\"edit-tarifa\" type=\"number\" step=\"0.5\" min=\"0\" value=\"${device.tarifaHora}\" />\n" +
               "                            </div>\n" +
               "                            <div class=\"form-group\">\n" +
               "                                <label>Vagas Carro</label>\n" +
               "                                <input id=\"edit-vagas-carro\" type=\"number\" min=\"0\" value=\"${device.vagasCarro}\" />\n" +
               "                            </div>\n" +
               "                            <div class=\"form-group\">\n" +
               "                                <label>Vagas Moto</label>\n" +
               "                                <input id=\"edit-vagas-moto\" type=\"number\" min=\"0\" value=\"${device.vagasMoto}\" />\n" +
               "                            </div>\n" +
               "                        </div>\n" +
               "                        <div class=\"form-group\">\n" +
               "                            <label>Dias de Licença (mín. 30)</label>\n" +
               "                            <input id=\"edit-dias\" type=\"number\" min=\"30\" value=\"${device.diasLicencaPendente > 0 ? device.diasLicencaPendente : 30}\" />\n" +
               "                        </div>\n" +
               "                        <div class=\"modal-actions\">\n" +
               "                            <button class=\"btn-cancel\" onclick=\"document.getElementById('edit-modal').remove()\">Cancelar</button>\n" +
               "                            <button class=\"btn-save\" onclick=\"salvarEdicao('${hardwareId}')\">💾 Salvar</button>\n" +
               "                        </div>\n" +
               "                    </div>\n" +
               "                </div>\n" +
               "            `);\n" +
               "        }\n" +
               "\n" +
               "        function salvarEdicao(hardwareId) {\n" +
               "            const nomeCliente = document.getElementById('edit-nome').value;\n" +
               "            const tarifaHora = parseFloat(document.getElementById('edit-tarifa').value) || 0;\n" +
               "            const vagasCarro = parseInt(document.getElementById('edit-vagas-carro').value) || 0;\n" +
               "            const vagasMoto = parseInt(document.getElementById('edit-vagas-moto').value) || 0;\n" +
               "            const diasLicenca = parseInt(document.getElementById('edit-dias').value) || 30;\n" +
               "            fetch('/api/update-config', {\n" +
               "                method: 'POST',\n" +
               "                headers: { 'Content-Type': 'application/json', 'X-Admin-Token': adminToken },\n" +
               "                body: JSON.stringify({ hardwareId, nomeCliente, tarifaHora, vagasCarro, vagasMoto, diasLicenca })\n" +
               "            }).then(r => r.json()).then(d => {\n" +
               "                if (d.success) {\n" +
               "                    showToast('💾 Configurações salvas como pendentes!', 'success');\n" +
               "                    document.getElementById('edit-modal').remove();\n" +
               "                    carregarDados();\n" +
               "                } else showToast('Erro ao salvar', 'error');\n" +
               "            }).catch(() => showToast('Erro de conexão', 'error'));\n" +
               "        }\n" +

               "        // Auto refresh a cada 15 segundos\n" +
               "        if (adminToken) {\n" +
               "            setInterval(carregarDados, 15000);\n" +
               "        }\n" +
               "\n" +
               "        // Inicializar dados\n" +
               "        if (adminToken) carregarDados();\n" +
               "    </script>\n" +
               "</body>\n" +
               "</html>";
    }
}
