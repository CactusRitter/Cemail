package com.cemail.app.data.mail

import com.ce.kit.log.AppLog
import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.entityextraction.DateTimeEntity
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Finds dates and times in a text with ML Kit Entity Extraction. It runs on the phone (the text goes nowhere); only
 * the language model is downloaded from Google the first time. Without model or network it finds nothing.
 */
object TextDates {
    suspend fun find(text: String, language: String, sent: Long): List<FoundDate> {
        if (text.isBlank()) return emptyList()
        val model = when (language) {
            "es" -> EntityExtractorOptions.SPANISH
            "de" -> EntityExtractorOptions.GERMAN
            else -> EntityExtractorOptions.ENGLISH
        }
        return try {
            val extractor = EntityExtraction.getClient(EntityExtractorOptions.Builder(model).build())
            try {
                extractor.downloadModelIfNeeded().await()
                val params = EntityExtractionParams.Builder(text)
                    .setEntityTypesFilter(setOf(Entity.TYPE_DATE_TIME))
                    .setReferenceTime(sent)
                    .setReferenceTimeZone(TimeZone.getDefault())
                    .setPreferredLocale(Locale.forLanguageTag(language))
                    .build()
                extractor.annotate(params).await().mapNotNull { annotation ->
                    val date = annotation.entities.filterIsInstance<DateTimeEntity>().firstOrNull() ?: return@mapNotNull null
                    // A month, a week or a year alone is not a date to meet.
                    if (date.dateTimeGranularity < DateTimeEntity.GRANULARITY_DAY) return@mapNotNull null
                    FoundDate(
                        annotation.start, annotation.end, date.timestampMillis,
                        hasTime = date.dateTimeGranularity >= DateTimeEntity.GRANULARITY_HOUR,
                    )
                }
            } finally {
                extractor.close()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("dates", "no dates read from the text", e)
            emptyList()
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }
}
