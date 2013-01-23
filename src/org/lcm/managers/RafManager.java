package org.lcm.managers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import org.lcm.main.RafManagerWin;
import org.lcm.model.RafFile;
import org.lcm.model.RafFileDataObject;
import org.lcm.model.RafFileEntry;
import org.lcm.model.RafFileList;
import org.lcm.utils.IniFile;
import org.lcm.utils.Log;
import org.lcm.utils.Pair;
import org.lcm.utils.SimpleSHA1;


public class RafManager {

	private String filename;
	private RafFile raf;
	// filename, list of entries that have the same filename
	private Map<String, List<Integer>> pathsMap = new HashMap<String, List<Integer>>();
	//  id of file entry, new filename path, boolean telling if file has been saved to raf/raf.data
	private Map<Integer, Pair<String, Boolean>> modifiedFilesMap = new HashMap<Integer, Pair<String,Boolean>>();
	// extension of zipped files
	private Map<String, Boolean> zipFilesExt = new HashMap<String, Boolean>() {
		private static final long serialVersionUID = 1L;
		{
			put("gfx", Boolean.TRUE);
			put("fev", Boolean.TRUE);
			put("fsb", Boolean.TRUE);
		}
	};

	public RafManager(String filename) {
		File file = new File(filename);
		// TODO: check for correct extension file
		if (!file.exists() || !filename.endsWith(".raf")) {
			Log.getInst().severe("'" + filename + "' doesn't exists or is not a valid .raf file");
			return;
		}
		
		this.filename = filename;
		
		byte[] data = readFile(file);

		ByteBuffer bb = ByteBuffer.wrap(data);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		// get our raf object
		raf = new RafFile(bb, this);
		
		Log.getInst().info("Correctly loaded " + filename + " (" + raf.getFileCount() + " files, " + raf.getOriginalRafDataFileSize() + " bytes)");

		// create map with key = filename and val = list of entry ids matching filename key
		Map<String, List<Integer>> dupFiles = new HashMap<String, List<Integer>>();
		for (int i = 0; i < raf.getFileCount(); i++) {
			RafFileEntry entry = raf.getFile(i);
			String key = entry.getFilename().toLowerCase();
			List<Integer> list = pathsMap.get(key);
			if (list == null) {
				list = new ArrayList<Integer>();
				pathsMap.put(key, list);
			}
			list.add(i);
			if (list.size() > 1) {
				dupFiles.put(key, list);
			}
		}
	}

	public RafFileEntry getFileEntry(int n) {
		return raf.getFile(n);
	}

	public int getFileCount() {
		return raf.getFileCount();
	}
	
	public RafFileList getPathOrdererFileList() {
		return raf.getOrderedByPathFileListCopy();
	}

	public String getRafFullFilename() {
		return filename;
	}
	
	public String getRafFilename() {
		return new File(filename).getName();
	}

	public void dumpRafDataFile(String destFolder) {
		File folder = new File(destFolder);
		if (!folder.exists() || !folder.isDirectory()) {
			Log.getInst().severe("'" + destFolder + "' doesn't exists or is not a folder");
			return;
		}
		
		int cpt = 0;
		for (RafFileEntry entry : raf.getFileList()) {
			cpt++;
			System.out.println("getting file " + cpt + " / " + raf.getFileList().size());
			// create complete path to file
			String path = entry.getFullFilename();
			path = path.substring(0, path.lastIndexOf("/"));
			path = folder.getAbsolutePath() + File.separator + path.replaceAll("/", File.separator);
			// if path created, get the file
			File ffold = new File(path);
			if (!ffold.exists()) {
				if (!ffold.mkdirs()) {
					Log.getInst().severe("Could not create path '" + path + "', skipping file '" + entry.getFullFilename());
					continue;
				}
			}
			getFileFromRaf(entry, path);
		}
	}
	
	// find the raf entry that match the exact filename
	// filename = 'DATA/Particles/Hecarim_WeaponTrail_Q.dds' for instance
	public RafFileEntry findFileEntry(String filename) {
		// getting filename
		String filen = filename.substring(filename.lastIndexOf('/') + 1, filename.length());
		// searching
		List<RafFileEntry> res = findFileEntries(filen);
		// look for the correct one
		for (RafFileEntry entry : res) {
			if (entry.getFullFilename().toLowerCase().equals(filename)) {
				return entry;
			}
		}
		return null;
	}
	
