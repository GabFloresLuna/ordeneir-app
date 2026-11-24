# Carga ordenier/.env en la sesion actual de PowerShell (spec 008).
# Uso, desde ordenier/:   . .\scripts\load-env.ps1
# Solo define variables de proceso (esta sesion); no toca las permanentes de Windows.
# No usa "exit": se ejecuta con punto y cerraria la terminal.
# Solo ASCII: Windows PowerShell 5.1 lee los .ps1 sin BOM como ANSI.

function Import-OrdenierEnv {
    $envFile = Join-Path $PSScriptRoot '..\.env'

    if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
        Write-Error 'No existe ordenier/.env. Copia .env.example a .env, editalo y vuelve a cargar.'
        return
    }

    $pairs = [ordered]@{}
    $badLines = @()
    $lineNumber = 0

    foreach ($line in (Get-Content -LiteralPath $envFile -Encoding UTF8)) {
        $lineNumber++
        if ($line.Trim() -eq '' -or $line.TrimStart().StartsWith('#')) { continue }

        $eq = $line.IndexOf('=')
        $name = if ($eq -gt 0) { $line.Substring(0, $eq).Trim() } else { '' }
        if ($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
            $badLines += $lineNumber
            continue
        }
        $pairs[$name] = $line.Substring($eq + 1)
    }

    if ($badLines.Count -gt 0) {
        $list = $badLines -join ', '
        Write-Error "El archivo .env tiene lineas con formato invalido (linea $list). Usa NOMBRE=valor. No se definio ninguna variable."
        return
    }

    foreach ($name in $pairs.Keys) {
        [Environment]::SetEnvironmentVariable($name, $pairs[$name], 'Process')
    }

    Write-Host ('Variables cargadas en esta sesion: ' + ($pairs.Keys -join ', '))
}

Import-OrdenierEnv
Remove-Item Function:\Import-OrdenierEnv
