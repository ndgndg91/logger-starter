package com.ndgndg91.logger

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ndgndg91.logging.http")
data class HttpLoggingProperties(
    var enabled: Boolean = false,
    var excludeUrlPatterns: List<String> = emptyList(),
    var excludeHeaders: List<String> = emptyList()
)