@echo off
setlocal enabledelayedexpansion

echo ========================================================
echo    Railway Reservation System - GitHub Sync Tool
echo ========================================================
echo.

:: Check if git is initialized
if not exist ".git" (
    echo [ERROR] Git repository is not initialized yet.
    echo Please initialize git and set your remote origin first.
    pause
    exit /b 1
)

:: Check if remote origin is configured
git remote get-url origin >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] No remote 'origin' configured.
    echo Run: git remote add origin ^<YOUR_GITHUB_REPO_URL^>
    pause
    exit /b 1
)

echo [1/3] Staging all modified and new files...
git add .

:: Check if there are changes to commit
git status --porcelain >nul 2>&1
for /f %%i in ('git status --porcelain') do set HAS_CHANGES=1

if not defined HAS_CHANGES (
    echo [INFO] No changes detected. Working tree is clean.
    echo Checking if there are unpushed commits...
    git push origin main
    goto end
)

set /p COMMIT_MSG="Enter commit message (Press Enter for 'Update railway reservation system'): "
if "%COMMIT_MSG%"=="" set COMMIT_MSG=Update railway reservation system

echo [2/3] Committing changes with message: "%COMMIT_MSG%"...
git commit -m "%COMMIT_MSG%"

echo [3/3] Pushing to GitHub (main branch)...
git push origin main

if %ERRORLEVEL% equ 0 (
    echo.
    echo [SUCCESS] Changes have been successfully pushed to GitHub!
) else (
    echo.
    echo [ERROR] Failed to push to GitHub. Please check your network or credentials.
)

:end
echo.
pause
