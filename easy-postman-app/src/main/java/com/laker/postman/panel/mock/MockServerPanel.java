package com.laker.postman.panel.mock;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.laker.postman.common.UiSingletonPanel;
import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.common.component.AppToolWindowChrome;
import com.laker.postman.common.component.FallbackAwareRSyntaxTextArea;
import com.laker.postman.common.component.SearchableTextArea;
import com.laker.postman.common.component.ToolWindowActionToolbar;
import com.laker.postman.common.component.ToolWindowSidebarHeader;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.CopyButton;
import com.laker.postman.common.component.button.EditButton;
import com.laker.postman.common.component.button.ModernButtonFactory;
import com.laker.postman.common.component.button.PlusButton;
import com.laker.postman.common.component.button.RefreshButton;
import com.laker.postman.common.component.notification.NotificationCenter;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.ioc.BeanFactory;
import com.laker.postman.mock.app.MockCollectionChoice;
import com.laker.postman.mock.app.MockCollectionRouteProvider;
import com.laker.postman.mock.app.MockNetworkAddressResolver;
import com.laker.postman.mock.app.MockRouteConflicts;
import com.laker.postman.mock.app.MockRouteEntry;
import com.laker.postman.mock.app.MockServerManager;
import com.laker.postman.mock.model.MockCallLog;
import com.laker.postman.mock.model.MockServerDefinition;
import com.laker.postman.panel.collections.tree.CollectionTreePanel;
import com.laker.postman.util.EditorThemeUtil;
import com.laker.postman.util.CommonI18n;
import com.laker.postman.util.CommonMessageKeys;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.IconUtil;
import com.laker.postman.util.MessageKeys;
import net.miginfocom.swing.MigLayout;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Local Mock Server management surface, hosted as a top-level sidebar menu.
 */
public class MockServerPanel extends UiSingletonPanel {
    private static final String EMPTY_CARD = "empty";
    private static final String DETAIL_CARD = "detail";
    private static final int LOGS_TAB_INDEX = 2;
    private static final int STATE_TAB_INDEX = 3;
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    private final MockServerManager manager = BeanFactory.getBean(MockServerManager.class);
    private final MockCollectionRouteProvider routeProvider = BeanFactory.getBean(MockCollectionRouteProvider.class);
    private final MockRouteEditorController routeEditorController = new MockRouteEditorController(routeProvider, manager);
    private final DefaultListModel<MockServerDefinition> serverListModel = new DefaultListModel<>();
    private final JList<MockServerDefinition> serverList = new JList<>(serverListModel) {
        @Override
        public String getToolTipText(MouseEvent event) {
            int row = locationToIndex(event.getPoint());
            Rectangle bounds = row < 0 ? null : getCellBounds(row, row);
            if (bounds == null || !bounds.contains(event.getPoint())) return null;
            MockServerDefinition server = getModel().getElementAt(row);
            String status = I18nUtil.getMessage(manager.isRunning(server.getId())
                    ? MessageKeys.MOCK_SERVER_STATUS_RUNNING
                    : MessageKeys.MOCK_SERVER_STATUS_STOPPED);
            return server.getName() + " — " + status + " — "
                    + MockNetworkAddressResolver.accessUrl(server, server.getPort());
        }
    };
    private final RouteTableModel routeTableModel = new RouteTableModel();
    private final LogTableModel logTableModel = new LogTableModel();
    private final StateTableModel stateTableModel = new StateTableModel();
    private final JTable routeTable = new JTable(routeTableModel);
    private final JTable logTable = new JTable(logTableModel);
    private final JTable stateTable = new JTable(stateTableModel);
    private final RSyntaxTextArea scriptEditor = new FallbackAwareRSyntaxTextArea(12, 60);
    private final JTextArea logDetail = new JTextArea();
    private final JLabel baseUrlLabel = new JLabel();
    private final JLabel exampleCountLabel = new JLabel();
    private final Icon runningStatusIcon = semanticIcon("icons/check.svg", ModernColors::getSuccess);
    private final Icon stoppedStatusIcon = semanticIcon("icons/nocheck.svg", ModernColors::getTextSecondary);
    private MockRouteConflicts routeConflicts = new MockRouteConflicts(0, Set.of());
    private final Timer refreshTimer = new Timer(1_000, event -> refreshRuntimeData());
    private final CardLayout detailCardLayout = new CardLayout();
    private final JPanel detailCards = new JPanel(detailCardLayout);

    private JButton addButton;
    private JButton emptyAddButton;
    private JButton editButton;
    private JButton deleteButton;
    private JButton runToggleButton;
    private final Icon startActionIcon = IconUtil.createThemed("icons/start.svg", 20, 20);
    private final Icon stopActionIcon = IconUtil.createThemed("icons/stop.svg", 20, 20);
    private JButton refreshButton;
    private JButton copyButton;
    private JButton deploymentButton;
    private JButton saveScriptButton;
    private JButton addRouteButton;
    private JButton addResponseButton;
    private JButton editRouteButton;
    private JButton deleteResponseButton;
    private JButton conflictButton;
    private JLabel detailTitleLabel;
    private JTabbedPane detailTabs;
    private boolean loadingScript;
    private boolean loadingDefinitions;
    private boolean listenersRegistered;
    private long displayedDefinitionsRevision = -1;

    @Override
    protected void initUI() {
        // Route data is supplied by the active collection tree even when the
        // Collections sidebar tab has not been opened in this session yet.
        UiSingletonFactory.getInstance(CollectionTreePanel.class);
        setLayout(new BorderLayout());
        ToolWindowSurfaceStyle.applyBackground(this);
        add(createMainContent(), BorderLayout.CENTER);
        reloadDefinitions(null);
        refreshTimer.setCoalesce(true);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        refreshRuntimeData();
        refreshTimer.start();
    }

    @Override
    public void removeNotify() {
        refreshTimer.stop();
        super.removeNotify();
    }

    public void switchWorkspaceAndRefreshUI() {
        manager.reloadWorkspace();
        reloadDefinitions(null);
    }

    private JComponent createMainContent() {
        JPanel left = createServerSidebar();
        JPanel right = createDetailCards();

        JSplitPane splitPane = AppToolWindowChrome.createHorizontalCardSplitPane(
                left,
                right,
                AppToolWindowChrome.DEFAULT_SIDE_WIDTH
        );
        splitPane.setResizeWeight(0.0);
        return splitPane;
    }

