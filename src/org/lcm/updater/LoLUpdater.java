package org.lcm.updater;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.text.html.HTMLEditorKit;

import org.lcm.main.Main;
import org.lcm.main.RafManagerWin;
import org.lcm.utils.Log;
 

public class LoLUpdater {
	
    final static String updateUrl = "http://lcb.spreadeas.com/update_raf.txt";
    final static String changelistUrl = "http://lcb.spreadeas.com/changelist_raf";
    final static String updaterName = "LoLUpdater.jar";
    final static String appTempName = "update_lolmm";
    final static String appName = "LoLModsManager.jar";
    
    public static void applyUpdate() {
    	 
    	File updater = new File(updaterName);
   	 	File newApp = new File(appTempName);
   	 	
   	 	// if the new version has been downloaded
   	 	if (newApp.exists()) {
   	 		Log.getInst().info("New version ready to be deployed");
   	 		try {
   	 			// if there is an updater, launch it
   	 			if (updater.exists()) {
   	 				Log.getInst().info("Closing program and launching updater");
   	 				// it will rename and launch the new version
   	 				restartApplication(null);
   	 			}
			} catch (IOException e) {
				Log.getInst().warning("Error : " + e.getMessage());
				e.printStackTrace();
			}
   	 	}
   	 	
	   	 // delete updater if present
	    if (updater.exists()) {
	    	Log.getInst().info("Deleting updater");
	    	updater.delete();
	    }
    }
    
    // return 1 if v1 > v2, -1 if v1 < v2, 0 if versions are equal
    public static int compareVersions(String v1, String v2) {
    	String[] nv = v1.split("\\.");
    	String[] ov = v2.split("\\.");
    	for (int i=0; i<nv.length; i++) {
    		int nit = Integer.parseInt(nv[i]), oit = Integer.parseInt(ov[i]);
    		if (nit > oit) {
    			return 1;
    		}
   		 	else if (nit < oit) {
   		 		return -1;
   		 	}
   	 	}
    	return 0;
    }

