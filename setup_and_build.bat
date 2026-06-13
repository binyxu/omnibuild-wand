@echo off
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

set "PROJECT_DIR=%~dp0"
set "LOG=%PROJECT_DIR%build_log.txt"
set "TOOLS_DIR=%USERPROFILE%\.bwmod_tools"

echo Build started: %date% %time% > "%LOG%"
call :main >> "%LOG%" 2>&1
set EXITCODE=%errorlevel%

echo.
echo Build log: %PROJECT_DIR%build_log.txt
echo.
if %EXITCODE% NEQ 0 (
    echo [FAILED] See build_log.txt for details.
) else (
    echo [SUCCESS] JAR: %PROJECT_DIR%build\libs\buildingwand-1.0.0+26.1.2.jar
    echo Copy to: D:\Game\MC\HMCL-3.6.12\.minecraft\mods\
)
pause
exit /b %EXITCODE%

:: ============================================================
:main
echo ===================================================
echo  Building Wand Mod
echo ===================================================

:: --- Find Java 25 (required by MC 26.1.2) ---
set "JAVA_HOME="
for /d %%D in ("%TOOLS_DIR%\jdk-25*") do (
    if exist "%%D\bin\java.exe" set "JAVA_HOME=%%D"
)

if not defined JAVA_HOME (
    echo [INFO] Java 25 not found locally. Downloading Eclipse Temurin 25 (~200 MB)...
    set "JAVA_ZIP=%TEMP%\temurin25.zip"
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
        "Invoke-WebRequest -Uri 'https://api.adoptium.net/v3/binary/latest/25/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk' -OutFile '!JAVA_ZIP!'"
    if !errorlevel! NEQ 0 ( echo [ERROR] Java 25 download failed & exit /b 1 )

    echo [INFO] Extracting Java 25...
    if not exist "%TOOLS_DIR%" mkdir "%TOOLS_DIR%"
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
        "Expand-Archive -Path '!JAVA_ZIP!' -DestinationPath '%TOOLS_DIR%' -Force"
    del "!JAVA_ZIP!" >nul 2>&1

    for /d %%D in ("%TOOLS_DIR%\jdk-25*") do (
        if exist "%%D\bin\java.exe" set "JAVA_HOME=%%D"
    )
)
if not defined JAVA_HOME ( echo [ERROR] Java 25 setup failed & exit /b 1 )

echo [OK] Java 21: %JAVA_HOME%
"%JAVA_HOME%\bin\java.exe" -version

:: --- Find or download Gradle ---
set "GRADLE_CMD="
for /d %%D in ("%TOOLS_DIR%\gradle-*") do (
    if exist "%%D\bin\gradle.bat" set "GRADLE_CMD=%%D\bin\gradle.bat"
)

if not defined GRADLE_CMD (
    echo [INFO] Querying latest Gradle version...
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
        "$v=(Invoke-RestMethod 'https://services.gradle.org/versions/current').version; $v|Out-File -Encoding ASCII '%TEMP%\gver.txt'"
    if !errorlevel! NEQ 0 ( echo [ERROR] Cannot query Gradle version & exit /b 1 )
    set /p GRADLE_VER=<"%TEMP%\gver.txt"
    set "GRADLE_VER=!GRADLE_VER: =!"
    echo [INFO] Downloading Gradle !GRADLE_VER!...

    set "GRADLE_ZIP=%TEMP%\gradle-!GRADLE_VER!-bin.zip"
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
        "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-!GRADLE_VER!-bin.zip' -OutFile '!GRADLE_ZIP!'"
    if !errorlevel! NEQ 0 ( echo [ERROR] Gradle download failed & exit /b 1 )

    echo [INFO] Extracting Gradle...
    if not exist "%TOOLS_DIR%" mkdir "%TOOLS_DIR%"
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
        "Expand-Archive -Path '!GRADLE_ZIP!' -DestinationPath '%TOOLS_DIR%' -Force"
    del "!GRADLE_ZIP!" >nul 2>&1

    for /d %%D in ("%TOOLS_DIR%\gradle-*") do (
        if exist "%%D\bin\gradle.bat" set "GRADLE_CMD=%%D\bin\gradle.bat"
    )
)
echo [OK] Gradle: !GRADLE_CMD!

:: --- Generate wrapper (only once) ---
if not exist "%PROJECT_DIR%gradle\wrapper\gradle-wrapper.jar" (
    echo [INFO] Generating Gradle wrapper...
    cd /d "%PROJECT_DIR%"
    call "!GRADLE_CMD!" wrapper
    if !errorlevel! NEQ 0 ( echo [ERROR] Wrapper generation failed & exit /b 1 )
    echo [OK] Wrapper generated.
)

:: --- Build ---
echo [INFO] Building mod (first run downloads MC deps, may take a while)...
cd /d "%PROJECT_DIR%"
call "%PROJECT_DIR%gradlew.bat" build
if %errorlevel% NEQ 0 ( echo [ERROR] Build failed & exit /b 1 )

echo [OK] Build successful!
exit /b 0
