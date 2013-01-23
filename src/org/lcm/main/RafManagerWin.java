package org.lcm.main;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;

import org.lcm.managers.CustomModManager;
import org.lcm.managers.IniManager;
import org.lcm.managers.RafManager;
import org.lcm.model.CustomMod;
import org.lcm.model.RafFileEntry;
import org.lcm.model.RafFileList;
import org.lcm.updater.LoLUpdater;
import org.lcm.utils.IniFile;
import org.lcm.utils.Log;


public class RafManagerWin extends JFrame implements PropertyChangeListener {

	private static final long serialVersionUID = 1L;
	// tree data
	private JTree tree;
	private DefaultTreeModel treeModel;
	private DefaultMutableTreeNode root;
	private List<DefaultMutableTreeNode> rawClassificationNodes;
	private DefaultMutableTreeNode organizedClassificationNode;
	private boolean isRawClassification = false;
	// list data
	private DefaultListModel listModel;
	private JList modsList;
	// data
	private JProgressBar progressBar;
	private CustomModManager modsManager;
	private CustomModManager.SaveTask task;
	private JButton save;
	
	private Map<RafManager, DefaultMutableTreeNode> rafNodes = new HashMap<RafManager, DefaultMutableTreeNode>();
	
	public static final String version = "0.0.2";
	
	public static class DebugOutput {
		private static JTextArea area;
		public void setField(JTextArea are) {
			area = are;
		}
		public static void println(String str) {
			if (area != null) {
				area.append(str + "\n");
			}
		}
	}
	
	private class NodeData {
		private String text;
		private RafManager manager;
		private int rafFileEntryId;
		public NodeData(String f, RafManager m) {
			text = f;
			manager = m;
			rafFileEntryId = -1;
		}
		public NodeData(String f, RafManager m, int entryId) {
			text = f;
			manager = m;
			rafFileEntryId = entryId;
		}
		public String getNodeText() {
			return text;
		}
		public int getFileId() {
			return rafFileEntryId;
		}
		public String toString() {
			return text;
		}
		public RafManager getManager() {
			return manager;
		}
	}
	
	public class LogStream extends PrintStream {
		PrintStream outs = System.out;
		public LogStream() {
			super(System.out);
		}
		
		public void println(String str) {
			super.println(str);
			RafManagerWin.DebugOutput.println(str);
		}
	}
	
	public String getRafPath(boolean forceChange) {
		IniFile.IniSection config = IniManager.getInst().getConfigIni().getSection("general", true);
		if (!forceChange) {
			String rafPath = config.get("rafPath");
			// check if we have one in our config file and if it's correct
			if (rafPath != null) {
				File fi = new File(rafPath);
				if (!rafPath.isEmpty() && fi.exists()) {
					return fi.getAbsolutePath();
				}
			}
		}
		// select folder dialog
		JFileChooser chooser = new JFileChooser(".");
		chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		chooser.setDialogTitle("Select your iLoL .app file (or the LoL install directory for Windows users)");
		int retval = chooser.showOpenDialog(RafManagerWin.this);
        if (retval == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            // now we make sure the selected folder is correct
            String path = isCorrectFolder(file);
            if (path != null) {
            	config.put("rafPath", path);
            	IniManager.getInst().getConfigIni().save();
            	return path;
            }
        }
        return null;
	}
	
	// return path of the raf folder if correct, null otherwise
	public String isCorrectFolder(File folder) {
		File iLoL = new File(folder.getAbsolutePath() + "/Contents/Resources/League of Legends/archives");
		if (iLoL.exists() && iLoL.isDirectory()) {
			return iLoL.getAbsolutePath();
		}
		// TODO: fix for windows !
		File windows = new File(folder.getAbsolutePath() + "\\rads\\projects\\lol_game_client\\filearchives");
		if (windows.exists() && windows.isDirectory()) {
			return windows.getAbsolutePath();
		}
		return folder.getAbsolutePath();
	}
	
