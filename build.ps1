$ErrorActionPreference = "Stop"
$ProjectDir = "C:\Users\20129\BuildingWandMod"
$ToolsDir   = "C:\Users\20129\.bwmod_tools"

function Log($msg) { Write-Host $msg }

Log "=== Building Wand Mod ==="

# ── Java 25 ──────────────────────────────────────────────────────────────────
$JavaHome = Get-ChildItem "$ToolsDir\jdk-25*" -Directory -ErrorAction SilentlyContinue |
            Where-Object { Test-Path "$($_.FullName)\bin\java.exe" } |
            Select-Object -Last 1 -ExpandProperty FullName

if (-not $JavaHome) {
    Log "[INFO] Downloading Java 25 (~200 MB)..."
    $zip = "$env:TEMP\jdk25.zip"
    Invoke-WebRequest "https://api.adoptium.net/v3/binary/latest/25/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk" -OutFile $zip
    New-Item -ItemType Directory -Force $ToolsDir | Out-Null
    Log "[INFO] Extracting Java 25..."
    Expand-Archive $zip $ToolsDir -Force
    Remove-Item $zip -ErrorAction SilentlyContinue

    $JavaHome = Get-ChildItem "$ToolsDir\jdk-25*" -Directory |
                Where-Object { Test-Path "$($_.FullName)\bin\java.exe" } |
                Select-Object -Last 1 -ExpandProperty FullName
}
if (-not $JavaHome) { throw "Java 25 setup failed" }
Log "[OK] Java: $JavaHome"
& "$JavaHome\bin\java.exe" -version

$env:JAVA_HOME = $JavaHome
$env:PATH      = "$JavaHome\bin;$env:PATH"

# ── Gradle ───────────────────────────────────────────────────────────────────
$GradleCmd = Get-ChildItem "$ToolsDir\gradle-*" -Directory -ErrorAction SilentlyContinue |
             Where-Object { Test-Path "$($_.FullName)\bin\gradle.bat" } |
             Select-Object -Last 1 |
             ForEach-Object { "$($_.FullName)\bin\gradle.bat" }

if (-not $GradleCmd) {
    Log "[INFO] Getting Gradle version..."
    $ver = (Invoke-RestMethod "https://services.gradle.org/versions/current").version
    Log "[INFO] Downloading Gradle $ver..."
    $zip = "$env:TEMP\gradle.zip"
    Invoke-WebRequest "https://services.gradle.org/distributions/gradle-$ver-bin.zip" -OutFile $zip
    Log "[INFO] Extracting Gradle..."
    Expand-Archive $zip $ToolsDir -Force
    Remove-Item $zip -ErrorAction SilentlyContinue

    $GradleCmd = Get-ChildItem "$ToolsDir\gradle-*" -Directory |
                 Where-Object { Test-Path "$($_.FullName)\bin\gradle.bat" } |
                 Select-Object -Last 1 |
                 ForEach-Object { "$($_.FullName)\bin\gradle.bat" }
}
if (-not $GradleCmd) { throw "Gradle setup failed" }
Log "[OK] Gradle: $GradleCmd"

# ── Gradle Wrapper ───────────────────────────────────────────────────────────
if (-not (Test-Path "$ProjectDir\gradle\wrapper\gradle-wrapper.jar")) {
    Log "[INFO] Generating Gradle wrapper..."
    Set-Location $ProjectDir
    & $GradleCmd wrapper
    if ($LASTEXITCODE -ne 0) { throw "Wrapper generation failed" }
}

# ── Build ────────────────────────────────────────────────────────────────────
Log "[INFO] Building mod (first run downloads MC deps, may take a while)..."
Set-Location $ProjectDir
& "$ProjectDir\gradlew.bat" build
if ($LASTEXITCODE -ne 0) { throw "Build failed" }

$Jar    = "$ProjectDir\build\libs\buildingwand-1.0.0+26.1.2.jar"
$ModsDir = "D:\Game\MC\HMCL-3.6.12\.minecraft\mods"

if (Test-Path $ModsDir) {
    Copy-Item $Jar $ModsDir -Force
    Log ""
    Log "[SUCCESS] JAR built and copied to mods folder:"
    Log "  $ModsDir\buildingwand-1.0.0+26.1.2.jar"
} else {
    Log ""
    Log "[SUCCESS] JAR ready (mods folder not found, copy manually):"
    Log "  $Jar"
}
