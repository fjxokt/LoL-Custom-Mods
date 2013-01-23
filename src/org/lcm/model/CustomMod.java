package org.lcm.model;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.lcm.utils.Log;


public class CustomMod {

	private String folder;
	private String name;
	// 0 = not installed, 01 = ready to install, 10 = ready to uninstall, 1 = installed
	private String state;
	// the mod files
	private List<File> filesList;
	
	public enum ModIntegrity {
		ENABLED, PARTLY_ENABLED, DISABLED, GHOST;
	}
	
	public CustomMod(String folder, String name, String state, boolean fetchFiles) {
		this.folder = folder;
		this.name = name;
		this.state = state;
		
		filesList = new ArrayList<File>();
		File fold = new File(folder);
		if (!fold.exists() || !fold.isDirectory()) {
			Log.getInst().severe("'" + fold.getAbsolutePath() + "' doesnt exist or is not a directory");
			return;
		}
		if (fetchFiles) {
			parseFolder(fold);
		}
	}
	
	// fetch all files (ignore txt files)
	private void parseFolder(File folder) {
		for (File f : folder.listFiles()) {
			if (f.isDirectory()) {
				parseFolder(f);
			}
			else {
				if (!f.getName().endsWith(".txt")) {
					filesList.add(f);
				}
			}
		}
	}
	
	// return -1 if no files still exist
	// return 0 if all files exist
	// return nb of missing files
	public int checkFilesIntegrity() {
		int res = 0;
		for (File f : filesList) {
			if (!f.exists()) {
				res++;
			}
		}
		return res == filesList.size() ? -1 : res;
	}

	public String getFolder() {
		return folder;
	}

	public void setFolder(String folder) {
		this.folder = folder;
	}
	
	public List<File> getFilesList() {
		return filesList;
	}
	
	public void addFile(File file) {
		filesList.add(file);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
	public void setState(String state) {
		this.state = state;
	}
	
	public String getState() {
		return state;
	}
	
	public boolean isEnabled() {
		return state.equals("1");	
	}
	
	public boolean isDisabled() {
		return state.equals("0");	
	}
	
	public String toString() {
		String pref = "";
		if (state.equals("1")) {
			pref = "✓ ";
		}
		else if (state.equals("01")) {
			pref = "a ";
		}
		else if (state.equals("10")) {
			pref = "d ";
		}
		else if (state.equals("0")) {
			pref = "\u00A0\u00A0\u00A0";
		}
		return pref + name;
	}
	
}
