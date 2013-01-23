package org.lcm.managers;


import java.util.HashMap;

import org.lcm.model.RafFileDataObject;


public class RafFileDataObjectManager {
	
    private static RafFileDataObjectManager instance;
    private HashMap<String, RafFileDataObject> map;
    
    public static RafFileDataObjectManager getInst() {
        if (instance == null) {
            instance = new RafFileDataObjectManager();
        }
        return instance;
    }
    
    public RafFileDataObject getRafFileDataObject(String filename) {
    	RafFileDataObject res = map.get(filename);
    	if (res == null) {
    		res = new RafFileDataObject(filename);
    		map.put(filename, res);
    	}
    	// open file if it has been closed
    	if (res.isFileClosed()) {
    		res.openFile();
    	}
    	return res;
    }
    
    private RafFileDataObjectManager() {
    	map = new HashMap<String, RafFileDataObject>();
    }
}
