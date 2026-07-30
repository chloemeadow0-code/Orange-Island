@echo off
set JAVA_HOME=C:\Users\27227\.jdks\ms-17.0.19
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d C:\Users\27227\Desktop\Orange-Island
gradlew.bat assembleDebug --no-daemon
