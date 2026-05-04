package kxgear.bikeparts.domain.service

data class KarooBikeSnapshot(
    val karooBikeId: String,
    val name: String,
    val mileageMeters: Int,
)

interface KarooBikeCatalogGateway {
    suspend fun listBikes(): List<KarooBikeSnapshot>
}
