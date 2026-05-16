fun main() {
    val estimator = FuelSmartEstimator(tankCapacity = 57.0, zoneCount = 11)
    val filter = FuelFilter(maxDeviationPercent = 0.6)

    val simulator = FuelSystemSimulator(estimator, filter)
    simulator.runTestScenario()
}