	public RafManagerWin() {
		//String folder = "/Applications/Games/iLoL Open Beta 1.1.app/Contents/Resources/League of Legends/archives";
		//String folder = ".";
		
		String folder = getRafPath(false);
		modsManager = new CustomModManager(folder);
		
        JTextArea textArea = new JTextArea();
        DebugOutput.area = textArea;
        
        // TODO: auto debug in textfield
        //System.setOut(new LogStream());
		
		if (modsManager.getManagers().size() == 0) {
			IniManager.getInst().getConfigIni().removeValue("general", "rafPath");
			IniManager.getInst().getConfigIni().save();
			this.setEnabled(false);
			DebugOutput.println("Folder '" + folder + "' does not contain any .raf file. \nPlease restart the program to chose a new folder");
		}
		
		// creating tree
		root = new DefaultMutableTreeNode("Raf Files");
		treeModel = new DefaultTreeModel(root);
		tree = new JTree(treeModel);
		
		// could use this to optimize loading (not sure if useful tho)
		tree.addTreeWillExpandListener(new TreeWillExpandListener() {
			public void treeWillExpand(TreeExpansionEvent e)
					throws ExpandVetoException {
				TreePath node = e.getPath();
				DefaultMutableTreeNode data = (DefaultMutableTreeNode)node.getLastPathComponent();
				NodeData d = (NodeData)data.getUserObject();
				System.out.println(d.getNodeText());
			}
			public void treeWillCollapse(TreeExpansionEvent arg0)
					throws ExpandVetoException {
			}
		});

		rawClassificationNodes = new ArrayList<DefaultMutableTreeNode>();
		
		List<RafManager> rafList = new ArrayList<RafManager>(modsManager.getManagers().keySet());
		Collections.sort(rafList, new Comparator<RafManager>() {
			public int compare(RafManager o1, RafManager o2) {
				return o1.getRafFullFilename().compareTo(o2.getRafFullFilename());
			}
		});
		
		for (RafManager raf : rafList) {
			
			// used as root node for each sub tree
			String rafName = new File(raf.getRafFullFilename()).getParentFile().getName();
			DefaultMutableTreeNode rafNode = new DefaultMutableTreeNode(new NodeData(rafName, raf));
			rafNodes.put(raf, rafNode);
			for (int i=0; i<raf.getFileCount(); i++) {
				RafFileEntry entry = raf.getFileEntry(i);
				String file = entry.getFullFilename();
				getNode(rafNode, file, raf, i);
			}
			rawClassificationNodes.add(rafNode);
			
//			DefaultMutableTreeNode cat = new DefaultMutableTreeNode(new NodeData(raf.getRafFullFilename(), raf));
//			RafFileList list = raf.getPathOrdererFileList();
//			for (RafFileEntry entry : list) {
//				DefaultMutableTreeNode scat = new DefaultMutableTreeNode(new NodeData(entry.getFullFilename(), raf, entry.getId()));
//				treeModel.insertNodeInto(scat, cat, cat.getChildCount());
//			}
//			rawClassificationNodes.add(cat);

			
//			DefaultMutableTreeNode cat = new DefaultMutableTreeNode(new NodeData(raf.getRafFullFilename(), raf));
//			for (int j=0; j<raf.getFileCount(); j++) {
//				DefaultMutableTreeNode scat = new DefaultMutableTreeNode(new NodeData(raf.getFilePath(j), raf, j));
//				treeModel.insertNodeInto(scat, cat, cat.getChildCount());
//			}
//			rawClassificationNodes.add(cat);
		}
		
//		SwingWorker worker = new SwingWorker<Void, Void>() {
//		    @Override
//		    public Void doInBackground() {
//		    	buildTree(root);
//				return null;
//		    }
//
//		    @Override
//		    public void done() {
//		        try {
//		            get();
//		        } catch (InterruptedException ignore) {}
//		        catch (java.util.concurrent.ExecutionException e) {
//		            String why = null;
//		            Throwable cause = e.getCause();
//		            if (cause != null) {
//		                why = cause.getMessage();
//		            } else {
//		                why = e.getMessage();
//		            }
//		            System.err.println("Error retrieving file: " + why);
//		        }
//		    }
//		};
//		worker.execute();
				
		buildTree(root);
		
		tree.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent me) {
				TreePath tp = tree.getPathForLocation(me.getX(), me.getY());
				if (tp == null) return;
				final DefaultMutableTreeNode sel = (DefaultMutableTreeNode)tp.getPathComponent(tp.getPathCount()-1);
				if (sel.isLeaf() && me.getClickCount() == 2) {
					NodeData nd = (NodeData)sel.getUserObject();
					RafFileEntry entry = nd.getManager().getFileEntry(nd.getFileId());
					boolean gotIt = nd.getManager().getFileFromRaf(entry, ".");
		    		if (gotIt) {
		    			DebugOutput.println("Successfuly Retrieved file " + nd.getNodeText() + " in '" + new File(".").getAbsolutePath() + "'");
		    		}
		    		else {
		    			DebugOutput.println("Error while retrieving " + nd.getNodeText());
		    		}
		    		
					//nd.getManager().dumpRafDataFile("DUMP");

				}
				else if (!sel.isLeaf() && me.getButton() == MouseEvent.BUTTON3) {
					
					JPopupMenu popup = new JPopupMenu();
					JMenuItem dump = new JMenuItem("Dump node");
					dump.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent arg0) {
							dumpNode(sel, "DUMP");
							DebugOutput.println("Node '" + ((NodeData)sel.getUserObject()).getNodeText() +"' has been dumped into " + new File("DUMP").getAbsolutePath());
						}
					});
					popup.add(dump);
					popup.show(tree, me.getX(), me.getY());
				}
			}
		});
		
	    JScrollPane treeView = new JScrollPane(tree);
	    treeView.setPreferredSize(new Dimension(342, 350));
	    
	    JPanel basePanel = new JPanel();
	    
	    JPanel mainPanel = new JPanel();
	    mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.X_AXIS));

	    JPanel treePan = new JPanel();
	    treePan.setLayout(new BoxLayout(treePan, BoxLayout.PAGE_AXIS));

	    JLabel jLabel2 = new JLabel("Raf data");
	    jLabel2.setAlignmentX(CENTER_ALIGNMENT);
	    treePan.add(jLabel2);
	    treePan.add(treeView);
	    
	    treePan.add(Box.createVerticalStrut(7));
	    
        JButton b = new JButton("Change classification");
        b.setAlignmentX(CENTER_ALIGNMENT);
        b.setPreferredSize(new Dimension(150, 30));
        b.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				switchClassification();
			}
        });
        treePan.add(b);
	    
	    mainPanel.add(treePan);
	    
	    listModel = new DefaultListModel();
	    modsList = new JList(listModel) {
			private static final long serialVersionUID = 1L;
			public String getToolTipText(MouseEvent e) {
	    		int index = locationToIndex(e.getPoint());
	    		if (-1 < index) {
	    			CustomMod item = (CustomMod)getModel().getElementAt(index);
	    			if (item.getState().equals("1")) {
	    				return "Mod '" + item.getName() + "' enabled";
	    			}
	    			else if (item.getState().equals("0")) {
	    				return "Mod '" + item.getName() + "' disabled";
	    			}
	    			else if (item.getState().equals("10")) {
	    				return "Mod '" + item.getName() + "' ready to be disabled";
	    			}
	    			else if (item.getState().equals("01")) {
	    				return "Mod '" + item.getName() + "' ready to be enabled";
	    			}
	    			return "error";
	    		}
	    		else {
	    			return null;
	    		}
	    	}
	    };
	    
	    modsList.addMouseListener(new MouseListener() {
			public void mouseClicked(MouseEvent e) {
				if (e.getButton() == MouseEvent.BUTTON3) {
					if (modsList.getSelectedValue() != null) {
						final CustomMod cs = (CustomMod)modsList.getSelectedValue();
								
						JPopupMenu popup = new JPopupMenu();
						JMenuItem delete = new JMenuItem("Delete custom mod");
						delete.addActionListener(new ActionListener() {
							public void actionPerformed(ActionEvent arg0) {
								if (cs.isDisabled()) {
									modsManager.deleteCustomMod(cs);
									fillModsList();
								}
								else {
									DebugOutput.println("Cannot delete mod '" + cs.getName() +"' as it is still installed. Uninstall it first");
								}
							}
						});
						popup.add(delete);
						
						JMenuItem checkIntegrity = new JMenuItem("Check mod integrity");
						checkIntegrity.addActionListener(new ActionListener() {
							public void actionPerformed(ActionEvent arg0) {
								modsManager.checkModIntegrity(cs);
							}
						});
						popup.add(checkIntegrity);
						
						JMenuItem checkConflicts = new JMenuItem("Check mod conflicts");
						checkConflicts.addActionListener(new ActionListener() {
							public void actionPerformed(ActionEvent arg0) {
								modsManager.checkModConflicting(cs);
							}
						});
						popup.add(checkConflicts);
						
						popup.show(modsList, e.getX(), e.getY());
					}
				}
			}
			public void mouseEntered(MouseEvent arg0) {}
			public void mouseExited(MouseEvent arg0) {}
			public void mousePressed(MouseEvent arg0) {}
			public void mouseReleased(MouseEvent arg0) {}
	    });
	    JScrollPane listView = new JScrollPane(modsList);
	    listView.setPreferredSize(new Dimension(180, 350));
	    
	    JPanel listPan = new JPanel();
	    listPan.setLayout(new BoxLayout(listPan, BoxLayout.PAGE_AXIS));

	    listPan.add(new JLabel("Mods library"));
	    listPan.add(listView);
	    
	    mainPanel.add(listPan);
	    
	    GridLayout experimentLayout = new GridLayout(0,1);
	    experimentLayout.setVgap(5);
	    JPanel buttonsPan = new JPanel(experimentLayout);
	    
	    JPanel optionsPan = new JPanel();
	    optionsPan.setLayout(new BoxLayout(optionsPan, BoxLayout.PAGE_AXIS));
	    JLabel jLabel = new JLabel("Options");
	    jLabel.setAlignmentX(CENTER_ALIGNMENT);
	    optionsPan.add(jLabel);
	    optionsPan.add(buttonsPan);
	    mainPanel.add(optionsPan);
        
        
        JButton addSkin = new JButton("Add custom mod");
        addSkin.setPreferredSize(new Dimension(120, 30));
        addSkin.setMinimumSize(new Dimension(120, 30));
        addSkin.setMaximumSize(new Dimension(120, 30));
        addSkin.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				JFileChooser chooser = new JFileChooser(".");
				chooser.setFileSelectionMode( JFileChooser.DIRECTORIES_ONLY);
				int retval = chooser.showOpenDialog(RafManagerWin.this);
	            if (retval == JFileChooser.APPROVE_OPTION) {
	                File file = chooser.getSelectedFile(); 
	                String modName = JOptionPane.showInputDialog("Enter this custom mod name: ", file.getName());	              
	                if (modName != null && !modName.isEmpty()) {
	                	modsManager.addCustomMod(file, modName);
	                }
	            }
				fillModsList();

			}
        });
        buttonsPan.add(addSkin);

        JButton enableSkin = new JButton("Enable custom mod");
        enableSkin.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if (modsList.getSelectedValue() == null) return;
				DebugOutput.println("Enabling custom mod '" + ((CustomMod)modsList.getSelectedValue()).getName() + "'");
				modsManager.enableMod((CustomMod)modsList.getSelectedValue());
				fillModsList();
			}
        });
        buttonsPan.add(enableSkin);

        JButton disableSkin = new JButton("Disable custom mod");
        disableSkin.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if (modsList.getSelectedValue() == null) return;
				DebugOutput.println("Disabling custom mod '" + ((CustomMod)modsList.getSelectedValue()).getName() + "'");
				modsManager.disableMod((CustomMod)modsList.getSelectedValue());
				fillModsList();
			}
        });
        buttonsPan.add(disableSkin);

        save = new JButton("Pack !");
        save.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				task = modsManager.new SaveTask();
		        task.addPropertyChangeListener(RafManagerWin.this);
		        save.setEnabled(false);
		        task.execute();
			}
        });
        buttonsPan.add(save);
        
        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(100, 30));
        buttonsPan.add(progressBar);
        
	    GridLayout experimentLayout2 = new GridLayout(1,2);
	    experimentLayout2.setHgap(5);
        JPanel findP = new JPanel(experimentLayout2);

        final JTextField field = new JTextField();
        field.setPreferredSize(new Dimension(20, 25));
        findP.add(field);

        JButton find = new JButton("Find");
        find.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				modsManager.findFiles(field.getText().toLowerCase().trim());
			}
        });
        findP.add(find);
        
        buttonsPan.add(findP);
        
        JButton restore = new JButton("Restore all");
        restore.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
   			 int res = JOptionPane.showConfirmDialog(RafManagerWin.this, "ARE YOU SURE ? - have you try the other solutions ???\nDo not use if you have no idea what you're doing",
   					 "Restaure", JOptionPane.OK_CANCEL_OPTION);
   			 if (res == JOptionPane.OK_OPTION) {
        		restoreAll();
   			 }
			}
        });
        buttonsPan.add(restore);

        JButton update = new JButton("Check update");
        update.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				LoLUpdater.checkUpdate(false);
			}
        });
        buttonsPan.add(update);
        
        basePanel.add(mainPanel, BorderLayout.CENTER);
        
        JPanel logPan = new JPanel();
        logPan.setLayout(new BoxLayout(logPan, BoxLayout.PAGE_AXIS));

        JPanel pm = new JPanel();
	    JLabel jLabel3 = new JLabel("Debug");
	    jLabel3.setPreferredSize(new Dimension(700, 20));
	    jLabel3.setAlignmentX(LEFT_ALIGNMENT);
	    pm.add(jLabel3);
	    logPan.add(pm);
        
        GridLayout experimentLayout3 = new GridLayout(0,1);
        JPanel logPanelIn = new JPanel(experimentLayout3);
        

        JScrollPane scrollPane = new JScrollPane(textArea); 
        textArea.setEditable(false);
        scrollPane.setPreferredSize(new Dimension(655, 150));
        
        logPanelIn.add(scrollPane);
        
        logPan.add(logPanelIn);
        
        basePanel.add(logPan, BorderLayout.LINE_START);

        

        this.add(basePanel);
        
		this.setSize(720, 625);
        this.setMinimumSize(new Dimension(720, 625));
        this.setPreferredSize(new Dimension(720, 625));
        this.setLocationRelativeTo(null);
        this.setResizable(true);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setTitle("Raf Browser + Mods Manager (" + version + ")");
        this.setJMenuBar(createMenus());
        
        switchClassification();
        
        // fill mod list
        fillModsList();
        
        this.setVisible(true);
        
	}
	
	public JMenuBar createMenus() {
		 JMenuBar menuBar;
    	 JMenu options;
    	 JMenuItem reset;

    	 //Create the menu bar.
    	 menuBar = new JMenuBar();
    	 
    	 options = new JMenu("Options");
    	 
    	 reset = new JMenuItem("Change client folder");
    	 reset.addActionListener(new ActionListener() {
    		 public void actionPerformed(ActionEvent e) {
    			 getRafPath(true);
    			 JOptionPane.showConfirmDialog(RafManagerWin.this, "The program will close now. Restart it!", "Restart needed", JOptionPane.OK_OPTION);
    			 System.exit(0);
    		 }
    	 });
    	 
    	 options.add(reset);
    	 menuBar.add(options);
    	 
    	 return menuBar;
	}
	
	public void fillModsList() {
        listModel.clear();
        for (CustomMod mod : modsManager.getModsList()) {
        	listModel.addElement(mod);
        }
	}
	
	public void switchClassification() {
    	isRawClassification = !isRawClassification;
		root.removeAllChildren();
		if (isRawClassification) {
			for (DefaultMutableTreeNode node : rawClassificationNodes) {
				treeModel.insertNodeInto(node, root, root.getChildCount());
			}
		}
		else {
			DefaultMutableTreeNode r = buildTree(root);
			treeModel.insertNodeInto(r, root, root.getChildCount());
		}
		treeModel.reload();
		
		tree.expandRow(isRawClassification ? 0 : 1);
    }
	
	public DefaultMutableTreeNode buildTree(DefaultMutableTreeNode top) {
		if (organizedClassificationNode == null) {
			Log.getInst().info("Creating classification structure...");
			long startTime = System.currentTimeMillis();
			for (RafManager man : modsManager.getManagers().keySet()) {
				/*for (int i=0; i<man.getFileCount(); i++) {
					RafFileEntry entry = man.getFileEntry(i);
					String file = entry.getFullFilename();
					getNode(top, file, man, i);
				}*/
				RafFileList list = man.getPathOrdererFileList();
				for (int i=0; i<list.size(); i++) {
					RafFileEntry entry = list.get(i);
					String file = entry.getFullFilename();
					getNode(top, file, man, entry.getId());
				}
			}
			Log.getInst().info("Created classification structure in " + (System.currentTimeMillis() - startTime) + " ms");
			if (top.getChildCount() > 0) {
				organizedClassificationNode = (DefaultMutableTreeNode)top.getFirstChild();
			}
		}
		return organizedClassificationNode;
	}
	
	public DefaultMutableTreeNode getNode(DefaultMutableTreeNode top, String nodeName, RafManager man, int entryId) {
		// we only keep de data folder
		if (!nodeName.startsWith("DATA/")) return null;
		String[] split = nodeName.split("/");
		DefaultMutableTreeNode mov = top;
		for (int i=0; i<split.length; i++) {
			NodeData nd;
			// file
			if (i == split.length -1) {
				nd = new NodeData(split[i], man, entryId);
			}
			else {
				nd = new NodeData(split[i], man);
			}
			// node has no children, no problem then just insert it
			if (mov.getChildCount() == 0) {
				DefaultMutableTreeNode n = new DefaultMutableTreeNode(nd);
				treeModel.insertNodeInto(n, mov, mov.getChildCount());
				mov = n;
			}
			// node has children
			else {
				boolean found = false;
				int insertPos = -1;
				for (int j=0; j<mov.getChildCount(); j++) {
					DefaultMutableTreeNode child = (DefaultMutableTreeNode)mov.getChildAt(j);
					NodeData path = (NodeData)child.getUserObject();
					// already exists
					if (path.toString().equals(split[i])) {
						mov = child;
						found = true;
						break;
					}
					else {
						// find correct position to insert node (orderer by name)
						if (path.toString().compareTo(split[i]) > 0 && insertPos == -1) {
							insertPos = j;
						}
					}
				}
				// child doesnt exists, create it
				if (!found) {
					DefaultMutableTreeNode n = new DefaultMutableTreeNode(nd);
					if (insertPos == -1) {
						treeModel.insertNodeInto(n, mov, mov.getChildCount());
					}
					else {
						treeModel.insertNodeInto(n, mov, insertPos);
					}
					mov = n;
				}
			}
		}
		for (int i=0; i<split.length; i++) {
			mov = (DefaultMutableTreeNode)mov.getParent();
		}
		return null;
	}
	
	public void restoreAll() {
		// restaure files
		modsManager.restoreAll();
		// update list
		fillModsList();
		DebugOutput.println("Restore done! Do not forget to pack to confirm these changes.");
	}
	
