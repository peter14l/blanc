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

# Trust certificate in current user Root store
$store = New-Object System.Security.Cryptography.X509Certificates.X509Store("Root", "CurrentUser")
$store.Open("ReadWrite")
$store.Add($cert)
$store.Close()

# Locate signtool
$signtool = (Get-ChildItem -Path "${env:ProgramFiles(x86)}\Windows Kits\10\bin\*\x64\signtool.exe" -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
if (-not $signtool) {
    $signtool = "signtool.exe"
}
Write-Host "==> Using signtool: $signtool"

# Sign all executables in target dir
$filesToSign = Get-ChildItem -Path $TargetDir -Recurse -Include "*.exe", "*.msi"
foreach ($file in $filesToSign) {
    Write-Host "==> Signing $($file.FullName)..."
    & $signtool sign /f $pfxPath /p "BlancTestPassword123!" /fd SHA256 /tr http://timestamp.digicert.com /td SHA256 /v $file.FullName
    & $signtool verify /pa /v $file.FullName
}

Write-Host "==> All Windows binaries successfully signed with test certificate!"
