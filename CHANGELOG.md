# Changelog

## version 2.4.2: 4 Apr 2026

- Added `.sav` battery-save export/import compatibility with VBA-M.
- Centralized the application version in `MeBoy/resources/version.properties` so MeBoy, MeBoyBuilder, Ant metadata and helper scripts read it from one place.
- Updated version display and manifest generation to follow the shared version property instead of duplicated hardcoded values.
- Added a GitHub Actions release workflow that validates tags against `app.version` and publishes release notes from `CHANGELOG.md`.
- Documented the GitHub release process in `README.md`.

## version 2.4: 22 Mar 2026

- Fixed command handling to avoid unreliable string identity checks in menus and in-game actions.
- Reworked CPU thread waiting during pause/save/release to avoid busy-wait loops.
- Extracted suspended-game persistence into dedicated helpers for save/load/copy/delete flows.
- Hardened settings and suspended-game loading against corrupt or incomplete RMS data.
- Replaced several magic numbers in `GBCanvas` with named constants for settings, input, and FPS handling.
- Improved SD/JSR-75 ROM browser UX with sorted entries, reusable wait forms, and clearer file-loading messages.
- Fixed visible text encoding in credits/about information and added credit for Juan Reategui Solis (chijure).
- Added small utility helpers to simplify repeated `String[]` append/remove operations.

## version 2.3: 23 Feb 2026

- Added `Load ROM from SD` support using JSR-75 file browsing (`.gb/.gbc/.cgb`).
- Added runtime loading of external ROM data (no bundled carts required in the JAR).
- Allowed creating `MeBoy.jar` from MeBoyBuilder even when no games are bundled.
- Added optional file read/write permissions for JSR-75 in generated manifest and JAD.
- Improved ROM validation handling in MeBoyBuilder (ZIP entry selection, copier-header tolerant checks, clearer error messages).
- Updated visible app versions to MeBoy 2.3 and MeBoyBuilder 2.3.

## version 2.2: 1 Feb 2009

