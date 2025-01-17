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

package org.opensearch.performanceanalyzer.reader;

import static org.junit.Assert.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;
import org.jooq.BatchBindStep;
import org.jooq.Record;
import org.jooq.Result;
import org.junit.Test;
import org.opensearch.performanceanalyzer.metrics.AllMetrics;

@SuppressWarnings("serial")
public class OSMetricsSnapshotTests {
    private static final String DB_URL = "jdbc:sqlite:";

    public OSMetricsSnapshotTests() throws ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
        System.setProperty("java.io.tmpdir", "/tmp");
    }

    @Test
    public void perfTest() throws Exception {
        System.out.println("Batch Insert");
        System.out.println("100: " + runBatchTest(100, 1));
        System.out.println("1000: " + runBatchTest(1000, 1));
        System.out.println("10000: " + runBatchTest(10000, 1));
        System.out.println("300000: " + runBatchTest(300000, 1));
        Connection conn = DriverManager.getConnection(DB_URL);
        conn.setAutoCommit(false);
        OSMetricsSnapshot osMetricsSnap = new OSMetricsSnapshot(conn, 1L);

        Thread t1 =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    System.out.println(
                                            "100000: " + runBatchTest(100000, osMetricsSnap));
                                } catch (Exception e) {
                                }
                            }
                        });
        t1.start();

        Thread t2 =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    System.out.println(
                                            "100000: " + runBatchTest(100000, osMetricsSnap));
                                } catch (Exception e) {
                                }
                            }
                        });

        Thread t3 =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    System.out.println(
                                            "100000: " + runBatchTest(100000, osMetricsSnap));
                                } catch (Exception e) {
                                }
                            }
                        });

        t2.start();
        t3.start();
        t1.join();
        t2.join();
        t3.join();
        conn.commit();
    }

    @Test
    public void perfTestDifferentConnections() throws Exception {
        System.out.println("Batch Insert");
        System.out.println("100: " + runBatchTest(100, 1));
        System.out.println("1000: " + runBatchTest(1000, 1));
        System.out.println("10000: " + runBatchTest(10000, 1));
        System.out.println("100000: " + runBatchTest(100000, 1));
        Thread t1 =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    System.out.println("100000: " + runBatchTest(100000, 1L));
                                } catch (Exception e) {
                                }
                            }
                        });
        t1.start();

        Thread t2 =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    System.out.println("100000: " + runBatchTest(100000, 1L));
                                } catch (Exception e) {
                                }
                            }
                        });

        Thread t3 =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    System.out.println("100000: " + runBatchTest(100000, 1L));
                                } catch (Exception e) {
                                }
                            }
                        });

        t2.start();
        t3.start();
        t1.join();
        t2.join();
        t3.join();
    }

    private Long runBatchTest(int iterations, OSMetricsSnapshot osMetricsSnap) throws Exception {
        Map<String, String> dimensions = new HashMap<>();
        AllMetrics.OSMetrics[] metrics = AllMetrics.OSMetrics.values();
        Map<String, Double> osMetrics = new HashMap<>();
        int numMetrics = metrics.length + 2;
        Object[] metricVals = new Object[numMetrics];
        metricVals[0] = "1";
        metricVals[1] = "GC";

        Map<String, Double> metricsMap = getMetricsMap();
        for (int i = 2; i < numMetrics; i++) {
            Double val = metricsMap.get(metrics[i - 2].toString());
            metricVals[i] = val;
            osMetrics.put(metrics[i - 2].toString(), val);
        }

        long mCurrT = System.currentTimeMillis();
        BatchBindStep handle = osMetricsSnap.startBatchPut();
        for (int i = 0; i < iterations; i++) {
            handle.bind(metricVals);
            osMetricsSnap.putMetric(osMetrics, dimensions, 1);
        }
        handle.execute();
        long mFinalT = System.currentTimeMillis();
        return mFinalT - mCurrT;
    }

    private Long runBatchTest(int iterations, long timestamp) throws Exception {
        Connection conn = DriverManager.getConnection(DB_URL);
        OSMetricsSnapshot osMetricsSnap = new OSMetricsSnapshot(conn, timestamp);
        Map<String, String> dimensions = new HashMap<>();
        Map<String, Double> osMetrics = new HashMap<>();
        AllMetrics.OSMetrics[] metrics = AllMetrics.OSMetrics.values();
        int numMetrics = metrics.length + 2;
        Object[] metricVals = new Object[numMetrics];
        metricVals[0] = "1";
        metricVals[1] = "GC";

        Map<String, Double> metricsMap = getMetricsMap();
        for (int i = 2; i < numMetrics; i++) {
            Double val = metricsMap.get(metrics[i - 2].toString());
            metricVals[i] = val;
            osMetrics.put(metrics[i - 2].toString(), val);
        }

        long mCurrT = System.currentTimeMillis();
        conn.setAutoCommit(false);
        BatchBindStep handle = osMetricsSnap.startBatchPut();
        for (int i = 0; i < iterations; i++) {
            handle.bind(metricVals);
            osMetricsSnap.putMetric(osMetrics, dimensions, 1);
        }
        handle.execute();
        conn.commit();
        long mFinalT = System.currentTimeMillis();
        return mFinalT - mCurrT;
    }

    private HashMap<String, Double> getMetricsMap() {
        return new HashMap<String, Double>() {
            {
                this.put(AllMetrics.OSMetrics.Constants.CPU_VALUE, 100d);
                this.put(AllMetrics.OSMetrics.Constants.PAGING_MAJFLT_VALUE, 100d);
                this.put(AllMetrics.OSMetrics.Constants.PAGING_MINFLT_VALUE, 13223.323243d);
                this.put(AllMetrics.OSMetrics.Constants.RUNTIME_VALUE, 22222d);
                this.put(AllMetrics.OSMetrics.Constants.RSS_VALUE, 444d);
                this.put(AllMetrics.OSMetrics.Constants.WAITTIME_VALUE, 2132134d);
                this.put(AllMetrics.OSMetrics.Constants.CTXRATE_VALUE, 3243.21321d);
                this.put(AllMetrics.OSMetrics.Constants.HEAP_ALLOC_VALUE, 32432.324d);
                this.put(AllMetrics.OSMetrics.Constants.READ_THROUGHPUT_VALUE, 23432d);
                this.put(AllMetrics.OSMetrics.Constants.WRITE_THROUGHPUT_VALUE, 32432432d);
                this.put(AllMetrics.OSMetrics.Constants.TOTAL_THROUGHPUT_VALUE, 32423d);
                this.put(AllMetrics.OSMetrics.Constants.READ_SYSCALL_RATE_VALUE, 234324.3432d);
                this.put(AllMetrics.OSMetrics.Constants.WRITE_SYSCALL_RATE_VALUE, 324323432d);
                this.put(AllMetrics.OSMetrics.Constants.TOTAL_SYSCALL_RATE_VALUE, 2342343223d);
                this.put(AllMetrics.OSMetrics.Constants.BLOCKED_TIME_VALUE, 23432.32432d);
                this.put(AllMetrics.OSMetrics.Constants.BLOCKED_COUNT_VALUE, 23432.32432d);
                this.put(AllMetrics.OSMetrics.Constants.WAITED_TIME_VALUE, 23432.32432d);
                this.put(AllMetrics.OSMetrics.Constants.WAITED_COUNT_VALUE, 23432.32432d);
            }
        };
    }

    @Test
    public void testCreateOSMetrics() throws Exception {
        //
        Connection conn = DriverManager.getConnection(DB_URL);
        OSMetricsSnapshot osMetricsSnap = new OSMetricsSnapshot(conn, 7000L);
        // Create OSMetricsSnapshot
        Map<String, Double> metrics = new HashMap<>();
        Map<String, String> dimensions = new HashMap<>();
        dimensions.put("tid", "1");
        dimensions.put("tName", "dummy thread");
        metrics.put(AllMetrics.OSMetrics.CPU_UTILIZATION.toString(), 2.3333d);
        metrics.put(AllMetrics.OSMetrics.PAGING_RSS.toString(), 3.63d);
        osMetricsSnap.putMetric(metrics, dimensions, 7000L);
        dimensions.put("tid", "2");
        metrics.put(AllMetrics.OSMetrics.CPU_UTILIZATION.toString(), 5.0d);
        metrics.put(AllMetrics.OSMetrics.PAGING_RSS.toString(), 3.63d);
        osMetricsSnap.putMetric(metrics, dimensions, 7000L);
        OSMetricsSnapshot os2 = new OSMetricsSnapshot(conn, 12000L);
        dimensions.put("tid", "1");
        metrics.put(AllMetrics.OSMetrics.CPU_UTILIZATION.toString(), 2.3333d);
        metrics.put(AllMetrics.OSMetrics.PAGING_RSS.toString(), 3.63d);
        os2.putMetric(metrics, dimensions, 12000L);
        dimensions.put("tid", "2");
        metrics.put(AllMetrics.OSMetrics.CPU_UTILIZATION.toString(), 3.0d);
        metrics.put(AllMetrics.OSMetrics.PAGING_RSS.toString(), 3.63d);
        os2.putMetric(metrics, dimensions, 12000L);
        dimensions.put("tid", "3");
        metrics.put(AllMetrics.OSMetrics.CPU_UTILIZATION.toString(), 3.0d);
        metrics.put(AllMetrics.OSMetrics.PAGING_RSS.toString(), 3.63d);
        os2.putMetric(metrics, dimensions, 12000L);

        OSMetricsSnapshot osFinal = new OSMetricsSnapshot(conn, 3L);
        OSMetricsSnapshot.alignWindow(osMetricsSnap, os2, osFinal.getTableName(), 5000L, 10000L);
        Result<Record> res = osFinal.fetchAll();
        // System.out.println(res);
        Double cpu =
                Double.parseDouble(
                        res.get(0).get(AllMetrics.OSMetrics.CPU_UTILIZATION.toString()).toString());
        assertEquals(cpu.doubleValue(), 2.3333d, 0);
        cpu =
                Double.parseDouble(
                        res.get(1).get(AllMetrics.OSMetrics.CPU_UTILIZATION.toString()).toString());
        assertEquals(cpu.doubleValue(), 3.8d, 0);
    }

    @Test
    public void testAlignWindow() throws Exception {
        //
        Connection conn = DriverManager.getConnection(DB_URL);
        OSMetricsSnapshot osMetricsSnap = new OSMetricsSnapshot(conn, 5000L);
        // Create OSMetricsSnapshot
        Map<String, Double> metrics = new HashMap<>();
        Map<String, String> dimensions = new HashMap<>();
        dimensions.put("tid", "1");
        dimensions.put("tName", "dummy thread");
        metrics.put("CPU_Utilization", 10d);
        osMetricsSnap.putMetric(metrics, dimensions, 7000L);
        dimensions.put("tid", "2");
        metrics.put("CPU_Utilization", 20d);
        osMetricsSnap.putMetric(metrics, dimensions, 8000L);
        OSMetricsSnapshot os2 = new OSMetricsSnapshot(conn, 10000L);
        dimensions.put("tid", "1");
        metrics.put("CPU_Utilization", 20d);
        os2.putMetric(metrics, dimensions, 13000L);
        dimensions.put("tid", "3");
        metrics.put("CPU_Utilization", 30d);
        os2.putMetric(metrics, dimensions, 12000L);

        OSMetricsSnapshot osFinal = new OSMetricsSnapshot(conn, 3L);
        OSMetricsSnapshot.alignWindow(osMetricsSnap, os2, osFinal.getTableName(), 5000L, 10000L);
        Result<Record> res = osFinal.fetchAll();
        assertEquals(3, res.size());
        // System.out.println(res);
        Double cpu = Double.parseDouble(res.get(0).get("CPU_Utilization").toString());
        assertEquals(cpu.doubleValue(), 16d, 0);
        cpu = Double.parseDouble(res.get(1).get("CPU_Utilization").toString());
        assertEquals(cpu.doubleValue(), 20, 0);
        cpu = Double.parseDouble(res.get(2).get("CPU_Utilization").toString());
        assertEquals(cpu.doubleValue(), 30, 0);
    }
}