	 public static void checkUpdate(boolean silent) {    	
    	 URL site;
    	 String error = null;
    	 String v = null, updaterUrl = null, jarUrl = null;
    	 // reading the update file
    	 try {
 			site = new URL(updateUrl);
 	        URLConnection yc = site.openConnection();
 	        yc.setConnectTimeout(5000);
 	        yc.setReadTimeout(5000);
 	        BufferedReader in = new BufferedReader(new InputStreamReader(yc.getInputStream(), "UTF-8"));
 	        // program last version
 	        v = in.readLine();
 	        // url of the updater program
 	        updaterUrl = in.readLine();
 	        // url of the last lol program
 	        jarUrl = in.readLine();
 	        in.close();
    	 } catch (Exception e) {
    		 error = e.toString();
    		 e.printStackTrace();
    	 }

    	 if (error != null) {
    		 // if not silent (user manually checked for update), display a message
    		 if (!silent)
    			 JOptionPane.showMessageDialog(null, "Error checking update" + "\n[error: "+error+"]", "Update", JOptionPane.ERROR_MESSAGE);
    		 return;
    	 }
    	  
    	 // new version found, check if more recent
    	 int newVersion = compareVersions(v, RafManagerWin.version);
    	 // if more recent
    	 if (newVersion == 1) {	 
    		 int res = JOptionPane.showConfirmDialog(null, getInfoPanel(v), "New version available", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
    		 if (res != JOptionPane.OK_OPTION)
    			 return;
    		 
    		 Log.getInst().info("New update available. Downloading " + jarUrl + "...");
    		 
    		 try {
    			 // download the updater
    			 URL newUpdaterJar = new URL(updaterUrl);
    			 ReadableByteChannel rbc = Channels.newChannel(newUpdaterJar.openStream());
    			 File curF = new File(updaterName);
    			 FileOutputStream fos = new FileOutputStream(curF);
    			 fos.getChannel().transferFrom(rbc, 0, 1 << 24);
    			 fos.close();
    			 Log.getInst().info("updater downloaded into : " + curF.getAbsolutePath());
    			 
    			 // download the new app
    			 URL newJar = new URL(jarUrl);
    			 rbc = Channels.newChannel(newJar.openStream());
    			 fos = new FileOutputStream(appTempName);
    			 fos.getChannel().transferFrom(rbc, 0, 1 << 24);
    			 fos.close();
    			     			     			 
    			 // restart the app now ?
    			 int retour = JOptionPane.showConfirmDialog(null, "Update downloaded! Would you like to restart now to apply changes ?",
    					 "Update", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
    				
    			 // if ok close the app and launch the LoLUpdater
    			 if (retour == JOptionPane.OK_OPTION)
    				 restartApplication(null);
    			 else {
    				 Log.getInst().info("Update will be installed next start");
    				 JOptionPane.showMessageDialog(null, "Update will be installed next start",
    						 "Update", JOptionPane.INFORMATION_MESSAGE);
    			 }
    			 
			} catch (IOException e) {
				e.printStackTrace();
				Log.getInst().warning("Error while trying to download new version files");
			}
    	 }
    	 // no update
    	 else {
    		 if (!silent) {
    			 JOptionPane.showMessageDialog(null, "Version " + RafManagerWin.version + " up to date",
    					 "Update", JOptionPane.INFORMATION_MESSAGE);
    		 }
    	 }
     }
	 
	 public static JPanel getInfoPanel(String v) {
		 JEditorPane area = new JEditorPane();
		 area.setEditorKit(new HTMLEditorKit());
		 StringBuffer buf = new StringBuffer("<html>");
		 area.setEditable(false);
		 // get the changes for this version
		 boolean news = false;
		 try {
    		 URL clUrl = new URL(changelistUrl);
    		 URLConnection urlConn = clUrl.openConnection();
    		 InputStreamReader inStream = new InputStreamReader(urlConn.getInputStream());
    		 BufferedReader buff = new BufferedReader(inStream);
    		 String line;
    		 String ul = "";
    		 while ((line = buff.readLine()) != null) {
    			 if (line.startsWith("[" + v)) {
    				 news = true;
    	    		 buf.append("<b><u>What is new between versions " + RafManagerWin.version + " and " + v + " ?</u></b><br/>");
    			 }
    			 if (news) {
    				 if (line.length() == 0) {
    					 continue;
    				 }
    				 if (line.startsWith("[" + RafManagerWin.version)) {
    					 break;
    				 }
    				 else if (line.startsWith("[")) {
    					 buf.append(ul + "<br/><b>" + line + "</b><br/><ul>");
    					 ul = "</ul>";
    				 }
    				 else {
    					 buf.append("<li>"+ line.substring(2) + "<br/></li>");
    				 }
    			 }
    		 }
		 } catch (Exception e) {}
		 buf.append("</html>");
		 area.setText(buf.toString());
		 area.setCaretPosition(0);
		 
		 JPanel pan = new JPanel(new BorderLayout());
		 pan.add(new JLabel("New version " + v + " available"), BorderLayout.NORTH);
		 if (news) {
			 pan.add(new JLabel(" "), BorderLayout.CENTER);
			 area.setMargin(new Insets(5,5,5,5)); 
			 JScrollPane scroll = new JScrollPane(area);
			 scroll.setPreferredSize(new Dimension(250, 200));
			 pan.add(scroll, BorderLayout.SOUTH);
		 }
		 return pan;
	 }
	 
	 public static String getRootFolder() {
		 String path = Main.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		 String decodedPath = null;
		try {
			decodedPath = URLDecoder.decode(path, "UTF-8");
			decodedPath = new File(decodedPath).getAbsolutePath();
			Log.getInst().info("Root folder : " + decodedPath);
		} catch (UnsupportedEncodingException e) {
			Log.getInst().warning("Erro while getting root folder : " + e.getMessage());
			e.printStackTrace();
		}
		return decodedPath;
	 }

	 public static void restartApplication(Runnable runBeforeRestart) throws IOException {
		 Log.getInst().info("Starting application restart");
		 try {
			 // java binary
			 String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
			 /*
			 // vm arguments
			 List<String> vmArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
			 StringBuffer vmArgsOneLine = new StringBuffer();
			 for (String arg : vmArguments) {
				 // if it's the agent argument : we ignore it otherwise the
				 // address of the old application and the new one will be in conflict
				 if (!arg.contains("-agentlib")) {
					 vmArgsOneLine.append(arg);
					 vmArgsOneLine.append(" ");
				 }
			 }*/
			 // init the command to execute, add the vm args
     		
			 // jar location
			 String decodedPath = getRootFolder();
			 String updaterNamePath = updaterName;
			 
			 String osName= System.getProperty("os.name");
			 if (osName.toLowerCase().contains("windows")) {
				 // TODO: rajouter le .exe a java ????
				 java = "\"" + java + ".exe\"";
				 updaterNamePath =  "\"" + decodedPath + File.separator + updaterName + "\"";
			 }
			 //final StringBuffer cmd = new StringBuffer(java + " " + vmArgsOneLine);
			 final StringBuffer cmd = new StringBuffer(java);
			 
			 // program main and program arguments
			 String com = System.getProperty("sun.java.command");
			 if (com != null) {
				 String[] mainCommand = com.split(" ");
				 // program main is a jar
				 //if (mainCommand[0].endsWith(".jar")) {
				 // if it's a jar, add -jar mainJar
				 cmd.append("-jar " + updaterNamePath + " " + appTempName + " " + appName);
				 //} else {
	     			// else it's a .class, add the classpath and mainClass
	     			//cmd.append("-cp \"" + System.getProperty("java.class.path") + "\" " + mainCommand[0]);
	     		//}
	     		// finally add program arguments
				 for (int i = 1; i < mainCommand.length; i++) {
					 cmd.append(" ");
					 cmd.append(mainCommand[i]);
				 }
			 }
			 else {
				 cmd.append("-jar " + updaterNamePath + " " + appTempName + " " + appName);
			 }
			 			 
			 // log
			 Log.getInst().info("Executing : " + cmd.toString());
			 
			 // TODO: TEST THIS
			 final String cmdarr[] = {java, "-jar", updaterNamePath, appTempName, appName};
			 
			 // execute the command in a shutdown hook, to be sure that all the
			 // resources have been disposed before restarting the application
			 Runtime.getRuntime().addShutdownHook(new Thread() {
				 @Override
				 public void run() {
					 try {
						 Runtime.getRuntime().exec(cmdarr);
					 } catch (IOException e) {
						 Log.getInst().severe("Error when executing exec command");
						 e.printStackTrace();
					 }
				 }
			 });
			 // execute some custom code before restarting
			 if (runBeforeRestart!= null) {
				 runBeforeRestart.run();
			 }
			 // exit
			 System.exit(0);
		 } catch (Exception e) {
			 // something went wrong
			 Log.getInst().severe("Error while trying to restart the application");
			 throw new IOException("Error while trying to restart the application", e);
		 }
	 }
}
