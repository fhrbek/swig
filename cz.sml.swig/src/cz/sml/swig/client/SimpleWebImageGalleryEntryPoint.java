package cz.sml.swig.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gwt.animation.client.Animation;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.IFrameElement;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.BorderStyle;
import com.google.gwt.dom.client.Style.Cursor;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.dom.client.Style.Position;
import com.google.gwt.dom.client.Style.TextAlign;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.dom.client.Style.Visibility;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.HasClickHandlers;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.logical.shared.AttachEvent;
import com.google.gwt.event.logical.shared.AttachEvent.Handler;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.http.client.URL;
import com.google.gwt.regexp.shared.MatchResult;
import com.google.gwt.regexp.shared.RegExp;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.safehtml.shared.UriUtils;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.HasWidgets;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.RootLayoutPanel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

import cz.sml.swig.client.presenter.AdminToolsPresenter;
import cz.sml.swig.client.util.BrowserVersion;
import cz.sml.swig.client.util.SwigUtil;
import cz.sml.swig.client.view.AdminTools;
import cz.sml.swig.client.view.AdminToolsImpl;
import cz.sml.swig.client.view.UnsupportedBrowserView;
import cz.sml.swig.client.view.UnsupportedBrowserViewImpl;
import cz.sml.swig.shared.AccessDeniedException;
import cz.sml.swig.shared.AccessRights;
import cz.sml.swig.shared.AccessRoles;
import cz.sml.swig.shared.Constants;
import cz.sml.swig.shared.FolderMetadata;
import cz.sml.swig.shared.MediaMetadata;
import cz.sml.swig.shared.RoleMapperInfo;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class SimpleWebImageGalleryEntryPoint implements EntryPoint {

	private static final int PLAYER_IMAGE_OFFSET = 30;

	private static final int PLAYER_EXTRA_WIDTH = 2 * PLAYER_IMAGE_OFFSET;

	private static final int PLAYER_EXTRA_HEIGHT = 2 * PLAYER_IMAGE_OFFSET;

	private static final int PLAYER_MINIMUM_IMAGE_SIZE = 150;

	private static final int PLAYER_MINIMUM_MARGIN = 10;

	private static final int ANIMATION_DURATION = 300;

	private static final int PLAYER_NAVIGATION_TOP_OFFSET = 15;

	private static final int PLAYER_CONTROL_BUTTON_GAP = 20;

	private static final int PLAYER_CONTROL_PANEL_CORRECTION = 5;

	private static final int CLOSE_BUTTON_OFFSET = 5;

	private static final String SPECIAL_PATH_TOOLS = ":tools";

	/**
	 * Create a remote service proxy to talk to the server-side service.
	 */
	private final SwigServiceAsync swigService = GWT.create(SwigService.class);

	private boolean playing;

	private AdminTools adminTools;

	private AdminToolsPresenter adminToolsPresenter;

	/**
	 * This is the entry point method.
	 */
	@Override
	public void onModuleLoad() {
		BrowserVersion browserVersion = SwigUtil.detectBrowserVersion();

		if(!SwigUtil.isSupported(browserVersion)) {
			UnsupportedBrowserView view = new UnsupportedBrowserViewImpl();
			view.setDetectedBrowser(browserVersion.toString());

			if(SwigUtil.couldRunOutsideIframe(browserVersion))
				view.setNewTabHint(UriUtils.fromTrustedString(Window.Location.getHref()));
			RootLayoutPanel.get().add(view.asWidget());
			return;
		}

		ResourceBundle.INSTANCE.iconStyle().ensureInjected();
		String roleToken = URL.decode(Window.Location.getHash());
		if(roleToken != null && roleToken.length() > 0 && roleToken.charAt(0) == '#')
			roleToken = roleToken.substring(1);

		String[] roleAndPath = parseToken(roleToken);
		render(mapTokenToRole(roleAndPath[0]), roleAndPath[1]);

		History.addValueChangeHandler(new ValueChangeHandler<String>() {

			@Override
			public void onValueChange(ValueChangeEvent<String> event) {
				String[] roleAndPath = parseToken(URL.decode(event.getValue()));
				render(mapTokenToRole(roleAndPath[0]), roleAndPath[1]);
			}

		});
	}

	private String[] parseToken(String token) {
		int pos = token != null
				? token.indexOf('/')
				: -1;

		if(pos != -1)
			return new String[] { token.substring(0, pos), token.substring(pos + 1) };

		pos = token != null
				? token.indexOf(':')
				: -1;

		return pos != -1
				? new String[] { token.substring(0, pos), token.substring(pos) }
				: new String[] { token, "" };
	}

	private void render(final String role, final String path) {
		if(role == null || role.trim().length() == 0) {
			renderPublicRoles();
			return;
		}

		swigService.isAuthorized(role, new AsyncCallback<Boolean>() {

			@Override
			public void onFailure(Throwable caught) {
				error(caught);
			}

			@Override
			public void onSuccess(Boolean authorized) {
				if(!authorized)
					renderAuthorizationScreen(role, path, false, null);
				else
					renderRoleContent(role, path);
			}

		});
	}

	private void renderPublicRoles() {
		final Panel rootPanel = RootPanel.get();
		rootPanel.clear();

		swigService.getAccessRoles(new AsyncCallback<AccessRoles>() {

			@Override
			public void onFailure(Throwable caught) {
				error("Nepodařilo se načíst seznam přístupových rolí: " + caught.getMessage());

			}

			@Override
			public void onSuccess(AccessRoles accessRoles) {
				VerticalPanel layout = new VerticalPanel();
				layout.getElement().getStyle().setWidth(100.0, Unit.PCT);
				layout.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);

				rootPanel.add(layout);

				List<String> roleNames = new ArrayList<String>(accessRoles.getRoleMap().keySet());
				Map<String, String> publicRolesMap = new TreeMap<String, String>();

				for(String roleName : roleNames) {
					if(isRolePublic(roleName)) {
						publicRolesMap.put(mapRoleToLabel(roleName), mapRoleToToken(roleName));
					}
				}

				if(publicRolesMap.size() > 0) {
					Label label = new Label("Vyberte přístupový kód, ke kterému Vám bylo přiděleno heslo");
					label.addStyleName("heading");

					layout.add(label);

					for(Map.Entry<String, String> entry : publicRolesMap.entrySet()) {
						Anchor roleLink = new Anchor(entry.getKey());
						roleLink.addStyleName("galleryLink");
						roleLink.setHref("#" + entry.getValue());

						layout.add(roleLink);
					}
				}
				else {
					Label label = new Label("Nejsou k dispozici žádné veřejné přístupové kódy");
					label.addStyleName("heading");

					layout.add(label);
				}
			}
		});
	}

	private void renderAuthorizationScreen(final String role, final String path, final boolean checkPersistent, String errorMessage) {
		Panel rootPanel = RootPanel.get();
		rootPanel.clear();

		VerticalPanel layout = new VerticalPanel();
		layout.getElement().getStyle().setWidth(100.0, Unit.PCT);
		layout.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);

		rootPanel.add(layout);

		Label label = new Label("Zadejte přístupový klíč pro " + mapRoleToLabel(role));
		label.addStyleName("heading");
		final PasswordTextBox accessKeyInput = new PasswordTextBox();
		accessKeyInput.addStyleName("accessKeyInput");
		Button sendButton = new Button("Přihlásit");
		SimplePanel checkBoxWrapper = new SimplePanel();
		final CheckBox persistent = new CheckBox("Přihlásit trvale");
		persistent.setValue(Boolean.valueOf(checkPersistent));
		checkBoxWrapper.add(persistent);
		Label errorLabel = new Label(errorMessage != null
				? errorMessage
				: "");
		errorLabel.addStyleName("errorLabel");

		layout.add(label);
		FlowPanel inputWrapper = new FlowPanel();
		inputWrapper.getElement().getStyle().setDisplay(Display.INLINE_BLOCK);
		inputWrapper.getElement().getStyle().setProperty("textAlign", "left");
		inputWrapper.add(accessKeyInput);
		inputWrapper.add(sendButton);
		inputWrapper.add(checkBoxWrapper);
		inputWrapper.add(errorLabel);

		layout.add(inputWrapper);

		accessKeyInput.addKeyDownHandler(new KeyDownHandler() {

			@Override
			public void onKeyDown(KeyDownEvent event) {
				if(event.getNativeKeyCode() == KeyCodes.KEY_ENTER)
					authorize(role, path, accessKeyInput.getValue(), persistent.getValue().booleanValue());
			}

		});

		sendButton.addClickHandler(new ClickHandler() {

			@Override
			public void onClick(ClickEvent event) {
				authorize(role, path, accessKeyInput.getValue(), persistent.getValue().booleanValue());
			}

		});
	}

	private native String mapRoleToLabel(String role) /*-{
		return "" + $wnd.mapRoleToLabel(role);
	}-*/;

	private native String mapRoleToToken(String role) /*-{
		return "" + $wnd.mapRoleToToken(role);
	}-*/;

	private native String mapTokenToRole(String token) /*-{
		return "" + $wnd.mapTokenToRole(token);
	}-*/;

	private native boolean isRolePublic(String role) /*-{
		if (typeof ($wnd.isRolePublic) === 'function') {
			return $wnd.isRolePublic(role);
		}

		return false;
	}-*/;

	private void authorize(final String role, final String path, String accessKey, final boolean persistent) {
		swigService.authorize(role, accessKey, persistent, new AsyncCallback<Boolean>() {

			@Override
			public void onFailure(Throwable caught) {
				error(caught);
			}

			@Override
			public void onSuccess(Boolean authorized) {
				if(authorized)
					renderRoleContent(role, path);
				else
					renderAuthorizationScreen(role, path, persistent, "Neplatný přístupový kód - zkuste znovu.");
			}

		});
	}

	private String makePath(String path, String gallery) {
		return path != null && path.length() > 0
				? (path + "/" + gallery)
				: gallery;
	}

	private void renderRoleContent(final String role, final String path) {
		if(AccessRoles.ADMIN_ROLE.equals(role)) {
			swigService.getAccessRoles(new AsyncCallback<AccessRoles>() {

				@Override
				public void onFailure(Throwable caught) {
					error(caught);
				}

				@Override
				public void onSuccess(AccessRoles accessRoles) {
					renderRoleContentInternal(role, path, accessRoles.getSortedNames());
				}

			});
		}
		else
			renderRoleContentInternal(role, path, null);
	}

	private void renderRoleContentInternal(final String role, final String path, final List<String> allRoles) {
		final Panel rootPanel = RootPanel.get();
		rootPanel.clear();

		final VerticalPanel layout = new VerticalPanel();
		layout.getElement().getStyle().setWidth(100.0, Unit.PCT);
		layout.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);

		rootPanel.add(layout);

		FlowPanel controlPanel = new FlowPanel();
		controlPanel.addStyleName("controlPanel");

		Label logout = new Label("Odhlásit " + mapRoleToLabel(role));
		logout.addStyleName("logoutLink");

		logout.addClickHandler(new ClickHandler() {

			@Override
			public void onClick(ClickEvent event) {
				swigService.unauthorize(role, new AsyncCallback<Void>() {

					@Override
					public void onFailure(Throwable caught) {
						error(caught);
					}

					@Override
					public void onSuccess(Void result) {
						Window.Location.reload();
					}

				});
			}

		});

		controlPanel.add(logout);

		if(AccessRoles.ADMIN_ROLE.equals(role)) {
			Anchor extraLink;
			if(SPECIAL_PATH_TOOLS.equals(path)) {
				extraLink = new Anchor("Správa galerií", "#" + AccessRoles.ADMIN_ROLE);
				extraLink.setTitle("Prohlížení galerií a správa přístupových práv");
			}
			else {
				extraLink = new Anchor("Správa přístupových rolí", "#" + AccessRoles.ADMIN_ROLE + SPECIAL_PATH_TOOLS);
				extraLink.setTitle("Správa seznamu a mapování přístupových rolí");
			}

			extraLink.addStyleName("extraLink");
			controlPanel.add(extraLink);
		}

		rootPanel.add(controlPanel);

		if(path != null && path.length() >= 1 && path.charAt(0) == ':') {
			FlowPanel container = new FlowPanel();
			rootPanel.add(container);
			renderSpecialContent(role, container, path);
			return;
		}

		Label label = new Label(mapRoleToLabel(role) + " - " + (path != null && path.length() > 0
				? toPresentationString(path, true)[0]
				: "Hlavní složka"));
		label.addStyleName("heading");

		layout.add(label);

		if(AccessRoles.ADMIN_ROLE.equals(role))
			layout.add(createAdminToolsEditor(path, allRoles));

		if(path != null && path.length() > 0) {
			int pos = path.lastIndexOf('/');
			final String folder = pos != -1
					? path.substring(0, pos)
					: "";
			String presentationFolder = toPresentationString(folder);
			String folderName = pos != -1
					? ("<b>" + SafeHtmlUtils.htmlEscape(presentationFolder) + "</b>")
					: "hlavní složky";
			String tooltipFolderName = pos != -1
					? toPresentationString(presentationFolder)
					: "hlavní složky";

			Anchor back = new Anchor(SafeHtmlUtils.fromTrustedString("Zpět do " + folderName));
			back.setTitle("Zpět na obsah " + tooltipFolderName + " dostupný pro " + mapRoleToLabel(role));
			back.getElement().getStyle().setDisplay(Display.INLINE_BLOCK);
			back.addStyleName("backLink");
			back.setHref("#" + role + (folder.length() > 0
					? ("/" + folder)
					: ""));

			layout.add(back);
		}

		final FlexTable grid = new FlexTable();
		grid.addStyleName("galleryContentSummary");
		layout.add(grid);

		final Anchor[] downloadManyAnchors = new Anchor[2];

		swigService.getAccessibleFolders(role, path, new AsyncCallback<FolderMetadata[]>() {

			@Override
			public void onFailure(Throwable caught) {
				error(caught);
			}

			@Override
			public void onSuccess(final FolderMetadata[] accessibleFolders) {
				if(accessibleFolders.length > 0) {
					for(FolderMetadata folderMetadata : accessibleFolders) {
						String[] nameAndDate = toPresentationString(folderMetadata.getName(), true);
						Anchor galleryLink = new Anchor(nameAndDate[0]);
						galleryLink.addStyleName("galleryLink");
						String relativePath = (path != null && path.length() > 0
								? (path + "/")
								: "") + folderMetadata.getName();
						galleryLink.setHref("#" + role + "/" + relativePath);

						int rowIdx = grid.getRowCount();
						grid.insertRow(rowIdx);

						SimplePanel icon = new SimplePanel();
						switch (folderMetadata.getContentType()) {
						case IMAGE:
							icon.addStyleName(ResourceBundle.INSTANCE.iconStyle().imageIcon());
							break;
						case VIDEO:
							icon.addStyleName(ResourceBundle.INSTANCE.iconStyle().videoIcon());
							break;
						case FOLDER:
							icon.addStyleName(ResourceBundle.INSTANCE.iconStyle().folderIcon());
							break;
						case IMAGE_AND_VIDEO:
							icon.addStyleName(ResourceBundle.INSTANCE.iconStyle().imageAndVideoIcon());
							break;
						default:
							break;
						}

						grid.setWidget(rowIdx, 0, icon);

						grid.setWidget(rowIdx, 1, galleryLink);

						Label date = new Label(nameAndDate[1]);
						grid.setWidget(rowIdx, 2, date);

						Label summary = new Label(folderMetadata.getContentSummary());
						grid.setWidget(rowIdx, 3, summary);

						if(AccessRoles.ADMIN_ROLE.equals(role)) {
							grid.getFlexCellFormatter().setColSpan(++rowIdx, 1, 4);
							grid.setWidget(rowIdx, 1, createAdminToolsEditor(relativePath, allRoles));
						}
					}
				}

				if(path != null && path.length() > 0)
					swigService.getMediaMetadata(role, path, MediaMetadata.FLAG_ARCHIVE_SIZE | MediaMetadata.FLAG_FILE_SIZE | MediaMetadata.FLAG_VIDEO_URL,
							new AsyncCallback<MediaMetadata[]>() {

								@Override
								public void onFailure(Throwable caught) {
									error(caught);
								}

								@Override
								public void onSuccess(final MediaMetadata[] images) {
									MediaMetadata archiveMetadata = null;

									if(images.length > 0) {
										archiveMetadata = images[images.length - 1];

										if(!archiveMetadata.getName().equals(MediaMetadata.ARCHIVE_METADATA_NAME))
											archiveMetadata = null;

										final CheckBox[] downloadSelectors = new CheckBox[images.length - (archiveMetadata != null
												? 1
												: 0)];

										if(archiveMetadata != null) {
											layout.add(downloadManyAnchors[0] = createOrUpdateDownloadManyAnchor(null, path, null,
													archiveMetadata.getOriginalFileSize()));
											layout.add(createSelectAllToggle(downloadSelectors));
										}

										HorizontalPanel thumbnailSuperContainer = new HorizontalPanel();

										layout.add(thumbnailSuperContainer);

										FlowPanel thumbnailContainer = new FlowPanel();

										thumbnailSuperContainer.add(thumbnailContainer);

										int index = 0;
										int maxSpriteRows = Constants.MAX_THUMBNAIL_SPRITE_HEIGHT / Constants.THUMBNAIL_SIZE;
										for(MediaMetadata imageMetadata : images) {
											if(imageMetadata.getName().equals(MediaMetadata.ARCHIVE_METADATA_NAME))
												continue;

											SimplePanel imageLoadingPanel = new SimplePanel();
											imageLoadingPanel.addStyleName("imageLoading");

											SimplePanel imageThumbnail = new SimplePanel();
											imageThumbnail.addStyleName("thumbnail");

											String backgroundImage = "url(image?path=" + UriUtils.encode(path) + "&format=thumbnail-sprite&index="
													+ (index / maxSpriteRows) + ")";

											imageThumbnail.getElement().getStyle().setBackgroundImage(backgroundImage);
											imageThumbnail
													.getElement()
													.getStyle()
													.setProperty("backgroundPosition",
															"0px " + "-" + (Constants.THUMBNAIL_SIZE * (index % maxSpriteRows)) + "px");

											final String imageName = imageMetadata.getName();

											FlowPanel imageWrapper = new FlowPanel();
											imageWrapper.addStyleName("imageWrapper");

											imageWrapper.addDomHandler(new ClickHandler() {

												@Override
												public void onClick(ClickEvent event) {
													play(role, path, imageName, downloadSelectors);
												}

											}, ClickEvent.getType());

											imageWrapper.add(imageLoadingPanel);
											imageWrapper.add(imageThumbnail);

											String name = imageMetadata.getName();
											int lastDot = name.lastIndexOf('.');
											if(lastDot != -1)
												name = name.substring(0, lastDot);

											imageWrapper.setTitle(name);
											Label label = new Label(ellipsis(name, 16));
											label.addStyleName("imageLabel");
											imageWrapper.add(label);

											if(imageMetadata.getType() == MediaMetadata.Type.IMAGE) {
												CheckBox downloadSelector = new CheckBox();
												downloadSelectors[index] = downloadSelector;

												downloadSelector.addClickHandler(new ClickHandler() {

													@Override
													public void onClick(ClickEvent event) {
														event.stopPropagation();
													}

												});

												downloadSelector.addValueChangeHandler(new ValueChangeHandler<Boolean>() {

													@Override
													public void onValueChange(ValueChangeEvent<Boolean> event) {
														updateDownloadSelection(downloadSelectors, images, downloadManyAnchors, path);
													}
												});

												imageWrapper.add(downloadSelector);
											}
											else {
												downloadSelectors[index] = null;
											}

											thumbnailContainer.add(imageWrapper);

											index++;
										}

										if(archiveMetadata != null) {
											layout.add(downloadManyAnchors[1] = createOrUpdateDownloadManyAnchor(null, path, null,
													archiveMetadata.getOriginalFileSize()));
											layout.add(createSelectAllToggle(downloadSelectors));
										}
									}
									else if(accessibleFolders.length == 0)
										renderNoContent(layout, role);
								}

								private String ellipsis(String name, int maxChars) {
									if(name.length() <= maxChars)
										return name;

									return name.substring(0, maxChars - 3) + "...";
								}

							});

				else if(accessibleFolders.length == 0)
					renderNoContent(layout, role);
			}

		});
	}

	private Widget createAdminToolsEditor(final String relativePath, final List<String> allRoles) {
		SimplePanel superContainer = new SimplePanel();
		superContainer.addStyleName("adminAccessRightSuperContainer");
		DisclosurePanel disclosurePanel = new DisclosurePanel("Přístupová práva");
		disclosurePanel.addStyleName("adminAccessRightOptionContainer");
		disclosurePanel.setAnimationEnabled(true);
		superContainer.add(disclosurePanel);
		final FlowPanel container = new FlowPanel();
		disclosurePanel.add(container);

		if(relativePath == null || relativePath.length() == 0) {
			AccessRights accessRights = new AccessRights();
			accessRights.addRole(AccessRights.ALL_ROLES);
			generateAdminToolsEditor(container, relativePath, allRoles, accessRights, true);
		}
		else
			swigService.getAccessRights(relativePath, new AsyncCallback<AccessRights>() {

				@Override
				public void onFailure(Throwable caught) {
					error(caught);
				}

				@Override
				public void onSuccess(AccessRights accessRights) {
					generateAdminToolsEditor(container, relativePath, allRoles, accessRights, false);
				}

			});

		return superContainer;
	}

	private boolean accessRightsCheckBoxSemaphore;

	private void generateAdminToolsEditor(HasWidgets container, String relativePath, List<String> allRoles, AccessRights accessRights, boolean readOnly) {
		List<CheckBox> checkBoxesToDisable = new ArrayList<CheckBox>();
		container.add(createAccessRightOption(AccessRights.ALL_ROLES, accessRights.isUniversalAccess(), relativePath, readOnly, accessRights,
				checkBoxesToDisable));

		try {
			accessRightsCheckBoxSemaphore = true;

			for(String role : allRoles) {
				if(AccessRoles.ADMIN_ROLE.equals(role))
					continue;

				container.add(createAccessRightOption(role, accessRights.hasRole(role), relativePath, readOnly, accessRights, checkBoxesToDisable));
			}
		}
		finally {
			accessRightsCheckBoxSemaphore = false;
		}
	}

	private Widget createAccessRightOption(final String role, boolean checked, final String relativePath, final boolean readOnly,
			final AccessRights accessRights, final List<CheckBox> checkBoxesToDisable) {

		boolean universalAccess = AccessRights.ALL_ROLES.equals(role);
		String roleLabel = universalAccess
				? "VŠICHNI"
				: mapRoleToLabel(role);

		FlowPanel superWrapper = new FlowPanel();
		superWrapper.addStyleName("adminAccessRightOptionWrapper");
		FlowPanel wrapper = new FlowPanel();
		superWrapper.add(wrapper);
		FlowPanel checkboxWrapper = new FlowPanel();
		checkboxWrapper.addStyleName("adminAccessRightOptionCheckBoxWrapper");
		CheckBox checkBox = new CheckBox(roleLabel);
		checkBox.setValue(checked);
		checkBox.setEnabled(!readOnly);
		checkBox.setTitle("Povolit přístup pro '" + roleLabel + "'");

		checkboxWrapper.add(checkBox);

		SimplePanel fadeOut = new SimplePanel();
		fadeOut.addStyleName("fadeOut");
		checkboxWrapper.add(fadeOut);

		wrapper.add(checkboxWrapper);

		Anchor anchor = new Anchor("Test");

		if(!universalAccess) {
			anchor.setTitle("Otevřít roli '" + roleLabel + "' v nové kartě prohlížeče");
			anchor.setTarget("_blank");
			anchor.setHref("#" + URL.encode(mapRoleToToken(role) + "/" + relativePath));
		}
		else
			anchor.getElement().getStyle().setVisibility(Visibility.HIDDEN);

		wrapper.add(anchor);

		checkBox.addValueChangeHandler(new ValueChangeHandler<Boolean>() {

			@Override
			public void onValueChange(ValueChangeEvent<Boolean> event) {
				boolean checked = event.getValue();

				if(AccessRights.ALL_ROLES.equals(role)) {
					try {
						accessRightsCheckBoxSemaphore = true;

						for(CheckBox w : checkBoxesToDisable) {
							if(checked)
								w.setValue(true, true);
							w.setEnabled(!checked);
						}
					}
					finally {
						accessRightsCheckBoxSemaphore = false;
					}
				}

				if(checked)
					accessRights.addRole(role);
				else
					accessRights.removeRole(role);

				if(!accessRightsCheckBoxSemaphore)
					swigService.storeAccessRights(relativePath, accessRights, new AsyncCallback<AccessRights>() {

						@Override
						public void onFailure(Throwable caught) {
							error(caught);
						}

						@Override
						public void onSuccess(AccessRights accessRights) {
							// Perhaps we could refresh the check
							// boxes... but it's not necessary
						}

					});
			}
		});

		if(!AccessRights.ALL_ROLES.equals(role)) {
			checkBoxesToDisable.add(checkBox);

			if(accessRights.isUniversalAccess()) {
				checkBox.setValue(true, true);
				checkBox.setEnabled(false);
			}
		}

		return superWrapper;
	}

	private void renderSpecialContent(String role, final HasWidgets container, String path) {
		if(SPECIAL_PATH_TOOLS.equals(path)) {
			if(!AccessRoles.ADMIN_ROLE.equals(role)) {
				error("Tato reference je přístupná pouze pro roli " + AccessRoles.ADMIN_ROLE);
				return;
			}

			if(adminTools == null)
				adminTools = new AdminToolsImpl();
			if(adminToolsPresenter == null)
				adminToolsPresenter = new AdminToolsPresenter(adminTools, swigService);
			adminTools.setPresenter(adminToolsPresenter);

			swigService.getRoleMapperInfo(new AsyncCallback<RoleMapperInfo>() {

				@Override
				public void onFailure(Throwable caught) {
					error("Nepodařilo se načíst pravidla mapování přístupových rolí: " + caught.getMessage());
				}

				@Override
				public void onSuccess(RoleMapperInfo roleMapperInfo) {
					adminToolsPresenter.setDefaultRoleMapper(roleMapperInfo.getDefaultRoleMapper());
					adminToolsPresenter.setCustomRoleMapper(roleMapperInfo.getCustomRoleMapper());
					adminToolsPresenter.setUseCustomRoleMapper(roleMapperInfo.isUseCustomRoleMapper());

					swigService.getAccessRoles(new AsyncCallback<AccessRoles>() {

						@Override
						public void onFailure(Throwable caught) {
							error("Nepodařilo se načíst seznam přístupových rolí: " + caught.getMessage());

						}

						@Override
						public void onSuccess(AccessRoles accessRoles) {
							adminToolsPresenter.setAccessRoles(accessRoles);
							adminToolsPresenter.go(container);
						}

					});
				}

			});
		}
		else
			Window.alert("Neznámá reference: " + path);
	}

	private String toPresentationString(String path) {
		return toPresentationString(path, false)[0];
	}

	private String[] toPresentationString(String path, boolean nameOnly) {
		if(path == null)
			return new String[] { null, null };

		if(nameOnly) {
			int pos = path.lastIndexOf('/');
			if(pos != -1)
				path = path.substring(pos + 1);

			return splitPresentationString(path.replace('_', ' '));
		}

		return new String[] { path.replace('_', ' '), null };
	}

	private String[] splitPresentationString(String dateAndName) {
		RegExp re = RegExp.compile("^(\\d{4})-(\\d{2})(?:-(\\d{2}))?\\s+(.*)$");
		MatchResult match = re.exec(dateAndName);

		if(match != null) {
			String day = match.getGroup(3);
			String month = match.getGroup(2);
			String year = match.getGroup(1);
			String name = match.getGroup(4);

			if(day != null && day.length() == 0)
				day = null;

			String date = day != null
					? (day + '.' + month + "." + year)
					: (month + "/" + year);

			return new String[] { name, date };
		}

		return new String[] { dateAndName, null };
	}

	private void renderNoContent(Panel layout, String role) {
		Label noGallery = new Label("Pro " + mapRoleToLabel(role) + " není v této složce dostupný žádný obsah");
		noGallery.addStyleName("noGallery");

		layout.add(noGallery);
	}

	private Anchor createOrUpdateDownloadManyAnchor(Anchor anchor, String gallery, String selection, long size) {
		if(anchor == null) {
			anchor = new Anchor();
		}

		if(selection == null || selection.length() == 0) {
			if(selection == null)
				anchor.getElement().setPropertyString("data-full-size", Long.valueOf(size).toString());
			else
				size = Long.valueOf(anchor.getElement().getPropertyString("data-full-size"));

			anchor.setText("Stáhnout celou galerii (" + smartSize(size) + ")");
			anchor.setHref("image?path=" + UriUtils.encode(gallery) + ".zip&fallbackUrl=" + UriUtils.encode(Window.Location.getHref()).replace("#", "%23"));
			anchor.setTitle("Stáhnout všechny obrázky v jediném souboru (formát ZIP)");
		}
		else {
			anchor.setText("Stáhnout výběr z galerie (" + smartSize(size) + ")");
			anchor.setHref("image?path=" + UriUtils.encode(gallery) + ".zip&selection=[" + selection + "]&fallbackUrl="
					+ UriUtils.encode(Window.Location.getHref()).replace("#", "%23"));
			anchor.setTitle("Stáhnout vybrané obrázky v jediném souboru (formát ZIP)");
		}

		return anchor;
	}

	private Button createSelectAllToggle(final CheckBox[] downloadSelectors) {
		Button button = new Button();

		button.addStyleName("selectAllButton");
		button.setText("Označit vše");
		button.addClickHandler(new ClickHandler() {

			@Override
			public void onClick(ClickEvent event) {
				boolean noneChecked = true;
				for(CheckBox downloadSelector : downloadSelectors) {
					if(downloadSelector != null && downloadSelector.getValue().booleanValue()) {
						noneChecked = false;
						break;
					}
				}

				for(CheckBox downloadSelector : downloadSelectors) {
					if(downloadSelector != null) {
						downloadSelector.setValue(noneChecked, true);
					}
				}
			}

		});

		return button;
	}

	private Boolean selectionStatus(CheckBox[] downloadSelectors, int index, Boolean defaultValue) {
		return downloadSelectors.length > index && downloadSelectors[index] != null
				? downloadSelectors[index].getValue()
				: defaultValue;
	}

	private void updateDownloadSelection(CheckBox[] downloadSelectors, MediaMetadata[] imagesMetadata, Anchor[] downloadAnchors, String gallery) {
		long estimatedSize = 0;
		StringBuilder selectionBuilder = new StringBuilder();
		boolean first = true;
		int idx = 0;
		for(CheckBox selector : downloadSelectors) {
			if(selector != null && selector.getValue()) {
				if(!first)
					selectionBuilder.append(',');
				else
					first = false;
				selectionBuilder.append(idx);
				estimatedSize += imagesMetadata[idx].getOriginalFileSize();
			}
			idx++;
		}

		String selection = selectionBuilder.toString();

		for(Anchor downloadAnchor : downloadAnchors)
			createOrUpdateDownloadManyAnchor(downloadAnchor, gallery, selection, estimatedSize);
	}

	private void play(final String role, final String galleryPath, final String mediaName, final CheckBox[] downloadSelectors) {

		if(playing)
			return;

		playing = true;

		swigService.getMediaMetadata(role, galleryPath, MediaMetadata.FLAG_PREVIEW_DIMENSIONS | MediaMetadata.FLAG_FILE_SIZE | MediaMetadata.FLAG_VIDEO_URL,
				new AsyncCallback<MediaMetadata[]>() {

					@Override
					public void onFailure(Throwable caught) {
						playing = false;
						error(caught);
					}

					@Override
					public void onSuccess(final MediaMetadata[] imagesOrVideos) {
						final int currentImageIndexRef[] = new int[] { -1 };
						final boolean fullScreen[] = new boolean[] { false };
						final String blackGlassBackground = "black";
						final String semitransparentGlassBackground = "rgba(0, 0, 0, 0.6)";
						int index = 0;

						for(MediaMetadata metadata : imagesOrVideos) {
							if(metadata.getName().equals(mediaName)) {
								currentImageIndexRef[0] = index;
								break;
							}

							index++;
						}

						if(currentImageIndexRef[0] == -1) {
							error("Snímek " + mediaName + " nebyl nalezen");
							playing = false;
							return;
						}

						Style style;

						final SimplePanel glassPanel = new SimplePanel();
						glassPanel.getElement().setId("playerGlass");
						style = glassPanel.getElement().getStyle();
						style.setPosition(Position.FIXED);
						style.setTop(0.0, Unit.PX);
						style.setRight(0.0, Unit.PX);
						style.setBottom(0.0, Unit.PX);
						style.setLeft(0.0, Unit.PX);
						style.setBackgroundColor(fullScreen[0]
								? blackGlassBackground
								: semitransparentGlassBackground);
						style.setOpacity(0.0);
						style.setVisibility(Visibility.HIDDEN);

						RootPanel.get().add(glassPanel);

						HorizontalPanel centerer = new HorizontalPanel();
						centerer.setVerticalAlignment(HasVerticalAlignment.ALIGN_MIDDLE);
						centerer.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);
						centerer.setWidth("100%");
						centerer.setHeight("100%");

						glassPanel.add(centerer);

						final FlowPanel player = new FlowPanel();
						player.addStyleName("player");
						style = player.getElement().getStyle();
						style.setBackgroundColor("white");
						style.setDisplay(Display.INLINE_BLOCK);
						style.setPosition(Position.RELATIVE);

						final Style playerStyle = style;

						final CheckBox downloadSelector = new CheckBox();
						downloadSelector.setValue(selectionStatus(downloadSelectors, currentImageIndexRef[0], Boolean.FALSE));
						downloadSelector.addValueChangeHandler(new ValueChangeHandler<Boolean>() {

							@Override
							public void onValueChange(ValueChangeEvent<Boolean> event) {
								int index = currentImageIndexRef[0];
								if(downloadSelectors.length > index && downloadSelectors[index] != null) {
									downloadSelectors[index].setValue(event.getValue(), true);
								}
							}

						});
						downloadSelector.getElement().getStyle().setZIndex(2);
						player.add(downloadSelector);

						final FlowPanel controlPanel = new FlowPanel();
						controlPanel.addStyleName("playerControlPanel");
						if(fullScreen[0])
							player.addStyleName("fullScreen");
						style = controlPanel.getElement().getStyle();
						style.setPaddingRight(PLAYER_IMAGE_OFFSET, Unit.PX);
						style.setZIndex(2);

						player.add(controlPanel);

						final Anchor downloadAnchor = new Anchor();
						downloadAnchor.setTitle("Stáhnout obrázek v plné velikosti");
						style = downloadAnchor.getElement().getStyle();
						style.setPosition(Position.ABSOLUTE);
						style.setLeft(PLAYER_IMAGE_OFFSET, Unit.PX);
						style.setTop(PLAYER_NAVIGATION_TOP_OFFSET, Unit.PX);

						setDownloadAnchor(downloadAnchor, galleryPath, imagesOrVideos[currentImageIndexRef[0]]);

						controlPanel.add(downloadAnchor);

						final int[] imageSizeToFit = new int[2]; // {width,height}
						computeImageSize(imagesOrVideos[currentImageIndexRef[0]], imageSizeToFit, fullScreen[0], false);

						final Widget[] mediaWidgetRef = new Widget[] { createMediaWidget(galleryPath, imagesOrVideos[currentImageIndexRef[0]], fullScreen[0]) };

						player.add(mediaWidgetRef[0]);

						style = mediaWidgetRef[0].getElement().getStyle();
						style.setPosition(Position.ABSOLUTE);
						style.setTop(fullScreen[0]
								? 0
								: PLAYER_IMAGE_OFFSET, Unit.PX);
						style.setLeft(fullScreen[0]
								? 0
								: PLAYER_IMAGE_OFFSET, Unit.PX);
						style.setCursor(Cursor.POINTER);
						style.setZIndex(1);

						if(mediaWidgetRef[0] instanceof Image)
							adjustImageSize(playerStyle, imageSizeToFit[0], imageSizeToFit[1], fullScreen[0], style);
						else {
							adjustImageSize(playerStyle, imageSizeToFit[0], imageSizeToFit[1], fullScreen[0], style, mediaWidgetRef[0].getElement()
									.getFirstChildElement().getStyle());
							downloadSelector.getElement().getStyle().setDisplay(Display.NONE);
						}

						final HandlerRegistration resizeHandler = Window.addResizeHandler(new ResizeHandler() {

							@Override
							public void onResize(ResizeEvent event) {
								computeImageSize(imagesOrVideos[currentImageIndexRef[0]], imageSizeToFit, fullScreen[0], false);
								if(mediaWidgetRef[0] instanceof Image)
									adjustImageSize(playerStyle, imageSizeToFit[0], imageSizeToFit[1], fullScreen[0], mediaWidgetRef[0].getElement().getStyle());
								else
									adjustImageSize(playerStyle, imageSizeToFit[0], imageSizeToFit[1], fullScreen[0],
											mediaWidgetRef[0].getElement().getStyle(), mediaWidgetRef[0].getElement().getFirstChildElement().getStyle());
							}

						});

						final Label previous = new Label();
						previous.setTitle("Předchozí obrázek");
						previous.getElement().setInnerHTML("&laquo;");
						previous.getElement().getStyle().setDisplay(Display.INLINE);
						previous.getElement().getStyle().setMarginRight(PLAYER_CONTROL_BUTTON_GAP, Unit.PX);
						previous.getElement().getStyle().setZIndex(2);
						previous.addStyleName("controlButtonLabel");
						if(currentImageIndexRef[0] > 0)
							previous.addStyleName("enabled");
						else
							previous.removeStyleName("enabled");

						controlPanel.add(previous);

						final Label next = new Label();
						next.setTitle("Další obrázek");
						next.getElement().setInnerHTML("&raquo;");
						next.getElement().getStyle().setDisplay(Display.INLINE);
						next.getElement().getStyle().setMarginRight(PLAYER_CONTROL_BUTTON_GAP, Unit.PX);
						next.getElement().getStyle().setZIndex(2);
						next.addStyleName("controlButtonLabel");
						if(currentImageIndexRef[0] < imagesOrVideos.length - 1)
							next.addStyleName("enabled");
						else
							next.removeStyleName("enabled");

						controlPanel.add(next);

						final Label toggleFullScreen = new Label();
						toggleFullScreen.setTitle("Celá obrazovka");
						toggleFullScreen.getElement().setInnerHTML("&#9634;");
						toggleFullScreen.getElement().getStyle().setDisplay(Display.INLINE);
						toggleFullScreen.getElement().getStyle().setZIndex(2);
						toggleFullScreen.addStyleName("controlButtonLabel");
						toggleFullScreen.addStyleName("fullScreenButtonLabel");
						toggleFullScreen.addStyleName("enabled");

						controlPanel.add(toggleFullScreen);

						final HandlerRegistration[] keyReg = new HandlerRegistration[1];

						final ClickHandler closeHandler = new ClickHandler() {

							@Override
							public void onClick(ClickEvent event) {
								if(playing) {
									playing = false;

									Animation fadeOut = new Animation() {

										@Override
										protected void onUpdate(double progress) {
											glassPanel.getElement().getStyle().setOpacity(1 - progress);
										}

										@Override
										protected void onComplete() {
											glassPanel.getElement().getStyle().setVisibility(Visibility.HIDDEN);
											super.onComplete();
											RootPanel.get().remove(glassPanel);
											resizeHandler.removeHandler();
											if(keyReg[0] != null)
												keyReg[0].removeHandler();
										}

									};

									// remove eventual iframes (typically for
									// video) since they don't work well with
									// opacity
									NodeList<Element> iframes = glassPanel.getElement().getElementsByTagName("iframe");

									for(int i = 0; i < iframes.getLength(); i++)
										iframes.getItem(i).removeFromParent();

									fadeOut.run(ANIMATION_DURATION);
								}
							}

						};

						final ClickHandler[] imageClickHandlerRef = new ClickHandler[1];

						class Controller {
							void next(boolean closeOnEdge) {
								if(!playing)
									return;

								int oldIndex = currentImageIndexRef[0];
								if(oldIndex == imagesOrVideos.length - 1) {
									if(closeOnEdge) {
										closeHandler.onClick(null);
									}

									return;
								}

								currentImageIndexRef[0]++;
								Widget newMedia = createMediaWidget(galleryPath, imagesOrVideos[currentImageIndexRef[0]], fullScreen[0]);
								if(newMedia instanceof HasClickHandlers)
									((HasClickHandlers) newMedia).addClickHandler(imageClickHandlerRef[0]);
								transition(player, galleryPath, imagesOrVideos, mediaWidgetRef, imageSizeToFit, newMedia, next, previous, toggleFullScreen,
										downloadAnchor, downloadSelector, downloadSelectors, currentImageIndexRef[0], oldIndex, fullScreen[0], false);

								if(currentImageIndexRef[0] < imagesOrVideos.length - 1)
									Image.prefetch(createImageUri(galleryPath, imagesOrVideos[currentImageIndexRef[0] + 1].getName(), fullScreen[0]));
							}

							void previous(boolean closeOnEdge) {
								if(!playing)
									return;

								int oldIndex = currentImageIndexRef[0];
								if(oldIndex == 0) {
									if(closeOnEdge) {
										closeHandler.onClick(null);
									}

									return;
								}

								currentImageIndexRef[0]--;
								Widget newMedia = createMediaWidget(galleryPath, imagesOrVideos[currentImageIndexRef[0]], fullScreen[0]);
								if(newMedia instanceof HasClickHandlers)
									((HasClickHandlers) newMedia).addClickHandler(imageClickHandlerRef[0]);
								transition(player, galleryPath, imagesOrVideos, mediaWidgetRef, imageSizeToFit, newMedia, next, previous, toggleFullScreen,
										downloadAnchor, downloadSelector, downloadSelectors, currentImageIndexRef[0], oldIndex, fullScreen[0], false);

								if(currentImageIndexRef[0] > 0)
									Image.prefetch(createImageUri(galleryPath, imagesOrVideos[currentImageIndexRef[0] - 1].getName(), fullScreen[0]));
							}

							void toggleFullScreen() {
								if(!playing)
									return;

								fullScreen[0] = !fullScreen[0];
								Widget newMedia = createMediaWidget(galleryPath, imagesOrVideos[currentImageIndexRef[0]], fullScreen[0]);
								if(newMedia instanceof HasClickHandlers)
									((HasClickHandlers) newMedia).addClickHandler(imageClickHandlerRef[0]);
								transition(player, galleryPath, imagesOrVideos, mediaWidgetRef, imageSizeToFit, newMedia, next, previous, toggleFullScreen,
										downloadAnchor, downloadSelector, downloadSelectors, currentImageIndexRef[0], currentImageIndexRef[0], fullScreen[0],
										true);
								glassPanel.getElement().getStyle().setBackgroundColor(fullScreen[0]
										? blackGlassBackground
										: semitransparentGlassBackground);

								if(currentImageIndexRef[0] > 0)
									Image.prefetch(createImageUri(galleryPath, imagesOrVideos[currentImageIndexRef[0] - 1].getName(), fullScreen[0]));

								if(currentImageIndexRef[0] < imagesOrVideos.length - 1)
									Image.prefetch(createImageUri(galleryPath, imagesOrVideos[currentImageIndexRef[0] + 1].getName(), fullScreen[0]));

								if(fullScreen[0])
									player.addStyleName("fullScreen");
								else
									player.removeStyleName("fullScreen");
							}
						}

						class NextClickController extends Controller implements ClickHandler {
							@Override
							public void onClick(ClickEvent event) {
								next(event.getSource() instanceof Image);
							}
						}

						class PreviousClickController extends Controller implements ClickHandler {
							@Override
							public void onClick(ClickEvent event) {
								previous(event.getSource() instanceof Image);
							}
						}

						class FullScreenClickController extends Controller implements ClickHandler {
							@Override
							public void onClick(ClickEvent event) {
								toggleFullScreen();
							}
						}

						class KeyController extends Controller implements KeyDownHandler, KeyUpHandler {
							@Override
							public void onKeyDown(KeyDownEvent event) {

								switch (event.getNativeKeyCode()) {
								case KeyCodes.KEY_ENTER:
								case KeyCodes.KEY_RIGHT:
									next(true);
									break;
								case KeyCodes.KEY_LEFT:
									previous(true);
									break;
								case KeyCodes.KEY_ESCAPE:
									closeHandler.onClick(null);
									break;
								case KeyCodes.KEY_SPACE:
									Boolean currentSelection = selectionStatus(downloadSelectors, currentImageIndexRef[0], null);
									if(currentSelection != null)
										downloadSelector.setValue(!currentSelection, true);
									event.stopPropagation();
									event.preventDefault();
									break;
								case KeyCodes.KEY_F:
									toggleFullScreen();
									break;
								}
							}

							@Override
							public void onKeyUp(KeyUpEvent event) {

								switch (event.getNativeKeyCode()) {
								case KeyCodes.KEY_SPACE:
									event.stopPropagation();
									event.preventDefault();
								}
							}
						}

						final KeyDownHandler keyHandler = new KeyController();

						final ClickHandler nextClickHandler = new NextClickController();

						next.addClickHandler(nextClickHandler);

						final ClickHandler previousClickHandler = new PreviousClickController();

						previous.addClickHandler(previousClickHandler);

						final ClickHandler fullScreenClickHandler = new FullScreenClickController();

						toggleFullScreen.addClickHandler(fullScreenClickHandler);

						imageClickHandlerRef[0] = new ClickHandler() {

							@Override
							public void onClick(ClickEvent event) {
								if(!playing)
									return;

								if(event.getNativeEvent().getShiftKey())
									previousClickHandler.onClick(event);
								else if(event.getNativeEvent().getCtrlKey())
									closeHandler.onClick(event);
								else
									nextClickHandler.onClick(event);
							}

						};

						if(mediaWidgetRef[0] instanceof HasClickHandlers)
							((HasClickHandlers) mediaWidgetRef[0]).addClickHandler(imageClickHandlerRef[0]);

						keyReg[0] = RootPanel.get().addDomHandler(keyHandler, KeyDownEvent.getType());

						FlowPanel topControlPanel = new FlowPanel();
						topControlPanel.addStyleName("playerTopControlPanel");
						topControlPanel.getElement().getStyle().setZIndex(2);
						player.add(topControlPanel);

						Label close = new Label();
						close.setTitle("Zavřít okno");
						close.getElement().setInnerHTML("&#10006;");
						close.addStyleName("controlButtonLabel");
						close.addStyleName("closeButtonLabel");
						close.addStyleName("enabled");
						style = close.getElement().getStyle();
						style.setPosition(Position.ABSOLUTE);
						style.setRight(CLOSE_BUTTON_OFFSET, Unit.PX);
						style.setBottom(CLOSE_BUTTON_OFFSET, Unit.PX);

						close.addDomHandler(closeHandler, ClickEvent.getType());

						topControlPanel.add(close);

						centerer.add(player);

						if(currentImageIndexRef[0] > 0)
							Image.prefetch(createImageUri(galleryPath, imagesOrVideos[currentImageIndexRef[0] - 1].getName(), fullScreen[0]));

						if(currentImageIndexRef[0] < imagesOrVideos.length - 1)
							Image.prefetch(createImageUri(galleryPath, imagesOrVideos[currentImageIndexRef[0] + 1].getName(), fullScreen[0]));

						Animation fadeIn = new Animation() {

							@Override
							protected void onStart() {
								glassPanel.getElement().getStyle().setVisibility(Visibility.VISIBLE);
							}

							@Override
							protected void onUpdate(double progress) {
								glassPanel.getElement().getStyle().setOpacity(progress);
							}

							@Override
							protected void onComplete() {
								super.onComplete();
								glassPanel.getElement().getStyle().clearOpacity();

								glassPanel.addDomHandler(new ClickHandler() {

									@Override
									public void onClick(ClickEvent event) {
										if(!playing)
											return;

										Object source = event.getNativeEvent().getEventTarget();
										if(source instanceof Element && "td".equalsIgnoreCase(((Element) source).getTagName())) {
											closeHandler.onClick(event);
										}
									}

								}, ClickEvent.getType());

							}

						};

						fadeIn.run(ANIMATION_DURATION);
					}

					private Widget createMediaWidget(String galleryPath, MediaMetadata mediaMetadata, boolean fullScreen) {
						switch (mediaMetadata.getType()) {
						case IMAGE:
							return new Image(createImageUri(galleryPath, mediaMetadata.getName(), fullScreen));
						case VIDEO:
							SimplePanel wrapper = new SimplePanel();
							IFrameElement iframe = Document.get().createIFrameElement();
							iframe.setFrameBorder(0);
							iframe.setSrc(mediaMetadata.getVideoUrl());
							wrapper.getElement().appendChild(iframe);

							return wrapper;
						default:
							throw new IllegalArgumentException("Unsupported media type");
						}
					}

					private void adjustImageSize(Style playerStyle, int imageWidth, int imageHeight, boolean fullScreen, Style... mediaStyles) {
						playerStyle.setWidth(imageWidth + (fullScreen
								? 0
								: PLAYER_EXTRA_WIDTH), Unit.PX);
						playerStyle.setHeight(imageHeight + (fullScreen
								? 0
								: PLAYER_EXTRA_HEIGHT), Unit.PX);

						for(Style mediaStyle : mediaStyles) {
							mediaStyle.setWidth(imageWidth, Unit.PX);
							mediaStyle.setHeight(imageHeight, Unit.PX);
						}
					}

					private void computeImageSize(MediaMetadata imageMetadata, int[] size, boolean fullScreen, boolean fullScreenChange) {
						int imageWidth = fullScreen
								? Window.getClientWidth()
								: imageMetadata.getPreviewWidth();
						int imageHeight = fullScreen
								? imageWidth * imageMetadata.getPreviewHeight() / imageMetadata.getPreviewWidth()
								: imageMetadata.getPreviewHeight();

						int totalPlayerWidth = imageWidth + (fullScreen && !fullScreenChange
								? 0
								: PLAYER_EXTRA_WIDTH + 2 * PLAYER_MINIMUM_MARGIN);
						int totalPlayerHeight = imageHeight + (fullScreen && !fullScreenChange
								? 0
								: PLAYER_EXTRA_HEIGHT + 2 * PLAYER_MINIMUM_MARGIN);

						int horizontalOverflow = totalPlayerWidth - Window.getClientWidth();
						int verticalOverflow = totalPlayerHeight - Window.getClientHeight();

						double horizontalRatio = 1.0;
						double verticalRatio = 1.0;

						if(horizontalOverflow > 0) {
							int availableImageWidth = imageWidth - horizontalOverflow;
							if(availableImageWidth < PLAYER_MINIMUM_IMAGE_SIZE)
								availableImageWidth = PLAYER_MINIMUM_IMAGE_SIZE;
							horizontalRatio = (double) availableImageWidth / imageWidth;
						}

						if(verticalOverflow > 0) {
							int availableImageHeight = imageHeight - verticalOverflow;
							if(availableImageHeight < PLAYER_MINIMUM_IMAGE_SIZE)
								availableImageHeight = PLAYER_MINIMUM_IMAGE_SIZE;
							verticalRatio = (double) availableImageHeight / imageHeight;
						}

						if(horizontalOverflow > 0 || verticalOverflow > 0) {
							double minHorizontalRatio = (double) PLAYER_MINIMUM_IMAGE_SIZE / imageWidth;
							double minVerticalRatio = (double) PLAYER_MINIMUM_IMAGE_SIZE / imageHeight;

							double finalRatio = horizontalRatio < verticalRatio
									? horizontalRatio
									: verticalRatio;

							if(finalRatio < minHorizontalRatio)
								finalRatio = minHorizontalRatio;
							if(finalRatio < minVerticalRatio)
								finalRatio = minVerticalRatio;

							size[0] = (int) (imageWidth * finalRatio + 0.5);
							size[1] = (int) (imageHeight * finalRatio + 0.5);
						}
						else {
							size[0] = imageWidth;
							size[1] = imageHeight;
						}

					}

					private String createImageUri(String gallery, String name, boolean fullScreen) {
						return "image?path=" + UriUtils.encode(gallery + "/" + name) + "&format=" + (fullScreen
								? "original"
								: "preview");
					}

					private void setDownloadAnchor(Anchor downloadAnchor, String gallery, MediaMetadata imageMetadata) {
						if(imageMetadata.getType() == MediaMetadata.Type.IMAGE) {
							downloadAnchor.setText("Stáhnout (" + smartSize(imageMetadata.getOriginalFileSize()) + ")");
							downloadAnchor.setHref("image?path=" + UriUtils.encode(gallery + "/" + imageMetadata.getName())
									+ "&format=original&attachment=true" + "&fallbackUrl=" + UriUtils.encode(Window.Location.getHref()).replace("#", "%23"));
							downloadAnchor.getElement().getStyle().clearDisplay();
						}
						else
							downloadAnchor.getElement().getStyle().setDisplay(Display.NONE);
					}

					private void transition(final FlowPanel player, final String gallery, final MediaMetadata[] imagesOrVideos, final Widget[] media,
							final int[] imageSizeToFit, final Widget newMedia, final Label next, final Label previous, final Label toggleFullScreen,
							final Anchor downloadAnchor, final CheckBox downloadSelector, final CheckBox[] downloadSelectors, final int currentIndex,
							final int oldIndex, final boolean fullScreen, final boolean fullScreenChange) {

						previous.removeStyleName("enabled");
						next.removeStyleName("enabled");
						toggleFullScreen.removeStyleName("enabled");
						downloadSelector.getElement().getStyle().setDisplay(Display.NONE);

						final Style playerStyle = player.getElement().getStyle();
						final Style oldImageStyle = media[0].getElement().getStyle();
						final Style oldVideoStyle = media[0] instanceof Image
								? null
								: media[0].getElement().getFirstChildElement().getStyle();
						final Style newImageStyle = newMedia.getElement().getStyle();
						final Style newVideoStyle = newMedia instanceof Image
								? null
								: newMedia.getElement().getFirstChildElement().getStyle();

						final int oldWidth = imageSizeToFit[0];
						final int oldHeight = imageSizeToFit[1];

						computeImageSize(imagesOrVideos[currentIndex], imageSizeToFit, fullScreen, fullScreenChange);

						final int newWidth = imageSizeToFit[0];
						final int newHeight = imageSizeToFit[1];

						setDownloadAnchor(downloadAnchor, gallery, imagesOrVideos[currentIndex]);

						Animation animation = new Animation() {

							@Override
							protected void onStart() {
								super.onStart();
								newImageStyle.setPosition(Position.ABSOLUTE);
								newImageStyle.setTop(fullScreen && !fullScreenChange
										? 0
										: PLAYER_IMAGE_OFFSET, Unit.PX);
								newImageStyle.setLeft(fullScreen && !fullScreenChange
										? 0
										: PLAYER_IMAGE_OFFSET, Unit.PX);
								newImageStyle.setWidth(oldWidth, Unit.PX);
								newImageStyle.setHeight(oldHeight, Unit.PX);
								newImageStyle.setCursor(Cursor.POINTER);
								newImageStyle.setOpacity(0.0);

								if(newVideoStyle != null) {
									newVideoStyle.setWidth(oldWidth, Unit.PX);
									newVideoStyle.setHeight(oldHeight, Unit.PX);
								}

								if(!fullScreen && fullScreenChange) {
									computeImageSize(imagesOrVideos[currentIndex], imageSizeToFit, false, false);
									oldImageStyle.setTop(PLAYER_IMAGE_OFFSET, Unit.PX);
									oldImageStyle.setLeft(PLAYER_IMAGE_OFFSET, Unit.PX);
									oldImageStyle.setWidth(imageSizeToFit[0], Unit.PX);
									oldImageStyle.setHeight(imageSizeToFit[1], Unit.PX);

									if(media[0] instanceof Image)
										adjustImageSize(playerStyle, imageSizeToFit[0], imageSizeToFit[1], false, playerStyle);
									else
										adjustImageSize(playerStyle, imageSizeToFit[0], imageSizeToFit[1], false, playerStyle, oldVideoStyle);

									playerStyle.clearBorderStyle();
								}

								player.add(newMedia);
							}

							@Override
							protected void onUpdate(double progress) {
								int width = (int) (oldWidth + (newWidth - oldWidth) * progress + 0.5);
								int height = (int) (oldHeight + (newHeight - oldHeight) * progress + 0.5);

								oldImageStyle.setWidth(width, Unit.PX);
								oldImageStyle.setHeight(height, Unit.PX);

								if(oldVideoStyle != null) {
									oldVideoStyle.setWidth(width, Unit.PX);
									oldVideoStyle.setHeight(height, Unit.PX);
								}

								newImageStyle.setWidth(width, Unit.PX);
								newImageStyle.setHeight(height, Unit.PX);
								newImageStyle.setOpacity(progress);

								if(newVideoStyle != null) {
									newVideoStyle.setWidth(width, Unit.PX);
									newVideoStyle.setHeight(height, Unit.PX);
								}

								playerStyle.setWidth(width + (fullScreen && !fullScreenChange
										? 0
										: PLAYER_EXTRA_WIDTH), Unit.PX);
								playerStyle.setHeight(height + (fullScreen && !fullScreenChange
										? 0
										: PLAYER_EXTRA_HEIGHT), Unit.PX);
							}

							@Override
							protected void onComplete() {
								super.onComplete();

								newImageStyle.clearOpacity();

								player.remove(media[0]);
								media[0] = newMedia;

								Boolean downloadSelectorValue = selectionStatus(downloadSelectors, currentIndex, null);
								if(downloadSelectorValue != null && newMedia instanceof Image) {
									downloadSelector.setValue(downloadSelectorValue);
									downloadSelector.getElement().getStyle().clearDisplay();
								}

								if(currentIndex > 0)
									previous.addStyleName("enabled");

								if(currentIndex < imagesOrVideos.length - 1)
									next.addStyleName("enabled");

								toggleFullScreen.addStyleName("enabled");

								if(fullScreen && fullScreenChange) {
									computeImageSize(imagesOrVideos[currentIndex], imageSizeToFit, true, false);
									newImageStyle.setTop(0, Unit.PX);
									newImageStyle.setLeft(0, Unit.PX);
									newImageStyle.setWidth(imageSizeToFit[0], Unit.PX);
									newImageStyle.setHeight(imageSizeToFit[1], Unit.PX);
									if(newMedia instanceof Image)
										adjustImageSize(playerStyle, imageSizeToFit[0], imageSizeToFit[1], true, playerStyle);
									else
										adjustImageSize(playerStyle, imageSizeToFit[0], imageSizeToFit[1], true, playerStyle, newVideoStyle);

									playerStyle.setBorderStyle(BorderStyle.NONE);
								}
							}

						};

						animation.run(ANIMATION_DURATION);
					}

				});

	}

	private String smartSize(long originalFileSize) {
		if(originalFileSize < 1024)
			return originalFileSize + " bytes";

		if(originalFileSize < 1024 * 1024)
			return oneDecimal(originalFileSize / 1024.0) + " kB";

		return oneDecimal(originalFileSize / ((double) 1024 * 1024)) + " MB";
	}

	private String oneDecimal(double d) {
		int integer = (int) d;
		int fraction = (int) Math.round((d - integer) * 10);

		if(fraction == 10) {
			integer++;
			fraction = 0;
		}

		return integer + "." + fraction;
	}

	private void error(Throwable caught) {
		if(caught instanceof AccessDeniedException) {
			String roleToken = URL.decode(Window.Location.getHash());
			if(roleToken != null && roleToken.length() > 0 && roleToken.charAt(0) == '#')
				roleToken = roleToken.substring(1);

			String[] roleAndPath = parseToken(roleToken);
			error("Je nám líto, ale pro zobrazení tohoto obsahu nemáte oprávnění. Zkuste začít znovu v hlavní složce.", "#" + roleAndPath[0], true);
		}
		else
			error(caught.getMessage());
	}

	private void error(String message) {
		error(message, null, false);
	}

	private void error(String message, final String nextUrl, boolean forceMessage) {
		// Let's ignore the message, user is not interested in details - unless
		// force message is set to true
		message = forceMessage
				? message
				: "Došlo k chybě, galerie bude znovu načtena. Možnou příčinou je dlouhá doba nečinnosti a vypršení platnosti autorizace.";

		final PopupPanel panel = new PopupPanel(false, true);
		FlowPanel container = new FlowPanel();
		container.getElement().getStyle().setTextAlign(TextAlign.CENTER);
		container.setWidth("100%");
		panel.add(container);
		Label messageLabel = new Label(message);
		messageLabel.getElement().getStyle().setPadding(20.0, Unit.PX);
		messageLabel.getElement().getStyle().setProperty("maxWidth", "300px");
		Button okButton = new Button("OK");
		okButton.getElement().getStyle().setMarginBottom(20.0, Unit.PX);
		okButton.addClickHandler(new ClickHandler() {

			@Override
			public void onClick(ClickEvent event) {
				if(nextUrl == null)
					Window.Location.reload();
				else
					Window.Location.assign(nextUrl);
			}

		});

		container.add(messageLabel);
		container.add(okButton);

		panel.setGlassEnabled(true);

		// this is a workaround to prevent panel content to be reformatted on
		// resize by fixing the initially computed size
		panel.addAttachHandler(new Handler() {

			@Override
			public void onAttachOrDetach(AttachEvent event) {
				if(event.isAttached()) {
					panel.getElement().getStyle().setWidth(panel.getOffsetWidth(), Unit.PX);
				}
			}

		});

		panel.center();

		Window.addResizeHandler(new ResizeHandler() {

			@Override
			public void onResize(ResizeEvent event) {
				panel.center();
			}

		});
	}
}
