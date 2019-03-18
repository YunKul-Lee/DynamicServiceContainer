package org.wheel.web.servlet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.JdkVersion;
import org.springframework.core.OrderComparator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.ui.context.ThemeSource;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.servlet.*;
import org.springframework.web.util.NestedServletException;
import org.springframework.web.util.UrlPathHelper;
import org.springframework.web.util.WebUtils;
import org.wheel.context.ContextLoadImage;
import org.wheel.context.MultiVersionContextContainer;
import org.wheel.context.MultiVersionContextContainerListener;
import org.wheel.web.context.DelegatingWebApplicationContext;

import javax.naming.Context;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.swing.text.html.HTMLDocument;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class WheelDispatcherServlet extends FrameworkServlet implements MultiVersionContextContainerListener {


    public static final String MULTIPART_RESOLVER_BEAN_NAME = "multipartResolver";

    public static final String LOCALE_RESOLVER_BEAN_NAME = "localeResolver";

    public static final String THEME_RESOLVER_BEAN_NAME = "themeResolver";

    public static final String HANDLER_MAPPING_BEAN_NAME = "handlerMapping";

    public static final String HANDLER_ADAPTER_BEAN_NAME = "handlerAdapter";

    public static final String HANDLER_EXCEPTION_RESOLVER_BEAN_NAME = "handlerExceptionResolver";

    public static final String REQUEST_TO_VIEW_NAME_TRANSLATOR_BEAN_NAME = "viewNameTranslator";

    public static final String VIEW_RESOLVER_BEAN_NAME = "viewResolver";

    public static final String HANDLER_EXECUTION_CHAIN_ATTRIBUTE = DispatcherServlet.class.getName() + ".HANDLER";

    public static final String WEB_APPLICATION_CONTEXT_ATTRIBUTE = DispatcherServlet.class.getName() + ".CONTEXT";

    public static final String LOCALE_RESOLVER_ATTRIBUTE = DispatcherServlet.class.getName() + ".LOCALE_RESOLVER";

    public static final String THEME_RESOLVER_ATTRIBUTE = DispatcherServlet.class.getName() + ".THEME_RESOLVER";

    public static final String THEME_SOURCE_ATTRIBUTE = DispatcherServlet.class.getName() + ".THEME_SOURCE";

    public static final String PAGE_NOT_FOUND_LOG_CATEGORY = "org.springframework.web.servlet.PageNotFound";

    private static final String DEFAULT_STRATEGIES_PATH = "DispatcherServlet.properties";

    protected static final Log pageNotFoundLogger = LogFactory.getLog(PAGE_NOT_FOUND_LOG_CATEGORY);

    private static final Properties defaultStrategies;

    static {
        try {
            ClassPathResource resource = new ClassPathResource(DEFAULT_STRATEGIES_PATH, DispatcherServlet.class);
            defaultStrategies = PropertiesLoaderUtils.loadProperties(resource);
        } catch(IOException ex) {
            throw new IllegalStateException("Could not load 'DispatcherServlet.properties': " + ex.getMessage());
        }
    }

    private boolean detectAllHandlerMappings = true;

    private boolean detectAllHandlerAdapters = true;

    private boolean detectAllHandlerExceptionResolvers = true;

    private boolean detectAllViewResolvers = true;

    private boolean cleanupAfterInclude = true;

    private boolean threadContextInheritable = false;

    private volatile Strategies complexContextStrategies;

    public void setDetectAllHandlerMappings(boolean detectAllHandlerMappings) {
        this.detectAllHandlerMappings = detectAllHandlerMappings;
    }

    public void setDetectAllHandlerAdapters(boolean detectAllHandlerAdapters) {
        this.detectAllHandlerAdapters = detectAllHandlerAdapters;
    }

    public void setDetectAllHandlerExceptionResolvers(boolean detectAllHandlerExceptionResolvers) {
        this.detectAllHandlerExceptionResolvers = detectAllHandlerExceptionResolvers;
    }

    public void setDetectAllViewResolvers(boolean detectAllViewResolvers) {
        this.detectAllViewResolvers = detectAllViewResolvers;
    }

    public void setCleanupAfterInclude(boolean cleanupAfterInclude) {
        this.cleanupAfterInclude = cleanupAfterInclude;
    }

    public void setThreadContextInheritable(boolean threadContextInheritable) {
        this.threadContextInheritable = threadContextInheritable;
    }

    @Override
    protected WebApplicationContext initWebApplicationContext() throws BeansException {
        WebApplicationContext originalWac = super.initWebApplicationContext();
        String[] names = originalWac.getBeanNamesForType(MultiVersionContextContainer.class);

        if(names.length != 1) {
            throw new NoSuchBeanDefinitionException(
                    "Exactly 1 instance of " + MultiVersionContextContainer.class.getName() + " must exist, there is(are) " + names.length + " instance(s)."
            );
        }

        // Only 1 instance
        MultiVersionContextContainer mvcc = (MultiVersionContextContainer)originalWac.getBean(names[0]);

        // Create delegating wac
        DelegatingWebApplicationContext delegatingWac = new DelegatingWebApplicationContext(originalWac, getServletContext(), mvcc);

        // Register listener
        mvcc.addListener(this);

        // Start
        Future<ContextLoadImage> imageFuture = mvcc.startLoad();

        try {
            if(logger.isDebugEnabled()) {
                logger.debug("Started first-loading Wheel Application Context and waiting for finishing...");
            }
            imageFuture.get();
        }
        catch(InterruptedException e) {
            logger.error("Interrupted.", e);
        }
        catch(ExecutionException e) {
            logger.error("Loading failed.", e);
        }

        // START Wrapper
        try {
            //delegatingWac.refresh();
        	//delegatingWac.refresh();
            logger.info("wrapper started.");
        }
        catch(IllegalStateException e) {
            logger.warn("refresh() failed.", e);
        }

        return delegatingWac;
    }

    protected void refreshStrategies(ContextLoadImage image) {
        if(logger.isDebugEnabled()) {
            logger.debug("initStrategies() with " + image.getApplicationContext());
        }

        Strategies newStrategies = new Strategies();
        newStrategies.latestImage = image;

        if(!image.isSuccess()) {
            logger.error("Wheel ApplicationContext is not capable for service. Fix the problem and reload.", image.getFailure());
            this.complexContextStrategies = newStrategies;
            return;
        }

        ApplicationContext context = image.getApplicationContext();

        initMultipartResolver(newStrategies, context);
        initLocaleResolver(newStrategies, context);
        initThemeResolver(newStrategies, context);
        initHandlerMappings(newStrategies, context);
        initHandlerAdapters(newStrategies, context);
        initHandlerExceptionResolvers(newStrategies, context);
        initRequestToViewNameTranslator(newStrategies, context);
        initViewResolvers(newStrategies, context);

        this.complexContextStrategies = newStrategies;
    }

    private void initMultipartResolver(Strategies newStrategies, ApplicationContext context) {
        try {
            newStrategies.multipartResolver = (MultipartResolver)context.getBean(MULTIPART_RESOLVER_BEAN_NAME, MultipartResolver.class);

            if(logger.isDebugEnabled()) {
                logger.debug("Using MultipartResolver [" + newStrategies.multipartResolver + "]");
            }
        } catch(NoSuchBeanDefinitionException ex) {
            newStrategies.multipartResolver = null;
            if(logger.isDebugEnabled()) {
                logger.debug("Unable to locate MultipartResolver with name '" + MULTIPART_RESOLVER_BEAN_NAME + "': no multipart request handling provided");
            }
        }
    }

    private void initLocaleResolver(Strategies newStrategies, ApplicationContext context) {
        try {
            newStrategies.localeResolver = (LocaleResolver) context.getBean(LOCALE_RESOLVER_BEAN_NAME, LocaleResolver.class);

            if(logger.isDebugEnabled()) {
                logger.debug("Using LocaleResolver [" + newStrategies.localeResolver + "]");
            }
        } catch(NoSuchBeanDefinitionException ex) {
            newStrategies.localeResolver = (LocaleResolver) getDefaultStrategy(context, LocaleResolver.class);
            if(logger.isDebugEnabled()) {
                logger.debug("Unable to locate LocaleResolver with name '" + LOCALE_RESOLVER_BEAN_NAME + "': using default [" + newStrategies.localeResolver + "]");
            }
        }
    }

    private void initThemeResolver(Strategies newStrategies, ApplicationContext context) {
        try {
            newStrategies.themeResolver = (ThemeResolver) context.getBean(THEME_RESOLVER_BEAN_NAME, ThemeResolver.class);

            if(logger.isDebugEnabled()) {
                logger.debug("Using ThemeResolver [" + newStrategies.themeResolver + "]");
            }
        } catch(NoSuchBeanDefinitionException ex) {
            newStrategies.themeResolver = (ThemeResolver) getDefaultStrategy(context, ThemeResolver.class);
        }
    }

    private void initHandlerMappings(Strategies newStrategies, ApplicationContext context) {
        newStrategies.handlerMappings = null;

        if(this.detectAllHandlerMappings) {
            Map matchingBeans = BeanFactoryUtils.beansOfTypeIncludingAncestors(context, HandlerMapping.class, true, false);

            if(!matchingBeans.isEmpty()) {
                newStrategies.handlerMappings = new ArrayList(matchingBeans.values());

                Collections.sort(newStrategies.handlerMappings, new OrderComparator());
            }
        } else {
            try {
                Object hm = context.getBean(HANDLER_MAPPING_BEAN_NAME, HandlerMapping.class);
                newStrategies.handlerMappings = Collections.singletonList(hm);
            } catch(NoSuchBeanDefinitionException ex) {
                // Ignore
            }
        }

        if(newStrategies.handlerMappings == null) {
            newStrategies.handlerMappings = getDefaultStrategies(context, HandlerMapping.class);
            if(logger.isDebugEnabled()) {
                logger.debug("No HandlerMappings found in servlet '" + getServletName() + "': using default");
            }
        }

        if(logger.isDebugEnabled()) {
            logger.debug("handlerMappings=" + newStrategies.handlerMappings.toString());
        }
    }

    private void initHandlerAdapters(Strategies newStrategies, ApplicationContext context) {
        newStrategies.handlerAdapters = null;

        if(this.detectAllHandlerAdapters) {
            Map matchingBeans = BeanFactoryUtils.beansOfTypeIncludingAncestors(context, HandlerAdapter.class, true, false);

            if(!matchingBeans.isEmpty()) {
                newStrategies.handlerAdapters = new ArrayList(matchingBeans.values());
                Collections.sort(newStrategies.handlerAdapters, new OrderComparator());
            }
        } else {
            try {
                Object ha = context.getBean(HANDLER_ADAPTER_BEAN_NAME, HandlerAdapter.class);
                newStrategies.handlerAdapters = Collections.singletonList(ha);
            } catch(NoSuchBeanDefinitionException ex) {
                // Ignore
            }
        }

        if(newStrategies.handlerAdapters == null) {
            newStrategies.handlerAdapters = getDefaultStrategies(context, HandlerAdapter.class);
            if(logger.isDebugEnabled()) {
                logger.debug("No HandlerAdapters found in servlet '" + getServletName() + "': using default");
            }
        }
    }

    private void initHandlerExceptionResolvers(Strategies newStrategies, ApplicationContext context) {
        newStrategies.handlerExceptionResolvers = null;

        if(this.detectAllHandlerExceptionResolvers) {
            Map matchingBeans = BeanFactoryUtils.beansOfTypeIncludingAncestors(context, HandlerExceptionResolver.class, true, false);

            if(!matchingBeans.isEmpty()) {
                newStrategies.handlerExceptionResolvers = new ArrayList(matchingBeans.values());

                Collections.sort(newStrategies.handlerExceptionResolvers, new OrderComparator());
            }
        } else {
            try {
                Object her = context.getBean(HANDLER_EXCEPTION_RESOLVER_BEAN_NAME, HandlerExceptionResolver.class);
                newStrategies.handlerExceptionResolvers =  Collections.singletonList(her);
            } catch (NoSuchBeanDefinitionException ex) {
                // Ignore
            }
        }

        if(newStrategies.handlerExceptionResolvers == null) {
            newStrategies.handlerExceptionResolvers = getDefaultStrategies(context, HandlerExceptionResolver.class);
            if(logger.isDebugEnabled()) {
                logger.debug("No HandlerExceptionResolvers found in servlet '" + getServletName() + "': using default");
            }
        }
    }


    private void initRequestToViewNameTranslator(Strategies newStrategies, ApplicationContext context) {
        try {
            newStrategies.viewNameTranslator = (RequestToViewNameTranslator)context.getBean(REQUEST_TO_VIEW_NAME_TRANSLATOR_BEAN_NAME, RequestToViewNameTranslator.class);

            if(logger.isDebugEnabled()) {
                logger.debug("Using RequestToViewNameTranslator [" + newStrategies.viewNameTranslator + "]");
            }
        } catch(NoSuchBeanDefinitionException ex) {
            newStrategies.viewNameTranslator = (RequestToViewNameTranslator) getDefaultStrategy(context, RequestToViewNameTranslator.class);

            if(logger.isDebugEnabled()) {
                logger.debug("Unable to locate RequestToViewNameTranslator with name '" + REQUEST_TO_VIEW_NAME_TRANSLATOR_BEAN_NAME + "': using default [" + newStrategies.viewNameTranslator + "]");
            }
        }
    }

    private void initViewResolvers(Strategies newStrategies, ApplicationContext context) {
        newStrategies.viewResolvers = null;

        if(this.detectAllViewResolvers) {
            Map matchingBeans = BeanFactoryUtils.beansOfTypeIncludingAncestors(context, ViewResolver.class, true, false);
            if(!matchingBeans.isEmpty()) {
                newStrategies.viewResolvers = new ArrayList(matchingBeans.values());
                Collections.sort(newStrategies.viewResolvers, new OrderComparator());
            }
        } else {
            try {
                Object vr = context.getBean(VIEW_RESOLVER_BEAN_NAME, ViewResolver.class);
                newStrategies.viewResolvers = Collections.singletonList(vr);
            } catch(NoSuchBeanDefinitionException ex) {
                // Ignore
            }
        }

        if(newStrategies.viewResolvers == null) {
            newStrategies.viewResolvers = getDefaultStrategies(context, ViewResolver.class);
            if(logger.isDebugEnabled()) {
                logger.debug("No ViewResolvers found in servlet '" + getServletName() + "': using default");
            }
        }
    }

    public final ThemeSource getThemeSource() {
        if(getWebApplicationContext() instanceof ThemeSource) {
            return (ThemeSource) getWebApplicationContext();
        } else {
            return null;
        }
    }

    public final MultipartResolver getMultipartResolver() {
        return requestBoundedStrategies.get().multipartResolver;
    }


    protected Object getDefaultStrategy(ApplicationContext context, Class strategyInterface) throws BeansException {
        List strategies = getDefaultStrategies(context, strategyInterface);
        if(strategies.size() != 1) {
            throw new BeanInitializationException("DispatcherServlet needs exactly 1 strategy for interface [" + strategyInterface.getName() + "]");
        }
        return strategies.get(0);
    }

    protected List getDefaultStrategies(ApplicationContext context, Class strategyInterface) throws BeansException {
        String key = strategyInterface.getName();
        List strategies = null;
        String value = defaultStrategies.getProperty(key);
        if(value != null) {
            String[] classNames = StringUtils.commaDelimitedListToStringArray(value);
            strategies = new ArrayList(classNames.length);

            for(int i=0; i < classNames.length; i++) {
                String className = classNames[i];
                if(JdkVersion.getMajorJavaVersion() < JdkVersion.JAVA_15 && className.indexOf("Annotation") != -1) {
                    // Skip JAVA 5 specific strategies when running on JDK 1.4...
                    continue;
                }

                try {
                    Class clazz = ClassUtils.forName(className, WheelDispatcherServlet.class.getClassLoader());
                    Object strategy = createDefaultStrategy(context, clazz);
                    strategies.add(strategy);
                } catch(ClassNotFoundException ex) {
                    throw new BeanInitializationException("Could not find DispatcherServlet's default strategy class [" + className + "] for interface [" + key + "]", ex );
                } catch(LinkageError err) {
                    throw new BeanInitializationException("Error loading DispatcherServlet's default strategy class [" + className + "] for interface [" + key + "]: problem with class file or dependent class", err);
                }

            }
        } else {
            strategies = Collections.EMPTY_LIST;
        }

        return strategies;
    }

    protected  Object createDefaultStrategy(ApplicationContext context, Class clazz) throws BeansException {
        return context.getAutowireCapableBeanFactory().createBean(clazz);
    }

    @Override
    protected void doService(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if(logger.isDebugEnabled()) {
            String requestUri = new UrlPathHelper().getRequestUri(request);
            logger.debug("DispatcherServlet with name '" + getServletName() + "' processing request for [" + requestUri + "]");
        }

        prepareHttpProcessing();

        try {
            Map attributesSnapshot = null;
            if(WebUtils.isIncludeRequest(request)) {
                logger.debug("Taking snapshot of request attributes vefore include");
                attributesSnapshot = new HashMap();
                Enumeration attrNames = request.getAttributeNames();
                while (attrNames.hasMoreElements()) {
                    String attrName = (String) attrNames.nextElement();
                    if(this.cleanupAfterInclude || attrName.startsWith("org.springframework.web.servlet")) {
                        attributesSnapshot.put(attrName, request.getAttribute(attrName));
                    }
                }
            }

            request.setAttribute(WEB_APPLICATION_CONTEXT_ATTRIBUTE, getWebApplicationContext());
            request.setAttribute(LOCALE_RESOLVER_ATTRIBUTE, complexContextStrategies.localeResolver);
            request.setAttribute(THEME_RESOLVER_ATTRIBUTE, complexContextStrategies.themeResolver);
            request.setAttribute(THEME_SOURCE_ATTRIBUTE, getThemeSource());

            try {
                doDispatch(request, response);
            } finally {
                if(attributesSnapshot != null) {
                    restoreAttributesAfterInclude(request, attributesSnapshot);
                }
            }

        } finally {
            finishHttpProcessing();
        }
    }

    private void prepareHttpProcessing() {
        if(!this.complexContextStrategies.latestImage.isSuccess()) {
            throw new IllegalStateException("Wheel ApplicationContext is not capable for service. Fix the problem and reload.", this.complexContextStrategies.latestImage.getFailure());
        }

        // =====================================================
        // Thread-local SET
        // =====================================================
        requestBoundedStrategies.set(complexContextStrategies);
    }

    private void finishHttpProcessing() {
        requestBoundedStrategies.set(null);
    }

    protected void doDispatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
        HttpServletRequest processedRequest = request;
        HandlerExecutionChain mappedHandler = null;
        int interceptorIndex = -1;

        LocaleContext previousLocaleContext = LocaleContextHolder.getLocaleContext();
        LocaleContextHolder.setLocaleContext(buildLocaleContext(request), this.threadContextInheritable);

        RequestAttributes previousRequestAttributes = RequestContextHolder.getRequestAttributes();
        ServletRequestAttributes requestAttributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(requestAttributes, this.threadContextInheritable);

        if(logger.isTraceEnabled()) {
            logger.trace("Bound request context to thread: " + request);
        }

        try {
            ModelAndView mv = null;
            boolean errorView = false;

            try {
                processedRequest = checkMultipart(request);

                mappedHandler = getHandler(processedRequest, false);
                if(mappedHandler == null || mappedHandler.getHandler() == null) {
                    noHandlerFound(processedRequest, response);
                    return;
                }

                HandlerInterceptor[] interceptors = mappedHandler.getInterceptors();
                if(interceptors != null) {
                    for(int i=0; i < interceptors.length; i++) {
                        HandlerInterceptor interceptor = interceptors[i];
                        if(!interceptor.preHandle(processedRequest, response, mappedHandler.getHandler())) {
                            triggerAfterCompletion(mappedHandler, interceptorIndex, processedRequest, response, null);
                            return;
                        }
                        interceptorIndex = i;
                    }
                }

                HandlerAdapter ha = getHandlerAdapter(mappedHandler.getHandler());
                mv = ha.handle(processedRequest, response, mappedHandler.getHandler());

                if(mv != null && !mv.hasView()) {
                    mv.setViewName(getDefaultViewName(request));
                }

                if(interceptors != null) {
                    for(int i=interceptors.length - 1; i >= 0; i--) {
                        HandlerInterceptor interceptor = interceptors[i];
                        interceptor.postHandle(processedRequest, response, mappedHandler.getHandler(), mv);
                    }
                }
            } catch(ModelAndViewDefiningException ex) {
                logger.debug("ModelAndViewDefiningException encountered", ex);
                mv = ex.getModelAndView();
            } catch(Exception ex) {
                Object handler = (mappedHandler != null ? mappedHandler.getHandler() : null);
                mv = processHandlerException(processedRequest, response, handler, ex);
                errorView = (mv != null);
            }

            if(mv != null && !mv.wasCleared()) {
                render(mv, processedRequest, response);
                if(errorView) {
                    WebUtils.clearErrorRequestAttributes(request);
                }
            } else {
                if(logger.isDebugEnabled()) {
                    logger.debug("Null ModelAndView returned to DispatcherServlet with name '" + getServletName() + "': assuming HandlerAdapter completed request handling");
                }
            }

            triggerAfterCompletion(mappedHandler, interceptorIndex, processedRequest, response, null);
        } catch(Exception ex) {
            triggerAfterCompletion(mappedHandler, interceptorIndex, processedRequest, response, ex);
            throw ex;
        } catch(Error err) {
            ServletException ex = new NestedServletException("Handler processing failed", err);
            triggerAfterCompletion(mappedHandler, interceptorIndex, processedRequest, response, ex);
            throw ex;
        } finally {
            if(processedRequest != request) {
                cleanupMultipart(processedRequest);
            }

            RequestContextHolder.setRequestAttributes(previousRequestAttributes, this.threadContextInheritable);
            LocaleContextHolder.setLocaleContext(previousLocaleContext, this.threadContextInheritable);

            requestAttributes.requestCompleted();
            if(logger.isTraceEnabled()) {
                logger.trace("Cleared thread-bound request context: " + request);
            }
        }
    }

    protected long getLastModified(HttpServletRequest request) {
        if(logger.isDebugEnabled()) {
            String requestUri = new UrlPathHelper().getRequestUri(request);
            logger.debug("DispatcherServlet with name '" + getServletName() + "' determining Last-Modified value for [" + requestUri + "]");
        }

        prepareHttpProcessing();

        try {
            HandlerExecutionChain mappedHandler = getHandler(request, true);
            if(mappedHandler == null || mappedHandler.getHandler() == null) {
                logger.debug("No handler found in getLastModified");
                return -1;
            }

            HandlerAdapter ha = getHandlerAdapter(mappedHandler.getHandler());
            long lastModified = ha.getLastModified(request, mappedHandler.getHandler());
            if(logger.isDebugEnabled()) {
                String requestUri = new UrlPathHelper().getRequestUri(request);
                logger.debug("Last-Modified value for [" + requestUri + "] is: " + lastModified);
            }
            return lastModified;
        } catch(Exception ex) {
            logger.debug("Exception thrown in getLastModified", ex);
            return -1;
        } finally {
            finishHttpProcessing();
        }
    }

    protected LocaleContext buildLocaleContext(final HttpServletRequest request) {
        return new LocaleContext() {
            @Override
            public Locale getLocale() {
                return requestBoundedStrategies.get().localeResolver.resolveLocale(request);
            }
            public String toString() {
                return getLocale().toString();
            }
        };
    }

    protected HttpServletRequest checkMultipart(HttpServletRequest request) throws MultipartException {
        if(requestBoundedStrategies.get().multipartResolver != null && requestBoundedStrategies.get().multipartResolver.isMultipart(request)) {
            if(request instanceof MultipartHttpServletRequest) {
                logger.debug("Request is already a MultipartHttpServletRequest - if not in a forwart," +
                        "this typically results from an additional MultipartFilter in web.xml");
            } else {
                return requestBoundedStrategies.get().multipartResolver.resolveMultipart(request);
            }
        }

        return request;
    }

    protected void cleanupMultipart(HttpServletRequest request) {
        if(request instanceof MultipartHttpServletRequest) {
            this.requestBoundedStrategies.get().multipartResolver.cleanupMultipart((MultipartHttpServletRequest) request);
        }
    }

    protected HandlerExecutionChain getHandler(HttpServletRequest request, boolean cache) throws Exception {

        HandlerExecutionChain handler = (HandlerExecutionChain) request.getAttribute(HANDLER_EXECUTION_CHAIN_ATTRIBUTE);

        if(handler != null) {
            if(!cache) {
                request.removeAttribute(HANDLER_EXECUTION_CHAIN_ATTRIBUTE);
            }
            return handler;
        }

        Iterator it = requestBoundedStrategies.get().handlerMappings.iterator();
        while(it.hasNext()) {
            HandlerMapping hm = (HandlerMapping) it.next();
            if(logger.isTraceEnabled()) {
                logger.trace("Testing handler map [" + hm + "] in DispatcherServlet with name '" +
                        getServletName() + "'");
            }
            handler = hm.getHandler(request);
            if(handler != null) {
                if(cache) {
                    request.setAttribute(HANDLER_EXECUTION_CHAIN_ATTRIBUTE, handler);
                }
                return handler;
            }
        }


        return null;
    }

    protected void noHandlerFound(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if(pageNotFoundLogger.isWarnEnabled()) {
            String requestUri = new UrlPathHelper().getRequestUri(request);
            pageNotFoundLogger.warn("No mapping found for HTTP request with URI [" +
                    requestUri + "] in DispatcherServlet with name '" + getServletName() + "'");
        }
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    protected HandlerAdapter getHandlerAdapter(Object handler) throws ServletException {
        Iterator it = requestBoundedStrategies.get().handlerAdapters.iterator();
        while(it.hasNext()) {
            HandlerAdapter ha = (HandlerAdapter) it.next();
            if(logger.isTraceEnabled()) {
                logger.trace("Testing handler adapter [" + ha + "]");
            }
            if(ha.supports(handler)) {
                return ha;
            }
        }
        throw new ServletException("No adapter for handler [" + handler +
                "]: Does your handler implement a supported interface like Controller?");
    }

    protected ModelAndView processHandlerException(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
            throws Exception {

        ModelAndView exMv = null;
        for(Iterator it = requestBoundedStrategies.get().handlerExceptionResolvers.iterator(); exMv == null && it.hasNext();) {
            HandlerExceptionResolver resolver = (HandlerExceptionResolver) it.next();
            exMv = resolver.resolveException(request, response, handler, ex);
        }
        if(exMv != null) {
            if(logger.isDebugEnabled()) {
                logger.debug("Handler execution resulted in exception - forwarding to resolved error view: " + exMv, ex);
            }
            WebUtils.exposeErrorRequestAttributes(request, ex, getServletName());
        }

        if(ex instanceof HttpRequestMethodNotSupportedException && !response.isCommitted()) {
            String[] supportedMethods = ((HttpRequestMethodNotSupportedException)ex).getSupportedMethods();
            if(supportedMethods != null) {
                response.setHeader("Allow", StringUtils.arrayToDelimitedString(supportedMethods, ", "));
            }
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, ex.getMessage());
            return null;
        }

        throw ex;
    }

    protected void render(ModelAndView mv, HttpServletRequest request, HttpServletResponse response) throws Exception {
        Locale locale = requestBoundedStrategies.get().localeResolver.resolveLocale(request);
        response.setLocale(locale);

        View view = null;

        if(mv.isReference()) {
            view = resolveViewName(mv.getViewName(), mv.getModel(), locale, request);
            if(view == null) {
                throw new ServletException("Could not resolve view with name '" + mv.getViewName() +
                        "' in servlet with name '" + getServletName() + "'");
            }
        } else {
            view = mv.getView();
            if(view == null) {
                throw new ServletException("ModelAndView [" + mv + "] neither contains a view name nor a " +
                        "View object in servlet with name '" + getServletName() + "'");
            }
        }

        if(logger.isDebugEnabled()) {
            logger.debug("Rendering view [" + view + "] in DispatcherServlet with name '" + getServletName() + "'");
        }

        view.render(mv.getModel(), request, response);
    }

    protected String getDefaultViewName(HttpServletRequest request) throws Exception {
        return requestBoundedStrategies.get().viewNameTranslator.getViewName(request);
    }

    protected View resolveViewName(String viewName, Map model, Locale locale, HttpServletRequest request) throws Exception {
        for(Iterator it = requestBoundedStrategies.get().viewResolvers.iterator(); it.hasNext();) {
            ViewResolver viewResolver = (ViewResolver) it.next();
            View view = viewResolver.resolveViewName(viewName, locale);
            if(view != null) {
                return view;
            }
        }
        return null;
    }

    private void triggerAfterCompletion(
            HandlerExecutionChain mappedHandler, int interceptorIndex,
            HttpServletRequest request, HttpServletResponse response, Exception ex)
            throws Exception {

        if(mappedHandler != null) {
            HandlerInterceptor[] interceptors = mappedHandler.getInterceptors();
            if(interceptors != null) {
                for(int i = interceptorIndex; i >= 0; i--) {
                    HandlerInterceptor interceptor = interceptors[i];
                    try {
                        interceptor.afterCompletion(request, response, mappedHandler.getHandler(), ex);
                    } catch(Throwable ex2) {
                        logger.error("HandlerInterceptor.afterCompletion threw exception", ex2);
                    }
                }
            }
        }
    }

    private void restoreAttributesAfterInclude(HttpServletRequest request, Map attributesSnapshot) {
        logger.debug("Restoring snapshot of request attributes after include");

        Set attrsToCheck = new HashSet();
        Enumeration attrNames = request.getAttributeNames();
        while (attrNames.hasMoreElements()) {
            String attrName = (String) attrNames.nextElement();
            if(this.cleanupAfterInclude || attrName.startsWith("org.springframework.web.servlet")) {
                attrsToCheck.add(attrName);
            }
        }

        for(Iterator it = attrsToCheck.iterator(); it.hasNext();) {
            String attrName = (String) it.next();
            Object attrValue = attributesSnapshot.get(attrName);
            if(attrValue != null) {
                if(logger.isDebugEnabled()) {
                    logger.debug("Restoring original value of attribute [" + attrName + "] after include");
                }
                request.setAttribute(attrName, attrValue);
            } else {
                if(logger.isDebugEnabled()) {
                    logger.debug("Removing attribute [" + attrName + "] after include");
                }
                request.removeAttribute(attrName);
            }
        }
    }

    @Override
    public void beforeActivation(ContextLoadImage image) {
        refreshStrategies(image);
    }

    // request-bounded(current request) strategies
    private ThreadLocal<Strategies> requestBoundedStrategies = new ThreadLocal<Strategies>();

    private class Strategies {

        private ContextLoadImage latestImage;

        private MultipartResolver multipartResolver;

        private LocaleResolver localeResolver;

        private ThemeResolver themeResolver;

        private List handlerMappings;

        private List handlerAdapters;

        private List handlerExceptionResolvers;

        private RequestToViewNameTranslator viewNameTranslator;

        private List viewResolvers;
    }
}
