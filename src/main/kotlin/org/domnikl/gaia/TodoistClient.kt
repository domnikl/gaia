package org.domnikl.gaia

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class TodoistClient(private val accessToken: String, private val projectId: String, private val client: OkHttpClient) {
    fun createTask(content: String): Response {
        val postBody = "{\"content\": \"$content\", \"due_string\": \"today\", \"due_lang\": \"en\", \"project_id\": \"$projectId\"}"

        val request = Request.Builder()
            .method("POST", postBody.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $accessToken")
            .url("https://api.todoist.com/rest/v1/tasks")
            .build()

        return client.newCall(request).execute()
    }
}
