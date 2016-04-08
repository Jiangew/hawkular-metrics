/*
 * Copyright 2014-2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.metrics.rest

import org.apache.commons.math3.stat.descriptive.moment.Mean
import org.apache.commons.math3.stat.descriptive.rank.Max
import org.apache.commons.math3.stat.descriptive.rank.Median
import org.apache.commons.math3.stat.descriptive.rank.Min
import org.apache.commons.math3.stat.descriptive.rank.PSquarePercentile
import org.hawkular.metrics.core.service.DateTimeService
import org.joda.time.DateTime
import org.joda.time.Duration
import org.junit.Test

import static java.lang.Double.NaN
import static org.joda.time.DateTime.now
import static org.joda.time.Seconds.seconds
import static org.junit.Assert.assertArrayEquals
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

/**
 * @author Thomas Segismont
 */
class GaugeMetricStatisticsITest extends RESTTest {

  @Test
  void shouldNotAcceptInvalidParams() {
    String tenantId = nextTenantId()
    String metric = "test"

    def response = hawkularMetrics.post(path: "gauges/$metric/raw", body: [
        [timestamp: new DateTimeService().currentHour().minusHours(1).millis, value: 1]
    ], headers: [(tenantHeaderName): tenantId])
    assertEquals(200, response.status)

    badGet(path: "gauges/$metric/stats", query: [buckets: 0], headers: [(tenantHeaderName): tenantId]) { exception ->
      // Bucket count = zero
      assertEquals(400, exception.response.status)
    }

    badGet(path: "gauges/$metric/stats",
        query: [buckets: Integer.MAX_VALUE], headers: [(tenantHeaderName): tenantId]) { exception ->
      // Bucket size = zero
      assertEquals(400, exception.response.status)
    }

    badGet(path: "gauges/$metric/stats", query: [bucketDuration: "1w"], headers: [(tenantHeaderName): tenantId]) {
      exception ->
        // Illegal duration
        assertEquals(400, exception.response.status)
    }

    badGet(path: "gauges/$metric/stats",
        query: [start: 0, end: Long.MAX_VALUE, bucketDuration: "1ms"], headers: [(tenantHeaderName): tenantId]) {
      exception ->
        // Number of buckets is too large
        assertEquals(400, exception.response.status)
    }

    badGet(path: "gauges/$metric/stats",
        query: [buckets: 1, bucketDuration: "1d"], headers: [(tenantHeaderName): tenantId]) { exception ->
      // Both buckets and bucketDuration parameters provided
      assertEquals(400, exception.response.status)
    }

    badGet(
        path: "gauges/$metric/stats/tags/x=1,y=2", headers: [(tenantHeaderName): tenantId]
    ) { exception -> assertEquals(400, exception.response.status) }
  }

