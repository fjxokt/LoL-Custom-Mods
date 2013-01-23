package org.lcm.managers;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.swing.SwingWorker;

import org.lcm.main.RafManagerWin;
import org.lcm.model.CustomMod;
import org.lcm.model.RafFileEntry;
import org.lcm.utils.IniFile;
import org.lcm.utils.Log;
import org.lcm.utils.Utils;


public class CustomModManager {
	
	private List<CustomMod> mods;
	private Map<RafManager, Boolean> managers;
	// folders
	public static final String dataFolder = "data";
	public static final String modsFolder = dataFolder + File.separator + "mods";
	public static final String backupFolder = dataFolder + File.separator + "backup";
	
	public CustomModManager(String rafFolder) {
		File fold = new File(rafFolder);
		if (!fold.exists() || !fold.isDirectory()) {
			Log.getInst().warning("Could not load raf ressources from folder '" + fold.getAbsolutePath() + "'");
			return;
		}
		
		// TODO: create a tree structure of the files ?
		// use DefaultTreeNode ?
		// being able to serialize it would be perfect
	
		// load rafs
		managers = new HashMap<RafManager, Boolean>();		
		File[] listOfFiles = fold.listFiles();
		// load all our manager (one per raf file)
		for (int i = 0; i < listOfFiles.length; i++) {
			if (listOfFiles[i].isDirectory()) {
				File[] rafF = listOfFiles[i].listFiles();
				for (File f : rafF) {
					if (f.getName().endsWith(".raf")) {
						// create new manager for the file found
						RafManager raf = new RafManager(f.getAbsolutePath());
						// if files in the raf
						if (raf.getFileCount() > 0) {
							// add it to the map
							managers.put(raf, Boolean.FALSE);
						}
					}
				}
			}
		}
		
		loadCustomMods();
		
		// TODO: for each mod, check and correct integrity
		// in the future, it would be nice to try to reinstall mods that were supposed to be installer
		// but that actually werent
		// TODO: call it fixModIntegrity instead ? and do this inside the method ?
		for (CustomMod mod : mods) {
			CustomMod.ModIntegrity status = checkModIntegrity(mod);
			if (status.equals(CustomMod.ModIntegrity.ENABLED) || status.equals(CustomMod.ModIntegrity.PARTLY_ENABLED)) {
				mod.setState("1");
			}
			else {
				mod.setState("0");
			}
		}
		//TODO: finish
    	saveCustomMods();
	}
	
	public Map<RafManager, Boolean> getManagers() {
		return managers;
	}
	
	public List<CustomMod> getModsList() {
		return mods;
	}
	
