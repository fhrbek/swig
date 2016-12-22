/*******************************************************************************
 * Copyright (c) 2011, Mettler Toledo Ltd., Czech Republic (MT)
 * The code, documentation and other materials contained herein
 * are the sole and exclusive property of MT and may
 * not be disclosed, used, modified, copied or distributed without
 * prior written consent or license from MT
 *******************************************************************************/
package cz.sml.swig.client.view;

import com.google.gwt.safehtml.shared.SafeUri;
import com.google.gwt.user.client.ui.Widget;

public interface UnsupportedBrowserView {

	Widget asWidget();

	void setDetectedBrowser(String browser);

	void setNewTabHint(SafeUri uri);
}
