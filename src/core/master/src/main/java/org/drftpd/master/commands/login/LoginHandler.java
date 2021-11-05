/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.master.commands.login;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.CommandInterface;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.commands.StandardCommandManager;
import org.drftpd.master.commands.usermanagement.UserManagement;
import org.drftpd.master.event.UserEvent;
import org.drftpd.master.network.BaseFtpConnection;
import org.drftpd.master.network.FtpReply;
import org.drftpd.master.usermanager.NoSuchUserException;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.usermanager.UserFileException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ResourceBundle;
import java.util.regex.PatternSyntaxException;

/**
 * @author mog
 * @author djb61
 * @version $Id$
 */
public class LoginHandler extends CommandInterface {
    private static final Logger logger = LogManager.getLogger(LoginHandler.class);

    private ResourceBundle _bundle;

    public void initialize(String method, String pluginName, StandardCommandManager cManager) {
        super.initialize(method, pluginName, cManager);
        _bundle = cManager.getResourceBundle();
    }

    /**
     * Syntax: IDNT ident@ip:dns
     * Returns nothing on success.
     */
    public CommandResponse doIDNT(CommandRequest request) {
        BaseFtpConnection conn = (BaseFtpConnection) request.getSession();
        request.getSession().setObject(BaseFtpConnection.FAILEDLOGIN, true);

        if (request.getSession().getObject(BaseFtpConnection.ADDRESS, null) != null) {
            logger.error("Multiple IDNT commands");
            request.getSession().setObject(BaseFtpConnection.FAILEDREASON, "IDNT Multiple");
            return new CommandResponse(530, "Multiple IDNT commands");
        }

        if (!conn.getObject(BaseFtpConnection.BOUNCERALLOWED, false)) {
            logger.warn("IDNT from non-bnc");
            request.getSession().setObject(BaseFtpConnection.FAILEDREASON, "IDNT Non-BNC");
            return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
        }

        String arg = request.getArgument();
        int pos1 = arg.indexOf('@');

        if (pos1 == -1) {
            request.getSession().setObject(BaseFtpConnection.FAILEDREASON, "IDNT Syntax");
            return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        }

        int pos2 = arg.indexOf(':', pos1 + 1);

        if (pos2 == -1) {
            request.getSession().setObject(BaseFtpConnection.FAILEDREASON, "IDNT Syntax");
            return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        }

        try {
            request.getSession().setObject(BaseFtpConnection.ADDRESS, InetAddress.getByName(arg.substring(pos1 + 1, pos2)));
            request.getSession().setObject(BaseFtpConnection.IDENT, arg.substring(0, pos1));
        } catch (UnknownHostException e) {
            logger.info("Invalid hostname passed to IDNT", e);
            request.getSession().setObject(BaseFtpConnection.FAILEDREASON, "IDNT Failed");
            //this will most likely cause control connection to become unsynchronized
            //but give error anyway, this error is unlikely to happen
            return new CommandResponse(501, "IDNT FAILED: " + e.getMessage());
        }

        // bnc doesn't expect any reply
        request.getSession().setObject(BaseFtpConnection.FAILEDLOGIN, false);
        return null;
    }

    /**
     * <code>PASS &lt;SP&gt; <password> &lt;CRLF&gt;</code><br>
     * <p>
     * The argument field is a Telnet string specifying the user's
     * password.  This command must be immediately preceded by the
     * user name command.
     */
    public CommandResponse doPASS(CommandRequest request) {
        BaseFtpConnection conn = (BaseFtpConnection) request.getSession();
        request.getSession().setObject(BaseFtpConnection.FAILEDLOGIN, true);

        if (conn.getUserNullUnchecked() == null) {
            request.getSession().setObject(BaseFtpConnection.FAILEDREASON, "PASS Bad-Sequence");
            return StandardCommandManager.genericResponse("RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS");
        }

        // set user password and login
        String pass = request.hasArgument() ? request.getArgument() : "";

        // login failure - close connection
        if (conn.getUserNullUnchecked().checkPassword(pass)) {
            conn.setAuthenticated(true);
            GlobalContext.getEventService().publishAsync(new UserEvent(conn.getUserNull(), "LOGIN", System.currentTimeMillis()));

            CommandResponse response = new CommandResponse(230, conn.jprintf(_bundle, "pass.success", conn.getUsername()));

            try {
                response.addComment(ConfigLoader.loadTextFile("welcome.txt"));
            } catch (IOException e) {
                // Not mandatory to have a welcome text, so if it is not present only show a message in debug mode
                logger.debug("No welcome.txt found, silently ignoring this");
            }
            request.getSession().setObject(BaseFtpConnection.FAILEDUSERNAME, "");
            request.getSession().setObject(BaseFtpConnection.FAILEDLOGIN, false);

            // Handle killing of ghosts
            if (request.getSession().getObject(BaseFtpConnection.KILLGHOSTS, false)) {
                for (BaseFtpConnection conn2 : GlobalContext.getConnectionManager().getConnections()) {
                    try {
                        // Do not kill this connection
                        if (!conn.equals(conn2)) {
                            if (conn2.getUser().equals(conn.getUser())) {
                                if ((System.currentTimeMillis() - conn2.getLastActive()) > (30 * 1000)) {
                                    conn2.stop("Killed on self request by using '!' prefix");
                                }
                            }
                        }
                    } catch (NoSuchUserException e) {
                        // The user might have logged of or be deleted, so ignore
                    }
                }
            }
            return response;
        }

        request.getSession().setObject(BaseFtpConnection.FAILEDREASON, "PASS Failed");
        return new CommandResponse(530, conn.jprintf(_bundle, "pass.fail", request.getUser() == null ? request.getSession().getObject(BaseFtpConnection.FAILEDUSERNAME, "") : request.getUser()));
    }

