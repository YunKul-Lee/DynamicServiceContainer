package org.wheel.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;
import org.wheel.context.support.DirectoriesSnapshot;
import org.wheel.context.support.FileUtil;
import org.wheel.context.support.JarFilenameFilter;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class DefaultMultiVersionContextContainer implements
        MultiVersionContextContainer, ContextLoaderListener, ApplicationContextAware, InitializingBean, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(DefaultMultiVersionContextContainer.class);

    // bean properties
    private ContextLoader loader;

    public static final String LOAD_DIRECTIVE_FILENAME = "LOAD_DIRECTIVE";
    public static final String LOAD_ATTR_DIRECTORY_SNAPSHOT = "originalDirectoriesSnapshot";

    private boolean enableAutoReload = true;
    private int monitorIntervalSecs = 30;
    private boolean autoSwitchAfterReload = true;
    private boolean autoSwitchSuccessOnly = true;
    private int maxSuccessImagesInMemory = 2;
    private File srcJarDirectory;
    private File destJarBaseDirectory;
    private int maxFileCopyRetryCount = 10;
    private int fileCopyRetryIntervalMilis = 2 * 1000;

    private volatile Thread monitorThread = null;   // 변경사항 감지 스레드

    private ExecutorService asyncReloadExecutorService; // asyncReloadTask 호출 시 사용할 executor

    private File preparedDestJarBaseDirectory;
    private volatile ContextLoadImage currentActive = null;
    private volatile DirectoriesSnapshot lastDirectoriesSnapshot = null;
    private Stack<ContextLoadImage> history = new Stack<ContextLoadImage>();
    private volatile boolean firstLoad = true;

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public boolean isEnableAutoReload() {
        return enableAutoReload;
    }

    public void setEnableAutoReload(boolean enableAutoReload) {
        this.enableAutoReload = enableAutoReload;
    }

    public int getMonitorIntervalSecs() {
        return monitorIntervalSecs;
    }

    public void setMonitorIntervalSecs(int monitorIntervalSecs) {
        this.monitorIntervalSecs = monitorIntervalSecs;
    }

    public boolean isAutoSwitchAfterReload() {
        return autoSwitchAfterReload;
    }

    public void setAutoSwitchAfterReload(boolean autoSwitchAfterReload) {
        this.autoSwitchAfterReload = autoSwitchAfterReload;
    }

    public boolean isAutoSwitchSuccessOnly() {
        return autoSwitchSuccessOnly;
    }

    public void setAutoSwitchSuccessOnly(boolean autoSwitchSuccessOnly) {
        this.autoSwitchSuccessOnly = autoSwitchSuccessOnly;
    }

    public int getMaxSuccessImagesInMemory() {
        return maxSuccessImagesInMemory;
    }

    public void setMaxSuccessImagesInMemory(int maxSuccessImagesInMemory) {
        this.maxSuccessImagesInMemory = maxSuccessImagesInMemory;
    }

    public File getSrcJarDirectory() {
        return srcJarDirectory;
    }

    public void setSrcJarDirectory(File srcJarDirectory) {
        this.srcJarDirectory = srcJarDirectory;
    }

    public File getDestJarBaseDirectory() {
        return destJarBaseDirectory;
    }

    public void setDestJarBaseDirectory(File destJarBaseDirectory) {
        this.destJarBaseDirectory = destJarBaseDirectory;
    }

    public int getMaxFileCopyRetryCount() {
        return maxFileCopyRetryCount;
    }

    public void setMaxFileCopyRetryCount(int maxFileCopyRetryCount) {
        this.maxFileCopyRetryCount = maxFileCopyRetryCount;
    }

    public int getFileCopyRetryIntervalMilis() {
        return fileCopyRetryIntervalMilis;
    }

    public void setFileCopyRetryIntervalMilis(int fileCopyRetryIntervalMilis) {
        this.fileCopyRetryIntervalMilis = fileCopyRetryIntervalMilis;
    }

    public ContextLoader getLoader() {
        return loader;
    }

    public void setLoader(ContextLoader loader) {
        this.loader = loader;
    }

    public ExecutorService getAsyncReloadExecutorService() {
        return asyncReloadExecutorService;
    }

    public void setAsyncReloadExecutorService(ExecutorService asyncReloadExecutorService) {
        this.asyncReloadExecutorService = asyncReloadExecutorService;
    }

    public ContextLoadImage getCurrentActive() {
        return this.currentActive;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        prepareDestJarBaseDirectory();

        this.loader.registerListener(this);

        this.asyncReloadExecutorService = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = Executors.defaultThreadFactory().newThread(r);
                t.setDaemon(true);
                return t;
            }
        });

        // autoReload 활성화 시 jar 변경감시 개시
        if(enableAutoReload) {
            this.monitorThread = new Thread(new JarDirChangeMonitor());
            this.monitorThread.setDaemon(true);
            this.monitorThread.setName(
                    "DefaultMultiVersionContextContainer.MonitoringThread." +
                            new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
            );
            this.monitorThread.start();
            logger.info("{} has started.", monitorThread.getName());
            if(logger.isDebugEnabled()) {
                logger.debug("{} will check changes on every {} seconds.", monitorThread.getName(), this.monitorIntervalSecs);
            }
        } else {
            logger.info("Auto-reloading is off.");
        }
    }

    private ContextLoadImage loadImmediatelyAfterCopy() {
        Map<String, Object> loadAttributes = new HashMap<String, Object>();
        DirectoriesSnapshot orginalDirectoriesSnapshot = new DirectoriesSnapshot(srcJarDirectory);
        loadAttributes.put(LOAD_ATTR_DIRECTORY_SNAPSHOT, orginalDirectoriesSnapshot);

        // Load ID의 결정
        String loadId = readLoadIdFromDir(this.srcJarDirectory);
        if(loadId == null) {
            loadId = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
                    + "_"
                    + orginalDirectoriesSnapshot.getChecksum();
        }
        // JAR 파일 복사
        File[] destJarFiles;
        try {
            destJarFiles = copyAllJarFiles(loadId, this.srcJarDirectory);
        } catch(IOException ioe) {
            throw new IllegalStateException("Can't copy jar files.", ioe);
        }

        // LOAD
        ContextLoadImage image = this.loader.load(loadId, destJarFiles, loadAttributes);

        // 로드결과 리턴
        return image;
    }

    private String readLoadIdFromDir(File dir) {
        File[] files = dir.listFiles(LOAD_DIRECTIVE_FILENAME_FILTER);
        if(files != null && files.length > 0) {
            for(File loadDirectiveFile : files) {
                FileInputStream fis = null;
                BufferedReader br = null;
                try {
                    fis = new FileInputStream(loadDirectiveFile);
                    br = new BufferedReader(new InputStreamReader(fis));
                    String line = null;
                    while((line = br.readLine()) != null) {
                        // #으로 시작하지 않는 첫번째 행을 trim 해서 load id로 반환
                        if(line.startsWith("#")) continue;
                        else return line.trim();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if(br != null) {
                        try { br.close(); } catch(IOException e) { e.printStackTrace(System.err);}
                    }
                    if(fis != null) {
                        try { fis.close(); } catch(IOException e) { e.printStackTrace(System.err);}
                    }
                }
            }
        }

        return null;
    }

    @Override
    public void destroy() throws Exception {
        logger.debug("destory()");
        asyncReloadExecutorService.shutdownNow();

        if(this.monitorThread != null) {
            this.monitorThread.interrupt();
        }
    }

    @Override
    public Future<ContextLoadImage> startLoad() {
        AsyncReloadTask asyncReloadTask = new AsyncReloadTask();
        Future<ContextLoadImage> future = asyncReloadExecutorService.submit(asyncReloadTask);
        return future;
    }

    @Override
    public void loadFinished(ContextLoadImage image) {
        appendNew(image);
    }

    @Override
    public ContextLoadImage current() {
        return currentActive;
    }

    @Override
    public ContextLoadImage switchToLatestSuccess() throws IllegalStateException {
        synchronized (history) {
            while(!history.empty()) {
                ContextLoadImage image = history.peek();
                if(image.isSuccess() && !image.isContextClosed()) {
                    changeCurrent(image);
                    return image;
                }
            }
        }
        throw new IllegalStateException("No success image, or no context available");
    }

    private void changeCurrent(ContextLoadImage image) {
        if(listenerList != null) {
            for(MultiVersionContextContainerListener listener : listenerList) {
                listener.beforeActivation(image);
            }
        }

        this.currentActive = image;
    }

    @Override
    public ContextLoadImage forceSwitchTo(int index) {
        synchronized (history) {
            ContextLoadImage image = history.elementAt(index);
            if(image.isContextClosed()) {
                throw new IllegalArgumentException("Success image, but the context inside has been already closed for freeing memory. image=" + image);
            }
            changeCurrent(image);
            return image;
        }
    }

    @Override
    public ContextLoadImage removeFromHistory(int index) {
        synchronized (history) {
            ContextLoadImage image = history.remove(index);
            if(image.isSuccess() && !image.isContextClosed()) {
                image.closeContext();
            }

            return image;
        }
    }

    @Override
    public int size() {
        synchronized (history) {
            return history.size();
        }
    }

    @Override
    public List<ContextLoadImage> getHistoryCopy() {
        List<ContextLoadImage> immutableHistory = new ArrayList<ContextLoadImage>();
        immutableHistory.addAll(this.history);
        return immutableHistory;
    }

    /**
     * 로딩 이미지 추가
     * @param newImage
     */
    private void appendNew(ContextLoadImage newImage) {
        synchronized (history) {
            this.lastDirectoriesSnapshot = (DirectoriesSnapshot)newImage.getLoadAttributes().get(LOAD_ATTR_DIRECTORY_SNAPSHOT);

            this.history.push(newImage);

            if(firstLoad) {
                changeCurrent(newImage);
                this.firstLoad = false;
                return;
            }

            // max를 넘지 않도록 기존 image의 context를 close한다(메모리 free 목적)
            freeExceedingOldContext();

            if(autoSwitchAfterReload) {
                if(newImage.isSuccess()) {
                    changeCurrent(newImage);
                } else if(!newImage.isSuccess() && !autoSwitchSuccessOnly) {
                    changeCurrent(newImage);
                    logger.warn("Forced applying failed image={}, because activeSuccessOnly is false.", newImage.getId());
                }
            }
        }
    }

    private void freeExceedingOldContext() {
        int successImageCount = 0;
        for(int i = this.history.size() - 1; i >= 0; i--) {
            ContextLoadImage image = this.history.get(i);
            if(image.isSuccess()) {
                successImageCount++;
                if(successImageCount > this.maxSuccessImagesInMemory) {
                    if(logger.isInfoEnabled()) {
                        logger.info("Closing {}. maxSuccessImagesInMemory={} has been exceeded.", image, this.maxSuccessImagesInMemory);
                    }
                    image.closeContext();
                    break;  // 1개만 지우면 됨.
                }
            }
        }
    }

    @Override
    public void addListener(MultiVersionContextContainerListener listener) {
        listenerList.add(listener);
    }

    private class AsyncReloadTask implements Callable<ContextLoadImage> {
        @Override
        public ContextLoadImage call() throws Exception {
            ContextLoadImage image = loadImmediatelyAfterCopy();
            return image;
        }
    }

    private class JarDirChangeMonitor implements Runnable {
        @Override
        public void run() {
            try {
                long monitorIntervalMilis = monitorIntervalSecs * 1000;
                while(true) {
                    Thread.sleep(monitorIntervalMilis);
                    if(lastDirectoriesSnapshot != null) {
                        DirectoriesSnapshot newDirectorysSnapshot = new DirectoriesSnapshot(srcJarDirectory);
                        boolean hasChanged = lastDirectoriesSnapshot.checkChanges(newDirectorysSnapshot);
                        if(!hasChanged) {
                            continue;
                        }

                        if(!Thread.currentThread().isInterrupted()) {
                            logger.debug("Detected changes of directory entries.");
                            loadImmediatelyAfterCopy();
                        }
                    }
                }
            } catch(InterruptedException e) {
                logger.info("{} has been interruppted internally. Terminating chage-monitoring.");
            } catch(NullPointerException e) {
                try {
                    logger.info("Monitor thread has been forced into termination. Possible resons are, "
                            + "1) Server reloaded the war, so current log4j is terminated."
                            + "2) Others. "
                            + "Exception message is {}"
                            , e.getMessage()
                    );
                } catch(Exception e2) {
                    // log4j가 중지되면 info 내에서 NullPointerException이 발생한다.
                    System.out.println(Thread.currentThread() + " has been forced into termination. Possible reasons are, "
                            + "1) Server reloaded the war, so current log4j is terminated."
                            + "2) Others. "
                            + "If following stack trace is about log4j, it's not a problem, but normal."
                    );
                    e2.printStackTrace(System.err);
                }
            }
        }
    }

    private File[] copyAllJarFiles(String loadId, File jarDirectory) throws IOException {
        Assert.notNull(jarDirectory, "jarDirectories must not be null.");
        List<File> srcJarFileList = new ArrayList<File>();
        File[] jars = jarDirectory.listFiles(new JarFilenameFilter());
        if(jars != null && jars.length > 0) {
            for(File jar : jars) {
                srcJarFileList.add(jar);
            }
        }

        String destDirName = loadId;
        File destFileDirectory = new File(this.destJarBaseDirectory, destDirName);

        List<File> destJarFileList = new ArrayList<File>();
        FileUtil.createDirectoryIfNotPresent(destFileDirectory);
        for(File jarFile : srcJarFileList) {
            String filename = jarFile.getName();
            File destFile = new File(destFileDirectory, filename);
            copyFileSafely(jarFile, destFile);
            destJarFileList.add(destFile);
        }

        return destJarFileList.toArray(new File[srcJarFileList.size()]);
    }

    private void prepareDestJarBaseDirectory() throws IOException {
        this.preparedDestJarBaseDirectory = this.destJarBaseDirectory;
        if(this.preparedDestJarBaseDirectory == null) {
            this.preparedDestJarBaseDirectory = new File(
                    System.getProperty("java.io.tmpdir") + File.separator + "loads"
            );
        }

        FileUtil.createDirectoryIfNotPresent(this.preparedDestJarBaseDirectory);
    }

    private void copyFileSafely(File srcFile, File destFile) {
        // FTP 업로드가 완료되지 않은 상태에서 여기까지 오는 경우가 있음.
        // 이 경우 file copy가 실패하므로, 약간의 delay 후에 retry 한다.
        // 성공하면 그 즉시 break for
        IOException fileCopyException = null;

        for(int i = 0; i < maxFileCopyRetryCount; i++) {
            if(i > 0) {
                // RETRY 상태(i>0)인 경우 로그 및 sleep...
                logger.debug("[{}] Retrying file-copy after {} ms......", (i+1), fileCopyRetryIntervalMilis);
                try {
                    Thread.sleep(fileCopyRetryIntervalMilis);
                } catch(InterruptedException ie) {
                    throw new RuntimeException("This thread has been interrupted!", ie);
                }
            }

            try {
                // 복사 시도해서 성공하면 break
                FileCopyUtils.copy(srcFile, destFile);
                fileCopyException = null;
                break;  // break for
            } catch(IOException ioe) {
                // 예외발생... File upload가 끝나지 않은 경우가 대부분
                fileCopyException = ioe;
                logger.debug("FAILED copying [{}] to [{}]. This may be because of file-uploading is underway.", srcFile.getAbsolutePath(), destFile.getAbsolutePath());
            }
        }

        // 예외발생여부 확인
        if(fileCopyException != null) {
            throw new IllegalArgumentException(
                    "Failed to copy srcFile=[" + srcFile.getAbsolutePath() + "] to [" + destFile.getAbsolutePath() + "]",
                    fileCopyException
            );
        }
    }

    private List<MultiVersionContextContainerListener> listenerList = new ArrayList<MultiVersionContextContainerListener>();

    private static final FilenameFilter LOAD_DIRECTIVE_FILENAME_FILTER = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
            return name.equals(LOAD_DIRECTIVE_FILENAME);
        }
    };
}
