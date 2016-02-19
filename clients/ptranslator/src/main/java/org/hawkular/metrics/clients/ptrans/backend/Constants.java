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
package org.hawkular.metrics.clients.ptrans.backend;

import io.vertx.core.http.HttpHeaders;

/**
 * @author Thomas Segismont
 */
public class Constants {

    public static final String METRIC_ADDRESS = "singlemetric";

    public static final CharSequence TENANT_HEADER_NAME = HttpHeaders.createOptimized("Hawkular-Tenant");
    public static final CharSequence PERSONA_HEADER_NAME = HttpHeaders.createOptimized("Hawkular-Persona");
    public static final CharSequence APPLICATION_JSON = HttpHeaders.createOptimized("application/json");

    private Constants() {
        // Defensive
    }
}