- Improved speed of writes to graphics memory
- Improved screen redraw syncing, especially for Advanced Graphics mode
- Improved speed of doubled sprites (8x16 pixel) by drawing one large image instead of two small
- Improved speed of decoding images in Advanced Graphics mode
- Tweaked the sprite drawing loop to be slightly faster and better reflect the order sprites should be drawn, especially in Gameboy Color games
- Simple Graphics mode now crops images that have transparent borders
- Simple Graphics detects solid images, and creates Images without alpha channel
- Tweaked decoding of images when scaling the screen to 75%
- Fixed flicker when the LCD screen was turned off
- Fixed bug when decoding Gameboy Color palettes (Pokemon trading card game starts now)
- Fixed HDMA bug when interrupts are disabled (Donkey Kong Country starts now)
- Fixed speed-switch bug where read-only bits could be written to (Conker's Pocket Tales starts now)
- Tweaked handling of cart-RAM (hopefully improving compatibility, but it's hard to tell)
- Emulation is now paused when setting keys

## version 2.1: 6 Dec 2008

- Fixed bug where canceling a Bluetooth transmission would not completely stop the operation, causing errors when retrying
- Fixed graphics bug where sprites in some games (R-Type, Kirby's Dream Land 2...) were missing in regular graphics mode
- Fixed bug that caused the real-time clock to not update
- Fixed sound bug where the volumes of notes were not updated correctly
- Fixed hiding of Bluetooth menu item for phones lacking JSR 82 support
- Added new MeBoy icon, in several sizes
- MeBoyBuilder: Added option for user to choose MeBoy icon size, or a custom icon

## version 2.0: 23 Nov 2008

- Added sound support
- Added support for sending cart-RAM and suspended games via Bluetooth
- Added "Advanced graphics" mode with slow but (almost) pixel-perfect emulation
- Added icon
- Added "Pause" overlay when paused
- Improved drawing compatibility ("locked" graphics should be more rare now)
- Improved interrupt timing emulation
- Improved instruction timing emulation
- Improved error messages
- Dropped backwards compatibility for 1.1/1.2-era savegames
- Reordered menu items to put most frequently used items first
- Completely redesigned MeBoyBuilder UI:
- Added support for renaming/removing savegames via Bluetooth
- Added support for sending/receiving savegames via Bluettoth
- Added "select location" dialog for the MeBoy.jar file
- Added support for renaming the .jar file (and thus the application)
- Improved error messages

## version 1.6: 3 Mar 2008

- Fixed "Scale to fit" bug for very large screens
- Fixed bug causing some games to crash after hours of gameplay (Pokemon games should run butter now)
- Fixed HDMA transfer status (Lemmings works now)
- Spanish translation by Pendor (www.pendor.com.ar)

## version 1.5: 10 Sept 2007

- Added "Scale to fit" for phones with large screens
- Added preference for starting games in full screen mode
- Added preference for disabling Gameboy Color support
- Optimized drawing code, some games (like the early Pokemon games) run faster
- Fixed bug where GBC games would show incorrect colors after resuming a suspended game
- Fixed several minor timing bugs
- Improed memory deallocation code, some phones should no longer get "out-of-memory" messages (Fix by Alberto Simon)

## version 1.4: 22 Jun 2007

- Added "Shrink to fit" for phones with small screens
- Fixed potential stack handling bug for GBC games
- Fixed several register initialization bugs
- Fixed several GBC palette reading, writing and initialization bugs
- Improved performance for GB games by using separate GB and GBC classes
- Improved performance when an entire ROM does not fit in memory
- Slightly improved performance when initializing images (still readlly slow though)

## version 1.3.1: 29 May 2007

- Fixed cartridge mapping bug, improves game compatibility (e.g. Catwoman)
- Fixed sound status register bug, improves game compatibility (e.g. Legend of Zelda - Oracle of Ages)
- MeBoyBuilder: added option for transferring saved games from version 1.1/1.2

## version 1.3: 24 May 2007

- Added support for Gameboy Color games
- Added support for multiple saved (suspended) games at once
- Added support for partial loading of very large ROM files (for phones with limited RAM)
- Added support for realtime clock (used by Pokemon Gold etc)
- In addition to frameskip, number of frames per second is displayed
- Fixed minor keyboard handling bugs, Japanese Pokemon games should work better now
- Fixed overflow bug that could occur after 2^32 instructions
- Fixed minor windowing bug, Dragonball Z 2 should work better now
- ROM files are split into smaller files, which reduces RAM requirement
- MeBoyBuilder: Now supports zipped ROM files (one ROM per zip file)
- MeBoyBuilder: Now tries to verify that the selected files are valid Gameboy carts
- MeBoyBuilder: Can now (optionally) automatically check for updates

## version 1.2: 4 Mar 2007

- Added support for rotating the screen (with a slight performance penalty)
- Added speed throttling in the unlikely event that MeBoy exceeds 60 fps
- Added support for rarely used "LCD control operation" for turning off the LCD
- Fixed flag handling bug for increment/decrement instructions, other minor flag handling bugs
- Fixed timing of screen update interrupts, to better match JavaBoy and hopefully Gameboy. Grabbing items while spinning in Metroid 2 no longer causes freeze
- Fixed minor keyboard handling bug
- Fixed palette bug (some sprites had incorrect colors, such as Bugs Bunny and some enemies in Super Mario Land 2)
- Fixed version bug (1.1 identifies itself as 1.0)
- MeBoyBuilder: Now has a simple UI with some instructions and information
- MeBoyBuilder: Non-ascii characters in filenames now handled
- MeBoyBuilder: Warning when selecting large ROM files and Gameboy Color games
- MeBoyBuilder: MeBoy.jad file is produced, since certain phones seem to require it

## version 1.1: 2 Nov 2005

- fixed spriteenabled (Balloon Kid sort of works now)
- fixed battery RAM
- fixed "suspend" and "resume"
- added full screen mode
- added "unload cart"
- added the source for MeBoyBuilder
- added settings for frameskip
- some small bug fixes and performance improvements

## version 1.0: 22 Jul 2005

- initial release
