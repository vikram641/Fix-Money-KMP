package com.fixmoney.shared.data

/**
 * From the old app's `google-services.json`.
 *
 * A Firebase Web API key is not a secret - it ships inside every client binary
 * by design, and access is controlled by Firestore security rules, not by
 * hiding it. The `categories` collection is deliberately world-readable
 * (`allow read: if true`); everything else in this project stays locked.
 */
internal object FirebaseConfig {
    const val PROJECT_ID = "expenseapp-7b500"
    const val API_KEY = "AIzaSyBp-93QyYAariQ5C5ILT1pj8hT1IjZwgfE"
}
