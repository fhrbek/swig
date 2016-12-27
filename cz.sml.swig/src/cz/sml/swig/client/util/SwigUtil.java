package cz.sml.swig.client.util;

import com.google.gwt.core.client.GWT;
import com.google.gwt.regexp.shared.MatchResult;
import com.google.gwt.regexp.shared.RegExp;
import com.google.gwt.user.client.Window;

/**
 * @author filip.hrbek@gmail.com
 */
public class SwigUtil {

	private static Boolean paranoidBrowser;

	public static boolean isParanoidBrowser() {
		if(paranoidBrowser == null)
			paranoidBrowser = Boolean.valueOf(BrowserType.CHROME == SwigUtil.detectBrowserVersion().getBrowserType());

		return paranoidBrowser.booleanValue();
	}

	public static BrowserVersion detectBrowserVersion() {
		BrowserVersion browserVersion = new BrowserVersion();
		if(GWT.isClient()) {
			String agent = Window.Navigator.getUserAgent();
			RegExp regExp;
			MatchResult match;

			// Test Firefox
			regExp = RegExp.compile("Firefox[\\/\\s](\\d+)\\.(\\d+)");
			match = regExp.exec(agent);
			if(match != null) {
				browserVersion.setBrowserType(BrowserType.FIREFOX);
				browserVersion.setBrowserMajorVersion(Integer.valueOf(match.getGroup(1)));
				browserVersion.setBrowserMinorVersion(Integer.valueOf(match.getGroup(2)));

				return browserVersion;
			}

			// Test Opera
			regExp = RegExp.compile("Opera[\\/\\s].*\\sVersion\\/(\\d+)\\.(\\d+)");
			match = regExp.exec(agent);
			if(match != null) {
				browserVersion.setBrowserType(BrowserType.OPERA);
				browserVersion.setBrowserMajorVersion(Integer.valueOf(match.getGroup(1)));
				browserVersion.setBrowserMinorVersion(Integer.valueOf(match.getGroup(2)));

				return browserVersion;
			}

			// Test MSIE
			regExp = RegExp.compile("MSIE (\\d+)\\.(\\d+);");
			match = regExp.exec(agent);
			if(match != null) {
				browserVersion.setBrowserType(BrowserType.MSIE);
				browserVersion.setBrowserMajorVersion(Integer.valueOf(match.getGroup(1)));
				browserVersion.setBrowserMinorVersion(Integer.valueOf(match.getGroup(2)));
				browserVersion.setBrowserDocumentMode(getDocumentMode());

				return browserVersion;
			}

			// Test MSIE 11+
			regExp = RegExp.compile("Mozilla/.*\\(Windows .*; rv:(\\d+)\\.(\\d+)\\) like Gecko");
			match = regExp.exec(agent);
			if(match != null) {
				browserVersion.setBrowserType(BrowserType.MSIE);
				browserVersion.setBrowserMajorVersion(Integer.valueOf(match.getGroup(1)));
				browserVersion.setBrowserMinorVersion(Integer.valueOf(match.getGroup(2)));
				browserVersion.setBrowserDocumentMode(getDocumentMode());

				return browserVersion;
			}

			// Test Edge
			regExp = RegExp.compile("<WebKit Rev> Edge");
			match = regExp.exec(agent);
			if(match != null) {
				browserVersion.setBrowserType(BrowserType.EDGE);
				browserVersion.setBrowserMajorVersion(0);
				browserVersion.setBrowserMinorVersion(0);
				browserVersion.setBrowserDocumentMode(getDocumentMode());

				return browserVersion;
			}

			// Test Chrome
			regExp = RegExp.compile("Chrome[\\/\\s](\\d+)\\.(\\d+)");
			match = regExp.exec(agent);
			if(match != null) {
				browserVersion.setBrowserType(BrowserType.CHROME);
				browserVersion.setBrowserMajorVersion(Integer.valueOf(match.getGroup(1)));
				browserVersion.setBrowserMinorVersion(Integer.valueOf(match.getGroup(2)));

				return browserVersion;
			}

			// Test Safari
			regExp = RegExp.compile("Safari[\\/\\s](\\d+)\\.(\\d+)");
			match = regExp.exec(agent);
			if(match != null) {
				browserVersion.setBrowserType(BrowserType.SAFARI);
				browserVersion.setBrowserMajorVersion(Integer.valueOf(match.getGroup(1)));
				browserVersion.setBrowserMinorVersion(Integer.valueOf(match.getGroup(2)));

				return browserVersion;
			}
		}

		browserVersion.setBrowserType(BrowserType.STANDARD);
		browserVersion.setBrowserMajorVersion(0);
		browserVersion.setBrowserMinorVersion(0);

		return browserVersion;
	}

	private static native int getDocumentMode() /*-{
		return $doc.documentMode;
	}-*/;

	public static boolean isSupported(BrowserVersion browserVersion) {
		// @fmtOff
		return (browserVersion.getBrowserType() == BrowserType.FIREFOX && browserVersion.getBrowserMajorVersion() >= 16 ||
				browserVersion.getBrowserType() == BrowserType.CHROME && browserVersion.getBrowserMajorVersion() >= 26 ||
				browserVersion.getBrowserType() == BrowserType.SAFARI && browserVersion.getBrowserMajorVersion() >= 6 && browserVersion.getBrowserMinorVersion() >= 1 ||
				browserVersion.getBrowserType() == BrowserType.OPERA && browserVersion.getBrowserMajorVersion() >= 12 && browserVersion.getBrowserMinorVersion() >= 1 ||
				browserVersion.getBrowserType() == BrowserType.MSIE && browserVersion.getBrowserMajorVersion() >= 10 ||
				browserVersion.getBrowserType() == BrowserType.EDGE);
		// @fmtOn
	}

	public static boolean couldRunOutsideIframe(BrowserVersion browserVersion) {
		return browserVersion.getBrowserType() == BrowserType.MSIE && browserVersion.getBrowserMajorVersion() >= 9;
	}
}
