@echo off
setlocal enabledelayedexpansion

set CHECK_MIXIN=0
for %%A in (%*) do (
    if /i "%%A"=="--checkmixin" set CHECK_MIXIN=1
)

if "%CHECK_MIXIN%"=="1" (
    echo [HYPCRO] Running lightweight Mixin bytecode audit...
    python validate_mixins.py
    if !errorlevel! neq 0 (
        echo [ERROR] Mixin audit failed! Check build\mixin_check.log for details.
        exit /b !errorlevel!
    )
    echo [HYPCRO] Mixin audit passed cleanly!
    echo.
)

echo [HYPCRO] Compiling Java and Kotlin...
call gradlew.bat compileJava compileKotlin
if %errorlevel% neq 0 (
    echo [ERROR] Build failed!
    exit /b %errorlevel%
)

echo.
echo [HYPCRO] All checks passed successfully!