  @Test
  void emptyNotEmptyTest() {
    DateTimeService dateTimeService = new DateTimeService()
    String tenantId = nextTenantId()
    String metric = 'n1'
    DateTime start = dateTimeService.currentHour().minusHours(1)
    DateTime end = start.plusHours(1)

    int numBuckets = 10
    long bucketSize = (end.millis - start.millis) / numBuckets
    def buckets = []
    numBuckets.times { buckets.add(start.millis + (it * bucketSize)) }

    def response = hawkularMetrics.post(path: "gauges/$metric/raw", body: [
        [timestamp: buckets[0], value: 12.22],
        [timestamp: buckets[0] + seconds(10).toStandardDuration().millis, value: 15.37],
        [timestamp: buckets[4], value: 25],
        [timestamp: buckets[4] + seconds(15).toStandardDuration().millis, value: 25],
        [timestamp: buckets[9], value: 18.367],
        [timestamp: buckets[9] + seconds(10).toStandardDuration().millis, value: 19.01]
    ], headers: [(tenantHeaderName): tenantId])
    assertEquals(200, response.status)

    response = hawkularMetrics.get(path: "gauges/$metric/stats",
        query: [start: start.millis, end: end.millis, buckets: 10], headers: [(tenantHeaderName): tenantId])
    assertEquals(200, response.status)

    def avg0 = new Mean(), med0 = new PSquarePercentile(50.0), perc95th0 = new PSquarePercentile(95.0)
    [12.22, 15.37].each { value ->
      avg0.increment(value)
      med0.increment(value)
      perc95th0.increment(value)
    }

    def avg9 = new Mean(), med9 = new PSquarePercentile(50.0), perc95th9 = new PSquarePercentile(95.0)
    [18.367, 19.01].each { value ->
      avg9.increment(value)
      med9.increment(value)
      perc95th9.increment(value)
    }

    def expectedData = [
        [
            start: buckets[0], end: buckets[0] + bucketSize, empty: false, min: 12.22,
            avg: avg0.getResult(), median: med0.getResult(), max: 15.37,
            samples: 2
        ], [
            start: buckets[1], end: buckets[1] + bucketSize, empty: true, min: NaN,
            avg  : NaN, median: NaN, max: NaN, samples: 0
        ], [
            start: buckets[2], end: buckets[2] + bucketSize, empty: true, min: NaN,
            avg  : NaN, median: NaN, max: NaN, samples: 0
        ], [
            start: buckets[3], end: buckets[3] + bucketSize, empty: true, min: NaN,
            avg  : NaN, median: NaN, max: NaN, samples: 0
        ], [
            start: buckets[4], end: buckets[4] + bucketSize, empty: false, min: 25.0,
            avg  : 25.0, median: 25.0, max: 25.0, samples: 2
        ],
        [
            start: buckets[5], end: buckets[5] + bucketSize, empty: true, min: NaN,
            avg  : NaN, median: NaN, max: NaN, samples: 0
        ], [
            start: buckets[6], end: buckets[6] + bucketSize, empty: true, min: NaN,
            avg  : NaN, median: NaN, max: NaN, samples: 0
        ], [
            start: buckets[7], end: buckets[7] + bucketSize, empty: true, min: NaN,
            avg  : NaN, median: NaN, max: NaN, samples: 0
        ], [
            start: buckets[8], end: buckets[8] + bucketSize, empty: true, min: NaN,
            avg  : NaN, median: NaN, max: NaN, samples: 0
        ], [
            start: buckets[9], end: buckets[9] + bucketSize, empty: false, min: 18.367,
            avg: avg9.getResult(), median: med9.getResult(), max: 19.01,
            samples: 2
        ]
    ]

    assertNumericBucketsEquals(expectedData, response.data ?: [])
  }

  @Test
  void largeDataSetTest() {
    String tenantId = nextTenantId()
    String metric = "test"
    int nbOfBuckets = 10
    long bucketSize = Duration.standardDays(1).millis
    int interval = Duration.standardMinutes(1).millis
    int sampleSize = (bucketSize / interval) - 1

    def start = new DateTimeService().currentHour().minus(3 * nbOfBuckets * bucketSize)

    def expectedData = []

    for (step in 0..nbOfBuckets - 1) {
      def bucketStart = start.plus(step * bucketSize)

      def sample = createSample(sampleSize)

      def data = [];
      def min = new Min(), avg = new Mean(), median = new PSquarePercentile(50.0), max = new Max(),
          perc95th = new PSquarePercentile(95.0)

      for (int i in 0..sample.size() - 1) {
        data.add([timestamp: bucketStart.plus(i * interval).millis, value: sample[i]])
      }

      sample.reverse().each { value ->
        min.increment(value);
        avg.increment(value);
        median.increment(value);
        max.increment(value);
        perc95th.increment(value);
      }

      def response = hawkularMetrics.post(path: "gauges/$metric/raw",
          body: data, headers: [(tenantHeaderName): tenantId])
      assertEquals(200, response.status)

      expectedData.add([
          start         : bucketStart.millis,
          end           : bucketStart.plus(bucketSize).millis,
          min           : min.getResult(),
          avg           : avg.getResult(),
          median        : median.getResult(),
          max           : max.getResult(),
          samples       : sampleSize,
          empty         : false
      ])
    }

    def response = hawkularMetrics.get(path: "gauges/$metric/stats",
        query: [
            start: start.millis, end: start.plus(nbOfBuckets * bucketSize).millis, bucketDuration: "${bucketSize}ms"
        ],
        headers: [(tenantHeaderName): tenantId])
    assertEquals(200, response.status)

    assertNumericBucketsEquals(expectedData, response.data ?: [])
  }

