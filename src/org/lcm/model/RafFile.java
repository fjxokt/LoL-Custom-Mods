package org.lcm.model;



import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Comparator;

import org.lcm.managers.RafManager;
import org.lcm.utils.Log;

public class RafFile {
	private int magicNumber;
	private int version;
	private int managerIndex;
	private int fileListOffset;
	private int pathListOffset;
	private RafFileList filesList;
	private RafPathList pathsList;
	private ByteBuffer buffer;
	// TODO: ref to manager here ?
	// and for fileEntry ?
	
	public RafFile(ByteBuffer data, RafManager manager) {
		buffer = data;			
		magicNumber = data.getInt();
		version = data.getInt();
		managerIndex = data.getInt();
		fileListOffset = data.getInt();
		pathListOffset = data.getInt();
		
		data.position(fileListOffset);
		filesList = new RafFileList(data, manager);
		
		data.position(pathListOffset);
		pathsList = new RafPathList(data);
		
		// fill entries name
		for (RafFileEntry entry : filesList) {
			entry.setFullFilename(getFilePath(entry));
		}
	}
	
	public int getChangedRafDataFileSize() {
		int size = 0;
		for (RafFileEntry entry : filesList) {
			if (entry.newSize != -1) {
				size += entry.newSize;
			}
			else { 
				size += entry.dataSize;
			}
		}
		return size;
	}
	
	public int getOriginalRafDataFileSize() {
		int size = 0;
		for (RafFileEntry entry : filesList) {
			size += entry.dataSize;
		}
		return size;
	}
	
	public RafFileList getFileList() {
		return filesList;
	}
	
	public RafFileList getOrderedFileListCopy() {
		RafFileList orderedList = new RafFileList(filesList);
		Collections.sort(orderedList, new Comparator<RafFileEntry>() {
			public int compare(RafFileEntry o1, RafFileEntry o2) {
				if (o1.dataOffset == o2.dataOffset) return 0;
				return (o1.dataOffset - o2.dataOffset > 0) ? 1 : -1;
			}
		});
		return orderedList;
	}
	
	public RafFileList getOrderedByPathFileListCopy() {
		RafFileList orderedList = new RafFileList(filesList);
		Collections.sort(orderedList, new Comparator<RafFileEntry>() {
			public int compare(RafFileEntry o1, RafFileEntry o2) {
				return (o1.getFullFilename().compareTo(o2.getFullFilename()));
			}
		});
		return orderedList;
	}
	
	// update entries if any change has been performed on any of the raf files
	public void updateEntriesData() {
		int diff = 0;
		// order file entries by data offset
		RafFileList orderedList = getOrderedFileListCopy();
		// list is orderer by data offset
		for (int i = 0; i < orderedList.size(); i++) {
			RafFileEntry entry = orderedList.get(i);
			// if any previous entry has been modified, need to change their
			// data offset
			if (diff != 0) {
				// System.out.println("entry offset modified by " + diff);
				if (entry.newOffset == -1) {
					entry.newOffset = entry.dataOffset;
				}
				entry.newOffset += diff;
			}
			// for files without changes, nothing to do more
			if (entry.zipData == null) {
				continue;
			}
			// something change, get the size diff
			int sizeDiff = entry.zipData.length - entry.dataSize;
			// if files have different size, update it
			if (sizeDiff != 0) {
				diff += sizeDiff;
				// change size of current file entry
				entry.newSize = entry.zipData.length;
			}
		}
	}
	
	// update the .raf file buffer (buffer we used when loading .raf file)
	public byte[] updateRafFile() {
		// look for all modified files and update datacount
		int offsetCpt = 0;
		int sizeCpt = 0;
		for (int i=0; i<filesList.size(); i++) {
			RafFileEntry entry = filesList.get(i);
			if (entry.newOffset != -1) {
				// list offset + list size + (nbPrevEntries * entry size) + path hash
				buffer.putInt(fileListOffset + 4 + (i*4*4) + 4, entry.newOffset);
				offsetCpt++;
			}
			if (entry.zipData != null) {
				// list offset + list size + (nbPrevEntries * entry size) + path hash + data offset
				buffer.putInt(fileListOffset + 4 + (i*4*4) + 4 + 4, entry.newSize);
				sizeCpt++;
			}
		}
		Log.getInst().info(offsetCpt + " offsets and " + sizeCpt + " sizes changed");
		return buffer.array();
	}
	
	public int getFileCount() {
		return filesList.size();
	}
	
	public RafFileEntry getFile(int n) {
		return filesList.get(n);
	}
	
	private String getFilePath(RafFileEntry entry) {
		int pathIndex = entry.pathListIndex;
		return getPathListString(pathIndex);
	}
	
	private String getPathListString(int n) {
		RafPathListEntry entry = pathsList.get(n);
		buffer.position(pathListOffset + entry.pathOffset);
		String res = "";
		for (int i=0; i<entry.length-1; i++) {
			res += (char)buffer.get();
		}
		return res;
	}
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("RafFile [magicNumber=");
		builder.append(magicNumber);
		builder.append(", version=");
		builder.append(version);
		builder.append(", managerIndex=");
		builder.append(managerIndex);
		builder.append(", fileListOffset=");
		builder.append(fileListOffset);
		builder.append(", pathListOffset=");
		builder.append(pathListOffset);			
		builder.append(", filesList=");
		builder.append(filesList);
		builder.append("]");
		return builder.toString();
	}
}