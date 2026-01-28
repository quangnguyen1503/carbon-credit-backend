package com.example.carbon_credit.MatchingEngine;

import com.example.carbon_credit.DTO.PlaceOrderCommandDTO;
import com.example.carbon_credit.DTO.TradeEventDTO;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance Test Suite for Matching Engine
 * 
 * Các bài test đo lường hiệu năng của hệ thống khớp lệnh bao gồm:
 * 1. Throughput: Số lượng lệnh xử lý được trên giây
 * 2. Latency: Thời gian xử lý từng lệnh
 * 3. Concurrency: Khả năng xử lý đồng thời
 * 4. Memory: Tải trọng bộ nhớ dưới áp lực cao
 * 5. Order Book Depth: Hiệu năng khi OrderBook có nhiều levels
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MatchingEnginePerformanceTest {

    private MatchingEngine matchingEngine;
    private Random random;

    // Test configuration
    private static final String CREDIT_ID = "CARBON-TEST-001";
    private static final int WARM_UP_ORDERS = 1000;
    private static final int BENCHMARK_ORDERS = 10000;

    // Performance thresholds (realistic for Java matching engine)
    // Note: 15,000+ orders/sec is good for Java, ~21,000 orders/sec achieved in
    // testing
    // Production-grade C++/Rust engines achieve 500k-1M+ orders/sec
    private static final double MIN_THROUGHPUT = 1500.0; // orders/second
    private static final double MAX_P99_LATENCY_MS = 10.0; // milliseconds

    @BeforeEach
    void setUp() {
        matchingEngine = new MatchingEngine();
        random = new Random(42); // Fixed seed for reproducibility
    }

    // ======================== HELPER METHODS ========================

    /**
     * Tạo lệnh ngẫu nhiên cho testing
     */
    private PlaceOrderCommandDTO createRandomOrder(int index, String orderType) {
        BigDecimal basePrice = new BigDecimal("10.00");
        BigDecimal priceVariation = BigDecimal.valueOf(random.nextDouble() * 2 - 1); // -1 to +1

        return PlaceOrderCommandDTO.builder()
                .orderId("ORD-" + System.nanoTime() + "-" + index)
                .userId("USER-" + (index % 100))
                .creditId(CREDIT_ID)
                .orderType(orderType)
                .orderCondition("LIMIT")
                .price(basePrice.add(priceVariation).max(BigDecimal.ONE))
                .amount(random.nextInt(100) + 1)
                .build();
    }

    /**
     * Tạo lệnh với giá cụ thể
     */
    private PlaceOrderCommandDTO createOrderWithPrice(int index, String orderType, BigDecimal price) {
        return PlaceOrderCommandDTO.builder()
                .orderId("ORD-" + System.nanoTime() + "-" + index)
                .userId("USER-" + (index % 100))
                .creditId(CREDIT_ID)
                .orderType(orderType)
                .orderCondition("LIMIT")
                .price(price)
                .amount(random.nextInt(100) + 1)
                .build();
    }

    /**
     * Warm up matching engine để JIT optimization
     */
    private void warmUp() {
        for (int i = 0; i < WARM_UP_ORDERS; i++) {
            String orderType = i % 2 == 0 ? "BUY" : "SELL";
            PlaceOrderCommandDTO order = createRandomOrder(i, orderType);
            matchingEngine.processOrder(order);
        }
    }

    /**
     * Tính toán percentile
     */
    private long percentile(List<Long> sortedLatencies, double percentile) {
        int index = (int) Math.ceil(percentile / 100.0 * sortedLatencies.size()) - 1;
        return sortedLatencies.get(Math.max(0, index));
    }

    // ======================== PERFORMANCE TESTS ========================

    /**
     * Test 1: Đo throughput - Số lượng lệnh xử lý được mỗi giây
     * Scenario: Gửi liên tục BUY và SELL orders
     */
    @Test
    @Order(1)
    @DisplayName("Test 1: Order Processing Throughput")
    void testOrderProcessingThroughput() {
        warmUp();

        // Reset engine
        matchingEngine = new MatchingEngine();

        int totalOrders = BENCHMARK_ORDERS;
        int totalTrades = 0;

        long startTime = System.nanoTime();

        for (int i = 0; i < totalOrders; i++) {
            String orderType = i % 2 == 0 ? "BUY" : "SELL";
            PlaceOrderCommandDTO order = createRandomOrder(i, orderType);
            List<TradeEventDTO> trades = matchingEngine.processOrder(order);
            totalTrades += trades.size();
        }

        long endTime = System.nanoTime();
        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
        double throughput = totalOrders / durationSeconds;

        System.out.println("\n========== THROUGHPUT TEST RESULTS ==========");
        System.out.printf("Total Orders Processed: %,d%n", totalOrders);
        System.out.printf("Total Trades Generated: %,d%n", totalTrades);
        System.out.printf("Duration: %.3f seconds%n", durationSeconds);
        System.out.printf("Throughput: %,.2f orders/second%n", throughput);
        System.out.printf("Match Rate: %.2f%%%n", (double) totalTrades / totalOrders * 100);
        System.out.println("============================================\n");

        assertTrue(throughput > MIN_THROUGHPUT,
                String.format("Throughput %.2f orders/sec is below minimum %.2f", throughput, MIN_THROUGHPUT));
    }

    /**
     * Test 2: Đo latency - Thời gian xử lý từng lệnh
     * Scenario: Đo P50, P95, P99, P999 latency
     */
    @Test
    @Order(2)
    @DisplayName("Test 2: Order Processing Latency Distribution")
    void testOrderProcessingLatency() {
        warmUp();

        // Reset engine
        matchingEngine = new MatchingEngine();

        List<Long> latencies = new ArrayList<>();

        for (int i = 0; i < BENCHMARK_ORDERS; i++) {
            String orderType = i % 2 == 0 ? "BUY" : "SELL";
            PlaceOrderCommandDTO order = createRandomOrder(i, orderType);

            long start = System.nanoTime();
            matchingEngine.processOrder(order);
            long end = System.nanoTime();

            latencies.add(end - start);
        }

        // Sort for percentile calculation
        Collections.sort(latencies);

        double avgLatencyUs = latencies.stream().mapToLong(Long::longValue).average().orElse(0) / 1000.0;
        double minLatencyUs = latencies.get(0) / 1000.0;
        double maxLatencyUs = latencies.get(latencies.size() - 1) / 1000.0;
        double p50LatencyUs = percentile(latencies, 50) / 1000.0;
        double p95LatencyUs = percentile(latencies, 95) / 1000.0;
        double p99LatencyUs = percentile(latencies, 99) / 1000.0;
        double p999LatencyUs = percentile(latencies, 99.9) / 1000.0;

        System.out.println("\n========== LATENCY TEST RESULTS ==========");
        System.out.printf("Sample Size: %,d orders%n", BENCHMARK_ORDERS);
        System.out.printf("Min Latency:  %.2f µs%n", minLatencyUs);
        System.out.printf("Avg Latency:  %.2f µs%n", avgLatencyUs);
        System.out.printf("P50 Latency:  %.2f µs%n", p50LatencyUs);
        System.out.printf("P95 Latency:  %.2f µs%n", p95LatencyUs);
        System.out.printf("P99 Latency:  %.2f µs%n", p99LatencyUs);
        System.out.printf("P99.9 Latency: %.2f µs%n", p999LatencyUs);
        System.out.printf("Max Latency:  %.2f µs%n", maxLatencyUs);
        System.out.println("==========================================\n");

        double p99LatencyMs = p99LatencyUs / 1000.0;
        assertTrue(p99LatencyMs < MAX_P99_LATENCY_MS,
                String.format("P99 latency %.2f ms exceeds maximum %.2f ms", p99LatencyMs, MAX_P99_LATENCY_MS));
    }

    /**
     * Test 3: Xử lý đồng thời - Multi-threaded order processing
     * Scenario: Nhiều threads gửi orders đồng thời
     */
    @Test
    @Order(3)
    @DisplayName("Test 3: Concurrent Order Processing")
    void testConcurrentOrderProcessing() throws InterruptedException {
        warmUp();

        // Reset engine
        matchingEngine = new MatchingEngine();

        int numThreads = Runtime.getRuntime().availableProcessors();
        int ordersPerThread = BENCHMARK_ORDERS / numThreads;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);

        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Exception> errors = new ConcurrentLinkedQueue<>();

        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready

                    Random threadRandom = new Random(42 + threadId);

                    for (int i = 0; i < ordersPerThread; i++) {
                        String orderType = i % 2 == 0 ? "BUY" : "SELL";
                        PlaceOrderCommandDTO order = PlaceOrderCommandDTO.builder()
                                .orderId("ORD-T" + threadId + "-" + System.nanoTime())
                                .userId("USER-" + (i % 100))
                                .creditId(CREDIT_ID)
                                .orderType(orderType)
                                .orderCondition("LIMIT")
                                .price(BigDecimal.valueOf(10 + threadRandom.nextDouble() * 2 - 1))
                                .amount(threadRandom.nextInt(100) + 1)
                                .build();

                        long start = System.nanoTime();
                        matchingEngine.processOrder(order);
                        long end = System.nanoTime();

                        latencies.add(end - start);
                    }
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        long startTime = System.nanoTime();
        startLatch.countDown(); // Start all threads
        endLatch.await(); // Wait for completion
        long endTime = System.nanoTime();

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Check for errors
        if (!errors.isEmpty()) {
            errors.forEach(e -> e.printStackTrace());
            fail("Errors occurred during concurrent processing: " + errors.size());
        }

        // Calculate metrics
        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
        int totalOrders = numThreads * ordersPerThread;
        double throughput = totalOrders / durationSeconds;

        List<Long> sortedLatencies = new ArrayList<>(latencies);
        Collections.sort(sortedLatencies);
        double avgLatencyUs = sortedLatencies.stream().mapToLong(Long::longValue).average().orElse(0) / 1000.0;
        double p99LatencyUs = percentile(sortedLatencies, 99) / 1000.0;

        System.out.println("\n========== CONCURRENCY TEST RESULTS ==========");
        System.out.printf("Number of Threads: %d%n", numThreads);
        System.out.printf("Orders per Thread: %,d%n", ordersPerThread);
        System.out.printf("Total Orders: %,d%n", totalOrders);
        System.out.printf("Duration: %.3f seconds%n", durationSeconds);
        System.out.printf("Throughput: %,.2f orders/second%n", throughput);
        System.out.printf("Avg Latency: %.2f µs%n", avgLatencyUs);
        System.out.printf("P99 Latency: %.2f µs%n", p99LatencyUs);
        System.out.printf("Errors: %d%n", errors.size());
        System.out.println("==============================================\n");

        assertTrue(errors.isEmpty(), "Concurrent processing should not produce errors");
    }

    /**
     * Test 4: OrderBook Depth Stress Test
     * Scenario: Nhiều price levels khác nhau
     */
    @Test
    @Order(4)
    @DisplayName("Test 4: OrderBook Depth Performance")
    void testOrderBookDepthPerformance() {
        warmUp();

        // Reset engine
        matchingEngine = new MatchingEngine();

        int priceLevels = 1000;
        int ordersPerLevel = 10;

        // Build deep order book with many price levels
        System.out.println("\n========== ORDER BOOK DEPTH TEST ==========");
        System.out.printf("Building OrderBook with %d price levels...%n", priceLevels);

        long buildStart = System.nanoTime();

        // Add BUY orders at different price levels (below mid price)
        for (int level = 0; level < priceLevels / 2; level++) {
            BigDecimal price = BigDecimal.valueOf(5.00 + level * 0.01);
            for (int i = 0; i < ordersPerLevel; i++) {
                PlaceOrderCommandDTO order = createOrderWithPrice(level * ordersPerLevel + i, "BUY", price);
                matchingEngine.processOrder(order);
            }
        }

        // Add SELL orders at different price levels (above mid price)
        for (int level = 0; level < priceLevels / 2; level++) {
            BigDecimal price = BigDecimal.valueOf(15.00 + level * 0.01);
            for (int i = 0; i < ordersPerLevel; i++) {
                PlaceOrderCommandDTO order = createOrderWithPrice(level * ordersPerLevel + i, "SELL", price);
                matchingEngine.processOrder(order);
            }
        }

        long buildEnd = System.nanoTime();
        double buildDuration = (buildEnd - buildStart) / 1_000_000.0;

        System.out.printf("OrderBook build time: %.2f ms%n", buildDuration);
        System.out.printf("Total orders in book: %,d%n", priceLevels * ordersPerLevel);

        // Now test matching performance on deep order book
        List<Long> matchLatencies = new ArrayList<>();
        int matchOrders = 1000;

        for (int i = 0; i < matchOrders; i++) {
            // Alternating aggressive BUY and SELL to match
            String orderType = i % 2 == 0 ? "BUY" : "SELL";
            BigDecimal price = i % 2 == 0
                    ? BigDecimal.valueOf(20.00) // High buy = will match asks
                    : BigDecimal.valueOf(1.00); // Low sell = will match bids

            PlaceOrderCommandDTO order = createOrderWithPrice(i, orderType, price);

            long start = System.nanoTime();
            matchingEngine.processOrder(order);
            long end = System.nanoTime();

            matchLatencies.add(end - start);
        }

        Collections.sort(matchLatencies);
        double avgMatchLatencyUs = matchLatencies.stream().mapToLong(Long::longValue).average().orElse(0) / 1000.0;
        double p99MatchLatencyUs = percentile(matchLatencies, 99) / 1000.0;

        System.out.printf("Match orders tested: %,d%n", matchOrders);
        System.out.printf("Avg Match Latency: %.2f µs%n", avgMatchLatencyUs);
        System.out.printf("P99 Match Latency: %.2f µs%n", p99MatchLatencyUs);
        System.out.println("============================================\n");
    }

    /**
     * Test 5: Cancel Order Performance
     * Scenario: Đo hiệu năng hủy lệnh
     */
    @Test
    @Order(5)
    @DisplayName("Test 5: Cancel Order Performance")
    void testCancelOrderPerformance() {
        // Reset engine
        matchingEngine = new MatchingEngine();

        int numOrders = 5000;
        List<String> orderIds = new ArrayList<>();

        // First, add orders to the book
        for (int i = 0; i < numOrders; i++) {
            String orderType = i % 2 == 0 ? "BUY" : "SELL";
            BigDecimal price = i % 2 == 0
                    ? BigDecimal.valueOf(5.00 + (i % 100) * 0.01)
                    : BigDecimal.valueOf(15.00 + (i % 100) * 0.01);

            PlaceOrderCommandDTO order = createOrderWithPrice(i, orderType, price);
            matchingEngine.processOrder(order);
            orderIds.add(order.getOrderId());
        }

        // Shuffle order IDs for random cancellation
        Collections.shuffle(orderIds);

        // Measure cancel performance
        List<Long> cancelLatencies = new ArrayList<>();
        int successfulCancels = 0;

        for (String orderId : orderIds) {
            long start = System.nanoTime();
            boolean cancelled = matchingEngine.cancelOrder(CREDIT_ID, orderId);
            long end = System.nanoTime();

            if (cancelled) {
                successfulCancels++;
                cancelLatencies.add(end - start);
            }
        }

        if (!cancelLatencies.isEmpty()) {
            Collections.sort(cancelLatencies);
            double avgCancelUs = cancelLatencies.stream().mapToLong(Long::longValue).average().orElse(0) / 1000.0;
            double p99CancelUs = percentile(cancelLatencies, 99) / 1000.0;

            System.out.println("\n========== CANCEL ORDER TEST RESULTS ==========");
            System.out.printf("Total Orders: %,d%n", numOrders);
            System.out.printf("Successful Cancels: %,d%n", successfulCancels);
            System.out.printf("Avg Cancel Latency: %.2f µs%n", avgCancelUs);
            System.out.printf("P99 Cancel Latency: %.2f µs%n", p99CancelUs);
            System.out.println("================================================\n");
        }
    }

    /**
     * Test 6: High Frequency Trading Simulation
     * Scenario: Mô phỏng giao dịch tần suất cao
     */
    @Test
    @Order(6)
    @DisplayName("Test 6: High Frequency Trading Simulation")
    void testHighFrequencyTradingSimulation() {
        warmUp();

        // Reset engine
        matchingEngine = new MatchingEngine();

        int durationSeconds = 5;
        long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);

        int orderCount = 0;
        int tradeCount = 0;
        List<Long> latencies = new ArrayList<>();

        System.out.println("\n========== HFT SIMULATION ==========");
        System.out.printf("Running for %d seconds...%n", durationSeconds);

        long startTime = System.nanoTime();

        while (System.currentTimeMillis() < endTime) {
            // Simulate HFT pattern: quick bursts of orders
            for (int burst = 0; burst < 10; burst++) {
                String orderType = orderCount % 2 == 0 ? "BUY" : "SELL";
                PlaceOrderCommandDTO order = createRandomOrder(orderCount++, orderType);

                long orderStart = System.nanoTime();
                List<TradeEventDTO> trades = matchingEngine.processOrder(order);
                long orderEnd = System.nanoTime();

                latencies.add(orderEnd - orderStart);
                tradeCount += trades.size();
            }
        }

        long actualDuration = System.nanoTime() - startTime;
        double durationSecs = actualDuration / 1_000_000_000.0;
        double throughput = orderCount / durationSecs;

        Collections.sort(latencies);
        double avgLatencyUs = latencies.stream().mapToLong(Long::longValue).average().orElse(0) / 1000.0;
        double p99LatencyUs = percentile(latencies, 99) / 1000.0;

        System.out.printf("Duration: %.2f seconds%n", durationSecs);
        System.out.printf("Orders Processed: %,d%n", orderCount);
        System.out.printf("Trades Generated: %,d%n", tradeCount);
        System.out.printf("Throughput: %,.2f orders/second%n", throughput);
        System.out.printf("Avg Latency: %.2f µs%n", avgLatencyUs);
        System.out.printf("P99 Latency: %.2f µs%n", p99LatencyUs);
        System.out.println("=====================================\n");
    }

    /**
     * Test 7: Memory Stress Test
     * Scenario: Kiểm tra memory footprint dưới áp lực cao
     */
    @Test
    @Order(7)
    @DisplayName("Test 7: Memory Stress Test")
    void testMemoryStress() {
        // Reset engine
        matchingEngine = new MatchingEngine();

        Runtime runtime = Runtime.getRuntime();
        runtime.gc(); // Request garbage collection

        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        int largeOrderCount = 50000;

        System.out.println("\n========== MEMORY STRESS TEST ==========");
        System.out.printf("Initial Memory Usage: %.2f MB%n", memoryBefore / (1024.0 * 1024.0));
        System.out.printf("Adding %,d orders...%n", largeOrderCount);

        // Add many orders without matching
        for (int i = 0; i < largeOrderCount; i++) {
            String orderType = i % 2 == 0 ? "BUY" : "SELL";
            BigDecimal price = i % 2 == 0
                    ? BigDecimal.valueOf(5.00 + (i % 1000) * 0.001)
                    : BigDecimal.valueOf(15.00 + (i % 1000) * 0.001);

            PlaceOrderCommandDTO order = createOrderWithPrice(i, orderType, price);
            matchingEngine.processOrder(order);
        }

        runtime.gc();
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        double memoryIncrease = (memoryAfter - memoryBefore) / (1024.0 * 1024.0);
        double memoryPerOrder = (memoryAfter - memoryBefore) / (double) largeOrderCount;

        System.out.printf("Final Memory Usage: %.2f MB%n", memoryAfter / (1024.0 * 1024.0));
        System.out.printf("Memory Increase: %.2f MB%n", memoryIncrease);
        System.out.printf("Memory per Order: %.2f bytes%n", memoryPerOrder);
        System.out.println("=========================================\n");
    }

    /**
     * Test 8: Market Order Stress Test
     * Scenario: Kiểm tra hiệu năng với lệnh thị trường
     */
    @Test
    @Order(8)
    @DisplayName("Test 8: Market Order Processing")
    void testMarketOrderProcessing() {
        // Reset engine
        matchingEngine = new MatchingEngine();

        // First fill the order book with limit orders
        int limitOrders = 2000;
        for (int i = 0; i < limitOrders; i++) {
            String orderType = i % 2 == 0 ? "BUY" : "SELL";
            BigDecimal price = i % 2 == 0
                    ? BigDecimal.valueOf(9.00 + (i % 100) * 0.01)
                    : BigDecimal.valueOf(11.00 + (i % 100) * 0.01);

            PlaceOrderCommandDTO order = createOrderWithPrice(i, orderType, price);
            matchingEngine.processOrder(order);
        }

        // Now send market orders
        int marketOrders = 1000;
        List<Long> latencies = new ArrayList<>();
        int totalTrades = 0;

        for (int i = 0; i < marketOrders; i++) {
            String orderType = i % 2 == 0 ? "BUY" : "SELL";

            PlaceOrderCommandDTO order = PlaceOrderCommandDTO.builder()
                    .orderId("MKT-" + System.nanoTime() + "-" + i)
                    .userId("USER-" + (i % 100))
                    .creditId(CREDIT_ID)
                    .orderType(orderType)
                    .orderCondition("MARKET")
                    .price(BigDecimal.ZERO) // Market order uses price = 0
                    .amount(random.nextInt(10) + 1)
                    .build();

            long start = System.nanoTime();
            List<TradeEventDTO> trades = matchingEngine.processOrder(order);
            long end = System.nanoTime();

            latencies.add(end - start);
            totalTrades += trades.size();
        }

        Collections.sort(latencies);
        double avgLatencyUs = latencies.stream().mapToLong(Long::longValue).average().orElse(0) / 1000.0;
        double p99LatencyUs = percentile(latencies, 99) / 1000.0;

        System.out.println("\n========== MARKET ORDER TEST RESULTS ==========");
        System.out.printf("Limit Orders in Book: %,d%n", limitOrders);
        System.out.printf("Market Orders Sent: %,d%n", marketOrders);
        System.out.printf("Trades Generated: %,d%n", totalTrades);
        System.out.printf("Avg Latency: %.2f µs%n", avgLatencyUs);
        System.out.printf("P99 Latency: %.2f µs%n", p99LatencyUs);
        System.out.println("================================================\n");
    }

    /**
     * Test 9: Final Summary Report
     */
    @Test
    @Order(9)
    @DisplayName("Test 9: Generate Performance Summary Report")
    void testGenerateSummaryReport() {
        System.out.println("\n");
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║           MATCHING ENGINE PERFORMANCE TEST SUMMARY               ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.println("║ Test Suite Information:                                          ║");
        System.out.printf("║   • Benchmark Orders:     %,d                                  ║%n", BENCHMARK_ORDERS);
        System.out.printf("║   • Warm-up Orders:       %,d                                   ║%n", WARM_UP_ORDERS);
        System.out.printf("║   • Min Throughput:       %,.0f orders/sec                       ║%n", MIN_THROUGHPUT);
        System.out.printf("║   • Max P99 Latency:      %.1f ms                                ║%n", MAX_P99_LATENCY_MS);
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.println("║ Key Performance Indicators:                                      ║");
        System.out.println("║   1. Throughput Test     - Measures orders processed per second  ║");
        System.out.println("║   2. Latency Test        - Measures P50/P95/P99/P99.9 latency    ║");
        System.out.println("║   3. Concurrency Test    - Tests multi-threaded performance      ║");
        System.out.println("║   4. Depth Test          - Tests with many price levels          ║");
        System.out.println("║   5. Cancel Test         - Measures order cancellation speed     ║");
        System.out.println("║   6. HFT Simulation      - Simulates high frequency trading      ║");
        System.out.println("║   7. Memory Test         - Checks memory footprint               ║");
        System.out.println("║   8. Market Order Test   - Tests market order execution          ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
        System.out.println("\n");
    }
}
