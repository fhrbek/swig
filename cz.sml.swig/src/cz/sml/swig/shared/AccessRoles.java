package cz.sml.swig.shared;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AccessRoles implements Storable {

	public static final String ADMIN_ROLE = "admin";

	public static final String DEFAULT_ADMIN_PASSWORD = "admin";

	private Map<String, String> roleMap = new HashMap<String, String>(0);

	public AccessRoles() {
	}

	public AccessRoles(String serialized) throws StorableFormatException {
		load(serialized);
	}

	public Map<String, String> getRoleMap() {
		return Collections.unmodifiableMap(roleMap);
	}

	public boolean hasRole(String name) {
		return roleMap.containsKey(name);
	}

	public void sortNames(List<String> names) {
		// show admin as first to improve human readability of the list
		Collections.sort(names, new Comparator<String>() {

			@Override
			public int compare(String name1, String name2) {
				int result = name1.compareTo(name2);
				if(result != 0) {
					if(ADMIN_ROLE.equals(name1))
						return -1;
					if(ADMIN_ROLE.equals(name2))
						return 1;
				}

				return result;
			}

		});
	}

	public void setRole(String name, String password) {
		roleMap.put(name, password);
	}

	@Override
	public String store() {
		StringBuilder bld = new StringBuilder();
		List<String> names = new ArrayList<String>(roleMap.keySet());

		sortNames(names);

		boolean first = true;
		for(String name : names) {
			if(first)
				first = false;
			else
				bld.append('\n');

			bld.append(name);
			bld.append(',');
			bld.append(roleMap.get(name));
		}

		return bld.toString();
	}

	@Override
	public void load(String serialized) throws StorableFormatException {
		String[] lines = serialized.split("\n");
		roleMap = new HashMap<String, String>(lines.length);
		for(String line : lines) {
			if(line.length() == 0)
				continue;

			String[] nameAndPassword = line.split(",");
			if(nameAndPassword.length != 2)
				throw new StorableFormatException("Invalid line: " + line);

			roleMap.put(nameAndPassword[0], nameAndPassword[1]);
		}
	}

	public int getSize() {
		return roleMap.size();
	}

	public List<String> getSortedNames() {
		List<String> names = new ArrayList<String>(roleMap.keySet());
		sortNames(names);
		return names;
	}

}
