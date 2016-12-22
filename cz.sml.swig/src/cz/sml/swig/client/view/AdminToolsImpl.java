package cz.sml.swig.client.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.gwt.cell.client.CheckboxCell;
import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.cell.client.TextInputCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.http.client.URL;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.uibinder.client.UiTemplate;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;

import cz.sml.swig.shared.AccessRoles;

public class AdminToolsImpl extends Composite implements AdminTools {

	static interface SHtmlTemplates extends SafeHtmlTemplates {
		@Template("<span style='color: red; cursor: default;' title='{0}'>&#9785;</span>")
		SafeHtml scriptError(String message);

		@Template("<span style='color: red; cursor: default;' title='Zpětné mapování URL identifikátoru se liší od interního kódu ({1} x {0})'>&#9746;</span>")
		SafeHtml tokenToRoleError(String original, String inverseMapped);
	}

	private static final SHtmlTemplates shtmlTemplates = GWT.create(SHtmlTemplates.class);

	private static final SafeHtml BAD_SCRIPT = shtmlTemplates.scriptError("Skript pro mapování obsahuje kompilační chyby");

	private static final SafeHtml MAPPING_OK = SafeHtmlUtils.fromTrustedString("<span style='color: green; cursor: default;'>&#9745;</span>");

	private static final SafeHtml ROLE_IS_PUBLIC = SafeHtmlUtils.fromTrustedString("<span style='color: green; cursor: default;'>&#9745;</span>");

	private static AdminToolsUiBinder uiBinder = GWT.create(AdminToolsUiBinder.class);

	private Timer compilator = new Timer() {

		@Override
		public void run() {
			updateTestingRoleMapper();
		}

	};

	private AdminTools.Presenter presenter;

	private AccessRoles accessRoles;

	private String defaultRoleMapper;

	@UiField
	Button addRole;

	@UiField
	Button removeSelectedRoles;

	@UiField
	Button confirmChangesRoles;

	@UiField
	Button cancelChangesRoles;

	@UiField
	CellTable<AccessRoleRecord> accessRolesTable;

	@UiField
	CheckBox useCustomRoleMapper;

	@UiField
	Button confirmChangesRoleMapper;

	@UiField
	Button cancelChangesRoleMapper;

	@UiField
	TextArea customRoleMapper;

	@UiField
	Label mapperCompilationStatus;

	private Map<String, String[]> testMappingCache = new HashMap<String, String[]>();

	private String oldCustomMapperValue;

	private ArrayList<AccessRoleRecord> accessRolesTableRows;

	private String savedCustomRoleMapper;

	private boolean savedUseCustomRoleMapper;

	private static class AccessRoleRecord {
		String name;

		String password;

		boolean marked;

		public AccessRoleRecord(String name, String password) {
			this.name = name;
			this.password = password;
		}

	}

	interface InputTemplate extends SafeHtmlTemplates {
		@Template("<input type=\"text\" value=\"{0}\" tabindex=\"-1\" disabled=\"true\"></input>")
		SafeHtml disabledInput(String value);

		@Template("<input type=\"text\" tabindex=\"-1\" disabled=\"true\"></input>")
		SafeHtml disabledInput();
	}

	private static InputTemplate inputTemplate = GWT.create(InputTemplate.class);

	@UiTemplate("AdminTools.ui.xml")
	interface AdminToolsUiBinder extends UiBinder<Widget, AdminToolsImpl> {
	}

