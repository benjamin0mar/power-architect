package ca.sqlpower.architect.swingui;

import ca.sqlpower.architect.*;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.Rectangle;
import java.awt.Container;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;

public class ArchitectFrame extends JFrame {

	private static Logger logger = Logger.getLogger(ArchitectFrame.class);

	/**
	 * The ArchitectFrame is a singleton; this is the main instance.
	 */
	protected static ArchitectFrame mainInstance;

	public static final double ZOOM_STEP = 0.25;

	protected ArchitectSession architectSession = null;
	protected SwingUIProject project = null;
	protected ConfigFile configFile = null;
	protected SwingUserSettings sprefs = null;
	protected JToolBar projectBar = null;
	protected JToolBar ppBar = null;
	protected JMenuBar menuBar = null;
	protected JSplitPane splitPane = null;
	protected PlayPen playpen = null;
	protected DBTree dbTree = null;
	
	protected AboutAction aboutAction;
	protected Action newProjectAction;
	protected Action openProjectAction;
	protected Action saveProjectAction;
	protected Action saveProjectAsAction;
	protected PreferencesAction prefAction;
	protected ProjectSettingsAction projectSettingsAction;
	protected PrintAction printAction;
 	protected ZoomAction zoomInAction;
 	protected ZoomAction zoomOutAction;
 	protected Action zoomNormalAction;
	
	// playpen edit actions
	protected EditColumnAction editColumnAction;
	protected InsertColumnAction insertColumnAction;
	protected EditTableAction editTableAction;
	protected DeleteSelectedAction deleteSelectedAction;
	protected CreateTableAction createTableAction;
	protected CreateRelationshipAction createIdentifyingRelationshipAction;
	protected CreateRelationshipAction createNonIdentifyingRelationshipAction;
	protected EditRelationshipAction editRelationshipAction;

	protected Action exportDDLAction;
	protected Action compareDMAction;
	protected ExportPLTransAction exportPLTransAction;
	protected ArchitectFrameWindowListener afWindowListener;
	protected Action exitAction = new AbstractAction("Exit") {
			public void actionPerformed(ActionEvent e) {
				exit();
			}
		};

	/**
	 * Updates the swing settings and then writes all settings to the
	 * config file whenever actionPerformed is invoked.
	 */
	protected Action saveSettingsAction = new AbstractAction("Save Settings") {
			public void actionPerformed(ActionEvent e) {
				try {
					saveSettings();
				} catch (ArchitectException ex) {
					logger.error("Couldn't save settings", ex);
				}
			}
		};

	public ArchitectFrame() throws ArchitectException {
		mainInstance = this;
		architectSession = ArchitectSession.getInstance();
		init();
	}

