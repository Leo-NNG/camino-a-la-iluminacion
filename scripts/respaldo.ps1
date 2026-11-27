<#
.SYNOPSIS
  Respaldo de Camino a la Iluminación: base de datos (mysqldump) y, opcionalmente, la carpeta de documentos.

.DESCRIPTION
  Usa las mismas variables de entorno que la aplicación: DB_HOST, DB_PORT, DB_NAME, DB_USER y DB_PASSWORD
  (y DOCS_DIR para los documentos). La contraseña nunca va en la línea de comandos: se pasa a mysqldump en un
  archivo temporal que solo puede leer tu usuario y que se borra al terminar.
  Deja los respaldos FUERA del repositorio (por defecto en tu carpeta de usuario) y borra los de más de -Dias días.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\respaldo.ps1
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\respaldo.ps1 -BaseDatos camino_iluminacion_dev -ConDocumentos
#>
[CmdletBinding()]
param(
    [string]$Destino = (Join-Path $HOME 'respaldos_camino_iluminacion'),
    [string]$BaseDatos = $(if ($env:DB_NAME) { $env:DB_NAME } else { 'camino_iluminacion' }),
    [int]$Dias = 30,
    [switch]$ConDocumentos,
    [string]$Mysqldump = ''
)

$ErrorActionPreference = 'Stop'

function Fallar([string]$mensaje) {
    Write-Host "ERROR: $mensaje" -ForegroundColor Red
    exit 1
}

if ($BaseDatos -notmatch '^[A-Za-z0-9_]+$') { Fallar "Nombre de base de datos no válido: $BaseDatos" }
$usuario = $env:DB_USER
$clave = $env:DB_PASSWORD
if (-not $usuario -or -not $clave) { Fallar 'Faltan las variables de entorno DB_USER y/o DB_PASSWORD (las mismas de la aplicación).' }
$servidor = if ($env:DB_HOST) { $env:DB_HOST } else { 'localhost' }
$puerto = if ($env:DB_PORT) { $env:DB_PORT } else { '3306' }

# Buscar mysqldump: parámetro, PATH o la instalación típica de MySQL 8 / MariaDB en Windows.
if (-not $Mysqldump) {
    $enPath = Get-Command mysqldump.exe, mariadb-dump.exe -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($enPath) {
        $Mysqldump = $enPath.Source
    } else {
        $candidatos = @(Get-ChildItem 'C:\Program Files\MySQL\MySQL Server *\bin\mysqldump.exe', 'C:\Program Files\MariaDB *\bin\mysqldump.exe' -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending)
        if ($candidatos.Count -gt 0) { $Mysqldump = $candidatos[0].FullName }
    }
}
if (-not $Mysqldump -or -not (Test-Path $Mysqldump)) { Fallar 'No se encontró mysqldump. Indícalo con -Mysqldump "C:\ruta\mysqldump.exe".' }

# El destino no debe quedar dentro del repositorio (los respaldos tienen datos personales).
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
New-Item -ItemType Directory -Force -Path $Destino | Out-Null
$destinoReal = (Resolve-Path $Destino).Path
if ($destinoReal.StartsWith($repo, [System.StringComparison]::OrdinalIgnoreCase)) { Fallar "Elige un destino fuera del repositorio ($repo)." }

$marca = Get-Date -Format 'yyyyMMdd_HHmmss'
$archivoSql = Join-Path $destinoReal "${BaseDatos}_$marca.sql"
$opciones = Join-Path $env:TEMP ("cai_respaldo_" + [guid]::NewGuid().ToString('N') + '.cnf')

try {
    # Archivo de opciones temporal solo para el usuario actual (sin herencia de permisos).
    $clave_cnf = $clave -replace '\\', '\\' -replace '"', '\"'
    Set-Content -Path $opciones -Encoding ASCII -Value "[client]`r`nuser=`"$usuario`"`r`npassword=`"$clave_cnf`"`r`nhost=$servidor`r`nport=$puerto"
    $acl = New-Object System.Security.AccessControl.FileSecurity
    $acl.SetAccessRuleProtection($true, $false)
    $yo = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    $acl.AddAccessRule((New-Object System.Security.AccessControl.FileSystemAccessRule($yo, 'FullControl', 'Allow')))
    Set-Acl -Path $opciones -AclObject $acl

    Write-Host "Respaldando la base '$BaseDatos' de ${servidor}:$puerto ..."
    & $Mysqldump "--defaults-extra-file=$opciones" --single-transaction --routines --triggers `
        --default-character-set=utf8mb4 --no-tablespaces "--result-file=$archivoSql" $BaseDatos
    if ($LASTEXITCODE -ne 0) { Fallar "mysqldump terminó con código $LASTEXITCODE (revisa usuario, contraseña y nombre de la base)." }
} finally {
    Remove-Item -Force -ErrorAction SilentlyContinue $opciones
}

# Comprobar que el respaldo está completo: mysqldump escribe '-- Dump completed' al final.
$ultimas = Get-Content -Path $archivoSql -Tail 3 -ErrorAction SilentlyContinue
if (-not ($ultimas -match 'Dump completed')) {
    Remove-Item -Force -ErrorAction SilentlyContinue $archivoSql
    Fallar 'El respaldo quedó incompleto y se descartó.'
}
$mb = [math]::Round((Get-Item $archivoSql).Length / 1MB, 2)
Write-Host "OK base de datos: $archivoSql ($mb MB)" -ForegroundColor Green

if ($ConDocumentos) {
    $docs = if ($env:DOCS_DIR) { $env:DOCS_DIR } else { Join-Path $HOME 'camino_iluminacion_docs' }
    if (Test-Path $docs) {
        $archivoZip = Join-Path $destinoReal "documentos_$marca.zip"
        $contenido = Get-ChildItem -Path $docs -Force
        if ($contenido) {
            Compress-Archive -Path (Join-Path $docs '*') -DestinationPath $archivoZip -CompressionLevel Optimal
            Write-Host "OK documentos: $archivoZip" -ForegroundColor Green
        } else {
            Write-Host "La carpeta de documentos está vacía: $docs"
        }
    } else {
        Write-Host "AVISO: no existe la carpeta de documentos $docs" -ForegroundColor Yellow
    }
}

# Rotación: borrar respaldos con más de $Dias días.
if ($Dias -gt 0) {
    $limite = (Get-Date).AddDays(-$Dias)
    Get-ChildItem -Path $destinoReal -File | Where-Object {
        ($_.Name -like "${BaseDatos}_*.sql" -or $_.Name -like 'documentos_*.zip') -and $_.LastWriteTime -lt $limite
    } | ForEach-Object {
        Remove-Item -Force $_.FullName
        Write-Host "Borrado por antigüedad: $($_.Name)"
    }
}
exit 0
