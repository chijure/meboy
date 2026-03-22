package meboy;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

final class SuspendedGameStore {
	private static final String STORE_PREFIX = "20S_";

	static final class SuspendedGameData {
		final String cartID;
		final byte[] state;

		SuspendedGameData(String cartID, byte[] state) {
			this.cartID = cartID;
			this.state = state;
		}
	}

	private SuspendedGameStore() {
	}

	static void save(String suspendName, String cartID, byte[] state) throws RecordStoreException {
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

	static SuspendedGameData load(String suspendName) throws RecordStoreException {
		RecordStore rs = null;
		try {
			rs = RecordStore.openRecordStore(STORE_PREFIX + suspendName, false);
			return new SuspendedGameData(new String(rs.getRecord(1)), rs.getRecord(2));
		} finally {
			closeQuietly(rs);
		}
	}

	static void copy(String sourceName, String targetName) throws RecordStoreException {
		SuspendedGameData data = load(sourceName);
		save(targetName, data.cartID, data.state);
	}

	static void delete(String suspendName) throws RecordStoreException {
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
