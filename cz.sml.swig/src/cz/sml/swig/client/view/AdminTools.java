package cz.sml.swig.client.view;

import com.google.gwt.user.client.ui.Widget;

import cz.sml.swig.shared.AccessRoles;

public interface AdminTools {

	public interface Presenter extends cz.sml.swig.client.presenter.Presenter {
		void setAccessRoles(AccessRoles accessRoles);

		AccessRoles getAccessRoles();

		void setUseCustomRoleMapper(boolean useIt);

		boolean isUseCustomRoleMapper();

		void setDefaultRoleMapper(String defaultRoleMapper);

		void setCustomRoleMapper(String customRoleMapper);

		String getCustomRoleMapper();

		void onConfirmChangesRolesClicked();

		void onConfirmChangesRoleMapperClicked();
	}

	void setPresenter(Presenter presenter);

	Widget asWidget();

	void setAccessRoles(AccessRoles accessRoles);

	AccessRoles getAccessRoles();

	void setUseCustomRoleMapper(boolean useIt);

	boolean isUseCustomRoleMapper();

	void setDefaultRoleMapper(String defaultRoleMapper);

	void setCustomRoleMapper(String customRoleMapper);

	String getCustomRoleMapper();
}
