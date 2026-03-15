# MeBoy
Game Boy / Game Boy Color emulator for Java MIDP 2.0, plus a desktop builder (`MeBoyBuilder`) to generate a bundled MIDlet JAR.

## Project Structure
- `MeBoy/`: MIDlet emulator sources (`meboy.*` classes).
- `MeBoyBuilder/`: Swing desktop app to bundle ROMs into a new `MeBoy.jar` + `.jad`.

## Requirements
- JDK 8.
- Java ME Wireless Toolkit in `C:\WTK2.5.2_01`.
- NetBeans project metadata is included (`nbproject/`).

## Build Order (Important)
1. Build `MeBoy` first.
2. Build and run `MeBoyBuilder`.
3. In `MeBoyBuilder`, add ROMs and create output JAR/JAD.

`MeBoyBuilder` now searches required emulator resources in:
- `../MeBoy/build/compiled/`
- `../MeBoy/build/preverified/`
- `../MeBoy/resources/`
- `../MeBoy/dist/MeBoy.jar`

So the emulator project must exist and be built before bundling.

## Notes About Bundled ROMs
- Bundled ROM chunks are stored under `meboy/<cartID><index>`.
- `MIDlet-1` points to `meboy.MeBoy`.
- Split logic uses ceil division, so final partial ROM chunk is not lost.

## Builder Icon Resources
Default builder icons must be on the classpath:
- `MeBoyBuilder/src/meboy0.png`
- `MeBoyBuilder/src/meboy1.png`
- `MeBoyBuilder/src/meboy2.png`

## Run In KEmulator Lite
Use 32-bit Java to match KEmulator SWT DLLs:

```powershell
& 'C:\Program Files (x86)\Java\jre1.8.0_202\bin\java.exe' "-Djava.library.path=C:\Users\chiju\Downloads\java me\KEmulator_JavaEmulator.com" -jar 'C:\Users\chiju\Downloads\java me\KEmulator_JavaEmulator.com\KEmulator.jar'
```

If KEmulator reports `Unsupported major.minor version`, rebuild MIDlet classes with Java 5 target before creating `MeBoy.jar`.

## Changelog
See `CHANGELOG.md`.

## License
MeBoy is distributed under [GPLv2 license](https://github.com/chijure/meboy/blob/master/LICENSE).

LEGAL: This product is not affiliated with, nor authorized, endorsed or licensed in any way by Nintendo Corporation, its affiliates or subsidiaries.

## Credits
- Neil Millstone [millstone] - JavaBoy, original Java-based Game Boy emulator behind MeBoy.
- Bjorn Carlin [arktos] - Creator of the original MeBoy for J2ME.

[millstone]: https://web.archive.org/web/20200607201027/http://www.millstone.demon.co.uk/download/javaboy/
[arktos]: http://arktos.se/meboy
