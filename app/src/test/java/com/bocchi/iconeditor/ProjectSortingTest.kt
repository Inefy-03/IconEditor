package com.bocchi.iconeditor

import com.bocchi.iconeditor.model.MtzInfo
import com.bocchi.iconeditor.model.ProjectMetadata
import com.bocchi.iconeditor.model.ProjectSortField
import com.bocchi.iconeditor.model.ProjectSummary
import com.bocchi.iconeditor.ui.page.sortProjects
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectSortingTest {
    @Test
    fun sortsTimeNewestFirstAndNameAscending() {
        val projects = listOf(
            ProjectSummary(id = "a", name = "A", createdAt = 1, updatedAt = 30),
            ProjectSummary(id = "b", name = "B", createdAt = 3, updatedAt = 10),
            ProjectSummary(id = "c", name = "C", createdAt = 2, updatedAt = 20),
        )
        val metadata = mapOf(
            "a" to ProjectMetadata(mtz = MtzInfo(title = "Beta")),
            "b" to ProjectMetadata(mtz = MtzInfo(title = "Gamma")),
            "c" to ProjectMetadata(mtz = MtzInfo(title = "Alpha")),
        )

        assertEquals(listOf("b", "c", "a"), sortProjects(projects, metadata, ProjectSortField.CreatedAt).map { it.id })
        assertEquals(listOf("a", "c", "b"), sortProjects(projects, metadata, ProjectSortField.UpdatedAt).map { it.id })
        assertEquals(listOf("c", "a", "b"), sortProjects(projects, metadata, ProjectSortField.Name).map { it.id })
    }
}
