package cz.sml.swig.shared;

import com.google.gwt.user.client.rpc.IsSerializable;

public class FolderMetadata implements IsSerializable {

	public enum FolderContentType {
		FOLDER, IMAGE, VIDEO, IMAGE_AND_VIDEO
	}

	private String name;

	private int folderCount = 0;

	private int imageCount = 0;

	private int videoCount = 0;

	FolderMetadata() {

	}

	public FolderMetadata(String name, int folderCount, int imageCount, int viedoCount) {
		this.name = name;
		this.folderCount = folderCount;
		this.imageCount = imageCount;
		this.videoCount = viedoCount;
	}

	public String getName() {
		return name;
	}

	public int getFolderCount() {
		return folderCount;
	}

	public int getImageCount() {
		return imageCount;
	}

	public int getVideoCount() {
		return videoCount;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder(name);
		builder.append(' ');
		builder.append(getContentSummary());

		return builder.toString();
	}

	public String getContentSummary() {
		StringBuilder builder = new StringBuilder();
		if(folderCount != 0) {
			builder.append(folderCount);
			builder.append(' ');
			builder.append(plural("složka", folderCount));
		}

		if(imageCount != 0) {
			if(folderCount != 0)
				builder.append(", ");
			builder.append(imageCount);
			builder.append(' ');
			builder.append(plural("obrázek", imageCount));
		}

		if(videoCount != 0) {
			if(folderCount != 0 || imageCount != 0)
				builder.append(", ");
			builder.append(videoCount);
			builder.append(' ');
			builder.append(plural("klip", videoCount));
		}

		if(folderCount == 0 && imageCount == 0 && videoCount == 0)
			builder.append("prázdná složka");

		return builder.toString();
	}

	public FolderContentType getContentType() {
		if(imageCount > 0 && videoCount > 0)
			return FolderContentType.IMAGE_AND_VIDEO;

		if(imageCount > 0)
			return FolderContentType.IMAGE;

		if(videoCount > 0)
			return FolderContentType.VIDEO;

		return FolderContentType.FOLDER;
	}

	private String plural(String noun, int count) {
		if(count >= 2 && count <= 4) {
			if("složka".equals(noun))
				return "složky";
			if("obrázek".equals(noun))
				return "obrázky";
			if("klip".equals(noun))
				return "klipy";
		}
		else if(count > 4) {
			if("složka".equals(noun))
				return "složek";
			if("obrázek".equals(noun))
				return "obrázků";
			if("klip".equals(noun))
				return "klipů";
		}

		return noun;
	}

}
