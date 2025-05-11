package com.siemens.internship;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siemens.internship.model.Item;
import com.siemens.internship.service.ItemService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class InternshipApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private ItemService itemService;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void testGetAllItems() throws Exception {
		List<Item> items = Arrays.asList(new Item(1L, "A", "desc", "on", "a@email.com"));
		Mockito.when(itemService.findAll()).thenReturn(items);
		mockMvc.perform(get("/api/items"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("A"));
	}

	@Test
	void testGetItemById_found() throws Exception {
		Item item = new Item(1L, "A", "desc", "on", "a@email.com");
		Mockito.when(itemService.findById(1L)).thenReturn(Optional.of(item));
		mockMvc.perform(get("/api/items/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("A"));
	}

	@Test
	void testGetItemById_notFound() throws Exception {
		Mockito.when(itemService.findById(2L)).thenReturn(Optional.empty());
		mockMvc.perform(get("/api/items/2"))
				.andExpect(status().isNotFound());
	}

	@Test
	void testCreateItem_success() throws Exception {
		Item item = new Item(null, "A", "desc", "on", "a@email.com");
		Item saved = new Item(1L, "A", "desc", "on", "a@email.com");
		Mockito.when(itemService.save(any(Item.class))).thenReturn(saved);
		mockMvc.perform(post("/api/items")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(item)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(1));
	}

	@Test
	void testCreateItem_validationError() throws Exception {
		// Missing required fields (simulate validation error)
		Item item = new Item();
		mockMvc.perform(post("/api/items")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(item)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void testUpdateItem_found() throws Exception {
		Item item = new Item(1L, "A", "desc", "on", "a@email.com");
		Mockito.when(itemService.findById(1L)).thenReturn(Optional.of(item));
		Mockito.when(itemService.save(any(Item.class))).thenReturn(item);
		mockMvc.perform(put("/api/items/1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(item)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1));
	}

	@Test
	void testUpdateItem_notFound() throws Exception {
		Item item = new Item(2L, "B", "desc", "on", "b@email.com");
		Mockito.when(itemService.findById(2L)).thenReturn(Optional.empty());
		mockMvc.perform(put("/api/items/2")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(item)))
				.andExpect(status().isNotFound());
	}

	@Test
	void testDeleteItem() throws Exception {
		Mockito.when(itemService.findById(1L)).thenReturn(Optional.of(new Item()));
		Mockito.doNothing().when(itemService).deleteById(1L);
		mockMvc.perform(delete("/api/items/1"))
				.andExpect(status().isNoContent());
	}

	@Test
	void testDeleteItem_notFound() throws Exception {
		Mockito.when(itemService.findById(2L)).thenReturn(Optional.empty());
		mockMvc.perform(delete("/api/items/2"))
				.andExpect(status().isNotFound());
	}

	@Test
	void testProcessItems() throws Exception {
		List<Item> items = Arrays.asList(new Item(1L, "A", "desc", "on", "a@email.com"));
		Mockito.when(itemService.findAll()).thenReturn(items);
		Mockito.when(itemService.processAllItems(any(List.class))).thenReturn(CompletableFuture.completedFuture(items));
		mockMvc.perform(get("/api/items/process"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(1));
	}
}
