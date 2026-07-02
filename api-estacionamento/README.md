# API REST - Sistema de Estacionamento

API REST para gerenciamento de estacionamento com Spring Boot.

## Tecnologias

- Java 11
- Spring Boot 2.7.18
- Spring Data JPA
- H2 Database (file-based)
- Maven

## Endpoints

### Veiculos

| Método | Rota | Descricao |
|--------|------|-----------|
| POST | `/api/veiculos/entrada` | Registrar entrada de veiculo |
| POST | `/api/veiculos/saida` | Registrar saida e pagamento |
| GET | `/api/veiculos/estacionados` | Listar veiculos estacionados |

### Transacoes

| Método | Rota | Descricao |
|--------|------|-----------|
| GET | `/api/transacoes` | Historico de transacoes |

### Relatorio

| Método | Rota | Descricao |
|--------|------|-----------|
| GET | `/api/relatorio/receita` | Relatorio de receita |

### Configuracao

| Método | Rota | Descricao |
|--------|------|-----------|
| GET | `/api/config/tarifa` | Obter tarifa atual |
| PUT | `/api/config/tarifa` | Alterar tarifa por hora |

## Exemplos

```bash
# Registrar entrada
curl -X POST http://localhost:8080/api/veiculos/entrada \
  -H "Content-Type: application/json" \
  -d '{"placa":"ABC-1234"}'

# Listar estacionados
curl http://localhost:8080/api/veiculos/estacionados

# Registrar saida
curl -X POST http://localhost:8080/api/veiculos/saida \
  -H "Content-Type: application/json" \
  -d '{"placa":"ABC-1234","valorPago":10.0}'

# Alterar tarifa
curl -X PUT http://localhost:8080/api/config/tarifa \
  -H "Content-Type: application/json" \
  -d '{"tarifaHora":7.5}'

# Relatorio
curl http://localhost:8080/api/relatorio/receita
```

## Compilar e Executar

```bash
# Compilar
mvn clean package -DskipTests

# Executar
java -jar target/api-estacionamento-1.0.0.jar
```

O servidor inicia em `http://localhost:8080`.

Console H2 disponivel em `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:file:./data/estacionamento`, usuario: `sa`).
