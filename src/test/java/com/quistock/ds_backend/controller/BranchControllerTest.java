package com.quistock.ds_backend.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quistock.ds_backend.model.dto.BranchDTO;
import com.quistock.ds_backend.service.BranchService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BranchControllerTest {

  @Test
  void shouldListBranchesOnPublicRoute() throws Exception {
    BranchService branchService = mock(BranchService.class);
    when(branchService.listBranches())
        .thenReturn(
            List.of(new BranchDTO("FIL001", "Santana Store", null, null, null, null, null)));

    mockMvc(branchService)
        .perform(get("/api/branches").contextPath("/api"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[0].id").value("FIL001"))
        .andExpect(jsonPath("$[0].name").value("Santana Store"));
  }

  private MockMvc mockMvc(BranchService branchService) {
    return MockMvcBuilders.standaloneSetup(new BranchController(branchService)).build();
  }
}
