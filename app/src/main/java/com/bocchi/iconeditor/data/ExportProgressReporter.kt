package com.bocchi.iconeditor.data

import android.util.Log
import com.bocchi.iconeditor.model.ExportPhase
import com.bocchi.iconeditor.model.ExportProgress

class ExportProgressReporter(
    private val onProgress: (ExportProgress) -> Unit = {},
) {
    private val logs = ArrayDeque<String>()
    private var phase = ExportPhase.Preparing
    private var current = 0
    private var total = 0
    private var detail = ""

    private var finished = false
    private var success = false

    @Synchronized
    fun update(
        phase: ExportPhase? = null,
        current: Int? = null,
        total: Int? = null,
        detail: String? = null,
        log: String? = null,
        finished: Boolean? = null,
        success: Boolean? = null,
    ) {
        phase?.let { this.phase = it }
        current?.let { this.current = it }
        total?.let { this.total = it }
        detail?.let { this.detail = it }
        finished?.let { this.finished = it }
        success?.let { this.success = it }
        log?.let(::appendLog)
        emit()
    }

    @Synchronized
    fun log(message: String) {
        appendLog(message)
        emit()
    }

    @Synchronized
    fun finish(success: Boolean, detail: String) {
        this.phase = ExportPhase.Finishing
        this.detail = detail
        this.finished = true
        this.success = success
        appendLog(if (success) "导出完成" else "导出失败")
        emit()
    }

    private fun emit() {
        onProgress(
            ExportProgress(
                phase = phase,
                current = current,
                total = total,
                detail = detail,
                logs = logs.toList(),
                finished = finished,
                success = success,
            ),
        )
    }

    private fun appendLog(message: String) {
        runCatching { Log.d(TAG, message) }
        if (logs.size >= MAX_LOG_LINES) {
            logs.removeFirst()
        }
        logs.addLast(message)
    }

    companion object {
        private const val TAG = "IconEditor/Export"
        private const val MAX_LOG_LINES = 100
        val NOOP = ExportProgressReporter()
    }
}
