package cz.sml.swig.client;

import com.google.gwt.user.client.rpc.RemoteService;
import com.google.gwt.user.client.rpc.RemoteServiceRelativePath;

import cz.sml.swig.shared.AccessDeniedException;
import cz.sml.swig.shared.AccessRights;
import cz.sml.swig.shared.AccessRoles;
import cz.sml.swig.shared.FolderMetadata;
import cz.sml.swig.shared.MediaMetadata;
import cz.sml.swig.shared.RoleMapperInfo;

/**
 * The client side stub for the RPC service.
 */
@RemoteServiceRelativePath("service")
public interface SwigService extends RemoteService {

	boolean isAuthorized(String role);

	boolean authorize(String role, String accessKey, boolean persistent);

	void unauthorize(String role);

	FolderMetadata[] getAccessibleFolders(String role, String folder);

	MediaMetadata[] getMediaMetadata(String role, String folder, int flags) throws AccessDeniedException;

	AccessRoles getAccessRoles() throws Exception;

	AccessRoles storeAccessRoles(AccessRoles accessRoles) throws Exception;

	AccessRights getAccessRights(String path) throws Exception;

	AccessRights storeAccessRights(String path, AccessRights accessRights) throws Exception;

	RoleMapperInfo getRoleMapperInfo() throws Exception;

	RoleMapperInfo storeCustomRoleMapper(String custeomRoleMappe) throws Exception;
}
