$ErrorActionPreference = 'Stop'

$projectRoot = $PSScriptRoot
$environmentFile = Join-Path $projectRoot '.env'
$backendDirectory = Join-Path $projectRoot 'backend'

if (Test-Path -LiteralPath $environmentFile) {
    foreach ($line in Get-Content -LiteralPath $environmentFile) {
        if ($line -match '^\s*#' -or [string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') {
            $name = $matches[1]
            $value = $matches[2].Trim()
            if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'"))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        }
    }
}

Push-Location $backendDirectory
try {
    & .\mvnw.cmd spring-boot:run
} finally {
    Pop-Location
}
