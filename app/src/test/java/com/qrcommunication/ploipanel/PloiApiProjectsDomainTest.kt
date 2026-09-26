package com.qrcommunication.ploipanel

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Parsing, validation and wire-level coverage for every documented route of the
 * `projects` domain (/api/projects, 5 routes).
 */
class PloiApiProjectsDomainTest {
    private val token = "test-token"
    private val recorded = CopyOnWriteArrayList<HttpRequest>()
    private var scriptedBody = "{}"

    private fun installFakeClient() {
        val transport = HttpTransport { request ->
            recorded.add(request)
            HttpResponse(200, scriptedBody, emptyMap())
        }
        PloiApi.httpClient = PloiHttpClient(transport = transport)
    }

    @After fun restoreClient() {
        PloiApi.httpClient = PloiHttpClient()
    }

    /** Documented list shape (developers.ploi.io/projects/list-projects). */
    private fun projectListJson(): String = JSONObject()
        .put(
            "data",
            JSONArray().put(
                JSONObject()
                    .put("id", 7)
                    .put("title", "My Project")
                    .put("servers", JSONArray())
                    .put("sites", JSONArray().put(JSONObject().put("id", 1).put("root_domain", "example.com")))
                    .put("created_at", "2022-07-29 07:37:51")
            )
        )
        .put("meta", JSONObject().put("current_page", 1).put("last_page", 1))
        .toString()

    /** Documented single-project shape (get + create + update responses share it). */
    private fun projectJson(): String = JSONObject()
        .put(
            "data",
            JSONObject()
                .put("id", 1)
                .put("title", "My Awesome Project")
                .put("servers", JSONArray())
                .put("sites", JSONArray())
                .put("created_at", "2022-07-29 07:44:23")
        )
        .toString()

    // ---- GET /api/projects ----

    @Test fun parsesProjectListWithDocumentedShape() {
        val page = PloiApi.parseProjects(projectListJson())
        assertEquals(1, page.currentPage)
        assertFalse(page.hasNext)
        val project = page.projects.single()
        assertEquals(7L, project.id)
        assertEquals("My Project", project.title)
        assertTrue(project.serverIds.isEmpty())
        assertEquals(ProjectSite(1L, "example.com"), project.sites.single())
        assertEquals("2022-07-29 07:37:51", project.createdAt)
    }

    @Test fun projectsHitsDocumentedRouteWithPagination() {
        installFakeClient()
        scriptedBody = projectListJson()
        val page = PloiApi.projects(token, page = 2, perPage = 50)
        assertEquals(1, page.projects.size)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/projects?page=2&per_page=50", request.url)
        assertEquals("Bearer $token", request.headers["Authorization"])
    }

    // ---- GET /api/projects/{project} ----

    @Test fun parsesSingleProject() {
        val project = PloiApi.parseProject(projectJson())
        assertEquals(1L, project.id)
        assertEquals("My Awesome Project", project.title)
    }

    @Test fun projectHitsDocumentedRoute() {
        installFakeClient()
        scriptedBody = projectJson()
        PloiApi.project(token, 3)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/projects/3", request.url)
    }

    // ---- POST /api/projects ----

    @Test fun createProjectSendsDocumentedBody() {
        installFakeClient()
        scriptedBody = projectJson()
        PloiApi.createProject(token, ProjectRequest("My Awesome Project", serverIds = listOf(1), siteIds = listOf(2)))
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/projects", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("My Awesome Project", body.getString("title"))
        assertEquals(1L, body.getJSONArray("servers").getLong(0))
        assertEquals(2L, body.getJSONArray("sites").getLong(0))
    }

    @Test fun projectRequestRejectsInvalidInput() {
        listOf(
            { ProjectRequest("") },
            { ProjectRequest("x".repeat(256)) },
            { ProjectRequest("ok", serverIds = listOf(0)) },
            { ProjectRequest("ok", siteIds = listOf(-1)) }
        ).forEach { invalid ->
            try {
                invalid()
                fail("Expected rejection")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    // ---- PATCH /api/projects/{project} ----

    @Test fun updateProjectSendsDocumentedBody() {
        installFakeClient()
        scriptedBody = projectJson()
        PloiApi.updateProject(token, 4, ProjectRequest("My New Awesome Project"))
        val request = recorded.single()
        assertEquals("PATCH", request.method)
        assertEquals("https://ploi.io/api/projects/4", request.url)
        assertEquals("My New Awesome Project", JSONObject(request.body.orEmpty()).getString("title"))
    }

    // ---- DELETE /api/projects/{project} ----

    @Test fun deleteProjectHitsDocumentedRouteAndParsesMessage() {
        installFakeClient()
        scriptedBody = JSONObject().put("message", "Project has been deleted.").toString()
        val message = PloiApi.deleteProject(token, 4)
        assertEquals("Project has been deleted.", message)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/projects/4", request.url)
    }
}
