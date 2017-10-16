package org.wheel.web.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.wheel.context.ContextLoadImage;
import org.wheel.context.MultiVersionContextContainer;

import javax.servlet.ServletContext;
import java.util.List;

public class DelegatingWebApplicationContext extends AbstractApplicationContext implements WebApplicationContext {

    private static final Logger logger = LoggerFactory.getLogger(DelegatingWebApplicationContext.class);

    private final ServletContext servletContext;
    private final MultiVersionContextContainer mvcc;

    private final ConfigurableListableBeanFactory fallbackBeanFactory = new DefaultListableBeanFactory();

    public DelegatingWebApplicationContext(WebApplicationContext parent, ServletContext servletContext, MultiVersionContextContainer mvcc) {
        super(parent);
        this.servletContext = servletContext;
        this.mvcc = mvcc;
    }

    @Override
    public ServletContext getServletContext() {
        return servletContext;
    }

    @Override
    protected void closeBeanFactory() {
        // This class does not have any bean factory.
        // Thus, no implementation here.
    }

    @Override
    public ConfigurableListableBeanFactory getBeanFactory() throws IllegalStateException {

        try {
            return getDelegateApplicationContext().getBeanFactory();
        } catch(IllegalStateException ise) {
            return this.fallbackBeanFactory;
        }
    }

    @Override
    protected void refreshBeanFactory() throws BeansException, IllegalStateException {
        // This class does not have any bean factory.
        // Thus, no implementation here.
    }

    @Override
    public void close() {
        super.close();

        // MVCC의 전체 history image를 모두 close 한다.
        List<ContextLoadImage> history = mvcc.getHistoryCopy();
        for(ContextLoadImage image : history) {
            try{
                image.closeContext();
                if(logger.isInfoEnabled()) {
                    logger.info("Closed context of image=" + image);
                }
            } catch(Exception e) {
                logger.warn("Exception occured during close image=" + image, e);
            }
        }
    }

    private AbstractApplicationContext getDelegateApplicationContext() {
        ContextLoadImage activeImage = this.mvcc.current();
        if(!activeImage.isSuccess()) {
            throw new IllegalStateException("Insane context.", activeImage.getFailure());
        }
        return (AbstractApplicationContext)activeImage.getApplicationContext();
    }
}
