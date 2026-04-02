package meboy;

/*

MeBoy

Copyright 2005-2009 Bjorn Carlin
http://www.arktos.se/

Based on JavaBoy, COPYRIGHT (C) 2001 Neil Millstone and The Victoria
University of Manchester. Bluetooth support based on code contributed by
Martin Neumann.

This program is free software; you can redistribute it and/or modify it
under the terms of the GNU General Public License as published by the Free
Software Foundation; either version 2 of the License, or (at your option)
any later version.

This program is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
more details.


You should have received a copy of the GNU General Public License along with
this program; if not, write to the Free Software Foundation, Inc., 59 Temple
Place - Suite 330, Boston, MA 02111-1307, USA.

*/

import javax.microedition.lcdui.*;
import javax.microedition.rms.*;
import meboy.io.SaveFileStore;
import meboy.io.SuspendedGameStore;


public class GBCanvas extends Canvas implements CommandListener {
	private static final int BUTTON_COUNT = 8;
	private static final int FPS_HISTORY_SIZE = 16;
	private static final int FPS_HISTORY_MASK = FPS_HISTORY_SIZE - 1;
	private static final int FPS_BAR_HEIGHT = 16;
	private static final int AUTO_SAVE_POLL_MS = 1000;
	private static final int AUTO_SAVE_DEBOUNCE_MS = 2000;
	private static final String SETTINGS_RECORD_NAME = "set";
	private static final int INT_BYTES = 4;
	private static final int SETTINGS_KEYS_OFFSET = 0;
	private static final int SETTINGS_MAX_FRAME_SKIP_OFFSET = 32;
	private static final int SETTINGS_ROTATIONS_OFFSET = 36;
	private static final int SETTINGS_LAZY_LOADING_OFFSET = 40;
	private static final int SETTINGS_SUSPEND_COUNTER_OFFSET = 44;
	private static final int SETTINGS_SUSPEND10_COUNT_OFFSET = 48;
	private static final int SETTINGS_SUSPEND10_DATA_OFFSET = 52;
	private static final int SETTINGS_FIXED_PART_LENGTH = 55;
	private static final int SETTINGS_FLAG_ENABLE_SCALING = 1;
	private static final int SETTINGS_FLAG_KEEP_PROPORTIONS = 2;
	private static final int SETTINGS_FLAG_FULLSCREEN = 4;
	private static final int SETTINGS_FLAG_DISABLE_COLOR = 8;
	private static final int SETTINGS_FLAG_LANGUAGE_V1 = 16;
	private static final int SETTINGS_FLAG_ENABLE_SOUND = 32;
	private static final int SETTINGS_FLAG_ADVANCED_SOUND = 64;
	private static final int SETTINGS_FLAG_ADVANCED_GRAPHICS = 128;
	private static final int SETTINGS_FLAG_SHOW_FPS = 1;
	private static final int SETTINGS_FLAG_SHOW_LOG_ITEM = 2;

	public MeBoy parent;
	private Dmgcpu cpu;
	private int w, h, l, t;
	private int sw, sh, trans; // translation, and screen size (on screen, i.e. scaled and rotated if applicable)
	private int ssw, ssh; // source screen width and height (possibly scaled, not rotated)
	private int clipHeight; // maybe including fps
	private int[] previousTime = new int[FPS_HISTORY_SIZE];
	private int previousTimeIx;
	
	private Command pauseCommand = new Command(MeBoy.literal[30], Command.SCREEN, 0);
	private Command resumeCommand = new Command(MeBoy.literal[31], Command.SCREEN, 0);
	private Command saveCommand = new Command(MeBoy.literal[32], Command.SCREEN, 1);
	private Command showFpsCommand = new Command(MeBoy.literal[33], Command.SCREEN, 3);
	private Command fullScreenCommand = new Command(MeBoy.literal[34], Command.SCREEN, 4);
	private Command setButtonsCommand = new Command(MeBoy.literal[35], Command.SCREEN, 5);
	private Command exitCommand;
	
