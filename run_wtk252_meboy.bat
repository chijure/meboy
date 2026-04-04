@echo off
setlocal

set "WTK=C:\WTK2.5.2_01"
set "JDK=C:\Program Files (x86)\Java\jdk1.8.0_202"
if not exist "%JDK%\bin\javac.exe" set "JDK=C:\Program Files\Java\jdk1.8.0_221"

set "TMP=build\wtk\tmpclasses"
set "CLS=build\wtk\classes"
set "DIST=build\wtk\dist"
set "APP_VERSION="

for /f "usebackq tokens=1,* delims==" %%A in ("MeBoy\resources\version.properties") do (
	if /I "%%A"=="app.version" set "APP_VERSION=%%B"
)

if not defined APP_VERSION (
	echo Could not read app version from MeBoy\resources\version.properties
	goto fail
)

if exist "%TMP%" rmdir /s /q "%TMP%"
if exist "%CLS%" rmdir /s /q "%CLS%"
if not exist "%DIST%" mkdir "%DIST%"
if not exist "%TMP%" mkdir "%TMP%"
if not exist "%CLS%" mkdir "%CLS%"

echo Compiling MIDlet sources...
"%JDK%\bin\javac.exe" -source 1.3 -target 1.3 -bootclasspath "%WTK%\lib\cldcapi11.jar;%WTK%\lib\midpapi20.jar" -d "%TMP%" -classpath "%WTK%\lib\mmapi.jar;%WTK%\lib\jsr082.jar;%WTK%\lib\jsr75.jar" MeBoy\src\meboy\AdvancedGraphicsChip.java MeBoy\src\meboy\Bluetooth.java MeBoy\src\meboy\Dmgcpu.java MeBoy\src\meboy\GBCanvas.java MeBoy\src\meboy\GraphicsChip.java MeBoy\src\meboy\MeBoy.java MeBoy\src\meboy\SimpleGraphicsChip.java MeBoy\src\meboy\app\AppInfo.java MeBoy\src\meboy\io\ExternalRomStore.java MeBoy\src\meboy\io\FileBrowserUtil.java MeBoy\src\meboy\io\SuspendedGameStore.java MeBoy\src\meboy\util\StringArrayUtil.java MeBoy\src\meboy\ui\FileBrowserController.java MeBoy\src\meboy\ui\ResumeGameController.java MeBoy\src\meboy\ui\SettingsController.java
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
echo MIDlet-Version: %APP_VERSION%
echo MIDlet-Description: Gameboy emulator for J2ME
echo MicroEdition-Configuration: CLDC-1.1
echo MicroEdition-Profile: MIDP-2.0
echo MIDlet-Permissions-Opt: javax.microedition.io.Connector.file.read, javax.microedition.io.Connector.file.write
) > "%DIST%\MANIFEST.MF"

echo Building JAR...
"%JDK%\bin\jar.exe" cmf "%DIST%\MANIFEST.MF" "%DIST%\MeBoy.jar" -C "%CLS%" .
if errorlevel 1 goto fail
"%JDK%\bin\jar.exe" uf "%DIST%\MeBoy.jar" -C MeBoy\resources lang -C MeBoyBuilder\src meboy0.png -C MeBoyBuilder\src meboy1.png -C MeBoyBuilder\src meboy2.png
if errorlevel 1 goto fail

for %%I in ("%DIST%\MeBoy.jar") do set "JARSIZE=%%~zI"

echo Writing JAD...
(
echo MIDlet-1: MeBoy, meboy.png, MeBoy
echo MIDlet-Name: MeBoy
echo MIDlet-Vendor: Bjorn Carlin, www.arktos.se
echo MIDlet-Version: %APP_VERSION%
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
