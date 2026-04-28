fun main() {
    val estimator = FuelSmartEstimator(tankCapacity = 50.0, zoneCount = 5)
    val filter = FuelFilter(maxDeviationPercent = 0.1)

    val simulator = FuelSystemSimulator(estimator, filter)
    simulator.runTestScenario()
}