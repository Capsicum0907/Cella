@echo off
rem Compile the mod and launch a dev Minecraft client with it loaded.
rem Safe to double-click: it runs from its own directory and pauses on failure.
rem Extra arguments are passed straight to Gradle, e.g.  run.bat --info
rem Versions and the mod id are not set here - they live in gradle.properties.

setlocal
cd /d "%~dp0"

rem Java 18 and later emit UTF-8, while a Japanese console reads CP932 by
rem default, which turns every non-ASCII log line into mojibake. Line the
rem reader up with the writer. Scoped to this window only.
chcp 65001 >nul

if defined JAVA_HOME goto :run
where java >nul 2>&1
if errorlevel 1 goto :nojava

:run
call gradlew.bat runClient %*
set EXITCODE=%ERRORLEVEL%
if not "%EXITCODE%"=="0" goto :failed
exit /b 0

:nojava
echo.
echo No JDK found.
echo Set JAVA_HOME to a JDK 21 installation, or reopen the terminal if you have
echo just installed one - PATH is not refreshed in already-running shells.
echo.
pause
exit /b 1

:failed
echo.
echo Failed with exit code %EXITCODE%.
echo.
pause
exit /b %EXITCODE%
