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
package org.drftpd.master.commands;


import org.drftpd.common.dynamicdata.Key;
import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.common.dynamicdata.KeyedMap;
import org.drftpd.common.dynamicdata.element.ConfigElement;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.network.Session;
import org.drftpd.master.permissions.Permission;
import org.drftpd.master.usermanager.NoSuchUserException;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.usermanager.UserFileException;
import org.drftpd.master.vfs.DirectoryHandle;

import java.util.Collections;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * @author djb61
 * @version $Id$
 */
@SuppressWarnings("serial")
public class CommandRequest extends KeyedMap<Key<?>, ConfigElement<?>> implements CommandRequestInterface {

    public static final Key<Boolean> ALLOWED = new Key<>(CommandRequest.class, "allowed");

    public static final Key<CommandResponse> DENIED_RESPONSE = new Key<>(CommandRequest.class, "denied_response");

    public static final Key<String> ARGUMENT = new Key<>(CommandRequest.class, "argument");

    public static final Key<String> COMMAND = new Key<>(CommandRequest.class, "command");

    public static final Key<DirectoryHandle> CURRENT_DIRECTORY = new Key<>(CommandRequest.class, "current_directory");

    public static final Key<Session> SESSION = new Key<>(CommandRequest.class, "session");

    public static final Key<String> USER = new Key<>(CommandRequest.class, "user");

    private static final Key<Properties> PROPERTIES = new Key<>(CommandRequest.class, "properties");

    public CommandRequest(String argument, String command, DirectoryHandle directory, String user) {
        setArgument(argument);
        setCommand(command);
        setCurrentDirectory(directory);
        setUser(user);
    }

    public CommandRequest(String command, String argument, DirectoryHandle directory, String user, Session session, Properties p) {
        setArgument(argument);
        setCommand(command);
        setCurrentDirectory(directory);
        setSession(session);
        setUser(user);
        setProperties(p);
    }

    public boolean isAllowed() {
        return getObjectBoolean(CommandRequest.ALLOWED);
    }

    public void setAllowed(Boolean allowed) {
        setObject(CommandRequest.ALLOWED, allowed);
    }

    public void setAllowed(boolean b) {
        setAllowed(Boolean.valueOf(b));
    }

    public String getArgument() {
        return getObjectString(CommandRequest.ARGUMENT);
    }

    public void setArgument(String argument) {
        if (argument != null) {
            setObject(CommandRequest.ARGUMENT, argument);
        }
    }

    public Permission getPermission() {
        Properties p = getProperties();
        if (p == null) {
            return new Permission(Collections.singletonList("=siteop"));
        }
        String perms = p.getProperty("perms");
        StringTokenizer st = new StringTokenizer(perms);
        if (!st.hasMoreTokens()) {
            return new Permission(Collections.singletonList("=siteop"));
        }
        return new Permission(Permission.makeUsers(st));
    }

    public CommandResponse getDeniedResponse() {
        return getObject(CommandRequest.DENIED_RESPONSE, null);
    }

    public void setDeniedResponse(CommandResponse response) {
        setObject(CommandRequest.DENIED_RESPONSE, response);
    }

    public DirectoryHandle getCurrentDirectory() {
        return getObject(CommandRequest.CURRENT_DIRECTORY, new DirectoryHandle("/"));
    }

    public void setCurrentDirectory(DirectoryHandle currentDirectory) {
        setObject(CommandRequest.CURRENT_DIRECTORY, currentDirectory);
    }

    public String getCommand() {
        return getObject(CommandRequest.COMMAND, null);
    }

    public void setCommand(String command) {
        setObject(CommandRequest.COMMAND, command.toLowerCase());
    }

    public Session getSession() {
        return getObject(CommandRequest.SESSION, null);
    }

    public void setSession(Session session) {
        setObject(CommandRequest.SESSION, session);
    }

    public String getUser() {
        return getObject(CommandRequest.USER, null);
    }

    public void setUser(String currentUser) {
        if (currentUser != null) {
            setObject(CommandRequest.USER, currentUser);
        }
    }

    public boolean hasArgument() {
        try {
            getObject(CommandRequest.ARGUMENT);
            return true;
        } catch (KeyNotFoundException e) {
            return false;
        }
    }

    public Properties getProperties() {
        return getObject(CommandRequest.PROPERTIES, new Properties());
    }

    public void setProperties(Properties properties) {
        if (properties == null) {
            return;
        }
        setObject(CommandRequest.PROPERTIES, properties);
    }

    public User getUserObject() throws NoSuchUserException, UserFileException {
        if (getUser() == null) {
            throw new NoSuchUserException("User not set, authentication may not have completed yet");
        }
        return GlobalContext.getGlobalContext().getUserManager().getUserByName(getUser());
    }

}
