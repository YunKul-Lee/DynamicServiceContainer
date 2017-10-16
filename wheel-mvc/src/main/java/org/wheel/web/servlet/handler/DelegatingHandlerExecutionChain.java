package org.wheel.web.servlet.handler;

import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerInterceptor;

public class DelegatingHandlerExecutionChain extends HandlerExecutionChain {

    public DelegatingHandlerExecutionChain(DelegatingHandler handler, HandlerInterceptor[] interceptors) {
        super(handler, interceptors);
    }

    public DelegatingHandlerExecutionChain(DelegatingHandler handler) {
        super(handler);
    }

    @Override
    public Object getHandler() {
        Object handler = super.getHandler();
        System.out.println("handler=" + handler.getClass().getCanonicalName());
        return super.getHandler();
    }

    public static class DelegatingHandler {

    }
}