	protected void init() throws ArchitectException {

		try {
			ConfigFile cf = ConfigFile.getDefaultInstance();
			architectSession.setUserSettings(cf.read());
			sprefs = architectSession.getUserSettings().getSwingSettings();
		} catch (IOException e) {
			throw new ArchitectException("prefs.read", e);
		}

		// Create actions
		aboutAction = new AboutAction();

		newProjectAction
			 = new AbstractAction("New Project",
					      ASUtils.createJLFIcon("general/New","New Project",sprefs.getInt(SwingUserSettings.ICON_SIZE, 24))) {
			public void actionPerformed(ActionEvent e) {
				try {
					setProject(new SwingUIProject("New Project"));
					logger.debug("Glass pane is "+getGlassPane());
					getGlassPane().setVisible(true);
				} catch (Exception ex) {
					JOptionPane.showMessageDialog(ArchitectFrame.this,
												  "Can't create new project: "+ex.getMessage());
					logger.error("Got exception while creating new project", ex);
				}
			}
		};
		newProjectAction.putValue(AbstractAction.SHORT_DESCRIPTION, "New");

		openProjectAction
			= new AbstractAction("Open Project...",
								 ASUtils.createJLFIcon("general/Open",
													   "Open Project",
													   sprefs.getInt(SwingUserSettings.ICON_SIZE, 24))) {
					public void actionPerformed(ActionEvent e) {
						JFileChooser chooser = new JFileChooser();
						chooser.addChoosableFileFilter(ASUtils.ARCHITECT_FILE_FILTER);
						int returnVal = chooser.showOpenDialog(ArchitectFrame.this);
						if (returnVal == JFileChooser.APPROVE_OPTION) {
							final File file = chooser.getSelectedFile();							
                            new Thread() {
								public void run() {
									try {
										SwingUIProject project = new SwingUIProject("Loading...");
										project.setFile(file);
										InputStream in = new BufferedInputStream
											(new ProgressMonitorInputStream
											 (ArchitectFrame.this,
											  "Reading " + file.getName(),
											  new FileInputStream(file)));
										project.load(in);
										setProject(project);
									} catch (Exception ex) {
										JOptionPane.showMessageDialog
											(ArchitectFrame.this,
											 "Can't open project: "+ex.getMessage());
										logger.error("Got exception while opening project", ex);
									}
								}
							}.start();
						}
					}
				};
		openProjectAction.putValue(AbstractAction.SHORT_DESCRIPTION, "Open");
		
		saveProjectAction 
			= new AbstractAction("Save Project",
								 ASUtils.createJLFIcon("general/Save",
													   "Save Project",
													   sprefs.getInt(SwingUserSettings.ICON_SIZE, 24))) {
					public void actionPerformed(ActionEvent e) {
						saveOrSaveAs(false);
					}
				};
		saveProjectAction.putValue(AbstractAction.SHORT_DESCRIPTION, "Save");
		
		saveProjectAsAction
			= new AbstractAction("Save Project As...",
								 ASUtils.createJLFIcon("general/SaveAs",
													   "Save Project As...",
													   sprefs.getInt(SwingUserSettings.ICON_SIZE, 24))) {
					public void actionPerformed(ActionEvent e) {
						saveOrSaveAs(true);
					}
				};
		saveProjectAsAction.putValue(AbstractAction.SHORT_DESCRIPTION, "Save As");

		prefAction = new PreferencesAction();
		projectSettingsAction = new ProjectSettingsAction();
		printAction = new PrintAction();
		zoomInAction = new ZoomAction(ZOOM_STEP);
		zoomOutAction = new ZoomAction(ZOOM_STEP * -1.0);

		zoomNormalAction
			= new AbstractAction("Reset Zoom",
								 ASUtils.createJLFIcon("general/Zoom",
													   "Reset Zoom",
													   sprefs.getInt(SwingUserSettings.ICON_SIZE, 24))) {
					public void actionPerformed(ActionEvent e) {
						playpen.setZoom(1.0);
					}
				};
		zoomNormalAction.putValue(AbstractAction.SHORT_DESCRIPTION, "Reset Zoom");

		exportDDLAction = new ExportDDLAction();
		compareDMAction = new CompareDMAction();

		exportPLTransAction = new ExportPLTransAction();
		deleteSelectedAction = new DeleteSelectedAction();
		createIdentifyingRelationshipAction = new CreateRelationshipAction(true);
		createNonIdentifyingRelationshipAction = new CreateRelationshipAction(false);
		editRelationshipAction = new EditRelationshipAction();
		createTableAction = new CreateTableAction();
		editColumnAction = new EditColumnAction();
		insertColumnAction = new InsertColumnAction();
		editTableAction = new EditTableAction();

		menuBar = new JMenuBar();
		JMenu fileMenu = new JMenu("File");
		fileMenu.setMnemonic('f');
		fileMenu.add(new JMenuItem(newProjectAction));
		fileMenu.add(new JMenuItem(openProjectAction));
		fileMenu.add(new JMenuItem(saveProjectAction));
		fileMenu.add(new JMenuItem(saveProjectAsAction));
		fileMenu.add(new JMenuItem(printAction));
		fileMenu.add(new JMenuItem(exportDDLAction));
		fileMenu.add(new JMenuItem(compareDMAction));
		fileMenu.add(new JMenuItem(prefAction));
		fileMenu.add(new JMenuItem(projectSettingsAction));
		fileMenu.add(new JMenuItem(saveSettingsAction));
		fileMenu.add(new JMenuItem(exitAction));
		menuBar.add(fileMenu);

		JMenu etlMenu = new JMenu("ETL");
		etlMenu.setMnemonic('e');
		JMenu etlSubmenuOne = new JMenu("Power*Loader");
		JMenu etlSubmenuTwo = new JMenu("Informatica");
		etlSubmenuOne.add(new JMenuItem(exportPLTransAction));
		etlSubmenuOne.add(new JMenuItem("PL Transaction File Export"));
		etlSubmenuOne.add(new JMenuItem("Run Power*Loader"));
		etlMenu.add(etlSubmenuOne);
		etlMenu.add(etlSubmenuTwo);
		menuBar.add(etlMenu);

		JMenu helpMenu = new JMenu("Help");
		helpMenu.setMnemonic('h');
		helpMenu.add(new JMenuItem(aboutAction));
		menuBar.add(helpMenu);
		
		setJMenuBar(menuBar);

		projectBar = new JToolBar(JToolBar.HORIZONTAL);
		ppBar = new JToolBar(JToolBar.VERTICAL);

		projectBar.add(newProjectAction);
		projectBar.add(openProjectAction);
		projectBar.add(saveProjectAction);
		projectBar.addSeparator();
		projectBar.add(printAction);
		projectBar.addSeparator();
		projectBar.add(exportDDLAction);
		projectBar.add(compareDMAction);

		JButton tempButton = null; // shared actions need to report where they are coming from
 		ppBar.add(zoomInAction);
 		ppBar.add(zoomOutAction);
 		ppBar.add(zoomNormalAction);
		ppBar.addSeparator();
		tempButton = ppBar.add(deleteSelectedAction);
		tempButton.setActionCommand(ArchitectSwingConstants.ACTION_COMMAND_SRC_PLAYPEN);
		ppBar.addSeparator();
		tempButton = ppBar.add(createTableAction);
		tempButton.setActionCommand(ArchitectSwingConstants.ACTION_COMMAND_SRC_PLAYPEN);
		ppBar.addSeparator();
		tempButton = ppBar.add(insertColumnAction);
		tempButton.setActionCommand(ArchitectSwingConstants.ACTION_COMMAND_SRC_PLAYPEN);
		tempButton = ppBar.add(editColumnAction);
		tempButton.setActionCommand(ArchitectSwingConstants.ACTION_COMMAND_SRC_PLAYPEN);
		ppBar.addSeparator();
		ppBar.add(createNonIdentifyingRelationshipAction);
		ppBar.add(createIdentifyingRelationshipAction);
		tempButton = ppBar.add(editRelationshipAction);
		tempButton.setActionCommand(ArchitectSwingConstants.ACTION_COMMAND_SRC_PLAYPEN);
		

		Container projectBarPane = getContentPane();
		projectBarPane.setLayout(new BorderLayout());
		projectBarPane.add(projectBar, BorderLayout.NORTH);

		JPanel cp = new JPanel(new BorderLayout());
		cp.add(ppBar, BorderLayout.EAST);
		projectBarPane.add(cp, BorderLayout.CENTER);

		splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		cp.add(splitPane, BorderLayout.CENTER);
		logger.debug("Added splitpane to content pane");
		splitPane.setDividerLocation
			(sprefs.getInt(SwingUserSettings.DIVIDER_LOCATION,
						   150)); //dbTree.getPreferredSize().width));

		Rectangle bounds = new Rectangle();
		bounds.x = sprefs.getInt(SwingUserSettings.MAIN_FRAME_X, 40);
		bounds.y = sprefs.getInt(SwingUserSettings.MAIN_FRAME_Y, 40);
		bounds.width = sprefs.getInt(SwingUserSettings.MAIN_FRAME_WIDTH, 600);
		bounds.height = sprefs.getInt(SwingUserSettings.MAIN_FRAME_HEIGHT, 440);
		setBounds(bounds);
		addWindowListener(afWindowListener = new ArchitectFrameWindowListener());
		setProject(new SwingUIProject("New Project"));
	}
	
