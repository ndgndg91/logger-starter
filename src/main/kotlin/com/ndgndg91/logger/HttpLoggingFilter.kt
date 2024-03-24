package com.ndgndg91.logger

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.function.Consumer
import java.util.regex.Matcher
import java.util.regex.Pattern


class HttpLoggingFilter: OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(HttpLoggingFilter::class.java)

    private val passwordReqBodyPatterns: List<Pattern> = listOf(
        Pattern.compile("(\"password\" *: *)(\"[\\w|!@#$%^&*()]+\")"),
        Pattern.compile("(\"resetPassword\" *: *)(\"[\\w|!@#$%^&*()]+\")")
    )

    private val visibleMediaTypes: List<MediaType> = Arrays.asList(
        MediaType.valueOf("text/*"),
        MediaType.APPLICATION_FORM_URLENCODED,
        MediaType.APPLICATION_JSON,
        MediaType.APPLICATION_XML,
        MediaType.valueOf("application/*+json"),
        MediaType.valueOf("application/*+xml"),
        MediaType.MULTIPART_FORM_DATA
    )

    /**
     * List of HTTP headers whose values should not be logged.
     */
    private val sensitiveHeaders: List<String> = mutableListOf(
        "authorization",
        "proxy-authorization"
    )

    private var enabled = true

    fun enable() {
        this.enabled = true
    }

    fun disable() {
        this.enabled = false
    }

    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (isAsyncDispatch(request)) {
            filterChain.doFilter(request, response)
        } else {
            doFilterWrapped(wrapRequest(request), wrapResponse(response), filterChain)
        }
    }

    @Throws(ServletException::class, IOException::class)
    fun doFilterWrapped(
        request: ContentCachingRequestWrapper,
        response: ContentCachingResponseWrapper,
        filterChain: FilterChain
    ) {
        val msg = StringBuilder()

        try {
            beforeRequest(request, response, msg)
            filterChain.doFilter(request, response)
        } finally {
            afterRequest(request, response, msg)
            if (log.isInfoEnabled) {
                log.info(msg.toString())
            }
            response.copyBodyToResponse()
        }
    }

    private fun beforeRequest(
        request: ContentCachingRequestWrapper,
        response: ContentCachingResponseWrapper?,
        msg: StringBuilder
    ) {
        if (log.isInfoEnabled) {
            msg.append("\n-- REQUEST --\n")
            logRequestHeader(request, request.remoteAddr + "|>", msg)
        }
    }

    private fun afterRequest(
        request: ContentCachingRequestWrapper,
        response: ContentCachingResponseWrapper,
        msg: StringBuilder
    ) {
        if (log.isInfoEnabled) {
            logRequestBody(request, request.remoteAddr + "|>", msg)
            msg.append("\n-- RESPONSE --\n")
            logResponse(response, request.remoteAddr + "|<", msg)
        }
    }

    private fun logRequestHeader(request: ContentCachingRequestWrapper, prefix: String, msg: StringBuilder) {
        val queryString = request.queryString
        if (queryString == null) {
            msg.append(String.format("%s %s %s", prefix, request.method, request.requestURI)).append("\n")
        } else {
            msg.append(String.format("%s %s %s?%s", prefix, request.method, request.requestURI, queryString))
                .append("\n")
        }
        Collections.list(request.headerNames)
            .forEach(Consumer { headerName: String ->
                Collections.list(request.getHeaders(headerName))
                    .forEach(Consumer { headerValue: String? ->
                        if (isSensitiveHeader(headerName)) {
                            msg.append(String.format("%s %s: %s", prefix, headerName, "*******")).append("\n")
                        } else {
                            msg.append(String.format("%s %s: %s", prefix, headerName, headerValue)).append("\n")
                        }
                    })
            })
        msg.append(prefix).append("\n")
    }

    private fun logRequestBody(request: ContentCachingRequestWrapper, prefix: String, msg: StringBuilder) {
        val content = request.contentAsByteArray
        if (content.isNotEmpty()) {
            logContent(content, request.contentType, request.characterEncoding, prefix, msg)
        }
    }

    private fun logResponse(response: ContentCachingResponseWrapper, prefix: String, msg: StringBuilder) {
        val status = response.status
        msg.append(String.format("%s %s %s", prefix, status, HttpStatus.valueOf(status).reasonPhrase)).append("\n")
        response.headerNames
            .forEach(Consumer { headerName: String ->
                response.getHeaders(headerName)
                    .forEach(Consumer { headerValue: String? ->
                        if (isSensitiveHeader(headerName)) {
                            msg.append(String.format("%s %s: %s", prefix, headerName, "*******")).append("\n")
                        } else {
                            msg.append(String.format("%s %s: %s", prefix, headerName, headerValue)).append("\n")
                        }
                    })
            })
        msg.append(prefix).append("\n")
        val content = response.contentAsByteArray
        if (content.isNotEmpty()) {
            logContent(content, response.contentType, StandardCharsets.UTF_8.name(), prefix, msg)
        }
    }

    private fun logContent(
        content: ByteArray,
        contentType: String,
        contentEncoding: String,
        prefix: String,
        msg: StringBuilder
    ) {
        val mediaType = MediaType.valueOf(contentType)
        val visible = visibleMediaTypes.stream().anyMatch { visibleType: MediaType ->
            visibleType.includes(mediaType)
        }
        if (visible) {
            try {
                String(content, charset(contentEncoding)).split("\r\n|\r|\n".toRegex()).dropLastWhile { it.isEmpty() }
                    .forEach { line ->
                        val convertedLine = if (line.contains("\"password\"") || line.contains("\"resetPassword\"")) {
                            convert(line)
                        } else {
                            line
                        }
                        msg.append(prefix).append(" ").append(convertedLine).append("\n")
                    }
            } catch (e: UnsupportedEncodingException) {
                msg.append(String.format("%s [%d bytes content]", prefix, content.size)).append("\n")
            }
        } else {
            msg.append(String.format("%s [%d bytes content]", prefix, content.size)).append("\n")
        }
    }

    private fun convert(target: String): String {
        val output = StringBuilder()
        passwordReqBodyPatterns.stream()
            .map { pattern: Pattern -> pattern.matcher(target) }
            .filter(Matcher::find)
            .findFirst()
            .ifPresentOrElse(
                { matcher: Matcher ->
                    output.append(target, 0, matcher.start())
                        .append(matcher.group(1))
                        .append(mask(matcher.group(2)))
                        .append(target, matcher.end(), target.length)
                },
                {
                    output.append(target)
                }
            )

        return output.toString()
    }

    private fun mask(credentials: String): String {
        return credentials.replace("[\\w|!@#$%^&*()]+".toRegex(), "*".repeat(credentials.length - 2))
    }

    /**
     * Determine if a given header name should have its value logged.
     * @param headerName HTTP header name.
     * @return True if the header is sensitive (i.e. its value should **not** be logged).
     */
    private fun isSensitiveHeader(headerName: String): Boolean {
        return sensitiveHeaders.contains(headerName.lowercase(Locale.getDefault()))
    }

    private fun wrapRequest(request: HttpServletRequest): ContentCachingRequestWrapper {
        return if (request is ContentCachingRequestWrapper) {
            request
        } else {
            ContentCachingRequestWrapper(request)
        }
    }

    private fun wrapResponse(response: HttpServletResponse): ContentCachingResponseWrapper {
        return if (response is ContentCachingResponseWrapper) {
            response
        } else {
            ContentCachingResponseWrapper(response)
        }
    }
}