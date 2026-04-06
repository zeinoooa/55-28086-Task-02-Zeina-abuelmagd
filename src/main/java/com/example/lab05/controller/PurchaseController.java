package com.example.lab05.controller;

import com.example.lab05.dto.PurchaseRequest;
import com.example.lab05.model.mongo.PurchaseReceipt;
import com.example.lab05.service.PurchaseService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for managing purchase operations across the polyglot persistence layer.
 * Entry point: /your-id/purchases
 */
@RestController
@RequestMapping("/55-28086/purchases") // Replace with your specific ID
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    /**
     * Executes a new purchase request.
     * Transactional across PostgreSQL and MongoDB; eventually consistent for others.
     *
     */
    @PostMapping
    public PurchaseReceipt createPurchase(@RequestBody PurchaseRequest request) {
        return purchaseService.executePurchase(request);
    }

    /**
     * Retrieves all purchase history (receipts) for a specific person from MongoDB.
     *
     */
    @GetMapping("/person/{personName}")
    public List<PurchaseReceipt> getReceiptsByPerson(@PathVariable String personName) {
        return purchaseService.getReceiptsByPerson(personName);
    }
}