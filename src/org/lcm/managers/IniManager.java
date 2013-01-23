package org.lcm.managers;
import java.util.HashMap;

import org.lcm.utils.IniFile;



public class IniManager {

	private static IniManager instance;
    private HashMap<String, IniFile> map;

	public static final String configfile = "lcm.cfg";
	public static final String modsfile = "mods.ini";
	
	public static IniManager getInst() {
        if (instance == null) {
            instance = new IniManager();
        }
        return instance;
	}
	
	public IniFile getConfigIni() {
		return getIni(configfile);
	}
	
	public IniFile getModsIni() {
		return getIni(modsfile);
	}
	
	public IniFile getIni(String iniFile) {
		IniFile ini = map.get(iniFile);
		if (ini == null) {
			ini = new IniFile(iniFile);
			map.put(iniFile, ini);
		}
		return ini;
	}
	
	private IniManager() {
    	map = new HashMap<String, IniFile>();
	}
	
}
