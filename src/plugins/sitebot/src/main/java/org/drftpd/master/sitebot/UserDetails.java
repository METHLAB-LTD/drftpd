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
package org.drftpd.master.sitebot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.dynamicdata.Key;
import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.usermanagement.UserManagement;
import org.drftpd.master.usermanager.NoSuchUserException;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.usermanager.UserFileException;

import java.util.ArrayList;
import java.util.List;

/**
 * @author djb61
 * @version $Id$
 */
public class UserDetails {

    public static final Key<String> BLOWKEY = new Key<>(UserDetails.class, "blowfishkey");
    private static final Logger logger = LogManager.getLogger(UserDetails.class);
    private final SiteBot _bot;

    private BlowfishManager _cipher = null;

    private String _blowKey = null;

    private String _blowMode = null;

    private String _ftpUser = null;

    private final String _ident;

    private String _nick;

    private final OutputWriter _writer;

    private final ArrayList<ServiceCommand> _commandSessions = new ArrayList<>();

    public UserDetails(String nick, String ident, SiteBot bot) {
        _bot = bot;
        _ident = ident;
        _nick = nick;
        _writer = new OutputWriter(_bot, _nick, null);
        getDetailsFromDB();
    }

    protected String getFtpUser() {
        // If we don't know the ftp username then check again as they may have updated the
        // ident in the userfile since we last tried.
        if (_ftpUser == null) {
            getDetailsFromDB();
        }
        return _ftpUser;
    }

    protected void setFtpUser(String ftpUser) {
        _ftpUser = ftpUser;
    }

    protected BlowfishManager getBlowCipher() {
        // If we don't have a cipher for this user then check again as they may have set
        // one on site since we last checked.
        if (_cipher == null) {
            getDetailsFromDB();
        }
        return _cipher;
    }

    protected void setBlowCipher(String blowKey, String blowMode) {
        _cipher = new BlowfishManager(blowKey, blowMode);
        _blowKey = blowKey;
        _blowMode = blowMode;
        // If we don't know who the user is then try to find them first
        if (_ftpUser == null) {
            getDetailsFromDB();
        }
        // If we know who the user is then update their userfile with the new key
        if (_ftpUser != null) {
            try {
                User user = GlobalContext.getGlobalContext().getUserManager().getUserByName(_ftpUser);
                setUserBlowKey(user);
            } catch (NoSuchUserException e) {
                // do nothing
            } catch (UserFileException e) {
                logger.warn("Error loading userfile for {}", _ftpUser, e);
            }
        }
        // Update cipher in the users OutputWriter
        _writer.updateCipher(_cipher);
    }

    private void getDetailsFromDB() {
        try {
            User user;
            // Check if we need to identify the user by ident or if we know their username
            if (_ftpUser == null) {
                user = GlobalContext.getGlobalContext().getUserManager().getUserByIdent(_ident, _bot.getBotName());
                _ftpUser = user.getName();
                // Force any known blowkey to be flushed into user database
                if (_blowKey != null) {
                    setUserBlowKey(user);
                }
            } else {
                user = GlobalContext.getGlobalContext().getUserManager().getUserByName(_ftpUser);
            }
            // If we know of a blowfish key for this user then set this in their userfile
            // now we have identified them, if we don't and they have a key set in the
            // userfile then use that key, if we still can't find a key then try DH1080 if
            // appropriate.
            if (_blowKey != null) {
                setUserBlowKey(user);
            } else {
                try {
                    String userKeysString = user.getConfigHelper().get(BLOWKEY);
                    String[] userKeys = userKeysString.split(",");
                    String userKey = "";
                    String userKeyMode = "";
                    for (String userKey1 : userKeys) {
                        if (userKey1.startsWith(_bot.getBotName() + "|")) {
                            String[] botKey = userKey1.split("\\|");
                            if (botKey.length > 1) {
                                userKey = botKey[1];
                                userKeyMode = botKey.length > 2 ? botKey[2] : BlowfishManager.ECB; //Compatibility
                                break;
                            }
                        }
                    }
                    if(userKey.length() > 0) {
                        if (userKeyMode.equalsIgnoreCase(BlowfishManager.ECB) && _bot.getConfig().getDH1080ECBEnabled()) {
                            setBlowCipher(userKey, userKeyMode);
                        } else if (userKeyMode.equalsIgnoreCase(BlowfishManager.CBC) && _bot.getConfig().getDH1080CBCEnabled()) {
                            setBlowCipher(userKey, userKeyMode);
                        } else {
                            logger.warn("Unknown blowfish cipher configured: [{}], Initiating new DH1080 exchange", userKeyMode);
                            _bot.initiateDH1080(_nick);
                        }
                    } else {
                        _bot.initiateDH1080(_nick);
                    }
                } catch (KeyNotFoundException e1) {
                    // Means this user has never set a blowfish key, is safe to proceed
                    logger.debug("Blowfish key has never been set for {}", _ftpUser);
                }
            }
        } catch (NoSuchUserException e) {
            //do nothing
        } catch (UserFileException e) {
            logger.warn("Error loading userfile for {}", _ftpUser, e);
        }
    }

