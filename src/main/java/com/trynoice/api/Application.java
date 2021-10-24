package com.trynoice.api;

import com.trynoice.api.identity.BearerTokenAuthFilter;
import com.trynoice.api.identity.SignInTokenDispatchStrategy;
import com.trynoice.api.identity.models.AuthConfiguration;
import com.trynoice.api.platform.GlobalControllerAdvice;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.Banner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
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
    @Bean
    SignInTokenDispatchStrategy signInTokenDispatchStrategy(
        @NonNull @Value("${app.auth.sign-in-token-dispatcher}") String strategy,
        @NonNull @Value("${app.auth.sign-in-token-dispatcher.email.from}") String sourceEmail
    ) {
        switch (strategy) {
            case "email":
                return new SignInTokenDispatchStrategy.Email(sourceEmail);
            case "console":
                return new SignInTokenDispatchStrategy.Console();
            default:
                throw new IllegalArgumentException("unsupported sign-in token dispatch strategy: " + strategy);
        }
    }

    @NonNull
    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info().title("Noice API"))
            .components(
                new Components()
                    .addSecuritySchemes("bearer-token", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }

    @Configuration
    static class WebSecurityConfiguration extends WebSecurityConfigurerAdapter {

        private final BearerTokenAuthFilter bearerTokenAuthFilter;
        private final GlobalControllerAdvice globalControllerAdvice;

        @Autowired
        public WebSecurityConfiguration(
            @NonNull BearerTokenAuthFilter bearerTokenAuthFilter,
            @NonNull GlobalControllerAdvice globalControllerAdvice
        ) {
            this.bearerTokenAuthFilter = bearerTokenAuthFilter;
            this.globalControllerAdvice = globalControllerAdvice;
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
                .antMatchers("/v1/accounts/**").permitAll()
                .antMatchers("/v1/**").fullyAuthenticated()
                .anyRequest().permitAll();

            // add custom filter to set SecurityContext based on Authorization bearer JWT.
            http.addFilterAfter(bearerTokenAuthFilter, AnonymousAuthenticationFilter.class);
        }
    }
}
