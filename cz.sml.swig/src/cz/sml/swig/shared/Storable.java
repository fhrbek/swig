package cz.sml.swig.shared;

import com.google.gwt.user.client.rpc.IsSerializable;

public interface Storable extends IsSerializable {

	public static class StorableFormatException extends Exception {
		private static final long serialVersionUID = -3574343732780723983L;

		public StorableFormatException(String message) {
			super(message);
		}
	}

	String store();

	void load(String serialized) throws StorableFormatException;
}
