package org.lcm.main;
import javax.swing.UIManager;

import org.lcm.updater.LoLUpdater;
import org.lcm.utils.Log;



public class Main {

	public static void main(String[] args) {
		try {
            // for windows users
			UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
		} catch (Exception e) {}
		
		Log.getInst().info("Starting application");
		// if there was an update, the app will be restarted
		LoLUpdater.applyUpdate();
		// run the main win
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				new RafManagerWin();
			}
		});
	}

}
