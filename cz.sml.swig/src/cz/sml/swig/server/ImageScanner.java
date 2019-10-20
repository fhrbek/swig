package cz.sml.swig.server;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;
import javax.media.jai.JAI;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;

import cz.sml.swig.shared.AccessDeniedException;
import cz.sml.swig.shared.AccessRights;
import cz.sml.swig.shared.AccessRoles;
import cz.sml.swig.shared.Constants;
import cz.sml.swig.shared.FolderMetadata;
import cz.sml.swig.shared.MediaMetadata;

public class ImageScanner implements ServletContextListener {

	public static final String THUMBNAIL_FOLDER = ".thumbnail";

	public static final String THUMBNAIL_SPRITE_FILE_PREFIX = ".sprite";

	public static final String THUMBNAIL_SPRITE_FILE_INDEX_PLACEHOLDER = "{index}";

	public static final String THUMBNAIL_SPRITE_FILE = THUMBNAIL_SPRITE_FILE_PREFIX + THUMBNAIL_SPRITE_FILE_INDEX_PLACEHOLDER + ".jpeg";

	public static final String PREVIEW_FOLDER = ".preview";

	public static final String ARCHIVE_FOLDER = ".archive";

	private static final int DEFAULT_SCAN_INTERVAL = 300; // 5 minutes

	private static final String[] IMAGE_EXTENSIONS = new String[] { ".jpg", ".jpeg", ".png", ".gif" };

	private static final String[] VIDEO_EXTENSIONS = new String[] { ".ytb" };

	private static final String DRAFT_IMAGE_EXTENSION = ".jpeg";

	private static final String SESSION_KEY_AUTHORIZED_ROLES = "authorized.roles";

	private static final String ACCESS_FILE = ".access";

	private static final String METADATA_FILE = ".metadata";

	private static final String REFRESH_FILE = ".refresh";

	private static final String VERSION_FILE = ".version";
	
	private static final Comparator<FolderMetadata> INVERSE_COMPARATOR = new Comparator<FolderMetadata>() {

		@Override
		public int compare(FolderMetadata md1, FolderMetadata md2) {
			return -md1.getName().compareTo(md2.getName());
		}

	};

	private static final String UUID_FILE = ".uuid";

	private static final String SWIG_AUTH_COOKIE = "SWIG.AUTH";

	private static void createArchive(File archive, List<File> files) throws IOException {
		if(files.size() > 0) {
			ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive));

			for(File image : files) {
				ZipEntry entry = new ZipEntry(image.getName());
				zip.putNextEntry(entry);
				FileInputStream source = new FileInputStream(image);
				byte[] buffer = new byte[4096];
				int read;
				while ((read = source.read(buffer)) != -1)
					zip.write(buffer, 0, read);
				source.close();
			}

