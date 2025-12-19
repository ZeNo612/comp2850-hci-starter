package utils

import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Server-side logging utility for capturing evaluation metrics.
 *
 * Records task completion events to CSV for later analysis.
 * Designed for privacy-by-design: uses anonymous session IDs only.
 *
 * Usage:
 *   Logger.write(
 *     session = sid(call),
 *     req = reqId(call),
 *     task = "T1_filter",
 *     step = "submit",
 *     outcome = "success",
 *     ms = duration,
 *     status = 200,
 *     js = jsMode(call)
 *   )
 *
 * Referenced in: Week 9 Lab 1 (Activity 3)
 */
object Logger {
    private val file = File("data/metrics.csv").apply {
        parentFile?.mkdirs()
        if (!exists()) {
            writeText("ts_iso,session_id,request_id,task_code,step,outcome,ms,http_status,js_mode\n")
        }
    }

    /**
     * Writes a single metric row.
     * Thread-safe via @Synchronized annotation.
     */
    @Synchronized
    fun write(
        session: String,
        req: String,
        task: String,
        step: String,
        outcome: String,
        ms: Long,
        status: Int,
        js: String
    ) {
        val ts = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        file.appendText("$ts,$session,$req,$task,$step,$outcome,$ms,$status,$js\n")
    }
}
