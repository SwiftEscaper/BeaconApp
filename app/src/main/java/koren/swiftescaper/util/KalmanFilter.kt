package koren.swiftescaper.util

class KalmanFilter(
    private val processNoise: Double,
    private val measurementNoise: Double,
    private val estimatedError: Double
) {
    private var estimate: Double = 0.0
    private var errorCovariance: Double = 1.0

    fun update(measurement: Double) {
        // 예측 단계
        val predictedEstimate = estimate
        val predictedErrorCovariance = errorCovariance + processNoise

        // 갱신 단계
        val kalmanGain = predictedErrorCovariance / (predictedErrorCovariance + measurementNoise)
        estimate = predictedEstimate + kalmanGain * (measurement - predictedEstimate)
        errorCovariance = (1 - kalmanGain) * predictedErrorCovariance
    }

    fun getEstimate(): Double = estimate
}