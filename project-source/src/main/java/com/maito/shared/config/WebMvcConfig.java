package com.maito.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

/**
 * Enterprise WebMvc configuration for localized, 10-year immutable static assets
 * and Server-Driven UI storefront client resources.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic());

        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache());
    }
}
