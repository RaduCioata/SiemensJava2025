package com.siemens.internship.service;

import com.siemens.internship.model.Item;
import com.siemens.internship.repository.ItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class ItemService {
    private static final Logger logger = LoggerFactory.getLogger(ItemService.class);

    private final ItemRepository itemRepository;

    // Thread-safe map to store processing status
    private final ConcurrentHashMap<Long, ProcessingStatus> idToProcessingStatusMap = new ConcurrentHashMap<>();

    // Atomic counter for tracking completed items
    private final AtomicInteger completedItems = new AtomicInteger(0);

    public ItemService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

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
     *
     * @param itemId The id of the item to process
     * @return CompletableFuture containing the processed item
     */
    @Async
    public CompletableFuture<Item> processItem(long itemId) {
        try {
            logger.info("Starting processing of item: {}", itemId);

            // Update processing status
            idToProcessingStatusMap.put(itemId, ProcessingStatus.IN_PROGRESS);

            // Read from the database
            Item item = itemRepository.findById(itemId)
                    .orElseThrow(() -> new IllegalArgumentException("Item not found"));

            // Simulate processing time (replace with actual processing logic)
            Thread.sleep(1000);

            // Set the status to PROCESSED
            item.setStatus("PROCESSED");

            // Save the updated item to the database
            Item processedItem = itemRepository.save(item);

            // Update status and counter
            idToProcessingStatusMap.put(item.getId(), ProcessingStatus.COMPLETED);
            completedItems.incrementAndGet();

            logger.info("Completed processing of item: {}", item.getId());
            return CompletableFuture.completedFuture(processedItem);

        } catch (Exception e) {
            logger.error("Error processing item: {}", itemId, e);
            idToProcessingStatusMap.put(itemId, ProcessingStatus.FAILED);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Process all items asynchronously and collect results
     *
     * @return CompletableFuture containing list of processed items
     */
    public CompletableFuture<List<Item>> processAllItems() {
        // Reset processing status
        idToProcessingStatusMap.clear();
        completedItems.set(0);

        List<Long> itemIds = itemRepository.findAllIds();
        logger.info("Starting batch processing of {} items", itemIds.size());

        // Initialize status for all items
        itemIds.forEach(itemId -> idToProcessingStatusMap.put(itemId, ProcessingStatus.PENDING));

        // Create a list of CompletableFuture<Item> by processing each item asynchronously
        List<CompletableFuture<Item>> futures = itemIds.stream()
                .map(this::processItem)
                .toList();

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
                        .filter(Objects::nonNull)
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
     *
     * @param itemId The ID of the item
     * @return The current processing status
     */
    public ProcessingStatus getItemStatus(Long itemId) {
        return idToProcessingStatusMap.getOrDefault(itemId, ProcessingStatus.UNKNOWN);
    }

    /**
     * Get the number of completed items
     *
     * @return The number of completed items
     */
    public int getCompletedItemsCount() {
        return completedItems.get();
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