	// find raf entries matching specified string
	public List<RafFileEntry> findFileEntries(String match) {
		List<RafFileEntry> res = new ArrayList<RafFileEntry>();
		for (String key : pathsMap.keySet()) {
			if (key.contains(match)) {
				List<Integer> ids = pathsMap.get(key);
				for (Integer id : ids) {
					res.add(raf.getFile(id));
				}
			}
		}
		return res;
	}
	
	// return list of raf entries that have the same exact filename 'filename' (only same filename, no path included)
	public List<RafFileEntry> getFileEntries(String filename) {
		List<RafFileEntry> res = new ArrayList<RafFileEntry>();
		// get filename as key
		String[] str = filename.split("\\" + File.separator);
		String key = str[str.length - 1].toLowerCase().trim();
		// get file entry id from key
		List<Integer> list = pathsMap.get(key);
		if (list == null || list.size() == 0) {
			return res;
		}
		for (Integer id : list) {
			res.add(raf.getFile(id));
		}
		return res;
	}

	// get file corresponding to raf entry 'entry' and save it in specified folder
	public boolean getFileFromRaf(RafFileEntry entry, String folder) {
		return getFileFromRaf(entry, folder, entry.getFilename());
	}

	public boolean getFileFromRaf(RafFileEntry entry, String folder, String newName) {
		// get unzip data
		byte[] decData = getFileDataFromRaf(entry);
		// save file to folder location
		if (!saveFile(decData, folder, newName)) {
			return false;
		}
		return true;
	}
	
	public byte[] getFileDataFromRaf(RafFileEntry entry) {
		return getFileDataFromRaf(entry, false);
	}
	
	public byte[] getFileDataFromRaf(RafFileEntry entry, boolean keepCompressed) {
		// get zip data
		RafFileDataObject rafData = RafFileDataObjectManager.getInst().getRafFileDataObject(getRafFullFilename() + ".dat");
		byte[] fileData = rafData.getFile(entry);
		
		if (keepCompressed) {
			return fileData;
		}

		// get unzip data
		byte[] decData = null;
		try {
			decData = decompressData(new ByteArrayInputStream(fileData));
		} catch (IOException e1) {
			Log.getInst().warning("Cound't decompress '" + entry.getFilename() + "', probably because it hasn't been compressed");
			decData = fileData;
		}
		
		return decData;
	}

