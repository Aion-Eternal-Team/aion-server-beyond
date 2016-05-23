package com.aionemu.chatserver.configs.main;

import com.aionemu.commons.configuration.Property;

public class CSConfig {

	/**
	 * Specifies the frequency the chat server will be restarted
	 */
	@Property(key = "chatserver.restart.frequency", defaultValue = "NEVER")
	public static String CHATSERVER_RESTART_FREQUENCY;

	/**
	 * Specifies the exact time of day the server should be restarted (of course respecting the frequency)
	 */
	@Property(key = "chatserver.restart.time", defaultValue = "5:00")
	public static String CHATSERVER_RESTART_TIME;

}