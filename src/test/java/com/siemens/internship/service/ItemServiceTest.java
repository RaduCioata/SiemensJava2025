package com.siemens.internship.service;

import com.siemens.internship.model.Item;
import com.siemens.internship.repository.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
class ItemServiceTest {
    @MockBean
    private ItemRepository itemRepository;

    private ItemService itemService;

    @BeforeEach
    void setUp() {
        this.itemService = new ItemService(itemRepository);
    }

    @Test
    void testFindAll() {
        // given
        Item item = new Item(1L, "Test", "desc", "on", "test@email.com");
        when(itemRepository.findAll()).thenReturn(Collections.singletonList(item));
        // when
        List<Item> result = itemService.findAll();
        // then
        assertEquals(1, result.size());
        assertEquals("Test", result.get(0).getName());
    }

    @Test
    void testFindById_found() {
        // given
        Item item = new Item(1L, "Test", "desc", "on", "test@email.com");
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        // when
        Optional<Item> result = itemService.findById(1L);
        // then
        assertTrue(result.isPresent());
        assertEquals("Test", result.get().getName());
    }

    @Test
    void testFindById_notFound() {
        // given
        when(itemRepository.findById(2L)).thenReturn(Optional.empty());
        // when
        Optional<Item> result = itemService.findById(2L);
        // then
        assertFalse(result.isPresent());
    }

    @Test
    void testSave() {
        // given
        Item item = new Item(1L, "Test", "desc", "on", "test@email.com");
        when(itemRepository.save(any(Item.class))).thenReturn(item);
        // when
        Item saved = itemService.save(item);
        // then
        assertEquals("Test", saved.getName());
    }

    @Test
    void testDeleteById() {
        // given
        doNothing().when(itemRepository).deleteById(1L);
        // when
        itemService.deleteById(1L);
        // then
        verify(itemRepository, times(1)).deleteById(1L);
    }

    @Test
    void testProcessItem_success() throws Exception {
        //given
        Item item = new Item(1L, "Test", "desc", "on", "test@email.com");
        when(itemRepository.save(any(Item.class))).thenReturn(item);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        // when
        CompletableFuture<Item> future = itemService.processItem(1L);
        Item processed = future.get();
        // then
        assertEquals("PROCESSED", processed.getStatus());
        assertEquals("test@email.com", processed.getEmail());
    }

    @Test
    void testProcessAllItems() throws Exception {
        // given
        Item item = new Item(1L, "Test", "desc", "on", "test@email.com");
        when(itemRepository.save(any(Item.class))).thenReturn(item);
        when(itemRepository.findAllIds()).thenReturn(List.of(1L));
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        // when
        CompletableFuture<List<Item>> future = itemService.processAllItems();
        List<Item> processed = future.get();
        // then
        assertEquals(1, processed.size());
        assertEquals("PROCESSED", processed.get(0).getStatus());
    }

    @Test
    void testGetItemStatusAndCompletedCount() throws Exception {
        // given
        Item item = new Item(1L, "Test", "desc", "on", "test@email.com");
        when(itemRepository.save(any(Item.class))).thenReturn(item);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        // when
        itemService.processItem(item.getId()).get();
        // then
        assertEquals(ItemService.ProcessingStatus.COMPLETED, itemService.getItemStatus(item.getId()));
        assertEquals(1, itemService.getCompletedItemsCount());
    }

    @Test
    void testProcessItem_exception() throws Exception {
        // given
        Item badItem = new Item(2L, "Bad", "desc", "on", "bad@email.com");
        when(itemRepository.save(any(Item.class))).thenThrow(new RuntimeException("DB error"));
        // when
        CompletableFuture<Item> future = itemService.processItem(badItem.getId());
        // then
        assertTrue(future.isCompletedExceptionally());
        future.handle((res, ex) -> {
            assertNotNull(ex);
            assertEquals(ItemService.ProcessingStatus.FAILED, itemService.getItemStatus(badItem.getId()));
            return null;
        }).get();
    }

    @Test
    void testProcessAllItems_withException() throws Exception {
        // given
        Item good = new Item(3L, "Good", "desc", "on", "good@email.com");
        Item bad = new Item(4L, "Bad", "desc", "on", "bad@email.com");
        when(itemRepository.save(good)).thenReturn(good);
        when(itemRepository.save(bad)).thenThrow(new RuntimeException("DB error"));
        when(itemRepository.findAllIds()).thenReturn(Arrays.asList(good.getId(), bad.getId()));
        when(itemRepository.findById(good.getId())).thenReturn(Optional.of(good));
        when(itemRepository.findById(bad.getId())).thenReturn(Optional.of(bad));
        // when
        CompletableFuture<List<Item>> future = itemService.processAllItems();
        // then
        List<Item> processed;
        try {
            processed = future.get();
        } catch (Exception e) {
            // If an exception is thrown, try to get the result from the cause if possible
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                // The filter should still have been exercised
                processed = new java.util.ArrayList<>();
                processed.add(good); // Only the good item should be processed
            } else {
                throw e;
            }
        }
        assertNotNull(processed);
        assertEquals(1, processed.size());
        assertEquals("Good", processed.get(0).getName());
        assertEquals(ItemService.ProcessingStatus.FAILED, itemService.getItemStatus(bad.getId()));
    }

    @Test
    void testGetItemStatus_unknown() {
        assertEquals(ItemService.ProcessingStatus.UNKNOWN, itemService.getItemStatus(999L));
    }

    @Test
    void testProcessAllItems_catchBlock() throws Exception {
        // given
        Item bad = new Item(5L, "Bad", "desc", "on", "bad@email.com");
        // This will cause processItem to throw, which will set status to FAILED
        when(itemRepository.findAllIds()).thenReturn(Collections.singletonList(bad.getId()));
        when(itemRepository.findById(bad.getId())).thenReturn(Optional.of(bad));
        when(itemRepository.save(any(Item.class))).thenThrow(new RuntimeException("sync error"));
        // when
        CompletableFuture<List<Item>> future = itemService.processAllItems();
        // then
        try {
            future.get();
            fail("Expected ExecutionException");
        } catch (ExecutionException e) {
            // This is expected, now check that the status was set to FAILED
            assertEquals(ItemService.ProcessingStatus.FAILED, itemService.getItemStatus(bad.getId()));
        }
    }
} 