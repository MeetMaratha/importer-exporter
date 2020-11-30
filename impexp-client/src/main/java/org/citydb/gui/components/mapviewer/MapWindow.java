/*
 * 3D City Database - The Open Source CityGML Database
 * http://www.3dcitydb.org/
 *
 * Copyright 2013 - 2019
 * Chair of Geoinformatics
 * Technical University of Munich, Germany
 * https://www.gis.bgu.tum.de/
 *
 * The 3D City Database is jointly developed with the following
 * cooperation partners:
 *
 * virtualcitySYSTEMS GmbH, Berlin <http://www.virtualcitysystems.de/>
 * M.O.S.S. Computer Grafik Systeme GmbH, Taufkirchen <http://www.moss.de/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.citydb.gui.components.mapviewer;

import org.citydb.config.Config;
import org.citydb.config.geometry.BoundingBox;
import org.citydb.config.gui.window.GeocodingServiceName;
import org.citydb.config.gui.window.WindowSize;
import org.citydb.config.i18n.Language;
import org.citydb.config.project.database.DatabaseConfig;
import org.citydb.config.project.database.DatabaseConfig.PredefinedSrsName;
import org.citydb.config.project.database.DatabaseSrs;
import org.citydb.event.Event;
import org.citydb.event.EventHandler;
import org.citydb.event.global.EventType;
import org.citydb.gui.components.bbox.BoundingBoxClipboardHandler;
import org.citydb.gui.components.bbox.BoundingBoxListener;
import org.citydb.gui.components.mapviewer.geocoder.Geocoder;
import org.citydb.gui.components.mapviewer.geocoder.GeocoderResult;
import org.citydb.gui.components.mapviewer.geocoder.Location;
import org.citydb.gui.components.mapviewer.geocoder.LocationType;
import org.citydb.gui.components.mapviewer.geocoder.service.GeocodingService;
import org.citydb.gui.components.mapviewer.geocoder.service.GeocodingServiceException;
import org.citydb.gui.components.mapviewer.geocoder.service.GoogleGeocoder;
import org.citydb.gui.components.mapviewer.geocoder.service.OSMGeocoder;
import org.citydb.gui.components.mapviewer.map.DefaultWaypoint;
import org.citydb.gui.components.mapviewer.map.DefaultWaypoint.WaypointType;
import org.citydb.gui.components.mapviewer.map.Map;
import org.citydb.gui.components.mapviewer.map.event.BoundingBoxSelectionEvent;
import org.citydb.gui.components.mapviewer.map.event.MapBoundsSelectionEvent;
import org.citydb.gui.components.mapviewer.map.event.MapEvents;
import org.citydb.gui.components.mapviewer.map.event.ReverseGeocoderEvent;
import org.citydb.gui.components.mapviewer.map.event.ReverseGeocoderEvent.ReverseGeocoderStatus;
import org.citydb.gui.components.mapviewer.validation.BoundingBoxValidator;
import org.citydb.gui.components.mapviewer.validation.BoundingBoxValidator.ValidationResult;
import org.citydb.gui.factory.PopupMenuDecorator;
import org.citydb.gui.util.GuiUtil;
import org.citydb.log.Logger;
import org.citydb.plugin.extension.view.ViewController;
import org.citydb.registry.ObjectRegistry;
import org.jdesktop.swingx.mapviewer.AbstractTileFactory;
import org.jdesktop.swingx.mapviewer.GeoPosition;
import org.jdesktop.swingx.mapviewer.TileFactory;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.awt.Desktop.Action;
import java.awt.event.ItemEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

@SuppressWarnings("serial")
public class MapWindow extends JDialog implements EventHandler {
	private final Logger log = Logger.getInstance();
	private static MapWindow instance = null;
	public static DecimalFormat LAT_LON_FORMATTER = new DecimalFormat("##0.0000000", DecimalFormatSymbols.getInstance(Locale.ENGLISH));

	static {
		LAT_LON_FORMATTER.setMaximumIntegerDigits(3);
		LAT_LON_FORMATTER.setMinimumIntegerDigits(1);
		LAT_LON_FORMATTER.setMinimumFractionDigits(2);
		LAT_LON_FORMATTER.setMaximumFractionDigits(7);
	}

	private final Config config;

	private Map map;	
	private JComboBox<Location> searchBox;
	private JLabel searchResult;
	private ImageIcon loadIcon;
	private volatile boolean updateSearchBox = true;

	private JFormattedTextField minX;
	private JFormattedTextField minY;
	private JFormattedTextField maxX;
	private JFormattedTextField maxY;

	private JButton goButton;
	private JButton applyButton;
	private JButton cancelButton;
	private JButton copyBBox;
	private JButton pasteBBox;
	private JButton showBBox;
	private JButton clearBBox;

	private JLabel bboxTitel;
	private JLabel reverseTitle;
	private JLabel reverseInfo;
	private JTextPane reverseText;
	private JLabel reverseSearchProgress;

	private JLabel helpTitle;
	private JLabel helpText;

	private JLabel googleMapsTitle;
	private JButton googleMapsButton;

	private JLabel geocoderTitle;
	private JComboBox<GeocodingServiceName> geocoderCombo;

	private BoundingBoxListener listener;
	private BBoxPopupMenu[] bboxPopups;
	private JFrame mainFrame;
	private BoundingBoxClipboardHandler clipboardHandler;
	private BoundingBoxValidator validator;

	private MapWindow(ViewController viewController) {
		super(viewController.getTopFrame(), true);
		config = ObjectRegistry.getInstance().getConfig();

		// register for events
		ObjectRegistry.getInstance().getEventDispatcher().addEventHandler(EventType.SWITCH_LOCALE, this);
		ObjectRegistry.getInstance().getEventDispatcher().addEventHandler(MapEvents.BOUNDING_BOX_SELECTION, this);
		ObjectRegistry.getInstance().getEventDispatcher().addEventHandler(MapEvents.MAP_BOUNDS, this);
		ObjectRegistry.getInstance().getEventDispatcher().addEventHandler(MapEvents.REVERSE_GEOCODER, this);

		mainFrame = viewController.getTopFrame();
		clipboardHandler = BoundingBoxClipboardHandler.getInstance();
		validator = new BoundingBoxValidator(this, config);

		init();
		doTranslation();
	}

	public static synchronized MapWindow getInstance(ViewController viewController) {
		if (instance == null) {
			instance = new MapWindow(viewController);
		}

		instance.applyButton.setVisible(false);
		instance.listener = null;
		instance.clearBoundingBox();
		instance.setSizeOnScreen();

		// update geocoder
		GeocodingService service = null;
		try {
			service = instance.getGeocodingService(instance.config.getGuiConfig().getMapWindow().getGeocoder());
		} catch (GeocodingServiceException e) {
			service = new OSMGeocoder();
			instance.config.getGuiConfig().getMapWindow().setGeocoder(GeocodingServiceName.OSM_NOMINATIM);
		} finally {
			Geocoder.getInstance().setGeocodingService(service);
			instance.geocoderCombo.setSelectedItem(instance.config.getGuiConfig().getMapWindow().getGeocoder());
		}

		return instance;
	}

	private void init() {
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("/org/citydb/gui/images/map/map_icon.png")));

		setLayout(new GridBagLayout());
		getContentPane().setBackground(Color.WHITE);

		Color borderColor = new Color(0, 0, 0, 150);
		loadIcon = new ImageIcon(getClass().getResource("/org/citydb/gui/images/map/loader.gif"));

		map = new Map(config);
		JPanel top = new JPanel();
		JPanel left = new JPanel();

		// map
		map.getMapKit().setBorder(BorderFactory.createMatteBorder(1, 2, 0, 0, borderColor));

		GridBagConstraints gridBagConstraints = GuiUtil.setConstraints(0, 0, 1, 0, GridBagConstraints.BOTH, 0, 0, 0, 0);
		gridBagConstraints.gridwidth = 2;
		add(top, gridBagConstraints);
		add(left, GuiUtil.setConstraints(0, 1, 0, 0, GridBagConstraints.BOTH, 5, 5, 5, 5));
		add(map.getMapKit(), GuiUtil.setConstraints(1, 1, 1, 1, GridBagConstraints.BOTH, 0, 0, 0, 0));

		// top components
		top.setLayout(new GridBagLayout());
		top.setBackground(new Color(245, 245, 245));
		top.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor));

		goButton = new JButton();
		searchBox = new JComboBox<>();
		searchResult = new JLabel();
		searchResult.setPreferredSize(new Dimension(searchResult.getPreferredSize().width, loadIcon.getIconHeight()));

		searchBox.setEditable(true);
		searchBox.setPreferredSize(new Dimension(500, (int)searchBox.getPreferredSize().getHeight()));

		applyButton = new JButton();
		cancelButton = new JButton();
		applyButton.setFont(applyButton.getFont().deriveFont(Font.BOLD));
		applyButton.setEnabled(false);

		top.add(searchBox, GuiUtil.setConstraints(0, 0, 0, 0, GridBagConstraints.HORIZONTAL, 10, 10, 0, 5));
		top.add(goButton, GuiUtil.setConstraints(1, 0, 0, 0, GridBagConstraints.BOTH, 10, 5, 0, 10));
		top.add(Box.createHorizontalGlue(), GuiUtil.setConstraints(2, 0, 1, 0, GridBagConstraints.HORIZONTAL, 10, 5, 0, 0));
		top.add(applyButton, GuiUtil.setConstraints(3, 0, 0, 0, GridBagConstraints.BOTH, 10, 0, 0, 5));
		top.add(cancelButton, GuiUtil.setConstraints(4, 0, 0, 0, GridBagConstraints.BOTH, 10, 5, 0, 5));
		top.add(searchResult, GuiUtil.setConstraints(0, 1, 0, 0, GridBagConstraints.BOTH, 2, 10, 2, 10));

		// left components
		left.setLayout(new GridBagLayout());
		left.setBackground(Color.WHITE);
		Border componentBorder = BorderFactory.createCompoundBorder(UIManager.getBorder("TitledBorder.border"), BorderFactory.createEmptyBorder(5, 5, 5, 5));		

		// BBox
		final JPanel bbox = new JPanel();
		bbox.setBorder(componentBorder);
		bbox.setLayout(new GridBagLayout());	

		bboxTitel = new JLabel();
		bboxTitel.setFont(bbox.getFont().deriveFont(Font.BOLD));
		bboxTitel.setIcon(new ImageIcon(getClass().getResource("/org/citydb/gui/images/map/selection.png")));
		bboxTitel.setIconTextGap(5);

		final JPanel bboxFields = new JPanel();
		bboxFields.setLayout(new GridBagLayout());		

		minX = new JFormattedTextField(LAT_LON_FORMATTER);
		minY = new JFormattedTextField(LAT_LON_FORMATTER);
		maxX = new JFormattedTextField(LAT_LON_FORMATTER);
		maxY = new JFormattedTextField(LAT_LON_FORMATTER);

		minX.setBackground(Color.WHITE);
		minY.setBackground(Color.WHITE);
		maxX.setBackground(Color.WHITE);
		maxY.setBackground(Color.WHITE);

		Dimension dim = new Dimension(90, minX.getPreferredSize().height);		
		minX.setPreferredSize(dim);
		minY.setPreferredSize(dim);
		maxX.setPreferredSize(dim);
		maxY.setPreferredSize(dim);
		minX.setMinimumSize(dim);
		minY.setMinimumSize(dim);
		maxX.setMinimumSize(dim);
		maxY.setMinimumSize(dim);

		gridBagConstraints = GuiUtil.setConstraints(0, 0, 0, 0, GridBagConstraints.NONE, 5, 2, 0, 2);
		gridBagConstraints.gridwidth = 2;
		gridBagConstraints.anchor = GridBagConstraints.CENTER;		
		bboxFields.add(maxY, gridBagConstraints);
		gridBagConstraints.gridwidth = 1;
		gridBagConstraints.gridy = 1;
		gridBagConstraints.anchor = GridBagConstraints.EAST;
		bboxFields.add(minX, gridBagConstraints);
		gridBagConstraints.gridx = 1;
		gridBagConstraints.anchor = GridBagConstraints.WEST;
		bboxFields.add(maxX, gridBagConstraints);
		gridBagConstraints.gridwidth = 2;
		gridBagConstraints.gridy = 2;
		gridBagConstraints.gridx = 0;	
		gridBagConstraints.anchor = GridBagConstraints.CENTER;
		bboxFields.add(minY, gridBagConstraints);

		// BBox buttons
		JPanel bboxButtons = new JPanel();
		bboxButtons.setLayout(new GridBagLayout());
		bboxButtons.setBackground(bbox.getBackground());

		showBBox = new JButton();
		clearBBox = new JButton();

		copyBBox = new JButton();
		ImageIcon copyIcon = new ImageIcon(getClass().getResource("/org/citydb/gui/images/common/bbox_copy.png"));
		copyBBox.setIcon(copyIcon);
		copyBBox.setMargin(new Insets(1, 1, 1, 1));
		copyBBox.setEnabled(false);

		pasteBBox = new JButton();
		ImageIcon pasteIcon = new ImageIcon(getClass().getResource("/org/citydb/gui/images/common/bbox_paste.png"));
		pasteBBox.setIcon(pasteIcon);
		pasteBBox.setMargin(new Insets(1, 1, 1, 1));
		pasteBBox.setEnabled(clipboardHandler.containsPossibleBoundingBox());

		bboxButtons.add(showBBox, GuiUtil.setConstraints(0, 0, 1, 0, GridBagConstraints.HORIZONTAL, 0, 0, 0, 0));
		bboxButtons.add(clearBBox, GuiUtil.setConstraints(1, 0, 1, 0, GridBagConstraints.HORIZONTAL, 0, 5, 0, 0));

		Box bboxTitelBox = Box.createHorizontalBox();
		bboxTitelBox.add(bboxTitel);
		bboxTitelBox.add(Box.createHorizontalGlue());
		bboxTitelBox.add(copyBBox);
		bboxTitelBox.add(Box.createHorizontalStrut(5));
		bboxTitelBox.add(pasteBBox);

		bbox.add(bboxTitelBox, GuiUtil.setConstraints(0, 0, 1, 0, GridBagConstraints.HORIZONTAL, 0, 0, 2, 0));
		bbox.add(bboxFields, GuiUtil.setConstraints(0, 1, 1, 0, GridBagConstraints.HORIZONTAL, 5, 0, 5, 0));
		bbox.add(bboxButtons, GuiUtil.setConstraints(0, 2, 1, 0, GridBagConstraints.HORIZONTAL, 10, 0, 0, 0));

		// Reverse geocoder
		JPanel reverse = new JPanel();
		reverse.setBorder(componentBorder);
		reverse.setLayout(new GridBagLayout());

		reverseTitle = new JLabel();
		reverseTitle.setFont(reverseTitle.getFont().deriveFont(Font.BOLD));
		reverseTitle.setIcon(new ImageIcon(getClass().getResource("/org/citydb/gui/images/map/waypoint_small.png")));

		reverseTitle.setIconTextGap(5);
		reverseSearchProgress = new JLabel();
		reverseInfo = new JLabel();

		reverseText = new JTextPane();
		reverseText.setEditable(false);
		reverseText.setBorder(minX.getBorder());
		reverseText.setBackground(Color.WHITE);
		reverseText.setContentType("text/html");
		((HTMLDocument)reverseText.getDocument()).getStyleSheet().addRule(
				"body { font-family: " + reverseText.getFont().getFamily() + "; " + "font-size: " + reverseText.getFont().getSize() + "pt; }");
		reverseText.setVisible(false);

		Box reverseTitelBox = Box.createHorizontalBox();
		reverseTitelBox.add(reverseTitle);
		reverseTitelBox.add(Box.createHorizontalGlue());
		reverseTitelBox.add(reverseSearchProgress);

		reverse.add(reverseTitelBox, GuiUtil.setConstraints(0, 0, 1, 0, GridBagConstraints.HORIZONTAL, 0, 0, 2, 0));
		reverse.add(reverseText, GuiUtil.setConstraints(0, 1, 1, 0, GridBagConstraints.BOTH, 10, 0, 0, 0));
		reverse.add(reverseInfo, GuiUtil.setConstraints(0, 2, 0, 0, GridBagConstraints.HORIZONTAL, 10, 0, 0, 0));

		// Geocoder picker
		JPanel geocoder = new JPanel();
		geocoder.setBorder(componentBorder);
		geocoder.setLayout(new GridBagLayout());

		geocoderTitle = new JLabel();
		geocoderTitle.setFont(geocoderTitle.getFont().deriveFont(Font.BOLD));
		geocoderTitle.setIcon(new ImageIcon(getClass().getResource("/org/citydb/gui/images/map/magnifier.png")));

		geocoderCombo = new JComboBox<>();
		for (GeocodingServiceName serviceName : GeocodingServiceName.values())
			geocoderCombo.addItem(serviceName);

		geocoder.add(geocoderTitle, GuiUtil.setConstraints(0, 0, 1, 0, GridBagConstraints.HORIZONTAL, 0, 0, 2, 0));
		geocoder.add(geocoderCombo, GuiUtil.setConstraints(0, 1, 1, 0, GridBagConstraints.HORIZONTAL, 10, 0, 0, 0));

		// Google maps
		JPanel googleMaps = new JPanel();
		googleMaps.setBorder(componentBorder);
		googleMaps.setLayout(new GridBagLayout());

		googleMapsTitle = new JLabel();
		googleMapsTitle.setFont(googleMapsTitle.getFont().deriveFont(Font.BOLD));
		googleMapsTitle.setIcon(new ImageIcon(getClass().getResource("/org/citydb/gui/images/map/google_maps.png")));

		googleMapsButton = new JButton();
		googleMapsButton.setEnabled(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Action.BROWSE));

		googleMaps.add(googleMapsTitle, GuiUtil.setConstraints(0, 0, 0, 0, GridBagConstraints.HORIZONTAL, 0, 0, 2, 0));
		googleMaps.add(googleMapsButton, GuiUtil.setConstraints(1, 0, 1, 0, GridBagConstraints.NONE, 0, 5, 0, 0));

		// help
		JPanel help = new JPanel();
		help.setBorder(componentBorder);
		help.setLayout(new GridBagLayout());	

		helpTitle = new JLabel();
		helpTitle.setFont(help.getFont().deriveFont(Font.BOLD));
		helpTitle.setIcon(new ImageIcon(getClass().getResource("/org/citydb/gui/images/map/help.png")));
		helpTitle.setIconTextGap(5);		
		helpText = new JLabel();

		help.add(helpTitle, GuiUtil.setConstraints(0, 0, 1, 0, GridBagConstraints.HORIZONTAL, 0, 0, 2, 0));
		help.add(helpText, GuiUtil.setConstraints(0, 1, 0, 0, GridBagConstraints.BOTH, 10, 0, 0, 0));

		left.add(bbox, GuiUtil.setConstraints(0, 0, 1, 0, GridBagConstraints.BOTH, 5, 0, 5, 0));		
		left.add(reverse, GuiUtil.setConstraints(0, 1, 1, 0, GridBagConstraints.BOTH, 5, 0, 5, 0));
		left.add(geocoder, GuiUtil.setConstraints(0, 2, 1, 0, GridBagConstraints.BOTH, 5, 0, 5, 0));
		left.add(googleMaps, GuiUtil.setConstraints(0, 3, 1, 0, GridBagConstraints.BOTH, 5, 0, 5, 0));
		left.add(help, GuiUtil.setConstraints(0, 4, 1, 0, GridBagConstraints.BOTH, 5, 0, 5, 0));
		left.add(Box.createVerticalGlue(), GuiUtil.setConstraints(0, 5, 0, 1, GridBagConstraints.VERTICAL, 5, 0, 2, 0));

		left.setMinimumSize(left.getPreferredSize());
		left.setPreferredSize(left.getMinimumSize());

		// actions
		goButton.addActionListener(e -> {
			if (searchBox.getSelectedItem() != null)
				geocode(searchBox.getSelectedItem().toString());
		});

		searchBox.getEditor().addActionListener(e -> geocode(e.getActionCommand()));

		searchBox.addActionListener(e -> {
			if (updateSearchBox && !"comboBoxEdited".equals(e.getActionCommand())) {
				Object selectedItem = searchBox.getSelectedItem();
				if (selectedItem instanceof Location) {
					Location location = (Location)selectedItem;
					map.getMapKit().getMainMap().setZoom(1);

					HashSet<GeoPosition> viewPort = new HashSet<>(2);
					viewPort.add(location.getViewPort().getSouthWest());
					viewPort.add(location.getViewPort().getNorthEast());
					map.getMapKit().getMainMap().calculateZoomFrom(viewPort);

					WaypointType type = location.getLocationType() == LocationType.PRECISE ?
							WaypointType.PRECISE : WaypointType.APPROXIMATE;
					map.getWaypointPainter().showWaypoints(new DefaultWaypoint(location.getPosition(), type));
				}
			}
		});

		clearBBox.addActionListener(e -> clearBoundingBox());

		KeyAdapter showBBoxAdapter = new KeyAdapter() {
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER)
					showBoundingBox();
			}
		};

		minX.addKeyListener(showBBoxAdapter);
		minY.addKeyListener(showBBoxAdapter);
		maxX.addKeyListener(showBBoxAdapter);
		maxY.addKeyListener(showBBoxAdapter);

		showBBox.addActionListener(e -> showBoundingBox());
		copyBBox.addActionListener(e -> copyBoundingBoxToClipboard());
		pasteBBox.addActionListener(e -> pasteBoundingBoxFromClipboard());

		PopupMenuDecorator popupMenuDecorator = PopupMenuDecorator.getInstance();
		popupMenuDecorator.decorate((JComponent)searchBox.getEditor().getEditorComponent(), reverseText);

		// popup menu
		final JPopupMenu popupMenu = new JPopupMenu();
		bboxPopups = new BBoxPopupMenu[5];

		bboxPopups[0] = new BBoxPopupMenu(popupMenuDecorator.decorateAndGet(minX), true);
		bboxPopups[1] = new BBoxPopupMenu(popupMenuDecorator.decorateAndGet(minY), true);
		bboxPopups[2] = new BBoxPopupMenu(popupMenuDecorator.decorateAndGet(maxX), true);
		bboxPopups[3] = new BBoxPopupMenu(popupMenuDecorator.decorateAndGet(maxY), true);
		bboxPopups[4] = new BBoxPopupMenu(popupMenu, false);

		Toolkit.getDefaultToolkit().getSystemClipboard().addFlavorListener(e -> {
			boolean enable = clipboardHandler.containsPossibleBoundingBox();

			pasteBBox.setEnabled(enable);
			for (BBoxPopupMenu bboxPopup : bboxPopups)
				bboxPopup.paste.setEnabled(enable);
		});

		PropertyChangeListener valueChangedListener = evt -> {
			if (evt.getPropertyName().equals("value")) {
				if (evt.getOldValue() instanceof Number && evt.getNewValue() instanceof Number) {
					String oldValue = LAT_LON_FORMATTER.format(((Number)evt.getOldValue()).doubleValue());
					String newValue = LAT_LON_FORMATTER.format(((Number)evt.getNewValue()).doubleValue());
					if (oldValue.equals(newValue))
						return;
				}

				try {
					minX.commitEdit();
					minY.commitEdit();
					maxX.commitEdit();
					maxY.commitEdit();

					GeoPosition southWest = new GeoPosition(((Number)minY.getValue()).doubleValue(), ((Number)minX.getValue()).doubleValue());
					GeoPosition northEast = new GeoPosition(((Number)maxY.getValue()).doubleValue(), ((Number)maxX.getValue()).doubleValue());

					setEnabledApplyBoundingBox(map.getSelectionPainter().isVisibleOnScreen(southWest, northEast));
				} catch (ParseException e) {
					//
				}
			}
		};

		minX.addPropertyChangeListener(valueChangedListener);
		minY.addPropertyChangeListener(valueChangedListener);
		maxX.addPropertyChangeListener(valueChangedListener);
		maxY.addPropertyChangeListener(valueChangedListener);

		bbox.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				showPopupMenu(e);
			}

			public void mouseReleased(MouseEvent e) {
				showPopupMenu(e);
			}

			private void showPopupMenu(MouseEvent e) {
				if (e.isPopupTrigger()) {
					popupMenu.show(e.getComponent(), e.getX(), e.getY());
					popupMenu.setInvoker(bbox);
				}
			}
		});

		addWindowListener(new WindowAdapter() {
			public void windowClosed(WindowEvent e) {
				// clear map cache
				((AbstractTileFactory)map.getMapKit().getMainMap().getTileFactory()).clearTileCache();
				((AbstractTileFactory)map.getMapKit().getMainMap().getTileFactory()).shutdownTileServicePool();
				((AbstractTileFactory)map.getMapKit().getMiniMap().getTileFactory()).clearTileCache();
				((AbstractTileFactory)map.getMapKit().getMiniMap().getTileFactory()).shutdownTileServicePool();

				WindowSize size = config.getGuiConfig().getMapWindow().getSize();
				Rectangle rect = MapWindow.this.getBounds();
				size.setX(rect.x);
				size.setY(rect.y);
				size.setWidth(rect.width);
				size.setHeight(rect.height);
			}
		});

		applyButton.addActionListener(e -> {
			double xmin = ((Number)minX.getValue()).doubleValue();
			double xmax = ((Number)maxX.getValue()).doubleValue();
			double ymin = ((Number)minY.getValue()).doubleValue();
			double ymax = ((Number)maxY.getValue()).doubleValue();

			final BoundingBox bbox1 = new BoundingBox();
			bbox1.getLowerCorner().setX(Math.min(xmin, xmax));
			bbox1.getLowerCorner().setY(Math.min(ymin, ymax));
			bbox1.getUpperCorner().setX(Math.max(xmin, xmax));
			bbox1.getUpperCorner().setY(Math.max(ymin, ymax));

			DatabaseSrs wgs84 = null;
			for (DatabaseSrs srs : config.getDatabaseConfig().getReferenceSystems()) {
				if (srs.getSrid() == DatabaseConfig.PREDEFINED_SRS.get(PredefinedSrsName.WGS84_2D).getSrid()) {
					wgs84 = srs;
					break;
				}
			}

			bbox1.setSrs(wgs84);

			Thread t = new Thread(() -> listener.setBoundingBox(bbox1));
			t.setDaemon(true);
			t.start();

			copyBoundingBoxToClipboard();
			dispose();
		});

		cancelButton.addActionListener(e -> dispose());

		geocoderCombo.addItemListener(l -> {
			if (l.getStateChange() == ItemEvent.SELECTED
					&& geocoderCombo.getSelectedItem() != config.getGuiConfig().getMapWindow().getGeocoder()) {
				try {
					GeocodingService service = getGeocodingService((GeocodingServiceName) geocoderCombo.getSelectedItem());
					Geocoder.getInstance().setGeocodingService(service);
				} catch (GeocodingServiceException e) {
					SwingUtilities.invokeLater(() -> {
						JOptionPane.showMessageDialog(this, e.getMessage(),
								Language.I18N.getString("map.error.geocoder.title"), JOptionPane.ERROR_MESSAGE);
					});

					geocoderCombo.setSelectedItem(config.getGuiConfig().getMapWindow().getGeocoder());
				}
			}
		});

		googleMapsButton.addActionListener(e -> {
			Rectangle view = map.getMapKit().getMainMap().getViewportBounds();
			TileFactory fac = map.getMapKit().getMainMap().getTileFactory();
			int zoom = map.getMapKit().getMainMap().getZoom();

			GeoPosition centerPoint = fac.pixelToGeo(new Point2D.Double(view.getCenterX(), view.getCenterY()), zoom);
			GeoPosition southWest = fac.pixelToGeo(new Point2D.Double(view.getMinX(), view.getMaxY()), zoom);
			GeoPosition northEast = fac.pixelToGeo(new Point2D.Double(view.getMaxX(), view.getMinY()), zoom);

			final StringBuilder url = new StringBuilder();
			url.append("http://maps.google.de/maps?");

			if (searchBox.getSelectedItem() instanceof Location) {
				String query = ((Location)searchBox.getSelectedItem()).getFormattedAddress();

				try {
					url.append("&q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8.displayName()));
				} catch (UnsupportedEncodingException e1) {
					//
				}
			}

			url.append("&ll=").append(centerPoint.getLatitude()).append(",").append(centerPoint.getLongitude());
			url.append("&spn=").append((northEast.getLatitude() - southWest.getLatitude()) / 2).append(",").append((northEast.getLongitude() - southWest.getLongitude()) / 2);
			url.append("&sspn=").append(northEast.getLatitude() - southWest.getLatitude()).append(",").append(northEast.getLongitude() - southWest.getLongitude());
			url.append("&t=m");

			SwingUtilities.invokeLater(() -> {
				try {
					Desktop.getDesktop().browse(new URI(url.toString()));
				} catch (IOException e1) {
					log.error("Failed to launch default browser.");
				} catch (URISyntaxException e1) {
					//
				}
			});
		});
	}

	public MapWindow withBoundingBoxListener(BoundingBoxListener listener) {
		this.listener = listener;
		applyButton.setVisible(listener != null);
		return this;
	}

	public MapWindow withBoundingBox(final BoundingBox bbox) {
		new SwingWorker<ValidationResult, Void>() {
			protected ValidationResult doInBackground() throws Exception {
				return validator.validate(bbox);
			}

			protected void done() {
				try {
					switch (get()) {
						case CANCEL:
							dispose();
							break;
						case SKIP:
						case OUT_OF_RANGE:
						case NO_AREA:
							clearBoundingBox();
							break;
						case INVISIBLE:
							clearBoundingBox();
							indicateInvisibleBoundingBox(bbox);
							break;
						default:
							minX.setValue(bbox.getLowerCorner().getX());
							minY.setValue(bbox.getLowerCorner().getY());
							maxX.setValue(bbox.getUpperCorner().getX());
							maxY.setValue(bbox.getUpperCorner().getY());
							showBoundingBox();
					}
				} catch (InterruptedException | ExecutionException e) {
					//
				}
			}
		}.execute();

		return this;
	}

	public boolean isBoundingBoxVisible(BoundingBox bbox) {
		GeoPosition southWest = new GeoPosition(bbox.getLowerCorner().getY(), bbox.getLowerCorner().getX());
		GeoPosition northEast = new GeoPosition(bbox.getUpperCorner().getY(), bbox.getUpperCorner().getX());
		return map.getSelectionPainter().isVisibleOnScreen(southWest, northEast);
	}

	private void copyBoundingBoxToClipboard() {
		try {
			minX.commitEdit();
			minY.commitEdit();
			maxX.commitEdit();
			maxY.commitEdit();

			BoundingBox bbox = new BoundingBox();
			bbox.getLowerCorner().setX(minX.isEditValid() && minX.getValue() != null ? ((Number)minX.getValue()).doubleValue() : null);
			bbox.getLowerCorner().setY(minY.isEditValid() && minY.getValue() != null ? ((Number)minY.getValue()).doubleValue() : null);
			bbox.getUpperCorner().setX(maxX.isEditValid() && maxX.getValue() != null ? ((Number)maxX.getValue()).doubleValue() : null);
			bbox.getUpperCorner().setY(maxY.isEditValid() && maxY.getValue() != null ? ((Number)maxY.getValue()).doubleValue() : null);

			for (DatabaseSrs srs : config.getDatabaseConfig().getReferenceSystems()) {
				if (srs.getSrid() == DatabaseConfig.PREDEFINED_SRS.get(PredefinedSrsName.WGS84_2D).getSrid()) {
					bbox.setSrs(srs);
					break;
				}
			}

			clipboardHandler.putBoundingBox(bbox);			
		} catch (ParseException e) {
			//
		}
	}

	private void pasteBoundingBoxFromClipboard() {
		withBoundingBox(clipboardHandler.getBoundingBox());
	}

	private void showBoundingBox() {
		try {
			minX.commitEdit();
			minY.commitEdit();
			maxX.commitEdit();
			maxY.commitEdit();

			GeoPosition southWest = new GeoPosition(((Number)minY.getValue()).doubleValue(), ((Number)minX.getValue()).doubleValue());
			GeoPosition northEast = new GeoPosition(((Number)maxY.getValue()).doubleValue(), ((Number)maxX.getValue()).doubleValue());

			if (map.getSelectionPainter().setBoundingBox(southWest, northEast)) {
				HashSet<GeoPosition> positions = new HashSet<>();
				positions.add(southWest);
				positions.add(northEast);
				map.getMapKit().setZoom(1);
				map.getMapKit().getMainMap().calculateZoomFrom(positions);
			} else
				map.getSelectionPainter().clearBoundingBox();
		} catch (ParseException e) {
			//
		}
	}
	
	private void indicateInvisibleBoundingBox(BoundingBox bbox) {
		double x = bbox.getLowerCorner().getX() + (bbox.getUpperCorner().getX() - bbox.getLowerCorner().getX()) / 2;
		double y = bbox.getLowerCorner().getY() + (bbox.getUpperCorner().getY() - bbox.getLowerCorner().getY()) / 2;

		GeoPosition pos = new GeoPosition(y, x);
		map.getWaypointPainter().clearWaypoints();
		map.getWaypointPainter().showWaypoints(new DefaultWaypoint(pos, WaypointType.PRECISE));

		map.getMapKit().setZoom(map.getMapKit().getMainMap().getTileFactory().getInfo().getMinimumZoomLevel());
		map.getMapKit().setCenterPosition(pos);
	}

	private void clearBoundingBox() {
		map.getSelectionPainter().clearBoundingBox();
		minX.setValue(null);
		maxX.setValue(null);
		minY.setValue(null);
		maxY.setValue(null);
		setEnabledApplyBoundingBox(false);
	}

	private void setEnabledApplyBoundingBox(boolean enable) {
		applyButton.setEnabled(enable);
		copyBBox.setEnabled(enable);
		for (BBoxPopupMenu bboxPopup : bboxPopups)
			bboxPopup.copy.setEnabled(enable);
	}

	private void geocode(final String address) {
		searchResult.setIcon(loadIcon);
		searchResult.setText("");
		searchResult.repaint();

		final long time = System.currentTimeMillis();
		new SwingWorker<GeocoderResult, Void>() {
			protected GeocoderResult doInBackground() throws Exception {
				return Geocoder.getInstance().geocode(address);
			}

			protected void done() {
				try {
					GeocoderResult result = get();

					searchBox.removeAllItems();
					if (result.isSetLocations()) {
						for (Location location : result.getLocations())
							searchBox.addItem(location);

						searchBox.setSelectedItem(result.getLocations().get(0));
					}

					String text = Language.I18N.getString("map.geocoder.search.result");
					Object[] args = new Object[]{result.getLocations().size()};
					String resultMsg = MessageFormat.format(text, args)
							+ " (" + ((System.currentTimeMillis() - time) / 1000.0) + " s)";

					searchResult.setText(resultMsg);
				} catch (InterruptedException | ExecutionException e) {
					if (e.getCause() instanceof GeocodingServiceException) {
						GeocodingServiceException exception = (GeocodingServiceException) e.getCause();
						searchResult.setText("The geocoder failed due to an error. Check the console log.");
						log.error("The geocoder failed due to an error.");
						for (String message : exception.getMessages())
							log.error("Cause: " + message);
					} else {
						log.error("An error occured while calling the geocoding service.");
						log.error("Caused by: " + e.getMessage());
					}
				} finally {
					searchResult.setIcon(null);
				}
			}
		}.execute();
	}

	private GeocodingService getGeocodingService(GeocodingServiceName serviceName) throws GeocodingServiceException {
		GeocodingService service = null;

		if (serviceName == GeocodingServiceName.OSM_NOMINATIM)
			service = new OSMGeocoder();
		else if (serviceName == GeocodingServiceName.GOOGLE_GEOCODING_API) {
			if (config.getGlobalConfig().getApiKeys().isSetGoogleGeocoding())
				service = new GoogleGeocoder(config.getGlobalConfig().getApiKeys().getGoogleGeocoding());
			else {
				Logger.getInstance().error("Failed to initialize geocoder '" + serviceName.toString() + "' due to a missing API key.");
				throw new GeocodingServiceException(MessageFormat.format(Language.I18N.getString("map.error.geocoder.apiKey"), serviceName));
			}
		}

		if (service != null)
			config.getGuiConfig().getMapWindow().setGeocoder(serviceName);

		return service;
	}

	private void setSizeOnScreen() {
		WindowSize size = config.getGuiConfig().getMapWindow().getSize();

		Integer x = size.getX();
		Integer y = size.getY();
		Integer width = size.getWidth();
		Integer height = size.getHeight();

		// create default values for main window
		if (x == null || y == null || width == null || height == null) {
			x = mainFrame.getLocation().x + 10;
			y = mainFrame.getLocation().y + 10;
			width = 1024;
			height = 768;

			Toolkit t = Toolkit.getDefaultToolkit();
			Insets frame_insets = t.getScreenInsets(mainFrame.getGraphicsConfiguration());
			int frame_insets_x = frame_insets.left + frame_insets.right;
			int frame_insets_y = frame_insets.bottom + frame_insets.top;

			Rectangle bounds = mainFrame.getGraphicsConfiguration().getBounds();

			if (!bounds.contains(x, y, width + frame_insets_x, height + frame_insets_y)) {
				// check width
				if (x + width + frame_insets_x > bounds.width || y + height + frame_insets_y > bounds.height) {
					x = frame_insets.left;
					y = frame_insets.top;

					if (width + frame_insets_x > bounds.width)
						width = bounds.width - frame_insets_x;

					if (height + frame_insets_y > bounds.height)
						height = bounds.height - frame_insets_y;
				}
			}
		}

		setLocation(x, y);
		setSize(new Dimension(width, height));
	}

	private void doTranslation() {
		setTitle(Language.I18N.getString("map.window.title"));
		applyButton.setText(Language.I18N.getString("common.button.apply"));
		cancelButton.setText(Language.I18N.getString("common.button.cancel"));
		goButton.setText(Language.I18N.getString("map.button.go"));
		bboxTitel.setText(Language.I18N.getString("map.boundingBox.label"));
		showBBox.setText(Language.I18N.getString("map.boundingBox.show.button"));
		showBBox.setToolTipText(Language.I18N.getString("map.boundingBox.show.tooltip"));
		clearBBox.setText(Language.I18N.getString("map.boundingBox.clear.button"));
		clearBBox.setToolTipText(Language.I18N.getString("map.boundingBox.clear.tooltip"));
		copyBBox.setToolTipText(Language.I18N.getString("common.tooltip.boundingBox.copy"));
		pasteBBox.setToolTipText(Language.I18N.getString("common.tooltip.boundingBox.paste"));
		reverseTitle.setText(Language.I18N.getString("map.reverseGeocoder.label"));
		reverseInfo.setText("<html>" + Language.I18N.getString("map.reverseGeocoder.hint.label") + "</html>");
		geocoderTitle.setText(Language.I18N.getString("map.geocoder.label"));
		helpTitle.setText(Language.I18N.getString("map.help.label"));
		helpText.setText("<html>" + Language.I18N.getString("map.help.hint") + "</html>");
		googleMapsButton.setText(Language.I18N.getString("map.google.label"));

		map.doTranslation();
		for (BBoxPopupMenu bboxPopup : bboxPopups)
			bboxPopup.doTranslation();
	}

	@Override
	public void handleEvent(Event event) throws Exception {
		if (event.getEventType() == EventType.SWITCH_LOCALE) {
			doTranslation();
		}

		else if (event.getEventType() == MapEvents.BOUNDING_BOX_SELECTION) {
			BoundingBoxSelectionEvent e = (BoundingBoxSelectionEvent)event;
			final GeoPosition[] bbox = e.getBoundingBox();

			SwingUtilities.invokeLater(() -> {
				// avoid property listener on text fields to be fired
				// before setting the last value
				maxY.setValue(null);

				minX.setValue(bbox[0].getLatitude());
				minY.setValue(bbox[0].getLongitude());
				maxX.setValue(bbox[1].getLatitude());
				maxY.setValue(bbox[1].getLongitude());
			});
		}

		else if (event.getEventType() == MapEvents.MAP_BOUNDS) {
			MapBoundsSelectionEvent e = (MapBoundsSelectionEvent)event;
			final GeoPosition[] bbox = e.getBoundingBox();

			SwingUtilities.invokeLater(() -> {
				// avoid property listener on text fields to be fired
				// before setting the last value
				maxY.setValue(null);

				minX.setValue(bbox[0].getLongitude());
				minY.setValue(bbox[0].getLatitude());
				maxX.setValue(bbox[1].getLongitude());
				maxY.setValue(bbox[1].getLatitude());
				map.getSelectionPainter().setBoundingBox(bbox[0], bbox[1]);
			});
		}

		else if (event.getEventType() == MapEvents.REVERSE_GEOCODER) {
			ReverseGeocoderEvent e = (ReverseGeocoderEvent)event;

			SwingUtilities.invokeLater(() -> {
				if (e.getStatus() == ReverseGeocoderStatus.SEARCHING) {
					reverseSearchProgress.setIcon(loadIcon);

				} else if (e.getStatus() == ReverseGeocoderStatus.RESULT) {
					Location location = e.getLocation();
					reverseText.setText(location.getFormattedAddress());
					reverseText.setVisible(true);
					reverseInfo.setVisible(false);
					reverseSearchProgress.setIcon(null);

					location.setFormattedAddress(location.getPosition().getLatitude() + ", " + location.getPosition().getLongitude());
					updateSearchBox = false;
					searchBox.setSelectedItem(location);
					updateSearchBox = true;

				} else {
					reverseText.setVisible(false);
					reverseInfo.setVisible(true);
					reverseSearchProgress.setIcon(null);

					if (e.getStatus() == ReverseGeocoderStatus.ERROR) {
						reverseInfo.setText("<html>The geocoder failed due to an error. Check the console log.</html>");
						GeocodingServiceException exception = e.getException();
						log.error("The geocoder failed due to an error.");
						for (String message : exception.getMessages())
							log.error("Cause: " + message);
					} else
						reverseInfo.setText("<html>No address found at this location.</html>");
				}
			});
		}
	}

	private final class BBoxPopupMenu extends JPopupMenu {
		private JMenuItem copy;	
		private JMenuItem paste;

		BBoxPopupMenu(JPopupMenu popupMenu, boolean addSeparator) {
			copy = new JMenuItem();	
			paste = new JMenuItem();

			copy.setEnabled(false);
			paste.setEnabled(clipboardHandler.containsPossibleBoundingBox());

			if (addSeparator) popupMenu.addSeparator();
			popupMenu.add(copy);
			popupMenu.add(paste);

			copy.addActionListener(e -> copyBoundingBoxToClipboard());
			paste.addActionListener(e -> pasteBoundingBoxFromClipboard());
		}

		private void doTranslation() {
			copy.setText(Language.I18N.getString("common.popup.boundingBox.copy"));
			paste.setText(Language.I18N.getString("common.popup.boundingBox.paste"));
		}
	}

}
