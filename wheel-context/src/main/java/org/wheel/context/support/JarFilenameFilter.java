package org.wheel.context.support;

import java.io.File;
import java.io.FilenameFilter;

public class JarFilenameFilter implements FilenameFilter {
    @Override
    public boolean accept(File dir, String name) {
        return name.endsWith(".jar");
    }
}
