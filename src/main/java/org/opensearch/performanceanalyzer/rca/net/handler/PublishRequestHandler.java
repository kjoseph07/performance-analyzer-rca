/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

/*
 * Copyright 2019-2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.opensearch.performanceanalyzer.rca.net.handler;


import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.performanceanalyzer.PerformanceAnalyzerApp;
import org.opensearch.performanceanalyzer.collectors.StatExceptionCode;
import org.opensearch.performanceanalyzer.collectors.StatsCollector;
import org.opensearch.performanceanalyzer.grpc.FlowUnitMessage;
import org.opensearch.performanceanalyzer.grpc.PublishResponse;
import org.opensearch.performanceanalyzer.grpc.PublishResponse.PublishResponseStatus;
import org.opensearch.performanceanalyzer.rca.framework.metrics.RcaGraphMetrics;
import org.opensearch.performanceanalyzer.rca.net.NodeStateManager;
import org.opensearch.performanceanalyzer.rca.net.ReceivedFlowUnitStore;
import org.opensearch.performanceanalyzer.rca.net.tasks.FlowUnitRxTask;

/** Service handler for the /sendData RPC. */
public class PublishRequestHandler {

    private static final Logger LOG = LogManager.getLogger(PublishRequestHandler.class);
    private final AtomicReference<ExecutorService> executorReference;
    private final NodeStateManager nodeStateManager;
    private final ReceivedFlowUnitStore receivedFlowUnitStore;
    private List<StreamObserver<PublishResponse>> upstreamResponseStreamList =
            Collections.synchronizedList(new ArrayList<>());

    public PublishRequestHandler(
            NodeStateManager nodeStateManager,
            ReceivedFlowUnitStore receivedFlowUnitStore,
            final AtomicReference<ExecutorService> executorReference) {
        this.executorReference = executorReference;
        this.nodeStateManager = nodeStateManager;
        this.receivedFlowUnitStore = receivedFlowUnitStore;
    }

    public StreamObserver<FlowUnitMessage> getClientStream(
            final StreamObserver<PublishResponse> serviceResponse) {
        upstreamResponseStreamList.add(serviceResponse);
        return new SendDataClientStreamUpdateConsumer(serviceResponse);
    }

    public void terminateUpstreamConnections() {
        for (final StreamObserver<PublishResponse> responseStream : upstreamResponseStreamList) {
            /* TODO: We need to check somehow to see if stream was already completed before calling onNext.
            This is causing issues and causes RCA thread to crash in some cases. */
            responseStream.onNext(
                    PublishResponse.newBuilder()
                            .setDataStatus(PublishResponseStatus.NODE_SHUTDOWN)
                            .build());
            responseStream.onCompleted();
        }
        upstreamResponseStreamList.clear();
    }

    private class SendDataClientStreamUpdateConsumer implements StreamObserver<FlowUnitMessage> {

        private final StreamObserver<PublishResponse> serviceResponse;

        SendDataClientStreamUpdateConsumer(final StreamObserver<PublishResponse> serviceResponse) {
            this.serviceResponse = serviceResponse;
        }

        /**
         * Persist the flow unit sent by the client.
         *
         * @param flowUnitMessage The flow unit that the client just streamed to the server.
         */
        @Override
        public void onNext(FlowUnitMessage flowUnitMessage) {
            final ExecutorService executorService = executorReference.get();
            if (executorService != null) {
                try {
                    executorService.execute(
                            new FlowUnitRxTask(
                                    nodeStateManager, receivedFlowUnitStore, flowUnitMessage));
                    PerformanceAnalyzerApp.RCA_GRAPH_METRICS_AGGREGATOR.updateStat(
                            RcaGraphMetrics.NET_BYTES_IN,
                            flowUnitMessage.getGraphNode(),
                            flowUnitMessage.getSerializedSize());
                } catch (final RejectedExecutionException ree) {
                    LOG.warn(
                            "Dropped handling received flow unit because the netwwork threadpool queue is "
                                    + "full");
                    StatsCollector.instance()
                            .logException(
                                    StatExceptionCode.RCA_NETWORK_THREADPOOL_QUEUE_FULL_ERROR);
                }
            }
        }

        /**
         * Client ran into an error while streaming FlowUnits.
         *
         * @param throwable The exception/error that the client encountered.
         */
        @Override
        public void onError(Throwable throwable) {
            LOG.error("Client ran into an error while streaming flow units:", throwable);
        }

        @Override
        public void onCompleted() {
            LOG.debug("Client finished streaming flow units");
            serviceResponse.onNext(buildDataResponse(PublishResponseStatus.SUCCESS));
            serviceResponse.onCompleted();
        }

        private PublishResponse buildDataResponse(final PublishResponseStatus status) {
            return PublishResponse.newBuilder().setDataStatus(status).build();
        }
    }
}
