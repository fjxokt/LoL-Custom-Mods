package org.lcm.model;


import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

import org.lcm.utils.Log;


public class RafFileDataObject {
		
	private String filename;
	private RandomAccessFile file;
	private boolean isClosed = true;
	
	public RafFileDataObject(String filename) {
		this.filename = filename;	
		openFile();
	}
	
	public void openFile() {
		try {
			file = new RandomAccessFile(filename, "r");
			isClosed = false;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	public byte[] getFile(RafFileEntry entry) {
		byte[] result = null;
		try {
			file.seek(entry.dataOffset);
		    result = new byte[entry.dataSize];
		    
			int offset = 0, numRead = 0;
			// copy the file into the byte array
			while ((offset < result.length) && ((numRead = file.read(result, offset, result.length - offset)) >= 0)) {
				offset += numRead;
			}
			if (offset < result.length) {
				Log.getInst().severe("Could not completely read file entry '" + entry.getFullFilename() + "'");
				return null;
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		return result;
	}
	
	public boolean isFileClosed() {
		return isClosed;
	}
	
	// write the content of files from index that don't have a new data set
	// return nb of files written at once
	public int writeFiles(RafFileList list, int startIndex, FileChannel source, FileChannel dest) {
		int seekStart = -1;
		int nbFilesRead = 1;
		int length = 0;
		for (int i=startIndex; i<list.size(); i++) {
			RafFileEntry entry = list.get(i);
			// the first file will be the starting point
			if (seekStart == -1) {
				seekStart = entry.dataOffset;
				length = entry.dataSize;
			}
			else {
				// if the next file don't have custom data, he will be read at the same time as the previous one
				if (entry.zipData == null) {
					length += entry.dataSize;
					nbFilesRead++;
				}
				// otherwise its over, can't get more files at once
				else {
					break;
				}
			}
		}
		
		// now copy from pos of first entry matching
		try {
			source.transferTo(seekStart, length, dest);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return nbFilesRead;
	}
	
	public boolean updateRafFiles(RafFile raf, String rafDataName) {
		int newSize = raf.getChangedRafDataFileSize();
		// create new empty raf file
		System.out.println("creating file " + filename + " of size " + newSize);
		long startTime = System.currentTimeMillis();
		try {
			// create our new raf data file
			RandomAccessFile f = new RandomAccessFile(rafDataName, "rw");
			f.setLength(newSize);
			
			// source file channel
			FileInputStream source = new FileInputStream(filename);			 
	        FileChannel sourceFileChannel = source.getChannel();
			
			RafFileList list = raf.getOrderedFileListCopy();
			boolean isStarted = false;
			int seek = 0;
			System.out.println(list.size() + " entries to be written...");
			for (int i=0; i<list.size(); i++) {
				RafFileEntry entry = list.get(i);
				if (entry.zipData == null && !isStarted) {
					seek += entry.dataSize;
					continue;
				}
				// first file with modified size
				if (entry.zipData != null && !isStarted) {
					isStarted = true;
					// we will have to copy everything until this pos to the new file
					// from 0 to seek
//					System.out.println("seek to pos " + seek);
			        sourceFileChannel.transferTo(0, seek, f.getChannel());
			        System.out.println("copied " + i + " files before first change");
				}
				if (isStarted) {
					// we already have this new data, just write it to the file
					if (entry.zipData != null) {
						System.out.println(entry.getFullFilename() + " : " + i + " is object with new data");
						// maybe not needed ?
						//f.seek(seek);
						f.write(entry.zipData);
						seek += entry.newSize;
					}
					// we don't have the data, we have to read if from the file and write it
					else {
//						Integer seekI = new Integer(0);
						int nbRead = writeFiles(list, i, sourceFileChannel, f.getChannel());
						//seek += seekI;
						// -1 because i is the current entry, so if nbRead = 1; means we just have read current i
						// and then i should get += 0
						i += nbRead-1;
						System.out.println("wrote " + nbRead + " files at once");
						/*
						// old method to copy files
						byte[] data = getFile(entry);
						//System.out.println(i + " got file, time to write it");
						f.seek(seek);
						f.write(data);
						seek += data.length;
						*/
					}
				}
			}

			source.close();
			f.close();
			
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		System.out.println("Saving done in " + (System.currentTimeMillis() - startTime) + " ms");
		return true;
	}
	
	public void closeFile() {
		try {
			file.close();
			isClosed = true;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}