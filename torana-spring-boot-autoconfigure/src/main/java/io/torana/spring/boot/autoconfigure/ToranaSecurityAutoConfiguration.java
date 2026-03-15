package io.torana.spring.boot.autoconfigure;

import io.torana.spi.ActorResolver;
import io.torana.spring.boot.autoconfigure.resolver.SecurityContextActorResolver;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Auto-configuration for Spring Security integration.
 *
 * <p>Automatically provides actor resolution from SecurityContext when Spring Security is present.
 * No additional dependencies required - just add spring-boot-starter-security to your project.
 */
@AutoConfiguration
@ConditionalOnClass(SecurityContextHolder.class)
public class ToranaSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ActorResolver.class)
    public ActorResolver toranaSecurityContextActorResolver() {
        return new SecurityContextActorResolver();
    }
}