  @Test
  void findDataForMultipleMetricsByTagsSimpleDownsample() {
    String tenantId = nextTenantId()
    DateTime start = now().minusMinutes(10)

    // Create some metrics
    def response = hawkularMetrics.post(path: 'gauges', body: [
        id  : 'G1',
        tags: ['type': 'cpu_usage', 'host': 'server1', 'env': 'stage']
    ], headers: [(tenantHeaderName): tenantId])
    assertEquals(201, response.status)

    response = hawkularMetrics.post(path: 'gauges', body: [
        id  : 'G2',
        tags: ['type': 'cpu_usage', 'host': 'server2', 'env': 'dev']
    ], headers: [(tenantHeaderName): tenantId])
    assertEquals(201, response.status)

    response = hawkularMetrics.post(path: 'gauges', body: [
        id  : 'G3',
        tags: ['type': 'cpu_usage', 'host': 'server3', 'env': 'stage']
    ], headers: [(tenantHeaderName): tenantId])
    assertEquals(201, response.status)

    // insert data points
    response = hawkularMetrics.post(path: "gauges/raw", body: [
        [
            id: 'G1',
            data: [
                [timestamp: start.millis, value: 37.45],
                [timestamp: start.plusMinutes(1).millis, value: 37.609],
                [timestamp: start.plusMinutes(2).millis, value: 39.11],
                [timestamp: start.plusMinutes(3).millis, value: 44.07],
                [timestamp: start.plusMinutes(4).millis, value: 42.335]
            ]
        ],
        [
            id: 'G2',
            data: [
                [timestamp: start.millis, value: 41.18],
                [timestamp: start.plusMinutes(1).millis, value: 39.55],
                [timestamp: start.plusMinutes(2).millis, value: 40.72],
                [timestamp: start.plusMinutes(3).millis, value: 36.94],
                [timestamp: start.plusMinutes(4).millis, value: 37.64]
            ]
        ],
        [
            id: 'G3',
            data: [
                [timestamp: start.millis, value: 57.12],
                [timestamp: start.plusMinutes(1).millis, value: 57.73],
                [timestamp: start.plusMinutes(2).millis, value: 55.49],
                [timestamp: start.plusMinutes(3).millis, value: 49.19],
                [timestamp: start.plusMinutes(4).millis, value: 35.48]
            ]
        ]
    ], headers: [(tenantHeaderName): tenantId])
    assertEquals(200, response.status)

    // query for data
    response = hawkularMetrics.get(
        path: 'gauges/stats',
        query: [
            start: start.millis,
            end: start.plusMinutes(4).millis,
            buckets: 1,
            tags: 'type:cpu_usage,host:server1|server2'
        ],
        headers: [(tenantHeaderName): tenantId]
    )
    assertEquals(200, response.status)

    assertEquals("Expected to get back one bucket", 1, response.data.size())

    def bucket = response.data[0]

    assertEquals("The start time is wrong", start.millis, bucket.start)
    assertEquals("The end time is wrong", start.plusMinutes(4).millis, bucket.end)
    assertDoubleEquals("The min is wrong", 36.94, bucket.min)
    assertDoubleEquals("The max is wrong", 44.07, bucket.max)
    assertDoubleEquals("The avg is wrong",
        avg([37.45, 37.609, 39.11, 44.07, 41.18, 39.55, 40.72, 36.94]), bucket.avg)
    assertEquals("The [empty] property is wrong", false, bucket.empty)
    assertTrue("Expected the [median] property to be set", bucket.median != null)
  }

