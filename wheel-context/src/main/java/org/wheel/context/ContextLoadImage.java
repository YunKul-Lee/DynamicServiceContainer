package org.wheel.context;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;

public class ContextLoadImage {

    // load id
    private final String id;

    // load performance
    private Date loadStartTime;
    private Date loadEndTime;

    // load result
    private volatile ApplicationContext applicationContext;
    private final Throwable failure;

    // additional info
    private final Map<String, Object> loadAttributes;

    public static ContextLoadImage withSuccess(
            String id,
            Date loadStartTime,
            Date loadEndTime,
            ApplicationContext applicationContext,
            Map<String, Object> loadAttributes
    ) {
        ContextLoadImage loadImage = new ContextLoadImage(
                id,
                loadStartTime,
                loadEndTime,
                applicationContext,
                null,
                loadAttributes
        );

        return loadImage;
    }

    public static ContextLoadImage withFailure(
            String id,
            Date loadStartTime,
            Date loadEndTime,
            Throwable failure,
            Map<String, Object> loadAttributes
    ) {
        ContextLoadImage loadImage = new ContextLoadImage(
                id,
                loadStartTime,
                loadEndTime,
                null,
                failure,
                loadAttributes
        );
    }

    private ContextLoadImage(
            String id,
            Date loadStartTime,
            Date loadEndTime,
            ApplicationContext applicationContext,
            Throwable failure,
            Map<String, Object> loadAttributes) {
        super();
        this.id = id;
        this.loadStartTime = loadStartTime;
        this.loadEndTime = loadEndTime;
        this.applicationContext = applicationContext;
        this.failure = failure;
        this.loadAttributes = loadAttributes;
    }

    public String getId() {
        return id;
    }

    public Date getLoadStartTime() {
        return loadStartTime;
    }

    public void setLoadStartTime(Date loadStartTime) {
        this.loadStartTime = loadStartTime;
    }

    public Date getLoadEndTime() {
        return loadEndTime;
    }

    public void setLoadEndTime(Date loadEndTime) {
        this.loadEndTime = loadEndTime;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public Map<String, Object> getLoadAttributes() {
        return loadAttributes;
    }

    public Throwable getFailure() {
        return failure;
    }

    public boolean isSuccess() {
        return (failure == null);
    }

    public synchronized void closeContext() {
        if(!isContextClosed()) {
            ApplicationContext toBeDestroyed = this.applicationContext;
            this.applicationContext = null;
            try {
                ((DisposableBean)toBeDestroyed).destroy();
            } catch(ClassCastException cce) {
                try {
                    ((AbstractApplicationContext)toBeDestroyed).close();
                } catch(ClassCastException cce2) {
                    cce2.printStackTrace();
                }
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    private synchronized boolean isContextClosed() {
        return isSuccess() && this.applicationContext == null;
    }

    @Override
    public String toString() {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return "ApplicationContextLoadImage [" +
                "id='" + id + '\'' +
                ", loadStartTime=" + df.format(loadStartTime) +
                ", loadEndTime=" + df.format(loadEndTime) +
                ", applicationContext=" + applicationContext +
                ", failure=" + failure +
                ", loadAttributes=" + loadAttributes + "]";
    }
}
