package cz.sml.swig.client;

import com.google.gwt.user.client.rpc.AsyncCallback;

import cz.sml.swig.shared.AccessRights;
import cz.sml.swig.shared.AccessRoles;
import cz.sml.swig.shared.FolderMetadata;
import cz.sml.swig.shared.MediaMetadata;
import cz.sml.swig.shared.RoleMapperInfo;

public interface SwigServiceAsync {

	void authorize(String role, String accessKey, boolean persistent, AsyncCallback<Boolean> callback);

	void getAccessibleFolders(String role, String folder, AsyncCallback<FolderMetadata[]> callback);

	void getMediaMetadata(String role, String folder, int flags, AsyncCallback<MediaMetadata[]> callback);

	void isAuthorized(String role, AsyncCallback<Boolean> callback);

	void unauthorize(String role, AsyncCallback<Void> callback);

	void getAccessRoles(AsyncCallback<AccessRoles> asyncCallback);

	void storeAccessRoles(AccessRoles accessRoles, AsyncCallback<AccessRoles> asyncCallback);

	void getAccessRights(String path, AsyncCallback<AccessRights> callback);

	void storeAccessRights(String path, AccessRights accessRights, AsyncCallback<AccessRights> callback);

	void getRoleMapperInfo(AsyncCallback<RoleMapperInfo> callback);

	void storeCustomRoleMapper(String customRoleMapper, AsyncCallback<RoleMapperInfo> asyncCallback);
}
