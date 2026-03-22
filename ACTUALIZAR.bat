@echo off
title QuestLog — Actualizar HTML y reinstalar
color 0B
echo.
echo  =====================================================
echo   QUESTLOG — Actualizar HTML en la APK
echo  =====================================================
echo.

:: Copiar HTML nuevo al proyecto
if "%1"=="" (
    echo  Arrastra tu QuestLog_vX.html sobre este .bat
    echo  o escribe la ruta del HTML:
    set /p HTML_PATH=   Ruta: 
) else (
    set HTML_PATH=%1
)

if not exist "%HTML_PATH%" (
    echo [ERROR] Archivo no encontrado: %HTML_PATH%
    pause & exit /b 1
)

echo [INFO] Copiando HTML...
copy /Y "%HTML_PATH%" "app\src\main\assets\index.html"
echo [OK] index.html actualizado

echo [INFO] Compilando...
call gradlew.bat assembleDebug
if errorlevel 1 (
    echo [ERROR] Compilacion fallida.
    pause & exit /b 1
)

echo [INFO] Instalando en el movil...
adb install -r app\build\outputs\apk\debug\app-debug.apk
if errorlevel 1 (
    echo [AVISO] Instala manualmente: app\build\outputs\apk\debug\app-debug.apk
) else (
    echo [OK] Instalado! Abre QuestLog en el movil.
)

echo.
pause
