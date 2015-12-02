/*
 * Copyright 2014-2015 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.metrics.api.jaxrs.handler;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import static org.hawkular.metrics.api.jaxrs.filter.TenantFilter.TENANT_HEADER_NAME;
import static org.hawkular.metrics.api.jaxrs.util.ApiUtils.badRequest;
import static org.hawkular.metrics.api.jaxrs.util.ApiUtils.noContent;
import static org.hawkular.metrics.api.jaxrs.util.ApiUtils.serverError;
import static org.hawkular.metrics.core.api.MetricType.COUNTER;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.hawkular.metrics.api.jaxrs.util.ApiUtils;
import org.hawkular.metrics.core.api.ApiError;
import org.hawkular.metrics.core.api.Buckets;
import org.hawkular.metrics.core.api.DataPoint;
import org.hawkular.metrics.core.api.Metric;
import org.hawkular.metrics.core.api.MetricId;
import org.hawkular.metrics.core.api.MetricType;
import org.hawkular.metrics.core.api.exception.MetricAlreadyExistsException;
import org.hawkular.metrics.core.api.param.BucketConfig;
import org.hawkular.metrics.core.api.param.Duration;
import org.hawkular.metrics.core.api.param.Percentiles;
import org.hawkular.metrics.core.api.param.Tags;
import org.hawkular.metrics.core.api.param.TimeRange;
import org.hawkular.metrics.core.impl.Functions;
import org.hawkular.metrics.core.impl.MetricsService;

import rx.Observable;

/**
 * @author Stefan Negrea
 *
 */
@Path("/counters")
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
public class CounterHandler {

    @Inject
    private MetricsService metricsService;

    @HeaderParam(TENANT_HEADER_NAME)
    private String tenantId;

    @POST
    @Path("/")
    public Response createCounter(
            Metric<Long> metric,
            @Context UriInfo uriInfo
    ) {
        if (metric.getType() != null
                && MetricType.UNDEFINED != metric.getType()
                && MetricType.COUNTER != metric.getType()) {
            return badRequest(new ApiError("Metric type does not match " + MetricType
                    .COUNTER.getText()));
        }
        metric = new Metric<>(new MetricId<>(tenantId, COUNTER, metric.getId()),
                metric.getTags(), metric.getDataRetention());
        URI location = uriInfo.getBaseUriBuilder().path("/counters/{id}").build(metric.getMetricId().getName());
        try {
            Observable<Void> observable = metricsService.createMetric(metric);
            observable.toBlocking().lastOrDefault(null);
            return Response.created(location).build();
        } catch (MetricAlreadyExistsException e) {
            String message = "A metric with name [" + e.getMetric().getMetricId().getName() + "] already exists";
            return Response.status(Response.Status.CONFLICT).entity(new ApiError(message)).build();
        } catch (Exception e) {
            return serverError(e);
        }
    }

    @GET
    @Path("/")
    public Response findCounterMetrics(
            @QueryParam("tags") Tags tags) {

        Observable<Metric<Long>> metricObservable = (tags == null)
                ? metricsService.findMetrics(tenantId, COUNTER)
                : metricsService.findMetricsWithFilters(tenantId, tags.getTags(), COUNTER);

        try {
            return metricObservable
                    .toList()
                    .map(ApiUtils::collectionToResponse)
                    .toBlocking()
                    .lastOrDefault(null);
        } catch (PatternSyntaxException e) {
            return badRequest(e);
        } catch (Exception e) {
            return serverError(e);
        }
    }

    @GET
    @Path("/{id}")
    public Response getCounter(@PathParam("id") String id) {
        try {
            return metricsService.findMetric(new MetricId<>(tenantId, COUNTER, id))
                .map(metricDef -> Response.ok(metricDef).build())
                    .switchIfEmpty(Observable.just(noContent()))
                .toBlocking().lastOrDefault(null);
        } catch (Exception e) {
            return serverError(e);
        }
    }

    @GET
    @Path("/{id}/tags")
    public Response getMetricTags(@PathParam("id") String id) {
        try {
            return metricsService.getMetricTags(new MetricId<>(tenantId, COUNTER, id))
                    .map(ApiUtils::valueToResponse)
                    .toBlocking().lastOrDefault(null);
        } catch (Exception e) {
            return serverError(e);
        }
    }

    @PUT
    @Path("/{id}/tags")
    public Response updateMetricTags(
            @PathParam("id") String id,
            Map<String, String> tags) {
        Metric<Long> metric = new Metric<>(new MetricId<>(tenantId, COUNTER, id));
        try {
            metricsService.addTags(metric, tags).toBlocking().lastOrDefault(null);
            return Response.ok().build();
        } catch (IllegalArgumentException e1) {
            return badRequest(new ApiError(e1.getMessage()));
        } catch (Exception e) {
            return serverError(e);
        }
    }

