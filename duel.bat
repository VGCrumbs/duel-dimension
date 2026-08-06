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
rem    duel server     the dedicated server only
rem    duel mp         the dedicated server AND both clients
rem    duel build      compile only, launch nothing
rem
rem  `mp` is the real multiplayer test: an integrated server shares a JVM with
rem  its client, which hides exactly the faults multiplayer has -- client-only
rem  classes being reachable, and state that is secretly shared rather than
rem  sent. A dedicated server has neither luxury.
rem
rem  The server has its own run directory and its own Gradle cache directory for
rem  the same reason Omega does: the lock is taken per cache directory, so
rem  without a second one the server would simply wait for a client to exit.
rem  In game, connect to localhost.
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

if /i "%MODE%"=="alpha"  goto :build
if /i "%MODE%"=="omega"  goto :build
if /i "%MODE%"=="both"   goto :build
if /i "%MODE%"=="server" goto :build
if /i "%MODE%"=="mp"     goto :build
if /i "%MODE%"=="build"  goto :build
echo.
echo   Unknown option "%MODE%".
echo   Usage: duel [alpha^|omega^|both^|server^|mp^|build]
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

if /i "%MODE%"=="build"  exit /b 0
if /i "%MODE%"=="alpha"  goto :alpha
if /i "%MODE%"=="omega"  goto :omega
if /i "%MODE%"=="server" goto :server
if /i "%MODE%"=="mp"     goto :mp
goto :both

:server
echo   Launching the dedicated server...
call "%GRADLEW%" runServer --project-cache-dir .gradle-server --console=plain
exit /b %errorlevel%

:mp
echo   Launching the dedicated server and both clients...
start "Duel Dimension - Server" cmd /c ""%GRADLEW%" runServer --project-cache-dir .gradle-server --console=plain"
rem  The clients are no use until the server is accepting connections, and the
rem  server has a world to generate on a first run.
echo   Waiting for the server to come up...
timeout /t 45 /nobreak >nul
start "Duel Dimension - Alpha" cmd /c ""%GRADLEW%" runClient -Pmcuser=Alpha -Pmcrun=run --console=plain"
timeout /t 5 /nobreak >nul
start "Duel Dimension - Omega" cmd /c ""%GRADLEW%" runClient -Pmcuser=Omega -Pmcrun=run-omega --project-cache-dir .gradle-omega --console=plain"
echo.
echo   Three windows. In each client: Multiplayer - Direct Connection - localhost
echo   Then from Alpha: /duel Omega
exit /b 0

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
