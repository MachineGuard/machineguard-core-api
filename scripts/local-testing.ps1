#Requires -Version 7.2
[CmdletBinding()]
param(
    [ValidateSet('Up', 'Down', 'Status', 'Credentials')][string]$Action = 'Status',
    [string]$WebDirectory = (Join-Path $PSScriptRoot '../../machineguard-web'),
    [string]$RuntimeDirectory = (Join-Path $env:LOCALAPPDATA 'MachineGuard/local-testing'),
    [string]$PostgresBin = '',
    [string]$MavenCommand = '',
    [ValidateRange(1024, 65535)][int]$ApiPort = 18090,
    [ValidateRange(1024, 65535)][int]$WebPort = 18420,
    [ValidateRange(1024, 65535)][int]$DatabasePort = 55441
)

$ErrorActionPreference = 'Stop'
if (-not $IsWindows) { throw 'This native testing launcher requires Windows and PowerShell 7.' }
$coreRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtime = [IO.Path]::GetFullPath($RuntimeDirectory)
$stateFile = Join-Path $runtime 'state.json'
$secretFile = Join-Path $runtime 'credentials.clixml'
$databaseName = 'machineguard_local_testing'
$databaseUser = 'mg_local_testing'

function Read-State {
    if (Test-Path -LiteralPath $stateFile) { return Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json -AsHashtable }
    return $null
}
function Save-State($State) { $State | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $stateFile -Encoding utf8 }
function Is-OwnedProcess($Record) {
    if (-not $Record) { return $false }
    $process = Get-Process -Id $Record.id -ErrorAction SilentlyContinue
    return $process -and $process.StartTime.ToUniversalTime().Ticks.ToString() -eq $Record.started -and $process.Path -eq $Record.path
}
function Record-Process($Process) {
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        $current = Get-Process -Id $Process.Id -ErrorAction Stop
        if ($current.Path) { return @{ id = $current.Id; started = $current.StartTime.ToUniversalTime().Ticks.ToString(); path = $current.Path } }
        Start-Sleep -Milliseconds 50
    }
    throw 'Could not record the newly started testing process.'
}
function Stop-OwnedProcess($Record) {
    if (Is-OwnedProcess $Record) { Stop-Process -Id $Record.id; Wait-Process -Id $Record.id -Timeout 15 -ErrorAction SilentlyContinue }
}
function Stop-Environment($State) {
    if (-not $State) { return }
    Stop-OwnedProcess $State.web
    Stop-OwnedProcess $State.api
    if (Is-OwnedProcess $State.postgres) {
        & (Join-Path $State.postgresBin 'pg_ctl.exe') stop -D $State.databaseDirectory -m fast -w
        if ($LASTEXITCODE -ne 0) { throw 'Could not stop the testing PostgreSQL cluster.' }
    }
    $State.web = $null; $State.api = $null; $State.postgres = $null
    Save-State $State
}
function New-TestSecret {
    return ConvertTo-SecureString ([Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))) -AsPlainText -Force
}
function Reveal($Secret) { return [Net.NetworkCredential]::new('', $Secret).Password }
function Start-Background([string]$File, [string[]]$Arguments, [string]$Directory, [string]$Name) {
    $quoted = $Arguments | ForEach-Object { '"' + $_ + '"' }
    return Start-Process -FilePath $File -ArgumentList $quoted -WorkingDirectory $Directory -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $runtime "$Name.stdout.log") -RedirectStandardError (Join-Path $runtime "$Name.stderr.log")
}
function Wait-Http([string]$Url, $Record, [int]$ExpectedStatus = 200) {
    $deadline = [DateTime]::UtcNow.AddSeconds(120)
    do {
        if (-not (Is-OwnedProcess $Record)) { throw "Testing process exited. Inspect logs in $runtime" }
        try { $response = Invoke-WebRequest $Url -TimeoutSec 3 -SkipHttpErrorCheck; if ($response.StatusCode -eq $ExpectedStatus) { return } } catch { }
        Start-Sleep -Seconds 1
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Testing service did not return HTTP $ExpectedStatus at $Url within 120 seconds. Inspect logs in $runtime"
}


function Wait-BootstrapLogin([string]$Url, $Record, $AdminSecret) {
    $deadline = [DateTime]::UtcNow.AddSeconds(120)
    do {
        if (-not (Is-OwnedProcess $Record)) { throw "Testing Core exited during bootstrap. Inspect logs in $runtime" }
        try {
            $result = Invoke-RestMethod $Url -Method Post -ContentType application/json -TimeoutSec 3 `
                -SkipHttpErrorCheck -StatusCodeVariable loginStatus `
                -Body (@{email='admin@testing.machineguard.invalid'; password=(Reveal $AdminSecret)} | ConvertTo-Json)
        } catch { throw "Testing bootstrap login request failed. Inspect logs in $runtime" }
        if ($loginStatus -eq 200) {
            if (-not $result.accessToken -or -not $result.organization.id -or $result.user.role -ne 'ADMIN') {
                throw 'Testing bootstrap login returned an unexpected identity.'
            }
            return $result
        }
        if ($loginStatus -notin @(401, 429)) { throw "Testing bootstrap login failed (HTTP $loginStatus). Inspect logs in $runtime" }
        # At most four attempts per minute, leaving room for the viewer login.
        $remaining = ($deadline - [DateTime]::UtcNow).TotalSeconds
        if ($remaining -gt 0) { Start-Sleep -Seconds ([Math]::Min(15, [Math]::Ceiling($remaining))) }
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Testing bootstrap authentication did not become ready within 120 seconds (last HTTP $loginStatus). Inspect logs in $runtime"
}

$state = Read-State
if ($Action -eq 'Status') {
    if (-not $state) { Write-Output 'Testing environment has not been initialized.'; return }
    [pscustomobject]@{ Web = "http://127.0.0.1:$($state.webPort)"; WebRunning = [bool](Is-OwnedProcess $state.web)
        CoreRunning = [bool](Is-OwnedProcess $state.api); DatabaseRunning = [bool](Is-OwnedProcess $state.postgres); Runtime = $runtime }
    return
}
if ($Action -eq 'Credentials') {
    if (-not (Test-Path -LiteralPath $secretFile)) { throw 'Run Up first.' }
    $secrets = Import-Clixml -LiteralPath $secretFile
    # Explicit action for the person testing. Do not copy this output to logs, Git or screenshots.
    [pscustomobject]@{ Role = 'ADMIN'; Email = 'admin@testing.machineguard.invalid'; Password = (Reveal $secrets.admin) }
    [pscustomobject]@{ Role = 'VIEWER'; Email = 'viewer@testing.machineguard.invalid'; Password = (Reveal $secrets.viewer) }
    return
}
if ($Action -eq 'Down') { Stop-Environment $state; Write-Output 'Testing services stopped. Database and credentials preserved.'; return }
if ($state -and ((Is-OwnedProcess $state.api) -or (Is-OwnedProcess $state.web) -or (Is-OwnedProcess $state.postgres))) {
    if ((Is-OwnedProcess $state.api) -and (Is-OwnedProcess $state.web) -and (Is-OwnedProcess $state.postgres)) {
        Write-Output "Already running: http://127.0.0.1:$($state.webPort)"; return
    }
    throw 'Some testing services are still running. Run Down before Up.'
}
if (@($ApiPort, $WebPort, $DatabasePort | Sort-Object -Unique).Count -ne 3) { throw 'Use three different ports.' }
foreach ($port in @($ApiPort, $WebPort, $DatabasePort)) {
    if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) { throw "Port $port is in use. No existing process was stopped." }
}
$webRoot = (Resolve-Path $WebDirectory).Path
if (-not (Test-Path (Join-Path $webRoot 'angular.json'))) { throw 'WebDirectory must point to machineguard-web.' }
$java = (Get-Command java.exe -ErrorAction Stop).Source
$node = (Get-Command node.exe -ErrorAction Stop).Source
$npm = (Get-Command npm.cmd -ErrorAction Stop).Source
if (-not $PostgresBin) {
    $pg = Get-ChildItem "$env:ProgramFiles/PostgreSQL/*/bin/pg_ctl.exe" -ErrorAction SilentlyContinue | Sort-Object FullName | Select-Object -First 1
    if (-not $pg) { throw 'Install PostgreSQL or pass -PostgresBin with its bin directory.' }
    $PostgresBin = $pg.DirectoryName
}
$PostgresBin = (Resolve-Path $PostgresBin).Path
foreach ($file in @('initdb.exe', 'pg_ctl.exe', 'psql.exe')) {
    if (-not (Test-Path (Join-Path $PostgresBin $file))) { throw "Missing PostgreSQL program: $file" }
}
if (-not $MavenCommand) {
    $mvn = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($mvn) { $MavenCommand = $mvn.Source }
    else {
        $cached = Get-ChildItem "$env:USERPROFILE/.m2/wrapper/dists/apache-maven-*/*/apache-maven-*/bin/mvn.cmd" -ErrorAction SilentlyContinue | Sort-Object FullName | Select-Object -First 1
        if (-not $cached) { throw 'Install Maven or pass -MavenCommand with the path to mvn.cmd.' }
        $MavenCommand = $cached.FullName
    }
}
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$databaseDirectory = Join-Path $runtime 'postgres'
if ($state -and ($state.coreRoot -ne $coreRoot -or $state.databaseDirectory -ne $databaseDirectory -or $state.postgresBin -ne $PostgresBin)) {
    throw 'Runtime belongs to a different checkout or PostgreSQL installation. Choose another -RuntimeDirectory.'
}
if (-not (Test-Path -LiteralPath $secretFile)) {
    if (Test-Path (Join-Path $databaseDirectory 'PG_VERSION')) { throw 'Database exists but its testing credentials are missing. No reset was performed.' }
    @{ admin = (New-TestSecret); viewer = (New-TestSecret); database = (New-TestSecret); jwt = (New-TestSecret) } | Export-Clixml -LiteralPath $secretFile
}
# Windows DPAPI encrypts these SecureStrings for this Windows account and machine.
$secrets = Import-Clixml -LiteralPath $secretFile
$state = @{ coreRoot = $coreRoot; webRoot = $webRoot; databaseDirectory = $databaseDirectory; postgresBin = $PostgresBin
    apiPort = $ApiPort; webPort = $WebPort; databasePort = $DatabasePort; api = $null; web = $null; postgres = $null }
Save-State $state
$savedEnvironment = @{}
function Set-ChildEnvironment([string]$Name, [string]$Value) {
    if (-not $savedEnvironment.ContainsKey($Name)) { $savedEnvironment[$Name] = [Environment]::GetEnvironmentVariable($Name, 'Process') }
    [Environment]::SetEnvironmentVariable($Name, $Value, 'Process')
}
try {
    Push-Location $coreRoot
    try { & $MavenCommand -B -DskipTests package *> (Join-Path $runtime 'build-core.log'); if ($LASTEXITCODE -ne 0) { throw 'Core build failed; see build-core.log.' } } finally { Pop-Location }
    Push-Location $webRoot
    try {
        $lockHash = (Get-FileHash (Join-Path $webRoot 'package-lock.json') -Algorithm SHA256).Hash
        $installFile = Join-Path $runtime 'web-install.json'
        $installed = if (Test-Path $installFile) { Get-Content $installFile -Raw | ConvertFrom-Json } else { $null }
        if (-not $installed -or $installed.directory -ne $webRoot -or $installed.hash -ne $lockHash -or -not (Test-Path 'node_modules/@angular/cli/bin/ng.js')) {
            & $npm ci --no-audit *> (Join-Path $runtime 'install-web.log')
            if ($LASTEXITCODE -ne 0) { throw 'Web dependency installation failed; see install-web.log.' }
            @{directory=$webRoot; hash=$lockHash} | ConvertTo-Json | Set-Content $installFile
        }
    } finally { Pop-Location }
    if (-not (Test-Path (Join-Path $databaseDirectory 'PG_VERSION'))) {
        $passwordFile = Join-Path $runtime ("initdb-" + [guid]::NewGuid().ToString('N') + '.tmp')
        New-Item -ItemType File -Path $passwordFile | Out-Null
        try {
            $acl = [Security.AccessControl.FileSecurity]::new()
            $acl.SetAccessRuleProtection($true, $false)
            $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new([Security.Principal.WindowsIdentity]::GetCurrent().User, 'FullControl', 'Allow'))
            Set-Acl -LiteralPath $passwordFile -AclObject $acl
            [IO.File]::WriteAllText($passwordFile, (Reveal $secrets.database), [Text.UTF8Encoding]::new($false))
            & (Join-Path $PostgresBin 'initdb.exe') -D $databaseDirectory -U $databaseUser --auth=scram-sha-256 --pwfile=$passwordFile --encoding=UTF8 --locale=C *> (Join-Path $runtime 'initdb.log')
            if ($LASTEXITCODE -ne 0) { throw 'Could not initialize the isolated testing cluster.' }
        } finally { Remove-Item -LiteralPath $passwordFile -Force }
    }
    # Wait for pg_ctl itself, not its persistent server descendants or inherited pipes.
    $control = Start-Background (Join-Path $PostgresBin 'pg_ctl.exe') @('start', '-D', $databaseDirectory,
        '-l', (Join-Path $runtime 'postgres.log'), '-o', "-h 127.0.0.1 -p $DatabasePort", '-w', '-t', '60') $runtime 'pgctl-start'
    if (-not $control.WaitForExit(70000) -or $control.ExitCode -ne 0) { throw 'Could not start the isolated testing cluster.' }
    $state.postgres = Record-Process (Get-Process -Id ([int](Get-Content (Join-Path $databaseDirectory 'postmaster.pid') -TotalCount 1)))
    Save-State $state
    Set-ChildEnvironment 'PGPASSWORD' (Reveal $secrets.database)
    $psql = Join-Path $PostgresBin 'psql.exe'
    $pgArgs = @('-h', '127.0.0.1', '-p', "$DatabasePort", '-U', $databaseUser, '-d', 'postgres', '-v', 'ON_ERROR_STOP=1')
    $exists = & $psql @pgArgs -tAc "SELECT 1 FROM pg_database WHERE datname='$databaseName'"
    if ($LASTEXITCODE -ne 0) { throw 'Could not connect to testing PostgreSQL.' }
    if ($exists -ne '1') { & $psql @pgArgs -c "CREATE DATABASE $databaseName" | Out-Null; if ($LASTEXITCODE -ne 0) { throw 'Could not create testing database.' } }
    Set-ChildEnvironment 'DB_URL' "jdbc:postgresql://127.0.0.1:$DatabasePort/$databaseName"
    Set-ChildEnvironment 'DB_USERNAME' $databaseUser
    Set-ChildEnvironment 'DB_PASSWORD' (Reveal $secrets.database)
    Set-ChildEnvironment 'IAM_JWT_SECRET' (Reveal $secrets.jwt)
    Set-ChildEnvironment 'IAM_BOOTSTRAP_ENABLED' 'true'
    Set-ChildEnvironment 'IAM_BOOTSTRAP_ORGANIZATION_NAME' 'MachineGuard Local Testing'
    Set-ChildEnvironment 'IAM_BOOTSTRAP_ADMIN_NAME' 'Testing administrator'
    Set-ChildEnvironment 'IAM_BOOTSTRAP_ADMIN_EMAIL' 'admin@testing.machineguard.invalid'
    Set-ChildEnvironment 'IAM_BOOTSTRAP_ADMIN_PASSWORD' (Reveal $secrets.admin)
    $jar = Join-Path $coreRoot 'target/core-api-0.1.0-SNAPSHOT.jar'
    $state.api = Record-Process (Start-Background $java @('-jar', $jar, "--server.port=$ApiPort", '--server.address=127.0.0.1') $coreRoot 'core')
    Save-State $state
    foreach ($entry in $savedEnvironment.GetEnumerator()) { [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process') }
    # A protected API response indicates that the HTTP/security stack is available;
    # the subsequent real login verifies bootstrap and authentication before readiness.
    Wait-Http "http://127.0.0.1:$ApiPort/api/v1/organizations" $state.api 401
    $login = Wait-BootstrapLogin "http://127.0.0.1:$ApiPort/api/v1/auth/login" $state.api $secrets.admin
    $headers = @{Authorization="Bearer $($login.accessToken)"}
    $usersUrl = "http://127.0.0.1:$ApiPort/api/v1/organizations/$($login.organization.id)/users"
    Invoke-RestMethod $usersUrl -Headers $headers -Method Post -ContentType application/json -SkipHttpErrorCheck -StatusCodeVariable viewerStatus -Body (@{email='viewer@testing.machineguard.invalid'; password=(Reveal $secrets.viewer); fullName='Testing viewer'; role='VIEWER'} | ConvertTo-Json) | Out-Null
    if ($viewerStatus -notin @(201, 409)) { throw "Could not provision testing viewer (HTTP $viewerStatus)." }
    $viewer = Invoke-RestMethod "http://127.0.0.1:$ApiPort/api/v1/auth/login" -Method Post -ContentType application/json -Body (@{email='viewer@testing.machineguard.invalid'; password=(Reveal $secrets.viewer)} | ConvertTo-Json)
    if ($viewer.user.role -ne 'VIEWER' -or $viewer.organization.id -ne $login.organization.id) { throw 'Testing viewer has unexpected access.' }
    @{ '/api/**' = @{ target="http://127.0.0.1:$ApiPort"; secure=$false; changeOrigin=$true } } | ConvertTo-Json | Set-Content (Join-Path $runtime 'proxy.json')
    $cli = Join-Path $webRoot 'node_modules/@angular/cli/bin/ng.js'
    $state.web = Record-Process (Start-Background $node @($cli, 'serve', '--host', '127.0.0.1', '--port', "$WebPort", '--proxy-config', (Join-Path $runtime 'proxy.json')) $webRoot 'web')
    Save-State $state
    Wait-Http "http://127.0.0.1:$WebPort/login" $state.web
    Write-Output "Testing ready: http://127.0.0.1:$WebPort"
    Write-Output "Runtime/logs: $runtime"
    Write-Output 'Run -Action Credentials to view synthetic login details. Run -Action Down to stop; data is preserved.'
} catch {
    $state | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $runtime 'last-failed-state.json')
    Stop-Environment $state
    throw
} finally {
    foreach ($entry in $savedEnvironment.GetEnumerator()) { [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process') }
}