	public void setProject(SwingUIProject p) throws ArchitectException {
		this.project = p;
		logger.debug("Setting project to "+project);
		setTitle(project.getName()+" - Power*Architect");
		playpen = project.getPlayPen();
		dbTree = project.getSourceDatabases();
		//
		setupActions();
		//
		splitPane.setLeftComponent(new JScrollPane(dbTree));
		splitPane.setRightComponent(new JScrollPane(playpen));		
	}
	
	public SwingUIProject getProject(){
		return this.project;
	}
	
	/**
	 * Points all the actions to the correct PlayPen and DBTree
	 * instances.  This method is called by setProject.
	 */
	protected void setupActions() {
		// playpen actions
		aboutAction.setPlayPen(playpen);
		printAction.setPlayPen(playpen);
		deleteSelectedAction.setPlayPen(playpen);
		editColumnAction.setPlayPen(playpen);
		insertColumnAction.setPlayPen(playpen);
		editTableAction.setPlayPen(playpen);
		createTableAction.setPlayPen(playpen);
		createIdentifyingRelationshipAction.setPlayPen(playpen);
		createNonIdentifyingRelationshipAction.setPlayPen(playpen);
		editRelationshipAction.setPlayPen(playpen);
		exportPLTransAction.setPlayPen(playpen);
		zoomInAction.setPlayPen(playpen);
		zoomOutAction.setPlayPen(playpen);
		// dbtree actions
		editColumnAction.setDBTree(dbTree);
		insertColumnAction.setDBTree(dbTree);
		editRelationshipAction.setDBTree(dbTree);
		deleteSelectedAction.setDBTree(dbTree);
		editTableAction.setDBTree(dbTree);
		//
		prefAction.setArchitectFrame(this);
		projectSettingsAction.setArchitectFrame(this);
	}