//	public RafFileEntry findFileFromPath(String match) {
//		String[] split = match.split("/");
//		DefaultMutableTreeNode node = organizedClassificationNode;
//		// we skip 0 as split[0] SHOULD BE 'data' and node is also the data node
//		for (int i=1; i<split.length; i++) {
//			boolean found = false;
//			for (int j=0; j<node.getChildCount(); j++) {
//				DefaultMutableTreeNode child = (DefaultMutableTreeNode)node.getChildAt(j);
//				NodeData data = (NodeData)child.getUserObject();
//				if (data.getNodeText().equals(split[i])) {
//					node = child;
//					System.out.println("found " + split[i]);
//					found = true;
//					break;
//				}
//			}
//			if (!found) {
//				return null;
//			}
//		}
//		NodeData data = (NodeData)node.getUserObject();
//		if (data.getFileId() == -1) return null;
//		return data.getManager().getFileEntry(data.getFileId());
//	}
	
	public void dumpNode(DefaultMutableTreeNode fromNode, String folder) {
		Enumeration<?> e = fromNode.preorderEnumeration();
		while (e.hasMoreElements()) {
			DefaultMutableTreeNode child = (DefaultMutableTreeNode)e.nextElement();
			if (child.isLeaf()) {
				NodeData userObject = (NodeData)child.getUserObject();
				RafManager manager = userObject.getManager();
				RafFileEntry entry = manager.getFileEntry(userObject.getFileId());
			  
				String path = folder + File.separator + entry.getFullFilename().substring(0, entry.getFullFilename().lastIndexOf("/"));
				File folderFile = new File(path);
				if (!folderFile.exists()) {
					folderFile.mkdirs();
				}
				
				System.out.println("getting file " + entry.getFilename() + " in path " + path);
				manager.getFileFromRaf(entry, path);
			}
		}
	 }

	public void propertyChange(PropertyChangeEvent evt) {
        if ("progress" == evt.getPropertyName()) {
            int progress = (Integer) evt.getNewValue();
            progressBar.setValue(progress);
            System.out.println("Completed " + task.getProgress() + "% of save task");
            if (progress == 100) {
            	save.setEnabled(true);
            }
        	fillModsList();
        }
	}
	
}
    