  @Test
  void findDataForMultipleMetricsByTagsSumDownsample() {
    String tenantId = nextTenantId()
    DateTime start = now().minusMinutes(10)

    // Create some metrics
    def response = hawkularMetrics.post(path: 'gauges', body: [
        id  : 'G1',
        tags: ['type': 'cpu_usage', 'host': 'server1', 'env': 'stage']
    ], headers: [(tenantHeaderName): tenantId])
    assertEquals(201, response.status)

    response = hawkularMetrics.post(path: 'gauges', body: [
        id  : 'G2',
        tags: ['type': 'cpu_usage', 'host': 'server2', 'env': 'dev']
    ], headers: [(tenantHeaderName): tenantId])
    assertEquals(201, response.status)

    response = hawkularMetrics.post(path: 'gauges', body: [
        id  : 'G3',
        tags: ['type': 'cpu_usage', 'host': 'server3', 'env': 'stage']
    ], headers: [(tenantHeaderName): tenantId])
    assertEquals(201, response.status)

    def m1 = [
      [timestamp: start.millis, value: 37.45],
      [timestamp: start.plusMinutes(1).millis, value: 37.609],
      [timestamp: start.plusMinutes(2).millis, value: 39.11],
      [timestamp: start.plusMinutes(3).millis, value: 44.07],
      [timestamp: start.plusMinutes(4).millis, value: 42.335]
    ]
    def m2 = [
      [timestamp: start.millis, value: 41.18],
      [timestamp: start.plusMinutes(1).millis, value: 39.55],
      [timestamp: start.plusMinutes(2).millis, value: 40.72],
      [timestamp: start.plusMinutes(3).millis, value: 36.94],
      [timestamp: start.plusMinutes(4).millis, value: 37.64]
    ]

    // insert data points
    response = hawkularMetrics.post(path: "gauges/raw", body: [
        [
            id: 'G1',
            data: m1
        ],
        [
            id: 'G2',
            data: m2
        ],
        [
            id: 'G3',
            data: [
                [timestamp: start.millis, value: 57.12],
                [timestamp: start.plusMinutes(1).millis, value: 57.73],
                [timestamp: start.plusMinutes(2).millis, value: 55.49],
                [timestamp: start.plusMinutes(3).millis, value: 49.19],
                [timestamp: start.plusMinutes(4).millis, value: 35.48]
            ]
        ]
    ], headers: [(tenantHeaderName): tenantId])
    assertEquals(200, response.status)

    // query for data
    response = hawkularMetrics.get(
        path: 'gauges/stats',
        query: [
            start: start.millis,
            end: start.plusMinutes(4).millis,
            buckets: 1,
            tags: 'type:cpu_usage,host:server1|server2',
            stacked: true
        ],
        headers: [(tenantHeaderName): tenantId]
    )
    assertEquals(200, response.status)

    assertEquals("Expected to get back one bucket", 1, response.data.size())

    def bucket = response.data[0]

    m1 = m1.take(m1.size() - 1)
    m2 = m2.take(m2.size() - 1)

    assertEquals("The start time is wrong", start.millis, bucket.start)
    assertEquals("The end time is wrong", start.plusMinutes(4).millis, bucket.end)
    assertDoubleEquals("The min is wrong", (m1.min {it.value}).value + (m2.min {it.value}).value, bucket.min)
    assertDoubleEquals("The max is wrong", (m1.max {it.value}).value + (m2.max {it.value}).value, bucket.max)
    assertDoubleEquals("The avg is wrong", avg(m1.collect {it.value}) + avg(m2.collect {it.value}), bucket.avg)
    assertEquals("The [empty] property is wrong", false, bucket.empty)
    assertTrue("Expected the [median] property to be set", bucket.median != null)
  }

  @Test
  void findDataForMultipleMetricsByMetricNamesSumDownsample() {
    String tenantId = nextTenantId()
    DateTime start = now().minusMinutes(10)

    // Create some metrics
    def response = hawkularMetrics.post(path: 'gauges', body: [
        id  : 'G1',
        tags: ['type': 'cpu_usage', 'host': 'server1', 'env': 'stage']
    ], headers: [(tenantHeaderName): tenantId])
    assertEquals(201, response.status)

    response = hawkularMetrics.post(path: 'gauges', body: [
        id  : 'G2',
        tags: ['type': 'cpu_usage', 'host': 'server2', 'env': 'dev']
    ], headers: [(tenantHeaderName): tenantId])
    assertEquals(201, response.status)

    response = hawkularMetrics.post(path: 'gauges', body: [
        id  : 'G3',
        tags: ['type': 'cpu_usage', 'host': 'server3', 'env': 'stage']
    ], headers: [(tenantHeaderName): tenantId])
    assertEquals(201, response.status)

    def g1 = [
      [timestamp: start.millis, value: 37.45],
      [timestamp: start.plusMinutes(1).millis, value: 37.609],
      [timestamp: start.plusMinutes(2).millis, value: 39.11],
      [timestamp: start.plusMinutes(3).millis, value: 44.07],
      [timestamp: start.plusMinutes(4).millis, value: 42.335]
    ]

    def g2 = [
      [timestamp: start.millis, value: 41.18],
      [timestamp: start.plusMinutes(1).millis, value: 39.55],
      [timestamp: start.plusMinutes(2).millis, value: 40.72],
      [timestamp: start.plusMinutes(3).millis, value: 36.94],
      [timestamp: start.plusMinutes(4).millis, value: 37.64]
    ]

    // insert data points
    response = hawkularMetrics.post(path: "gauges/raw", body: [
        [
            id: 'G1',
            data: g1
        ],
        [
            id: 'G2',
            data: g2
        ],
        [
            id: 'G3',
            data: [
                [timestamp: start.millis, value: 57.12],
                [timestamp: start.plusMinutes(1).millis, value: 57.73],
                [timestamp: start.plusMinutes(2).millis, value: 55.49],
                [timestamp: start.plusMinutes(3).millis, value: 49.19],
                [timestamp: start.plusMinutes(4).millis, value: 35.48]
            ]
        ]
    ], headers: [(tenantHeaderName): tenantId])
    assertEquals(200, response.status)

    // query for data
    response = hawkularMetrics.get(
        path: 'gauges/stats',
        query: [
            start: start.millis,
            end: start.plusMinutes(4).millis,
            buckets: 1,
            metrics: ['G1', 'G2'],
            stacked: true
        ],
        headers: [(tenantHeaderName): tenantId]
    )
    assertEquals(200, response.status)

    assertEquals("Expected to get back one bucket", 1, response.data.size())

    def bucket = response.data[0]

    g1 = g1.take(g1.size() - 1)
    g2 = g2.take(g2.size() - 1)

    assertEquals("The start time is wrong", start.millis, bucket.start)
    assertEquals("The end time is wrong", start.plusMinutes(4).millis, bucket.end)
    assertDoubleEquals("The min is wrong", (g1.min {it.value}).value + (g2.min {it.value}).value, bucket.min)
    assertDoubleEquals("The max is wrong", (g1.max {it.value}).value + (g2.max {it.value}).value, bucket.max)
    assertDoubleEquals("The avg is wrong", avg(g1.collect {it.value}) + avg(g2.collect {it.value}), bucket.avg)
    assertEquals("The [empty] property is wrong", false, bucket.empty)
    assertTrue("Expected the [median] property to be set", bucket.median != null)
  }

