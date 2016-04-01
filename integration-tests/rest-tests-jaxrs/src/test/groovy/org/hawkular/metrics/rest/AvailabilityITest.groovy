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

import static org.joda.time.DateTime.now
import static org.junit.Assert.assertEquals

import org.hawkular.metrics.model.AvailabilityType
import org.joda.time.DateTime
import org.junit.Test

/**
 * @author Thomas Segismont
 */
class AvailabilityITest extends RESTTest {
  def tenantId = nextTenantId()

  @Test
  void shouldNotAcceptInvalidTimeRange() {
    badGet(path: "availability/test/raw", headers: [(tenantHeaderName): tenantId],
        query: [start: 1000, end: 500]) { exception ->
      assertEquals(400, exception.response.status)
    }
  }

  @Test
  void shouldNotAcceptInvalidBucketConfig() {
    badGet(path: "availability/test/stats", headers: [(tenantHeaderName): tenantId],
        query: [start: 500, end: 100, buckets: '10', bucketDuration: '10ms']) { exception ->
      assertEquals("Should fail when both bucket params are specified", 400, exception.response.status)
    }
  }

  @Test
  void shouldNotCreateMetricWithEmptyPayload() {
    badPost(path: "availability", headers: [(tenantHeaderName): tenantId], body: "" /* Empty Body */) { exception ->
      assertEquals(400, exception.response.status)
    }
  }

  @Test
  void shouldNotAddAvailabilityForMetricWithEmptyPayload() {
    badPost(path: "availability/pimpo/raw", headers: [(tenantHeaderName): tenantId],
        body: "" /* Empty Body */) { exception ->
      assertEquals(400, exception.response.status)
    }

    badPost(path: "availability/pimpo/raw", headers: [(tenantHeaderName): tenantId],
        body: [] /* Empty List */) { exception ->
      assertEquals(400, exception.response.status)
    }
  }

  @Test
  void shouldNotAddAvailabilityDataWithEmptyPayload() {
    badPost(path: "availability/raw", headers: [(tenantHeaderName): tenantId],
        body: "" /* Empty Body */) { exception ->
      assertEquals(400, exception.response.status)
    }

    badPost(path: "availability/raw", headers: [(tenantHeaderName): tenantId],
        body: [] /* Empty List */) { exception ->
      assertEquals(400, exception.response.status)
    }
  }

  @Test
  void shouldStoreLargePayloadSize() {
    def availabilityTypes = AvailabilityType.values()
    checkLargePayload("availability", tenantId, { points, int i ->
      points.push([timestamp: i, value: availabilityTypes[i % availabilityTypes.length]])
    })
  }

  @Test
  void testAvailabilityGet() {
    DateTime start = now().minusMinutes(20)
    String metric = 'A1'

    def response = hawkularMetrics.post(path: "availability/$metric/raw", body: [
        [timestamp: start.millis, value: "up"]], headers: [(tenantHeaderName): tenantId])

    assertEquals(200, response.status)

    response = hawkularMetrics.get(path: "availability/$metric", headers: [(tenantHeaderName): tenantId])
    assertEquals(200, response.status)
    assertEquals(metric, response.data.id)
  }

  @Test
  void shouldNotAcceptDataWithEmptyTimestamp() {
    invalidPointCheck("availability", tenantId, [[value: "up"]])
  }

  @Test
  void shouldNotAcceptDataWithNullTimestamp() {
    invalidPointCheck("availability", tenantId, [[timestamp: null, value: "up"]])
  }

  @Test
  void shouldNotAcceptDataWithInvalidTimestamp() {
    invalidPointCheck("availability", tenantId, [[timestamp: "aaa", value: "up"]])
  }

  @Test
  void shouldNotAcceptDataWithEmptyValue() {
    invalidPointCheck("availability", tenantId, [[timestamp: 13]])
  }

  @Test
  void shouldNotAcceptDataWithNullValue() {
    invalidPointCheck("availability", tenantId, [[timestamp: 13, value: null]])
  }

  @Test
  void shouldNotAcceptDataWithInvalidValue() {
    invalidPointCheck("availability", tenantId, [[timestamp: 13, value: ["dsqdqs"]]])
  }

  @Test
  void addTaggedDataPoints() {
    String tenantId = nextTenantId()
    DateTime start = now().minusMinutes(30)
    DateTime end = start.plusMinutes(10)
    String id = 'A1'

    def response = hawkularMetrics.post(
        path: "availability/$id/raw",
        headers: [(tenantHeaderName): tenantId],
        body: [
            [
                timestamp: start.millis,
                value: 'up',
                tags: [x: '1', y: '2']
            ],
            [
                timestamp: start.plusMinutes(1).millis,
                value: 'down',
                tags: [y: '3', z: '5']
            ],
            [
                timestamp: start.plusMinutes(3).millis,
                value: 'up',
                tags: [x: '4', z: '6']
            ]
        ]
    )
    assertEquals(200, response.status)
    response = hawkularMetrics.get(path: "availability/$id/raw", headers: [(tenantHeaderName): tenantId])
    def expectedData = [
        [
            timestamp: start.plusMinutes(3).millis,
            value: 'up',
            tags: [x: '4', z: '6']
        ],
        [
            timestamp: start.plusMinutes(1).millis,
            value: 'down',
            tags: [y: '3', z: '5']
        ],
        [
            timestamp: start.millis,
            value: 'up',
            tags: [x: '1', y: '2']
        ]
    ]
    assertEquals(expectedData, response.data)
  }
}
