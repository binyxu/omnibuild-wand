@echo off
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion
echo.
echo === Building Wand Mod ===
echo.

set "PROJECT_DIR=C:\Users\20129\BuildingWandMod"
set "TOOLS_DIR=C:\Users\20129\.bwmod_tools"

:: --- Find or download Java 25 ---
set "JAVA_HOME="
for /d %%D in ("%TOOLS_DIR%\jdk-25*") do (
    if exist "%%D\bin\java.exe" set "JAVA_HOME=%%D"
)

if not defined JAVA_HOME (
    echo [INFO] Downloading Java 25 (~200 MB)...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://api.adoptium.net/v3/binary/latest/25/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk' -OutFile '%TEMP%\jdk25.zip'"
    if !errorlevel! NEQ 0 ( echo [ERROR] Java 25 download failed & goto :end )
    echo [INFO] Extracting Java 25...
    if not exist "%TOOLS_DIR%" mkdir "%TOOLS_DIR%"
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%TEMP%\jdk25.zip' -DestinationPath '%TOOLS_DIR%' -Force"
    del "%TEMP%\jdk25.zip" >nul 2>&1
    for /d %%D in ("%TOOLS_DIR%\jdk-25*") do (
        if exist "%%D\bin\java.exe" set "JAVA_HOME=%%D"
    )
)
if not defined JAVA_HOME ( echo [ERROR] Java 25 not found after download & goto :end )
echo [OK] Java: %JAVA_HOME%

:: --- Find or download Gradle ---
set "GRADLE_CMD="
for /d %%D in ("%TOOLS_DIR%\gradle-*") do (
    if exist "%%D\bin\gradle.bat" set "GRADLE_CMD=%%D\bin\gradle.bat"
)

if not defined GRADLE_CMD (
    echo [INFO] Getting Gradle version...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$v=(Invoke-RestMethod 'https://services.gradle.org/versions/current').version; Set-Content '%TEMP%\gver.txt' $v"
    set /p GVER=<"%TEMP%\gver.txt"
    set "GVER=!GVER: =!"
    echo [INFO] Downloading Gradle !GVER!...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-!GVER!-bin.zip' -OutFile '%TEMP%\gradle.zip'"
    if !errorlevel! NEQ 0 ( echo [ERROR] Gradle download failed & goto :end )
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%TEMP%\gradle.zip' -DestinationPath '%TOOLS_DIR%' -Force"
    del "%TEMP%\gradle.zip" >nul 2>&1
    for /d %%D in ("%TOOLS_DIR%\gradle-*") do (
        if exist "%%D\bin\gradle.bat" set "GRADLE_CMD=%%D\bin\gradle.bat"
    )
)
if not defined GRADLE_CMD ( echo [ERROR] Gradle not found & goto :end )
echo [OK] Gradle: %GRADLE_CMD%

:: --- Wrapper ---
if not exist "%PROJECT_DIR%\gradle\wrapper\gradle-wrapper.jar" (
    echo [INFO] Generating wrapper...
    cd /d "%PROJECT_DIR%"
    call "%GRADLE_CMD%" wrapper
    if !errorlevel! NEQ 0 ( echo [ERROR] Wrapper failed & goto :end )
)

:: --- Build ---
echo [INFO] Building (first run downloads MC deps)...
cd /d "%PROJECT_DIR%"
call "%PROJECT_DIR%\gradlew.bat" build
if %errorlevel% NEQ 0 ( echo [ERROR] Build failed & goto :end )

echo.
echo [SUCCESS] JAR ready:
echo %PROJECT_DIR%\build\libs\buildingwand-1.0.0+26.1.2.jar
echo Copy to: D:\Game\MC\HMCL-3.6.12\.minecraft\mods\

:end
echo.
echo Press any key to close...
pause >nul
