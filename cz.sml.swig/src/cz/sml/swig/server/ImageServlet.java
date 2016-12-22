package cz.sml.swig.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.Date;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import cz.sml.swig.shared.AccessDeniedException;

public class ImageServlet extends HttpServlet {

	private static final long serialVersionUID = 5079561624234309265L;

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) {
		String path = request.getParameter("path");
		if(path == null || path.trim().length() == 0) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}

		String contentType = null;
		String contentDisposition = null;
		StringBuilder relativePath = new StringBuilder();
		File folder = null;

		if(path.endsWith(".zip")) {
			String folderPath = path.substring(0, path.length() - ".zip".length());
			folder = new File(folderPath);
			relativePath.append(folderPath);
			relativePath.append('/');
			relativePath.append(ImageScanner.ARCHIVE_FOLDER);
			relativePath.append('/');
			relativePath.append(folder.getName());

			String fileName = folder.getName() + ".zip";

			String selection = request.getParameter("selection");
			if(selection != null)
				relativePath.append(selection);

			relativePath.append(".zip");

			contentDisposition = buildContentDisposition(fileName);
			contentType = "applicataion/zip";
		}
		else {
			String format = request.getParameter("format");

			if(format == null || format.trim().length() == 0)
				format = "original";

			if("thumbnail-sprite".equals(format)) {
				String indexStr = request.getParameter("index");
				if("0".equals(indexStr) || indexStr == null)
					indexStr = "";

				folder = new File(path);
				File target = new File(new File(folder, ImageScanner.THUMBNAIL_FOLDER), ImageScanner.THUMBNAIL_SPRITE_FILE.replace(
						ImageScanner.THUMBNAIL_SPRITE_FILE_INDEX_PLACEHOLDER, indexStr));
				relativePath.append(target.getPath());
				contentType = "image/jpeg";
			}
			else {
				folder = new File(path).getParentFile();

				if("thumbnail".equals(format)) {
					relativePath.append(pathToDraft(path, ImageScanner.THUMBNAIL_FOLDER));
					contentType = "image/jpeg";
				}
				else if("preview".equals(format)) {
					relativePath.append(pathToDraft(path, ImageScanner.PREVIEW_FOLDER));
					contentType = "image/jpeg";
				}
				else if("original".equals(format)) {
					relativePath.append(path);
					contentType = guessImageType(path);
				}
				else {
					response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
					return;
				}
			}

			String attachment = request.getParameter("attachment");
			if("true".equalsIgnoreCase(attachment)) {
				File file = new File(path);
				contentDisposition = buildContentDisposition(file.getName());
			}
		}

		if(folder == null) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}

		if(!ImageScanner.isAccessible(request, folder, null)) {
			String fallbackUrl = request.getParameter("fallbackUrl");
			if(fallbackUrl != null && fallbackUrl.length() > 0)
				try {
					response.sendRedirect(fallbackUrl);
				}
				catch(IOException e) {
					response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				}
			else
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}

		File fullPath = null;

		FileInputStream is = null;

		try {
			fullPath = getArchiveFile(request, relativePath.toString());

			if(fullPath == null)
				throw new FileNotFoundException("Internal error - cannot fetch archive file");

			long lastModified = fullPath.lastModified();
			long ifModifiedSince = -1;

			try {
				ifModifiedSince = request.getDateHeader("If-Modified-Since");
			}
			catch(Exception e) {
				// ignore this header
			}

			// for purposes of comparison we add 999 to ifModifiedSince since
			// the fidelity
			// of the IMS header generally doesn't include milli-seconds
			if(ifModifiedSince > -1 && lastModified > 0 && lastModified <= (ifModifiedSince + 999)) {
				response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
				return;
			}

			if(contentType != null)
				response.setContentType(contentType);
			if(contentDisposition != null)
				response.setHeader("Content-Disposition", contentDisposition);

			// The convenience method does not work for large files (over 2GB)
			// response.setContentLength((int) fullPath.length());
			response.setHeader("Content-Length", "" + fullPath.length());

			response.setDateHeader("Last-Modified", lastModified);
			response.setDateHeader("Expires", new Date().getTime() + 3600000); // expires
																				// after
																				// an
																				// hour
			response.setHeader("Cache-Control", "max-age=3600"); // expires
																	// after an
																	// hour
			response.setHeader("Connection", "keep-alive");

			OutputStream os = response.getOutputStream();
			is = new FileInputStream(fullPath);
			byte[] buffer = new byte[4096];
			int read;
			while ((read = is.read(buffer)) != -1)
				os.write(buffer, 0, read);
		}
		catch(FileNotFoundException e) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		catch(IOException e) {
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
		}
		catch(AccessDeniedException e) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}
		finally {
			if(is != null)
				try {
					is.close();
				}
				catch(IOException e) {
					// ignore
				}
			deleteIfTemporary(fullPath);
		}
	}

	private static final Pattern PATTERN_IMAGE_SELECTION = Pattern.compile(".*\\[(\\d+(?:,\\d+)*)\\]\\.zip$");
	private static final String TMP_SUFFIX = "-tmp";
	private static final String TMP_ZIP_SUFFIX = TMP_SUFFIX + ".zip";

	private File getArchiveFile(HttpServletRequest httpServletRequest, String relativePath) throws AccessDeniedException, IOException {
		File fullPath = new File(ImageScanner.getSwigRoot(), relativePath);
		if(fullPath.exists())
			return fullPath;

		Matcher m = PATTERN_IMAGE_SELECTION.matcher(fullPath.getAbsolutePath());

		if(m.matches()) {
			String[] indexes = m.group(1).split(",");
			int[] indexesAsInt = new int[indexes.length];

			for(int i = indexes.length - 1; i >= 0; i--)
				indexesAsInt[i] = Integer.valueOf(indexes[i]).intValue();

			return ImageScanner.createArchive(null, httpServletRequest, new File(relativePath).getParentFile().getParentFile().toString(), indexesAsInt, UUID
					.randomUUID().toString() + TMP_SUFFIX);
		}

		return null;
	}

	private boolean deleteIfTemporary(File path) {
		if(path != null && path.getName().endsWith(TMP_ZIP_SUFFIX))
			return path.delete();

		return false;
	}

	private String buildContentDisposition(String fileName) {
		String encodedFileName;
		try {
			// encode slashes into underscores (to make the file name valid),
			// then temporarily replace spaces with slashes
			// and then encode them to '%20' which is the correct code for a
			// space (otherwise they would be encoded into '+' and
			// '+' characters would be passed up to the target file name instead
			// of spaces)
			String encodedSlash = URLEncoder.encode("/", "UTF-8");
			encodedFileName = URLEncoder.encode(fileName.replace('/', '_').replace(' ', '/'), "UTF-8").replace(encodedSlash, "%20");
		}
		catch(Exception e) {
			throw new RuntimeException("Unable to encode file name");
		}

		return "attachment; filename*=UTF-8''" + encodedFileName + "";
	}

	private String guessImageType(String path) {
		path = path.toLowerCase();
		if(path.endsWith(".jpg") || path.endsWith(".jpeg"))
			return "image/jpeg";
		if(path.endsWith(".png"))
			return "image/png";
		if(path.endsWith(".tif") || path.endsWith(".tiff"))
			return "image/tiff";
		if(path.endsWith(".gif"))
			return "image/gif";

		return null;
	}

	private String pathToDraft(String path, String draftFolder) {
		File file = new File(path);
		File target = new File(new File(file.getParentFile(), draftFolder), file.getName() + ".jpeg");

		return target.getPath();
	}
}
