package io.torana.spring.boot.autoconfigure;

import io.torana.spi.RequestContextResolver;
import io.torana.spring.webmvc.WebMvcRequestContextResolver;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Auto-configuration for Spring Web MVC integration.
 *
 * <p>Provides request context resolution from RequestContextHolder when Spring Web MVC is present.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({RequestContextHolder.class, WebMvcRequestContextResolver.class})
public class ToranaWebMvcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RequestContextResolver.class)
    public RequestContextResolver toranaWebMvcRequestContextResolver() {
        return new WebMvcRequestContextResolver();
    }
}
