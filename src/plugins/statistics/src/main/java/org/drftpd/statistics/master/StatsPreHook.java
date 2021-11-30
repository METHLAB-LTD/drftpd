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
package org.drftpd.statistics.master;

import org.drftpd.common.extensibility.CommandHook;
import org.drftpd.common.extensibility.HookType;
import org.drftpd.common.util.Bytes;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandRequestInterface;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.commands.StandardCommandManager;
import org.drftpd.master.permissions.stats.CreditLimitPathPermission;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.ObjectNotValidException;

import java.io.FileNotFoundException;

/**
 * @author fr0w
 * @version $Id$
 */
public class StatsPreHook {

    @CommandHook(commands = "doRETR", type = HookType.PRE)
    public CommandRequestInterface doRETRPreHook(CommandRequest request) {
        DirectoryHandle dir = request.getCurrentDirectory();
        User user = request.getSession().getUserNull(request.getUser());

        CreditLimitPathPermission clc = StatsManager.getStatsManager().creditLimitCheck(dir, user, StatsHandler.DIRECTION_DN);
        if (clc != null) {
            request.setAllowed(false);
            request.setDeniedResponse(new CommandResponse(550, "Credit limit for period exceeded [" +
                    Bytes.formatBytes(clc.getBytes()) + "/" + clc.getPeriod() + "]"));
            return request;
        }

        double ratio = StatsManager.getStatsManager().getCreditLossRatio(dir, user);

        if (ratio == 0L) {
            return request;
        }

        try {
            long fileSize = dir.getFileUnchecked(request.getArgument()).getSize();
            long creditsLoss = (long) ratio * fileSize;
            double userCredits = user.getCredits();

            if (userCredits < creditsLoss) {
                // this comparison doesn't allow a user with negative credits,
                // but a 0 ratio to download files :)
                request.setAllowed(false);
                request.setDeniedResponse(new CommandResponse(550, "Not enough credits"));
            }

        } catch (FileNotFoundException e) {
            request.setAllowed(false);
            request.setDeniedResponse(StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN"));
        } catch (ObjectNotValidException e) {
            request.setAllowed(false);
            request.setDeniedResponse(new CommandResponse(550, "Argument is not a file"));
        }

        return request;
    }

    @CommandHook(commands = "doSTOR", type = HookType.PRE)
    public CommandRequestInterface doSTORPreHook(CommandRequest request) {
        DirectoryHandle dir = request.getCurrentDirectory();
        User user = request.getSession().getUserNull(request.getUser());

        CreditLimitPathPermission clc = StatsManager.getStatsManager().creditLimitCheck(dir, user, StatsHandler.DIRECTION_UP);
        if (clc != null) {
            request.setAllowed(false);
            request.setDeniedResponse(new CommandResponse(550, "Credit limit for period exceeded [" +
                    Bytes.formatBytes(clc.getBytes()) + "/" + clc.getPeriod() + "]"));
        }
        return request;
    }
}
