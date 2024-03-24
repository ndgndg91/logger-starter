package com.ndgndg91.logger

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

@Configuration
@EnableConfigurationProperties(HttpLoggingProperties::class)
@ConditionalOnProperty(value = ["logging.http.enabled"], havingValue = "true", matchIfMissing = false)
@ConditionalOnWebApplication
class HttpLoggingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun loggingFilter(properties: HttpLoggingProperties): FilterRegistrationBean<HttpLoggingFilter> {
        val registrationBean: FilterRegistrationBean<HttpLoggingFilter> = FilterRegistrationBean()
        registrationBean.filter = HttpLoggingFilter(HttpLogger(), properties)
        registrationBean.addUrlPatterns("/*")
        registrationBean.order = Ordered.HIGHEST_PRECEDENCE
        return registrationBean
    }
}