	private static int[] key = new int[] {KEY_NUM6, KEY_NUM4, KEY_NUM2, KEY_NUM8, KEY_NUM7, KEY_NUM9, KEY_POUND, KEY_STAR};
	private String[] keyName = {
		MeBoy.literal[38],
		MeBoy.literal[39],
		MeBoy.literal[40],
		MeBoy.literal[41],
		MeBoy.literal[42],
		MeBoy.literal[43],
		MeBoy.literal[44],
		MeBoy.literal[45]};
	private int keySetCounter;
	private boolean settingKeys;
	private boolean paused;
	private boolean exiting;
	
	private String cartDisplayName;
	private String cartID;
	private String suspendName;
	
    private Thread cpuThread;
    private Thread autoSaveThread;
    private boolean autoSaveThreadRunning;
	
	
	// Common constructor
	private GBCanvas(MeBoy p, String cartID, String cartDisplayName) {
		parent = p;
		this.cartID = cartID;
		this.cartDisplayName = cartDisplayName;
		
		exitCommand = new Command(MeBoy.literal[36] + " " + cartDisplayName, Command.SCREEN, 6);
		setCommandListener(this);
		
		updateCommands();
		
		setFullScreenMode(MeBoy.fullScreen);
	}
	
	// Constructor for loading suspended games
	public GBCanvas(String cartID, MeBoy p, String cartDisplayName,
				String suspendName, byte[] suspendState) {
		this(p, cartID, cartDisplayName);
		
		this.suspendName = suspendName;
		
		cpu = new Dmgcpu(cartID, this, suspendState);
		setDimensions();
		startAutoSaveThread();
		
		cpuThread = new Thread(cpu);
		cpuThread.start();
	}
	
	// Constructor for new games
	public GBCanvas(String cartID, MeBoy p, String cartDisplayName) {
		this(p, cartID, cartDisplayName);
		
		cpu = new Dmgcpu(cartID, this);
		
		if (cpu.hasBattery())
			loadCartRam();
		
		setDimensions();
		startAutoSaveThread();
		
		cpuThread = new Thread(cpu);
		cpuThread.start();
	}
	
	private void updateCommands() {
		// remove and add all commands, to prevent pause/resume to end up last
		removeCommand(pauseCommand);
		removeCommand(resumeCommand);
		removeCommand(saveCommand);
		removeCommand(showFpsCommand);
		removeCommand(fullScreenCommand);
		removeCommand(setButtonsCommand);
		removeCommand(exitCommand);
		
		if (paused)
			addCommand(resumeCommand);
		else
			addCommand(pauseCommand);
		
		addCommand(saveCommand);
		addCommand(showFpsCommand);
		addCommand(fullScreenCommand);
		addCommand(setButtonsCommand);
		addCommand(exitCommand);
	}
	
	public void setDimensions() {
		w = getWidth();
		h = getHeight();
		
		int[] transs = new int[] {0, 5, 3, 6};
		trans = transs[MeBoy.rotations];
		boolean rotate = (MeBoy.rotations & 1) != 0;
		
		if (MeBoy.enableScaling) {
			int deltah = MeBoy.showFps ? -16: 0;
			cpu.setScale(rotate ? (h+deltah) : w, rotate ? w : (h+deltah));
		}
		ssw = sw = cpu.graphicsChip.scaledWidth;
		clipHeight = ssh = sh = cpu.graphicsChip.scaledHeight;
		
		if (rotate) {
			sw = ssh;
			clipHeight = sh = ssw;
		}
		
		l = (w - sw) / 2;
		t = (h - sh) / 2;
		if (MeBoy.showFps) {
			t -= 8;
			clipHeight += 16;
		}
		
		if (l < 0)
			l = 0;
		if (t < 0)
			t = 0;
		
		//cpu.setTranslation(trans == 0 ? l : 0, trans == 0 ? t : 0);
	}
	
