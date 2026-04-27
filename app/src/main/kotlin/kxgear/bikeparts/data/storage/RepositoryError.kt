package kxgear.bikeparts.data.storage

sealed class RepositoryError(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    class NotFound(message: String) : RepositoryError(message)
    class Validation(message: String) : RepositoryError(message)
    class CorruptState(message: String, cause: Throwable? = null) : RepositoryError(message, cause)
    class WriteFailure(message: String, cause: Throwable? = null) : RepositoryError(message, cause)
}