  @Test
  void findDataForMultipleMetricsByMetricNamesSimpleDownsample() {
    String tenantId = nextTenantId()
    DateTime start = now().minusMinutes(10)

    // Create some metrics
    def response = hawkularMetrics.post(path: 'gauges', body: [
        id  : 'G1',
        tags: ['type': 'cpu_usage', 'host': 'server1', 'env': 'stage']
    ], headers: [(tenantHeaderName): tenantId])
    assertEquals(201, response.status)

    response = hawkularMetrics.post(path: 'gauges', body: [
        id  : 'G2',
        tags: ['type': 'cpu_usage', 'host': 'server2', 'env': 'dev']
    ], headers: [(tenantHeaderName): tenantId])
    assertEquals(201, response.status)

    response = hawkularMetrics.post(path: 'gauges', body: [
        id  : 'G3',
        tags: ['type': 'cpu_usage', 'host': 'server3', 'env': 'stage']
    ], headers: [(tenantHeaderName): tenantId])
    assertEquals(201, response.status)

    // insert data points
    response = hawkularMetrics.post(path: "gauges/raw", body: [
        [
            id: 'G1',
            data: [
                [timestamp: start.millis, value: 37.45],
                [timestamp: start.plusMinutes(1).millis, value: 37.609],
                [timestamp: start.plusMinutes(2).millis, value: 39.11],
                [timestamp: start.plusMinutes(3).millis, value: 44.07],
                [timestamp: start.plusMinutes(4).millis, value: 42.335]
            ]
        ],
        [
            id: 'G2',
            data: [
                [timestamp: start.millis, value: 41.18],
                [timestamp: start.plusMinutes(1).millis, value: 39.55],
                [timestamp: start.plusMinutes(2).millis, value: 40.72],
                [timestamp: start.plusMinutes(3).millis, value: 36.94],
                [timestamp: start.plusMinutes(4).millis, value: 37.64]
            ]
        ],
        [
            id: 'G3',
            data: [
                [timestamp: start.millis, value: 57.12],
                [timestamp: start.plusMinutes(1).millis, value: 57.73],
                [timestamp: start.plusMinutes(2).millis, value: 55.49],
                [timestamp: start.plusMinutes(3).millis, value: 49.19],
                [timestamp: start.plusMinutes(4).millis, value: 35.48]
            ]
        ]
    ], headers: [(tenantHeaderName): tenantId])
    assertEquals(200, response.status)

    // query for data
    response = hawkularMetrics.get(
        path: 'gauges/stats',
        query: [
            start: start.millis,
            end: start.plusMinutes(4).millis,
            buckets: 1,
            metrics: ['G1', 'G2']
        ],
        headers: [(tenantHeaderName): tenantId]
    )
    assertEquals(200, response.status)

    assertEquals("Expected to get back one bucket", 1, response.data.size())

    def bucket = response.data[0]

    assertEquals("The start time is wrong", start.millis, bucket.start)
    assertEquals("The end time is wrong", start.plusMinutes(4).millis, bucket.end)
    assertDoubleEquals("The min is wrong", 36.94, bucket.min)
    assertDoubleEquals("The max is wrong", 44.07, bucket.max)
    assertDoubleEquals("The avg is wrong",
        avg([37.45, 37.609, 39.11, 44.07, 41.18, 39.55, 40.72, 36.94]), bucket.avg)
    assertEquals("The [empty] property is wrong", false, bucket.empty)
    assertTrue("Expected the [median] property to be set", bucket.median != null)
  }

