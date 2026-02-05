# Add Spring Security to the application
# This script uncomments the spring-boot-starter-security dependency and enables SecurityConfig.java

Write-Host "Adding Spring Security..." -ForegroundColor Yellow

# Uncomment spring-boot-starter-security in pom.xml
$pomPath = "pom.xml"
if (Test-Path $pomPath) {
    $pomContent = Get-Content $pomPath -Raw
    $pomContent = $pomContent -replace '(\s*)<!-- Spring Security disabled[^>]*>(.*?)</dependency>\s*-->', '$1$2</dependency>'
    Set-Content $pomPath $pomContent -NoNewline
    Write-Host "✓ Uncommented spring-boot-starter-security in pom.xml" -ForegroundColor Green
} else {
    Write-Host "✗ pom.xml not found" -ForegroundColor Red
    exit 1
}

# Enable SecurityConfig.java
$securityConfigDisabledPath = "src\main\java\oracleai\common\SecurityConfig.java.disabled"
$securityConfigPath = "src\main\java\oracleai\common\SecurityConfig.java"

if (Test-Path $securityConfigDisabledPath) {
    Rename-Item -Path $securityConfigDisabledPath -NewName "SecurityConfig.java" -Force
    Write-Host "✓ Renamed SecurityConfig.java.disabled to SecurityConfig.java" -ForegroundColor Green
} elseif (Test-Path $securityConfigPath) {
    Write-Host "! SecurityConfig.java already exists (may already be enabled)" -ForegroundColor Yellow
} else {
    Write-Host "✗ SecurityConfig.java.disabled not found" -ForegroundColor Red
}

Write-Host "`nSpring Security has been added. Run .\build_and_run.ps1 to rebuild and restart." -ForegroundColor Cyan
