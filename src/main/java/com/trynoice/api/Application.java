package com.trynoice.api;

import com.trynoice.api.identity.AuthBearerJWTReadFilter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.Banner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.access.ExceptionTranslationFilter;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        new SpringApplicationBuilder(Application.class)
            .bannerMode(Banner.Mode.OFF)
            .run(args);
    }

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info().title("Noice API"))
            .components(
                new Components()
                    .addSecuritySchemes("bearer-token", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                    )
            );
    }

    @Configuration
    static class MyWebSecurityConfiguration extends WebSecurityConfigurerAdapter {

        private final AuthBearerJWTReadFilter authBearerJWTReadFilter;

        @Autowired
        public MyWebSecurityConfiguration(@NonNull AuthBearerJWTReadFilter authBearerJWTReadFilter) {
            this.authBearerJWTReadFilter = authBearerJWTReadFilter;
        }

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            // disable default filters.
            http.securityContext().disable()
                .headers().disable()
                .csrf().disable()
                .logout().disable()
                .requestCache().disable()
                .sessionManagement().disable();

            // use request filter to use SecurityContext for authorizing requests.
            http.authorizeRequests()
                .antMatchers("/v1/accounts/**").permitAll()
                .anyRequest().authenticated();

            // add custom filter to set SecurityContext based on Authorization bearer JWT.
            http.addFilterBefore(authBearerJWTReadFilter, ExceptionTranslationFilter.class);
        }
    }
}