  @Test
  void tagsOrMetricsParamIsRequiredWhenQueryingForDataFromMultipleMetrics() {
    String tenantId = nextTenantId()
    DateTime start = now().minusMinutes(10)

    // Create some metrics
    def response = hawkularMetrics.post(path: 'gauges', body: [
        id  : 'G1',
        tags: ['type': 'cpu_usage', 'host': 'server1', 'env': 'stage']
    ], headers: [(tenantHeaderName): tenantId])
    assertEquals(201, response.status)

    response = hawkularMetrics.post(path: 'gauges', body: [
        id  : 'G2',
        tags: ['type': 'cpu_usage', 'host': 'server2', 'env': 'dev']
    ], headers: [(tenantHeaderName): tenantId])
    assertEquals(201, response.status)

    // query for data
    response = hawkularMetrics.get(
        path: 'gauges/stats',
        query: [
            start: start.millis,
            end: start.plusMinutes(3).millis,
            buckets: 1
        ],
        headers: [(tenantHeaderName): tenantId]
    )
    assertEquals(400, response.status)
  }

  @Test
  void shouldNotAllowTagsAndMetricNameWhenQueryingForDataFromMultipleMetrics() {
    String tenantId = nextTenantId()
    DateTime start = now().minusMinutes(10)

    // Create some metrics
    def response = hawkularMetrics.post(path: 'gauges', body: [
        id  : 'G1',
        tags: ['type': 'cpu_usage', 'host': 'server1', 'env': 'stage']
    ], headers: [(tenantHeaderName): tenantId])
    assertEquals(201, response.status)

    response = hawkularMetrics.post(path: 'gauges', body: [
        id  : 'G2',
        tags: ['type': 'cpu_usage', 'host': 'server2', 'env': 'dev']
    ], headers: [(tenantHeaderName): tenantId])
    assertEquals(201, response.status)

    // query for data
    response = hawkularMetrics.get(
        path: 'gauges/stats',
        query: [
            start: start.millis,
            end: start.plusMinutes(3).millis,
            buckets: 1,
            tags: [type: 'cpu_usage'],
            metrics: ['G2']
        ],
        headers: [(tenantHeaderName): tenantId]
    )
    assertEquals(400, response.status)
  }

  @Test
  void bucketParamIsRequiredWhenQueryingForDataFromMultipleMetrics() {
    // This test verifies that a 400 response is returned when neither the buckets nor bucketsDuration parameter is
    // included in the request.

    String tenantId = nextTenantId()
    DateTime start = now().minusMinutes(10)

    // Create some metrics
    // Create some metrics
    def response = hawkularMetrics.post(path: 'gauges', body: [
        id  : 'G1',
        tags: ['type': 'cpu_usage', 'host': 'server1', 'env': 'stage']
    ], headers: [(tenantHeaderName): tenantId])
    assertEquals(201, response.status)

    // query for data
    response = hawkularMetrics.get(
        path: 'gauges/stats',
        query: [
            start: start.millis,
            end: start.plusMinutes(3).millis,
            tags: 'type:cpu_usage,host:server1|server2'
        ],
        headers: [(tenantHeaderName): tenantId]
    )
    assertEquals(400, response.status)
  }

  @Test
  void fromEarliestWithData() {
    String tenantId = nextTenantId()
    String metric = "testStats"

    def response = hawkularMetrics.post(path: 'gauges', body: [
        id  : '$metric',
        tags: ['type': 'counter_cpu_usage', 'host': 'server1', 'env': 'stage']
    ], headers: [(tenantHeaderName): tenantId])
    assertEquals(201, response.status)

    response = hawkularMetrics.post(path: "gauges/$metric/raw", body: [
        [timestamp: new DateTimeService().currentHour().minusHours(2).millis, value: 2]
    ], headers: [(tenantHeaderName): tenantId])
    assertEquals(200, response.status)

    response = hawkularMetrics.post(path: "gauges/$metric/raw", body: [
        [timestamp: new DateTimeService().currentHour().minusHours(3).millis, value: 3]
    ], headers: [(tenantHeaderName): tenantId])
    assertEquals(200, response.status)

    response = hawkularMetrics.get(path: "gauges/$metric/stats",
        query: [fromEarliest: "true", bucketDuration: "1h"], headers: [(tenantHeaderName): tenantId])

    assertEquals(200, response.status)
    assertEquals(4, response.data.size)

    def expectedArray = [new BigDecimal(3), new BigDecimal(2), 'NaN', 'NaN'].toArray()
    assertArrayEquals(expectedArray, response.data.min.toArray())
    assertArrayEquals(expectedArray, response.data.max.toArray())
    assertArrayEquals(expectedArray, response.data.avg.toArray())
  }

