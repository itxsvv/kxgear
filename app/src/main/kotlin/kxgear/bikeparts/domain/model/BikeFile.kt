package kxgear.bikeparts.domain.model

data class BikeFile(
    val version: Int = CURRENT_VERSION,
    val bike: Bike,
    val parts: List<Part>,
    val rideCursor: RideCursor = RideCursor(),
    val lastUpdatedAt: Long,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
