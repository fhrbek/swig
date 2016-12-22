package cz.sml.swig.shared;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AccessRights implements Storable {

	public static final String ALL_ROLES = "*";

	private Set<String> roleSet = new HashSet<String>(0);

	public AccessRights() {
	}

	public AccessRights(String serialized) throws StorableFormatException {
		load(serialized);
	}

	public Set<String> getRoles() {
		return Collections.unmodifiableSet(roleSet);
	}

	public boolean hasRole(String name) {
		return roleSet.contains(name);
	}

	public boolean isUniversalAccess() {
		return hasRole(ALL_ROLES);
	}

	public boolean hasOneOfRolesOrIsUniversalAccess(String[] roles) {
		if(isUniversalAccess())
			return true;

		for(String role : roles)
			if(hasRole(role))
				return true;

		return false;
	}

	public void sortNames(List<String> names) {
		// show universal role as first to improve human readability of the list
		Collections.sort(names, new Comparator<String>() {

			@Override
			public int compare(String name1, String name2) {
				int result = name1.compareTo(name2);
				if(result != 0) {
					if(ALL_ROLES.equals(name1))
						return -1;
					if(ALL_ROLES.equals(name2))
						return 1;
				}

				return result;
			}

		});
	}

	public void addRole(String name) {
		roleSet.add(name);
	}

	public void removeRole(String name) {
		roleSet.remove(name);
	}

	@Override
	public String store() {
		StringBuilder bld = new StringBuilder();
		List<String> names = new ArrayList<String>(roleSet);

		sortNames(names);

		boolean first = true;
		for(String name : names) {
			if(first)
				first = false;
			else
				bld.append(',');

			bld.append(name);

			if(ALL_ROLES.equals(name)) // we don't need to go on if universal
										// access has been written (and it's
										// always the first one)
				break;
		}

		return bld.toString();
	}

	@Override
	public void load(String serialized) throws StorableFormatException {
		String[] lines = serialized.split("\n");
		roleSet = new HashSet<String>(lines.length);
		for(String line : lines) {
			if(line.length() == 0)
				continue;

			if(!roleSet.isEmpty())
				throw new StorableFormatException("Unexpected line: " + line);

			String[] roles = line.split(",");
			if(roles.length > 0) {
				for(String role : roles)
					roleSet.add(role);
			}
		}
	}

	public int getSize() {
		return roleSet.size();
	}

	public List<String> getSortedNames() {
		List<String> names = new ArrayList<String>(roleSet);
		sortNames(names);
		return names;
	}

}