	public AdminToolsImpl() {
		initWidget(uiBinder.createAndBindUi(this));

		final Column<AccessRoleRecord, Boolean> markedColumn = new Column<AccessRoleRecord, Boolean>(new CheckboxCell() {
			@Override
			public void render(Context context, Boolean value, SafeHtmlBuilder sb) {
				AccessRoleRecord record = (AccessRoleRecord) context.getKey();
				if(AccessRoles.ADMIN_ROLE.equals(record.name)) {
					SafeHtmlBuilder fake = new SafeHtmlBuilder();
					super.render(context, value, fake);
					sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
				}
				else
					super.render(context, value, sb);
			}

		}) {

			@Override
			public Boolean getValue(AccessRoleRecord nameAndPassword) {
				return Boolean.valueOf(nameAndPassword.marked);
			}
		};

		accessRolesTable.addColumn(markedColumn);

		markedColumn.setFieldUpdater(new FieldUpdater<AccessRoleRecord, Boolean>() {

			@Override
			public void update(int index, AccessRoleRecord object, Boolean value) {
				if(AccessRoles.ADMIN_ROLE.equals(object.name)) {
					Window.alert("Role 'admin' nemůže být označena ke smazání");
					((CheckboxCell) markedColumn.getCell()).clearViewData(object);
				}
				else {
					object.marked = value.booleanValue();
					enableOrDisableRolesButtons();
				}

				accessRolesTable.redraw();
			}
		});

		final Column<AccessRoleRecord, String> nameColumn = new Column<AccessRoleRecord, String>(new TextInputCell() {
			@Override
			public void render(Context context, String value, SafeHtmlBuilder sb) {
				AccessRoleRecord record = (AccessRoleRecord) context.getKey();
				if(AccessRoles.ADMIN_ROLE.equals(record.name)) {
					SafeHtmlBuilder fake = new SafeHtmlBuilder();
					super.render(context, value, fake);
					if(value != null)
						sb.append(inputTemplate.disabledInput(value));
					else
						sb.append(inputTemplate.disabledInput());
				}
				else
					super.render(context, value, sb);
			}
		}) {

			@Override
			public String getValue(AccessRoleRecord nameAndPassword) {
				return nameAndPassword.name;
			}
		};

		accessRolesTable.addColumn(nameColumn, "Interní kód");

		nameColumn.setFieldUpdater(new FieldUpdater<AccessRoleRecord, String>() {

			@Override
			public void update(int index, AccessRoleRecord object, String value) {
				if(value.length() < 1) {
					Window.alert("Interní kód musí obsahovat alespoň 1 znak");
					((TextInputCell) nameColumn.getCell()).clearViewData(object);
				}
				else {
					boolean exists = false;

					for(AccessRoleRecord record : accessRolesTableRows) {
						if(record == object)
							continue;
						if(value.equals(record.name)) {
							exists = true;
							break;
						}
					}

					if(exists) {
						Window.alert("Tento interní kód již existuje");
						((TextInputCell) nameColumn.getCell()).clearViewData(object);
					}
					else if(AccessRoles.ADMIN_ROLE.equals(object.name)) {
						Window.alert("Role 'admin' nesmí být přejmenována");
						((TextInputCell) nameColumn.getCell()).clearViewData(object);
					}
					else {
						object.name = value;
						enableOrDisableRolesButtons();
					}
				}

				accessRolesTable.redraw();
			}
		});

		final Column<AccessRoleRecord, String> passwordColumn = new Column<AccessRoleRecord, String>(new TextInputCell()) {

			@Override
			public String getValue(AccessRoleRecord nameAndPassword) {
				return nameAndPassword.password;
			}
		};

		accessRolesTable.addColumn(passwordColumn, "Heslo");

		passwordColumn.setFieldUpdater(new FieldUpdater<AccessRoleRecord, String>() {

			@Override
			public void update(int index, AccessRoleRecord object, String value) {
				if(value.length() < 5) {
					Window.alert("Heslo musí obsahovat alespoň 5 znaků");
					((TextInputCell) passwordColumn.getCell()).clearViewData(object);
				}
				else {
					object.password = value;
					enableOrDisableRolesButtons();
				}

				accessRolesTable.redraw();
			}
		});

		accessRolesTable.addColumn(new Column<AccessRoleRecord, SafeHtml>(new SafeHtmlCell()) {

			@Override
			public SafeHtml getValue(AccessRoleRecord nameAndPassword) {
				String err = getTestRoleMapperError();
				if(err != null)
					return BAD_SCRIPT;

				err = getTestRoleMappingError(nameAndPassword.name);
				SafeHtml content = err == null
						? SafeHtmlUtils.fromString(getTestRoleMapping(nameAndPassword.name)[0])
						: shtmlTemplates.scriptError(err);
				return content;
			}
		}, "Prezentační jméno");

		accessRolesTable.addColumn(new Column<AccessRoleRecord, SafeHtml>(new SafeHtmlCell()) {

			@Override
			public SafeHtml getValue(AccessRoleRecord nameAndPassword) {
				String err = getTestRoleMapperError();
				if(err != null)
					return BAD_SCRIPT;

				err = getTestRoleMappingError(nameAndPassword.name);
				if(err == null) {
					String token = getTestRoleMapping(nameAndPassword.name)[1];
					return SafeHtmlUtils.fromTrustedString("<a href=\"#" + URL.encode(token) + "\" title=\"" + "Otevřít roli '"
							+ getTestRoleMapping(nameAndPassword.name)[0] + "' v nové kartě" + "\" target=\"_blank\">#" + SafeHtmlUtils.htmlEscape(token)
							+ "</a>");
				}

				return shtmlTemplates.scriptError(err);
			}
		}, "URL identifikátor");

		accessRolesTable.addColumn(new Column<AccessRoleRecord, SafeHtml>(new SafeHtmlCell()) {

			@Override
			public SafeHtml getValue(AccessRoleRecord nameAndPassword) {
				String err = getTestRoleMapperError();
				if(err != null)
					return BAD_SCRIPT;

				err = getTestRoleMappingError(nameAndPassword.name);
				if(err != null)
					return shtmlTemplates.scriptError(err);

				boolean problem = !nameAndPassword.name.equals(getTestRoleMapping(nameAndPassword.name)[2]);
				return problem
						? shtmlTemplates.tokenToRoleError(nameAndPassword.name, getTestRoleMapping(nameAndPassword.name)[2])
						: MAPPING_OK;
			}
		}, "Stav");

		accessRolesTable.addColumn(new Column<AccessRoleRecord, SafeHtml>(new SafeHtmlCell()) {

			@Override
			public SafeHtml getValue(AccessRoleRecord nameAndPassword) {
				String err = getTestRoleMapperError();
				if(err != null)
					return BAD_SCRIPT;

				err = getTestRoleMappingError(nameAndPassword.name);
				if(err != null)
					return shtmlTemplates.scriptError(err);

				return getTestRoleMapping(nameAndPassword.name)[3] == "true"
						? ROLE_IS_PUBLIC
						: SafeHtmlUtils.EMPTY_SAFE_HTML;
			}
		}, "Veřejné");

		customRoleMapper.getElement().setAttribute("spellcheck", "false");
	}

