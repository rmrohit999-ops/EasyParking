package com.parkease.core.model

/**
 * Safe String->enum parsing for values that came over the wire. Used
 * instead of Moshi enum adapters directly on response DTOs so that a
 * category/status value the client doesn't yet recognize (e.g. the backend
 * ships a new one before this app version does) fails soft to null rather
 * than crashing JSON parsing for the whole response.
 */
inline fun <reified T : Enum<T>> String?.toEnumOrNull(): T? =
    this?.let { value -> enumValues<T>().firstOrNull { it.name == value } }
