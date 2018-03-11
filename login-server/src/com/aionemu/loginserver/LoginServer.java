package com.aionemu.loginserver;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.commons.database.dao.DAOManager;
import com.aionemu.commons.utils.ConsoleUtil;
import com.aionemu.commons.utils.ExitCode;
import com.aionemu.commons.utils.concurrent.UncaughtExceptionHandler;
import com.aionemu.commons.utils.info.SystemInfoUtil;
import com.aionemu.commons.utils.info.VersionInfoUtil;
import com.aionemu.loginserver.configs.Config;
import com.aionemu.loginserver.controller.BannedIpController;
import com.aionemu.loginserver.controller.PremiumController;
import com.aionemu.loginserver.dao.BannedHddDAO;
import com.aionemu.loginserver.dao.BannedMacDAO;
import com.aionemu.loginserver.network.NetConnector;
import com.aionemu.loginserver.network.ncrypt.KeyGen;
import com.aionemu.loginserver.service.PlayerTransferService;
import com.aionemu.loginserver.utils.ThreadPoolManager;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;

/**
 * @author -Nemesiss-
 */
public class LoginServer {

	/**
	 * Logger for this class.
	 */
	private static final Logger log = LoggerFactory.getLogger(LoginServer.class);

	private static void initalizeLoggger() {
		new File("./log/backup/").mkdirs();
		File[] files = new File("log").listFiles(new FilenameFilter() {

			@Override
			public boolean accept(File dir, String name) {
				return name.endsWith(".log");
			}
		});

		if (files != null && files.length > 0) {
			byte[] buf = new byte[1024];
			String outFilename = "./log/backup/" + new SimpleDateFormat("yyyy-MM-dd HHmmss").format(new Date()) + ".zip";
			try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(outFilename))) {
				out.setMethod(ZipOutputStream.DEFLATED);
				out.setLevel(Deflater.BEST_COMPRESSION);

				for (File logFile : files) {
					try (FileInputStream in = new FileInputStream(logFile)) {
						out.putNextEntry(new ZipEntry(logFile.getName()));
						int len;
						while ((len = in.read(buf)) > 0) {
							out.write(buf, 0, len);
						}
						out.closeEntry();
					}
					logFile.delete();
				}
			} catch (IOException e) {
			}
		}
		LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
		try {
			JoranConfigurator configurator = new JoranConfigurator();
			configurator.setContext(lc);
			lc.reset();
			configurator.doConfigure("config/slf4j-logback.xml");
		} catch (JoranException je) {
			throw new RuntimeException("Failed to configure loggers, shutting down...", je);
		}
	}

	/**
	 * @param args
	 */
	public static void main(final String[] args) {
		long start = System.currentTimeMillis();

		initalizeLoggger();
		Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler());

		Config.load();
		DatabaseFactory.init();
		DAOManager.init();

		ThreadPoolManager.getInstance();

		/**
		 * Initialize Key Generator
		 */
		try {
			KeyGen.init();
		} catch (Exception e) {
			log.error("Failed initializing Key Generator", e);
			System.exit(ExitCode.CODE_ERROR);
		}

		GameServerTable.load();
		BannedIpController.start();
		DAOManager.getDAO(BannedMacDAO.class).cleanExpiredBans();
		DAOManager.getDAO(BannedHddDAO.class).cleanExpiredBans();

		PlayerTransferService.getInstance();
		PremiumController.getController();

		ConsoleUtil.printSection("System Info");
		VersionInfoUtil.printAllInfo(LoginServer.class);
		SystemInfoUtil.printAllInfo();

		NetConnector.getInstance().connect(ThreadPoolManager.getInstance());
		Runtime.getRuntime().addShutdownHook(Shutdown.getInstance());
		log.info("Login Server started in " + (System.currentTimeMillis() - start) / 1000 + " seconds.");
	}
}
