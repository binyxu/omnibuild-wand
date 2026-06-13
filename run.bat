@echo off
powershell -NoProfile -ExecutionPolicy Bypass -Command "& 'C:\Users\20129\BuildingWandMod\build.ps1'; if ($LASTEXITCODE -ne 0 -or $Error.Count -gt 0) { Write-Host '[FAILED]' -ForegroundColor Red } else { Write-Host '[DONE]' -ForegroundColor Green }"
pause