			zip.close();
		}
	}

	private class ScannerThread extends Thread {

		private boolean shutDown = false;

		@Override
		public void run() {
			log(Level.INFO, "Scanner started.");

			while (!shutDown) {
				try {
					scanImages();
					Thread.sleep(scanInterval);
				}
				catch(InterruptedException e) {
					// ignore
				}
			}

			log(Level.INFO, "Scanner shut down.");
		}

		synchronized public void shutDown() {
			shutDown = true;
			interrupt();

			log(Level.INFO, "Shutting down scanner...");
		}

		private void scanImages() {
			clearLog();

			log(Level.INFO, "Checking if admin access is configured...");

			File rolesFile = new File(swigRoot, ACCESS_FILE);

			AccessRoles accessRoles = null;

			if(rolesFile.exists())
				try {
					accessRoles = StorableManager.load(AccessRoles.class, rolesFile);
				}
				catch(Exception e) {
					log(Level.SEVERE, "Access Roles file exists but could not be loaded: " + e.getMessage());
				}
			else
				accessRoles = new AccessRoles();

			if(accessRoles != null) {
				if(!accessRoles.hasRole(AccessRoles.ADMIN_ROLE)) {
					log(Level.INFO, "Admin role does not exist, it will be created...");
					accessRoles.setRole(AccessRoles.ADMIN_ROLE, AccessRoles.DEFAULT_ADMIN_PASSWORD);

					try {
						StorableManager.store(accessRoles, rolesFile);
						log(Level.INFO, "Admin role was successfully created...");
					}
					catch(IOException e) {
						log(Level.SEVERE, "Could not store modified access roles: " + e.getMessage());
					}
				}
			}

			log(Level.INFO, "Scanning root directories...");

			boolean forceRefresh = false;

			File refreshFile = new File(swigRoot, REFRESH_FILE);

			if(refreshFile.isFile() && refreshFile.length() == 0) {
				log(Level.INFO, "Global refresh is requested - all galleries will be refreshed...");
				forceRefresh = true;
			}

			for(File candidate : swigRoot.listFiles()) {
				if(shutDown) {
					log(Level.INFO, "Scanning interrupted before completed (scanner shutting down)");
					break;
				}

				log(Level.INFO, "Checking " + candidate.getAbsolutePath() + "...");

				if(candidate.isDirectory() && !isHidden(candidate)) {
					log(Level.INFO, candidate.getAbsolutePath() + " is a valid directory, stepping into it...");
					scanImages(candidate, forceRefresh);
				}
				else
					log(Level.INFO, candidate.getAbsolutePath() + " will be ignored...");
			}

			if(forceRefresh) {
				if(!refreshFile.delete())
					log(Level.WARNING, "Unable to delete .refresh file in " + swigRoot.getAbsolutePath());
			}

			log(Level.INFO, "Scanning done.");
		}

		private void scanImages(File dir, boolean forceRefresh) {
			log(Level.INFO, "Scanning directory " + dir.getAbsolutePath() + "...");

			LinkedHashMap<String, File> imagesAndVideos = new LinkedHashMap<String, File>();
			File[] files = dir.listFiles();
			Arrays.sort(files);

			for(File candidate : files) {
				if(shutDown) {
					log(Level.INFO, "Scanning interrupted before completed (scanner shutting down)");
					break;
				}

				if(isHidden(candidate)) {
					log(Level.INFO, candidate.getAbsolutePath() + " will be ignored...");
					continue;
				}

				if(candidate.isDirectory()) {
					log(Level.INFO, candidate.getAbsolutePath() + " is a valid directory, stepping into it...");
					scanImages(candidate, forceRefresh);
				}
				else if(isImageOrVideo(candidate)) {
					log(Level.INFO, candidate.getAbsolutePath() + " is a supported media file, adding to list...");
					imagesAndVideos.put(candidate.getName(), candidate);
				}
				else
					log(Level.INFO, candidate.getAbsolutePath() + " will be ignored...");
			}

			try {
				boolean changes = false;
				boolean obsoleteVersion = false;
				boolean localForceRefresh = false;

				File refreshFile = new File(dir, REFRESH_FILE);

				if(refreshFile.isFile() && refreshFile.length() == 0) {
					log(Level.INFO, "Refresh in " + dir.getAbsolutePath() + " is requested - this gallery will be refreshed...");
					forceRefresh = true;
					localForceRefresh = true;
				}

				if(!Constants.CURRENT_DATA_VERSION.equals(readDataVersion(dir))) {
					obsoleteVersion = true;
					log(Level.INFO, "Data version of " + dir.getAbsolutePath() + " is obsolete - this gallery will be refreshed...");
					forceRefresh = true;
				}

				File thumbnail = ensureThumbnailsFolderExists(dir);
				File preview = ensurePreviewFolderExists(dir);

				log(Level.INFO, "Reading thumbnails from " + thumbnail.getAbsolutePath() + "...");
				LinkedHashMap<String, File> thumbnailImages = getDraftImages(thumbnail);
				log(Level.INFO, "Reading previews from " + preview.getAbsolutePath() + "...");
				LinkedHashMap<String, File> previewImages = getDraftImages(preview);
				Set<File> corruptedImages = new HashSet<File>();

				for(Map.Entry<String, File> imageEntry : imagesAndVideos.entrySet()) {
					File imageOrVideo = imageEntry.getValue();
					File imageThumbnail = thumbnailImages.get(imageEntry.getKey());
					if(imageThumbnail == null || imageThumbnail.lastModified() < imageOrVideo.lastModified() || forceRefresh) {
						if(imageThumbnail == null)
							log(Level.INFO, "Thumbnail for " + imageOrVideo.getName() + " does not exist - it will be generated...");
						else if(imageThumbnail.lastModified() < imageOrVideo.lastModified())
							log(Level.INFO, "Thumbnail for " + imageOrVideo.getName() + " is obsolete - it will be rerendered...");
						else
							log(Level.INFO, "Thumbnail for " + imageOrVideo.getName() + " will be rerendered (refresh was requested)...");

						try {
							updateThumbnail(imageOrVideo);
							changes = true;
						}
						catch(Exception e) {
							log(Level.WARNING, "Error generating thumbnail for " + imageOrVideo.getName() + ": " + e.getMessage());
							corruptedImages.add(imageOrVideo);
							continue;
						}
					}

					File imagePreview = previewImages.get(imageEntry.getKey());
					if(imagePreview == null || imagePreview.lastModified() < imageOrVideo.lastModified() || forceRefresh) {
						if(imagePreview == null)
							log(Level.INFO, "Preview for " + imageOrVideo.getName() + " does not exist - it will be generated...");
						else if(imagePreview.lastModified() < imageOrVideo.lastModified())
							log(Level.INFO, "Preview for " + imageOrVideo.getName() + " is obsolete - it will be rerendered...");
						else
							log(Level.INFO, "Preview for " + imageOrVideo.getName() + " will be rerendered (refresh was requested)...");

						try {
							updatePreview(imageOrVideo);
						}
						catch(Exception e) {
							log(Level.WARNING, "Error generating preview for " + imageOrVideo.getName() + ": " + e.getMessage());
							corruptedImages.add(imageOrVideo);
							log(Level.INFO, "Deleting thumbnail for " + imageOrVideo.getName() + "...");
							if(!imageThumbnail.delete())
								log(Level.WARNING, "Thumbnail for " + imageOrVideo.getName() + " could not be deleted...");
							continue;
						}
					}
				}

				for(Map.Entry<String, File> thunbnailEntry : thumbnailImages.entrySet()) {
					if(!imagesAndVideos.containsKey(thunbnailEntry.getKey())) {
						log(Level.INFO, "Image for thumbnail " + thunbnailEntry.getKey() + " does not exist any more - the thumbnail will be deleted...");
						if(!thunbnailEntry.getValue().delete())
							log(Level.WARNING, "Thumbnail " + thunbnailEntry.getKey() + " could not be deleted...");
						changes = true;
					}
				}

				File thumbnailSpriteFile = new File(thumbnail, THUMBNAIL_SPRITE_FILE.replace(THUMBNAIL_SPRITE_FILE_INDEX_PLACEHOLDER, ""));

				if(!thumbnailSpriteFile.exists() || changes)
					generateThumbnailSprite(dir);

				for(Map.Entry<String, File> previewEntry : previewImages.entrySet()) {
					if(!imagesAndVideos.containsKey(previewEntry.getKey())) {
						log(Level.INFO, "Image for preview " + previewEntry.getKey() + " does not exist any more - the preview will be deleted...");
						if(!previewEntry.getValue().delete())
							log(Level.WARNING, "Preview " + previewEntry.getKey() + " could not be deleted...");
					}
				}

				File archiveFolder = new File(dir, ARCHIVE_FOLDER);
				if(!archiveFolder.exists())
					archiveFolder.mkdir();

				File archive = new File(archiveFolder, dir.getName() + ".zip");

				if(!archive.isFile() || changes) {
					log(Level.INFO, "Deleting all previously created archives in " + archiveFolder.getAbsolutePath() + "...");

					for(File file : archiveFolder.listFiles()) {
						file.delete();
					}

					log(Level.INFO, "Creating folder archive " + archive.getName() + " in " + archiveFolder.getAbsolutePath() + "...");

					List<File> filesToProcess = new ArrayList<File>(imagesAndVideos.size());
					for(File imageOrVideo : imagesAndVideos.values()) {
						if(corruptedImages.contains(imageOrVideo) || isVideo(imageOrVideo.getName()))
							continue;

						filesToProcess.add(imageOrVideo);
					}

					createArchive(archive, filesToProcess);
				}

				File metadataFile = new File(preview, METADATA_FILE);

				if(!metadataFile.exists() || changes)
					generateMetadata(dir);

				if(obsoleteVersion)
					updateDataVersion(dir);

				if(localForceRefresh) {
					if(!refreshFile.delete())
						log(Level.WARNING, "Unable to delete .refresh file in " + dir.getAbsolutePath());
				}

				log(Level.INFO, "Scanning of " + dir.getAbsolutePath() + " done.");
			}
			catch(Throwable t) {
				log(Level.WARNING, "Scanning of " + dir.getAbsolutePath() + " failed: " + t.getMessage());
			}
		}

		private String readDataVersion(File dir) {
			File versionFile = new File(dir, VERSION_FILE);

			BufferedReader reader = null;
			String version = null;

			try {
				reader = new BufferedReader(new FileReader(versionFile));
				version = reader.readLine();
				if(version == null)
					version = Constants.DEFAULT_DATA_VERSION;
			}
			catch(Exception e) {
				if(e instanceof FileNotFoundException)
					log(Level.INFO, "Version file for " + dir.getAbsolutePath() + " does not exist, it will be generated");
				else
					log(Level.WARNING, "Unable to read data version file for " + dir.getAbsolutePath() + ": " + e.getMessage());
				version = Constants.DEFAULT_DATA_VERSION;
			}
			finally {
				if(reader != null)
					try {
						reader.close();
					}
					catch(IOException e) {
						// ignore
					}
			}

			return version;
		}

		private void updateDataVersion(File dir) {
			log(Level.INFO, "Updating version of " + dir.getAbsolutePath() + " to " + Constants.CURRENT_DATA_VERSION);

			File versionFile = new File(dir, VERSION_FILE);
			PrintWriter writer = null;

			try {
				writer = new PrintWriter(versionFile);
				writer.println(Constants.CURRENT_DATA_VERSION);
			}
			catch(Exception e) {
				log(Level.WARNING, "Unable to update data version file for " + dir.getAbsolutePath() + "...");
			}
			finally {
				if(writer != null)
					writer.close();
			}
		}

		private void updateThumbnail(final File mediaFile) throws IOException, ImageProcessingException, MetadataException {
			Image image;
			boolean isVideo = false;

			if(isImage(mediaFile.getName()))
				image = tryToReadImage(mediaFile);
			else if(isVideo(mediaFile.getName())) {
				image = readVideoSplashImage(mediaFile);
				isVideo = true;
			}
			else
				throw new IllegalArgumentException(mediaFile.getName() + " is not a supported media file");

			BufferedImage thumbImage = new BufferedImage(Constants.THUMBNAIL_SIZE, Constants.THUMBNAIL_SIZE, BufferedImage.TYPE_INT_RGB);

			Graphics2D graphics2D = thumbImage.createGraphics();
			graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics2D.clearRect(0, 0, Constants.THUMBNAIL_SIZE, Constants.THUMBNAIL_SIZE);

			double imageWidth = image.getWidth(null);
			double imageHeight = image.getHeight(null);
			double ratio = imageWidth > imageHeight
					? imageHeight / Constants.THUMBNAIL_SIZE
					: imageWidth / Constants.THUMBNAIL_SIZE;

			int scaledWidth = (int) (imageWidth / ratio + 0.5);
			int scaledHeight = (int) (imageHeight / ratio + 0.5);

			int xOffset = scaledWidth > scaledHeight
					? (int) ((scaledHeight - scaledWidth) / 2)
					: 0;
			int yOffset = scaledWidth <= scaledHeight
					? (int) ((scaledWidth - scaledHeight) / 2)
					: 0;

			Image scaledImage = image.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);

			graphics2D.drawImage(scaledImage, xOffset, yOffset, scaledWidth, scaledHeight, null);

			if(isVideo)
				drawFilmStripes(graphics2D, scaledWidth + 2 * xOffset, scaledHeight, 10, 2);

			writeImage(mediaFile, adjustOrientation(thumbImage, getOrientation(mediaFile)), THUMBNAIL_FOLDER);
		}
		
		private BufferedImage tryToReadImage(File imageFile) throws IOException {
			BufferedImage image;
			
			try {
				image = ImageIO.read(imageFile);
			}
			catch(IOException e) {
				log(Level.WARNING, "Cannot read " + imageFile.getName() + " with standard IO: " + e.getMessage() + ", trying another method...");
				
				try {
					image = JAI.create("fileload", imageFile.getAbsolutePath()).getAsBufferedImage();
				}
				catch(Exception e1) {
					log(Level.WARNING, "Cannot read " + imageFile.getName() + " with JAI: " + e1.getMessage() + ", trying another method...");
					
					try {
						String extension = null;
						String fileName = imageFile.getName();

						int i = fileName.lastIndexOf('.');
						if (i > 0) {
						    extension = fileName.substring(i);
						}
						File tempTarget = File.createTempFile(fileName, extension);
						tempTarget.deleteOnExit();
						log(Level.INFO, "Trying to convert " + fileName + " into " + tempTarget.getAbsolutePath() + "...");
						List<String> command = new ArrayList<String>();
						command.add("convert"); // call ImageMagick (hopefully installed)
						command.add(imageFile.getAbsolutePath());
						command.add(tempTarget.getAbsolutePath());
						ProcessBuilder builder = new ProcessBuilder(command);
						builder.redirectError();
						builder.redirectOutput();
						Process process = builder.start();
						process.waitFor();
						if (process.exitValue() != 0) {
							tempTarget.delete();
							throw new Exception("ImageMagick failed (is it installed?) - see servlet container log for more details");
						}
						imageFile.renameTo(new File(imageFile.getAbsolutePath() + ".original"));
						try {
							Files.copy(tempTarget.toPath(), imageFile.toPath());
						} catch (Exception e2) {
							new File(imageFile.getAbsolutePath() + ".original").renameTo(imageFile);
							throw new Exception("Unable to move temporary file to original location: " + imageFile.getAbsolutePath() + "(" + e2.getMessage() + ")");
						}
						finally {
							tempTarget.delete();
						}
						log(Level.INFO, "Trying to load converted file " + fileName);
						image = ImageIO.read(imageFile);
					} catch (Exception e3) {
						log(Level.WARNING, "Failed to use alternative methods for " + imageFile.getName() + ": " + e3.getMessage());
						throw new IOException("All image reading methods failed, see warnings above");
					}
				}
			}
			
			return image;
		}

		private void updatePreview(File mediaFile) throws IOException, ImageProcessingException, MetadataException {
			Image image;
			boolean isVideo = false;

			if(isImage(mediaFile.getName()))
				image = tryToReadImage(mediaFile);
			else if(isVideo(mediaFile.getName())) {
				image = readVideoSplashImage(mediaFile);
				isVideo = true;
			}
			else
				throw new IllegalArgumentException(mediaFile.getName() + " is not a supported media file");

			double imageWidth = image.getWidth(null);
			double imageHeight = image.getHeight(null);
			double ratio = imageWidth > imageHeight
					? imageWidth / Constants.PREVIEW_MAX_SIZE
					: imageHeight / Constants.PREVIEW_MAX_SIZE;

			int scaledWidth = (int) (imageWidth / ratio + 0.5);
			int scaledHeight = (int) (imageHeight / ratio + 0.5);

			Image scaledImage = image.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);

			BufferedImage bufferedImage = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);

			Graphics2D graphics2D = bufferedImage.createGraphics();
			graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics2D.clearRect(0, 0, scaledWidth, scaledHeight);

			graphics2D.drawImage(scaledImage, 0, 0, scaledWidth, scaledHeight, null);

			if(isVideo)
				drawFilmStripes(graphics2D, scaledWidth, scaledHeight, 16, 12);

			writeImage(mediaFile, adjustOrientation(bufferedImage, getOrientation(mediaFile)), PREVIEW_FOLDER);
		}

		private void drawFilmStripes(Graphics2D graphics2D, int width, int height, int count, int radius) {
			int stripeHeight = height / 8;
			graphics2D.setColor(Color.black);
			graphics2D.fillRect(0, 0, width, stripeHeight);
			graphics2D.fillRect(0, height - stripeHeight, width, stripeHeight);
			graphics2D.setColor(Color.gray);

			int segWidth = width / count;
			int holeWidth = segWidth * 2 / 3;
			int holeHeight = stripeHeight * 1 / 2;
			int xOffs = (segWidth - holeWidth) / 2;
			int yOffs = (stripeHeight - holeHeight) / 2;

			for(int i = 0; i < count; i++) {
				graphics2D.fillRoundRect(segWidth * i + xOffs, yOffs, holeWidth, holeHeight, radius, radius);
				graphics2D.fillRoundRect(segWidth * i + xOffs, height - stripeHeight + yOffs, holeWidth, holeHeight, radius, radius);
			}
		}

		private Image readVideoSplashImage(File mediaFile) throws IOException {
			String id = readVideoId(mediaFile);

			URL thumbnailUrl = new URL("http://i3.ytimg.com/vi/" + id + "/default.jpg");

			try {
				return ImageIO.read(thumbnailUrl);
			}
			catch(Exception e) {
				return ImageIO.read(getClass().getResourceAsStream("images/ytb-default.png"));
			}
		}

		private int getOrientation(File imageFile) throws ImageProcessingException, IOException, MetadataException {
			int orientation = 1;

			if(isImage(imageFile.getName())) {
				Metadata metadata = ImageMetadataReader.readMetadata(imageFile);
				ExifIFD0Directory directory = metadata.getDirectory(ExifIFD0Directory.class);

				if(directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION))
					orientation = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
			}

			return orientation;
		}

		private RenderedImage adjustOrientation(BufferedImage image, int orientation) {
			switch (orientation) {
			case 1:
				// no transformation
				break;
			case 2:
				// mirror horizontally
				image = mirrorImage(image);
				break;
			case 3:
				// rotate +180 deg
				image = rotateImage(image, 2);
				break;
			case 4:
				// mirror & rotate +180 deg
				image = rotateImage(image, 2);
				image = mirrorImage(image);
				break;
			case 5:
				// mirror & rotate -90 deg
				image = rotateImage(image, 3);
				image = mirrorImage(image);
				break;
			case 6:
				// rotate +90 deg
				image = rotateImage(image, 1);
				break;
			case 7:
				// mirror & rotate +90 deg
				image = rotateImage(image, 1);
				image = mirrorImage(image);
				break;
			case 8:
				// rotate -90 deg
				image = rotateImage(image, 3);
				break;
			}

			return image;
		}

		private BufferedImage mirrorImage(BufferedImage image) {
			AffineTransform tx = AffineTransform.getScaleInstance(-1, 1);
			tx.translate(-image.getWidth(), 0);
			AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);

			return op.filter(image, null);
		}

		private BufferedImage rotateImage(BufferedImage image, int numberOfQuadrants) {
			numberOfQuadrants = numberOfQuadrants % 4;

			if(numberOfQuadrants == 0)
				return image;

			int imageWidth = image.getWidth();
			int imageHeight = image.getHeight();

			AffineTransform tx = new AffineTransform();

			switch (numberOfQuadrants) {
			case 1:
				tx.setTransform(0, 1, -1, 0, imageHeight, 0);
				break;
			case 2:
				tx.setTransform(-1, 0, 0, -1, imageWidth, imageHeight);
				break;
			case 3:
				tx.setTransform(0, -1, 1, 0, 0, imageWidth);
				break;
			}

			AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);

			BufferedImage target = numberOfQuadrants != 2 && imageWidth != imageHeight
					? new BufferedImage(imageHeight, imageWidth, BufferedImage.TYPE_INT_RGB)
					: null;

			return op.filter(image, target);
		}

		private void writeImage(File imageFile, RenderedImage draftImage, String subFolder) {
			File thumbImageFile = new File(new File(imageFile.getParentFile(), subFolder), imageFile.getName() + DRAFT_IMAGE_EXTENSION);

			try {
				ImageIO.write(draftImage, "jpeg", thumbImageFile);
			}
			catch(IOException e) {
				log(Level.WARNING, "An error has occurred while rendering " + thumbImageFile.getAbsolutePath() + ": " + e.getMessage());
			}
		}

		private LinkedHashMap<String, File> getDraftImages(File dir) {
			LinkedHashMap<String, File> images = new LinkedHashMap<String, File>();

			File[] files = dir.listFiles();
			Arrays.sort(files);

			for(File candidate : files) {
				if(shutDown) {
					log(Level.INFO, "Scanning interrupted before completed (scanner shutting down)");
					break;
				}

				if(isHidden(candidate)) {
					log(Level.INFO, candidate.getAbsolutePath() + " will be ignored...");
					continue;
				}

				if(candidate.getName().endsWith(DRAFT_IMAGE_EXTENSION)) {
					String originalName = getOriginalName(candidate);
					if(isImageOrVideo(originalName))
						images.put(originalName, candidate);
					else
						log(Level.INFO, candidate.getAbsolutePath() + " will be ignored...");
				}
				else
					log(Level.INFO, candidate.getAbsolutePath() + " will be ignored...");
			}

			return images;
		}

		private File ensureThumbnailsFolderExists(File dir) {
			File thumbnail = new File(dir, THUMBNAIL_FOLDER);
			if(thumbnail.exists()) {
				if(!thumbnail.isDirectory())
					throw new IllegalArgumentException("Thunbnail file is not a directory: " + thumbnail.getAbsolutePath());
			}
			else if(!thumbnail.mkdir())
				throw new RuntimeException("Unable to create thumbnail directory: " + thumbnail.getAbsolutePath());

			return thumbnail;
		}

		private File ensurePreviewFolderExists(File dir) {
			File preview = new File(dir, PREVIEW_FOLDER);
			if(preview.exists()) {
				if(!preview.isDirectory())
					throw new IllegalArgumentException("Preview file is not a directory: " + preview.getAbsolutePath());
			}
			else if(!preview.mkdir())
				throw new RuntimeException("Unable to create preview directory: " + preview.getAbsolutePath());

			return preview;
		}

	}

	private static ImageScanner instance;

	private StringBuilder recentLog = new StringBuilder();

	private Logger log;

	private Date startTime;

	private ScannerThread timer = null;

	private File swigRoot;

	private int scanInterval = DEFAULT_SCAN_INTERVAL * 1000;

	@Override
	public void contextDestroyed(ServletContextEvent event) {
		if(timer != null) {
			timer.shutDown();
			timer = null;
		}

		instance = null;
		log = null;
		startTime = null;
	}

	@Override
	public void contextInitialized(ServletContextEvent event) {
		instance = this;
		log = Logger.getLogger(getClass().getName());

		String root = System.getProperty("swig.root");
		if(root == null)
			throw new IllegalArgumentException("'swig.root' property is not defined");

		swigRoot = new File(root);

		if(!swigRoot.exists())
			throw new IllegalArgumentException("SWIG root (" + root + ") does not exist");
		else if(!swigRoot.isDirectory())
			throw new IllegalArgumentException("SWIG root (" + root + ") is not a directory");

		String scanIntervalString = System.getProperty("scan.interval");
		if(scanIntervalString != null)
			scanInterval = Integer.valueOf(scanIntervalString) * 1000;

		timer = new ScannerThread();
		timer.start();

		startTime = new Date();
	}

	public static File getSwigRoot() {
		File root = instance != null
				? instance.swigRoot
				: null;

		if(root == null)
			throw new IllegalArgumentException("SWIG root is not known at the moment");

		return root;
	}

	public static String getRecentLog() {
		if(instance == null)
			return "ERROR: Image Scanner is not running";

		StringBuilder report = new StringBuilder();

		report.append("Image Scanner started on " + instance.startTime.toString());
		report.append('\n');
		report.append("Java version is " + System.getProperty("java.version"));
		report.append('\n');
		report.append("Root directory is set to " + instance.swigRoot.getAbsolutePath());
		report.append('\n');
		report.append("Scan interval is set to " + instance.scanInterval / 1000 + " seconds");
		report.append('\n');
		report.append('\n');
		report.append("Recent scanner log:");
		report.append('\n');
		report.append('\n');
		if(instance.recentLog.length() > 0)
			report.append(instance.recentLog);
		else
			report.append("Not Available");
		report.append('\n');

		return report.toString();
	}

	private static ImageScanner getInstance() {
		if(instance == null)
			throw new IllegalArgumentException("Image Scanner is not running");

		return instance;
	}

	public static boolean isAuthorized(HttpServletRequest httpServletRequest, String role) {
		return getInstance().isAuthorizedInternal(httpServletRequest, role);
	}

	public static AccessRoles getAccessRoles() throws Exception {
		return getInstance().getAccessRolesInternal();
	}

	private AccessRoles getAccessRolesInternal() throws Exception {
		File accessRolesFile = new File(swigRoot, ACCESS_FILE);

		if(!accessRolesFile.isFile())
			throw new Exception("Soubor přístupových rolí neexistuje");

		return StorableManager.load(AccessRoles.class, accessRolesFile);
	}

	public static AccessRoles storeAccessRoles(AccessRoles accessRoles) throws Exception {
		return getInstance().storeAccessRolesInternal(accessRoles);
	}

	private AccessRoles storeAccessRolesInternal(AccessRoles accessRoles) throws Exception {
		File accessRolesFile = new File(swigRoot, ACCESS_FILE);

		return StorableManager.store(accessRoles, accessRolesFile);
	}

	public static AccessRights getAccessRights(String path) throws Exception {
		return getInstance().getAccessRightsInternal(path);
	}

	private AccessRights getAccessRightsInternal(String path) throws Exception {
		File accessRightsFile = new File(new File(swigRoot, path), ACCESS_FILE);

		if(!accessRightsFile.isFile())
			return new AccessRights();

		return StorableManager.load(AccessRights.class, accessRightsFile);
	}

	public static AccessRights storeAccessRights(String path, AccessRights accessRights) throws Exception {
		return getInstance().storeAccessRightsInternal(path, accessRights);
	}

	private AccessRights storeAccessRightsInternal(String path, AccessRights accessRights) throws Exception {
		File accessRightsFile = new File(new File(swigRoot, path), ACCESS_FILE);

		return StorableManager.store(accessRights, accessRightsFile);
	}

	synchronized private String[] getAuthorizedRoles(HttpServletRequest httpServletRequest, boolean reloadPersistent) {
		String authorizedRolesString = (String) httpServletRequest.getSession().getAttribute(SESSION_KEY_AUTHORIZED_ROLES);

		String[] authorizedRoles = authorizedRolesString != null
				? authorizedRolesString.split(",")
				: new String[0];

		if(reloadPersistent) {
			File accessRulesFile = new File(swigRoot, ACCESS_FILE);

			if(!accessRulesFile.isFile())
				return authorizedRoles;

			Map<String, String> passwdMap;
			try {
				passwdMap = StorableManager.load(AccessRoles.class, accessRulesFile).getRoleMap();
			}
			catch(Exception e) {
				passwdMap = Collections.emptyMap();
			}

			Set<String> rolesCollector = new HashSet<String>();

			String authorizedRoleTokensString = null;

			Cookie[] cookies = httpServletRequest.getCookies();

			if(cookies != null)
				for(Cookie cookie : cookies) {
					if(SWIG_AUTH_COOKIE.equals(cookie.getName())) {
						authorizedRoleTokensString = cookie.getValue();
						break;
					}
				}

			String[] authorizedRoleTokens = authorizedRoleTokensString != null
					? authorizedRoleTokensString.split("\\|")
					: new String[0];

			for(String authorizedRoleToken : authorizedRoleTokens) {
				int pos = authorizedRoleToken.indexOf(':');

				if(pos != -1) {
					String role = authorizedRoleToken.substring(0, pos);
					String persistentAuthToken = getPersistentAuthorizationToken(role, passwdMap.get(role));

					if(authorizedRoleToken.equals(persistentAuthToken))
						rolesCollector.add(role);
				}
			}

			if(rolesCollector.size() > 0) {

				for(String persistentRole : rolesCollector)
					registerRoleInSession(httpServletRequest.getSession(), persistentRole);

				for(String sessionRole : authorizedRoles)
					rolesCollector.add(sessionRole);

				return rolesCollector.toArray(new String[rolesCollector.size()]);
			}
		}

		return authorizedRoles;
	}

	synchronized private boolean isAuthorizedInternal(HttpServletRequest httpServletRequest, String role) {
		for(String authorizedRole : getAuthorizedRoles(httpServletRequest, false))
			if(authorizedRole.equals(role))
				return true;

		for(String authorizedRole : getAuthorizedRoles(httpServletRequest, true))
			if(authorizedRole.equals(role))
				return true;

		return false;
	}

	public static boolean authorize(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, String role, String accessKey,
			boolean persistent) {
		return getInstance().authorizeInternal(httpServletRequest, httpServletResponse, role, accessKey, persistent);
	}

	public static void unauthorize(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, String role) {
		getInstance().unauthorizeInternal(httpServletRequest, httpServletResponse, role);
	}

	synchronized private String getPersistentAuthorizationSalt() {
		File uuidFile = new File(swigRoot, UUID_FILE);

		if(uuidFile.isFile()) {
			BufferedReader brd = null;

			try {
				FileReader rd = new FileReader(uuidFile);
				brd = new BufferedReader(rd);
				return brd.readLine();
			}
			catch(Exception e) {
				log.warning("Unable to use persistent connections: " + e.getMessage());
				return null;
			}
			finally {
				if(brd != null)
					try {
						brd.close();
					}
					catch(IOException e) {
						// ignore
					}
			}
		}

		UUID uuid = UUID.randomUUID();
		String uuidStr = uuid.toString();
		try {
			FileOutputStream os = new FileOutputStream(uuidFile);
			os.write(uuidStr.getBytes());
			os.close();

			return uuidStr;
		}
		catch(Exception e) {
			log.warning("Unable to use persistent connections: " + e.getMessage());
			return null;
		}
	}

	private String getPersistentAuthorizationToken(String role, String accessKey) {
		if(role == null || accessKey == null)
			return null;

		StringBuilder bld = new StringBuilder(role);
		bld.append(':');
		try {
			byte[] digest = MessageDigest.getInstance("MD5").digest((role + '|' + accessKey + '|' + getPersistentAuthorizationSalt()).getBytes());
			for(byte b : digest)
				bld.append(String.format("%02x", b));
			return bld.toString();
		}
		catch(Exception e) {
			return null;
		}
	}

	synchronized private boolean authorizeInternal(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, String role,
			String accessKey, boolean persistent) {
		File accessRulesFile = new File(swigRoot, ACCESS_FILE);

		if(!accessRulesFile.isFile())
			return false;

		BufferedReader reader = null;

		try {
			reader = new BufferedReader(new FileReader(accessRulesFile));
			String accessRule;

			while ((accessRule = reader.readLine()) != null) {
				String[] ruleTokens = accessRule.split(",");
				if(ruleTokens.length == 2 && ruleTokens[0].equals(role) && ruleTokens[1].equals(accessKey)) {

					registerRoleInSession(httpServletRequest.getSession(), role);

					if(persistent) {
						String authorizedRoleTokensString = null;

						Cookie[] cookies = httpServletRequest.getCookies();

						if(cookies != null)
							for(Cookie cookie : cookies) {
								if(SWIG_AUTH_COOKIE.equals(cookie.getName())) {
									authorizedRoleTokensString = cookie.getValue();
									break;
								}
							}

						String[] authorizedRoleTokens = authorizedRoleTokensString != null
								? authorizedRoleTokensString.split("\\|")
								: new String[0];

						String persistentAuthToken = getPersistentAuthorizationToken(role, accessKey);

						boolean alreadyPersistentlyAuthorized = false;
						if(persistentAuthToken != null)
							for(String authorizedRole : authorizedRoleTokens)
								if(persistentAuthToken.equals(authorizedRole)) {
									alreadyPersistentlyAuthorized = true;
									break;
								}

						if(!alreadyPersistentlyAuthorized) {
							if(authorizedRoleTokensString == null || authorizedRoleTokensString.trim().length() == 0)
								authorizedRoleTokensString = persistentAuthToken;
							else
								authorizedRoleTokensString += "|" + persistentAuthToken;

							setPersistentAuthorizationCookie(httpServletResponse, authorizedRoleTokensString);
						}
					}

					return true;
				}
			}
		}
		catch(FileNotFoundException e) {
			// ignore
		}
		catch(IOException e) {
			// ignore
		}
		finally {
			if(reader != null)
				try {
					reader.close();
				}
				catch(IOException e) {
					// ignore
				}
		}

		return false;
	}

	private void setPersistentAuthorizationCookie(HttpServletResponse httpServletResponse, String authorizedRoleTokensString) {
		Cookie cookie = new Cookie(SWIG_AUTH_COOKIE, authorizedRoleTokensString);
		cookie.setPath("/");
		cookie.setMaxAge(86400 * 365 * 50); // grant access for 50 years :-)
		httpServletResponse.addCookie(cookie);
	}

	private void unauthorizeInternal(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, String role) {
		removeRoleFromSession(httpServletRequest.getSession(), role);
		removeRoleFromCookie(httpServletRequest, httpServletResponse, role);
	}

	private void registerRoleInSession(HttpSession session, String role) {
		String authorizedRolesString = (String) session.getAttribute(SESSION_KEY_AUTHORIZED_ROLES);
		String[] authorizedRoles = authorizedRolesString != null
				? authorizedRolesString.split(",")
				: new String[0];

		boolean alreadyAuthorized = false;
		for(String authorizedRole : authorizedRoles)
			if(authorizedRole.equals(role)) {
				alreadyAuthorized = true;
				break;
			}

		if(!alreadyAuthorized) {
			if(authorizedRolesString == null || authorizedRolesString.trim().length() == 0)
				authorizedRolesString = role;
			else
				authorizedRolesString += "," + role;

			session.setAttribute(SESSION_KEY_AUTHORIZED_ROLES, authorizedRolesString);
		}
	}

	synchronized private void removeRoleFromSession(HttpSession session, String role) {
		String authorizedRolesString = (String) session.getAttribute(SESSION_KEY_AUTHORIZED_ROLES);
		String[] authorizedRoles = authorizedRolesString != null
				? authorizedRolesString.split(",")
				: new String[0];

		StringBuilder remainingRoles = new StringBuilder();

		for(String authorizedRole : authorizedRoles) {
			if(authorizedRole.equals(role))
				continue;

			if(remainingRoles.length() > 0)
				remainingRoles.append(',');

			remainingRoles.append(authorizedRole);
		}

		session.setAttribute(SESSION_KEY_AUTHORIZED_ROLES, remainingRoles.toString());
	}

	synchronized private void removeRoleFromCookie(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, String role) {
		String authorizedRoleTokensString = null;

		Cookie[] cookies = httpServletRequest.getCookies();

		if(cookies != null)
			for(Cookie cookie : cookies) {
				if(SWIG_AUTH_COOKIE.equals(cookie.getName())) {
					authorizedRoleTokensString = cookie.getValue();
					break;
				}
			}

		String[] authorizedRoleTokens = authorizedRoleTokensString != null
				? authorizedRoleTokensString.split("\\|")
				: new String[0];

		StringBuilder remainingRoles = new StringBuilder();
		for(String authorizedRoleToken : authorizedRoleTokens) {
			int pos = authorizedRoleToken.indexOf(':');

			String authorizedRole = null;

			if(pos != -1)
				authorizedRole = authorizedRoleToken.substring(0, pos);

			if(role.equals(authorizedRole))
				continue;

			if(remainingRoles.length() > 0)
				remainingRoles.append('|');

			remainingRoles.append(authorizedRoleToken);
		}

		setPersistentAuthorizationCookie(httpServletResponse, remainingRoles.toString());
	}

	public static FolderMetadata[] getAccessibleFolders(String role, String folder) {
		return getInstance().getAccessibleFoldersInternal(role, folder);
	}

	private FolderMetadata[] getAccessibleFoldersInternal(String role, String folder) {
		List<FolderMetadata> accessibleFolders = new ArrayList<FolderMetadata>();
		File folderToAnalyze = folder != null && folder.length() > 0
				? new File(swigRoot, folder)
				: swigRoot;
		for(File dir : folderToAnalyze.listFiles()) {
			if(!isHidden(dir) && isAccessible(new String[] { role }, dir)) {
				int imageCount = 0;
				int videoCount = 0;

				for(MediaMetadata md : getMediaMetadata(dir, MediaMetadata.FLAG_DEFAULT)) {
					switch (md.getType()) {
					case IMAGE:
						imageCount++;
						break;
					case VIDEO:
						videoCount++;
						break;
					default:
					}
				}

				accessibleFolders.add(new FolderMetadata(dir.getName(), getAccessibleFolderCount(role, folder + "/" + dir.getName()), imageCount, videoCount));
			}
		}

		Collections.sort(accessibleFolders, INVERSE_COMPARATOR);
		return accessibleFolders.toArray(new FolderMetadata[accessibleFolders.size()]);
	}

	private int getAccessibleFolderCount(String role, String folder) {
		int count = 0;
		File folderToAnalyze = folder != null && folder.length() > 0
				? new File(swigRoot, folder)
				: swigRoot;
		for(File dir : folderToAnalyze.listFiles()) {
			if(!isHidden(dir) && isAccessible(new String[] { role }, dir))
				count++;
		}

		return count;
	}

	public static boolean isAccessible(HttpServletRequest httpServletRequest, File folder, String requiredRole) {
		return getInstance().isAccessibleInternal(httpServletRequest, folder, requiredRole);
	}

	private boolean isAccessibleInternal(HttpServletRequest httpServletRequest, File folder, String requiredRole) {
		String[] authorizedRoles = getAuthorizedRoles(httpServletRequest, false);
		if(requiredRole != null) {
			boolean present = false;
			for(String auth : authorizedRoles)
				if(requiredRole.equals(auth)) {
					present = true;
					break;
				}

			authorizedRoles = present
					? new String[] { requiredRole }
					: new String[0];
		}

		boolean result = isAccessible(authorizedRoles, folder);

		if(!result) {
			authorizedRoles = getAuthorizedRoles(httpServletRequest, true);
			if(requiredRole != null) {
				boolean present = false;
				for(String auth : authorizedRoles)
					if(requiredRole.equals(auth)) {
						present = true;
						break;
					}

				authorizedRoles = present
						? new String[] { requiredRole }
						: new String[0];
			}

			result = isAccessible(authorizedRoles, folder);
		}

		return result;
	}

	private boolean isAccessible(String[] roles, File folder) {
		if(folder == null || roles.length == 0)
			return false;

		if(!folder.isAbsolute())
			folder = new File(swigRoot, folder.getPath());

		if(!folder.isDirectory())
			return false;

		if(folder.equals(swigRoot))
			return true;

		for(String role : roles)
			if(AccessRoles.ADMIN_ROLE.equals(role))
				return true;

		try {
			AccessRights accessRights = StorableManager.load(AccessRights.class, new File(folder, ACCESS_FILE));
			if(accessRights.hasOneOfRolesOrIsUniversalAccess(roles))
				return isAccessible(roles, folder.getParentFile());
		}
		catch(Exception e) {
			// fallback to false
		}

		return false;
	}

	public static MediaMetadata[] getMediaMetadata(String role, HttpServletRequest httpServletRequest, String path, int flags) throws AccessDeniedException {
		return getInstance().getMediaMetadataInternal(role, httpServletRequest, path, flags);
	}

	public static File createArchive(String role, HttpServletRequest httpServletRequest, String path, int[] selection, String suffix)
			throws AccessDeniedException, IOException {
		return getInstance().createArchiveInternal(role, httpServletRequest, path, selection, suffix);
	}

	private File createArchiveInternal(String role, HttpServletRequest httpServletRequest, String path, int[] selection, String suffix)
			throws AccessDeniedException, IOException {
		MediaMetadata[] metadata = getMediaMetadataInternal(role, httpServletRequest, path, MediaMetadata.FLAG_DEFAULT);

		File folder = new File(swigRoot, path);
		List<File> files = new ArrayList<File>(selection.length);
		StringBuilder fileName = new StringBuilder(folder.getName());

		for(int index : selection) {
			files.add(new File(folder, metadata[index].getName()));
		}
		fileName.append("-");
		fileName.append(suffix);
		fileName.append(".zip");

		File archiveFolder = new File(folder, ARCHIVE_FOLDER);
		File archiveFile = new File(archiveFolder, fileName.toString());

		log(Level.INFO, "Creating folder selection archive " + fileName + " in " + archiveFolder.getAbsolutePath() + "...");
		createArchive(archiveFile, files);

		return archiveFile;
	}

	private MediaMetadata[] getMediaMetadataInternal(String role, HttpServletRequest httpServletRequest, String path, int flags) throws AccessDeniedException {
		File folder = new File(swigRoot, path);
		if(!isAccessible(httpServletRequest, folder, role))
			throw new AccessDeniedException("Access denied");

		return getMediaMetadata(folder, flags);
	}

	private MediaMetadata[] getMediaMetadata(File folder, int flags) {
		List<MediaMetadata> metadataList;

		File metadataFile = new File(new File(folder, PREVIEW_FOLDER), METADATA_FILE);

		if(metadataFile.isFile())
			try {
				metadataList = readMetadata(metadataFile, flags);
			}
			catch(Exception e) {
				log(Level.WARNING, "Unable to read metadata for " + folder.getAbsolutePath() + ": " + e.getMessage());
				metadataList = generateMetadata(folder);
			}
		else
			metadataList = generateMetadata(folder);

		if((flags & MediaMetadata.FLAG_ARCHIVE_SIZE) > 0) {
			File archiveFile = new File(new File(folder, ARCHIVE_FOLDER), folder.getName() + ".zip");

			if(archiveFile.isFile()) {
				MediaMetadata metadata = new MediaMetadata(MediaMetadata.Type.ARCHIVE, MediaMetadata.ARCHIVE_METADATA_NAME);
				metadata.setOriginalFileSize(archiveFile.length());

				metadataList.add(metadata);
			}
		}

		return metadataList.toArray(new MediaMetadata[metadataList.size()]);
	}

	private List<MediaMetadata> readMetadata(File metadataFile, int flags) throws Exception {
		List<MediaMetadata> metadataList = new ArrayList<MediaMetadata>();

		BufferedReader reader = new BufferedReader(new FileReader(metadataFile));
		String line;

		try {
			while ((line = reader.readLine()) != null)
				metadataList.add(MediaMetadata.fromString(line, flags));
		}
		finally {
			reader.close();
		}

		return metadataList;
	}

	private void generateThumbnailSprite(File dir) {
		log(Level.INFO, "Generating thumbnail sprite image for " + dir.getAbsolutePath() + "...");

		File thumbnailFolder = new File(dir, THUMBNAIL_FOLDER);
		List<String> thumbnailNames = getThumbnailNames(thumbnailFolder);
		int thumbnailCount;
		List<String> spriteFileNames = new ArrayList<String>();
		if((thumbnailCount = thumbnailNames.size()) > 0) {
			int maxSpriteHeight = Constants.THUMBNAIL_SIZE * thumbnailCount;
			int spriteWidth = Constants.THUMBNAIL_SIZE;
			int spriteRows;
			if(maxSpriteHeight < Constants.MAX_THUMBNAIL_SPRITE_HEIGHT) {
				spriteRows = thumbnailCount;
			}
			else {
				spriteRows = Constants.MAX_THUMBNAIL_SPRITE_HEIGHT / Constants.THUMBNAIL_SIZE;
				maxSpriteHeight = spriteRows * Constants.THUMBNAIL_SIZE;
			}

			BufferedImage spriteImage = null;
			Graphics2D graphics = null;
			File thumbnailSpriteFile = null;

			int i = 0;
			int spriteIndex = 0;
			String lastThumbnailName = null;

			for(String thumbnailName : thumbnailNames) {

				lastThumbnailName = thumbnailName;

				if(i % spriteRows == 0) {
					if(i > 0) {
						try {
							ImageIO.write(spriteImage, "jpeg", thumbnailSpriteFile);
						}
						catch(Exception e) {
							log(Level.SEVERE, "Cannot save thumbnail sprite image in " + dir.getAbsolutePath() + ": " + e.getMessage());
							thumbnailSpriteFile.delete();
						}

						spriteIndex++;
					}

					int height = thumbnailCount - i >= spriteRows
							? maxSpriteHeight
							: (thumbnailCount - i) * Constants.THUMBNAIL_SIZE;
					spriteImage = new BufferedImage(spriteWidth, height, BufferedImage.TYPE_INT_RGB);
					graphics = spriteImage.createGraphics();
					String indexStr = spriteIndex > 0
							? ("" + spriteIndex)
							: "";
					thumbnailSpriteFile = new File(thumbnailFolder, THUMBNAIL_SPRITE_FILE.replace(THUMBNAIL_SPRITE_FILE_INDEX_PLACEHOLDER, indexStr));
					spriteFileNames.add(thumbnailSpriteFile.getName());
				}

				File thumbnailFile = new File(thumbnailFolder, thumbnailName + DRAFT_IMAGE_EXTENSION);
				try {
					BufferedImage image = ImageIO.read(thumbnailFile);
					int dy = (i % spriteRows) * Constants.THUMBNAIL_SIZE;
					graphics.drawImage(image, 0, dy, Constants.THUMBNAIL_SIZE, dy + Constants.THUMBNAIL_SIZE, 0, 0, Constants.THUMBNAIL_SIZE,
							Constants.THUMBNAIL_SIZE, null);
				}
				catch(IOException e) {
					log(Level.SEVERE, "Cannot add thumbnail " + lastThumbnailName + " to the sprite file " + thumbnailSpriteFile.getAbsolutePath() + " in "
							+ dir.getAbsolutePath() + ": " + e.getMessage());
					thumbnailSpriteFile.delete();
					break;
				}

				i++;
			}

			try {
				ImageIO.write(spriteImage, "jpeg", thumbnailSpriteFile);
			}
			catch(Exception e) {
				log(Level.SEVERE,
						"Cannot add thumbnail " + lastThumbnailName + " to the sprite file " + thumbnailSpriteFile.getAbsolutePath() + " in "
								+ dir.getAbsolutePath() + ": " + e.getMessage());
				thumbnailSpriteFile.delete();
			}
		}
		else {
			File thumbnailSpriteFile = new File(thumbnailFolder, THUMBNAIL_SPRITE_FILE.replace(THUMBNAIL_SPRITE_FILE_INDEX_PLACEHOLDER, ""));
			spriteFileNames.add(thumbnailSpriteFile.getName());

			try {
				new FileOutputStream(thumbnailSpriteFile).close(); // create a
																	// fake file
			}
			catch(Exception e) {
				log(Level.SEVERE, "Cannot save a fake thumbnail sprite image in " + dir.getAbsolutePath() + ": " + e.getMessage());
				thumbnailSpriteFile.delete();
			}
		}

		// clean up
		for(File candidate : thumbnailFolder.listFiles(new FilenameFilter() {

			@Override
			public boolean accept(File dir, String name) {
				return name.startsWith(THUMBNAIL_SPRITE_FILE_PREFIX);
			}

		})) {
			if(!spriteFileNames.contains(candidate.getName())) {
				if(!candidate.delete())
					log(Level.WARNING, "Cannot delete an obsolete thumbnail sprite image " + candidate.getAbsolutePath() + " in " + dir.getAbsolutePath());
			}
		}
	}

	private String readVideoUrl(File mediaFile) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(mediaFile));
		String videoUrl = reader.readLine();
		reader.close();

		if(videoUrl == null || videoUrl.length() == 0)
			throw new IOException("Invalid content of " + mediaFile.getAbsolutePath() + ": expected a URL on the first line");

		return videoUrl;
	}

	private String readVideoId(File mediaFile) throws IOException {
		String videoUrl = readVideoUrl(mediaFile);
		if(videoUrl.startsWith("http://youtu.be/"))
			return videoUrl.substring("http://youtu.be/".length());
		else if(videoUrl.startsWith("https://youtu.be/"))
			return videoUrl.substring("https://youtu.be/".length());

		throw new IOException("Invalid content of " + mediaFile.getAbsolutePath() + ": expected a simple YouTube URL");
	}

	private String readVideoEmbedUrl(File mediaFile) throws IOException {
		return "http://www.youtube.com/embed/" + readVideoId(mediaFile);
	}

	private List<MediaMetadata> generateMetadata(File dir) {
		log(Level.INFO, "Generating metadata file for " + dir.getAbsolutePath() + "...");

		File thumbnailFolder = new File(dir, THUMBNAIL_FOLDER);
		File previewFolder = new File(dir, PREVIEW_FOLDER);

		List<MediaMetadata> metadataList = new ArrayList<MediaMetadata>();

		for(String thumbnailName : getThumbnailNames(thumbnailFolder)) {
			MediaMetadata.Type metadataType;

			if(isImage(thumbnailName))
				metadataType = MediaMetadata.Type.IMAGE;
			else if(isVideo(thumbnailName))
				metadataType = MediaMetadata.Type.VIDEO;
			else
				throw new IllegalArgumentException("Unsupported media file: " + thumbnailName);

			MediaMetadata metadata = new MediaMetadata(metadataType, thumbnailName);
			File previewFile = new File(previewFolder, thumbnailName + DRAFT_IMAGE_EXTENSION);
			if(previewFile.exists()) {
				try {
					BufferedImage image = ImageIO.read(previewFile);
					metadata.setPreviewWidth(image.getWidth());
					metadata.setPreviewHeight(image.getHeight());
				}
				catch(IOException e) {
					metadata.setPreviewWidth(-2);
					metadata.setPreviewHeight(-2);
				}
			}

			File originalFile = new File(dir, thumbnailName);
			if(originalFile.exists()) {
				if(metadataType == MediaMetadata.Type.VIDEO) {
					String videoUrl;

					try {
						videoUrl = readVideoEmbedUrl(originalFile);
					}
					catch(IOException e) {
						videoUrl = "";
					}

					metadata.setVideoUrl(videoUrl);
					metadata.setOriginalFileSize(-2);
				}
				else
					metadata.setOriginalFileSize(originalFile.length());
			}
			else
				metadata.setOriginalFileSize(-2);

			metadataList.add(metadata);
		}

		File metadataFile = new File(previewFolder, METADATA_FILE);
		PrintWriter writer = null;

		try {
			writer = new PrintWriter(new FileWriter(metadataFile));
			for(MediaMetadata metadata : metadataList)
				writer.println(metadata.toString());
		}
		catch(Exception e) {
			log(Level.WARNING, "Unable to store metadata file for " + dir.getAbsolutePath() + ": " + e.getMessage());
		}
		finally {
			if(writer != null)
				writer.close();
		}

		return metadataList;
	}

	private List<String> getThumbnailNames(File dir) {
		if(!dir.isDirectory())
			return Collections.emptyList();

		List<String> images = new ArrayList<String>();

		File[] files = dir.listFiles();
		Arrays.sort(files);

		for(File candidate : files) {
			if(isHidden(candidate))
				continue;

			if(candidate.getName().endsWith(DRAFT_IMAGE_EXTENSION)) {
				String originalName = getOriginalName(candidate);
				if(isImageOrVideo(originalName))
					images.add(originalName);
			}
		}

		return images;
	}

	private String getOriginalName(File thumbnailFile) {
		return thumbnailFile.getName().substring(0, thumbnailFile.getName().length() - DRAFT_IMAGE_EXTENSION.length());
	}

	private boolean isImageOrVideo(File file) {
		if(file.isFile() && !isHidden(file))
			return isImage(file.getName()) || isVideo(file.getName());

		return false;
	}

	private boolean isImageOrVideo(String fileName) {
		return isImage(fileName) || isVideo(fileName);
	}

	private boolean isImage(String fileName) {
		String name = fileName.toLowerCase();
		for(String imageExtension : IMAGE_EXTENSIONS) {
			if(name.endsWith(imageExtension))
				return true;
		}

		return false;
	}

	private boolean isVideo(String fileName) {
		String name = fileName.toLowerCase();
		for(String videoExtension : VIDEO_EXTENSIONS) {
			if(name.endsWith(videoExtension))
				return true;
		}

		return false;
	}

	private boolean isHidden(File file) {
		return file.getName().startsWith("."); // same rule for all platforms!
	}

	private void clearLog() {
		synchronized (recentLog) {
			recentLog.setLength(0);
		}
	}

	private void log(Level level, String message) {

		if(log != null)
			log.log(level, message);

		synchronized (recentLog) {
			recentLog.append(new Date());
			recentLog.append(' ');
			recentLog.append(level.getName());
			recentLog.append(": ");
			recentLog.append(message);
			recentLog.append('\n');
		}
	}
}
