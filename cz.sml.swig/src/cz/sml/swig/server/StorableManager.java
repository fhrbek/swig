package cz.sml.swig.server;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;

import cz.sml.swig.shared.Storable;
import cz.sml.swig.shared.Storable.StorableFormatException;

public class StorableManager {

	public static <T extends Storable> T load(Class<T> clazz, File file) throws IOException, StorableFormatException {
		ByteArrayOutputStream content = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		FileInputStream is = new FileInputStream(file);
		int len;
		while ((len = is.read(buffer)) != -1)
			content.write(buffer, 0, len);
		is.close();
		content.close();

		try {
			return clazz.getConstructor(String.class).newInstance(content.toString());
		}
		catch(Exception e) {
			throw new IOException("Unable to create a new instance of " + clazz.getName() + ": " + e.getMessage());
		}
	}

	public static <T extends Storable> T store(T storable, File file) throws IOException {
		PrintWriter pw = new PrintWriter(file);
		pw.write(storable.store());
		pw.close();

		return storable;
	}
}
