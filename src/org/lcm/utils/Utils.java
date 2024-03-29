package org.lcm.utils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class Utils {

	public static void deleteFolder(File folder) {
	    File[] files = folder.listFiles();
	    if(files != null) {
	        for (File f: files) {
	            if (f.isDirectory()) {
	                deleteFolder(f);
	            } else {
	                f.delete();
	            }
	        }
	    }
	    folder.delete();
	}
	
	public static void copyFolder(File src, File dest) throws IOException {
 
		if(src.isDirectory()) {
			//if directory not exists, create it
    		if(!dest.exists()) {
    		   dest.mkdirs();
    		}
 
    		//list all the directory contents
    		String files[] = src.list();
    		for (String file : files) {
    		   //construct the src and dest file structure
    		   File srcFile = new File(src, file);
    		   File destFile = new File(dest, file);
    		   //recursive copy
    		   copyFolder(srcFile,destFile);
    		}
    	} 
    	else {
    		//if file, then copy it
    		//Use bytes stream to support all file types
    		InputStream in = new FileInputStream(src);
    		OutputStream out = new FileOutputStream(dest); 
 
    		byte[] buffer = new byte[8192];
    		int length;
    		//copy the file content in bytes 
    		while ((length = in.read(buffer)) > 0){
    			out.write(buffer, 0, length);
    		}
 
    		in.close();
    		out.close();
    		System.out.println("File copied from " + src + " to " + dest);
    	}
    }
	
}
