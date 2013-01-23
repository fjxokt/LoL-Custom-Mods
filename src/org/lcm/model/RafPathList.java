package org.lcm.model;


import java.nio.ByteBuffer;
import java.util.ArrayList;

public class RafPathList extends ArrayList<RafPathListEntry>{
	private static final long serialVersionUID = 1L;
	int pathListSize;
	public RafPathList(ByteBuffer data) {
		super();
		pathListSize = data.getInt();
		int nbEntries = data.getInt();
		for (int i=0; i<nbEntries; i++) {
			RafPathListEntry entry = new RafPathListEntry(data);
			add(entry);
		}
	}
	public int getPathListSize() {
		return pathListSize;
	}
	public String toString() {
		return "RafPathList = " + size();
	}
}