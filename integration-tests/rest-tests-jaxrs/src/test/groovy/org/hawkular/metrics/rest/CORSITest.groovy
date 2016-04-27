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

import static org.hawkular.metrics.api.jaxrs.util.Headers.ACCESS_CONTROL_ALLOW_CREDENTIALS
import static org.hawkular.metrics.api.jaxrs.util.Headers.ACCESS_CONTROL_ALLOW_HEADERS
import static org.hawkular.metrics.api.jaxrs.util.Headers.ACCESS_CONTROL_ALLOW_METHODS
import static org.hawkular.metrics.api.jaxrs.util.Headers.ACCESS_CONTROL_ALLOW_ORIGIN
import static org.hawkular.metrics.api.jaxrs.util.Headers.ACCESS_CONTROL_MAX_AGE
import static org.hawkular.metrics.api.jaxrs.util.Headers.ACCESS_CONTROL_REQUEST_METHOD
import static org.hawkular.metrics.api.jaxrs.util.Headers.DEFAULT_CORS_ACCESS_CONTROL_ALLOW_HEADERS
import static org.hawkular.metrics.api.jaxrs.util.Headers.DEFAULT_CORS_ACCESS_CONTROL_ALLOW_METHODS
import static org.hawkular.metrics.api.jaxrs.util.Headers.ORIGIN
import static org.joda.time.DateTime.now
import static org.junit.Assert.assertEquals
import static org.junit.Assume.assumeTrue

import org.joda.time.DateTime
import org.junit.Before
import org.junit.Test

class CORSITest extends RESTTest {
  static final String testOrigin = System.getProperty("hawkular-metrics.test.origin", "http://test.hawkular.org")
  static final String testAccessControlAllowHeaders = DEFAULT_CORS_ACCESS_CONTROL_ALLOW_HEADERS + ',' +
    System.getProperty("hawkular-metrics.test.access-control-allow-headers");

  @Before
  void assumeIsMavenBuild() {
    def version = System.properties.getProperty('project.version');
    assumeTrue('This test only works in a Maven build', version != null)
  }

  @Test
  void testOptionsWithOrigin() {
    def response = hawkularMetrics.options(path: "ping",
        headers: [
            (ACCESS_CONTROL_REQUEST_METHOD): "POST",
            //this should be ignored by the container and reply with pre-configured headers
            (ACCESS_CONTROL_ALLOW_HEADERS): "test-header",
            (ORIGIN): testOrigin,
        ])

    def responseHeaders = "==== Response Headers = Start  ====\n"
    response.headers.each { responseHeaders += "${it.name} : ${it.value}\n" }
    responseHeaders += "==== Response Headers = End ====\n"

    //Expected a 200 because this is be a pre-flight call that should never reach the resource router
    assertEquals(200, response.status)
    assertEquals(null, response.getData())
    assertEquals(responseHeaders, DEFAULT_CORS_ACCESS_CONTROL_ALLOW_METHODS, response.headers[ACCESS_CONTROL_ALLOW_METHODS].value)
    assertEquals(responseHeaders, testAccessControlAllowHeaders, response.headers[ACCESS_CONTROL_ALLOW_HEADERS].value)
    assertEquals(responseHeaders, testOrigin, response.headers[ACCESS_CONTROL_ALLOW_ORIGIN].value)
    assertEquals(responseHeaders, "true", response.headers[ACCESS_CONTROL_ALLOW_CREDENTIALS].value)
    assertEquals(responseHeaders, (72 * 60 * 60) + "", response.headers[ACCESS_CONTROL_MAX_AGE].value)
  }

  @Test
  void testOptionsWithBadOrigin() {
    badOptions(path: "gauges/test/raw", headers: [
            (ACCESS_CONTROL_REQUEST_METHOD): "OPTIONS",
            (ORIGIN): "*"
    ]) { exception ->
      //Expected 400 because this pre-flight call that should never reach the resource router since
      //the request origin is bad
      assertEquals(400, exception.response.status)
    }

    def wrongSchemeOrigin = testOrigin.replaceAll("http://", "https://")
    badOptions(path: "gauges/test/raw", headers: [
            (ACCESS_CONTROL_REQUEST_METHOD): "GET",
            (ORIGIN): wrongSchemeOrigin
    ]) { exception ->
      //Expected 400 because this call should never reach the resource router since
      //the request origin is bad
      assertEquals(400, exception.response.status)
    }
  }