	@Override
	public void setPresenter(Presenter presenter) {
		this.presenter = presenter;
	}

	@Override
	protected void onAttach() {
		super.onAttach();
		compilator.scheduleRepeating(500);
	}

	@Override
	protected void onDetach() {
		compilator.cancel();
		super.onDetach();
	}

	@Override
	public void setAccessRoles(AccessRoles accessRoles) {
		this.accessRoles = accessRoles;
		refreshAccessRolesTable();
	}

	private void refreshAccessRolesTable() {
		accessRolesTableRows = new ArrayList<AccessRoleRecord>(accessRoles.getSize());
		List<String> sortedNames = accessRoles.getSortedNames();
		Map<String, String> roleMap = accessRoles.getRoleMap();
		for(String name : sortedNames)
			accessRolesTableRows.add(new AccessRoleRecord(name, roleMap.get(name)));
		accessRolesTable.setRowData(accessRolesTableRows);
		enableOrDisableRolesButtons();
	}

	@Override
	public AccessRoles getAccessRoles() {
		AccessRoles newAccessRoles = new AccessRoles();
		for(AccessRoleRecord record : accessRolesTableRows)
			newAccessRoles.setRole(record.name, record.password);

		return newAccessRoles;
	}

	@Override
	public void setUseCustomRoleMapper(boolean useIt) {
		this.useCustomRoleMapper.setValue(Boolean.valueOf(useIt));
		savedUseCustomRoleMapper = useIt;
		updateTestingRoleMapper();
	}

