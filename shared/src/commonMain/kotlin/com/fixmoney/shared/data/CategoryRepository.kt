package com.fixmoney.shared.data

import com.fixmoney.shared.model.Category
import com.fixmoney.shared.model.DefaultCategories
import com.fixmoney.shared.platform.logWarn
import com.fixmoney.shared.platform.currentTimeMillis
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "CategoryRepository"

/**
 * Categories are global, default data, served from a public Firestore
 * collection - the same source the old app switched to (see the old project's
 * CLAUDE.md, "Categories now come from Firestore").
 *
 * The old app used the Firestore Android SDK. This talks to Firestore's **REST
 * API over Ktor** instead: it needs no SDK, works identically on both
 * platforms, and sidesteps the iosX64 / Kotlin-version constraints that a
 * community Firebase KMP wrapper would bring. Same pattern the migration plan
 * recommends for Gemini in section 6.2.
 */
class CategoryRepository(
    private val client: HttpClient,
    private val settings: Settings
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Survives configuration changes and screen hops; Room will replace it. */
    private var cached: List<Category>? = null

    private companion object {
        const val DISK_KEY = "categories_cache_json"
        const val DISK_AT = "categories_cached_at"

        /**
         * How long the on-disk copy is trusted.
         *
         * The in-memory cache dies with the process, so before this every cold
         * start spent **one Firestore read per category** - seven of them, plus
         * one for the feature flags. On the free plan that is 50,000/day over
         * eight, about 6,000 cold starts, and the category list changes perhaps
         * never. A day of staleness buys back roughly that whole multiple.
         */
        const val DISK_TTL_MILLIS = 24L * 60 * 60 * 1000
    }

    /**
     * Retries like the old `OnboardingViewModel.loadCategoriesWithRetry()` did -
     * a single cold call raced app startup and failed on a fraction of launches,
     * leaving the chip picker permanently empty for that session.
     *
     * Unlike the old app, a total failure falls back to [DefaultCategories]
     * rather than returning nothing: the content is identical to what is seeded
     * in Firestore, so an offline first run shows chips instead of a blank row.
     */
    suspend fun getCategories(): List<Category> {
        cached?.let { return it }

        readDisk(fresh = true)?.let {
            cached = it
            return it
        }

        repeat(3) { attempt ->
            fetch()?.let { fetched ->
                cached = fetched
                writeDisk(fetched)
                return fetched
            }
            if (attempt < 2) delay(1500)
        }

        // Offline or over quota: a stale copy of the real list still beats the
        // seeded defaults, which carry no edit made in Firestore since.
        readDisk(fresh = false)?.let {
            logWarn(TAG, "Firestore unavailable, using the stale on-disk copy")
            cached = it
            return it
        }

        logWarn(TAG, "Firestore fetch failed after 3 attempts, using seeded defaults")
        return DefaultCategories.ALL
    }

    private fun readDisk(fresh: Boolean): List<Category>? {
        val raw = settings.getStringOrNull(DISK_KEY) ?: return null
        if (fresh) {
            val age = currentTimeMillis() - settings.getLong(DISK_AT, 0L)
            // A clock moved backwards leaves a negative age; treat that as stale
            // rather than trusting the copy forever.
            if (age !in 0 until DISK_TTL_MILLIS) return null
        }
        return try {
            json.decodeFromString<List<Category>>(raw).takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            logWarn(TAG, "discarding unreadable category cache: ${e.message}")
            null
        }
    }

    private fun writeDisk(value: List<Category>) {
        try {
            settings.putString(DISK_KEY, json.encodeToString(value))
            settings.putLong(DISK_AT, currentTimeMillis())
        } catch (e: Exception) {
            // Best effort: a cache that cannot be written must never take the
            // category list down with it.
            logWarn(TAG, "could not cache categories: ${e.message}")
        }
    }

    private suspend fun fetch(): List<Category>? = try {
        val url = "https://firestore.googleapis.com/v1/projects/" +
                "${FirebaseConfig.PROJECT_ID}/databases/(default)/documents/categories" +
                "?key=${FirebaseConfig.API_KEY}"

        val response = client.get(url)
        val body = response.bodyAsText()

        if (!response.status.isSuccess()) {
            logWarn(TAG, "Firestore returned ${response.status.value}: $body")
            null
        } else {
            json.decodeFromString<FirestoreList>(body).documents
                .mapNotNull { it.toCategory() }
                .takeIf { it.isNotEmpty() }
        }
    } catch (e: Exception) {
        logWarn(TAG, "Firestore fetch failed: ${e.message}")
        null
    }
}

/**
 * Firestore's REST shape wraps every field in its type:
 * `{"fields": {"name": {"stringValue": "Food"}}}`.
 */
@Serializable
private data class FirestoreList(val documents: List<FirestoreDoc> = emptyList())

@Serializable
private data class FirestoreDoc(val name: String = "", val fields: FirestoreFields? = null) {
    fun toCategory(): Category? {
        val f = fields ?: return null
        val categoryName = f.name?.stringValue?.takeIf { it.isNotBlank() } ?: return null
        // Fall back to the document id when the `id` field is missing.
        val id = f.id?.stringValue?.takeIf { it.isNotBlank() }
            ?: name.substringAfterLast('/').takeIf { it.isNotBlank() }
            ?: return null
        return Category(
            id = id,
            name = categoryName,
            color = f.color?.stringValue ?: "#B0BEC5",
            icon = f.icon?.stringValue ?: "ic_other"
        )
    }
}

@Serializable
private data class FirestoreFields(
    val id: FirestoreString? = null,
    val name: FirestoreString? = null,
    val color: FirestoreString? = null,
    val icon: FirestoreString? = null
)

@Serializable
private data class FirestoreString(val stringValue: String = "")
