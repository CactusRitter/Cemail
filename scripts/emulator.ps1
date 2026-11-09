<#
  Arranca, para o o consulta el emulador de Android sin ventana (ni consola).

  Uso (desde la raíz del proyecto):
    powershell -File scripts\emulator.ps1 start    # arranca oculto y espera a que termine de arrancar
    powershell -File scripts\emulator.ps1 stop     # lo apaga
    powershell -File scripts\emulator.ps1 shot out.png   # captura de pantalla (funciona sin ventana)
    powershell -File scripts\emulator.ps1 window   # lo arranca CON ventana, por si quieres verlo

  Cambia $Avd si usas otro dispositivo virtual.
#>
param(
    [Parameter(Mandatory)][ValidateSet("start", "stop", "shot", "window", "status")][string]$Action,
    [string]$Path = "screen.png"
)

$Avd = "Pixel_API_36"
$Sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$Adb = "$Sdk\platform-tools\adb.exe"
$Emu = "$Sdk\emulator\emulator.exe"

function Test-Running { (& $Adb devices) -match "emulator-\d+\s+device" }

function Start-Emu([bool]$Headless) {
    if (Test-Running) { Write-Output "Ya está en marcha."; return }
    $emuArgs = @("-avd", $Avd, "-no-snapshot-save", "-no-audio", "-no-boot-anim")
    if ($Headless) { $emuArgs += @("-no-window", "-gpu", "swiftshader_indirect") }
    # Hidden: sin ventana de consola de emulator.exe.
    Start-Process $Emu -ArgumentList $emuArgs -WindowStyle Hidden
    & $Adb wait-for-device
    $t = 0
    while ((& $Adb shell getprop sys.boot_completed 2>$null) -ne "1" -and $t -lt 180) { Start-Sleep 2; $t++ }
    if ($t -ge 180) { Write-Error "El emulador no terminó de arrancar."; exit 1 }
    Write-Output "Emulador listo."
}

switch ($Action) {
    "start"  { Start-Emu $true }
    "window" { Start-Emu $false }
    "stop"   { if (Test-Running) { & $Adb emu kill | Out-Null; Write-Output "Apagado." } else { Write-Output "No estaba en marcha." } }
    "status" { if (Test-Running) { Write-Output "En marcha." } else { Write-Output "Apagado." } }
    "shot"   {
        $full = [System.IO.Path]::GetFullPath($Path)
        # cmd evita que PowerShell 5 corrompa los bytes del PNG al redirigir.
        cmd /c "`"$Adb`" exec-out screencap -p > `"$full`""
        Write-Output $full
    }
}
