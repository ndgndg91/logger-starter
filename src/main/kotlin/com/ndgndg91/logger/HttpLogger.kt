package com.ndgndg91.logger

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory

class HttpLogger {
    private val logger = LoggerFactory.getLogger(HttpLogger::class.java)
    private val om = jacksonObjectMapper()

    fun log(log: HttpLog?) {
        if (log == null) return
        logger.info("{}", om.writeValueAsString(log))
    }
}