  @Test
  void testOptionsWithSubdomainOrigin() {
    //construct a subdomain with "tester." as prefix
    def subDomainOrigin = testOrigin.substring(0, testOrigin.indexOf("/") + 2) + "tester." + testOrigin.substring(testOrigin.indexOf("/") + 2)
    def response = hawkularMetrics.options(path: "gauges/test/raw",
        headers: [
            (ACCESS_CONTROL_REQUEST_METHOD): "GET",
            (ORIGIN): subDomainOrigin
        ])

    def responseHeaders = "==== Response Headers = Start  ====\n"
    response.headers.each { responseHeaders += "${it.name} : ${it.value}\n" }
    responseHeaders += "==== Response Headers = End ====\n"

    assertEquals(200, response.status)
    assertEquals(null, response.getData())
    assertEquals(responseHeaders, DEFAULT_CORS_ACCESS_CONTROL_ALLOW_METHODS, response.headers[ACCESS_CONTROL_ALLOW_METHODS].value)
    assertEquals(responseHeaders, testAccessControlAllowHeaders, response.headers[ACCESS_CONTROL_ALLOW_HEADERS].value)
    assertEquals(responseHeaders, subDomainOrigin, response.headers[ACCESS_CONTROL_ALLOW_ORIGIN].value)
    assertEquals(responseHeaders, "true", response.headers[ACCESS_CONTROL_ALLOW_CREDENTIALS].value)
    assertEquals(responseHeaders, (72 * 60 * 60) + "", response.headers[ACCESS_CONTROL_MAX_AGE].value)
  }

  @Test
  void testOptionsWithoutTenantIDAndData() {
    DateTime start = now().minusMinutes(20)
    def tenantId = nextTenantId()

    // First create a couple gauge metrics by only inserting data
    def response = hawkularMetrics.post(path: "gauges/raw", body: [
        [
            id: "m11",
            data: [
                [timestamp: start.millis, value: 1.1],
                [timestamp: start.plusMinutes(1).millis, value: 1.2]
            ]
        ],
        [
            id: "m12",
            data: [
                [timestamp: start.millis, value: 2.1],
                [timestamp: start.plusMinutes(1).millis, value: 2.2]
            ]
        ]
    ], headers: [(tenantHeaderName): tenantId])
    assertEquals(200, response.status)

    // Now query for the gauge metrics
    response = hawkularMetrics.get(path: "metrics", query: [type: "gauge"], headers: [(tenantHeaderName): tenantId])

    assertEquals(200, response.status)
    assertEquals(
        [
            [dataRetention: 7, tenantId: tenantId, id: "m11", type: "gauge"],
            [dataRetention: 7, tenantId: tenantId, id: "m12", type: "gauge"],
        ],
        response.data
    )

    //Send a CORS pre-flight request and make sure it returns no data
    response = hawkularMetrics.options(path: "metrics",
        query: [type: "gauge"],
        headers: [
            (ACCESS_CONTROL_REQUEST_METHOD): "GET",
            (ORIGIN): testOrigin
        ])

    def responseHeaders = "==== Response Headers = Start  ====\n"
    response.headers.each { responseHeaders += "${it.name} : ${it.value}\n" }
    responseHeaders += "==== Response Headers = End ====\n"

    //Expected a 200 because this is be a pre-flight call that should never reach the resource router
    assertEquals(200, response.status)
    assertEquals(null, response.getData())
    assertEquals(responseHeaders, DEFAULT_CORS_ACCESS_CONTROL_ALLOW_METHODS, response.headers[ACCESS_CONTROL_ALLOW_METHODS].value)
    assertEquals(responseHeaders, testAccessControlAllowHeaders, response.headers[ACCESS_CONTROL_ALLOW_HEADERS].value)
    assertEquals(responseHeaders, testOrigin, response.headers[ACCESS_CONTROL_ALLOW_ORIGIN].value)
    assertEquals(responseHeaders, "true", response.headers[ACCESS_CONTROL_ALLOW_CREDENTIALS].value)
    assertEquals(responseHeaders, (72 * 60 * 60) + "", response.headers[ACCESS_CONTROL_MAX_AGE].value)

    //Requery "metrics" endpoint to make sure data gets returned and check headers
    response = hawkularMetrics.get(path: "metrics", query: [type: "gauge"],
        headers: [
            (tenantHeaderName): tenantId,
            (ORIGIN): testOrigin
        ])

    responseHeaders = "==== Response Headers = Start  ====\n"
    response.headers.each { responseHeaders += "${it.name} : ${it.value}\n" }
    responseHeaders += "==== Response Headers = End ====\n"

    assertEquals(200, response.status)
    assertEquals(
        [
            [dataRetention: 7, tenantId: tenantId, id: "m11", type: "gauge"],
            [dataRetention: 7, tenantId: tenantId, id: "m12", type: "gauge"],
        ],
        response.data
    )
    assertEquals(responseHeaders, DEFAULT_CORS_ACCESS_CONTROL_ALLOW_METHODS, response.headers[ACCESS_CONTROL_ALLOW_METHODS].value)
    assertEquals(responseHeaders, testAccessControlAllowHeaders, response.headers[ACCESS_CONTROL_ALLOW_HEADERS].value)
    assertEquals(responseHeaders, testOrigin, response.headers[ACCESS_CONTROL_ALLOW_ORIGIN].value)
    assertEquals(responseHeaders, "true", response.headers[ACCESS_CONTROL_ALLOW_CREDENTIALS].value)
    assertEquals(responseHeaders, (72 * 60 * 60) + "", response.headers[ACCESS_CONTROL_MAX_AGE].value)
  }
}
