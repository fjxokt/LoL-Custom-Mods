package org.lcm.model;


import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.lcm.managers.RafManager;

public class RafFileList extends ArrayList<RafFileEntry> {
	private static final long serialVersionUID = 1L;
	public RafFileList(ByteBuffer data, RafManager manager) {
		super();
		int nbEntries = data.getInt();
		for (int i=0; i<nbEntries; i++) {
			RafFileEntry entry = new RafFileEntry(data, i, manager);
			add(entry);
		}
	}
	public RafFileList(RafFileList copy) {
		super(copy);
	}
	public String toString() {
		return "RafFileList [nbEntries=" + this.size() + "]";
	}
}