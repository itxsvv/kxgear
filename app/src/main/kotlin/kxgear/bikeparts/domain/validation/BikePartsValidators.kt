package kxgear.bikeparts.domain.validation

import kxgear.bikeparts.data.storage.RepositoryError
import kxgear.bikeparts.domain.model.Bike

object BikePartsValidators {
    fun requireUniqueBikeName(existingBikes: Collection<Bike>, candidateName: String, ignoreBikeId: String? = null) {
        if (existingBikes.any { it.bikeId != ignoreBikeId && it.name.equals(candidateName, ignoreCase = true) }) {
            throw RepositoryError.Validation("Bike name must be unique: $candidateName")
        }
    }

    fun requireWholeMileage(value: Int) {
        if (value < 0) {
            throw RepositoryError.Validation("Mileage must be a non-negative whole number")
        }
    }

    fun deriveRideDelta(lastAccepted: Int?, incoming: Int): Int {
        requireWholeMileage(incoming)
        if (lastAccepted == null) {
            return incoming
        }
        if (incoming < lastAccepted) {
            throw RepositoryError.Validation("Ride metric value cannot decrease")
        }
        return incoming - lastAccepted
    }
}