	public static ArchitectFrame getMainInstance() {
		return mainInstance;
	}
	
	/**
	 * Convenience method for getArchitectSession().getUserSettings().
	 */
	public UserSettings getUserSettings() {
		return architectSession.getUserSettings();
	}

	public ArchitectSession getArchitectSession() {
		return architectSession;
	}

	class ArchitectFrameWindowListener extends WindowAdapter {
		public void windowClosing(WindowEvent e) {
			exit();
		}
	}

	public void saveSettings() throws ArchitectException {
		if (configFile == null) configFile = ConfigFile.getDefaultInstance();

		sprefs.setInt(SwingUserSettings.DIVIDER_LOCATION, splitPane.getDividerLocation());
		sprefs.setInt(SwingUserSettings.MAIN_FRAME_X, getLocation().x);
		sprefs.setInt(SwingUserSettings.MAIN_FRAME_Y, getLocation().y);
		sprefs.setInt(SwingUserSettings.MAIN_FRAME_WIDTH, getWidth());
		sprefs.setInt(SwingUserSettings.MAIN_FRAME_HEIGHT, getHeight());
		
		configFile.write(getArchitectSession());
	}

	/**
	 * Calling this method quits the application and terminates the
	 * JVM.
	 */
	public void exit() {
		try {
			saveSettings();
		} catch (ArchitectException e) {
			logger.error("Couldn't save settings: "+e);
		}
		System.exit(0);
	}

	/**
	 * Creates an ArchitectFrame and sets is visible.  This method is
	 * an acceptable way to launch the Architect application.
	 */
	public static void main(String args[]) throws ArchitectException {
		ArchitectUtils.configureLog4j();

		new ArchitectFrame();
		
		SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					// this doesn't appear to have any effect on the motion threshold 
                    // in the Playpen, but it does seem to work on the DBTree...
					logger.debug("current motion threshold is: " + System.getProperty("awt.dnd.drag.threshold"));
					System.setProperty("awt.dnd.drag.threshold","50");
					logger.debug("new motion threshold is: " + System.getProperty("awt.dnd.drag.threshold"));
					mainInstance.setVisible(true);
				}
			});
	}

	public void saveOrSaveAs(boolean showChooser) {
		if (project.getFile() == null || showChooser) {
			JFileChooser chooser = new JFileChooser(project.getFile());
			chooser.addChoosableFileFilter(ASUtils.ARCHITECT_FILE_FILTER);
			int response = chooser.showSaveDialog(ArchitectFrame.this);
			if (response != JFileChooser.APPROVE_OPTION) {
				return;
			} else {
				File file = chooser.getSelectedFile();
				if (!file.getPath().endsWith(".architect")) {
					file = new File(file.getPath()+".architect");
				}
				if (file.exists()) {
				    response = JOptionPane.showConfirmDialog(
				            ArchitectFrame.this,
				            "The file\n\n"+file.getPath()+"\n\nalready exists. Do you want to overwrite it?",
				            "File Exists", JOptionPane.YES_NO_OPTION);
				    if (response == JOptionPane.NO_OPTION) {
				        saveOrSaveAs(true);
				        return;
				    }
				}
				project.setFile(file);
				String projName = file.getName().substring(0, file.getName().length()-".architect".length());
				project.setName(projName);
				setTitle(projName);
			}
		}
		final ProgressMonitor pm = new ProgressMonitor
			(ArchitectFrame.this, "Saving Project", "", 0, 100);
		new Thread() {
			public void run() {
				try {
					project.save(pm);
				} catch (Exception ex) {
					JOptionPane.showMessageDialog
						(ArchitectFrame.this,
						 "Can't save project: "+ex.getMessage());
					logger.error("Got exception while saving project", ex);
				}
			}
		}.start();
	}
}