	@Override
	public boolean isUseCustomRoleMapper() {
		return this.useCustomRoleMapper.getValue().booleanValue();
	}

	@UiHandler("useCustomRoleMapper")
	public void onUseCustomRoleMapperChanged(ValueChangeEvent<Boolean> event) {
		updateTestingRoleMapper();
		customRoleMapper.setEnabled(event.getValue().booleanValue());
	}

	@Override
	public void setDefaultRoleMapper(String defaultRoleMapper) {
		this.defaultRoleMapper = defaultRoleMapper;
		updateTestingRoleMapper();
	}

	@Override
	public void setCustomRoleMapper(String customRoleMapper) {
		this.customRoleMapper.setText(customRoleMapper);
		savedCustomRoleMapper = customRoleMapper;
		updateTestingRoleMapper();
	}

	@Override
	public String getCustomRoleMapper() {
		return customRoleMapper.getText();
	}

	@UiHandler("addRole")
	void onAddRoleClicked(ClickEvent event) {
		if(accessRolesTableRows != null) {
			for(int i = 0; i < 10000; i++) {
				String newRoleName = "new_role" + (i > 0
						? i
						: "");
				boolean exists = false;
				for(AccessRoleRecord record : accessRolesTableRows) {
					if(newRoleName.equals(record.name)) {
						exists = true;
						break;
					}
				}

				if(!exists) {
					accessRolesTableRows.add(new AccessRoleRecord(newRoleName, "change_me"));
					accessRolesTable.setRowData(accessRolesTableRows);
					enableOrDisableRolesButtons();
					return;
				}
			}

			Window.alert("Nelze navrhnout nový název role");
		}
	}

	@UiHandler("removeSelectedRoles")
	void onRemoveSelectedRolesClicked(ClickEvent event) {
		if(accessRolesTableRows != null) {
			Iterator<AccessRoleRecord> iterator = accessRolesTableRows.iterator();
			while (iterator.hasNext()) {
				AccessRoleRecord record = iterator.next();
				if(record.marked)
					iterator.remove();
			}

			accessRolesTable.setRowData(accessRolesTableRows);
			enableOrDisableRolesButtons();
		}
	}

	@UiHandler("confirmChangesRoles")
	void onConfirmChangesRolesClicked(ClickEvent event) {
		if(presenter != null)
			presenter.onConfirmChangesRolesClicked();
	}

	@UiHandler("cancelChangesRoles")
	void onCancelChangesRolesClicked(ClickEvent event) {
		refreshAccessRolesTable();
	}

	private void enableOrDisableRolesButtons() {
		if(accessRolesTableRows != null) {
			boolean markedForDeletion = false;

			Map<String, String> editorMap = new HashMap<String, String>(accessRolesTableRows.size());

			for(AccessRoleRecord record : accessRolesTableRows) {
				if(!markedForDeletion && record.marked)
					markedForDeletion = true;

				editorMap.put(record.name, record.password);
			}

			removeSelectedRoles.setEnabled(markedForDeletion);

			boolean changes = !accessRoles.getRoleMap().equals(editorMap);

			confirmChangesRoles.setEnabled(changes);
			cancelChangesRoles.setEnabled(changes);
		}
	}

	@UiHandler("confirmChangesRoleMapper")
	void onConfirmChangesRoleMapperClicked(ClickEvent event) {
		if(presenter != null)
			presenter.onConfirmChangesRoleMapperClicked();
	}

