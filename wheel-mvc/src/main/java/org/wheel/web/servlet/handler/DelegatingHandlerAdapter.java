package org.wheel.web.servlet.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerAdapter;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class DelegatingHandlerAdapter implements HandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(DelegatingHandlerAdapter.class);

    @Override
    public long getLastModified(HttpServletRequest request, Object handler) {
        return -1;
    }

    @Override
    public ModelAndView handle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if(logger.isDebugEnabled()) {
            logger.debug("handler=" + handler.getClass());
        }

        return null;
    }

    @Override
    public boolean supports(Object handler) {
        System.out.println("###########################supporing handler=" + (handler instanceof DelegatingHandlerExecutionChain.DelegatingHandler));
        return handler instanceof DelegatingHandlerExecutionChain.DelegatingHandler;
    }
}
