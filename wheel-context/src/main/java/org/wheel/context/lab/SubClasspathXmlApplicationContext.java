package org.wheel.context.lab;

import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SubClasspathXmlApplicationContext extends ClassPathXmlApplicationContext{

    SubClasspathXmlApplicationContext(
            ClassLoader classLoader,
            String[] configLocations,
            ApplicationContext parent) {

        super(configLocations, parent);
        // Spring에게 이 ClassLoader를 이용하여 load하라고 지시함.
        this.setClassLoader(classLoader);
    }

    @Override
    protected void initBeanDefinitionReader(XmlBeanDefinitionReader reader) {
        super.initBeanDefinitionReader(reader);

        reader.setBeanClassLoader(getClassLoader());
    }
}
