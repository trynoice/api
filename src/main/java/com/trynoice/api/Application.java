package com.trynoice.api;

import com.trynoice.api.identity.BearerTokenAuthFilter;
import com.trynoice.api.identity.CookieAuthFilter;
import com.trynoice.api.identity.SignInTokenDispatchStrategy;
import com.trynoice.api.identity.models.AuthConfiguration;
import com.trynoice.api.identity.models.EmailSignInTokenDispatcherConfiguration;
import com.trynoice.api.platform.GlobalControllerAdvice;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.Banner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.validation.annotation.Validated;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        new SpringApplicationBuilder(Application.class)
            .bannerMode(Banner.Mode.OFF)
            .run(args);
    }

    @NonNull
    @Validated
    @Bean
    @ConfigurationProperties("app.auth")
    AuthConfiguration authConfiguration() {
        return new AuthConfiguration();
    }

    @NonNull
    @Validated
    @Bean
    @ConfigurationProperties("app.auth.sign-in-token-dispatcher.email")
    @ConditionalOnProperty(name = "app.auth.sign-in-token-dispatcher-type", havingValue = "email")
    EmailSignInTokenDispatcherConfiguration emailSignInTokenDispatcherConfiguration() {
        return new EmailSignInTokenDispatcherConfiguration();
    }

    @NonNull
    @Bean
    SignInTokenDispatchStrategy signInTokenDispatchStrategy(
        @NonNull AuthConfiguration authConfig,
        @Autowired(required = false) EmailSignInTokenDispatcherConfiguration emailSignInTokenDispatcherConfig
    ) {
        switch (authConfig.getSignInTokenDispatcherType()) {
            case EMAIL:
                assert emailSignInTokenDispatcherConfig != null;
                return new SignInTokenDispatchStrategy.Email(emailSignInTokenDispatcherConfig);
            case CONSOLE:
                return new SignInTokenDispatchStrategy.Console();
            default:
                throw new IllegalArgumentException("unsupported sign-in token dispatch strategy: "
                    + authConfiguration().getSignInTokenDispatcherType());
        }
    }

    @NonNull
    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info().title("Noice API").version("v1"))
            .components(
                new Components()
                    .addSecuritySchemes("bearer-token", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"))
                    .addSecuritySchemes("refresh-token-cookie", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name(CookieAuthFilter.REFRESH_TOKEN_COOKIE)))
            .addSecurityItem(new SecurityRequirement().addList("bearer-token"))
            .addSecurityItem(new SecurityRequirement().addList("refresh-token-cookie"));
    }

    @Configuration
    static class WebSecurityConfiguration extends WebSecurityConfigurerAdapter {

        private final BearerTokenAuthFilter bearerTokenAuthFilter;
        private final CookieAuthFilter cookieAuthFilter;
        private final GlobalControllerAdvice globalControllerAdvice;

        @Autowired
        public WebSecurityConfiguration(
            @NonNull BearerTokenAuthFilter bearerTokenAuthFilter,
            @NonNull CookieAuthFilter cookieAuthFilter,
            @NonNull GlobalControllerAdvice globalControllerAdvice
        ) {
            this.bearerTokenAuthFilter = bearerTokenAuthFilter;
            this.cookieAuthFilter = cookieAuthFilter;
            this.globalControllerAdvice = globalControllerAdvice;
        }

        @Override
        public void configure(WebSecurity web) {
            // exclude URLs from the Spring security filter chain.
            web.ignoring().antMatchers(
                "/v1/accounts/signUp",
                "/v1/accounts/signIn",
                "/v1/accounts/signOut",
                "/v1/accounts/credentials"
            );
        }

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            // disable default filters.
            http.csrf().disable()
                .formLogin().disable()
                .headers().disable()
                .httpBasic().disable()
                .logout().disable()
                .rememberMe().disable()
                .requestCache().disable()
                .securityContext().disable()
                .sessionManagement().disable();

            http.exceptionHandling()
                .authenticationEntryPoint(globalControllerAdvice.noOpAuthenticationEntrypoint());

            // use request filter to use SecurityContext for authorizing requests.
            http.authorizeRequests()
                .antMatchers("/v1/**").fullyAuthenticated()
                .anyRequest().permitAll();

            // add custom filter to set SecurityContext based on Authorization bearer JWT.
            http.addFilterBefore(bearerTokenAuthFilter, AnonymousAuthenticationFilter.class);
            http.addFilterBefore(cookieAuthFilter, AnonymousAuthenticationFilter.class);
        }
    }
}