	public CustomMod.ModIntegrity checkModIntegrity(CustomMod mod) {
		// skip not enabled mods
		if (!mod.isEnabled()) {
			return CustomMod.ModIntegrity.DISABLED;
		}
		
		System.out.println("Checking installed mod " + mod.getName() + "...");
		RafManagerWin.DebugOutput.println("Checking installed mod " + mod.getName() + "...");
		
		List<File> skippedFiles = new ArrayList<File>();
		List<File> notFoundFiles = new ArrayList<File>();
		List<File> modifiedFiles = new ArrayList<File>();
		List<File> installedFiles = new ArrayList<File>();
		int totalModifs = 0;

		// for each file of the mod
		for (File file : mod.getFilesList()) {
			// get the corresponding entries
			List<RafFileEntry> entries = getEntriesForFile(file);
			
			if (entries.size() == 0) {
				notFoundFiles.add(file);
				continue;
			}
			
			totalModifs += entries.size();
			
			// now its time to check if they are still installed
			for (RafFileEntry entry : entries) {					
				// get the backup data
				IniFile.IniSection sec = IniManager.getInst().getConfigIni().getSection("backup", true);
				String backupFile = sec.get(entry.getFullFilename().toLowerCase());
				if (backupFile == null) {
					// if no backup file we have a pb: the file hasnt probably been installed
					System.out.println("file " + file.getAbsolutePath() + " hasnt been modified before");
					skippedFiles.add(new File(entry.getFullFilename()));
					continue;
				}
				
				String[] data = backupFile.split(":");
				int customFileSize = Integer.parseInt(data[2]);
				
				// it entry size differente from the custom file we put instead, it means the file has been modified
				if (entry.getDataSize() != customFileSize) {
					modifiedFiles.add(new File(entry.getFullFilename()));
					// if we're here it means the file is not installed anymore
					// the entry is whether the original file or a new file (from a patch process ?)
					// anyway, the backup file is not usefull anymore
					System.out.println("Original file changed (back to normal or patch), removing backup file");
					// we have to remove the backup file as it is apparently incorrect now
					sec.remove(entry.getFullFilename().toLowerCase());
					// and remove the actual file
					new File(backupFolder + File.separator + data[1]).delete();
				}
				else {
					installedFiles.add(new File(entry.getFullFilename()));
				}
			}
		}
		
		// save changes on ini file
		IniManager.getInst().getConfigIni().save();
		
		// display stats
		System.out.println("- total files: " + totalModifs + " (" + mod.getFilesList().size() + ")");
		System.out.println("- files installed: " + installedFiles.size() + " : " + Arrays.toString(installedFiles.toArray()));
		System.out.println("- files not found: " + notFoundFiles.size() + " : " + Arrays.toString(notFoundFiles.toArray()));
		System.out.println("- files skipped: " + skippedFiles.size() + " : " + Arrays.toString(skippedFiles.toArray()));
		System.out.println("- files modified: " + modifiedFiles.size() + " : " + Arrays.toString(modifiedFiles.toArray()));
		RafManagerWin.DebugOutput.println("- total files: " + totalModifs + " (" + mod.getFilesList().size() + ")");
		RafManagerWin.DebugOutput.println("- files installed: " + installedFiles.size() + " : " + Arrays.toString(installedFiles.toArray()));
		RafManagerWin.DebugOutput.println("- files not found: " + notFoundFiles.size() + " : " + Arrays.toString(notFoundFiles.toArray()));
		RafManagerWin.DebugOutput.println("- files skipped: " + skippedFiles.size() + " : " + Arrays.toString(skippedFiles.toArray()));
		RafManagerWin.DebugOutput.println("- files modified: " + modifiedFiles.size() + " : " + Arrays.toString(modifiedFiles.toArray()));

		
		int totalErr = notFoundFiles.size() + skippedFiles.size() + modifiedFiles.size();
		
		if (totalErr == 0) {
			System.out.println("Mod fully enabled");
			RafManagerWin.DebugOutput.println("Mod fully enabled");
			return CustomMod.ModIntegrity.ENABLED;
		}
		else if (installedFiles.size() != 0) {
			System.out.println("Mod partially enabled");
			RafManagerWin.DebugOutput.println("Mod partially enabled");
			return CustomMod.ModIntegrity.PARTLY_ENABLED;
		}
		else {
			System.out.println("Mod should be disabled !!");
			RafManagerWin.DebugOutput.println("Mod should be disabled !!");
			return CustomMod.ModIntegrity.GHOST;
		}
	}
	
	// check what mods are installed and haven't been modified by any patch whatsoever...
	public void checkModsIntegrity() {
		System.out.println("Checking installed mods integrity...");
		for (CustomMod mod : mods) {
			checkModIntegrity(mod);
		}
		System.out.println("done.");
	}
	
	// check if the mod mod would conflict with any of the already enabled mods
	public List<CustomMod> checkModConflicting(CustomMod modToCheck) {
		List<CustomMod> conflictingMods = new ArrayList<CustomMod>();
		// for every file of the selected mod
		for (File file : modToCheck.getFilesList()) {
			// get the corresponding entries
			List<RafFileEntry> entries = getEntriesForFile(file);
			// for each entry
			for (RafFileEntry entry : entries) {
				// for each mod
				next: for (CustomMod mod : mods) {
					// skip mod we're checking and not enabled mods
					if (modToCheck == mod || mod.isDisabled() || conflictingMods.contains(mod)) {
						continue;
					}
					// for each of its file
					for (File f : mod.getFilesList()) {
						// get entries list of the other mod
						List<RafFileEntry> otherEntries = getEntriesForFile(f);
						// for each of its entry 
						for (RafFileEntry otherentry : otherEntries) {
							// if same filename, this two mods conflicts
							if (entry.getFullFilename().equals(otherentry.getFullFilename())) {
								conflictingMods.add(mod);
								continue next;
							}
						}
					}
				}
			}
		}
		
		return conflictingMods;
	}
	
