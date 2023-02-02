package com.trynoice.api;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.trynoice.api.identity.BearerTokenAuthFilter;
import com.trynoice.api.identity.CookieAuthFilter;
import com.trynoice.api.platform.GlobalControllerAdvice;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.Banner;
import org.springframework.boot.actuate.web.exchanges.HttpExchangeRepository;
import org.springframework.boot.actuate.web.exchanges.InMemoryHttpExchangeRepository;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.info.BuildProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.firewall.HttpStatusRequestRejectedHandler;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.stereotype.Component;

// exclude user details service from Spring security. We're not using it.
@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
@EnableCaching
@EnableScheduling
@ConfigurationPropertiesScan(basePackageClasses = Application.class)
public class Application {

    public static void main(String[] args) {
        new SpringApplicationBuilder(Application.class)
            .bannerMode(Banner.Mode.OFF)
            .run(args);
    }

    @NonNull
    @Bean
    Jackson2ObjectMapperBuilderCustomizer objectMapperBuilderCustomizer() {
        return builder -> {
            builder.featuresToEnable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            builder.featuresToDisable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);
        };
    }

    @NonNull
    @Bean
    OpenAPI openAPI(@NonNull BuildProperties buildProperties) {
        return new OpenAPI()
            .info(
                new Info()
                    .title("Noice API")
                    .version(String.format("v%s", buildProperties.getVersion())))
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

    @NonNull
    @Bean
    HttpExchangeRepository httpExchangeRepository() {
        // TODO: configure a production-ready http request tracing solution.
        return new InMemoryHttpExchangeRepository();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
        @NonNull HttpSecurity http,
        @NonNull BearerTokenAuthFilter bearerTokenAuthFilter,
        @NonNull CookieAuthFilter cookieAuthFilter
    ) throws Exception {
        // disable default filters.
        http.cors().disable()
            .csrf().disable()
            .formLogin().disable()
            .headers().disable()
            .httpBasic().disable()
            .jee().disable()
            .logout().disable()
            .rememberMe().disable()
            .requestCache().disable()
            .securityContext().disable()
            .sessionManagement().disable();

        // Always return 401 since we don't have an entrypoint where we can redirect users for
        // authentication. They must manually initiate authentication by invoking relevant endpoints
        // upon receiving a 401 response status.
        http.exceptionHandling().authenticationEntryPoint(
            (request, response, authException) -> response.setStatus(HttpServletResponse.SC_UNAUTHORIZED));

        // use request filter to use SecurityContext for authorizing requests.
        http.authorizeHttpRequests()
            .requestMatchers(HttpMethod.GET, "/v1/sounds/*/segments/*/authorize").permitAll()
            .requestMatchers(HttpMethod.GET, "/v1/subscriptions/plans").permitAll()
            .requestMatchers(HttpMethod.POST, "/v1/accounts/signUp").anonymous()
            .requestMatchers(HttpMethod.POST, "/v1/accounts/signIn").anonymous()
            .requestMatchers(HttpMethod.GET, "/v1/accounts/credentials").anonymous()
            .requestMatchers(HttpMethod.POST, "/v1/subscriptions/stripe/webhook").anonymous()
            .requestMatchers("/v?*/**").fullyAuthenticated()
            .anyRequest().permitAll();

        // add custom filter to set SecurityContext based on Authorization bearer JWT.
        http.addFilterBefore(bearerTokenAuthFilter, AnonymousAuthenticationFilter.class);
        http.addFilterBefore(cookieAuthFilter, AnonymousAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    RequestRejectedHandler requestRejectedHandler() {
        return new HttpStatusRequestRejectedHandler();
    }

    @Component
    @Slf4j
    static class ApplicationVersionLogger implements ApplicationRunner {

        @Autowired
        private BuildProperties buildProperties;

        @Override
        public void run(ApplicationArguments args) {
            log.info("Running {} version: v{}", buildProperties.getName(), buildProperties.getVersion());
        }
    }
}
