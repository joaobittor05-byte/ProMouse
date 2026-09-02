@echo off
setlocal
chcp 65001 >nul

echo ===============================================
echo        LEO OPTIMAZER - ADB SHELL ACTIVATE
echo ===============================================
echo.

where adb >nul 2>nul
if errorlevel 1 (
  echo [ERRO] adb.exe nao foi encontrado no PATH.
  echo Instale/abra o Android Platform Tools e execute este arquivo por la.
  pause
  exit /b 1
)

adb start-server >nul
adb get-state 1>nul 2>nul
if errorlevel 1 (
  echo [ERRO] Nenhum Android autorizado no ADB USB.
  echo Conecte o celular, ative Depuracao USB e aceite a autorizacao RSA.
  pause
  exit /b 2
)

set "LEO_APK="
for /f "tokens=2 delims=:" %%A in ('adb shell pm path com.leo.optimazer') do set "LEO_APK=%%A"

if not defined LEO_APK (
  echo [ERRO] Leo Optimazer nao esta instalado no aparelho.
  pause
  exit /b 3
)

echo APK: %LEO_APK%
echo Iniciando bridge com UID shell...
adb shell "app_process -Djava.class.path=%LEO_APK% /system/bin com.leo.optimazer.bridge.ShellBridge ^</dev/null ^>/dev/null 2^>^&1 ^&"

ping 127.0.0.1 -n 2 >nul
echo.
echo [OK] Comando de ativacao enviado.
echo Abra o Leo Optimazer e toque em VERIFICAR ATIVACAO.
echo A ativacao precisa ser refeita depois que o Android for reiniciado.
echo.
pause