    /**
     * {@code QUIT <CRLF>}<br>
     * <p>
     * This command terminates a USER and if file transfer is not
     * in progress, the server closes the control connection.
     */
    public CommandResponse doQUIT(CommandRequest request) {
        BaseFtpConnection conn = (BaseFtpConnection) request.getSession();
        conn.stop();

        return new CommandResponse(221, conn.jprintf(_bundle, "quit.success", request.getUser()));
    }

    private void getIP(CommandRequest request) {
        if (request.getSession().getObject(BaseFtpConnection.ADDRESS, null) == null) {
            request.getSession().setObject(BaseFtpConnection.ADDRESS, ((BaseFtpConnection) request.getSession()).getClientAddress());
        }
    }

    /**
     * {@code USER <SP> <username> <CRLF>}<br>
     * <p>
     * The argument field is a Telnet string identifying the user.
     * The user identification is that which is required by the
     * server for access to its file system.  This command will
     * normally be the first command transmitted by the user after
     * the control connections are made.
     */
    public CommandResponse doUSER(CommandRequest request) {
        BaseFtpConnection conn = (BaseFtpConnection) request.getSession();
        request.getSession().setObject(BaseFtpConnection.FAILEDLOGIN, true);
        conn.setAuthenticated(false);
        conn.setUser(null);

        // argument check
        if (!request.hasArgument()) {
            request.getSession().setObject(BaseFtpConnection.FAILEDREASON, "USER Syntax");
            getIP(request);
            return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        }

        // Handle killghosts part (the ! in front of the user)
        String username = request.getArgument();
        if (username.charAt(0) == '!') {
            username = username.substring(1);
            request.getSession().setObject(BaseFtpConnection.KILLGHOSTS, true);
        }
        request.getSession().setObject(BaseFtpConnection.FAILEDUSERNAME, username);

        User newUser;

        try {
            newUser = conn.getGlobalContext().getUserManager().getUserByNameIncludeDeleted(username);
        } catch (NoSuchUserException ex) {
            getIP(request);
            request.getSession().setObject(BaseFtpConnection.FAILEDREASON, "USER Non-Existant");
            return new CommandResponse(530, ex.getMessage());
        } catch (UserFileException ex) {
            logger.warn(ex, ex);
            getIP(request);
            request.getSession().setObject(BaseFtpConnection.FAILEDREASON, "USER Non-Existant");
            return new CommandResponse(530, "IOException: " + ex.getMessage());
        } catch (RuntimeException ex) {
            logger.error(ex, ex);
            getIP(request);
            request.getSession().setObject(BaseFtpConnection.FAILEDREASON, "USER Runtime");
            return new CommandResponse(530, "RuntimeException: " + ex.getMessage());
        }

        if (newUser.isDeleted()) {
            getIP(request);
            request.getSession().setObject(BaseFtpConnection.FAILEDREASON, "USER Deleted");
            return new CommandResponse(530, newUser.getConfigHelper().get(UserManagement.REASON, StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED").getMessage()));
        }

        if (!GlobalContext.getConfig().isLoginAllowed(newUser)) {
            getIP(request);
            request.getSession().setObject(BaseFtpConnection.FAILEDREASON, "USER Not-Allowed");
            if (GlobalContext.getConfig().getAllowConnectionsDenyReason() != null) {
                return new CommandResponse(530, GlobalContext.getConfig().getAllowConnectionsDenyReason());
            }
            return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
        }

        try {
            InetAddress address = request.getSession().getObject(BaseFtpConnection.ADDRESS, null);
            String ident = request.getSession().getObject(BaseFtpConnection.IDENT, null);
            if (ident == null) {
                logger.error("Somehow we got here with an empty IDENT, this should not be possible");
                return new CommandResponse(530, "Internal server error");
            }

            boolean hostMaskPassed;
            if (address != null) {
                // this means that the user is connecting from a BNC.
                hostMaskPassed = newUser.getHostMaskCollection().check(ident, address);
            } else {
                // this mean that the user is connecting to the ftp directly.
                hostMaskPassed = newUser.getHostMaskCollection().check(ident, conn.getClientAddress());

                // ADDRESS is null, let's set it.
                request.getSession().setObject(BaseFtpConnection.ADDRESS, conn.getClientAddress());
            }

            if (hostMaskPassed) {
                //success
                // max_users and num_logins restriction
                FtpReply ftpResponse = GlobalContext.getConnectionManager().canLogin(conn, newUser);

                if (ftpResponse != null) {
                    return new CommandResponse(ftpResponse.getCode(), ftpResponse.getMessage());
                }

                request.getSession().setObject(BaseFtpConnection.FAILEDLOGIN, false);
                return new CommandResponse(331, conn.jprintf(_bundle, "user.success", newUser.getName()), request.getCurrentDirectory(), newUser.getName());
            }
        } catch (PatternSyntaxException e) {
            return new CommandResponse(530, e.getMessage());
        }

        request.getSession().setObject(BaseFtpConnection.FAILEDREASON, "USER IP-Failed");
        //fail
        logger.warn("{} failed hostmask check", newUser.getName());
        return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
    }
}
