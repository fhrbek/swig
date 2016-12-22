package cz.sml.swig.server;

import com.google.gwt.user.server.rpc.RemoteServiceServlet;

import cz.sml.swig.client.SwigService;
import cz.sml.swig.shared.AccessDeniedException;
import cz.sml.swig.shared.AccessRights;
import cz.sml.swig.shared.AccessRoles;
import cz.sml.swig.shared.FolderMetadata;
import cz.sml.swig.shared.MediaMetadata;
import cz.sml.swig.shared.RoleMapperInfo;

public class SwigServiceImpl extends RemoteServiceServlet implements SwigService {

	private static final long serialVersionUID = -5193272765070875263L;

	// This is a workaround for stupid iOS 6 which caches POST requests if the
	// following header is missing
	@Override
	protected void onBeforeRequestDeserialized(String serializedRequest) {
		getThreadLocalResponse().setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
	}

	@Override
	public boolean isAuthorized(String role) {
		return ImageScanner.isAuthorized(getThreadLocalRequest(), role);
	}

	@Override
	public boolean authorize(String role, String accessKey, boolean persistent) {
		return ImageScanner.authorize(getThreadLocalRequest(), getThreadLocalResponse(), role, accessKey, persistent);
	}

	@Override
	public void unauthorize(String role) {
		ImageScanner.unauthorize(getThreadLocalRequest(), getThreadLocalResponse(), role);
	}

	@Override
	public FolderMetadata[] getAccessibleFolders(String role, String folder) {
		return ImageScanner.getAccessibleFolders(role, folder);
	}

	@Override
	public MediaMetadata[] getMediaMetadata(String role, String folder, int flags) throws AccessDeniedException {
		return ImageScanner.getMediaMetadata(role, getThreadLocalRequest(), folder, flags);
	}

	@Override
	public AccessRoles getAccessRoles() throws Exception {
		return ImageScanner.getAccessRoles();
	}

	@Override
	public AccessRoles storeAccessRoles(AccessRoles accessRoles) throws Exception {
		return ImageScanner.storeAccessRoles(accessRoles);
	}

	@Override
	public AccessRights getAccessRights(String path) throws Exception {
		return ImageScanner.getAccessRights(path);
	}

	@Override
	public AccessRights storeAccessRights(String path, AccessRights accessRights) throws Exception {
		return ImageScanner.storeAccessRights(path, accessRights);
	}

	@Override
	public RoleMapperInfo getRoleMapperInfo() throws Exception {
		String defaultRoleMapper = RoleMapperServlet.getDefaultRoleMapper();
		String customRoleMapper = RoleMapperServlet.getCustomRoleMapper();
		boolean useCustomRoleMapper = RoleMapperServlet.customRoleMapperExists();

		return new RoleMapperInfo(defaultRoleMapper, customRoleMapper, useCustomRoleMapper);
	}

	@Override
	public RoleMapperInfo storeCustomRoleMapper(String customRoleMapper) throws Exception {
		RoleMapperServlet.storeCustomRoleMapper(customRoleMapper);

		return getRoleMapperInfo();
	}

}
