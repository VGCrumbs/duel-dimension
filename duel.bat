@echo off
setlocal
cd /d "%~dp0"

rem ---------------------------------------------------------------------------
rem  Launches the dev clients.
rem
rem    duel            Alpha only (the default)
rem    duel alpha      Alpha only
rem    duel omega      Omega only
rem    duel both       Alpha and Omega, in separate windows
rem    duel build      compile only, launch nothing
rem
rem  Compilation happens ONCE up front rather than being left to each launch.
rem  A Gradle build holds a lock on the project, and `runAlpha` does not finish
rem  until the game window closes, so two launches that both tried to compile
rem  would have the second sitting behind the first for the whole session.
rem  Building first means both launches find everything up to date.
rem
rem  Alpha and Omega are the SAME run config with different -P properties:
rem  ForgeGradle only gives client/server/data a main class, so a config named
rem  "alpha" cannot launch at all.
rem
rem  Omega additionally runs with its own --project-cache-dir. That lock is
rem  taken on the cache directory, so without a second one Omega would simply
rem  wait for Alpha to exit instead of starting alongside it.
rem ---------------------------------------------------------------------------

rem  Called by full path: this machine does not search the current directory
rem  for executables, so a bare gradlew17.cmd is not found even from here.
set "GRADLEW=%~dp0gradlew17.cmd"

set "MODE=%~1"
if "%MODE%"=="" set "MODE=alpha"

if /i "%MODE%"=="alpha" goto :build
if /i "%MODE%"=="omega" goto :build
if /i "%MODE%"=="both"  goto :build
if /i "%MODE%"=="build" goto :build
echo.
echo   Unknown option "%MODE%".
echo   Usage: duel [alpha^|omega^|both^|build]
echo.
exit /b 1

:build
echo.
echo   Compiling...
call "%GRADLEW%" classes --console=plain
if errorlevel 1 (
    echo.
    echo   Compile failed - not launching.
    exit /b 1
)
echo   Up to date.
echo.

if /i "%MODE%"=="build" exit /b 0
if /i "%MODE%"=="alpha" goto :alpha
if /i "%MODE%"=="omega" goto :omega
goto :both

:alpha
echo   Launching Alpha at 1634x920...
call "%GRADLEW%" runClient -Pmcuser=Alpha -Pmcrun=run --console=plain
exit /b %errorlevel%

:omega
echo   Launching Omega at 1634x920...
call "%GRADLEW%" runClient -Pmcuser=Omega -Pmcrun=run-omega --project-cache-dir .gradle-omega --console=plain
exit /b %errorlevel%

:both
echo   Launching Alpha and Omega at 1634x920...
start "Duel Dimension - Alpha" cmd /c ""%GRADLEW%" runClient -Pmcuser=Alpha -Pmcrun=run --console=plain"
rem  Let Alpha take the project lock and get moving before Omega starts, so the
rem  two do not race over the run directory while it is being prepared.
timeout /t 5 /nobreak >nul
start "Duel Dimension - Omega" cmd /c ""%GRADLEW%" runClient -Pmcuser=Omega -Pmcrun=run-omega --project-cache-dir .gradle-omega --console=plain"
echo.
echo   Both launched. Each has its own window; close a window to stop that client.
echo   In game: /duel Omega   (from Alpha)
exit /b 0
