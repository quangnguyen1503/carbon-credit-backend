# Phân Tích Luồng Hoạt Động Hệ Thống Carbon Credit

## Mục Lục
1. [Luồng Retire (Thu hồi Carbon Credit)](#1-luồng-retire-thu-hồi-carbon-credit)
2. [Luồng Đặt Order (Place Order)](#2-luồng-đặt-order-place-order)
3. [Luồng Matching Order (Khớp Lệnh)](#3-luồng-matching-order-khớp-lệnh)
4. [Cách Spring Lắng Nghe Sự Kiện Blockchain](#4-cách-spring-lắng-nghe-sự-kiện-blockchain)

---

## 1. Luồng Retire (Thu hồi Carbon Credit)

### Tổng Quan
Retire là quá trình thu hồi (retire) carbon credit từ người dùng và tạo chứng chỉ (certificate) NFT trên blockchain. Hệ thống lắng nghe sự kiện `BATCH_CERTIFICATE_RETIRED` từ blockchain để xử lý.

### Luồng Chi Tiết

#### Bước 1: Sự Kiện Blockchain Được Phát Sinh
- Người dùng thực hiện giao dịch retire trên smart contract
- Smart contract phát sinh event `BATCH_CERTIFICATE_RETIRED` với các thông tin:
  - `certificateTokenId` (NFT token ID)
  - `retiredBy` (địa chỉ người retire)
  - `totalValue` (tổng giá trị)
  - `recordCount` (số lượng records)

#### Bước 2: BlockPollerService Phát Hiện Event
```java
// BlockPollerService.java - Line 63-112
@Scheduled(fixedDelay = 3000) // Mỗi 3 giây
public void pollBlocks()
```
- Service này chạy định kỳ mỗi 3 giây để quét các block mới
- Sử dụng `web3j.ethGetLogs()` để lấy events từ blockchain
- Lọc events từ các contract được cấu hình trong `ChainConstants`

#### Bước 3: Chuyển Event Vào Kafka
```java
// BlockPollerService.java - Line 186-220
private void processSingleLog(Log logData) {
    // Xác định loại event từ event hash
    String eventType = identifyEventType(eventHash);
    
    // Tạo BlockchainEventDTO
    BlockchainEventDTO eventDTO = new BlockchainEventDTO();
    eventDTO.setEventType("BATCH_CERTIFICATE_RETIRED");
    // ... set các thông tin khác
    
    // Gửi vào Kafka topic "onchain-events"
    kafkaProducer.sendOnChainEvent(eventDTO);
}
```

#### Bước 4: KafkaConsumerService Xử Lý Event
```java
// KafkaConsumerService.java - Line 148-204
@KafkaListener(topics = "onchain-events", groupId = "carbon-market-group")
public void consumeBlockchainEvent(BlockchainEventDTO event, Acknowledgment ack)
```
- Consumer nhận event từ Kafka
- Có cơ chế retry (tối đa 5 lần) để xử lý lỗi
- Gọi `processEvent(event)` để định tuyến đến handler phù hợp

#### Bước 5: CertificateService Xử Lý Retire
```java
// KafkaConsumerService.java - Line 234-236
case "BATCH_CERTIFICATE_RETIRED" -> {
    certificateService.handleBatchCeritificateRetired(event);
}
```

#### Bước 6: Xử Lý Chi Tiết Trong CertificateService
```java
// CertificateService.java - Line 177-230
@Transactional
public void handleBatchCeritificateRetired(BlockchainEventDTO event)
```

**Các bước xử lý:**

1. **Kiểm tra trùng lặp:**
   ```java
   if (processedTransactionRepository.existsByTxHash(event.getTransactionHash())) {
       log.warn("Transaction {} already processed. Skipping.", ...);
       return;
   }
   ```

2. **Giải mã dữ liệu từ event:**
   ```java
   BigInteger certificateTokenId = BlockchainHelper.extractUint256FromTopic(event, 1);
   String retiredBy = BlockchainHelper.extractAddressFromTopic(event, 2);
   BigInteger totalValue = (BigInteger) decoded.get(0).getValue();
   ```

3. **Tạo Certificate mới:**
   ```java
   Certificate certificate = Certificate.builder()
       .id(UUID.randomUUID().toString())
       .userId(retiredBy)
       .totalAmount(totalValue)
       .nftTokenId(certificateTokenId)
       .txHash(event.getTransactionHash())
       .createdAt(LocalDateTime.now())
       .build();
   certificateRepository.save(certificate);
   ```

4. **Lấy chi tiết records từ blockchain:**
   ```java
   getRecordFromChain(certificate);
   ```
   - Gọi smart contract để lấy danh sách records chi tiết
   - Mỗi record chứa: `tokenId`, `amount`

5. **Cập nhật số dư người dùng:**
   ```java
   // CertificateService.java - Line 254-287
   private void updateUserBalance(String userAddress, BigInteger creditTokenId, BigInteger amount)
   ```
   - Trừ `availableBalance` từ `WalletCredit`
   - Tăng `retiredAmount` trong `CarbonCredit`
   - Sử dụng synchronized lock để tránh race condition

6. **Lưu ProcessedTransaction:**
   ```java
   ProcessedTransaction processedTx = ProcessedTransaction.builder()
       .txHash(event.getTransactionHash())
       .eventType(event.getEventType())
       .processedAt(LocalDateTime.now())
       .build();
   processedTransactionRepository.save(processedTx);
   ```

### Sơ Đồ Luồng Retire

```
Blockchain Event (BATCH_CERTIFICATE_RETIRED)
    ↓
BlockPollerService (pollBlocks - mỗi 3s)
    ↓
Kafka Topic: "onchain-events"
    ↓
KafkaConsumerService.consumeBlockchainEvent()
    ↓
CertificateService.handleBatchCeritificateRetired()
    ↓
├─ Kiểm tra trùng lặp
├─ Giải mã event data
├─ Tạo Certificate
├─ Lấy records từ blockchain
├─ Cập nhật WalletCredit (trừ availableBalance)
├─ Cập nhật CarbonCredit (tăng retiredAmount)
└─ Lưu ProcessedTransaction
```

---

## 2. Luồng Đặt Order (Place Order)

### Tổng Quan
Place Order là quá trình người dùng đặt lệnh mua/bán carbon credit. Hệ thống xử lý validation, lock balance trên blockchain, và gửi order vào matching engine qua Kafka.

### Luồng Chi Tiết

#### Bước 1: API Endpoint Nhận Request
```java
// OrderController.java - Line 46-113
@PostMapping("/place")
public ResponseEntity<?> placeOrder(
    @Valid @RequestBody PlaceOrderCommandDTO request,
    Authentication authentication)
```

**Validation ban đầu:**
- Kiểm tra carbon credit tồn tại
- Kiểm tra project đã được APPROVED
- Với SELL order: kiểm tra quyền sở hữu và số lượng available

#### Bước 2: TradingService.placeOrder()
```java
// TradingService.java - Line 43-170
@Transactional
public Order placeOrder(PlaceOrderCommandDTO request, String userId)
```

**Các bước xử lý:**

1. **Xử lý Market Order:**
   ```java
   if (isMarket) {
       if (isBuy) {
           // Lấy bestAsk từ OrderBook
           BigDecimal bestAsk = (BigDecimal) snapshot.get("bestAsk");
           // Tính giá với slippage buffer (5%)
           calculationPrice = bestAsk.multiply(SLIPPAGE_BUFFER);
       } else {
           calculationPrice = BigDecimal.ZERO; // Sẽ được xử lý trong matching
       }
   }
   ```

2. **Kiểm tra số dư trên blockchain:**
   ```java
   if (request.getOrderType().equalsIgnoreCase("BUY")) {
       BigInteger nativeBalance = contractService.getNativeBalance(userId);
       if (nativeBalance.compareTo(totalValue) < 0) {
           throw new IllegalArgumentException("Insufficient native balance");
       }
   } else if (request.getOrderType().equalsIgnoreCase("SELL")) {
       BigInteger creditBalance = contractService.getCreditBalance(userId, creditTokenId);
       if (creditBalance.compareTo(amount) < 0) {
           throw new IllegalArgumentException("Insufficient credit balance");
       }
   }
   ```

3. **Tạo Order Entity và lưu DB:**
   ```java
   Order order = Order.builder()
       .id(UUID.randomUUID().toString())
       .userId(userId)
       .creditId(request.getCreditId())
       .orderType(request.getOrderType())
       .orderCondition(request.getOrderCondition())
       .price(request.getPrice())
       .amount(request.getAmount())
       .remainingAmount(request.getAmount())
       .status("PENDING")
       .createdAt(LocalDateTime.now())
       .build();
   orderRepository.save(order);
   ```

4. **Lock balance trên blockchain:**
   ```java
   // TradingService.java - Line 122-141
   try {
       BigInteger creditTokenId = new BigInteger(request.getCreditId());
       boolean isCreditToken = request.getOrderType().equalsIgnoreCase("SELL");
       BigInteger lockAmount = isCreditToken ? amount : totalValue;
       
       // Gọi smart contract để lock balance
       contractService.lockBalance(orderId, userId, creditTokenId, lockAmount, isCreditToken);
       
       order.setStatus("OPEN");
       orderRepository.save(order);
   } catch (Exception e) {
       order.setStatus("FAILED");
       orderRepository.save(order);
       throw new RuntimeException("Failed to lock balance on blockchain");
   }
   ```

5. **Gửi Order vào Kafka:**
   ```java
   // TradingService.java - Line 143-155
   PlaceOrderCommandDTO command = PlaceOrderCommandDTO.builder()
       .orderId(order.getId())
       .userId(userId)
       .creditId(request.getCreditId())
       .orderType(request.getOrderType())
       .orderCondition(request.getOrderCondition())
       .price(isMarket ? BigDecimal.ZERO : request.getPrice())
       .amount(request.getAmount())
       .build();
   
   kafkaProducerService.sendOrder(command);
   ```

6. **Thông báo qua WebSocket:**
   ```java
   wsService.notify(
       String.format("Đặt %s thành công", order.getOrderType()),
       String.format("Đặt %s với giá: %s và số lượng %s.", ...),
       "SUCCESS", null, userId.toLowerCase());
   ```

### Sơ Đồ Luồng Place Order

```
API Request (POST /api/orders/place)
    ↓
OrderController.placeOrder()
    ↓
├─ Validate: Credit exists, Project approved, Ownership
    ↓
TradingService.placeOrder()
    ↓
├─ Xử lý Market Order (tính giá từ bestAsk)
├─ Kiểm tra số dư trên blockchain
├─ Tạo Order entity (status: PENDING)
├─ Lock balance trên blockchain (ContractService.lockBalance)
│  └─ Nếu thành công: status = OPEN
│  └─ Nếu thất bại: status = FAILED
├─ Gửi vào Kafka topic "orders"
└─ Thông báo WebSocket
```

---

## 3. Luồng Matching Order (Khớp Lệnh)

### Tổng Quan
Matching Engine sử dụng thuật toán Price-Time Priority để khớp lệnh. Orders được xử lý bất đồng bộ qua Kafka, và trades được tạo khi có khớp giá.

### Luồng Chi Tiết

#### Bước 1: Kafka Consumer Nhận Order
```java
// KafkaConsumerService.java - Line 52-91
@KafkaListener(topics = "orders", groupId = "carbon-matching-group", concurrency = "3")
public void consumeOrder(PlaceOrderCommandDTO command, Acknowledgment ack, ...)
```

**Xử lý:**
- Kiểm tra order chưa bị CANCELLED
- Gọi MatchingEngine để xử lý khớp lệnh

#### Bước 2: MatchingEngine.processOrder()
```java
// MatchingEngine.java - Line 24-57
public List<TradeEventDTO> processOrder(PlaceOrderCommandDTO command)
```

**Các bước:**
1. Validate order
2. Lấy hoặc tạo OrderBook cho creditId
3. Xử lý Market Order (set giá cực đoan để đảm bảo khớp)
4. Gọi OrderBook.matchOrder()

#### Bước 3: OrderBook.matchOrder() - Thuật Toán Khớp Lệnh
```java
// OrderBook.java - Line 73-215
public synchronized List<TradeEventDTO> matchOrder(PlaceOrderCommandDTO newOrder)
```

**Thuật toán Price-Time Priority:**

1. **Lấy best price từ phía đối diện:**
   ```java
   BigDecimal bestOppositePrice = isBuy ? getBestAskPrice() : getBestBidPrice();
   ```
   - BUY order: tìm bestAsk (giá bán thấp nhất)
   - SELL order: tìm bestBid (giá mua cao nhất)

2. **Kiểm tra price cross:**
   ```java
   boolean priceCross = isBuy
       ? newOrder.getPrice().compareTo(bestOppositePrice) >= 0  // Buy >= Sell
       : newOrder.getPrice().compareTo(bestOppositePrice) <= 0;  // Sell <= Buy
   ```

3. **Khớp với orders ở price level:**
   ```java
   while (iterator.hasNext() && remaining > 0) {
       PlaceOrderCommandDTO oppOrder = iterator.next();
       
       // Tránh self-match
       if (oppOrder.getUserId().equals(newOrder.getUserId())) {
           continue;
       }
       
       // Tính số lượng khớp
       int matchAmount = Math.min(remaining, oppRemaining);
       
       // Tạo TradeEventDTO
       TradeEventDTO trade = TradeEventDTO.builder()
           .tradeId(UUID.randomUUID().toString())
           .buyOrderId(buyOrderId)
           .sellOrderId(sellOrderId)
           .creditId(creditId)
           .amount(matchAmount)
           .price(bestOppositePrice)
           .totalValue(bestOppositePrice.multiply(BigDecimal.valueOf(matchAmount)))
           .tradeAt(LocalDateTime.now())
           .build();
       
       trades.add(trade);
       
       // Cập nhật remaining amounts
       remaining -= matchAmount;
       oppRemaining -= matchAmount;
       
       // Xóa order nếu đã fill hết
       if (oppRemaining <= 0) {
           iterator.remove();
           orderIndex.remove(oppOrder.getOrderId());
       }
   }
   ```

4. **Thêm order còn lại vào OrderBook:**
   ```java
   if (remaining > 0) {
       addOrder(newOrder); // Thêm vào OrderBook
   }
   ```

#### Bước 4: Xử Lý Trades Sau Khi Khớp
```java
// KafkaConsumerService.java - Line 74-79
if (trades != null && !trades.isEmpty()) {
    log.info("Matched {} trades for order {}", trades.size(), command.getOrderId());
    kafkaProducerService.sendTradesSync(trades); // Gửi vào topic "trades"
} else {
    log.info("Order {} added to OrderBook (No match)", command.getOrderId());
}
```

#### Bước 5: Post-Trade Processing
```java
// KafkaConsumerService.java - Line 97-123
@KafkaListener(topics = "trades", groupId = "carbon-post-trade-group")
public void consumeTrade(TradeEventDTO trade, Acknowledgment ack)
```

**Xử lý:**
1. **Lưu vào Database:**
   ```java
   persistenceService.saveHistoricalTrade(trade);
   ```

2. **Thêm vào Settlement Batch:**
   ```java
   settlementService.addTradeToBatch(trade);
   ```
   - Trades được gom thành batch (10 trades hoặc 30 giây)
   - Sau đó được settle trên blockchain

3. **Broadcast qua WebSocket:**
   ```java
   wsService.broadcastTrade(trade);
   ohlcService.updateOhlcFromTrade(...);
   wsService.broadcastPriceUpdate(...);
   ```

#### Bước 6: Settlement (Quyết Toán)
```java
// SettlementService.java - Line 54-148
@Scheduled(fixedDelay = 30000) // Mỗi 30 giây
public void settleBatch()
```

**Quy trình:**
1. Gom các trades thành batch
2. Validate orders và balances
3. Gọi smart contract để settle:
   ```java
   TransactionReceipt receipt = contractService.processBatchSettlement(
       BigInteger.valueOf(batchId), 
       trades, 
       buyOrderIds, 
       sellOrderIds
   );
   ```
4. Cập nhật order status và remaining amounts
5. Unlock balances nếu cần

### Sơ Đồ Luồng Matching Order

```
Kafka Topic: "orders"
    ↓
KafkaConsumerService.consumeOrder()
    ↓
MatchingEngine.processOrder()
    ↓
OrderBook.matchOrder()
    ↓
├─ Lấy bestOppositePrice (bestAsk cho BUY, bestBid cho SELL)
├─ Kiểm tra price cross
├─ Khớp với orders ở price level (Price-Time Priority)
│  ├─ Tránh self-match
│  ├─ Tính matchAmount = min(remaining, oppRemaining)
│  ├─ Tạo TradeEventDTO
│  └─ Cập nhật remaining amounts
├─ Nếu còn remaining: thêm vào OrderBook
└─ Trả về List<TradeEventDTO>
    ↓
Nếu có trades:
    ↓
Kafka Topic: "trades"
    ↓
KafkaConsumerService.consumeTrade()
    ↓
├─ Lưu vào Database (PersistenceService)
├─ Thêm vào Settlement Batch (SettlementService)
└─ Broadcast WebSocket
    ↓
SettlementService.settleBatch() (mỗi 30s hoặc đủ 10 trades)
    ↓
├─ Validate trades
├─ Gọi smart contract: processBatchSettlement()
├─ Cập nhật order status
└─ Unlock balances
```

---

## 4. Cách Spring Lắng Nghe Sự Kiện Blockchain

### Tổng Quan
Hệ thống sử dụng **Block Polling** thay vì WebSocket subscription để lắng nghe events từ blockchain. Service `BlockPollerService` chạy định kỳ để quét các block mới và phát hiện events.

### Kiến Trúc

#### 1. BlockPollerService - Service Chính
```java
// BlockPollerService.java
@Service
@RequiredArgsConstructor
@Slf4j
public class BlockPollerService {
    private final Web3j web3j;
    private final KafkaProducerService kafkaProducer;
    private final IndexerStateRepository stateRepository;
    private final ChainConstants chainConstants;
}
```

#### 2. Khởi Tạo (PostConstruct)
```java
// BlockPollerService.java - Line 42-61
@PostConstruct
public void init() {
    // Kiểm tra hoặc tạo IndexerState
    if (!stateRepository.existsById(ChainConstants.INDEXER_ID)) {
        stateRepository.save(new IndexerState(ChainConstants.INDEXER_ID, START_BLOCK));
    } else {
        IndexerState state = stateRepository.findById(ChainConstants.INDEXER_ID).orElseThrow();
        log.info("Resuming from block: {}", state.getLastScannedBlock());
    }
}
```

#### 3. Scheduled Task - Poll Blocks
```java
// BlockPollerService.java - Line 63-112
@Scheduled(fixedDelay = 3000) // Mỗi 3 giây
public void pollBlocks()
```

**Quy trình:**

1. **Lấy block hiện tại từ network:**
   ```java
   BigInteger currentNetworkBlock = web3j.ethBlockNumber().send().getBlockNumber();
   ```

2. **Tính safe block (trừ confirmation depth):**
   ```java
   BigInteger safeBlock = currentNetworkBlock.subtract(BigInteger.valueOf(CONFIRMATION_DEPTH));
   ```
   - `CONFIRMATION_DEPTH` mặc định là 1 block
   - Đảm bảo chỉ xử lý các block đã được confirm

3. **Lấy last scanned block:**
   ```java
   IndexerState state = stateRepository.findById(ChainConstants.INDEXER_ID).orElseThrow();
   BigInteger lastScanned = state.getLastScannedBlock();
   BigInteger fromBlock = lastScanned.add(BigInteger.ONE);
   ```

4. **Tính toBlock (batch size tối đa 100 blocks):**
   ```java
   BigInteger toBlock = fromBlock.add(BigInteger.valueOf(MAX_BATCH_SIZE));
   if (toBlock.compareTo(safeBlock) > 0) {
       toBlock = safeBlock;
   }
   ```

5. **Quét và xử lý events:**
   ```java
   scanAndProcess(fromBlock, toBlock);
   ```

6. **Cập nhật last scanned block:**
   ```java
   state.setLastScannedBlock(toBlock);
   stateRepository.save(state);
   ```

#### 4. Scan và Process Events
```java
// BlockPollerService.java - Line 114-184
private void scanAndProcess(BigInteger from, BigInteger to) throws IOException
```

**Quy trình:**

1. **Tạo EthFilter:**
   ```java
   List<String> listenedAddresses = chainConstants.getListenedAddresses();
   
   EthFilter filter = new EthFilter(
       DefaultBlockParameter.valueOf(from),
       DefaultBlockParameter.valueOf(to),
       listenedAddresses  // Danh sách contract addresses cần lắng nghe
   );
   ```

2. **Gọi RPC để lấy logs:**
   ```java
   EthLog ethLog = web3j.ethGetLogs(filter).send();
   List<EthLog.LogResult> logs = ethLog.getLogs();
   ```

3. **Xử lý từng log:**
   ```java
   for (EthLog.LogResult logResult : logs) {
       Log logData = (Log) logResult.get();
       processSingleLog(logData);
   }
   ```

#### 5. Process Single Log
```java
// BlockPollerService.java - Line 186-220
private void processSingleLog(Log logData)
```

**Quy trình:**

1. **Xác định loại event từ event hash:**
   ```java
   String eventHash = logData.getTopics().get(0);
   String eventType = identifyEventType(eventHash);
   ```
   - Event hash là topic đầu tiên trong log
   - So sánh với các hash trong `ChainConstants`

2. **Tạo BlockchainEventDTO:**
   ```java
   BlockchainEventDTO eventDTO = new BlockchainEventDTO();
   eventDTO.setTransactionHash(logData.getTransactionHash());
   eventDTO.setBlockNumber(logData.getBlockNumber());
   eventDTO.setContractAddress(logData.getAddress());
   eventDTO.setEventType(eventType);
   eventDTO.setTopics(logData.getTopics());
   eventDTO.setData(logData.getData());
   ```

3. **Gửi vào Kafka:**
   ```java
   kafkaProducer.sendOnChainEvent(eventDTO);
   ```

#### 6. Identify Event Type
```java
// BlockPollerService.java - Line 222-274
private String identifyEventType(String hash)
```

So sánh hash với các event signatures:
- `CREDIT_RETIRED`
- `BATCH_CERTIFICATE_RETIRED`
- `CREDIT_MINTED`
- `TRADE_SETTLED`
- `BALANCE_LOCKED`
- `BALANCE_UNLOCKED`
- ... và nhiều events khác

#### 7. Kafka Consumer Xử Lý Events
```java
// KafkaConsumerService.java - Line 148-204
@KafkaListener(topics = "onchain-events", groupId = "carbon-market-group")
public void consumeBlockchainEvent(BlockchainEventDTO event, Acknowledgment ack)
```

**Đặc điểm:**
- **Retry mechanism:** Tối đa 5 lần retry với exponential backoff
- **Dead Letter Queue:** Events thất bại được gửi vào DLQ
- **Optimistic locking:** Xử lý conflict khi có concurrent updates

**Xử lý theo event type:**
```java
// KafkaConsumerService.java - Line 206-262
private void processEvent(BlockchainEventDTO event) {
    switch (eventType) {
        case "BATCH_CERTIFICATE_RETIRED" -> 
            certificateService.handleBatchCeritificateRetired(event);
        case "CREDIT_MINTED" -> 
            carbonCreditService.handleCreditMinted(event);
        case "TRADE_SETTLED" -> 
            walletService.handleTradeSettled(event);
        // ... các events khác
    }
}
```

### Sơ Đồ Luồng Lắng Nghe Blockchain

```
Blockchain (Smart Contract Events)
    ↓
BlockPollerService.pollBlocks() [@Scheduled mỗi 3s]
    ↓
├─ Lấy currentNetworkBlock
├─ Tính safeBlock = currentBlock - CONFIRMATION_DEPTH
├─ Lấy lastScannedBlock từ IndexerState
├─ Tính fromBlock = lastScanned + 1
├─ Tính toBlock = min(fromBlock + 100, safeBlock)
    ↓
scanAndProcess(fromBlock, toBlock)
    ↓
├─ Tạo EthFilter (block range + contract addresses)
├─ web3j.ethGetLogs(filter) → Lấy logs
└─ Xử lý từng log
    ↓
processSingleLog(Log logData)
    ↓
├─ Xác định eventType từ event hash
├─ Tạo BlockchainEventDTO
└─ Gửi vào Kafka topic "onchain-events"
    ↓
KafkaConsumerService.consumeBlockchainEvent()
    ↓
├─ Retry mechanism (tối đa 5 lần)
├─ processEvent(event) → Định tuyến đến handler
│  ├─ BATCH_CERTIFICATE_RETIRED → CertificateService
│  ├─ CREDIT_MINTED → CarbonCreditService
│  ├─ TRADE_SETTLED → WalletService
│  └─ ... các events khác
└─ Nếu thất bại → Gửi vào DLQ
```

### Ưu Điểm Của Block Polling

1. **Độ tin cậy cao:** Không phụ thuộc vào WebSocket connection
2. **Xử lý lại được:** Có thể quét lại các block đã bỏ lỡ
3. **Batch processing:** Xử lý nhiều events cùng lúc
4. **State management:** Lưu lastScannedBlock để resume sau khi restart

### Nhược Điểm

1. **Độ trễ:** Phải chờ block được confirm (1 block)
2. **Tải RPC:** Gọi RPC nhiều hơn so với WebSocket subscription
3. **Không real-time:** Có độ trễ 3 giây (scheduled interval)

---

## Tổng Kết

### Các Luồng Chính

1. **Retire:** Blockchain Event → BlockPoller → Kafka → CertificateService → Update DB
2. **Place Order:** API → TradingService → Lock Balance → Kafka → Matching Engine
3. **Matching:** Kafka Order → MatchingEngine → OrderBook → Trades → Settlement
4. **Blockchain Listening:** BlockPoller (scheduled) → ethGetLogs → Kafka → Event Handlers

### Công Nghệ Sử Dụng

- **Spring Boot:** Framework chính
- **Kafka:** Message queue cho async processing
- **Web3j:** Thư viện tương tác với blockchain
- **In-Memory OrderBook:** Matching engine với Price-Time Priority
- **Scheduled Tasks:** Block polling và settlement batching

### Điểm Quan Trọng

1. **Idempotency:** Sử dụng `ProcessedTransaction` để tránh xử lý trùng
2. **Concurrency:** Sử dụng synchronized và locks để tránh race conditions
3. **Error Handling:** Retry mechanism và Dead Letter Queue
4. **State Management:** IndexerState để track block scanning progress
