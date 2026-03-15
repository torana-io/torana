package io.torana.spring.boot.autoconfigure;

import io.torana.spi.RequestContextResolver;
import io.torana.spring.boot.autoconfigure.resolver.WebMvcRequestContextResolver;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Auto-configuration for Spring WebMVC integration.
 *
 * <p>Automatically provides request context resolution when Spring WebMVC is present. No additional
 * dependencies required - just add spring-boot-starter-web to your project.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(RequestContextHolder.class)
public class ToranaWebMvcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RequestContextResolver.class)
    public RequestContextResolver toranaWebMvcRequestContextResolver() {
        return new WebMvcRequestContextResolver();
    }
}
