@echo off
rem This fork targets Minecraft 26.2, which requires Java 25; running the
rem Gradle daemon on the same JDK keeps toolchains out of the picture.
rem Override with JAVA25_HOME if the path differs on your machine.
if "%JAVA25_HOME%"=="" set "JAVA25_HOME=C:\Program Files\Java\jdk-25.0.2"
set "JAVA_HOME=%JAVA25_HOME%"
call "%~dp0gradlew.bat" %*
