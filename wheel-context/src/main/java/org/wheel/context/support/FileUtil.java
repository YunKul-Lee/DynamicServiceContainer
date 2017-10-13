package org.wheel.context.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class FileUtil {

    private static final Logger logger = LoggerFactory.getLogger(FileUtil.class);

    public static void createDirectoryIfNotPresent(File dir) throws IOException {
        if(!dir.exists()) {
            if(dir.mkdir()) {
                logger.debug("Created new dir=[" + dir + "]");
            } else {
                // 상위 디렉토리의 어느 단계인가가 없는 상태이므로 사우이 디렉토리를 생성한다.
                String fullPath = dir.getPath();
                int idxOfLastSeparator = fullPath.lastIndexOf(File.separator);
                if(idxOfLastSeparator < 0) {
                    throw new IllegalArgumentException(
                            "FAILED to create dirpath=[" + dir.getPath() + "]."
                    );
                }
                String oneStepParentPath = fullPath.substring(0, idxOfLastSeparator);
                createDirectoryIfNotPresent(new File(oneStepParentPath));

                if(dir.mkdir()) {
                    logger.debug("Created dir=[{}].", dir);
                } else {
                    throw new IllegalArgumentException(
                            "FAILED to create dirpath=[" + dir.getPath() + "]. Maybe file/directory permission problem?"
                    );
                }
            }
        }
    }

}