    private void setUserBlowKey(User user) {
        String userKeysString = "";
        try {
            userKeysString = user.getConfigHelper().get(BLOWKEY);
        } catch (KeyNotFoundException e1) {
            // Means this user has never set a blowfish key, is safe to proceed
            logger.debug("Blowfish key has never been set for {}", _ftpUser);
        }
        String[] userKeys = userKeysString.split(",");
        boolean foundOld = false;
        StringBuilder userKey = new StringBuilder();
        for (int i = 0; i < userKeys.length; i++) {
            if (userKeys[i].startsWith(_bot.getBotName() + "|")) {
                userKey.append(_bot.getBotName());
                userKey.append("|");
                userKey.append(_blowKey);
                userKey.append("|");
                userKey.append(_blowMode);
                foundOld = true;
            } else {
                userKey.append(userKeys[i]);
            }
            if (i < userKeys.length - 1) {
                userKey.append(",");
            }
        }
        if (!foundOld) {
            if (userKey.length() > 0) {
                userKey.append(",");
            }
            userKey.append(_bot.getBotName());
            userKey.append("|");
            userKey.append(_blowKey);
            userKey.append("|");
            userKey.append(_blowMode);
        }
        user.getConfigHelper().setString(BLOWKEY, userKey.toString());
        user.commit();
    }

    public String getNick() {
        return _nick;
    }

    protected void setNick(String nick) {
        _nick = nick;
    }

    protected void setIdent(String ident) {
        String existIdentString = "";
        User user;

        try {
            user = GlobalContext.getGlobalContext().getUserManager().getUserByName(_ftpUser);
        } catch (NoSuchUserException e) {
            // can't set ident, just return
            return;
        } catch (UserFileException e) {
            logger.warn("Error loading userfile for {}", _ftpUser, e);
            // can't set ident, just return
            return;
        }
        try {
            existIdentString = user.getConfigHelper().get(UserManagement.IRCIDENT);
        } catch (KeyNotFoundException e) {
            // Means no existing idents at all, safe to proceed
        }
        boolean foundOld = false;
        String[] existIdents = existIdentString.split(",");
        StringBuilder newIdents = new StringBuilder();
        String sourceBot = _bot.getBotName();
        for (int i = 0; i < existIdents.length; i++) {
            if (existIdents[i].startsWith(sourceBot + "|")) {
                newIdents.append(sourceBot);
                newIdents.append("|");
                newIdents.append(ident);
                foundOld = true;
            } else {
                newIdents.append(existIdents[i]);
            }
            if (i < existIdents.length - 1) {
                newIdents.append(",");
            }
        }
        if (!foundOld) {
            if (newIdents.length() > 0) {
                newIdents.append(",");
            }
            newIdents.append(sourceBot);
            newIdents.append("|");
            newIdents.append(ident);
        }
        user.getConfigHelper().setString(UserManagement.IRCIDENT, newIdents.toString());
        user.commit();
        logger.info("Set IRC ident to '{}' for {} on bot {}", ident, user.getName(), sourceBot);
    }

    protected OutputWriter getOutputWriter() {
        return _writer;
    }

    protected synchronized ServiceCommand getCommandSession(ArrayList<OutputWriter> outputs, String source) {
        ServiceCommand newSession = new ServiceCommand(_bot, outputs, this, _ident, source);
        _commandSessions.add(newSession);
        return newSession;
    }

    protected synchronized void removeCommandSession(ServiceCommand session) {
        _commandSessions.remove(session);
    }

    protected List<ServiceCommand> getCommandSessions() {
        return _commandSessions;
    }
}
