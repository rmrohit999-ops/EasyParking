package com.parkease.core.maps

/** True when a real Maps API key was supplied at build time (local.properties `MAPS_API_KEY` or the `MAPS_API_KEY_ANDROID` CI secret). */
fun mapsConfigured(): Boolean = BuildConfig.MAPS_API_KEY.isNotBlank()
