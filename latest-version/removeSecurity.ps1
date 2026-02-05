# Remove Spring Security from the application
# This script comments out the spring-boot-starter-security dependency and disables SecurityConfig.java

Write-Host "Removing Spring Security..." -ForegroundColor Yellow

# Comment out spring-boot-starter-security in pom.xml
$pomPath = "pom.xml"
if (Test-Path $pomPath) {
    $pomContent = Get-Content $pomPath -Raw
    $pomContent = $pomContent -replace '(\s*)<dependency>\s*<groupId>org\.springframework\.boot</groupId>\s*<artifactId>spring-boot-starter-security</artifactId>\s*</dependency>', '$1<!-- Spring Security disabled - uncomment to enable$0$1-->'
    Set-Content $pomPath $pomContent -NoNewline
    Write-Host "✓ Commented out spring-boot-starter-security in pom.xml" -ForegroundColor Green
} else {
    Write-Host "✗ pom.xml not found" -ForegroundColor Red
    exit 1
}

# Disable SecurityConfig.java
$securityConfigPath = "src\main\java\oracleai\common\SecurityConfig.java"
if (Test-Path $securityConfigPath) {
    Rename-Item -Path $securityConfigPath -NewName "SecurityConfig.java.disabled" -Force
    Write-Host "✓ Renamed SecurityConfig.java to SecurityConfig.java.disabled" -ForegroundColor Green
} else {
    Write-Host "! SecurityConfig.java not found (may already be disabled)" -ForegroundColor Yellow
}

Write-Host "`nSpring Security has been removed. Run .\build_and_run.ps1 to rebuild and restart." -ForegroundColor Cyan