	public void addCustomMod(File modFolder, String modName) {
		if (!modFolder.exists() || !modFolder.isDirectory()) {
			Log.getInst().warning("'" + modFolder.getAbsolutePath() + "' is not a folder or doesn't exist");
			return;
		}
		// we will parse the dir and copy it to our own structure
		File newFolder = new File(CustomModManager.modsFolder + File.separator + modName);
		
		// build already loaded ?
		if (newFolder.exists()) {
			Log.getInst().warning("Custom mod '" + modName + "' has already been loaded");
			return;
		}
		
		try {
			Utils.copyFolder(modFolder, newFolder);
		} catch (IOException e) {
			e.printStackTrace();
			Log.getInst().severe("Could not copy folder '" + modFolder.getAbsolutePath() + "' into '" + newFolder.getAbsolutePath() + "'");
			return;
		}
		
		CustomMod cs = new CustomMod(newFolder.getPath(), modName, "0", true);
		mods.add(cs);
		
		saveCustomMods();
	}
	
	public void deleteCustomMod(CustomMod cs) {
		File folder = new File(cs.getFolder());
		// deleting mod folder
		if (folder.exists() && folder.isDirectory()) {
			Utils.deleteFolder(folder);
		}
		// and cs
		mods.remove(cs);
		// save
		saveCustomMods();
		Log.getInst().info("Custom mod '" + cs.getName() + "' has been correctly deleted");
	}
	
	public void loadCustomMods() {
		// load mods
		mods = new ArrayList<CustomMod>();
		IniFile modsIni = IniManager.getInst().getModsIni();
		for (int i=0; i<modsIni.size(); i++) {
			IniFile.IniSection mod = modsIni.getSection(i);
			CustomMod cs = new CustomMod(mod.get("folder"), mod.get("name"), mod.get("state").substring(0, 1), false);
			String fname = mod.get("file_0");
			int j=0;
			while (fname != null) {
				j++;
				cs.addFile(new File(fname));
				fname = mod.get("file_" + j);
			}
			mods.add(cs);
		}
		Log.getInst().info(mods.size() + " custom mods loaded");
	}
	
	public void saveCustomMods() {
		IniFile sav = IniManager.getInst().getModsIni();
		sav.clear();
		for (CustomMod mod : mods) {
			IniFile.IniSection sec = sav.createSection(mod.getName());
			sec.put("folder", mod.getFolder());
			sec.put("name", mod.getName());
			sec.put("state", mod.getState());
			for (int i=0; i<mod.getFilesList().size(); i++) {
				sec.put("file_" + i, mod.getFilesList().get(i).getPath());
			}
		}
		sav.save();
		Log.getInst().info(mods.size() + " custom mods saved");
	}
	
	// use with any string
	public void findFiles(String match) {
		
		// this part was just for testing original files verification, moting to do with the rest
		for (RafManager rm : managers.keySet()) {
			Boolean b = rm.isFileOriginal(match);
			if (b != null) {
				System.out.println("original file: " + b);
			}
			else {
				System.out.println("file not found in " + rm.getRafFilename());
			}
		}
		
		Map<RafManager, List<RafFileEntry>> res = new HashMap<RafManager, List<RafFileEntry>>();
		for (RafManager rm : managers.keySet()) {
			List<RafFileEntry> lst = rm.findFileEntries(match);
			if (lst.size() > 0) {
				res.put(rm, lst);
			}
		}
		RafManagerWin.DebugOutput.println("'" + match + "' matches in " + res.size() + " raf files");
		for (RafManager key : res.keySet()) {
			List<RafFileEntry> entries = res.get(key);
			RafManagerWin.DebugOutput.println(key.getRafFilename() + " : " + entries.size() + " matches");
			for (RafFileEntry entry : entries) {
				RafManagerWin.DebugOutput.println(entry.getFullFilename());
			}
		}
	}
	
