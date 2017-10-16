package org.wheel.web.servlet.handler;

import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;

public class DelegatingHandlerMapping implements HandlerMapping {

    @Override
    public HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception {
        if("y".equalsIgnoreCase(request.getParameter("wheel"))) {
            return new DelegatingHandlerExecutionChain(new DelegatingHandlerExecutionChain.DelegatingHandler());
        }
        return null;        // 이 핸들러 매핑에서 다루지 않음을 null로 표현함.
    }
}
