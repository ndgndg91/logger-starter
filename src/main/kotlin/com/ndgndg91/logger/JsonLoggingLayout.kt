package com.ndgndg91.logger

import ch.qos.logback.contrib.json.classic.JsonLayout

class JsonLoggingLayout: JsonLayout() {

    override fun addTimestamp(key: String, hasField: Boolean, timestamp: Long, map: MutableMap<String, Any>) {
        map[key] = timestamp
    }
}