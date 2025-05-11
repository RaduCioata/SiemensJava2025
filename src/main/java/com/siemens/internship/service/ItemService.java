package com.siemens.internship.service;

import com.siemens.internship.model.Item;
import com.siemens.internship.repository.ItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class ItemService {
    private static final Logger logger = LoggerFactory.getLogger(ItemService.class);
    
    @Autowired
    private ItemRepository itemRepository;
    private static final ExecutorService executor = Executors.newFixedThreadPool(10);
    private final List<Item> processedItems = new ArrayList<>();
    private int processedCount = 0;
    
    // Thread-safe map to store processing status
    private final ConcurrentHashMap<Long, ProcessingStatus> processingStatus = new ConcurrentHashMap<>();
    
    // Atomic counter for tracking completed items
    private final AtomicInteger completedItems = new AtomicInteger(0);

    public List<Item> findAll() {
        return itemRepository.findAll();
    }

    public Optional<Item> findById(Long id) {
        return itemRepository.findById(id);
    }

    public Item save(Item item) {
        return itemRepository.save(item);
    }

    public void deleteById(Long id) {
        itemRepository.deleteById(id);
    }

    /**
     * Process a single item asynchronously
     * @param item The item to process
     * @return CompletableFuture containing the processed item
     */
    @Async
    public CompletableFuture<Item> processItem(Item item) {
        try {
            logger.info("Starting processing of item: {}", item.getId());
            
            // Update processing status
            processingStatus.put(item.getId(), ProcessingStatus.IN_PROGRESS);
            
            // Simulate processing time (replace with actual processing logic)
            Thread.sleep(1000);
            
            // Your actual item processing logic here
            Item processedItem = processItemLogic(item);
            
            // Update status and counter
            processingStatus.put(item.getId(), ProcessingStatus.COMPLETED);
            completedItems.incrementAndGet();
            
            logger.info("Completed processing of item: {}", item.getId());
            return CompletableFuture.completedFuture(processedItem);
            
        } catch (Exception e) {
            logger.error("Error processing item: {}", item.getId(), e);
            processingStatus.put(item.getId(), ProcessingStatus.FAILED);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Process all items asynchronously and collect results
     * @param items List of items to process
     * @return CompletableFuture containing list of processed items
     */
    public CompletableFuture<List<Item>> processAllItems(List<Item> items) {
        logger.info("Starting batch processing of {} items", items.size());
        
        // Reset processing status
        processingStatus.clear();
        completedItems.set(0);
        
        // Initialize status for all items
        items.forEach(item -> processingStatus.put(item.getId(), ProcessingStatus.PENDING));
        
        // Create a list of CompletableFuture<Item> by processing each item asynchronously
        List<CompletableFuture<Item>> futures = items.stream()
                .map(this::processItem)
                .collect(Collectors.toList());
        
        // Combine all futures and handle errors
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(future -> {
                            try {
                                return future.join();
                            } catch (Exception e) {
                                logger.error("Error in future completion", e);
                                return null;
                            }
                        })
                        .filter(item -> item != null)
                        .collect(Collectors.toList()))
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        logger.error("Error in batch processing", throwable);
                    } else {
                        logger.info("Batch processing completed. Processed {} items successfully", 
                            completedItems.get());
                    }
                });
    }

    /**
     * Get the current processing status of an item
     * @param itemId The ID of the item
     * @return The current processing status
     */
    public ProcessingStatus getItemStatus(Long itemId) {
        return processingStatus.getOrDefault(itemId, ProcessingStatus.UNKNOWN);
    }

    /**
     * Get the number of completed items
     * @return The number of completed items
     */
    public int getCompletedItemsCount() {
        return completedItems.get();
    }

    // Private helper method for actual item processing logic
    private Item processItemLogic(Item item) {
        // Set the status to PROCESSED
        item.setStatus("PROCESSED");
        // Save the updated item to the database
        return itemRepository.save(item);
    }

    // Enum to track processing status
    public enum ProcessingStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        UNKNOWN
    }
}