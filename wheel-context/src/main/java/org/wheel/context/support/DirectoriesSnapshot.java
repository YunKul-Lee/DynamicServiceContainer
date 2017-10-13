package org.wheel.context.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DirectoriesSnapshot {

    private static Logger logger = LoggerFactory.getLogger(DirectoriesSnapshot.class);

    private final List<File> jarFileList;
    private long checksum = 0;

    public DirectoriesSnapshot(File... jarDirs) {
        this.jarFileList = new ArrayList<File>();
        for(File jarDir : jarDirs) {
            if(jarDir == null || !jarDir.exists() || !jarDir.isDirectory()) {
                throw new IllegalArgumentException("jarDir=" + jarDir.getAbsolutePath() + " is not a directory nor exist.");
            }
            File[] jarFiles = jarDir.listFiles(new JarFilenameFilter());

            for(File jarFile : jarFiles) {
                checksum += jarFile.lastModified();
                this.jarFileList.add(jarFile);
            }
        }
    }

    public boolean checkChanges(DirectoriesSnapshot newSnapshot) {
        List<File> oldJarFileList = this.jarFileList;
        List<File> newJarFileList = newSnapshot.jarFileList;

        if(!oldJarFileList.containsAll(newJarFileList) ||
                !newJarFileList.containsAll(oldJarFileList)) {
            return true;
        }

        if(this.checksum != newSnapshot.checksum) {
            logger.debug("this.lastModifiedSum={}, new.lastModifiedSum={}", this.checksum, newSnapshot.checksum);
            return true;
        }

        return false;
    }

    public long getChecksum() {
        return checksum;
    }

    public List<File> getJarFileList() {
        return jarFileList;
    }

    @Override
    public String toString() {
        return "DirectoriesSnapshot{" +
                "jarFileList=" + jarFileList +
                ", checksum=" + checksum +
                '}';
    }
}
