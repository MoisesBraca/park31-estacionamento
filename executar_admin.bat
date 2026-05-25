@echo off
cd /d "%~dp0"
echo ============================================
echo  Park '31 - Painel Administrativo
echo  Recompilando e iniciando servidor...
echo ============================================
echo.

:: Compilar todos os arquivos Java do desktop
javac -encoding UTF-8 -cp "lib\sqlite-jdbc.jar" -d build_temp -sourcepath src src\com\estacionamento\Main.java

:: Verificar se compilou
if %ERRORLEVEL% neq 0 (
    echo ERRO: Falha na compilacao. Verifique os erros acima.
    pause
    exit /b 1
)

:: Iniciar o servidor + interface desktop
java -cp "build_temp;lib\sqlite-jdbc.jar" com.estacionamento.Main

:: Limpar
rmdir /s /q build_temp 2>nul
pause
