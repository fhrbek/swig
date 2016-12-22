package cz.sml.swig.shared;

import com.google.gwt.user.client.rpc.IsSerializable;

public class MediaMetadata implements IsSerializable {

	public enum Type {
		IMAGE, VIDEO, ARCHIVE
	};

	public static final int FLAG_DEFAULT = 0x00;

	public static final int FLAG_PREVIEW_DIMENSIONS = 0x01;

	public static final int FLAG_FILE_SIZE = 0x02;

	public static final int FLAG_ARCHIVE_SIZE = 0x04;

	public static final int FLAG_VIDEO_URL = 0x08;

	public static final String ARCHIVE_METADATA_NAME = ".archive";

	private Type type;

	private String name;

	private int previewWidth = -1;

	private int previewHeight = -1;

	private long originalFileSize = -1;

	private String videoUrl;

	MediaMetadata() {

	}

	public MediaMetadata(Type type, String name) {
		this.type = type;
		this.name = name;
	}

	public int getPreviewWidth() {
		return previewWidth;
	}

	public void setPreviewWidth(int previewWidth) {
		this.previewWidth = previewWidth;
	}

	public int getPreviewHeight() {
		return previewHeight;
	}

	public void setPreviewHeight(int previewHeight) {
		this.previewHeight = previewHeight;
	}

	public long getOriginalFileSize() {
		return originalFileSize;
	}

	public void setOriginalFileSize(long originalFileSize) {
		this.originalFileSize = originalFileSize;
	}

	public String getVideoUrl() {
		return videoUrl;
	}

	public void setVideoUrl(String videoUrl) {
		this.videoUrl = videoUrl;
	}

	public Type getType() {
		return type;
	}

	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder(type.toString());
		builder.append(',');
		builder.append(name);
		builder.append(',');
		builder.append(previewWidth);
		builder.append(',');
		builder.append(previewHeight);
		builder.append(',');
		builder.append(originalFileSize);
		builder.append(',');
		builder.append(videoUrl);

		return builder.toString();
	}

	public static MediaMetadata fromString(String serialized, int flags) {
		String[] tokens = serialized.split(",");
		if(tokens.length != 6)
			throw new IllegalArgumentException("Invalid serialized form of ImageMetadata: " + serialized);

		MediaMetadata metadata = new MediaMetadata(Type.valueOf(tokens[0]), tokens[1]);

		if((flags & FLAG_PREVIEW_DIMENSIONS) > 0) {
			metadata.setPreviewWidth(Integer.valueOf(tokens[2]).intValue());
			metadata.setPreviewHeight(Integer.valueOf(tokens[3]).intValue());
		}

		if((flags & FLAG_FILE_SIZE) > 0)
			metadata.setOriginalFileSize(Long.valueOf(tokens[4]).intValue());

		if((flags & FLAG_VIDEO_URL) > 0)
			metadata.setVideoUrl(tokens[5]);

		return metadata;
	}

}
