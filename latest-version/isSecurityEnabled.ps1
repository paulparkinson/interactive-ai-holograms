# Check if Spring Security is currently enabled in the application

Write-Host "`nChecking Spring Security status..." -ForegroundColor Cyan
Write-Host ("=" * 60)

$securityEnabled = $true
$pomSecurityEnabled = $false
$configFileEnabled = $false

# Check pom.xml for uncommented spring-boot-starter-security
$pomPath = "pom.xml"
if (Test-Path $pomPath) {
    $pomContent = Get-Content $pomPath -Raw
    if ($pomContent -match '<dependency>\s*<groupId>org\.springframework\.boot</groupId>\s*<artifactId>spring-boot-starter-security</artifactId>\s*</dependency>') {
        $pomSecurityEnabled = $true
        Write-Host "[OK] pom.xml: spring-boot-starter-security is ENABLED" -ForegroundColor Green
    } else {
        Write-Host "[X] pom.xml: spring-boot-starter-security is DISABLED (commented out)" -ForegroundColor Red
    }
} else {
    Write-Host "[WARN] pom.xml not found" -ForegroundColor Yellow
}

# Check SecurityConfig.java status
$securityConfigPath = "src\main\java\oracleai\common\SecurityConfig.java"
$securityConfigDisabledPath = "src\main\java\oracleai\common\SecurityConfig.java.disabled"

if (Test-Path $securityConfigPath) {
    $configFileEnabled = $true
    Write-Host "[OK] SecurityConfig.java is ENABLED" -ForegroundColor Green
} elseif (Test-Path $securityConfigDisabledPath) {
    Write-Host "[X] SecurityConfig.java is DISABLED (.disabled extension)" -ForegroundColor Red
} else {
    Write-Host "[WARN] SecurityConfig.java not found in either state" -ForegroundColor Yellow
}

# Overall status
Write-Host "`n$("=" * 60)"
if ($pomSecurityEnabled -and $configFileEnabled) {
    Write-Host "RESULT: Spring Security is ENABLED" -ForegroundColor Green
    Write-Host "  - POST requests to /vectorrag/** will require authentication" -ForegroundColor White
    Write-Host "  - Run .\removeSecurity.ps1 to disable security" -ForegroundColor White
} elseif (-not $pomSecurityEnabled -and -not $configFileEnabled) {
    Write-Host "RESULT: Spring Security is DISABLED" -ForegroundColor Yellow
    Write-Host "  - POST requests to /vectorrag/** will work without authentication" -ForegroundColor White
    Write-Host "  - Run .\addSecurity.ps1 to enable security" -ForegroundColor White
} else {
    Write-Host "RESULT: Spring Security is in INCONSISTENT state" -ForegroundColor Red
    $pomStatus = if ($pomSecurityEnabled) { "ENABLED" } else { "DISABLED" }
    $configStatus = if ($configFileEnabled) { "ENABLED" } else { "DISABLED" }
    Write-Host "  - pom.xml: $pomStatus" -ForegroundColor White
    Write-Host "  - SecurityConfig.java: $configStatus" -ForegroundColor White
    Write-Host "  - Run .\removeSecurity.ps1 or .\addSecurity.ps1 to fix" -ForegroundColor White
}
Write-Host ("=" * 60) -ForegroundColor Cyan
