package routes

import data.TaskRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.pebbletemplates.pebble.PebbleEngine
import java.io.StringWriter
import utils.Logger
import utils.generateRequestId
import utils.SessionData

/**
 * NOTE FOR NON-INTELLIJ IDEs (VSCode, Eclipse, etc.):
 * IntelliJ IDEA automatically adds imports as you type. If using a different IDE,
 * you may need to manually add imports. The commented imports below show what you'll need
 * for future weeks. Uncomment them as needed when following the lab instructions.
 *
 * When using IntelliJ: You can ignore the commented imports below - your IDE will handle them.
 */

// Week 7+ imports (inline edit, toggle completion):
// import model.Task               // When Task becomes separate model class
// import model.ValidationResult   // For validation errors
// import renderTemplate            // Extension function from Main.kt
// import isHtmxRequest             // Extension function from Main.kt

// Week 8+ imports (pagination, search, URL encoding):
// import io.ktor.http.encodeURLParameter  // For query parameter encoding
// import utils.Page                       // Pagination helper class

// Week 9+ imports (metrics logging, instrumentation):
// import utils.jsMode              // Detect JS mode (htmx/nojs)
// import utils.logValidationError  // Log validation failures
// import utils.timed               // Measure request timing

// Note: Solution repo uses storage.TaskStore instead of data.TaskRepository
// You may refactor to this in Week 10 for production readiness

/**
 * Week 6 Lab 1: Simple task routes with HTMX progressive enhancement.
 *
 * **Teaching approach**: Start simple, evolve incrementally
 * - Week 6: Basic CRUD with Int IDs
 * - Week 7: Add toggle, inline edit
 * - Week 8: Add pagination, search
 */

