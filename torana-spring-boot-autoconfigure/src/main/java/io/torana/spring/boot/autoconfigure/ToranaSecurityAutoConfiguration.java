package io.torana.spring.boot.autoconfigure;

import io.torana.spi.ActorResolver;
import io.torana.spring.security.SecurityContextActorResolver;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Auto-configuration for Spring Security integration.
 *
 * <p>Provides actor resolution from SecurityContext when Spring Security is present.
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