	// use with a filename (without path) (not used for search)
	public Map<RafManager, List<RafFileEntry>> getFiles(String filename) {
		Map<RafManager, List<RafFileEntry>> res = new HashMap<RafManager, List<RafFileEntry>>();
		for (RafManager rm : managers.keySet()) {
			List<RafFileEntry> lst = rm.getFileEntries(filename);
			if (lst.size() > 0) {
				res.put(rm, lst);
			}
		}
		return res;
	}
	
	// return entry from a filename (ex: /DATA/items/blabla.jpg)
	// TODO: could be improved if we would have a tree structure
	public RafFileEntry findEntryFromName(String filename) {
		String name = new File(filename).getName();
		// get possible matches
		Map<RafManager, List<RafFileEntry>> files = getFiles(name);
		// for each entry, check if the name is the one researched
		for (List<RafFileEntry> entries : files.values()) {
			for (RafFileEntry entry : entries) {
				// found it
				if (entry.getFullFilename().toLowerCase().equals(filename.toLowerCase())) {
					return entry;
				}
			}
		}
		return null;
	}
	
	public void enableMod(CustomMod mod) {
		// check for conflicts with enabled mods
		List<CustomMod> conflicts = checkModConflicting(mod);
		
		if (conflicts.size() == 0) {
			setModState(mod, true);
		}
		else {
			RafManagerWin.DebugOutput.println("Mod " + mod.getName() + " conflicts with " + conflicts.size() + " mods. Disable them to enable this one.");
			for (CustomMod cmod : conflicts) {
				RafManagerWin.DebugOutput.println(" -  mod " + cmod.getName());
			}
		}
	}
	
	public void disableMod(CustomMod mod) {
		setModState(mod, false);
	}
	
	// TODO: return an enum and do the printout on the display side, not here
	private void setModState(CustomMod mod, boolean state) {
		boolean atLeastOneChange = false;
		for (File file : mod.getFilesList()) {
			boolean s = setFileState(file, state);
			if (s && !atLeastOneChange) {
				atLeastOneChange = true;
			}
		}
		if (!atLeastOneChange) {
			Log.getInst().info("Custom mod '" + mod.getName() + "' did not change any file, mod not installed");
			RafManagerWin.DebugOutput.println("Custom mod '" + mod.getName() + "' did not change any file, mod not installed");
			return;
		}
		if (state) {
			Log.getInst().info("Custom mod '" + mod.getName() + "' has correctly been enabled");
			mod.setState("01");
		}
		else {
			Log.getInst().info("Custom mod '" + mod.getName() + "' has correctly been disabled");
			// if the mod was really enabled (raf data file changed)
			if (mod.isEnabled()) {
				// set his state to ready to be disabled
				mod.setState("10");
			}
			// otherwise we can consider the mod disabled now (no need to modif the raf data file)
			else {
				mod.setState("0");
			}
		}
	}
	
	public boolean setFileState(File file, boolean state) {

		// get the corresponding entries for each file
		List<RafFileEntry> options = getEntriesForFile(file);
		
		// if no options, nothing to change
		if (options.size() == 0) {
			return false;
		}
		
		int errorCount = 0;
		
		// for each option
		for (RafFileEntry entry : options) {
			// path of the new file
			String newFile = file.getAbsolutePath();
			// if we remove a previously installed file, replace newFile with backup file name
			if (!state) {
				Log.getInst().info("Rolling back '" + entry.getFullFilename() + "' for file '" + file.getAbsolutePath() + "'");
				// get filename
				String backupFile = IniManager.getInst().getConfigIni().getSection("backup", true).get(entry.getFullFilename().toLowerCase());
				// if file null, there is nothing to roll back
				if (backupFile == null) {
					continue;
				}
				
				// get backup filename
				backupFile = backupFile.split(":")[1];
				
				// change path of filename to path of the backup file
				newFile = CustomModManager.backupFolder + File.separator + backupFile;
			}				
			if (!entry.getManager().changeFile(entry, newFile, !state)) {
				// TODO: what to do if something goes wrong, rollback changes ?
				System.out.println("Error while changing entry " + entry.getFullFilename() + " for file " + newFile + "!");
				errorCount++;
			}
			else {
				// at least one file modified, we can set that to true
				managers.put(entry.getManager(), Boolean.TRUE);
			}
		}
		
		// if all changes failed, return false
		if (errorCount == options.size()) {
			return false;
		}
		
		return true;
	}
	
