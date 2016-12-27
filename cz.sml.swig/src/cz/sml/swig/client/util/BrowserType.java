package cz.sml.swig.client.util;

public enum BrowserType {
	STANDARD("Standard Browser"),

	MSIE("Microsoft Internet Explorer"),

	EDGE("Microsoft Edge"),

	FIREFOX("Mozilla Firefox"),

	CHROME("Google Chrome"),

	SAFARI("Safari"),

	OPERA("Opera");

	private final String literal;

	private BrowserType(String literal) {
		this.literal = literal;
	}

	@Override
	public String toString() {
		return literal;
	}

}