fun Route.taskRoutes() {
    val pebble =
        PebbleEngine
            .Builder()
            .loader(
                io.pebbletemplates.pebble.loader.ClasspathLoader().apply {
                    prefix = "templates/"
                },
            ).build()

    /**
     * Helper: Check if request is from HTMX
     */
    fun ApplicationCall.isHtmx(): Boolean =
    request.headers["HX-Request"]?.equals("true", ignoreCase = true) == true


    /**
     * GET /tasks - List all tasks or filter by query
     * Returns full page (no HTMX differentiation in Week 6)
     */
    get("/tasks") {
        val startTime = System.currentTimeMillis()
        val query = call.request.queryParameters["query"] ?: ""
        val allTasks = TaskRepository.all()
        val tasks = if (query.isNotEmpty()) allTasks.filter { it.title.contains(query, ignoreCase = true) } else allTasks
        if (query.isNotEmpty()) {
            val duration = System.currentTimeMillis() - startTime
            Logger.write(
                session = call.sessions.get<SessionData>()?.user_id ?: "anon",
                req = generateRequestId(),
                task = "T5_filter",
                step = "submit",
                outcome = "success",
                ms = duration,
                status = 200,
                js = if (call.request.headers["HX-Request"] != null) "htmx" else "standard"
            )
        }
        val model =
            mapOf(
                "title" to "Tasks",
                "tasks" to tasks,
            )
        val template = pebble.getTemplate("tasks/index.peb")
        val writer = StringWriter()
        template.evaluate(writer, model)
        call.respondText(writer.toString(), ContentType.Text.Html)
    }

    /**
     * POST /tasks - Add new task
     * Dual-mode: HTMX fragment or PRG redirect
     */
    post("/tasks") {
        val startTime = System.currentTimeMillis()
        val title = call.receiveParameters()["title"].orEmpty().trim()

        // Validation
        if (title.isBlank()) {
            val duration = System.currentTimeMillis() - startTime
            Logger.write(
                session = call.sessions.get<SessionData>()?.user_id ?: "anon",
                req = generateRequestId(),
                task = "T1_add",
                step = "submit",
                outcome = "error",
                ms = duration,
                status = if (call.isHtmx()) 400 else 302,
                js = if (call.request.headers["HX-Request"] != null) "htmx" else "standard"
            )
            if (call.isHtmx()) {
                val error = """<div id="status" hx-swap-oob="true" role="alert" aria-live="assertive">
                    Title is required. Please enter at least one character.
                </div>"""
                return@post call.respondText(error, ContentType.Text.Html, HttpStatusCode.BadRequest)
            } else {
                // No-JS path: redirect with error flag (handle in GET if needed)
                return@post call.respondRedirect("/tasks?error=required")
            }
        }

        val task = TaskRepository.add(title)
        val duration = System.currentTimeMillis() - startTime
        Logger.write(
            session = call.sessions.get<SessionData>()?.user_id ?: "anon",
            req = generateRequestId(),
            task = "T1_add",
            step = "submit",
            outcome = "success",
            ms = duration,
            status = if (call.isHtmx()) 201 else 302,
            js = if (call.request.headers["HX-Request"] != null) "htmx" else "standard"
        )

        if (call.isHtmx()) {
            // Return HTML fragment for new task
            val fragment = """<li id="task-${task.id}">
                <span>${task.title}</span>
                <button hx-get="/tasks/${task.id}/edit" hx-target="closest li" hx-swap="outerHTML" aria-label="Edit task: ${task.title}">Edit</button>
                <form action="/tasks/${task.id}/delete" method="post" style="display: inline;"
                      hx-post="/tasks/${task.id}/delete"
                      hx-target="#task-${task.id}"
                      hx-swap="outerHTML"
                      hx-confirm="Are you sure you want to delete '${task.title}'?">
                  <button type="submit" class="delete-btn" aria-label="Delete task: ${task.title}">Delete</button>
                </form>
            </li>"""

            val status = """<div id="status" hx-swap-oob="true">Task "${task.title}" added successfully.</div>"""

            return@post call.respondText(fragment + status, ContentType.Text.Html, HttpStatusCode.Created)
        }

        call.respondRedirect("/tasks") // No-JS fallback
    }


    /**
     * POST /tasks/{id}/delete - Delete task
     * Dual-mode: HTMX empty response or PRG redirect
     */
    post("/tasks/{id}/delete") {
        val startTime = System.currentTimeMillis()
        val id = call.parameters["id"]?.toIntOrNull()
        val removed = id?.let { TaskRepository.delete(it) } ?: false

        val duration = System.currentTimeMillis() - startTime
        Logger.write(
            session = call.sessions.get<SessionData>()?.user_id ?: "anon",
            req = generateRequestId(),
            task = "T4_delete",
            step = "submit",
            outcome = "success",
            ms = duration,
            status = if (call.isHtmx()) 200 else 302,
            js = if (call.request.headers["HX-Request"] != null) "htmx" else "standard"
        )

        if (call.isHtmx()) {
            val message = if (removed) "Task deleted." else "Could not delete task."
            val status = """<div id="status" hx-swap-oob="true">$message</div>"""
            // Return empty content to trigger outerHTML swap (removes the <li>)
            return@post call.respondText(status, ContentType.Text.Html)
        }

        call.respondRedirect("/tasks")
    }


    // Week 7 Lab 1 Activity 2 Steps 2-5
    // Add inline edit routes here
    // Follow instructions in mdbook to implement:
    // - GET /tasks/{id}/edit - Show edit form (dual-mode)
    // - POST /tasks/{id}/edit - Save edits with validation (dual-mode)
    // - GET /tasks/{id}/view - Cancel edit (HTMX only)

    get("/tasks/{id}/edit") {
        val id = call.parameters["id"]?.toIntOrNull()
        val task = id?.let { TaskRepository.find(it) }
        if (task == null) {
            call.respondText("Task not found", status = HttpStatusCode.NotFound)
            return@get
        }
        val writer = StringWriter()
        pebble.getTemplate("tasks/task-edit.peb").evaluate(writer, mapOf("task" to task))
        call.respondText(writer.toString(), ContentType.Text.Html)
    }

    patch("/tasks/{id}") {
        val startTime = System.currentTimeMillis()
        val id = call.parameters["id"]?.toIntOrNull()
        val task = id?.let { TaskRepository.find(it) }
        if (task == null) {
            call.respondText("Task not found", status = HttpStatusCode.NotFound)
            return@patch
        }
        val title = call.receiveParameters()["title"].orEmpty().trim()
        if (title.isBlank()) {
            // For simplicity, treat as error, but since HTMX, perhaps render error form or something
            // But to keep simple, update anyway
        }
        task.title = title
        TaskRepository.update(task)
        val duration = System.currentTimeMillis() - startTime
        Logger.write(
            session = call.sessions.get<SessionData>()?.user_id ?: "anon",
            req = generateRequestId(),
            task = "T3_edit",
            step = "submit",
            outcome = "success",
            ms = duration,
            status = 200,
            js = if (call.request.headers["HX-Request"] != null) "htmx" else "standard"
        )
        val writer = StringWriter()
        pebble.getTemplate("tasks/task-item.peb").evaluate(writer, mapOf("task" to task))
        call.respondText(writer.toString(), ContentType.Text.Html)
    }

    get("/tasks/{id}/view") {
        val id = call.parameters["id"]?.toIntOrNull()
        val task = id?.let { TaskRepository.find(it) }
        if (task == null) {
            call.respondText("Task not found", status = HttpStatusCode.NotFound)
            return@get
        }
        val writer = StringWriter()
        pebble.getTemplate("tasks/task-item.peb").evaluate(writer, mapOf("task" to task))
        call.respondText(writer.toString(), ContentType.Text.Html)
    }
}
