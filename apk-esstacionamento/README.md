# Sistema de Estacionamento

Sistema para gerenciamento de estacionamento com versões Desktop (Swing) e Android.

## Funcionalidades

- Dashboard com indicadores em tempo real
- Registrar entrada de veiculos
- Registrar saida com calculo de tarifa e troco
- Listar veiculos estacionados
- Historico completo de transacoes
- Relatorio de receita
- Alterar tarifa por hora em tempo real
- Persistencia automatica em arquivos CSV

## Estrutura

```
apk-estacionamento/
├── src/                               # Versao Desktop (Swing)
│   └── com/estacionamento/
│       ├── Main.java                  # Interface grafica Swing
│       ├── Veiculo.java
│       ├── Pagamento.java
│       ├── CalculadoraTarifa.java
│       ├── Transacao.java
│       ├── EstacionamentoService.java
│       └── EstacionamentoRepository.java
├── app/                               # Versao Android
│   └── src/main/
│       ├── java/com/estacionamento/   # Logica + Activities/Fragments
│       └── res/                       # Layouts XML (Material Design)
├── build.gradle                       # Build Android
└── settings.gradle
```

## Desktop (Swing)

```bash
javac -encoding UTF-8 -d bin -sourcepath src src/com/estacionamento/*.java
java -cp bin com.estacionamento.Main
```

## Android

1. Abra a pasta `apk-estacionamento` no **Android Studio**
2. O Android Studio vai baixar o Gradle e as dependencias automaticamente
3. Conecte seu celular ou use um emulador
4. Clique em **Run** (ou `Shift+F10`)

> Requisitos: Android Studio, JDK 11+, Android SDK (gerenciado pelo Android Studio)
