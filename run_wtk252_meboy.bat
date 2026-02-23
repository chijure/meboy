@echo off
setlocal

set "WTK=C:\WTK2.5.2_01"
set "JDK=C:\Program Files (x86)\Java\jdk1.8.0_202"
if not exist "%JDK%\bin\javac.exe" set "JDK=C:\Program Files\Java\jdk1.8.0_221"

set "TMP=build\wtk\tmpclasses"
set "CLS=build\wtk\classes"
set "DIST=build\wtk\dist"

if exist "%TMP%" rmdir /s /q "%TMP%"
if exist "%CLS%" rmdir /s /q "%CLS%"
if not exist "%DIST%" mkdir "%DIST%"
if not exist "%TMP%" mkdir "%TMP%"
if not exist "%CLS%" mkdir "%CLS%"

echo Compiling MIDlet sources...
"%JDK%\bin\javac.exe" -source 1.3 -target 1.3 -bootclasspath "%WTK%\lib\cldcapi11.jar;%WTK%\lib\midpapi20.jar" -d "%TMP%" -classpath "%WTK%\lib\mmapi.jar;%WTK%\lib\jsr082.jar;%WTK%\lib\jsr75.jar" AdvancedGraphicsChip.java Bluetooth.java Dmgcpu.java GBCanvas.java GraphicsChip.java MeBoy.java SimpleGraphicsChip.java
if errorlevel 1 goto fail

echo Preverifying classes...
"%WTK%\bin\preverify.exe" -classpath "%WTK%\lib\cldcapi11.jar;%WTK%\lib\midpapi20.jar;%WTK%\lib\mmapi.jar;%WTK%\lib\jsr082.jar;%WTK%\lib\jsr75.jar;%TMP%" -d "%CLS%" "%TMP%"
if errorlevel 1 goto fail

echo Writing manifest...
(
echo Manifest-Version: 1.0
echo MIDlet-1: MeBoy, meboy.png, MeBoy
echo MIDlet-Name: MeBoy
echo MIDlet-Vendor: Bjorn Carlin, www.arktos.se
echo MIDlet-Version: 2.3.0
echo MIDlet-Description: Gameboy emulator for J2ME
echo MicroEdition-Configuration: CLDC-1.1
echo MicroEdition-Profile: MIDP-2.0
echo MIDlet-Permissions-Opt: javax.microedition.io.Connector.file.read, javax.microedition.io.Connector.file.write
) > "%DIST%\MANIFEST.MF"

echo Building JAR...
"%JDK%\bin\jar.exe" cmf "%DIST%\MANIFEST.MF" "%DIST%\MeBoy.jar" -C "%CLS%" .
if errorlevel 1 goto fail
"%JDK%\bin\jar.exe" uf "%DIST%\MeBoy.jar" lang meboy0.png meboy1.png meboy2.png
if errorlevel 1 goto fail

for %%I in ("%DIST%\MeBoy.jar") do set "JARSIZE=%%~zI"

echo Writing JAD...
(
echo MIDlet-1: MeBoy, meboy.png, MeBoy
echo MIDlet-Name: MeBoy
echo MIDlet-Vendor: Bjorn Carlin, www.arktos.se
echo MIDlet-Version: 2.3.0
echo MIDlet-Description: Gameboy emulator for J2ME
echo MicroEdition-Configuration: CLDC-1.1
echo MicroEdition-Profile: MIDP-2.0
echo MIDlet-Permissions-Opt: javax.microedition.io.Connector.file.read, javax.microedition.io.Connector.file.write
echo MIDlet-Jar-URL: MeBoy.jar
echo MIDlet-Jar-Size: %JARSIZE%
) > "%DIST%\MeBoy.jad"

echo Running emulator...
"%WTK%\bin\emulator.exe" -Xheapsize:16M -Xdescriptor:%CD%\%DIST%\MeBoy.jad
goto end

:fail
echo Failed.
exit /b 1

:end
endlocal
