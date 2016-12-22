package cz.sml.swig.server;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class RoleMapperServlet extends HttpServlet {

	private static final long serialVersionUID = 1859826808670095956L;

	private static final String DEFAULT_SCRIPT = "function mapRoleToLabel(role) { return role; }\n" + "function mapRoleToToken(role) { return role; }\n"
			+ "function mapTokenToRole(label) { return label; }\nfunction isRolePublic(role) { return false; }";

	private static final String PUBLIC_SCRIPT_NAME = "rolemapper.js";

	private static final String SCRIPT_FILE_NAME = "." + PUBLIC_SCRIPT_NAME;

	private static final String EXPECTED_PATH = "/" + PUBLIC_SCRIPT_NAME;

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) {

		if(!EXPECTED_PATH.equals(request.getPathInfo())) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}

		response.setContentType("text/javascript");
		response.setCharacterEncoding("utf-8");

		try {
			copyCustomRoleMapper(response.getOutputStream());
		}
		catch(Exception e) {
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	public static boolean customRoleMapperExists() {
		return getCustomRoleMapperFile().exists();
	}

	public static String getDefaultRoleMapper() {
		return DEFAULT_SCRIPT;
	}

	public static String getCustomRoleMapper() throws Exception {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		copyCustomRoleMapper(os);
		return os.toString();
	}

	private static void copyCustomRoleMapper(OutputStream os) throws Exception {
		File jsFile = getCustomRoleMapperFile();

		if(jsFile.exists()) {
			byte[] buffer = new byte[1024];
			int len;
			FileInputStream is = new FileInputStream(jsFile);
			while ((len = is.read(buffer)) != -1)
				os.write(buffer, 0, len);
			is.close();
		}
		else
			os.write(DEFAULT_SCRIPT.getBytes("utf-8"));
	}

	private static File getCustomRoleMapperFile() {
		return new File(ImageScanner.getSwigRoot(), SCRIPT_FILE_NAME);
	}

	public static void storeCustomRoleMapper(String customRoleMapper) throws Exception {
		File jsFile = getCustomRoleMapperFile();

		if(customRoleMapper == null) {
			jsFile.delete();

			if(jsFile.exists())
				throw new IOException("Unable to delete file " + jsFile.getAbsolutePath());
		}
		else {
			FileOutputStream os = new FileOutputStream(jsFile);
			os.write(customRoleMapper.getBytes());
			os.close();
		}
	}
}
