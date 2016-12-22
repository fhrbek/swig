package cz.sml.swig.client.util;

public class BrowserVersion {

	protected static final BrowserType BROWSER_TYPE_EDEFAULT = BrowserType.STANDARD;

	protected BrowserType browserType = BROWSER_TYPE_EDEFAULT;

	protected static final int BROWSER_MAJOR_VERSION_EDEFAULT = 0;

	protected int browserMajorVersion = BROWSER_MAJOR_VERSION_EDEFAULT;

	protected static final int BROWSER_MINOR_VERSION_EDEFAULT = 0;

	protected int browserMinorVersion = BROWSER_MINOR_VERSION_EDEFAULT;

	protected static final int BROWSER_DOCUMENT_MODE_EDEFAULT = 0;

	protected int browserDocumentMode = BROWSER_DOCUMENT_MODE_EDEFAULT;

	public BrowserType getBrowserType() {
		return browserType;
	}

	public void setBrowserType(BrowserType newBrowserType) {
		browserType = newBrowserType == null
				? BROWSER_TYPE_EDEFAULT
				: newBrowserType;
	}

	public int getBrowserMajorVersion() {
		return browserMajorVersion;
	}

	public void setBrowserMajorVersion(int newBrowserMajorVersion) {
		browserMajorVersion = newBrowserMajorVersion;
	}

	public int getBrowserMinorVersion() {
		return browserMinorVersion;
	}

	public void setBrowserMinorVersion(int newBrowserMinorVersion) {
		browserMinorVersion = newBrowserMinorVersion;
	}

	public int getBrowserDocumentMode() {
		return browserDocumentMode;
	}

	public void setBrowserDocumentMode(int newBrowserDocumentMode) {
		browserDocumentMode = newBrowserDocumentMode;
	}

	public String toString() {
		StringBuffer result = new StringBuffer();
		result.append(browserType);
		result.append(' ');
		result.append(browserMajorVersion);
		result.append('.');
		result.append(browserMinorVersion);

		if(browserDocumentMode != BROWSER_DOCUMENT_MODE_EDEFAULT) {
			result.append(" [mode IE");
			result.append(browserDocumentMode);
			result.append("]");
		}

		return result.toString();
	}
}
