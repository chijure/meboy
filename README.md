# meboy
Gameboy / Gameboy Color emulator for Java MIDP 2.0.

## Run (MeBoyBuilder)

Requirements:
- JDK 8 (`javac` and `java`)
- Java ME Wireless Toolkit in `C:\WTK2.5.2_01`

Compile:
```powershell
& 'C:\Program Files\Java\jdk1.8.0_221\bin\javac.exe' -cp '.;C:\WTK2.5.2_01\lib\cldcapi11.jar;C:\WTK2.5.2_01\lib\midpapi20.jar;C:\WTK2.5.2_01\lib\mmapi.jar;C:\WTK2.5.2_01\lib\jsr082.jar' *.java se\arktos\meboy\*.java
```

Compile 2:
```powershell
# 1) Clases MIDlet (para KEmulator / MeBoy.jar)
& 'C:\Program Files\Java\jdk1.8.0_221\bin\javac.exe' -source 1.6 -target 1.6 -bootclasspath "C:\WTK2.5.2_01\lib\cldcapi11.jar;C:\WTK2.5.2_01\lib\midpapi20.jar" -cp ".;C:\WTK2.5.2_01\lib\jsr082.jar;C:\WTK2.5.2_01\lib\mmapi.jar" *.java
# 2) Builder desktop (Swing), sin bootclasspath CLDC/MIDP
& 'C:\Program Files\Java\jdk1.8.0_221\bin\javac.exe' -cp ".;C:\WTK2.5.2_01\lib\cldcapi11.jar;C:\WTK2.5.2_01\lib\midpapi20.jar;C:\WTK2.5.2_01\lib\mmapi.jar;C:\WTK2.5.2_01\lib\jsr082.jar" se\arktos\meboy\*.java

```

Run:
```powershell
& 'C:\Program Files\Java\jdk1.8.0_221\bin\java.exe' -cp '.;C:\WTK2.5.2_01\lib\cldcapi11.jar;C:\WTK2.5.2_01\lib\midpapi20.jar;C:\WTK2.5.2_01\lib\mmapi.jar;C:\WTK2.5.2_01\lib\jsr082.jar' se.arktos.meboy.MeBoyBuilder
```

## Changelog
See `CHANGELOG.md`.

## Run In KEmulator Lite
Use 32-bit Java to match KEmulator native SWT DLLs:

```powershell
& 'C:\Program Files (x86)\Java\jre1.8.0_202\bin\java.exe' "-Djava.library.path=C:\Users\chiju\Downloads\java me\KEmulator_JavaEmulator.com" -jar 'C:\Users\chiju\Downloads\java me\KEmulator_JavaEmulator.com\KEmulator.jar'
```

If KEmulator reports `Unsupported major.minor version`, rebuild the MIDlet classes with Java 5 target before creating `MeBoy.jar`:

```powershell
& 'C:\Program Files\Java\jdk1.8.0_221\bin\javac.exe' -source 1.5 -target 1.5 -cp ".;C:\WTK2.5.2_01\lib\cldcapi11.jar;C:\WTK2.5.2_01\lib\midpapi20.jar;C:\WTK2.5.2_01\lib\jsr082.jar;C:\WTK2.5.2_01\lib\mmapi.jar" *.java
```

## License
Meboy is distributed under [GPLv2 license](https://github.com/chijure/meboy/blob/master/LICENSE).

LEGAL: This product is not affiliated with, nor authorized, endorsed or licensed in any way by Nintendo Corporation, its affiliates or subsidiaries.

## Credits
- Neil Millstone [millstone] - JavaBoy, the original java based GameBoy emulator behind MeBoy
- Bjorn Carlin [arktos] - Creator of the original MeBoy for J2ME

[millstone]:
http://www.millstone.demon.co.uk/download/javaboy/

[arktos]:
http://arktos.se/meboy
