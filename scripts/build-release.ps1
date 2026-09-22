param([string]$Gradle = '.\gradlew.bat')
$ErrorActionPreference = 'Stop'

# This local signing identity is intentionally outside the repository.
$signingDir = Join-Path $env:LOCALAPPDATA 'WordTrace/signing'
$credentialsFile = Join-Path $signingDir 'credentials.json'
$keystore = Join-Path $signingDir 'wordtrace-release.jks'
New-Item -ItemType Directory -Force -Path $signingDir | Out-Null
if (!(Test-Path -LiteralPath $credentialsFile)) {
    if (Test-Path -LiteralPath $keystore) { throw 'Keystore exists without credentials. Restore your signing backup first.' }
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    @{ password = [Convert]::ToBase64String($bytes); alias = 'wordtrace' } | ConvertTo-Json | Set-Content -LiteralPath $credentialsFile -Encoding utf8
}
$credentials = Get-Content -LiteralPath $credentialsFile -Raw | ConvertFrom-Json
$env:WORDTRACE_KEYSTORE = $keystore
$env:WORDTRACE_STORE_PASSWORD = $credentials.password
$env:WORDTRACE_KEY_PASSWORD = $credentials.password
$env:WORDTRACE_KEY_ALIAS = $credentials.alias
try {
    if (!(Test-Path -LiteralPath $keystore)) {
        & (Join-Path $env:JAVA_HOME 'bin/keytool.exe') -genkeypair -keystore $keystore -storetype JKS -alias $credentials.alias -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=WordTrace, O=WordTrace' -storepass:env WORDTRACE_STORE_PASSWORD -keypass:env WORDTRACE_KEY_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Could not create signing key.' }
    }
    & $Gradle assembleRelease --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
    New-Item -ItemType Directory -Force -Path dist | Out-Null
    $buildConfigText = Get-Content -LiteralPath app/build.gradle -Raw
    $versionMatch = [regex]::Match($buildConfigText, "versionName '([0-9.]+)'")
    if (!$versionMatch.Success) { throw 'Could not read the app version.' }
    $apkPath = Join-Path dist ('wordtrace-' + $versionMatch.Groups[1].Value + '.apk')
    Copy-Item -LiteralPath app/build/outputs/apk/release/app-release.apk -Destination $apkPath
    Get-FileHash -LiteralPath $apkPath -Algorithm SHA256
    Write-Output "Back up signing key and credentials privately: $signingDir"
} finally {
    Remove-Item Env:WORDTRACE_STORE_PASSWORD,Env:WORDTRACE_KEY_PASSWORD,Env:WORDTRACE_KEYSTORE,Env:WORDTRACE_KEY_ALIAS -ErrorAction SilentlyContinue
}