  @Test
  void fromEarliestWithoutDataAndBad() {
    String tenantId = nextTenantId()
    String metric = "testStats"

    def response = hawkularMetrics.post(path: 'gauges', body: [
        id  : '$metric',
        tags: ['type': 'counter_cpu_usage', 'host': 'server1', 'env': 'stage']
    ], headers: [(tenantHeaderName): tenantId])
    assertEquals(201, response.status)

    response = hawkularMetrics.get(path: "gauges/$metric/stats",
      query: [start: 1, end: now().millis, bucketDuration: "1000d"], headers: [(tenantHeaderName): tenantId])
    assertEquals(200, response.status)

    badGet(path: "gauges/$metric/stats",
        query: [fromEarliest: "true", bucketDuration: "a"], headers: [(tenantHeaderName): tenantId]) {
        exception ->
          assertEquals(400, exception.response.status)
    }

    response = hawkularMetrics.get(path: "gauges/$metric/stats",
        query: [fromEarliest: "true", bucketDuration: "1h"], headers: [(tenantHeaderName): tenantId])
    assertEquals(204, response.status)
    assertEquals(null, response.data)

    badGet(path: "gauges/$metric/stats",
      query: [fromEarliest: "true"], headers: [(tenantHeaderName): tenantId]) {
      exception ->
        // From earliest works only with buckets
        assertEquals(400, exception.response.status)
    }
  }

  @Test
  void findTaggedBuckets() {
    String tenantId = nextTenantId()
    String metric = "tagged-buckets"
    DateTime start = now().minusHours(2)

    def response = hawkularMetrics.post(
        path: "gauges/$metric/raw",
        headers: [(tenantHeaderName): tenantId],
        body: [
            [
                timestamp: start.millis,
                value: 23.17,
                tags: [x: '1']
            ],
            [
                timestamp: start.plusMinutes(5).millis,
                value: 14.93,
                tags: [x: '2']
            ],
            [
                timestamp: start.plusMinutes(10).millis,
                value: 31.06,
                tags: [x: '3']
            ],
            [
                timestamp: start.plusMinutes(15).millis,
                value: 25.55,
                tags: [x: '1']
            ],
            [
                timestamp: start.plusMinutes(20).millis,
                value: 16.66,
                tags: [x: '2']
            ],
            [
                timestamp: start.plusMinutes(25).millis,
                value: 34.33,
                tags: [x: '3']
            ]
        ]
    )
    assertEquals(200, response.status)

    response = hawkularMetrics.get(
        path: "gauges/$metric/stats/tags/x:*",
        headers: [(tenantHeaderName): tenantId],
        query: [start: start.millis, end: now().millis]
    )
    assertEquals(200, response.status)
    assertEquals(3, response.data.size())

    def expectedData = [
        'x:1': [
            tags: [x: '1'],
            max: 25.55,
            min: 23.17,
            avg: avg([23.17, 25.55]),
            median: median(23.17, 25.55),
            samples: 2,
        ],
        'x:2': [
            tags: [x: '2'],
            max: 16.66,
            min: 14.93,
            avg: avg([16.66, 14.93]),
            median: median(16.66, 14.93),
            samples: 2,
        ],
        'x:3': [
            tags: [x: '3'],
            max: 34.33,
            min: 31.06,
            avg: avg([34.33, 31.06]),
            median: median(34.33, 31.06),
            samples: 2,
        ]
    ]
    assertTaggedBucketEquals(expectedData['x:1'], response.data['x:1'])
    assertTaggedBucketEquals(expectedData['x:2'], response.data['x:2'])
    assertTaggedBucketEquals(expectedData['x:3'], response.data['x:3'])
  }

