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

