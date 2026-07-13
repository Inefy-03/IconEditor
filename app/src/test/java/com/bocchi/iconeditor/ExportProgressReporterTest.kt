package com.bocchi.iconeditor

import com.bocchi.iconeditor.data.ExportProgressReporter
import com.bocchi.iconeditor.model.ExportPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportProgressReporterTest {
    @Test
    fun accumulatesLogsAndUpdatesPhase() {
        val events = mutableListOf<com.bocchi.iconeditor.model.ExportProgress>()
        val reporter = ExportProgressReporter { events += it }
        reporter.update(phase = ExportPhase.Preparing, log = "开始导出")
        reporter.update(
            phase = ExportPhase.PackagingIcons,
            current = 2,
            total = 10,
            detail = "com.example.app",
            log = "写入图标",
        )
        val last = events.last()
        assertEquals(ExportPhase.PackagingIcons, last.phase)
        assertEquals(2, last.current)
        assertEquals(10, last.total)
        assertEquals("com.example.app", last.detail)
        assertTrue(last.logs.contains("开始导出"))
        assertTrue(last.logs.contains("写入图标"))
    }
}
