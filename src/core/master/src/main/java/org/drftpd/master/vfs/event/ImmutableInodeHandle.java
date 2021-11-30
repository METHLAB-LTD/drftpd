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
package org.drftpd.master.vfs.event;

import org.drftpd.common.dynamicdata.Key;
import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.VirtualFileSystem;
import org.drftpd.master.vfs.VirtualFileSystemFile;
import org.drftpd.master.vfs.VirtualFileSystemInode;

import java.util.HashSet;
import java.util.Set;

/**
 * @author djb61
 * @version $Id$
 */
public class ImmutableInodeHandle {

    private final VirtualFileSystemInode _inode;

    private final String _path;

    public ImmutableInodeHandle(VirtualFileSystemInode inode, String path) {
        _inode = inode;
        _path = path;
    }

    public String getGroup() {
        return _inode.getGroup();
    }

    public long getLastModified() {
        return _inode.getLastModified();
    }

    public String getName() {
        return _inode.getName();
    }

    public DirectoryHandle getParent() {
        if (_path.equals(VirtualFileSystem.separator)) {
            throw new IllegalStateException("Can't get the parent of the root directory");
        }
        return new DirectoryHandle(VirtualFileSystem.stripLast(getPath()));
    }

    public String getPath() {
        return _path;
    }

    public <T> T getPluginMetaData(Key<T> key) throws KeyNotFoundException {
        return _inode.getPluginMetaData(key);
    }

    public long getSize() {
        return _inode.getSize();
    }

    public Set<String> getSlaveNames() throws UnsupportedOperationException {
        if (isFile()) {
            return new HashSet<>(((VirtualFileSystemFile) _inode).getSlaves());
        }
        throw new UnsupportedOperationException("Slaves can only be retrieved from file inodes");
    }

    public String getUsername() {
        return _inode.getUsername();
    }

    public boolean isDirectory() {
        return _inode.isDirectory();
    }

    public boolean isFile() {
        return _inode.isFile();
    }

    public boolean isLink() {
        return _inode.isLink();
    }

    public long lastModified() {
        return _inode.getLastModified();
    }
}
