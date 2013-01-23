package org.lcm.model;


import java.nio.ByteBuffer;

import org.lcm.managers.RafManager;

public class RafFileEntry {
	int id;
	int pathHash;
	int dataOffset;
	int dataSize;
	int pathListIndex;
	byte[] zipData;
	int newOffset = -1;
	int newSize = -1;
	private String fullFileName;
	private String fileName;
	boolean isOriginal;
	// what manager manages this file
	RafManager manager;
	
	public RafFileEntry(ByteBuffer data, int id, RafManager manager) {
		this.id = id;
		this.manager = manager;
		pathHash = data.getInt();
		dataOffset = data.getInt();
		dataSize = data.getInt();
		pathListIndex = data.getInt();
		isOriginal = false;
	}
	
	public void setFullFilename(String fn) {
		fullFileName = fn;
		String[] str = fn.split("/");
		fileName = str[str.length-1];
	}
	
	public String getFullFilename() {
		return fullFileName;
	}
	
	public String getFilename() {
		return fileName;
	}
	
	public boolean hasZipData() {
		return zipData != null;
	}
	
	public byte[] getZipData() {
		return zipData;
	}
	
	public void setZipData(byte[] data) {
		zipData = data;
		newSize = data.length;
	}
	
	public int getDataSize() {
		return dataSize;
	}
	
	public boolean isOriginal() {
		return isOriginal;
	}
	
	public void setOriginal(boolean state) {
		isOriginal = state;
	}
	
	public int getId() {
		return id;
	}
	
	public RafManager getManager() {
		return manager;
	}
	
	// method to call when file entry has been modified and saved
	public void apply() {
		if (newOffset != -1) {
			dataOffset = newOffset;
		}
		if (zipData != null) {
			zipData = null;
			if (newSize != -1) dataSize = newSize;
		}
		newOffset = newSize = -1;
	}
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("RafFileEntry [pathHash=");
		builder.append(pathHash);
		builder.append(", dataOffset=");
		builder.append(dataOffset);
		builder.append(", dataSize=");
		builder.append(dataSize);
		builder.append(", pathListIndex=");
		builder.append(pathListIndex);
		builder.append("]");
		return builder.toString();
	}
}