package meboy.io;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

public final class SuspendedGameStore {
	private static final String STORE_PREFIX = "20S_";

	public static final class SuspendedGameData {
		public final String cartID;
		public final byte[] state;

		SuspendedGameData(String cartID, byte[] state) {
			this.cartID = cartID;
			this.state = state;
		}
	}

	private SuspendedGameStore() {
	}

	public static void save(String suspendName, String cartID, byte[] state) throws RecordStoreException {
		if (suspendName == null || cartID == null || state == null || cartID.length() == 0 || state.length == 0) {
			throw new RecordStoreException("Invalid suspended game payload");
		}
		RecordStore rs = null;
		try {
			rs = RecordStore.openRecordStore(STORE_PREFIX + suspendName, true);
			byte[] cartIDBytes = cartID.getBytes();
			if (rs.getNumRecords() == 0) {
				rs.addRecord(cartIDBytes, 0, cartIDBytes.length);
				rs.addRecord(state, 0, state.length);
			} else {
				rs.setRecord(1, cartIDBytes, 0, cartIDBytes.length);
				rs.setRecord(2, state, 0, state.length);
			}
		} finally {
			closeQuietly(rs);
		}
	}

	public static SuspendedGameData load(String suspendName) throws RecordStoreException {
		RecordStore rs = null;
		try {
			rs = RecordStore.openRecordStore(STORE_PREFIX + suspendName, false);
			if (rs.getNumRecords() < 2) {
				throw new RecordStoreException("Corrupt suspended game store: missing records");
			}
			byte[] cartIDBytes = rs.getRecord(1);
			byte[] state = rs.getRecord(2);
			if (cartIDBytes == null || cartIDBytes.length == 0 || state == null || state.length == 0) {
				throw new RecordStoreException("Corrupt suspended game store: empty cart ID or state");
			}
			return new SuspendedGameData(new String(cartIDBytes), state);
		} finally {
			closeQuietly(rs);
		}
	}

	public static void copy(String sourceName, String targetName) throws RecordStoreException {
		SuspendedGameData data = load(sourceName);
		save(targetName, data.cartID, data.state);
	}

	public static void delete(String suspendName) throws RecordStoreException {
		RecordStore.deleteRecordStore(STORE_PREFIX + suspendName);
	}

	private static void closeQuietly(RecordStore rs) {
		if (rs == null) {
			return;
		}
		try {
			rs.closeRecordStore();
		} catch (Exception e) {
		}
	}
}
