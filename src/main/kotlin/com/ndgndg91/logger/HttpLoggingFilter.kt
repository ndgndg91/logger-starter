package com.ndgndg91.logger

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.util.*


class HttpLoggingFilter(
    private val httpLogger: HttpLogger,
    private val properties: HttpLoggingProperties
) : OncePerRequestFilter() {
    private val visibleMediaTypes: List<MediaType> = listOf(
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
        try {
            filterChain.doFilter(request, response)
        } finally {
            httpLogger.log(logRequestAndResponse(request, response))
            response.copyBodyToResponse()
        }
    }

    private fun logRequestAndResponse(request: ContentCachingRequestWrapper, response: ContentCachingResponseWrapper): HttpLog? {
        val skip = properties.excludeUrlPatterns.stream().anyMatch { pattern ->
            val matcher = AntPathMatcher()
            matcher.match(pattern, request.servletPath)
        }

        if (skip) return null
        return HttpLog(
            ip = request.remoteAddr,
            method = request.method,
            url = request.requestURI,
            statusCode = response.status.toString(),
            requestHeaders = Collections.list(request.headerNames)
                .associate { headerName ->
                    val headerValue = Collections.list(request.getHeaders(headerName)).joinToString(",")
                    if (isSensitiveHeader(headerName)) {
                        Pair(headerName, "*******")
                    } else {
                        Pair(headerName, headerValue)
                    }
                },
            requestBody = run {
                val mediaType = MediaType.valueOf(request.contentType)
                val visible = visibleMediaTypes.stream().anyMatch { visibleType: MediaType ->
                    visibleType.includes(mediaType)
                }
                if (visible) {
                    try {
                        String(request.contentAsByteArray, charset(request.characterEncoding))
                    } catch (e: UnsupportedEncodingException) {
                        String.format("[%d bytes content]", request.contentAsByteArray.size)
                    }
                } else {
                    String.format("[%d bytes content]", request.contentAsByteArray.size)
                }
            },
            requestParams = request.queryString,
            responseHeaders = response.headerNames.associate { headerName ->
                val headerValue = Collections.list(request.getHeaders(headerName)).joinToString(",")
                if (isSensitiveHeader(headerName)) {
                    Pair(headerName, "*******")
                } else {
                    Pair(headerName, headerValue)
                }
            },
            responseBody = kotlin.run {
                val mediaType = MediaType.valueOf(response.contentType)
                val visible = visibleMediaTypes.stream().anyMatch { visibleType: MediaType ->
                    visibleType.includes(mediaType)
                }
                if (visible) {
                    try {
                        String(response.contentAsByteArray, charset(response.characterEncoding))
                    } catch (e: UnsupportedEncodingException) {
                        String.format("[%d bytes content]", response.contentAsByteArray.size)
                    }
                } else {
                    String.format("[%d bytes content]", response.contentAsByteArray.size)
                }
            }
        )
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