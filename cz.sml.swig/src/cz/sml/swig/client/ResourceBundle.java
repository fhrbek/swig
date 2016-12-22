package cz.sml.swig.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.ImageResource;

public interface ResourceBundle extends ClientBundle {

	public static ResourceBundle INSTANCE = GWT.create(ResourceBundle.class);

	public interface IconStyle extends CssResource {
		String folderIcon();

		String imageIcon();

		String videoIcon();

		String imageAndVideoIcon();
	}

	@Source("css/iconStyle.css")
	IconStyle iconStyle();

	@Source("images/folder.png")
	ImageResource folderIcon();

	@Source("images/image.png")
	ImageResource imageIcon();

	@Source("images/video.png")
	ImageResource videoIcon();

	@Source("images/imageAndVideo.png")
	ImageResource imageAndVideoIcon();

	@Source("images/browser.logo.chrome.png")
	ImageResource chrome();

	@Source("images/browser.logo.firefox.png")
	ImageResource firefox();

	@Source("images/browser.logo.opera.png")
	ImageResource opera();

	@Source("images/browser.logo.safari.png")
	ImageResource safari();
}