	// change the file related to filenameKey
	// newFile = '/home/fjxokt/test.dds' for instance
	public boolean changeFile(RafFileEntry entry, String newFile, boolean isOriginal) {
		File newF = new File(newFile);
		if (!newF.exists()) {
			Log.getInst().severe("'" + newFile + "' doesn't exists");
			return false;
		}
		byte[] data = readFile(newF);
		byte[] zipData = null;
		
		// we zip the file if it should be
		String ext = entry.getFilename().substring(entry.getFilename().lastIndexOf(".") + 1, entry.getFilename().length()-1);
		if (zipFilesExt.get(ext) == null) {
			ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
			try {
				compressData(data, out);
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
			zipData = out.toByteArray();
		}
		else {
			zipData = data;
		}
		
		// set if the entry has been changed for the original file
		entry.setOriginal(isOriginal);

		// set new file data
		entry.setZipData(zipData);

		System.out.println(getRafFilename() + ": file " + entry.getFullFilename() + " changed to " + newFile);
		
		// add the modified entry to our map
		modifiedFilesMap.put(entry.getId(), new Pair<String, Boolean>(newFile, Boolean.FALSE));
		
		return true;
	}

	// save both .raf and .raf.dat files and updates corresponding objects
	public boolean packRaf() {

		// update the raf file entries data (offset and size fields)
		raf.updateEntriesData();
		
		// now we need to mofidy the .raf file
		byte[] newRaf = raf.updateRafFile();
		
		// save it
		File newRafFile = new File(this.getRafFullFilename() + ".custom");
		if (!saveFile(newRaf, "", newRafFile.getAbsolutePath())) {
			return false;
		}

		// backup original files being changed
		saveBackupFiles();

		// and save the raf.data file
		File rafDataFile = new File(this.getRafFullFilename() + ".dat");
		RafFileDataObject rafData = RafFileDataObjectManager.getInst().getRafFileDataObject(rafDataFile.getAbsolutePath());
		if (!rafData.updateRafFiles(raf, this.getRafFullFilename() + ".dat.custom")) {
			return false;
		}
		
		// close our big file
		rafData.closeFile();
		
		// delete restaured files from backup data
		clearBackupFiles();
		
		// TODO: rename files to correct ones
		
		// raf
		File rafFile = new File(this.getRafFullFilename());
		//rafFile.delete();
		File rafBackup = new File(this.getRafFullFilename() + ".backup");
		if (!rafBackup.exists()) {
			rafFile.renameTo(rafBackup);
		}
		newRafFile.renameTo(rafFile);
		
		// raf data
		File newRafDataFile = new File(this.getRafFullFilename() + ".dat.custom");
		//rafDataFile.delete();
		File rafDataBackup = new File(this.getRafFullFilename() + ".dat.backup");
		if (!rafDataBackup.exists()) {
			rafDataFile.renameTo(rafDataBackup);
		}
		newRafDataFile.renameTo(rafDataFile);
		
		// change status: TODO: useless so far
		for (Integer entryId : modifiedFilesMap.keySet()) {
			Pair<String, Boolean> pair = modifiedFilesMap.get(entryId);
			pair.setValue(Boolean.TRUE);
		}
		
		// save what happened for each file saved
		
		// apply all changes on entries
		for (RafFileEntry entry : raf.getFileList()) {
			entry.apply();
		}
		
		RafManagerWin.DebugOutput.println("Files " + this.getRafFullFilename() + " and .raf.dat correctly updated");
		
		// everything went ok
		return true;
	}
	
	// save original version of all files that will be changed when packing raf
	private void saveBackupFiles() {
		// get our backup data file
		IniFile.IniSection backup = IniManager.getInst().getConfigIni().getSection("backup", true);
		// make backup of original files
		for (RafFileEntry entry : raf.getFileList()) {
			// modified file
			if (entry.hasZipData() && !entry.isOriginal()) {
				// if the file is not in the backup file, save it
				if (!backup.containsKey(entry.getFullFilename().toLowerCase())) {
					// get original file size (compressed)
					byte[] data = getFileDataFromRaf(entry, true);
					// get SHA-1 hash of original file
					String sha = SimpleSHA1.SHA1(data);
					// save backup file
					System.out.println("backup file " + entry.getFilename() + " into " + CustomModManager.backupFolder);
					getFileFromRaf(entry, CustomModManager.backupFolder, sha);
					// add file to ini, values are original file size, original file name, modified file size and count
					backup.put(entry.getFullFilename().toLowerCase(), "" + data.length + ":" + sha + ":" + entry.getZipData().length + ":1");
				}
				// if we already have a backup of this file, inc counter
				else {
					String[] data = backup.get(entry.getFullFilename().toLowerCase()).split(":");
					// inc the counter
					Integer count = Integer.parseInt(data[3]) + 1;
					backup.put(entry.getFullFilename().toLowerCase(), data[0] + ":" + data[1] + ":" + data[2] + ":" + count);
				}
			}
		}
		// save changes on ini file
		IniManager.getInst().getConfigIni().save();
	}
	
	// to call after the raf files have been written
	private void clearBackupFiles() {
		// get our backup data file
		IniFile.IniSection backup = IniManager.getInst().getConfigIni().getSection("backup", true);
		// make backup of original files
		for (RafFileEntry entry : raf.getFileList()) {
			// modified file
			if (entry.hasZipData() && entry.isOriginal()) {
				System.out.println("file " + entry.getFilename() + " restaured, decreasing backup file counter");
				// get counter
				String[] data = backup.get(entry.getFullFilename().toLowerCase()).split(":");
				Integer count = Integer.parseInt(data[3]);
				// if count = 1, remove entry and delete backup file
				if (count == 1) {
					// remove file from backup, as it has been restaured
					backup.remove(entry.getFullFilename().toLowerCase());
					// and delete file
					new File(CustomModManager.backupFolder + File.separator + data[1]).delete();
				}
				// otherwise just dec counter
				else {
					count--;
					backup.put(entry.getFullFilename().toLowerCase(), data[0] + ":" + data[1] + ":" + data[2] + ":" + count);
				}
			}
		}
		// save changes on ini file
		IniManager.getInst().getConfigIni().save();
	}
	
	// says if the file in raf is the original one
	// filename = 'DATA/Particles/Hecarim_WeaponTrail_Q.dds' for instance
	// careful! if the file has been modified by riot this would also return false
	// TODO: not used yet
	public Boolean isFileOriginal(String filename) {
		// get our backup data file
		IniFile.IniSection backup = IniManager.getInst().getConfigIni().getSection("backup", true);
		// check if file has been backuped
		String data = backup.get(filename);
		// file hasnt been backuped, just return null
		if (data == null) {
			System.out.println("No backupfile, null returned");
			return null;
		}
		// get the raf file entry
		RafFileEntry entry = findFileEntry(filename);
		if (entry == null) {
			return null;
		}
		String[] s = data.split(":");
		// get original file size
		int size = Integer.parseInt(s[0]);
		
		// if same size, we consider that the file are the same
		// i could replace this with a SHA-1 verif, but its heavier
		System.out.println("taille file: " + entry.getFilename() + " : " + entry.getDataSize());
		if (size == entry.getDataSize()) {
			return Boolean.TRUE;
		}
		
		return Boolean.FALSE;
	}
	
	///////////////////////////
	//
	// Utils
	//
	//////////////////////////

	// not used yet, as we never have to add new files to raf
	public int hashString(String str) {
		int hash = 0;
		int temp;
		for (int i = 0; i < str.length(); i++) {
			hash = (hash << 4) + str.charAt(i);
			temp = hash & 0xf0000000;
			if (temp != 0) {
				hash = hash ^ (temp >> 24);
				hash = hash ^ temp;
			}
		}
		return hash;
	}
	
	// read a file and return a byte array
	private byte[] readFile(File f) {
		byte[] data = new byte[(int) f.length()];
		try {
			FileInputStream in = new FileInputStream(f);

			int offset = 0, numRead = 0;
			while ((offset < data.length) && ((numRead = in.read(data, offset, data.length - offset)) >= 0)) {
				offset += numRead;
			}
			if (offset < data.length) {
				Log.getInst().severe("Could not completely read file '" + f.getName() + "'");
				return null;
			}
			in.close();
		} catch (Exception e) {
			e.printStackTrace();
			Log.getInst().severe("Could not read file '" + f.getName() + "'");
			return null;
		}
		return data;
	}
	
	private boolean saveFile(byte[] data, String folder, String filename) {
		// create folder if not existing
		if (!folder.isEmpty()) {
			File fold = new File(folder);
			if (!fold.exists()) {
				fold.mkdirs();
			}
		}
		File newFile = new File(folder + File.separator + filename);
		try {
			// TODO: check if no problem under windows (with this strange last char of the filename)
			FileOutputStream fos = new FileOutputStream(newFile);
			fos.write(data);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		Log.getInst().info("'" + newFile.getAbsolutePath() + "' has been correctly saved");
		return true;
	}

	public byte[] decompressData(InputStream input) throws IOException {
		InflaterInputStream in = new InflaterInputStream(input);
		ByteArrayOutputStream bout = new ByteArrayOutputStream(8192);
		int b;
		while ((b = in.read()) != -1) {
			bout.write(b);
		}
		in.close();
		bout.close();
		return bout.toByteArray();
	}

	public void compressData(byte[] data, OutputStream out) throws IOException {
		Deflater d = new Deflater();
		DeflaterOutputStream dout = new DeflaterOutputStream(out, d);
		dout.write(data);
		dout.close();
	}

	public String toString() {
		return filename;
	}
}