	// maybe I shouldnt have this ??
	public void restoreAll() {
		IniFile.IniSection backup = IniManager.getInst().getConfigIni().getSection("backup", true);
		for (String filePath : backup.keySet()) {
			// get file path and backup filename
			String[] data = backup.get(filePath).split(":");
			String backupFile = backupFolder + File.separator + data[1];
			// get corresponding manager and file entry
			RafFileEntry match = findEntryFromName(filePath);
			if (match != null) {
				if (!match.getManager().changeFile(match, backupFile, true)) {
					Log.getInst().warning("Could not restore file: " + filePath);
				}
				else {
					// at least one file modified, we can set that to true
					managers.put(match.getManager(), Boolean.TRUE);
				}
			}
		}
		
		// uninstall all custom mods TODO: check its correct
		for (CustomMod mod : mods) {
			if (mod.isEnabled()) {
				mod.setState("10");
			}
		}
	}
	
	// this will return the entires corresponding to the specifil filename (without path !)
	public List<RafFileEntry> getEntriesForFile(File file) {
		// get list of files with same name
		Map<RafManager, List<RafFileEntry>> results = getFiles(file.getName());
		
		// two list, good options and other options
		List<RafFileEntry> goodOptions = new ArrayList<RafFileEntry>();
		List<RafFileEntry> allOptions = new ArrayList<RafFileEntry>();
		
		// for each possible raf having at least one possible match
		for (RafManager manager : results.keySet()) {
			List<RafFileEntry> entries = results.get(manager);
			// for each possible match
			for (RafFileEntry entry : entries) {
				String fileAsString = file.getAbsolutePath();
				File entryAsFile = new File(entry.getFullFilename());
				// if their respective folders are the same, probably a good match
				if (file.getParentFile().getName().equalsIgnoreCase(entryAsFile.getParentFile().getName())) {
					goodOptions.add(entry);
				}
				// if both have particles in their path
				else if (entry.getFullFilename().toLowerCase().contains("particles") && fileAsString.toLowerCase().contains("particles")) {
					goodOptions.add(entry);
				}
				// if both have character in their path
				else if (entry.getFullFilename().toLowerCase().contains("characters") && fileAsString.toLowerCase().contains("characters")) {
					goodOptions.add(entry);
				}
				// no particular match, add it here
				else {
					allOptions.add(entry);
				}
			}
		}
		return (goodOptions.size() > 0) ? goodOptions : allOptions;
	}
	
	// task used to pack the rafs
	public class SaveTask extends SwingWorker<Void, Void> {
				
        public Void doInBackground() {
            //Initialize progress property
            setProgress(5);
            
            Set<Entry<RafManager, Boolean>> entrySet = managers.entrySet();
            int max = entrySet.size(), i = 1;
    		for (Entry<RafManager, Boolean> entry : entrySet) {
    			// if this raf data has been changed, need to save it
    			if (entry.getValue()) {
    				if (entry.getKey().packRaf()) {
    					// after all changes have been made, set boolean to false
    					managers.put(entry.getKey(), Boolean.FALSE);
    				}
    			}
    			System.out.println("" + ((100 * i) / max));
    			setProgress((100 * i) / max);
    			
    			i++;
    		}
            
            return null;
        }

        /*
         * Executed in event dispatching thread
         */
        @Override
        public void done() {
        	System.out.println("save done!");
        	
        	System.out.println("updating mods states...");
        	for (CustomMod mod : mods) {
        		if (mod.getState().equals("01")) {
        			mod.setState("1");
        		}
        		else if (mod.getState().equals("10")) {
        			mod.setState("0");
        		}
        	}
        	saveCustomMods();
        	
        }
    }

}
