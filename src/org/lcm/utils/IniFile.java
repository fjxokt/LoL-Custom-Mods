package org.lcm.utils;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;



public class IniFile {
	
	public class IniSection extends HashMap<String,String> {
		private static final long serialVersionUID = 1L;
		private String sectionName;
		public IniSection(String section) {
			super();
			sectionName = section;
		}
		public String getName() {
			return sectionName;
		}
		public void setName(String newName) {
			sectionName = newName;
		}
		public String toString() {
			return "[" + sectionName + "] " + super.toString();
		}
	}
	
	// attribute
	private List<IniSection> data;
	private String filename = null;
	
	public IniFile()
	{
		data = new ArrayList<IniSection>();
	}
	
	public IniFile(File file) {
		filename = file.getAbsolutePath();
		data = new ArrayList<IniSection>();
		try {
			FileInputStream stream = new FileInputStream(file);
			BufferedInputStream bstream = new BufferedInputStream(stream);
			DataInputStream in = new DataInputStream(bstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			load(br);
			in.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public IniFile(String file) {
		filename = file;
		data = new ArrayList<IniSection>();
		File src = new File(file);
		if (!src.exists()) return;
		try {
			FileInputStream stream = new FileInputStream(file);
			BufferedInputStream bstream = new BufferedInputStream(stream);
			DataInputStream in = new DataInputStream(bstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			load(br);
			in.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public IniFile(BufferedReader br)
	{
		data = new ArrayList<IniSection>();
		load(br);
	}
	
	private void load(BufferedReader br) {
		// if no file exists, initialization is over
		try {
			String strLine;
			IniSection cur = null;
			while ((strLine = br.readLine()) != null) {
				String trimStr = strLine.trim();
				// empty line or comment line
				if (trimStr.isEmpty() || trimStr.startsWith(";")) {
					continue;
				}
				// section found
				else if (trimStr.matches("^\\[.+\\]$")) {
					if (cur != null)
						data.add(cur);
					cur = new IniSection(trimStr.substring(1, trimStr.length()-1));
				}
				// key/val found
				else {
					int pos = strLine.indexOf('=');
					if (pos == -1) {
						Log.getInst().warning(strLine + " malformated, skipping");
						continue;
					}
					if (cur == null) {
						Log.getInst().warning("no section found");
						continue;
					}
					cur.put(strLine.substring(0, pos), strLine.substring(pos+1));
				}
			}
			if (cur != null)
				data.add(cur);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public IniSection getSection(String section) {
		return getSection(section, false);
	}
	
	public IniSection getSection(String section, boolean createIfNull)
	{
		for (IniSection in : data) {
			if (in.getName().equals(section))
				return in;
		}
		return (createIfNull) ? createSection(section) : null;
	}
	
	public void renameSection(String section, String newName) {
		IniSection se = getSection(section);
		if (se != null) {
			se.setName(newName);
		}
	}
	
	public IniSection getSection(int i) {
		return data.get(i);
	}

	public int size() {
		return data.size();
	}
	
	public void clear() {
		data.clear();
	}
	
	public IniSection createSection(String section) {
		if (getSection(section) == null) {
			IniSection s = new IniSection(section);
			data.add(s);
			return s;
		}
		return null;
	}
	
	public void addSection(IniSection s) {
		data.add(s);
	}
	
	public void removeSection(String section) {
		IniSection sect = getSection(section);
		if (sect != null) {
			data.remove(sect);
		}
	}	
	
	public void emptySection(String section) {
		IniSection sect = getSection(section);
		if (sect != null) {
			sect.clear();
		}
	}
	
	public void addValue(String section, String key, String value)
	{
		IniSection sect = getSection(section);
		if (sect != null) {
			sect.put(key, value);
		}
	}
	
	public String getValue(String section, String key)
	{
		IniSection sect = getSection(section);
		if (sect != null) {
			return sect.get(key);
		}
		return null;
	}
	
	public void removeValue(String section, String key) {
		IniSection sect = getSection(section);
		if (sect != null) {
			sect.remove(key);
		}
	}
	
	public void save() {
		if (filename != null) {
			save(filename);
		}
	}
	
	public void save(String file) {
		save(new File(file));
	}
	
	public void save(File file) {
		try {
			// Create file 
			FileWriter fstream = new FileWriter(file);
			BufferedWriter out = new BufferedWriter(fstream);
			
			for (int i=0; i<size(); i++) {
				IniSection cur = getSection(i);
				out.write("[" + cur.getName() + "]");
				out.newLine();
				Iterator<Map.Entry<String,String>> iter = cur.entrySet().iterator();
				while (iter.hasNext()) {
					Map.Entry<String,String> mEntry = (Map.Entry<String,String>)iter.next();
					out.write(mEntry.getKey() + "=" + mEntry.getValue());
					out.newLine();
				}
			}
			out.close();
			Log.getInst().info("File \"" + file.getAbsolutePath() + "\" saved correctly");
		} catch (Exception e) {
			Log.getInst().severe("save error: " + e.getMessage());
		}	
	}
	
}
