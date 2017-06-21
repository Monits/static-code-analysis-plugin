/*
 * Copyright 2010-2017 Monits S.A.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
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
    final BigDecimal median

    @SuppressWarnings('DuplicateNumberLiteral')
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

        List<BigDecimal> sorted = []
        sorted.addAll(values)
        sorted.sort()
        double index = sorted.size() / 2.0
        median = (sorted.get((int) Math.floor(index)) + sorted.get((int) Math.ceil(index))) / 2

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

    @Override
    String toString() {
        "DataSeries{ avg: ${average}, min: ${min}, max: ${max}, stdErr: ${standardError}, " +
            "meanStdErr: ${standardErrorOfMean}"
    }
}
