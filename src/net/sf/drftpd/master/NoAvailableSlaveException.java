package net.sf.drftpd.master;

/**
 * @author mog
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class NoAvailableSlaveException extends Exception {
	public NoAvailableSlaveException(String message) {
		super(message);
	}
	public NoAvailableSlaveException() {
		super();
	}
}
