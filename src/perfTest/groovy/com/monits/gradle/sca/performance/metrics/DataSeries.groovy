package com.monits.gradle.sca.performance.metrics

import groovy.transform.CompileStatic

/**
 * A series of data, with some statistical data associated
 */
@CompileStatic
class DataSeries extends ArrayList<BigDecimal> {
    final BigDecimal average
    final BigDecimal max
    final BigDecimal min
    // https://en.wikipedia.org/wiki/Standard_error
    final BigDecimal standardError
    // https://en.wikipedia.org/wiki/Standard_error#Standard_error_of_the_mean
    final BigDecimal standardErrorOfMean

    DataSeries(final Iterable<? extends BigDecimal> values) {
        for (BigDecimal value : values) {
            if (value != null) {
                add(value)
            }
        }

        if (isEmpty()) {
            average = null
            max = null
            min = null
            standardError = null
            standardErrorOfMean = null
            return
        }

        BigDecimal total = get(0)
        BigDecimal min = get(0)
        BigDecimal max = get(0)
        for (int i = 1; i < size(); i++) {
            BigDecimal amount = get(i)
            total = total + amount
            min = min < amount ? min : amount
            max = max > amount ? max : amount
        }
        average = total / size()
        this.min = min
        this.max = max

        BigDecimal sumSquares = BigDecimal.ZERO
        for (int i = 0; i < size(); i++) {
            BigDecimal diff = get(i)
            diff = diff - average
            diff = diff * diff
            sumSquares = sumSquares + diff
        }
        // This isn't quite right, as we may loose precision when converting to a double
        standardError = BigDecimal.valueOf(Math.sqrt(
                sumSquares.divide(BigDecimal.valueOf(size()), BigDecimal.ROUND_HALF_UP).doubleValue())
            ).setScale(2, BigDecimal.ROUND_HALF_UP)
        standardErrorOfMean = standardError / Math.sqrt(size())
    }
}
