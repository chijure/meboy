# meboy
Gameboy / GAmeboy Color emulator for Java MIDP 2.0

License
-
Meboy is distributed under [GPLv2 license](https://github.com/chijure/meboy/blob/master/LICENSE)
LEGAL: This product is not affiliated with, nor authorized, endorsed or licensed in any way by Nintendo Corporation, its affiliates or subsidiaries.

Credits
-
* Neil Millstone [millstone] - JavaBoy, the original java based GameBoy emulator behind MeBoy
* Björn Carlin [arktos] - Creator of the original MeBoy for J2ME

MeBoy Change Log
-

version 1.6: 3 Mar 2008
-

* Fixed "Scale to fit" bug for very large screens
* Fixed bug causing some games to crash after hours of gameplay (Pokemon games should run butter now)
* Fixed HDMA transfer status (Lemmings works now)
* Spanish translation by Pendor (www.pendor.com.ar)

version 1.5: 10 Sept 2007
-

* Added "Scale to fit" for phones with large screens
* Added preference for starting games in full screen mode
* Added preference for disabling Gameboy Color support
* Optimized drawing code, some games (like the early Pokemon games) run faster
* Fixed bug where GBC games would show incorrect colors after resuming a suspended game
* Fixed several minor timing bugs
* Improed memory deallocation code, some phones should no longer get "out-of-memory" messages (Fix by Alberto Simon)

version 1.4: 22 Jun 2007
-

* Added "Shrink to fit" for phones with small screens
* Fixed potential stack handling bug for GBC games
* Fixed several register initialization bugs
* Fixed several GBC palette reading, writing and initialization bugs
* Improved performance for GB games by using separate GB and GBC classes
* Improved performance when an entire ROM does not fit in memory
* Slightly improved performance when initializing images (still readlly slow though)

version 1.3.1: 29 May 2007
-

* Fixed cartridge mapping bug, improves game compatibility (e.g. Catwoman)
* Fixed sound status register bug, improves game compatibility (e.g. Legend of Zelda - Oracle of Ages)
* MeBoyBuilder: added option for transferring saved games from version 1.1/1.2

version 1.3: 24 May 2007
-

* Added support for Gameboy Color games
* Added support for multiple saved (suspended) games at once
* Added support for partial loading of very large ROM files (for phones with
  limited RAM)
* Added support for realtime clock (used by Pokemon Gold etc)
* In addition to frameskip, number of frames per second is displayed
* Fixed minor keyboard handling bugs, Japanese Pokemon games should work better
  now
* Fixed overflow bug that could occur after 2^32 instructions
* Fixed minor windowing bug, Dragonball Z 2 should work better now
* ROM files are split into smaller files, which reduces RAM requirement
* MeBoyBuilder: Now supports zipped ROM files (one ROM per zip file)
* MeBoyBuilder: Now tries to verify that the selected files are valid Gameboy
  carts
* MeBoyBuilder: Can now (optionally) automatically check for updates

version 1.2: 4 Mar 2007
-
* Added support for rotating the screen (with a slight performance penalty)
* Added speed throttling in the unlikely event that MeBoy exceeds 60 fps
* Added support for rarely used "LCD control operation" for turning off the LCD
* Fixed flag handling bug for increment/decrement instructions, other minor
  flag handling bugs
* Fixed timing of screen update interrupts, to better match JavaBoy and
  hopefully Gameboy. Grabbing items while spinning in Metroid 2 no longer
  causes freeze
* Fixed minor keyboard handling bug
* Fixed palette bug (some sprites had incorrect colors, such as Bugs Bunny and
  some enemies in Super Mario Land 2)
* Fixed version bug (1.1 identifies itself as 1.0)
* MeBoyBuilder: Now has a simple UI with some instructions and information
* MeBoyBuilder: Non-ascii characters in filenames now handled
* MeBoyBuilder: Warning when selecting large ROM files and Gameboy Color games
* MeBoyBuilder: MeBoy.jad file is produced, since certain phones seem to
  require it

version 1.1: 2 Nov 2005
-

* fixed spriteenabled (Balloon Kid sort of works now)
* fixed battery RAM
* fixed "suspend" and "resume"
* added full screen mode
* added "unload cart"
* added the source for MeBoyBuilder
* added settings for frameskip
* some small bug fixes and performance improvements


version 1.0: 22 Jul 2005
-

* initial release



[millstone]:
http://www.millstone.demon.co.uk/download/javaboy/

[arktos]: http://arktos.se/meboy

