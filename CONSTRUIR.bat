@echo off
title QuestLog — Compilar APK
color 0A
echo.
echo  =====================================================
echo   QUESTLOG — Compilador de APK
echo  =====================================================
echo.

:: ── 1. Verificar Java ────────────────────────────────────
java -version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java no encontrado.
    echo         Instala Android Studio que incluye Java automaticamente:
    echo         https://developer.android.com/studio
    pause & exit /b 1
)
echo [OK] Java detectado

:: ── 2. Descargar gradle-wrapper.jar si falta ─────────────
if not exist "gradle\wrapper\gradle-wrapper.jar" (
    echo [INFO] Descargando gradle-wrapper.jar...
    powershell -Command "Invoke-WebRequest -Uri 'https://github.com/gradle/gradle/raw/v8.4.0/gradle/wrapper/gradle-wrapper.jar' -OutFile 'gradle\wrapper\gradle-wrapper.jar'" 2>nul
    if not exist "gradle\wrapper\gradle-wrapper.jar" (
        echo [INFO] Intentando con curl...
        curl -sL "https://github.com/gradle/gradle/raw/v8.4.0/gradle/wrapper/gradle-wrapper.jar" -o "gradle\wrapper\gradle-wrapper.jar"
    )
)

if not exist "gradle\wrapper\gradle-wrapper.jar" (
    echo.
    echo [INFO] No se pudo descargar automaticamente.
    echo        Buscando Gradle de Android Studio...
    
    :: Try Android Studio's bundled Gradle
    set AS_GRADLE=%LOCALAPPDATA%\Google\AndroidStudio*\plugins\android\lib\native\win\x86_64
    for /d %%G in ("%LOCALAPPDATA%\Google\AndroidStudio*") do (
        if exist "%%G\plugins\gradle\lib\gradle-wrapper.jar" (
            copy "%%G\plugins\gradle\lib\gradle-wrapper.jar" "gradle\wrapper\gradle-wrapper.jar" >nul
            echo [OK] gradle-wrapper.jar copiado desde Android Studio
        )
    )
)

if not exist "gradle\wrapper\gradle-wrapper.jar" (
    echo.
    echo [ERROR] No se encontro gradle-wrapper.jar
    echo.
    echo  Solucion: Abre el proyecto en Android Studio una vez,
    echo  deja que sincronice, y luego cierra. Despues vuelve
    echo  a ejecutar este script.
    echo.
    echo  O simplemente en Android Studio:
    echo  Build - Build Bundle / APK - Build APK
    pause & exit /b 1
)

echo [OK] Gradle wrapper listo

:: ── 3. Compilar APK debug ────────────────────────────────
echo.
echo [INFO] Compilando APK debug...
echo        (Primera vez puede tardar 5-10 min descargando dependencias)
echo.

call gradlew.bat assembleDebug

if errorlevel 1 (
    echo.
    echo [ERROR] La compilacion ha fallado.
    echo         Revisa los errores arriba.
    pause & exit /b 1
)

:: ── 4. Resultado ─────────────────────────────────────────
echo.
echo  =====================================================
echo   APK generada correctamente!
echo  =====================================================
echo.
echo   Ubicacion:
echo   app\build\outputs\apk\debug\app-debug.apk
echo.

:: Abrir carpeta automaticamente
explorer app\build\outputs\apk\debug\ 2>nul

:: ── 5. Instalar en movil (opcional) ──────────────────────
echo  ¿Instalar en el movil conectado por USB? (S/N)
set /p INSTALAR=   Respuesta: 
if /i "%INSTALAR%"=="S" (
    echo.
    echo [INFO] Buscando dispositivo...
    adb devices
    echo.
    adb install -r app\build\outputs\apk\debug\app-debug.apk
    if errorlevel 1 (
        echo [AVISO] No se pudo instalar. Asegurate de tener USB debugging activado.
    ) else (
        echo [OK] QuestLog instalado en el movil!
    )
)

echo.
pause