	public void keyReleased(int keyCode) {
		for (int i = 0; i < BUTTON_COUNT; i++) {
			if (keyCode == key[i]) {
				cpu.buttonUp(i);
			}
		}
	}
	
	public void keyPressed(int keyCode) {
		if (settingKeys) {
			key[keySetCounter++] = keyCode;
			if (keySetCounter == BUTTON_COUNT) {
				writeSettings();
				settingKeys = false;
			}
			repaint();
			return;
		}
		
		for (int i = 0; i < BUTTON_COUNT; i++) {
			if (keyCode == key[i]) {
				cpu.buttonDown(i);
			}
		}
	}
	
	public void commandAction(Command c, Displayable s) {
		try {
			if (c == exitCommand) {
				if (exiting) {
					return;
				}
				exiting = true;
				parent.showWaitForm("Saving game...");
				Thread exitThread = new Thread(new Runnable() {
					public void run() {
						try {
							if (cpu.hasBattery())
								saveCartRam();
						} finally {
							parent.unloadCart();
							Runtime.getRuntime().gc();
							exiting = false;
						}
					}
				});
				exitThread.start();
			} else if (c == pauseCommand) {
				pause();
			} else if (c == resumeCommand && !settingKeys) {
				paused = false;
				updateCommands();
				
				cpuThread = new Thread(cpu);
				cpuThread.start();
			} else if (c == showFpsCommand) {
				MeBoy.showFps = !MeBoy.showFps;
				setDimensions();
			} else if (c == setButtonsCommand && !settingKeys) {
				pause();
				settingKeys = true;
				keySetCounter = 0;
			} else if (c == saveCommand && !settingKeys) {
				if (!cpu.isTerminated()) {
					cpu.terminate();
					waitForCpuThread();
					suspend();
					
					cpuThread = new Thread(cpu);
					cpuThread.start();
				} else {
					suspend();
				}
			} else if (c == fullScreenCommand && !settingKeys) {
				MeBoy.fullScreen = !MeBoy.fullScreen;
				setFullScreenMode(MeBoy.fullScreen);
				
				setDimensions();
			}
		} catch (Throwable th) {
			MeBoy.log(th.toString());
			MeBoy.showLog();
            if (MeBoy.debug)
                th.printStackTrace();
		}
		repaint();
	}

	public final void pause() {
		if (cpuThread == null)
			return;
		
		flushBatterySave();
		paused = true;
		updateCommands();
		
		cpu.terminate();
		waitForCpuThread();
		cpuThread = null;
	}

