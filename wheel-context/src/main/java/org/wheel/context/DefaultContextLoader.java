package org.wheel.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.support.AbstractRefreshableConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.Assert;
import org.springframework.util.SystemPropertyUtils;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Date;
import java.util.Map;

public class DefaultContextLoader
        implements ContextLoader, ApplicationContextAware, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(DefaultContextLoader.class);

    private ContextLoaderListener listener;

    public static final String DEFAULT_CONFIG_LOCATION = "classpath*:wheelApplicationContext.xml";
    public static final Class<?> DEFAULT_CONTEXT_CLASS = ClassPathXmlApplicationContext.class;

    private ApplicationContext thisApplicationContext;

    private String[] configLocations = new String[] { DEFAULT_CONFIG_LOCATION };
    private Class<?> contextClass = DEFAULT_CONTEXT_CLASS;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.thisApplicationContext = applicationContext;
    }

    @Override
    public void registerListener(ContextLoaderListener listener) {
        this.listener = listener;
    }

    public Class<?> getContextClass() {
        return contextClass;
    }

    public void setContextClass(Class<?> contextClass) {
        this.contextClass = contextClass;
    }

    public String[] getConfigLocations() {
        return this.configLocations;
    }

    public void setConfigLocations(String[] locations) {
        if(locations != null) {
            Assert.noNullElements(locations,
                    "Config locations must not be null");
            this.configLocations =  new String[locations.length];
            for(int i=0; i < locations.length; i++) {
                this.configLocations[i] = resolvePath(locations[i]).trim();
            }
        } else {
            logger.debug("configLocations is not specified, default value({}) will be used.", DEFAULT_CONFIG_LOCATION);
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {

    }

    @Override
    public ContextLoadImage load(String loadId, File[] jarFiles, Map<String, Object> loadAttributes) {
        ContextLoadImage resultImage;
        Date loadStartTime = new Date();

        try {
            if(logger.isDebugEnabled()) {
                logger.debug("====================================================================");
                logger.debug("LOAD START with loadId={}", loadId);
                logger.debug("====================================================================");
                for(int i=0; i < jarFiles.length; i++) {
                    logger.debug("jarFile[{}]={}", i, jarFiles[i].getAbsoluteFile());
                }
            }

            if(!AbstractRefreshableConfigApplicationContext.class.isAssignableFrom(getContextClass())) {
                throw new ApplicationContextException(
                        "Fatal initialization error" +
                        ": custom ApplicationContext class [" + getContextClass().getName() +
                        "] is not of type AbstractRefreshableConfigApplicationContext");
            }

            AbstractRefreshableConfigApplicationContext newContext =
                    (AbstractRefreshableConfigApplicationContext) BeanUtils.instantiateClass(getContextClass());

            newContext.setParent(thisApplicationContext);
            newContext.setConfigLocations(getConfigLocations());
            newContext.setDisplayName("VirtualWheelContext-" + loadId);

            WheelClassLoader subClassLoader = new WheelClassLoader(
                    jarFiles,
                    Thread.currentThread().getContextClassLoader());
            newContext.setClassLoader(subClassLoader);

            newContext.refresh();

            Date loadEndTime = new Date();
            loadAttributes.put("loadEndTime", loadEndTime);
            logger.info("Succeeded to load with loadAttributes={}.", loadAttributes);

            resultImage = ContextLoadImage.withSuccess(
                    loadId,
                    loadStartTime,
                    loadEndTime,
                    newContext,
                    loadAttributes
            );

            if(logger.isDebugEnabled()) {
                logger.debug("====================================================================");
                logger.debug("LOAD SUCCESS");
                logger.debug("====================================================================");
            }

        } catch (Throwable t) {
            Date loadEndTime = new Date();
            loadAttributes.put("loadEndTime", loadEndTime);
            logger.warn("Failed to load with loadAttributes={}.", loadAttributes, t);

            if(logger.isDebugEnabled()) {
                logger.debug("====================================================================");
                logger.debug("LOAD FAILURE");
                logger.debug("====================================================================");
            }

            resultImage = ContextLoadImage.withFailure(
                    loadId,
                    loadStartTime,
                    loadEndTime,
                    t,
                    loadAttributes
            );
        }

        if(listener != null) {
            listener.loadFinished(resultImage);
        }

        return resultImage;
    }

    protected  ApplicationContext createApplicationContext(ApplicationContext parent) throws BeansException {

        if(logger.isDebugEnabled()) {
            logger.debug("Will try to create custom ApplicationContext context of class '" +
                    getContextClass().getName() + "'" + ", using parent context [" + parent + "]");
        }
        if(!AbstractRefreshableConfigApplicationContext.class.isAssignableFrom(getContextClass())) {
            throw new ApplicationContextException(
                "Fatal initialization error" +
                ": custom ApplicationContext class [" + getContextClass().getName() +
                "] is not of type AbstractRefreshableConfigApplicationContext");
        }

        AbstractRefreshableConfigApplicationContext arcac =
                (AbstractRefreshableConfigApplicationContext) BeanUtils.instantiateClass(getContextClass());
        arcac.setParent(parent);
        arcac.setConfigLocations(getConfigLocations());

        postProcessWebApplicationContext(arcac);
        arcac.refresh();

        return arcac;
    }

    protected void postProcessWebApplicationContext(AbstractRefreshableConfigApplicationContext wac) {
    }

    protected String resolvePath(String path) {
        return SystemPropertyUtils.resolvePlaceholders(path);
    }

    private static class WheelClassLoader extends URLClassLoader {

        private File[] jarFiles;

        public WheelClassLoader(File[] jarFiles, ClassLoader parent) {
            super(urlsOf(jarFiles), parent);
            this.jarFiles = jarFiles;
        }

        /**
         * buildUrlsWithFilePaths
         * @param   files
         */
        static private URL[] urlsOf(File[] files) {
            URL[] urls = new URL[files.length];

            try {
                for (int i=0; i < files.length; i++) {
                    urls[i] = new FileSystemResource(files[i]).getURL();
                }
            } catch(MalformedURLException e) {
                // can not happen
                throw new IllegalArgumentException(e);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return urls;
        }
    }
}
