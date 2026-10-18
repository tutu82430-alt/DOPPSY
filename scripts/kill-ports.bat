@echo off
setlocal enabledelayedexpansion

echo ==============================================================================
echo  Doppsy Windows Ghost Process Killer
echo  Scanning and freeing ports: 3000, 8080, 5432, 5672, 15672
echo ==============================================================================

set PORTS=3000 8080 5432 5672 15672

for %%P in (%PORTS%) do (
    echo [*] Checking port %%P...
    set "FOUND="
    for /f "tokens=5" %%a in ('netstat -a -n -o ^| findstr /R /C:":%%P .*LISTENING"') do (
        set PID=%%a
        if defined PID (
            if not "!PID!"=="0" (
                echo [!] Found stuck process holding port %%P with PID !PID!
                taskkill /F /PID !PID! >nul 2>&1
                if !errorlevel! equ 0 (
                    echo [+] Successfully terminated PID !PID! on port %%P
                ) else (
                    echo [-] Could not kill PID !PID! (may require Administrator permissions)
                )
                set FOUND=1
            )
        )
    )
    if not defined FOUND (
        echo [OK] Port %%P is free.
    )
)

echo.
echo ==============================================================================
echo  All target ports checked. Clean slate ready!
echo ==============================================================================
pause