	private void waitForCpuThread() {
		Thread thread = cpuThread;
		while (thread != null && thread.isAlive()) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				MeBoy.log("CPU thread wait interrupted: " + e.toString());
				break;
			}
		}
	}

	private void startAutoSaveThread() {
		if (!cpu.hasBattery() || autoSaveThread != null) {
			return;
		}

		autoSaveThreadRunning = true;
		autoSaveThread = new Thread(new Runnable() {
			public void run() {
				while (autoSaveThreadRunning) {
					try {
						Thread.sleep(AUTO_SAVE_POLL_MS);
					} catch (InterruptedException e) {
						break;
					}

					if (!autoSaveThreadRunning || cpu == null || paused || exiting) {
						continue;
					}

					if (cpu.isBatterySaveDirty()) {
						long idleTime = System.currentTimeMillis() - cpu.getLastBatteryWriteTime();
						if (idleTime >= AUTO_SAVE_DEBOUNCE_MS) {
							flushBatterySave();
						}
					}
				}
			}
		});
		autoSaveThread.start();
	}

	private void stopAutoSaveThread() {
		autoSaveThreadRunning = false;
		Thread thread = autoSaveThread;
		autoSaveThread = null;
		if (thread != null) {
			thread.interrupt();
			while (thread.isAlive()) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					MeBoy.log("Auto-save thread wait interrupted: " + e.toString());
					break;
				}
			}
		}
	}

	private synchronized void flushBatterySave() {
		if (cpu == null || !cpu.hasBattery() || !cpu.isBatterySaveDirty()) {
			return;
		}
		saveCartRam();
		cpu.markBatterySaveClean();
	}
	
	public final void redrawSmall() {
		repaint(l, t, sw, clipHeight);
	}
	
	public final void paintFps(Graphics g) {
		g.setClip(l, t+sh, sw, FPS_BAR_HEIGHT);
		g.setColor(0x999999);
		g.fillRect(l, t+sh, sw, FPS_BAR_HEIGHT);
		g.setColor(0);
		
		int now = (int) System.currentTimeMillis();
		// calculate moving-average fps
		// 17 ms * 60 fps * 2*16 seconds = 32640 ms
		int estfps = ((32640 + now - previousTime[previousTimeIx]) / (now - previousTime[previousTimeIx])) >> 1;
		previousTime[previousTimeIx] = now;
		previousTimeIx = (previousTimeIx + 1) & FPS_HISTORY_MASK;
		
		g.drawString(estfps + " fps * " + (cpu.getLastSkipCount() + 1), l+1, t+sh, 20);
	}
	
	public final void paint(Graphics g) {
		if (cpu == null)
			return;
		
		if (settingKeys) {
			g.setColor(0x446688);
			g.fillRect(0, 0, w, h);
			g.setColor(-1);
			int cut = MeBoy.literal[37].indexOf(' ', MeBoy.literal[37].length() * 2/5);
			if (cut == -1)
				cut = 0;
			g.drawString(MeBoy.literal[37].substring(0, cut), w/2, h/2-18, 65);
			g.drawString(MeBoy.literal[37].substring(cut + 1), w/2, h/2, 65);
			g.drawString(keyName[keySetCounter], w/2, h/2+18, 65);
			return;
		}
		
		if (g.getClipWidth() != sw || g.getClipHeight() != clipHeight) {
			g.setColor(0x666666);
			g.fillRect(0, 0, w, h);
		}
		
		if (MeBoy.showFps) {
			paintFps(g);
		}
		
		g.setClip(l, t, sw, sh);
		if (cpu.graphicsChip.frameBufferImage == null) {
			g.setColor(0xaaaaaa);
			g.fillRect(l, t, sw, sh);
		} else if (trans == 0) {
			g.drawImage(cpu.graphicsChip.frameBufferImage, l, t, 20);
		} else {
			g.drawRegion(cpu.graphicsChip.frameBufferImage, 0, 0, ssw, ssh, trans, l, t, 20);
		}
		cpu.graphicsChip.notifyRepainted();
		
		if (paused) {
			g.setColor(0);
			g.drawString(MeBoy.literal[30], w/2-1, h/2, 65);
			g.drawString(MeBoy.literal[30], w/2, h/2-1, 65);
			g.drawString(MeBoy.literal[30], w/2+1, h/2, 65);
			g.drawString(MeBoy.literal[30], w/2, h/2+1, 65);
			g.setColor(0xffffff);
			g.drawString(MeBoy.literal[30], w/2, h/2, 65);
		}
	}
	
	public static final void setInt(byte[] b, int i, int v) {
		b[i++] = (byte) (v >> 24);
		b[i++] = (byte) (v >> 16);
		b[i++] = (byte) (v >> 8);
		b[i++] = (byte) (v);
	}
	
	public static final int getInt(byte[] b, int i) {
		int r = b[i++] & 0xFF;
		r = (r << 8) + (b[i++] & 0xFF);
		r = (r << 8) + (b[i++] & 0xFF);
		return (r << 8) + (b[i++] & 0xFF);
	}

	private static boolean hasRange(byte[] b, int index, int length) {
		return b != null && index >= 0 && length >= 0 && index <= b.length - length;
	}

	private static int getIntChecked(byte[] b, int index, String field) throws Exception {
		if (!hasRange(b, index, 4)) {
			throw new Exception("Corrupt settings store: missing " + field + " at " + index);
		}
		return getInt(b, index);
	}

	private static String readByteString(byte[] b, int[] indexRef, String field) throws Exception {
		int index = indexRef[0];
		if (!hasRange(b, index, 1)) {
			throw new Exception("Corrupt settings store: missing length for " + field);
		}
		int length = b[index++] & 0xFF;
		if (!hasRange(b, index, length)) {
			throw new Exception("Corrupt settings store: invalid length for " + field + ": " + length);
		}
		String value = new String(b, index, length);
		indexRef[0] = index + length;
		return value;
	}

	private static String readUtf16String(byte[] b, int[] indexRef, String field) throws Exception {
		int index = indexRef[0];
		if (!hasRange(b, index, 1)) {
			throw new Exception("Corrupt settings store: missing UTF-16 length for " + field);
		}
		int length = b[index++] & 0xFF;
		if (!hasRange(b, index, length * 2)) {
			throw new Exception("Corrupt settings store: invalid UTF-16 length for " + field + ": " + length);
		}
		char[] chars = new char[length];
		for (int i = 0; i < length; i++) {
			chars[i] += (b[index++] & 0xFF) << 8;
			chars[i] += (b[index++] & 0xFF);
		}
		indexRef[0] = index;
		return new String(chars);
	}
	
	public static final void writeSettings() {
		RecordStore rs = null;
		try {
			rs = RecordStore.openRecordStore(SETTINGS_RECORD_NAME, true);
			
			int bLength = SETTINGS_FIXED_PART_LENGTH;
			for (int i = 0; i < MeBoy.suspendName10.length; i++)
				bLength += 1 + MeBoy.suspendName10[i].length();
			for (int i = 0; i < MeBoy.suspendName20.length; i++)
				bLength += 1 + MeBoy.suspendName20[i].length() * 2;
			bLength++;
			
			byte[] b = new byte[bLength];
			
			for (int i = 0; i < BUTTON_COUNT; i++)
				setInt(b, SETTINGS_KEYS_OFFSET + (i * INT_BYTES), key[i]);
			setInt(b, SETTINGS_MAX_FRAME_SKIP_OFFSET, MeBoy.maxFrameSkip);
			setInt(b, SETTINGS_ROTATIONS_OFFSET, MeBoy.rotations);
			setInt(b, SETTINGS_LAZY_LOADING_OFFSET, MeBoy.lazyLoadingThreshold);
			setInt(b, SETTINGS_SUSPEND_COUNTER_OFFSET, MeBoy.suspendCounter);
			setInt(b, SETTINGS_SUSPEND10_COUNT_OFFSET, MeBoy.suspendName10.length);
			
			int index = SETTINGS_SUSPEND10_DATA_OFFSET;
			for (int i = 0; i < MeBoy.suspendName10.length; i++) {
				String s = MeBoy.suspendName10[i];
				b[index++] = (byte) (s.length());
				for (int j = 0; j < s.length(); j++)
					b[index++] = (byte) s.charAt(j);
			}
			
			b[index++] = (byte) ((MeBoy.enableScaling ? SETTINGS_FLAG_ENABLE_SCALING : 0)
					+ (MeBoy.keepProportions ? SETTINGS_FLAG_KEEP_PROPORTIONS : 0)
					+ (MeBoy.fullScreen ? SETTINGS_FLAG_FULLSCREEN : 0)
					+ (MeBoy.disableColor ? SETTINGS_FLAG_DISABLE_COLOR : 0)
					+ (MeBoy.enableSound ? SETTINGS_FLAG_ENABLE_SOUND : 0)
					+ (MeBoy.advancedSound ? SETTINGS_FLAG_ADVANCED_SOUND : 0)
					+ (MeBoy.advancedGraphics ? SETTINGS_FLAG_ADVANCED_GRAPHICS : 0));
			b[index++] = (byte) MeBoy.language;
			b[index++] = (byte) ((MeBoy.showFps ? SETTINGS_FLAG_SHOW_FPS : 0)
					+ (MeBoy.showLogItem ? SETTINGS_FLAG_SHOW_LOG_ITEM : 0));

			b[index++] = (byte) MeBoy.suspendName20.length;
			for (int i = 0; i < MeBoy.suspendName20.length; i++) {
				// Manual UTF-16, since String.getBytes(encoding) is not well-supported:
				char[] chars = MeBoy.suspendName20[i].toCharArray();
				b[index++] = (byte) (chars.length);
				
				for (int j = 0; j < chars.length; j++) {
					b[index++] = (byte) (chars[j] >> 8);
					b[index++] = (byte) (chars[j]);
				}
			}
			
			if (rs.getNumRecords() == 0) {
				rs.addRecord(b, 0, bLength);
			} else {
				rs.setRecord(1, b, 0, bLength);
			}
		} catch (Exception e) {
			if (MeBoy.debug) {
				e.printStackTrace();
			}
			MeBoy.log(e.toString());
		} finally {
			try {
				if (rs != null)
					rs.closeRecordStore();
			} catch (Exception e) {
			}
		}
	}
	
	public static final void readSettings() {
		RecordStore rs = null;
		try {
			MeBoy.suspendName10 = new String[0];
			MeBoy.suspendName20 = new String[0];
			
			rs = RecordStore.openRecordStore(SETTINGS_RECORD_NAME, true);
			if (rs.getNumRecords() > 0) {
				MeBoy.log("Settings: existing RecordStore found, loading persisted settings");
				byte[] b = rs.getRecord(1);
				
				for (int i = 0; i < BUTTON_COUNT; i++)
					key[i] = getIntChecked(b, SETTINGS_KEYS_OFFSET + (i * INT_BYTES), "key[" + i + "]");
				if (hasRange(b, SETTINGS_MAX_FRAME_SKIP_OFFSET, INT_BYTES))
					MeBoy.maxFrameSkip = getIntChecked(b, SETTINGS_MAX_FRAME_SKIP_OFFSET, "maxFrameSkip");
				if (hasRange(b, SETTINGS_ROTATIONS_OFFSET, INT_BYTES))
					MeBoy.rotations = getIntChecked(b, SETTINGS_ROTATIONS_OFFSET, "rotations");
				if (hasRange(b, SETTINGS_LAZY_LOADING_OFFSET, INT_BYTES))
					MeBoy.lazyLoadingThreshold = getIntChecked(b, SETTINGS_LAZY_LOADING_OFFSET, "lazyLoadingThreshold");
				
				int index = SETTINGS_SUSPEND_COUNTER_OFFSET;
				if (b.length > index) {
					// suspended games index
					MeBoy.suspendCounter = getIntChecked(b, index, "suspendCounter");
					index += INT_BYTES;
					int oldSuspendCount = getIntChecked(b, index, "suspendName10 length");
					if (oldSuspendCount < 0) {
						throw new Exception("Corrupt settings store: negative suspendName10 length");
					}
					MeBoy.suspendName10 = new String[oldSuspendCount];
					index += INT_BYTES;
					int[] indexRef = new int[] {index};
					for (int i = 0; i < MeBoy.suspendName10.length; i++) {
						MeBoy.suspendName10[i] = readByteString(b, indexRef, "suspendName10[" + i + "]");
					}
					index = indexRef[0];
				}

				if (b.length > index) {
					// settings, part 1
					MeBoy.enableScaling = (b[index] & SETTINGS_FLAG_ENABLE_SCALING) != 0;
					MeBoy.keepProportions = (b[index] & SETTINGS_FLAG_KEEP_PROPORTIONS) != 0;
					MeBoy.fullScreen = (b[index] & SETTINGS_FLAG_FULLSCREEN) != 0;
					MeBoy.disableColor = (b[index] & SETTINGS_FLAG_DISABLE_COLOR) != 0;
					MeBoy.language = (b[index] & SETTINGS_FLAG_LANGUAGE_V1) != 0 ? 1 : 0;
					MeBoy.enableSound = (b[index] & SETTINGS_FLAG_ENABLE_SOUND) != 0;
					MeBoy.advancedSound = (b[index] & SETTINGS_FLAG_ADVANCED_SOUND) != 0;
					MeBoy.advancedGraphics = (b[index] & SETTINGS_FLAG_ADVANCED_GRAPHICS) != 0;
					index++;
				}

				if (b.length > index) {
					MeBoy.language = b[index++];
				}

				if (b.length > index) {
					// settings, part 2
					MeBoy.showFps = (b[index] & SETTINGS_FLAG_SHOW_FPS) != 0;
					MeBoy.showLogItem = (b[index] & SETTINGS_FLAG_SHOW_LOG_ITEM) != 0;
					index++;
				}

				if (b.length > index) {
					int suspendCount20 = b[index++] & 0xFF;
					MeBoy.suspendName20 = new String[suspendCount20];
					int[] indexRef = new int[] {index};
					for (int i = 0; i < MeBoy.suspendName20.length; i++) {
						MeBoy.suspendName20[i] = readUtf16String(b, indexRef, "suspendName20[" + i + "]");
					}
					index = indexRef[0];
				}
				MeBoy.log("Settings: loaded persisted language id " + MeBoy.language + " (bytes=" + b.length + ")");
			} else {
				MeBoy.log("Settings: no RecordStore settings found, using locale auto-detection");
				MeBoy.language = MeBoy.detectLanguageFromLocale();
				MeBoy.log("Settings: auto-detected language id " + MeBoy.language + ", saving initial settings");
				writeSettings();
			}
		} catch (Exception e) {
			if (MeBoy.debug)
				e.printStackTrace();
			MeBoy.log(e.toString());
			MeBoy.log("Settings: failed to load persisted settings, resetting to defaults");
			MeBoy.suspendName10 = new String[0];
			MeBoy.suspendName20 = new String[0];
			MeBoy.language = MeBoy.detectLanguageFromLocale();
		} finally {
			try {
				if (rs != null)
					rs.closeRecordStore();
			} catch (Exception e) {
			}
		}
	}
	
	private synchronized void saveCartRam() {
		try {
			String externalRomFile = MeBoy.getExternalRomFile(cartID);
			if (externalRomFile != null) {
				SaveFileStore.write(externalRomFile, cpu.exportBatterySave());
				saveExternalRtc(externalRomFile);
				cpu.markBatterySaveClean();
				return;
			}

			byte[][] ram = cpu.getCartRam();
			int bankCount = ram.length;
			int bankSize = ram[0].length;
			int size = bankCount * bankSize + 13;
			byte[] b = new byte[size];
			
			for (int i = 0; i < bankCount; i++)
				System.arraycopy(ram[i], 0, b, i * bankSize, bankSize);
			
			System.arraycopy(cpu.getRtcReg(), 0, b, bankCount * bankSize, 5);
			long now = System.currentTimeMillis();
			setInt(b, bankCount * bankSize + 5, (int) (now >> 32));
			setInt(b, bankCount * bankSize + 9, (int) now);

			RecordStore rs = RecordStore.openRecordStore("20R_" + cartID, true);
			try {
				if (rs.getNumRecords() == 0) {
					rs.addRecord(b, 0, size);
				} else {
					rs.setRecord(1, b, 0, size);
				}
			} finally {
				rs.closeRecordStore();
			}
			cpu.markBatterySaveClean();
		} catch (Exception e) {
			if (MeBoy.debug)
				e.printStackTrace();
		}
	}
	
	private final void loadCartRam() {
		try {
			byte[] b = null;
			String externalRomFile = MeBoy.getExternalRomFile(cartID);
			if (externalRomFile != null) {
				b = SaveFileStore.read(externalRomFile);
				if (b != null) {
					if (isLegacySaveFormat(b)) {
						loadLegacyBatterySave(b);
					} else {
						cpu.importBatterySave(b);
						loadExternalRtc(externalRomFile);
					}
					return;
				}
			}

			if (b == null) {
				RecordStore rs = RecordStore.openRecordStore("20R_" + cartID, true);
				try {
					if (rs.getNumRecords() > 0) {
						b = rs.getRecord(1);
					}
				} finally {
					rs.closeRecordStore();
				}
			}

			if (b == null) {
				return;
			}

			loadLegacyBatterySave(b);
		} catch (Exception e) {
			if (MeBoy.debug)
				e.printStackTrace();
			MeBoy.log(e.toString());
		}
	}

	private void saveExternalRtc(String externalRomFile) throws Exception {
		if (!cpu.hasRtc()) {
			SaveFileStore.deleteRtc(externalRomFile);
			return;
		}

		byte[] rtc = new byte[13];
		System.arraycopy(cpu.getRtcReg(), 0, rtc, 0, 5);
		long now = System.currentTimeMillis();
		setInt(rtc, 5, (int) (now >> 32));
		setInt(rtc, 9, (int) now);
		SaveFileStore.writeRtc(externalRomFile, rtc);
	}

	private void loadExternalRtc(String externalRomFile) throws Exception {
		byte[] rtc = SaveFileStore.readRtc(externalRomFile);
		if (rtc == null || rtc.length != 13 || !cpu.hasRtc()) {
			return;
		}

		System.arraycopy(rtc, 0, cpu.getRtcReg(), 0, 5);
		long time = getInt(rtc, 5);
		time = (time << 32) + ((long) getInt(rtc, 9) & 0xffffffffL);
		time = System.currentTimeMillis() - time;
		cpu.rtcSkip((int) (time / 1000));
	}

	private boolean isLegacySaveFormat(byte[] data) {
		byte[][] ram = cpu.getCartRam();
		return data.length == ram.length * ram[0].length + 13;
	}

	private void loadLegacyBatterySave(byte[] b) {
		byte[][] ram = cpu.getCartRam();
		int bankCount = ram.length;
		int bankSize = ram[0].length;

		for (int i = 0; i < bankCount; i++)
			System.arraycopy(b, i * bankSize, ram[i], 0, bankSize);

		if (b.length == bankCount * bankSize + 13) {
			// load real time clock
			System.arraycopy(b, bankCount * bankSize, cpu.getRtcReg(), 0, 5);
			long time = getInt(b, bankCount * bankSize + 5);
			time = (time << 32) + ((long) getInt(b, bankCount * bankSize + 9) & 0xffffffffL);
			time = System.currentTimeMillis() - time;
			cpu.rtcSkip((int) (time / 1000));
		}
	}
	
	private final void suspend() {
		try {
			boolean insertIndex = false;
			if (suspendName == null) {
				suspendName = (MeBoy.suspendCounter++) + ": " + cartDisplayName;
				insertIndex = true;
			}
			SuspendedGameStore.save(suspendName, cartID, cpu.flatten());
			if (insertIndex)
				MeBoy.addSuspendedGame(suspendName);
		} catch (Exception e) {
			MeBoy.showError(null, "error#10", e);
		}
	}
	
    public void releaseReferences() {
		// This code helps the garbage collector on some platforms.
		// (contributed by Alberto Simon)
        stopAutoSaveThread();
        flushBatterySave();
        cpu.terminate();
        waitForCpuThread();
        cpu.releaseReferences();
        cpu = null;
        
        cartID = null;
        cartDisplayName = null;
        previousTime = null;
        parent = null;
        System.gc();
    }
}

