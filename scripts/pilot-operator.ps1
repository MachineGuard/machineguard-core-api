<#
.SYNOPSIS
Lista, aprueba o rechaza solicitudes de piloto de machineguard-core-api.

.DESCRIPTION
Lee la clave de operador solo de la variable de entorno MG_OPERATOR_KEY y nunca la imprime ni la acepta como argumento.
La aprobación devuelve el token de activación una sola vez; guárdelo de forma privada y entréguelo al administrador.

.EXAMPLE
$env:MG_OPERATOR_KEY = '<clave privada del operador>'
./scripts/pilot-operator.ps1 -Action list
./scripts/pilot-operator.ps1 -Action approve -Id 00000000-0000-0000-0000-000000000000
./scripts/pilot-operator.ps1 -Action reject -Id 00000000-0000-0000-0000-000000000000
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][ValidateSet('list', 'approve', 'reject')][string]$Action,
    [ValidatePattern('^[0-9a-fA-F-]{36}$')][string]$Id,
    [ValidateSet('PENDING', 'APPROVED', 'REJECTED')][string]$Status = 'PENDING',
    [string]$BaseUrl = 'http://127.0.0.1:8080'
)

$ErrorActionPreference = 'Stop'

$key = $env:MG_OPERATOR_KEY
if ([string]::IsNullOrWhiteSpace($key)) {
    throw 'Defina MG_OPERATOR_KEY en el entorno. El script no acepta la clave como argumento.'
}
if ($Action -ne 'list' -and [string]::IsNullOrWhiteSpace($Id)) {
    throw 'Indique -Id para approve o reject.'
}

$headers = @{ 'X-Operator-Key' = $key; 'Accept' = 'application/json' }
$base = $BaseUrl.TrimEnd('/') + '/api/v1/operator/pilot-requests'

try {
    switch ($Action) {
        'list'    { $result = Invoke-RestMethod -Method Get -Uri "${base}?status=$Status" -Headers $headers }
        'approve' { $result = Invoke-RestMethod -Method Post -Uri "$base/$Id/approve" -Headers $headers }
        'reject'  { $result = Invoke-RestMethod -Method Post -Uri "$base/$Id/reject" -Headers $headers }
    }
}
catch {
    $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 'sin respuesta' }
    Write-Error "La operación '$Action' no se completó (HTTP $code). Revise la clave, la URL y el estado de la solicitud."
    exit 1
}

if ($Action -eq 'approve' -and $result.invitationIssuedNow) {
    Write-Warning 'Guarde el invitationToken ahora; no se volverá a mostrar. Entréguelo solo al administrador de la solicitud.'
}

$result | ConvertTo-Json -Depth 5