    private JPanel createServerSidebar() {
        JPanel left = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyCard(left);
        addButton = new PlusButton();
        left.add(new ToolWindowSidebarHeader(
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_LIST_TITLE), addButton), BorderLayout.NORTH);

        serverList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        serverList.setToolTipText("");
        serverList.setCellRenderer(new ServerRenderer());
        serverList.setFixedCellHeight(52);
        JScrollPane serverScroll = new JScrollPane(serverList);
        serverScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        ToolWindowSurfaceStyle.applyListScrollPaneCard(serverScroll, serverList);
        left.add(serverScroll, BorderLayout.CENTER);
        left.setMinimumSize(new Dimension(220, 160));
        return left;
    }

    private JPanel createDetailCards() {
        ToolWindowSurfaceStyle.applyCard(detailCards);
        detailCards.add(createEmptyState(), EMPTY_CARD);
        detailCards.add(createDetailPanel(), DETAIL_CARD);
        detailCardLayout.show(detailCards, EMPTY_CARD);
        return detailCards;
    }

    private JComponent createEmptyState() {
        JPanel empty = new JPanel(new MigLayout(
                "insets 32 24 32 24,fill,novisualpadding",
                "[grow,center]",
                "[grow]14[]14[]8[]20[]14[grow]"
        ));
        // detailCards owns the rounded card chrome; this child only lays out the empty-state content.
        empty.setOpaque(false);

        JLabel icon = new JLabel(IconUtil.createPrimary("icons/mock-server.svg", 44, 44));
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel title = new JLabel(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_EMPTY_TITLE));
        title.setHorizontalAlignment(SwingConstants.CENTER);
        title.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, 1));
        JLabel hint = new JLabel("<html><div style='text-align:center;width:420px'>"
                + I18nUtil.getMessage(MessageKeys.MOCK_SERVER_CREATE_HINT) + "</div></html>");
        hint.setHorizontalAlignment(SwingConstants.CENTER);
        hint.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        hint.setForeground(ModernColors.getTextSecondary());
        emptyAddButton = ModernButtonFactory.createCompactButton(
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_CREATE), true, "icons/plus.svg");

        empty.add(icon, "cell 0 1,alignx center");
        empty.add(title, "cell 0 2,alignx center");
        empty.add(hint, "cell 0 3,alignx center");
        empty.add(emptyAddButton, "cell 0 4,alignx center");
        return empty;
    }

    private JComponent createDetailPanel() {
        JPanel detail = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyCard(detail);
        detail.add(createDetailHeader(), BorderLayout.NORTH);

        detailTabs = new JTabbedPane(SwingConstants.TOP);
        ToolWindowSurfaceStyle.applyTabbedPaneCard(detailTabs);
        detailTabs.addTab(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTES), createRoutesPanel());
        detailTabs.addTab(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_GLOBAL_SCRIPT), createScriptPanel());
        detailTabs.addTab(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_LOGS), createLogsPanel());
        detailTabs.addTab(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_STATE), createStatePanel());
        detail.add(detailTabs, BorderLayout.CENTER);
        return detail;
    }

    private JComponent createDetailHeader() {
        JPanel header = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyCard(header);

        JPanel identity = new JPanel(new MigLayout(
                "insets 8 10 6 10, fillx, novisualpadding",
                "[grow,fill]12[shrink]4[]",
                "[]"
        ));
        ToolWindowSurfaceStyle.applySectionHeader(identity);
        detailTitleLabel = new JLabel();
        detailTitleLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, 1));
        identity.add(detailTitleLabel, "wmin 0");

        baseUrlLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        baseUrlLabel.setForeground(ModernColors.getTextSecondary());
        baseUrlLabel.setToolTipText(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_LOCAL_HINT));
        copyButton = new CopyButton();
        copyButton.setToolTipText(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_COPY_URL));
        copyButton.getAccessibleContext().setAccessibleName(copyButton.getToolTipText());
        identity.add(baseUrlLabel, "wmin 0,alignx right");
        identity.add(copyButton);

        runToggleButton = toolbarButton(CommonI18n.get(CommonMessageKeys.BUTTON_START), "icons/start.svg");
        runToggleButton.setIcon(startActionIcon);
        refreshButton = new RefreshButton();
        refreshButton.setToolTipText(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_REFRESH));
        refreshButton.getAccessibleContext().setAccessibleName(refreshButton.getToolTipText());
        editButton = new EditButton();
        deleteButton = toolbarButton(CommonI18n.get(CommonMessageKeys.BUTTON_DELETE), "icons/delete.svg");
        deploymentButton = toolbarButton(
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_COPY_DEPLOY_COMMAND), "icons/code.svg");

        JPanel actions = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applySectionHeader(actions);
        ToolWindowSurfaceStyle.applyToolWindowToolbarSeparator(actions, 1, 0, 0, 0);
        actions.add(ToolWindowActionToolbar.left(runToggleButton, refreshButton), BorderLayout.WEST);
        actions.add(ToolWindowActionToolbar.right(deploymentButton, editButton, deleteButton), BorderLayout.EAST);

        header.add(identity, BorderLayout.NORTH);
        header.add(actions, BorderLayout.SOUTH);
        return header;
    }

    private JButton toolbarButton(String tooltip, String iconPath) {
        JButton button = new JButton(IconUtil.createThemed(iconPath, IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
        button.setToolTipText(tooltip);
        button.getAccessibleContext().setAccessibleName(tooltip);
        button.setFocusable(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setMargin(new Insets(0, 0, 0, 0));
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        return button;
    }

    private JComponent createRoutesPanel() {
        configureTable(routeTable);
        routeTable.getColumnModel().getColumn(0).setMaxWidth(80);
        routeTable.getColumnModel().getColumn(5).setMaxWidth(95);
        routeTable.getColumnModel().getColumn(6).setMaxWidth(90);
        routeTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        routeTable.getColumnModel().getColumn(4).setPreferredWidth(180);
        routeTable.getColumnModel().getColumn(4).setMaxWidth(260);
        routeTable.getColumnModel().getColumn(0).setCellRenderer(new RouteMethodRenderer());
        routeTable.getColumnModel().getColumn(4).setCellRenderer(new RouteResponseRenderer());
        routeTable.getColumnModel().getColumn(5).setCellRenderer(new RouteStatusRenderer());
        routeTable.getColumnModel().getColumn(6).setCellRenderer(new RouteModeRenderer());
        JScrollPane scrollPane = new JScrollPane(routeTable);
        ToolWindowSurfaceStyle.applyTableScrollPaneCard(scrollPane, routeTable);

        addRouteButton = ModernButtonFactory.createCompactButton(
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_ADD), true, "icons/plus.svg");
        addResponseButton = ModernButtonFactory.createCompactButton(
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_ADD_RESPONSE), false, "icons/plus.svg");
        editRouteButton = new EditButton();
        editRouteButton.setToolTipText(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_EDIT));
        deleteResponseButton = toolbarButton(
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_DELETE_RESPONSE), "icons/delete.svg");
        Dimension routeActionSize = new Dimension(ToolWindowActionToolbar.ACTION_SIZE,
                ToolWindowActionToolbar.ACTION_SIZE);
        editRouteButton.setPreferredSize(routeActionSize);
        deleteResponseButton.setPreferredSize(routeActionSize);
        exampleCountLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        exampleCountLabel.setForeground(ModernColors.getTextSecondary());
        conflictButton = toolbarButton("", "icons/warning.svg");
        conflictButton.setIcon(semanticIcon("icons/warning.svg", ModernColors::getWarning));
        conflictButton.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        conflictButton.setForeground(ModernColors.getWarning());
        conflictButton.setFocusable(true);
        conflictButton.setVisible(false);

        JPanel toolbar = new JPanel(new MigLayout(
                "insets 4 8 4 8,fillx,novisualpadding,hidemode 3",
                "[]4[][grow,fill]8[]8[]8[]4[]", "[]"));
        ToolWindowSurfaceStyle.applySectionHeader(toolbar);
        toolbar.add(addRouteButton);
        toolbar.add(addResponseButton);
        toolbar.add(new JLabel(), "growx");
        toolbar.add(exampleCountLabel, "wmin 0");
        toolbar.add(conflictButton);
        toolbar.add(editRouteButton);
        toolbar.add(deleteResponseButton);

        JPanel panel = createTabContentPanel();
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JComponent createScriptPanel() {
        JPanel panel = createTabContentPanel();

        MockCodeEditorSupport.installCompletion(scriptEditor);
        JPanel header = new JPanel(new MigLayout(
                "insets 7 10 7 10,fillx,novisualpadding", "[grow,fill][]8[]", "[]"));
        ToolWindowSurfaceStyle.applySectionHeader(header);
        ToolWindowSurfaceStyle.applyToolWindowToolbarSeparator(header, 0, 0, 1, 0);
        JLabel hint = new JLabel(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_SCRIPT_HINT));
        hint.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        hint.setForeground(ModernColors.getTextSecondary());
        header.add(hint, "growx,wmin 0");
        header.add(MockCodeEditorSupport.createExamplesButton(scriptEditor));
        header.add(MockCodeEditorSupport.createClearButton(scriptEditor));
        panel.add(header, BorderLayout.NORTH);

        scriptEditor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
        scriptEditor.setCodeFoldingEnabled(true);
        scriptEditor.setAutoIndentEnabled(true);
        scriptEditor.setBracketMatchingEnabled(true);
        scriptEditor.setMarkOccurrences(true);
        scriptEditor.setAntiAliasingEnabled(true);
        scriptEditor.setTabSize(4);
        EditorThemeUtil.loadTheme(scriptEditor);
        EditorThemeUtil.installViewportClippedTokenPainter(scriptEditor);
        panel.add(new SearchableTextArea(scriptEditor), BorderLayout.CENTER);

        JPanel footer = new JPanel(new MigLayout(
                "insets 7 10 7 10, fillx, novisualpadding", "[grow][]", "[]"));
        ToolWindowSurfaceStyle.applySectionHeader(footer);
        saveScriptButton = ModernButtonFactory.createCompactButton(
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_SCRIPT_SAVE), true, "icons/save.svg");
        JLabel quickReference = MockCodeEditorSupport.createQuickReferenceLabel();
        quickReference.setForeground(ModernColors.getTextSecondary());
        footer.add(quickReference, "growx,wmin 0");
        footer.add(saveScriptButton);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent createLogsPanel() {
        configureTable(logTable);
        JScrollPane tableScroll = new JScrollPane(logTable);
        ToolWindowSurfaceStyle.applyTableScrollPaneCard(tableScroll, logTable);

        logDetail.setEditable(false);
        logDetail.setLineWrap(false);
        logDetail.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        ToolWindowSurfaceStyle.applyTextComponentCard(logDetail);
        JScrollPane detailScroll = new JScrollPane(logDetail);
        ToolWindowSurfaceStyle.applyScrollPaneCard(detailScroll);

        JSplitPane split = AppToolWindowChrome.createVerticalInnerSplitPane(tableScroll, detailScroll, 260);

        JPanel panel = createTabContentPanel();
        JButton clear = commonCompact(CommonMessageKeys.BUTTON_CLEAR, false, "icons/clear.svg");
        clear.addActionListener(event -> clearLogs());
        JPanel toolbar = new JPanel(new MigLayout(
                "insets 6 8 6 8, fillx, novisualpadding", "[grow][]", "[]"));
        ToolWindowSurfaceStyle.applySectionHeader(toolbar);
        JLabel detailTitle = new JLabel(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_LOG_DETAIL));
        detailTitle.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, -1));
        toolbar.add(detailTitle, "growx");
        toolbar.add(clear);
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JComponent createStatePanel() {
        configureTable(stateTable);
        JScrollPane scrollPane = new JScrollPane(stateTable);
        ToolWindowSurfaceStyle.applyTableScrollPaneCard(scrollPane, stateTable);
        JButton clear = commonCompact(CommonMessageKeys.BUTTON_CLEAR, false, "icons/clear.svg");
        clear.addActionListener(event -> clearState());
        JPanel toolbar = new JPanel(new MigLayout(
                "insets 6 8 6 8, fillx, novisualpadding", "[grow][]", "[]"));
        ToolWindowSurfaceStyle.applySectionHeader(toolbar);
        toolbar.add(new JLabel(), "growx");
        toolbar.add(clear);
        JPanel panel = createTabContentPanel();
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Keeps opaque editor, table and split-pane surfaces away from the tab edge without
     * taking ownership of the detail card's rounded chrome.
     */
    private JPanel createTabContentPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));
        return panel;
    }

    @Override
    protected void registerListeners() {
        serverList.addListSelectionListener(event -> {
            if (!loadingDefinitions && !event.getValueIsAdjusting()) selectServer();
        });
        serverList.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { showServerPopup(event); }
            @Override public void mouseReleased(MouseEvent event) { showServerPopup(event); }
        });
        detailTabs.addChangeListener(event -> refreshRuntimeData());
        addButton.addActionListener(event -> createServer());
        emptyAddButton.addActionListener(event -> createServer());
        editButton.addActionListener(event -> editServer());
        deleteButton.addActionListener(event -> deleteServer());
        runToggleButton.addActionListener(event -> {
            MockServerDefinition server = selected();
            if (server == null) return;
            if (manager.isRunning(server.getId())) stopServer();
            else startServer();
        });
        refreshButton.addActionListener(event -> refreshExamples());
        copyButton.addActionListener(event -> copyUrl());
        deploymentButton.addActionListener(event -> copyDeploymentCommand());
        saveScriptButton.addActionListener(event -> saveScript());
        addRouteButton.addActionListener(event -> createMockRoute());
        addResponseButton.addActionListener(event -> addMockResponse());
        editRouteButton.addActionListener(event -> editMockRoute());
        deleteResponseButton.addActionListener(event -> deleteMockResponse());
        conflictButton.addActionListener(event -> selectNextConflict());
        routeTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) updateRouteButtonState();
        });
        routeTable.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { showRoutePopup(event); }
            @Override public void mouseReleased(MouseEvent event) { showRoutePopup(event); }
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && event.getButton() == MouseEvent.BUTTON1) editMockRoute();
            }
        });
        logTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) showSelectedLog();
        });
        listenersRegistered = true;
        selectServer();
    }

    private void showServerPopup(MouseEvent event) {
        if (!event.isPopupTrigger()) return;
        int row = serverList.locationToIndex(event.getPoint());
        Rectangle bounds = row < 0 ? null : serverList.getCellBounds(row, row);
        if (bounds == null || !bounds.contains(event.getPoint())) {
            JPopupMenu menu = new JPopupMenu();
            ToolWindowSurfaceStyle.applyPopupMenuCard(menu);
            menu.add(popupItem(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_CREATE),
                    "icons/plus.svg", this::createServer));
            menu.show(serverList, event.getX(), event.getY());
            return;
        }
        serverList.setSelectedIndex(row);
        MockServerDefinition server = selected();
        if (server == null) return;

        boolean running = manager.isRunning(server.getId());
        JPopupMenu menu = new JPopupMenu();
        ToolWindowSurfaceStyle.applyPopupMenuCard(menu);
        JMenuItem toggle = popupItem(CommonI18n.get(running
                ? CommonMessageKeys.BUTTON_STOP : CommonMessageKeys.BUTTON_START),
                running ? "icons/stop.svg" : "icons/start.svg",
                running ? this::stopServer : this::startServer);
        menu.add(toggle);
        JMenuItem edit = popupItem(CommonI18n.get(CommonMessageKeys.BUTTON_EDIT),
                "icons/edit.svg", this::editServer);
        edit.setEnabled(!running);
        menu.add(edit);
        menu.add(popupItem(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_COPY_URL),
                "icons/copy.svg", this::copyUrl));
        menu.add(popupItem(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_COPY_DEPLOY_COMMAND),
                "icons/code.svg", this::copyDeploymentCommand));
        menu.addSeparator();
        menu.add(popupItem(CommonI18n.get(CommonMessageKeys.BUTTON_DELETE),
                "icons/delete.svg", this::deleteServer));
        menu.show(serverList, event.getX(), event.getY());
    }

    private void showRoutePopup(MouseEvent event) {
        if (!event.isPopupTrigger()) return;
        int row = routeTable.rowAtPoint(event.getPoint());
        if (row < 0) {
            if (selected() == null) return;
            JPopupMenu menu = new JPopupMenu();
            ToolWindowSurfaceStyle.applyPopupMenuCard(menu);
            menu.add(popupItem(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_ADD),
                    "icons/plus.svg", this::createMockRoute));
            menu.show(routeTable, event.getX(), event.getY());
            return;
        }
        routeTable.setRowSelectionInterval(row, row);
        MockRouteEntry entry = selectedRouteEntry();
        if (entry == null) return;

        JPopupMenu menu = new JPopupMenu();
        ToolWindowSurfaceStyle.applyPopupMenuCard(menu);
        menu.add(popupItem(I18nUtil.getMessage(entry.standalone()
                        ? MessageKeys.MOCK_SERVER_ROUTE_EDIT_STANDALONE
                        : entry.configured() ? MessageKeys.MOCK_SERVER_ROUTE_EDIT
                        : MessageKeys.MOCK_SERVER_ROUTE_ADD_RESPONSE),
                entry.configured() ? "icons/edit.svg" : "icons/plus.svg", this::editMockRoute));
        if (!entry.standalone() && entry.configured()) {
            menu.add(popupItem(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_ADD_RESPONSE),
                    "icons/plus.svg", this::addMockResponse));
        }
        if (entry.configured()) {
            menu.addSeparator();
            menu.add(popupItem(I18nUtil.getMessage(entry.standalone()
                            ? MessageKeys.MOCK_SERVER_ROUTE_DELETE_STANDALONE
                            : MessageKeys.MOCK_SERVER_ROUTE_DELETE_RESPONSE),
                    "icons/delete.svg", this::deleteMockResponse));
        }
        menu.show(routeTable, event.getX(), event.getY());
    }

    private JMenuItem popupItem(String label, String iconPath, Runnable action) {
        JMenuItem item = new JMenuItem(label, IconUtil.createThemed(iconPath, 16, 16));
        item.addActionListener(event -> action.run());
        return item;
    }

    private static FlatSVGIcon semanticIcon(String path, Supplier<Color> color) {
        return IconUtil.create(path, 16, 16)
                .setColorFilter(new FlatSVGIcon.ColorFilter(ignored -> color.get()));
    }

    private void createServer() {
        createServer(null);
    }

    public void createServerForCollection(String collectionId) {
        createServer(collectionId);
    }

    public void addMockResponseForRequest(String collectionId, String requestId) {
        MockServerDefinition server = manager.listDefinitions().stream()
                .filter(item -> item.usesCollection(collectionId))
                .findFirst()
                .orElseGet(() -> createServer(collectionId));
        if (server == null) return;
        reloadDefinitions(server.getId());
        MockRouteEntry entry = routeProvider.listRouteEntries(collectionId).stream()
                .filter(item -> Objects.equals(item.sourceCollectionId(), collectionId)
                        && Objects.equals(item.requestId(), requestId))
                .findFirst()
                .orElse(null);
        if (entry == null) {
            NotificationCenter.showError(I18nUtil.getMessage(
                    MessageKeys.MOCK_SERVER_ROUTE_SAVE_FAILED,
                    I18nUtil.getMessage(MessageKeys.MOCK_SERVER_REQUEST_NOT_FOUND)));
            return;
        }
        try {
            if (routeEditorController.addResponse(this, server, entry)) routesChanged(server);
        } catch (Exception ex) {
            NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_SAVE_FAILED, ex.getMessage()));
        }
    }

    private MockServerDefinition createServer(String preferredCollectionId) {
        List<MockCollectionChoice> choices = routeProvider.listCollections();
        MockServerDefinition created = MockServerConfigDialog.showDialog(
                this, null, choices, suggestedPort(), preferredCollectionId);
        if (created == null) return null;
        try {
            manager.saveDefinition(created);
            reloadDefinitions(created.getId());
            return manager.findDefinition(created.getId()).orElse(created);
        } catch (Exception ex) {
            NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_SAVE_FAILED, ex.getMessage()));
            return null;
        }
    }

    private void editServer() {
        MockServerDefinition selected = selected();
        if (selected == null || manager.isRunning(selected.getId())) return;
        MockServerDefinition edited = MockServerConfigDialog.showDialog(
                this, selected, routeProvider.listCollections(), selected.getPort());
        saveDefinition(edited);
    }

    private void saveDefinition(MockServerDefinition definition) {
        if (definition == null) return;
        try {
            manager.saveDefinition(definition);
            reloadDefinitions(definition.getId());
        } catch (Exception ex) {
            NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_SAVE_FAILED, ex.getMessage()));
        }
    }

    private void deleteServer() {
        MockServerDefinition selected = selected();
        if (selected == null) return;
        int answer = JOptionPane.showConfirmDialog(
                this,
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_DELETE_CONFIRM, selected.getName()),
                CommonI18n.get(CommonMessageKeys.BUTTON_DELETE),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (answer == JOptionPane.OK_OPTION) {
            manager.removeDefinition(selected.getId());
            reloadDefinitions(null);
        }
    }

    private void startServer() {
        MockServerDefinition selected = selected();
        if (selected == null) return;
        try {
            saveScriptSilently(selected.getId());
            manager.start(selected.getId());
            refreshSelectedContent();
        } catch (Exception ex) {
            NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_START_FAILED, ex.getMessage()));
        }
    }

    private void stopServer() {
        MockServerDefinition selected = selected();
        if (selected == null) return;
        manager.stop(selected.getId());
        refreshSelectedContent();
    }

    private void refreshExamples() {
        MockServerDefinition selected = selected();
        if (selected == null) return;
        boolean restart = manager.isRunning(selected.getId());
        try {
            if (restart) manager.stop(selected.getId());
            if (restart) manager.start(selected.getId());
            refreshSelectedContent();
        } catch (Exception ex) {
            NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_START_FAILED, ex.getMessage()));
        }
    }

    private void createMockRoute() {
        MockServerDefinition server = selected();
        if (server == null) return;
        try {
            if (routeEditorController.createRoute(this, server)) routesChanged(server);
        } catch (Exception ex) {
            NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_SAVE_FAILED, ex.getMessage()));
        }
    }

    private void addMockResponse() {
        MockServerDefinition server = selected();
        MockRouteEntry entry = selectedRouteEntry();
        if (server == null || entry == null) return;
        try {
            if (routeEditorController.addResponse(this, server, entry)) routesChanged(server);
        } catch (Exception ex) {
            NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_SAVE_FAILED, ex.getMessage()));
        }
    }

    private void editMockRoute() {
        MockServerDefinition server = selected();
        MockRouteEntry entry = selectedRouteEntry();
        if (server == null || entry == null) return;
        try {
            boolean changed = entry.configured()
                    ? routeEditorController.editResponse(this, server, entry)
                    : routeEditorController.addResponse(this, server, entry);
            if (changed) routesChanged(server);
        } catch (Exception ex) {
            NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_SAVE_FAILED, ex.getMessage()));
        }
    }

    private void deleteMockResponse() {
        MockServerDefinition server = selected();
        MockRouteEntry entry = selectedRouteEntry();
        if (server == null || entry == null || !entry.configured()) return;
        int answer = JOptionPane.showConfirmDialog(
                this,
                I18nUtil.getMessage(entry.standalone()
                                ? MessageKeys.MOCK_SERVER_ROUTE_DELETE_STANDALONE_CONFIRM
                                : MessageKeys.MOCK_SERVER_ROUTE_DELETE_CONFIRM,
                        entry.exampleName()),
                I18nUtil.getMessage(entry.standalone()
                        ? MessageKeys.MOCK_SERVER_ROUTE_DELETE_STANDALONE
                        : MessageKeys.MOCK_SERVER_ROUTE_DELETE_RESPONSE),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (answer == JOptionPane.OK_OPTION && routeEditorController.deleteResponse(server, entry)) {
            routesChanged(server);
        }
    }

    private void routesChanged(MockServerDefinition server) {
        boolean restart = manager.isRunning(server.getId());
        try {
            if (restart) {
                saveScriptSilently(server.getId());
                manager.stop(server.getId());
                manager.start(server.getId());
            }
            refreshSelectedContent();
            NotificationCenter.showSuccess(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_SAVED));
        } catch (Exception ex) {
            refreshSelectedContent();
            NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_START_FAILED, ex.getMessage()));
        }
    }

    private void copyUrl() {
        MockServerDefinition selected = selected();
        if (selected == null) return;
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(manager.baseUrl(selected.getId())), null);
        NotificationCenter.showSuccess(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_URL_COPIED));
    }

    private void copyDeploymentCommand() {
        MockServerDefinition selected = selected();
        if (selected == null) return;
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(manager.deploymentCommand(selected.getId())), null);
        NotificationCenter.showSuccess(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_DEPLOY_COMMAND_COPIED));
    }

    private void saveScript() {
        MockServerDefinition selected = selected();
        if (selected == null) return;
        saveScriptSilently(selected.getId());
        NotificationCenter.showSuccess(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_SCRIPT_SAVED));
    }

    private void saveScriptSilently(String id) {
        if (!loadingScript) manager.updateScript(id, scriptEditor.getText());
    }

    private void clearLogs() {
        MockServerDefinition selected = selected();
        if (selected != null) manager.clearLogs(selected.getId());
        logDetail.setText("");
        refreshRuntimeData();
    }

    private void clearState() {
        MockServerDefinition selected = selected();
        if (selected != null) manager.clearState(selected.getId());
        refreshRuntimeData();
    }

    private void reloadDefinitions(String preferredId) {
        String selectedId = preferredId != null ? preferredId : selectedId();
        List<MockServerDefinition> definitions = manager.listDefinitions();
        displayedDefinitionsRevision = manager.definitionsRevision();
        loadingDefinitions = true;
        try {
            serverListModel.clear();
            definitions.forEach(serverListModel::addElement);
            int selectedIndex = -1;
            if (selectedId != null) {
                for (int i = 0; i < serverListModel.size(); i++) {
                    if (Objects.equals(serverListModel.get(i).getId(), selectedId)) {
                        selectedIndex = i;
                        break;
                    }
                }
            }
            if (selectedIndex < 0 && !serverListModel.isEmpty()) selectedIndex = 0;
            serverList.setSelectedIndex(selectedIndex);
        } finally {
            loadingDefinitions = false;
        }
        if (listenersRegistered) selectServer();
    }

    private void selectServer() {
        MockServerDefinition selected = selected();
        loadingScript = true;
        try {
            scriptEditor.setText(selected == null ? "" : selected.getScript());
            scriptEditor.setCaretPosition(0);
            scriptEditor.discardAllEdits();
        } finally {
            loadingScript = false;
        }
        refreshSelectedContent();
    }

    private void refreshSelectedContent() {
        MockServerDefinition selected = selected();
        if (selected == null) {
            detailCardLayout.show(detailCards, EMPTY_CARD);
            routeConflicts = new MockRouteConflicts(0, Set.of());
            routeTableModel.setRows(List.of(), routeConflicts.rowIndexes());
            logTableModel.setRows(List.of());
            stateTableModel.setRows(Map.of());
            baseUrlLabel.setText("");
            exampleCountLabel.setText("");
            conflictButton.setVisible(false);
            updateButtonState(false, false);
            return;
        }
        detailCardLayout.show(detailCards, DETAIL_CARD);
        detailTitleLabel.setText(selected.getName());
        detailTitleLabel.setToolTipText(selected.getName());
        List<MockRouteEntry> routes = manager.routeEntries(selected.getId());
        routeConflicts = MockRouteConflicts.from(routes);
        routeTableModel.setRows(routes, routeConflicts.rowIndexes());
        long configured = routes.stream().filter(MockRouteEntry::configured).count();
        String routeCount = I18nUtil.getMessage(
                MessageKeys.MOCK_SERVER_ROUTE_COUNT, routes.size(), configured);
        exampleCountLabel.setText(routeCount);
        exampleCountLabel.setToolTipText(routeCount);
        String conflictCount = I18nUtil.getMessage(
                MessageKeys.MOCK_SERVER_ROUTE_CONFLICTS, routeConflicts.groupCount());
        conflictButton.setText(String.valueOf(routeConflicts.groupCount()));
        conflictButton.setToolTipText(conflictCount + " — "
                + I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_CONFLICTS_HINT)
                + " " + I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_CONFLICTS_ACTION_HINT));
        conflictButton.getAccessibleContext().setAccessibleName(conflictCount);
        conflictButton.setVisible(routeConflicts.groupCount() > 0);
        refreshRuntimeData();
    }

    private void refreshRuntimeData() {
        if (manager.definitionsRevision() != displayedDefinitionsRevision) {
            reloadDefinitions(null);
            return;
        }
        MockServerDefinition selected = selected();
        if (selected == null) return;
        boolean running = manager.isRunning(selected.getId());
        String baseUrl = manager.baseUrl(selected.getId());
        baseUrlLabel.setText(baseUrl);
        baseUrlLabel.setToolTipText(baseUrl);
        if (detailTabs.getSelectedIndex() == LOGS_TAB_INDEX) refreshLogs(selected.getId());
        if (detailTabs.getSelectedIndex() == STATE_TAB_INDEX) {
            stateTableModel.setRows(manager.state(selected.getId()));
        }
        updateButtonState(true, running);
        serverList.repaint();
    }

    private void refreshLogs(String serverId) {
        MockCallLog selectedLog = selectedLog();
        if (!logTableModel.setRows(manager.logs(serverId))) return;

        if (selectedLog == null) return;
        int modelRow = logTableModel.indexOf(selectedLog);
        if (modelRow < 0) {
            logDetail.setText("");
            return;
        }
        int viewRow = logTable.convertRowIndexToView(modelRow);
        if (viewRow >= 0) {
            logTable.getSelectionModel().setSelectionInterval(viewRow, viewRow);
        }
    }

    private void updateButtonState(boolean selected, boolean running) {
        editButton.setEnabled(selected && !running);
        deleteButton.setEnabled(selected);
        runToggleButton.setEnabled(selected);
        runToggleButton.setIcon(running ? stopActionIcon : startActionIcon);
        String runAction = CommonI18n.get(running
                ? CommonMessageKeys.BUTTON_STOP : CommonMessageKeys.BUTTON_START);
        runToggleButton.setToolTipText(runAction);
        runToggleButton.getAccessibleContext().setAccessibleName(runAction);
        refreshButton.setEnabled(selected);
        copyButton.setEnabled(selected);
        deploymentButton.setEnabled(selected);
        saveScriptButton.setEnabled(selected);
        scriptEditor.setEnabled(selected);
        if (addRouteButton != null) addRouteButton.setEnabled(selected);
        updateRouteButtonState();
    }

    private void updateRouteButtonState() {
        MockRouteEntry entry = selectedRouteEntry();
        boolean hasEntry = selected() != null && entry != null;
        if (addResponseButton != null) {
            addResponseButton.setVisible(hasEntry && !entry.standalone());
            addResponseButton.setEnabled(hasEntry && !entry.standalone());
        }
        if (editRouteButton != null) {
            editRouteButton.setVisible(hasEntry && entry.configured());
            editRouteButton.setEnabled(hasEntry && entry.configured());
        }
        if (deleteResponseButton != null) {
            deleteResponseButton.setVisible(hasEntry && entry.configured());
            deleteResponseButton.setEnabled(hasEntry && entry.configured());
        }
        if (editRouteButton != null) editRouteButton.setToolTipText(I18nUtil.getMessage(
                hasEntry && entry.standalone()
                        ? MessageKeys.MOCK_SERVER_ROUTE_EDIT_STANDALONE
                        : MessageKeys.MOCK_SERVER_ROUTE_EDIT));
        if (deleteResponseButton != null) deleteResponseButton.setToolTipText(I18nUtil.getMessage(
                hasEntry && entry.standalone()
                        ? MessageKeys.MOCK_SERVER_ROUTE_DELETE_STANDALONE
                        : MessageKeys.MOCK_SERVER_ROUTE_DELETE_RESPONSE));
    }

    private MockRouteEntry selectedRouteEntry() {
        int viewRow = routeTable.getSelectedRow();
        return viewRow < 0 ? null : routeTableModel.row(routeTable.convertRowIndexToModel(viewRow));
    }

    private void showSelectedLog() {
        MockCallLog entry = selectedLog();
        if (entry == null) {
            logDetail.setText("");
            return;
        }
        StringBuilder detail = new StringBuilder();
        detail.append(entry.method()).append(' ').append(entry.path()).append('\n');
        detail.append(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_STATUS))
                .append(": ").append(entry.statusCode()).append("  ")
                .append(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_LOG_DURATION))
                .append(": ").append(entry.durationMs()).append(" ms\n");
        if (!entry.requestName().isBlank()) {
            detail.append(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_REQUEST))
                    .append(": ").append(entry.requestName()).append('\n');
        }
        if (!entry.exampleName().isBlank()) {
            detail.append(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_EXAMPLE))
                    .append(": ").append(entry.exampleName()).append('\n');
        }
        if (entry.error() != null && !entry.error().isBlank()) {
            detail.append('\n').append(I18nUtil.getMessage(MessageKeys.ERROR).toUpperCase())
                    .append('\n').append(entry.error()).append('\n');
        }
        detail.append('\n').append(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_LOG_REQUEST_BODY).toUpperCase())
                .append('\n').append(entry.requestBody());
        detail.append("\n\n").append(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_LOG_RESPONSE_BODY).toUpperCase())
                .append('\n').append(entry.responseBody());
        logDetail.setText(detail.toString());
        logDetail.setCaretPosition(0);
    }

    private MockCallLog selectedLog() {
        int viewRow = logTable.getSelectedRow();
        return viewRow < 0 ? null : logTableModel.row(logTable.convertRowIndexToModel(viewRow));
    }

    private int suggestedPort() {
        List<Integer> used = manager.listDefinitions().stream().map(MockServerDefinition::getPort).toList();
        int port = 3001;
        while (used.contains(port) && port < 65_535) port++;
        return port;
    }

    private void selectNextConflict() {
        List<Integer> views = routeConflicts.rowIndexes().stream()
                .map(routeTable::convertRowIndexToView)
                .filter(index -> index >= 0)
                .sorted()
                .toList();
        if (views.isEmpty()) return;
        int selectedRow = routeTable.getSelectedRow();
        int next = views.stream().filter(index -> index > selectedRow).findFirst().orElse(views.get(0));
        routeTable.getSelectionModel().setSelectionInterval(next, next);
        routeTable.scrollRectToVisible(routeTable.getCellRect(next, 0, true));
        routeTable.requestFocusInWindow();
    }

    private MockServerDefinition selected() {
        return serverList.getSelectedValue();
    }

    private String selectedId() {
        MockServerDefinition selected = selected();
        return selected == null ? null : selected.getId();
    }

    private JButton commonCompact(String key, boolean primary, String icon) {
        return ModernButtonFactory.createCompactButton(CommonI18n.get(key), primary, icon);
    }

    private void configureTable(JTable table) {
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(Math.max(24, table.getRowHeight()));
        ToolWindowSurfaceStyle.applyTableCard(table);
    }

    private final class ServerRenderer extends JPanel implements ListCellRenderer<MockServerDefinition> {
        private final JLabel serverIcon = new JLabel(IconUtil.createThemed("icons/mock-server.svg", 18, 18));
        private final JLabel titleLabel = new JLabel();
        private final JLabel stateLabel = new JLabel();
        private final JLabel addressLabel = new JLabel();

        private ServerRenderer() {
            setLayout(new MigLayout(
                    "insets 5 8 5 8,fillx,novisualpadding",
                    "[18]8[grow,fill][]",
                    "[]1[]"
            ));
            setOpaque(true);
            titleLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, -1));
            addressLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -2));
            add(serverIcon, "cell 0 0,span 1 2,aligny center");
            add(titleLabel, "cell 1 0,growx,wmin 0");
            add(addressLabel, "cell 1 1,growx,wmin 0");
            add(stateLabel, "cell 2 0,span 1 2,aligny center");
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends MockServerDefinition> list,
                                                      MockServerDefinition definition,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            boolean running = definition != null && manager.isRunning(definition.getId());
            titleLabel.setText(definition == null ? "" : definition.getName());
            String status = I18nUtil.getMessage(running
                    ? MessageKeys.MOCK_SERVER_STATUS_RUNNING
                    : MessageKeys.MOCK_SERVER_STATUS_STOPPED);
            stateLabel.setText(null);
            stateLabel.setIcon(running ? runningStatusIcon : stoppedStatusIcon);
            stateLabel.setToolTipText(status);
            stateLabel.getAccessibleContext().setAccessibleName(status);
            String accessUrl = definition == null ? ""
                    : MockNetworkAddressResolver.accessUrl(definition, definition.getPort());
            addressLabel.setText(accessUrl);
            setToolTipText(definition == null ? null : definition.getName() + " — " + status + " — " + accessUrl);

            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            titleLabel.setForeground(isSelected ? list.getSelectionForeground() : ModernColors.getTextPrimary());
            addressLabel.setForeground(isSelected ? list.getSelectionForeground() : ModernColors.getTextSecondary());
            return this;
        }
    }

    private final class RouteMethodRenderer extends DefaultTableCellRenderer {
        private final Icon conflictIcon = semanticIcon("icons/warning.svg", ModernColors::getWarning);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focus, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            boolean conflicting = routeTableModel.isConflict(table.convertRowIndexToModel(row));
            setIcon(conflicting ? conflictIcon : null);
            setIconTextGap(4);
            setToolTipText(conflicting
                    ? I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_CONFLICTS_HINT)
                    : null);
            return this;
        }
    }

    private final class RouteResponseRenderer extends DefaultTableCellRenderer {
        private final Icon configuredIcon = semanticIcon("icons/check.svg", ModernColors::getSuccess);
        private final Icon missingIcon = semanticIcon("icons/warning.svg", ModernColors::getWarning);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focus, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            MockRouteEntry entry = routeTableModel.row(table.convertRowIndexToModel(row));
            boolean configured = entry != null && entry.configured();
            setIcon(configured ? configuredIcon : missingIcon);
            setText(configured ? String.valueOf(value) : "");
            setIconTextGap(5);
            String description = configured ? String.valueOf(value)
                    : I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_UNCONFIGURED_HINT);
            setToolTipText(description);
            getAccessibleContext().setAccessibleName(description);
            return this;
        }
    }

    private final class RouteModeRenderer extends DefaultTableCellRenderer {
        private final Icon codeIcon = IconUtil.createThemed("icons/code.svg", 16, 16);
        private final Icon staticIcon = IconUtil.createThemed("icons/file.svg", 16, 16);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focus, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            MockRouteEntry entry = routeTableModel.row(table.convertRowIndexToModel(row));
            setIcon(entry == null || !entry.configured() ? null : entry.codeMock() ? codeIcon : staticIcon);
            setText("");
            setHorizontalAlignment(SwingConstants.CENTER);
            String description = entry == null || !entry.configured()
                    ? I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_UNCONFIGURED)
                    : String.valueOf(value);
            setToolTipText(description);
            getAccessibleContext().setAccessibleName(description);
            return this;
        }
    }

    private static final class RouteStatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focus, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            setHorizontalAlignment(SwingConstants.CENTER);
            if (!selected && value instanceof Integer status) {
                setForeground(status >= 500 ? ModernColors.getError()
                        : status >= 400 ? ModernColors.getWarning()
                        : status >= 200 && status < 400 ? ModernColors.getSuccess()
                        : ModernColors.getTextPrimary());
            }
            return this;
        }
    }

    private static final class RouteTableModel extends AbstractTableModel {
        private List<MockRouteEntry> rows = List.of();
        private Set<Integer> conflictRows = Set.of();
        private final String[] columns = {
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_METHOD),
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_PATH),
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_REQUEST),
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_SOURCE),
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_RESPONSE),
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_STATUS),
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_MODE)
        };

        void setRows(List<MockRouteEntry> rows, Set<Integer> conflictRows) {
            this.rows = rows == null ? List.of() : List.copyOf(rows);
            this.conflictRows = conflictRows == null ? Set.of() : Set.copyOf(conflictRows);
            fireTableDataChanged();
        }

        MockRouteEntry row(int index) { return index < 0 || index >= rows.size() ? null : rows.get(index); }
        boolean isConflict(int index) { return conflictRows.contains(index); }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            MockRouteEntry row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.method();
                case 1 -> row.path();
                case 2 -> row.requestName();
                case 3 -> row.sourceName();
                case 4 -> row.configured()
                        ? row.exampleName()
                        : I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_UNCONFIGURED);
                case 5 -> row.configured() ? row.statusCode() : "";
                case 6 -> row.configured()
                        ? I18nUtil.getMessage(row.codeMock()
                                ? MessageKeys.MOCK_SERVER_ROUTE_MODE_CODE
                                : MessageKeys.MOCK_SERVER_ROUTE_MODE_STATIC)
                        : "";
                default -> "";
            };
        }
    }

    static final class LogTableModel extends AbstractTableModel {
        private List<MockCallLog> rows = List.of();
        private final String[] columns = {
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_LOG_TIME),
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_METHOD),
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_PATH),
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_STATUS),
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_LOG_DURATION),
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_EXAMPLE)
        };

        boolean setRows(List<MockCallLog> rows) {
            List<MockCallLog> nextRows = rows == null ? List.of() : List.copyOf(rows);
            if (this.rows.equals(nextRows)) return false;
            this.rows = nextRows;
            fireTableDataChanged();
            return true;
        }

        MockCallLog row(int index) { return index < 0 || index >= rows.size() ? null : rows.get(index); }
        int indexOf(MockCallLog row) { return rows.indexOf(row); }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            MockCallLog row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> LOG_TIME_FORMAT.format(row.timestamp());
                case 1 -> row.method();
                case 2 -> row.path();
                case 3 -> row.statusCode();
                case 4 -> row.durationMs() + " ms";
                case 5 -> row.exampleName();
                default -> "";
            };
        }
    }

    private static final class StateTableModel extends AbstractTableModel {
        private final List<StateRow> rows = new ArrayList<>();
        private final String[] columns = {
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_STATE_SESSION),
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_STATE_KEY),
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_STATE_VALUE)
        };

        void setRows(Map<String, Map<String, Object>> sessions) {
            List<StateRow> nextRows = new ArrayList<>();
            if (sessions != null) {
                sessions.forEach((session, values) -> values.forEach(
                        (key, value) -> nextRows.add(new StateRow(session, key, String.valueOf(value)))));
            }
            if (rows.equals(nextRows)) return;
            rows.clear();
            rows.addAll(nextRows);
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            StateRow row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.session();
                case 1 -> row.key();
                case 2 -> row.value();
                default -> "";
            };
        }
    }

    private record StateRow(String session, String key, String value) {
    }
}
