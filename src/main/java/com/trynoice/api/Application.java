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
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.Banner;
import org.springframework.boot.actuate.trace.http.HttpTraceRepository;
import org.springframework.boot.actuate.trace.http.InMemoryHttpTraceRepository;
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
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
    public Jackson2ObjectMapperBuilderCustomizer objectMapperBuilderCustomizer() {
        return builder -> {
            builder.featuresToEnable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            builder.featuresToDisable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);
        };
    }

    @NonNull
    @Bean
    WebMvcConfigurer webMvcConfigurer(@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(@NonNull CorsRegistry registry) {
                registry.addMapping("/**")
                    .allowedOriginPatterns(allowedOrigins)
                    .allowedMethods("GET", "POST", "PUT", "DELETE");
            }
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
    HttpTraceRepository httpTraceRepository() {
        // TODO: configure a production-ready http request tracing solution.
        return new InMemoryHttpTraceRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
        @NonNull HttpSecurity http,
        @NonNull BearerTokenAuthFilter bearerTokenAuthFilter,
        @NonNull CookieAuthFilter cookieAuthFilter,
        @NonNull GlobalControllerAdvice globalControllerAdvice
    ) throws Exception {
        // disable default filters.
        http.csrf().disable()
            .formLogin().disable()
            .headers().disable()
            .httpBasic().disable()
            .jee().disable()
            .logout().disable()
            .rememberMe().disable()
            .requestCache().disable()
            .securityContext().disable()
            .sessionManagement().disable();

        // will automatically consider CORS configuration from WebMVC.
        // https://docs.spring.io/spring-security/site/docs/5.2.1.RELEASE/reference/htmlsingle/#cors
        http.cors();
        http.exceptionHandling()
            .authenticationEntryPoint(globalControllerAdvice.noOpAuthenticationEntrypoint());

        // use request filter to use SecurityContext for authorizing requests.
        http.authorizeRequests()
            .mvcMatchers(HttpMethod.GET, "/v1/sounds/*/segments/*/authorize").permitAll()
            .mvcMatchers(HttpMethod.GET, "/v1/subscriptions/plans").permitAll()
            .mvcMatchers(HttpMethod.POST, "/v1/accounts/signUp").anonymous()
            .mvcMatchers(HttpMethod.POST, "/v1/accounts/signIn").anonymous()
            .mvcMatchers(HttpMethod.GET, "/v1/accounts/credentials").anonymous()
            .mvcMatchers(HttpMethod.POST, "/v1/subscriptions/googlePlay/webhook").anonymous()
            .mvcMatchers(HttpMethod.POST, "/v1/subscriptions/stripe/webhook").anonymous()
            .antMatchers("/v?*/**").fullyAuthenticated()
            .anyRequest().permitAll();

        // add custom filter to set SecurityContext based on Authorization bearer JWT.
        http.addFilterBefore(bearerTokenAuthFilter, AnonymousAuthenticationFilter.class);
        http.addFilterBefore(cookieAuthFilter, AnonymousAuthenticationFilter.class);
        return http.build();
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
