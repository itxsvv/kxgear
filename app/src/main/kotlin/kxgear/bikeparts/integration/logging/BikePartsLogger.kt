package kxgear.bikeparts.integration.logging

import android.util.Log

class BikePartsLogger(
    private val tag: String = "KXGear",
) {
    fun debug(message: String) {
        runCatching { Log.d(tag, message) }
            .getOrElse {
                println("$tag DEBUG: $message")
                0
            }
    }

    fun warn(message: String, error: Throwable? = null) {
        runCatching { Log.w(tag, message, error) }
            .getOrElse {
                System.err.println("$tag WARN: $message")
                error?.printStackTrace()
                0
            }
    }

    fun error(message: String, error: Throwable? = null) {
        runCatching { Log.e(tag, message, error) }
            .getOrElse {
                System.err.println("$tag ERROR: $message")
                error?.printStackTrace()
                0
            }
    }
}
