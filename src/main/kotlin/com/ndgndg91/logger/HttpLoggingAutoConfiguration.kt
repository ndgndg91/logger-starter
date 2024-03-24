package com.ndgndg91.logger

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnWebApplication
class HttpLoggingAutoConfiguration {

    @Bean
    fun loggingFilter(): FilterRegistrationBean<HttpLoggingFilter> {
        val registrationBean: FilterRegistrationBean<HttpLoggingFilter> = FilterRegistrationBean()
        registrationBean.filter = HttpLoggingFilter()
        registrationBean.addUrlPatterns("/*")
        return registrationBean
    }
}