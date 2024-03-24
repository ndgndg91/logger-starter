package com.ndgndg91.logger

import com.fasterxml.jackson.annotation.JsonProperty

data class HttpLog(
    val ip: String?,
    val method: String?,
    val url: String?,
    @JsonProperty("status_code") val statusCode: String?,
    @JsonProperty("request_headers") val requestHeaders: Map<String, String> = emptyMap(),
    @JsonProperty("request_body") val requestBody: String?,
    @JsonProperty("request_params") val requestParams: String?,
    @JsonProperty("response_body") val responseHeaders: Map<String, String> = emptyMap(),
    @JsonProperty("response_body") val responseBody: String?
)