  @Test
  void findTaggedDataPointsWithMultipleTagFilters() {
    String tenantId = nextTenantId()
    DateTime start = now().minusHours(2)
    String id = 'G1'

    def response = hawkularMetrics.post(
        path: "gauges/$id/raw",
        headers: [(tenantHeaderName): tenantId],
        body: [
            [
                timestamp: start.millis,
                value: 11.1,
                tags: [x: '1', y: '1', z: '1']
            ],
            [
                timestamp: start.plusMinutes(2).millis,
                value: 13.3,
                tags: [x: '2', y: '2', z: '2']
            ],
            [
                timestamp: start.plusMinutes(4).millis,
                value: 14.4,
                tags: [x: '3', y: '2', z: '3']
            ],
            [
                timestamp: start.plusMinutes(6).millis,
                value: 15.5,
                tags: [x: '1', y: '3', z: '4']
            ]
        ]
    )
    assertEquals(200, response.status)

    response = hawkularMetrics.get(path: "gauges/$id/stats/tags/x:*,y:2,z:2|3", headers: [(tenantHeaderName): tenantId]
    )
    assertEquals(200, response.status)
    assertEquals(2, response.data.size())

    def expectedData = [
        'x:2,y:2,z:2': [
            tags: [x: '2', y: '2', z: '2'],
            max: 13.3,
            min: 13.3,
            avg: 13.3,
            median: 13.3,
            samples: 1
        ],
        'x:3,y:2,z:3': [
            tags: [x: '3', y: '2', z: '3'],
            max: 14.4,
            min: 14.4,
            avg: 14.4,
            median: 14.4,
            samples: 1
        ]
    ]
    assertTaggedBucketEquals(expectedData['x:2,y:2,z:2'], response.data['x:2,y:2,z:2'])
    assertTaggedBucketEquals(expectedData['x:3,y:2,z:3'], response.data['x:3,y:2,z:3'])
  }

  @Test
  void findTaggedBuckets() {
    String tenantId = nextTenantId()
    String metric = "tagged-buckets"
    DateTime start = now().minusHours(2)

    def response = hawkularMetrics.post(
        path: "gauges/$metric/data",
        headers: [(tenantHeaderName): tenantId],
        body: [
            [
                timestamp: start.millis,
                value: 23.17,
                tags: [x: '1']
            ],
            [
                timestamp: start.plusMinutes(5).millis,
                value: 14.93,
                tags: [x: '2']
            ],
            [
                timestamp: start.plusMinutes(10).millis,
                value: 31.06,
                tags: [x: '3']
            ],
            [
                timestamp: start.plusMinutes(15).millis,
                value: 25.55,
                tags: [x: '1']
            ],
            [
                timestamp: start.plusMinutes(20).millis,
                value: 16.66,
                tags: [x: '2']
            ],
            [
                timestamp: start.plusMinutes(25).millis,
                value: 34.33,
                tags: [x: '3']
            ]
        ]
    )
    assertEquals(200, response.status)

    response = hawkularMetrics.get(
        path: "gauges/$metric/data",
        headers: [(tenantHeaderName): tenantId],
        query: [start: start.millis, end: now().millis, tags: 'x:*']
    )
    assertEquals(200, response.status)
    assertEquals(3, response.data.size())

    def expectedData = [
        'x:1': [
            tags: [x: '1'],
            max: 25.55,
            min: 23.17,
            avg: avg([23.17, 25.55]),
            median: median(23.17, 25.55),
            samples: 2,
        ],
        'x:2': [
            tags: [x: '2'],
            max: 16.66,
            min: 14.93,
            avg: avg([16.66, 14.93]),
            median: median(16.66, 14.93),
            samples: 2,
        ],
        'x:3': [
            tags: [x: '3'],
            max: 34.33,
            min: 31.06,
            avg: avg([34.33, 31.06]),
            median: median(34.33, 31.06),
            samples: 2,
        ]
    ]
    assertTaggedBucketEquals(expectedData['x:1'], response.data['x:1'])
    assertTaggedBucketEquals(expectedData['x:2'], response.data['x:2'])
    assertTaggedBucketEquals(expectedData['x:3'], response.data['x:3'])
  }

  private static List<Double> createSample(int sampleSize) {
    def values = new double[sampleSize];
    def random = new Random()
    Arrays.setAll(values, { i -> random.nextDouble() * 1000D })
    return values;
  }

  static double median(double... values) {
    Median median = new Median()
    return median.evaluate(values)
  }

  static void assertTaggedBucketEquals(def expected, def actual) {
    assertDoubleEquals(expected.max, actual.max)
    assertDoubleEquals(expected.min, actual.min)
    assertDoubleEquals(expected.avg, actual.avg)
    assertDoubleEquals(expected.median, actual.median)
    assertEquals(expected.samples, actual.samples)
  }
}
