@echo off
setlocal
set "MAVEN_PROJECTBASEDIR=%~dp0"
set "WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar"
set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
if not exist "%JAVA_CMD%" set "JAVA_CMD=java"
"%JAVA_CMD%" -jar "%WRAPPER_JAR%" %*
endlocal
