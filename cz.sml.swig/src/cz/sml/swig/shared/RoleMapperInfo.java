package cz.sml.swig.shared;

import com.google.gwt.user.client.rpc.IsSerializable;

public class RoleMapperInfo implements IsSerializable {

	private String defaultRoleMapper;

	private String customRoleMapper;

	private boolean useCustomRoleMapper;

	RoleMapperInfo() {
		// for deserialization
	}

	public RoleMapperInfo(String defaultRoleMapper, String customRoleMapper, boolean useCustomRoleMapper) {
		this.defaultRoleMapper = defaultRoleMapper;
		this.customRoleMapper = customRoleMapper;
		this.useCustomRoleMapper = useCustomRoleMapper;
	}

	public String getDefaultRoleMapper() {
		return defaultRoleMapper;
	}

	public String getCustomRoleMapper() {
		return customRoleMapper;
	}

	public boolean isUseCustomRoleMapper() {
		return useCustomRoleMapper;
	}
}
