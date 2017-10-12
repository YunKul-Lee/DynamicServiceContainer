package org.wheel.context;

import java.io.File;
import java.util.Map;

public interface ContextLoader {

    ContextLoadImage load(String loadId, File[] jarFiles, Map<String, Object> loadAttributes);

    void registerListener(ContextLoaderListener listener);
}