    @DELETE
    @Path("/{id}/tags/{tags}")
    public Response deleteMetricTags(
            @PathParam("id") String id,
            @PathParam("tags") Tags tags) {
        try {
            Metric<Long> metric = new Metric<>(new MetricId<>(tenantId, COUNTER, id));
            metricsService.deleteTags(metric, tags.getTags()).toBlocking().lastOrDefault(null);
            return Response.ok().build();
        } catch (Exception e) {
            return serverError(e);
        }
    }

    @POST
    @Path("/data")
    public Response addData(List<Metric<Long>> counters) {
        Observable<Metric<Long>> metrics = Functions.metricToObservable(tenantId, counters, COUNTER);
        try {
            metricsService.addDataPoints(COUNTER, metrics).toBlocking().lastOrDefault(null);
            return Response.ok().build();
        } catch (Exception e) {
            return serverError(e);
        }
    }

    @POST
    @Path("/{id}/data")
    public Response addData(
            @PathParam("id") String id,
            List<DataPoint<Long>> data
    ) {
        Observable<Metric<Long>> metrics = Functions.dataPointToObservable(tenantId, id, data, COUNTER);
        try {
            metricsService.addDataPoints(COUNTER, metrics).toBlocking().lastOrDefault(null);
            return Response.ok().build();
        } catch (Exception e) {
            return serverError(e);
        }
    }

    @GET
    @Path("/{id}/data")
    public Response findCounterData(
            @PathParam("id") String id,
            @QueryParam("start") Long start,
            @QueryParam("end") Long end,
            @QueryParam("buckets") Integer bucketsCount,
            @QueryParam("bucketDuration") Duration bucketDuration,
            @QueryParam("percentiles") Percentiles percentiles
    ) {
        TimeRange timeRange = new TimeRange(start, end);
        if (!timeRange.isValid()) {
            return badRequest(new ApiError(timeRange.getProblem()));
        }
        BucketConfig bucketConfig = new BucketConfig(bucketsCount, bucketDuration, timeRange);
        if (!bucketConfig.isValid()) {
            return badRequest(new ApiError(bucketConfig.getProblem()));
        }

        MetricId<Long> metricId = new MetricId<>(tenantId, COUNTER, id);
        Buckets buckets = bucketConfig.getBuckets();
        try {
            if (buckets == null) {
                return metricsService.findDataPoints(metricId, timeRange.getStart(), timeRange.getEnd())
                        .toList()
                        .map(ApiUtils::collectionToResponse)
                        .toBlocking()
                        .lastOrDefault(null);
            } else {
                if(percentiles == null) {
                    percentiles = new Percentiles(Collections.<Double>emptyList());
                }

                return metricsService
                        .findCounterStats(metricId, timeRange.getStart(), timeRange.getEnd(), buckets,
                                percentiles.getPercentiles())
                        .map(ApiUtils::collectionToResponse)
                        .toBlocking()
                        .lastOrDefault(null);
           }
        } catch (Exception e) {
            return serverError(e);
        }
    }

    @GET
    @Path("/{id}/rate")
    public Response findRate(
        @PathParam("id") String id,
        @QueryParam("start") Long start,
        @QueryParam("end") Long end,
        @QueryParam("buckets") Integer bucketsCount,
        @QueryParam("bucketDuration") Duration bucketDuration,
        @QueryParam("percentiles") Percentiles percentiles
    ) {
        TimeRange timeRange = new TimeRange(start, end);
        if (!timeRange.isValid()) {
            return badRequest(new ApiError(timeRange.getProblem()));
        }
        BucketConfig bucketConfig = new BucketConfig(bucketsCount, bucketDuration, timeRange);
        if (!bucketConfig.isValid()) {
            return badRequest(new ApiError(bucketConfig.getProblem()));
        }

        MetricId<Long> metricId = new MetricId<>(tenantId, COUNTER, id);
        Buckets buckets = bucketConfig.getBuckets();
        try {
            if (buckets == null) {
                return metricsService
                        .findRateData(metricId, timeRange.getStart(), timeRange.getEnd())
                        .toList()
                        .map(ApiUtils::collectionToResponse)
                        .toBlocking()
                        .lastOrDefault(null);
            } else {
                if(percentiles == null) {
                    percentiles = new Percentiles(Collections.<Double>emptyList());
                }

                return metricsService
                        .findRateStats(metricId, timeRange.getStart(), timeRange.getEnd(), buckets,
                                percentiles.getPercentiles())
                        .map(ApiUtils::collectionToResponse)
                        .toBlocking()
                        .lastOrDefault(null);
            }
        } catch (Exception e) {
            return serverError(e);
        }
    }

