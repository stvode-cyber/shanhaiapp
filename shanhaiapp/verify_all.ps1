# verify_all.ps1 - One-shot full-chain verification
# Usage: powershell -ExecutionPolicy Bypass -File verify_all.ps1
# Covers: prototype (smoke_dom + test_game) + Gradle (jvmTest + assembleDebug + assembleRelease)
# 2026-10-17 · Works around Chinese-path JNI error via robocopy to C:\temp

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$TempBuild = "C:\temp\shanhaiapp_build"
$JDK = "C:\Program Files\Java\jdk-17"

function Write-Step($msg) { Write-Host "`n===== $msg =====" -ForegroundColor Cyan }
function Write-Ok($msg) { Write-Host "  [OK] $msg" -ForegroundColor Green }
function Write-Fail($msg) { Write-Host "  [FAIL] $msg" -ForegroundColor Red }
function Write-Skip($msg) { Write-Host "  [SKIP] $msg" -ForegroundColor DarkGray }

$allPass = $true

# --- 0. Pre-checks ---
Write-Step "0. Environment check"
if (-not (Test-Path $JDK)) { Write-Fail "JDK not found: $JDK"; exit 1 }
Write-Ok "JDK: $JDK"
if (-not (Get-Command robocopy -ErrorAction SilentlyContinue)) { Write-Fail "robocopy unavailable"; exit 1 }
Write-Ok "robocopy available"

# --- 1. Prototype: test_game.js ---
Write-Step "1. Prototype - test_game.js"
Push-Location (Join-Path $ScriptDir "prototype")
try {
    $testOut = (node test_game.js 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) {
        Write-Fail "test_game.js exit code $LASTEXITCODE"
        Write-Host $testOut
        $allPass = $false
    } else {
        if ($testOut -match "(\d+)\s*/\s*(\d+)") {
            $got = [int]$Matches[1]; $total = [int]$Matches[2]
            Write-Ok "test_game.js: $got/$total $(if($got -eq $total){'zero drift'}else{'DRIFT DETECTED'})"
            if ($got -ne $total) { $allPass = $false }
        } else {
            Write-Host $testOut
            Write-Ok "test_game.js exit 0"
        }
    }
} finally { Pop-Location }

# --- 2. Prototype: smoke_dom.js ---
Write-Step "2. Prototype - smoke_dom.js"
Push-Location (Join-Path $ScriptDir "prototype")
try {
    $smokeOut = (node smoke_dom.js 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) {
        Write-Fail "smoke_dom.js exit code $LASTEXITCODE"
        Write-Host $smokeOut
        $allPass = $false
    } else {
        if ($smokeOut -match "(\d+)\s*/\s*(\d+)") {
            $got = [int]$Matches[1]; $total = [int]$Matches[2]
            Write-Ok "smoke_dom.js: $got/$total $(if($got -eq $total){'zero exception'}else{'EXCEPTION DETECTED'})"
            if ($got -ne $total) { $allPass = $false }
        } else {
            Write-Host $smokeOut
            Write-Ok "smoke_dom.js exit 0"
        }
    }
} finally { Pop-Location }

# --- 3. Gradle via robocopy to C:\temp ---
Write-Step "3. robocopy to C:\temp\shanhaiapp_build (bypass Chinese path)"
robocopy $ScriptDir $TempBuild /E /XD "build" ".gradle" /PURGE /NFL /NDL /NJH /NJS /nc /ns /np | Out-Null
if ($LASTEXITCODE -gt 7) { Write-Fail "robocopy failed (exit $LASTEXITCODE)"; exit 1 }
Write-Ok "robocopy done"

$env:JAVA_HOME = $JDK
Push-Location $TempBuild
try {
    # 3a. jvmTest
    Write-Step "4. Gradle - :shared:jvmTest"
    $jvmOut = (& .\gradlew.bat :shared:jvmTest --no-daemon --console=plain 2>&1 | Out-String)
    $gradleExit = $LASTEXITCODE
    if ($gradleExit -ne 0 -or $jvmOut -notmatch "BUILD SUCCESSFUL") {
        Write-Fail "jvmTest FAILED (exit=$gradleExit)"
        $allPass = $false
    } else { Write-Ok "jvmTest BUILD SUCCESSFUL" }

    # 3b. assembleDebug
    Write-Step "5. Gradle - :composeApp:assembleDebug"
    $dbgOut = (& .\gradlew.bat :composeApp:assembleDebug --no-daemon --console=plain 2>&1 | Out-String)
    $gradleExit = $LASTEXITCODE
    if ($gradleExit -ne 0 -or $dbgOut -notmatch "BUILD SUCCESSFUL") {
        Write-Fail "assembleDebug FAILED (exit=$gradleExit)"
        $allPass = $false
    } else { Write-Ok "assembleDebug BUILD SUCCESSFUL" }

    # 3c. assembleRelease
    Write-Step "6. Gradle - :composeApp:assembleRelease"
    $relOut = (& .\gradlew.bat :composeApp:assembleRelease --no-daemon --console=plain 2>&1 | Out-String)
    $gradleExit = $LASTEXITCODE
    if ($gradleExit -ne 0 -or $relOut -notmatch "BUILD SUCCESSFUL") {
        Write-Fail "assembleRelease FAILED (exit=$gradleExit)"
        $allPass = $false
    } else { Write-Ok "assembleRelease BUILD SUCCESSFUL" }
} finally { Pop-Location }

# --- Summary ---
Write-Step "SUMMARY"
if ($allPass) {
    Write-Host "`n[ALL PASS] Full chain verified!" -ForegroundColor Green
    Write-Host "  prototype JS + DOM + jvmTest + assembleDebug + assembleRelease" -ForegroundColor Green
    exit 0
} else {
    Write-Host "`n[PARTIAL FAIL] See red markers above." -ForegroundColor Yellow
    exit 1
}
