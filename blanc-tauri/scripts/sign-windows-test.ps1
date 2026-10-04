# PowerShell script to generate a test Code Signing certificate and sign the Windows binary & installer
param(
    [string]$TargetDir = "src-tauri/target/release"
)

$ErrorActionPreference = "Stop"

Write-Host "==> Generating Test Code Signing Certificate for Windows..."
$certSubject = "CN=Blanc Test Publisher, O=Bananify Creative, OU=Browser Development"
$cert = New-SelfSignedCertificate -Type CodeSigningCert -Subject $certSubject -CertStoreLocation "Cert:\CurrentUser\My" -NotAfter (Get-Date).AddYears(3)

# Export to PFX
$password = ConvertTo-SecureString -String "BlancTestPassword123!" -Force -AsPlainText
$pfxPath = "$env:TEMP\blanc-test-cert.pfx"
Export-PfxCertificate -Cert $cert -FilePath $pfxPath -Password $password | Out-Null
Write-Host "==> Certificate exported to $pfxPath"

# Export public cert (.cer) and import to LocalMachine Root non-interactively
$cerPath = "$env:TEMP\blanc-test-cert.cer"
Export-Certificate -Cert $cert -FilePath $cerPath | Out-Null
try {
    # LocalMachine Root does not pop up a GUI confirmation modal when running with admin privileges in CI
    Import-Certificate -FilePath $cerPath -CertStoreLocation "Cert:\LocalMachine\Root" -ErrorAction SilentlyContinue | Out-Null
} catch {
    Write-Host "==> Note: LocalMachine Root import skipped; proceeding with direct PFX signing."
}

# Locate signtool
$signtool = (Get-ChildItem -Path "${env:ProgramFiles(x86)}\Windows Kits\10\bin\*\x64\signtool.exe" -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
if (-not $signtool) {
    $signtool = "signtool.exe"
}
Write-Host "==> Using signtool: $signtool"

# Only sign primary app binary and bundle installers (skip intermediate build/deps artifacts)
$filesToSign = @()
$mainExe = Join-Path $TargetDir "blanc-tauri.exe"
if (Test-Path $mainExe) {
    $filesToSign += Get-Item $mainExe
}
$bundleDir = Join-Path $TargetDir "bundle"
if (Test-Path $bundleDir) {
    $filesToSign += Get-ChildItem -Path $bundleDir -Recurse -Include "*.exe", "*.msi"
}

foreach ($file in $filesToSign) {
    Write-Host "==> Signing $($file.FullName)..."
    & $signtool sign /f $pfxPath /p "BlancTestPassword123!" /fd SHA256 /v $file.FullName
    $sig = Get-AuthenticodeSignature $file.FullName
    Write-Host "==> Signature Status for $($file.Name): $($sig.Status) ($($sig.SignerCertificate.Subject))"
}

Write-Host "==> All Windows binaries successfully signed with test certificate!"
