/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.plugins.sitebot.announce.trialmanager.grouptop;

import java.util.ArrayList;
import java.util.ResourceBundle;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.master.common.Bytes;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.util.ReplacerUtils;
import org.drftpd.plugins.sitebot.AbstractAnnouncer;
import org.drftpd.plugins.sitebot.AnnounceWriter;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.plugins.sitebot.config.AnnounceConfig;
import org.drftpd.plugins.trialmanager.types.grouptop.GroupTopEvent;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @author CyBeR
 * @version $Id: GroupTopAnnouncer.java 2072 2010-09-18 22:01:23Z djb61 $
 */
public class GroupTopAnnouncer extends AbstractAnnouncer {

	private AnnounceConfig _config;

	private ResourceBundle _bundle;



	public void initialise(AnnounceConfig config, ResourceBundle bundle) {
		_config = config;
		_bundle = bundle;

		// Subscribe to events
		AnnotationProcessor.process(this);
	}

	public void stop() {
		AnnotationProcessor.unprocess(this);
	}

	public String[] getEventTypes() {
		return new String[] {"trialmanager.grouptop"};
	}
	
	public void setResourceBundle(ResourceBundle bundle) {
		_bundle = bundle;
	}

    @EventSubscriber
	public void onGroupTopEvent(GroupTopEvent event) {
		AnnounceWriter writer = _config.getSimpleWriter("trialmanager.grouptop");
		if (writer != null) {
			ReplacerEnvironment env_header = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
			env_header.add("name",event.getName());
			env_header.add("min",event.getMin());
			env_header.add("period",event.getPeriodStr());
			
			sayOutput(ReplacerUtils.jprintf("header", env_header, _bundle), writer);
			int passed = 0;
			ArrayList<User> users = event.getUsers();
			for (User user : users) {
				ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
				env.add("num",++passed);
				env.add("bytes", Bytes.formatBytes(user.getUploadedBytesForPeriod(event.getPeriod())));
				env.add("files",user.getUploadedFilesForPeriod(event.getPeriod()));
				env.add("name", user.getName());
				if ((user.getUploadedBytesForPeriod(event.getPeriod()) > event.getMin()) && (passed < event.getKeep())) {
					sayOutput(ReplacerUtils.jprintf("passed", env, _bundle), writer);
				} else {
					sayOutput(ReplacerUtils.jprintf("failed", env, _bundle), writer);
				
				}
			}	
			if (passed == 0) {
				sayOutput(ReplacerUtils.jprintf("empty", env_header, _bundle), writer);
			}
		}
	}
}