	@UiHandler("cancelChangesRoleMapper")
	void onCancelChangesRoleMapperClicked(ClickEvent event) {
		customRoleMapper.setValue(savedCustomRoleMapper);
		useCustomRoleMapper.setValue(Boolean.valueOf(savedUseCustomRoleMapper), true);
	}

	private void enableOrDisableMapperButtons() {
		boolean changes = savedCustomRoleMapper != null
				&& (savedUseCustomRoleMapper != useCustomRoleMapper.getValue().booleanValue() || useCustomRoleMapper.getValue().booleanValue()
						&& !savedCustomRoleMapper.equals(customRoleMapper.getText()));

		confirmChangesRoleMapper.setEnabled(changes && getTestRoleMapperError() == null);
		cancelChangesRoleMapper.setEnabled(changes);
	}

	private void updateTestingRoleMapper() {
		String newCustomMapperValue = useCustomRoleMapper.getValue().booleanValue()
				? customRoleMapper.getValue()
				: defaultRoleMapper;

		if(newCustomMapperValue.equals(oldCustomMapperValue)
				&& !(savedCustomRoleMapper != null && (savedUseCustomRoleMapper != useCustomRoleMapper.getValue().booleanValue()))) {
			enableOrDisableMapperButtons();
			return;
		}

		oldCustomMapperValue = newCustomMapperValue;
		testMappingCache.clear();
		updateTestingRoleMapper(newCustomMapperValue);
		String error = getTestRoleMapperError();

		if(error != null) {
			mapperCompilationStatus.setText(error);
			mapperCompilationStatus.getElement().getStyle().setColor("red");
		}
		else {
			mapperCompilationStatus.setText(useCustomRoleMapper.getValue().booleanValue()
					? "Skript je syntakticky v pořádku"
					: "Skript není používán");
			mapperCompilationStatus.getElement().getStyle().clearColor();
		}

		accessRolesTable.redraw();
		enableOrDisableMapperButtons();
	}

	private String[] getTestRoleMapping(String roleName) {
		String[] mappings = testMappingCache.get(roleName);
		if(mappings == null) {
			JsArrayString nativeMappings = runTestingRoleMapper(roleName);
			switch (nativeMappings.length()) {
			case 1:
				testMappingCache.put(roleName, mappings = new String[] { nativeMappings.get(0) });
				break;
			case 4:
				testMappingCache.put(roleName,
						mappings = new String[] { nativeMappings.get(0), nativeMappings.get(1), nativeMappings.get(2), nativeMappings.get(3) });
				break;
			default:
				testMappingCache.put(roleName, mappings = new String[] { nativeMappings.get(0) != null
						? nativeMappings.get(0).toString()
						: "Null result" });
			}
		}

		return mappings;
	}

	protected String getTestRoleMappingError(String roleName) {
		String[] mappings = getTestRoleMapping(roleName);

		return mappings.length == 1
				? mappings[0]
				: null;
	}

	private native void updateTestingRoleMapper(String roleMapperJs) /*-{
		try {
			testRoleMapperError = null;
			eval("testRoleMapper = function(roleName) {\n" + roleMapperJs
					+ "\n" + "results = [];\n"
					+ "results[0] = '' + mapRoleToLabel(roleName);\n"
					+ "results[1] = '' + mapRoleToToken(roleName);\n"
					+ "results[2] = '' + mapTokenToRole(results[1]);\n"
					+ "results[3] = '' + isRolePublic(roleName);\n"
					+ "return results\n" + "}");
			testRoleMapper('test');
		} catch (e) {
			testRoleMapperError = "" + e;
		}
	}-*/;

	private native String getTestRoleMapperError() /*-{
		if (typeof testRoleMapperError === 'undefined') {
			return null;
		}

		return testRoleMapperError;
	}-*/;

	private native JsArrayString runTestingRoleMapper(String roleName) /*-{
		try {
			return testRoleMapper(roleName);
		} catch (e) {
			return [ "" + e ];
		}
	}-*/;
}
