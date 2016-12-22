/*******************************************************************************
 * Copyright (c) 2011, Mettler Toledo Ltd., Czech Republic (MT)
 * The code, documentation and other materials contained herein
 * are the sole and exclusive property of MT and may
 * not be disclosed, used, modified, copied or distributed without
 * prior written consent or license from MT
 *******************************************************************************/
package cz.sml.swig.client.view;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeUri;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiTemplate;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

public class UnsupportedBrowserViewImpl extends Composite implements UnsupportedBrowserView {

	@UiTemplate("UnsupportedBrowserView.ui.xml")
	interface UnsupportedBrowserUiBinder extends UiBinder<Widget, UnsupportedBrowserViewImpl> {
	}

	private static UnsupportedBrowserUiBinder uiBinder = GWT.create(UnsupportedBrowserUiBinder.class);

	static interface SHtmlTemplates extends SafeHtmlTemplates {
		@Template("Litujeme, ale tato konfigurace prohlížeče není aplikací podporována. Spusťte galerii samostatně v <a href='{0}' target='_blank'>nové záložce</a>, nebo celou aplikaci v jednom z níže uvedených prohlížečů, které jsou k dispozici ke stažení zdarma.")
		SafeHtml newTabHint(SafeUri uri);
	}

	static SHtmlTemplates sHtmlTemplates = GWT.create(SHtmlTemplates.class);

	@UiField
	Label detectedBrowser;

	@UiField
	HTML message;

	public UnsupportedBrowserViewImpl() {
		initWidget(uiBinder.createAndBindUi(this));
	}

	@Override
	public Widget asWidget() {
		return this;
	}

	@Override
	public void setDetectedBrowser(String browser) {
		detectedBrowser.setText(browser);
	}

	@Override
	public void setNewTabHint(SafeUri uri) {
		message.setHTML(sHtmlTemplates.newTabHint(uri));
	}
}
