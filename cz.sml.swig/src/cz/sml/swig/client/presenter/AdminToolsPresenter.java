package cz.sml.swig.client.presenter;

import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.HasWidgets;

import cz.sml.swig.client.SwigServiceAsync;
import cz.sml.swig.client.view.AdminTools;
import cz.sml.swig.shared.AccessRoles;
import cz.sml.swig.shared.RoleMapperInfo;

public class AdminToolsPresenter implements cz.sml.swig.client.view.AdminTools.Presenter {

	private AdminTools adminTools;

	private SwigServiceAsync swigService;

	public AdminToolsPresenter(AdminTools adminTools, SwigServiceAsync swigService) {
		this.adminTools = adminTools;
		this.swigService = swigService;
	}

	@Override
	public void go(HasWidgets container) {
		container.clear();
		container.add(adminTools.asWidget());
	}

	@Override
	public void setAccessRoles(AccessRoles accessRoles) {
		adminTools.setAccessRoles(accessRoles);
	}

	@Override
	public AccessRoles getAccessRoles() {
		return adminTools.getAccessRoles();
	}

	@Override
	public void setUseCustomRoleMapper(boolean useIt) {
		adminTools.setUseCustomRoleMapper(useIt);
	}

	@Override
	public boolean isUseCustomRoleMapper() {
		return adminTools.isUseCustomRoleMapper();
	}

	@Override
	public void setDefaultRoleMapper(String defaultRoleMapper) {
		adminTools.setDefaultRoleMapper(defaultRoleMapper);
	}

	@Override
	public void setCustomRoleMapper(String customRoleMapper) {
		adminTools.setCustomRoleMapper(customRoleMapper);
	}

	@Override
	public String getCustomRoleMapper() {
		return adminTools.getCustomRoleMapper();
	}

	@Override
	public void onConfirmChangesRolesClicked() {
		swigService.storeAccessRoles(adminTools.getAccessRoles(), new AsyncCallback<AccessRoles>() {

			@Override
			public void onFailure(Throwable caught) {
				Window.alert("Nepodařilo se uložit změny!");
			}

			@Override
			public void onSuccess(AccessRoles accessRoles) {
				adminTools.setAccessRoles(accessRoles);
			}

		});
	}

	@Override
	public void onConfirmChangesRoleMapperClicked() {
		swigService.storeCustomRoleMapper(adminTools.isUseCustomRoleMapper()
				? adminTools.getCustomRoleMapper()
				: null, new AsyncCallback<RoleMapperInfo>() {

			@Override
			public void onFailure(Throwable caught) {
				Window.alert("Nepodařilo se uložit změny!");
			}

			@Override
			public void onSuccess(RoleMapperInfo roleMapperInfo) {
				adminTools.setUseCustomRoleMapper(roleMapperInfo.isUseCustomRoleMapper());
				adminTools.setCustomRoleMapper(roleMapperInfo.getCustomRoleMapper());
				adminTools.setDefaultRoleMapper(roleMapperInfo.getDefaultRoleMapper());
			}

		});
	}

}
