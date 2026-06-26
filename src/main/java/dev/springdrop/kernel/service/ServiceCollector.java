package dev.springdrop.kernel.service;

import java.util.List;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.stereotype.Component;

/**
 * Gathers every bean implementing a contract, ordered by {@code @Order} or the
 * {@link org.springframework.core.Ordered} interface. This is the convention
 * modules use to discover all implementations of a plugin or handler contract
 * the way Drupal's tagged service collectors do.
 */
@Component
public class ServiceCollector {

    private final ListableBeanFactory beanFactory;

    public ServiceCollector(ListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    public <T> List<T> collect(Class<T> contract) {
        return beanFactory.getBeanProvider(contract).orderedStream().toList();
    }
}
