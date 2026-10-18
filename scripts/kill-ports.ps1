# ==============================================================================
# Doppsy Windows Ghost Process Killer (PowerShell)
# ==============================================================================
# Finds and terminates any background process holding ports 3000, 8080, 5432, 5672
# ==============================================================================

$targetPorts = @(3000, 8080, 5432, 5672, 15672)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Doppsy Ghost Process Killer (PowerShell)" -ForegroundColor Cyan
Write-Host " Scanning ports: $($targetPorts -join ', ')" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

foreach ($port in $targetPorts) {
    Write-Host "[*] Checking port $port..." -NoNewline
    $connections = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue

    if ($connections) {
        Write-Host " OCCUPIED" -ForegroundColor Red
        foreach ($conn in $connections) {
            $pId = $conn.OwningProcess
            try {
                $proc = Get-Process -Id $pId -ErrorAction Stop
                Write-Host "    Found process: $($proc.ProcessName) (PID: $pId). Killing..." -ForegroundColor Yellow
                Stop-Process -Id $pId -Force
                Write-Host "    [+] Successfully killed PID $pId" -ForegroundColor Green
            } catch {
                Write-Host "    [-] Unable to kill PID $pId. $($_.Exception.Message)" -ForegroundColor Red
            }
        }
    } else {
        Write-Host " FREE" -ForegroundColor Green
    }
}

Write-Host "`nAll target ports verified. You now have a clean slate to boot the stack!" -ForegroundColor Green
