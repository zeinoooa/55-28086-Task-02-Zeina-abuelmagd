package com.example.lab05.service;

import com.example.lab05.dto.PurchaseRequest;
import com.example.lab05.model.Product;
import com.example.lab05.model.mongo.PurchaseReceipt;
import com.example.lab05.repository.mongo.PurchaseReceiptRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PurchaseService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseService.class);

    private final ProductService productService;
    private final PurchaseReceiptRepository purchaseReceiptRepository;
    private final SocialGraphService socialGraphService;
    private final SensorService sensorService;
    private final ProductSearchService productSearchService;
    private final RedisTemplate<String, Object> redisTemplate;

    public PurchaseService(ProductService productService,
                           PurchaseReceiptRepository purchaseReceiptRepository,
                           SocialGraphService socialGraphService,
                           SensorService sensorService,
                           ProductSearchService productSearchService,
                           RedisTemplate<String, Object> redisTemplate) {
        this.productService = productService;
        this.purchaseReceiptRepository = purchaseReceiptRepository;
        this.socialGraphService = socialGraphService;
        this.sensorService = sensorService;
        this.productSearchService = productSearchService;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public PurchaseReceipt executePurchase(PurchaseRequest request) {

        // ======================================================
        // Step 1 — PostgreSQL (HARD dependency)
        // ======================================================
        Product product = productService.getProductById(request.productId());

        if (product.getStockQuantity() < request.quantity()) {
            throw new RuntimeException("Insufficient stock for product: " + product.getName());
        }

        // Deduct the stock
        product.setStockQuantity(product.getStockQuantity() - request.quantity());

        // Save using the update method from your ProductService
        productService.updateProduct(product.getId(), product);

        // ======================================================
        // Step 2 — MongoDB (HARD dependency)
        // ======================================================
        double total = product.getPrice() * request.quantity();

        PurchaseReceipt receipt = new PurchaseReceipt();
        receipt.setPersonName(request.personName());
        receipt.setProductName(product.getName());
        receipt.setProductCategory(product.getCategory());
        receipt.setQuantity(request.quantity());
        receipt.setUnitPrice(product.getPrice());
        receipt.setTotalPrice(total);
        receipt.setPurchaseDetails(request.purchaseDetails());
        receipt.setPurchasedAt(LocalDateTime.now());

        PurchaseReceipt savedReceipt = purchaseReceiptRepository.save(receipt);

        // ======================================================
        // Step 3 — Neo4j (Soft dependency / try-catch)
        // ======================================================
        try {
            socialGraphService.addPurchase(request.personName(), product.getName());
        } catch (Exception e) {
            log.warn("Failed to create PURCHASED edge for {} -> {}: {}",
                    request.personName(), product.getName(), e.getMessage());
        }

        // ======================================================
        // Step 4 — Cassandra (Soft dependency / try-catch)
        // ======================================================
        try {
            String sensorId = "user-activity-" + request.personName().toLowerCase();
            sensorService.recordReading(sensorId, product.getName());
        } catch (Exception e) {
            log.warn("Failed to log purchase event for {}: {}",
                    request.personName(), e.getMessage());
        }

        // ======================================================
        // Step 5 — Elasticsearch (Soft dependency / try-catch)
        // ======================================================
        try {
            if (product.getStockQuantity() == 0) {
                var searchResults = productSearchService.searchByName(product.getName());

                if (searchResults != null && !searchResults.isEmpty()) {
                    var esProduct = searchResults.get(0);
                    esProduct.setInStock(false);
                    productSearchService.save(esProduct);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to update ES inStock for product {}: {}",
                    product.getId(), e.getMessage());
        }

        // ======================================================
        // Step 6 — Redis (Soft dependency / try-catch)
        // ======================================================
        try {
            String cacheKey = "dashboard:" + request.personName();
            redisTemplate.delete(cacheKey);
        } catch (Exception e) {
            log.warn("Failed to evict dashboard cache for {}: {}",
                    request.personName(), e.getMessage());
        }

        return savedReceipt;
    }

    public List<PurchaseReceipt> getReceiptsByPerson(String personName) {
        return List.of();
    }
}