    @GET
    @Path("/data")
    public Response findCounterDataStats(
            @QueryParam("start") final Long start,
            @QueryParam("end") final Long end,
            @QueryParam("tags") Tags tags,
            @QueryParam("buckets") Integer bucketsCount,
            @QueryParam("bucketDuration") Duration bucketDuration,
            @QueryParam("percentiles") Percentiles percentiles,
            @QueryParam("metrics") List<String> metricNames,
            @DefaultValue("false") @QueryParam("stacked") Boolean stacked) {

        TimeRange timeRange = new TimeRange(start, end);
        if (!timeRange.isValid()) {
            return badRequest(new ApiError(timeRange.getProblem()));
        }
        BucketConfig bucketConfig = new BucketConfig(bucketsCount, bucketDuration, timeRange);
        if (bucketConfig.isEmpty()) {
            return badRequest(new ApiError("Either the buckets or bucketsDuration parameter must be used"));
        }
        if (!bucketConfig.isValid()) {
            return badRequest(new ApiError(bucketConfig.getProblem()));
        }
        if (metricNames.isEmpty() && (tags == null || tags.getTags().isEmpty())) {
            return badRequest(new ApiError("Either metrics or tags parameter must be used"));
        }
        if (!metricNames.isEmpty() && !(tags == null || tags.getTags().isEmpty())) {
            return badRequest(new ApiError("Cannot use both the metrics and tags parameters"));
        }

        if (percentiles == null) {
            percentiles = new Percentiles(Collections.<Double> emptyList());
        }

        if (metricNames.isEmpty()) {
            return metricsService
                    .findNumericStats(tenantId, MetricType.COUNTER, tags.getTags(), timeRange.getStart(),
                            timeRange.getEnd(),
                            bucketConfig.getBuckets(), percentiles.getPercentiles(), stacked)
                    .map(ApiUtils::collectionToResponse)
                    .toBlocking()
                    .lastOrDefault(null);
        } else {
            return metricsService
                    .findNumericStats(tenantId, MetricType.COUNTER, metricNames, timeRange.getStart(),
                            timeRange.getEnd(), bucketConfig.getBuckets(), percentiles.getPercentiles(), stacked)
                    .map(ApiUtils::collectionToResponse)
                    .toBlocking()
                    .lastOrDefault(null);
        }
    }

    @GET
    @Path("/data/rate")
    public Response findCounterRateDataStats(
            @QueryParam("start") final Long start,
            @QueryParam("end") final Long end,
            @QueryParam("tags") Tags tags,
            @QueryParam("buckets") Integer bucketsCount,
            @QueryParam("bucketDuration") Duration bucketDuration,
            @QueryParam("percentiles") Percentiles percentiles,
            @QueryParam("metrics") List<String> metricNames,
            @DefaultValue("false") @QueryParam("stacked") Boolean stacked) {

        TimeRange timeRange = new TimeRange(start, end);
        if (!timeRange.isValid()) {
            return badRequest(new ApiError(timeRange.getProblem()));
        }
        BucketConfig bucketConfig = new BucketConfig(bucketsCount, bucketDuration, timeRange);
        if (bucketConfig.isEmpty()) {
            return badRequest(new ApiError("Either the buckets or bucketsDuration parameter must be used"));
        }
        if (!bucketConfig.isValid()) {
            return badRequest(new ApiError(bucketConfig.getProblem()));
        }
        if (metricNames.isEmpty() && (tags == null || tags.getTags().isEmpty())) {
            return badRequest(new ApiError("Either metrics or tags parameter must be used"));
        }
        if (!metricNames.isEmpty() && !(tags == null || tags.getTags().isEmpty())) {
            return badRequest(new ApiError("Cannot use both the metrics and tags parameters"));
        }

        if (percentiles == null) {
            percentiles = new Percentiles(Collections.<Double> emptyList());
        }

        if (metricNames.isEmpty()) {
            return metricsService
                    .findNumericStats(tenantId, MetricType.COUNTER_RATE, tags.getTags(), timeRange.getStart(),
                            timeRange.getEnd(),
                            bucketConfig.getBuckets(), percentiles.getPercentiles(), stacked)
                    .map(ApiUtils::collectionToResponse)
                    .toBlocking()
                    .lastOrDefault(null);
        } else {
            return metricsService
                    .findNumericStats(tenantId, MetricType.COUNTER_RATE, metricNames, timeRange.getStart(),
                            timeRange.getEnd(), bucketConfig.getBuckets(), percentiles.getPercentiles(), stacked)
                    .map(ApiUtils::collectionToResponse)
                    .toBlocking()
                    .lastOrDefault(null);
        }
    }
}
