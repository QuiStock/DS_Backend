package com.quistock.ds_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.quistock.ds_backend.model.dto.ChatRequest;
import com.quistock.ds_backend.model.dto.ChatResponse;
import com.quistock.ds_backend.model.dto.FlowDTO;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatServiceTest {
  private FlowService flowService;
  private ChatService chatService;

  @BeforeEach
  void setUp() {
    flowService = mock(FlowService.class);
    chatService = new ChatService(flowService);
  }

  @Test
  void shouldAnswerPromotionQuestionWithLowRiskProducts() {
    when(flowService.listFlows()).thenReturn(List.of(flow("101", "LOW"), flow("102", "HIGH")));

    ChatResponse response =
        chatService.answer(new ChatRequest("1", "Which products need a promotion?"));

    assertThat(response.responsibleAgent()).isEqualTo("operations_agent");
    assertThat(response.answer()).contains("promotion for Whole Milk 1L");
    assertThat(response.referencedData())
        .singleElement()
        .satisfies(
            data -> {
              assertThat(data.productId()).isEqualTo("PROD001:FIL001");
              assertThat(data.name()).isEqualTo("Whole Milk 1L");
              assertThat(data.flowType()).isEqualTo("LOW");
              assertThat(data.suggestedAction()).isEqualTo("PROMOTION");
            });
  }

  @Test
  void shouldAnswerStockoutQuestionWithHighRiskProducts() {
    when(flowService.listFlows()).thenReturn(List.of(flow("101", "HIGH")));

    ChatResponse response =
        chatService.answer(new ChatRequest("1", "Which products are out of stock?"));

    assertThat(response.answer()).contains("stock order for Whole Milk 1L");
    assertThat(response.referencedData())
        .singleElement()
        .extracting(data -> data.suggestedAction())
        .isEqualTo("STOCK_ORDER");
  }

  @Test
  void shouldReturnEmptyReferencesWhenNoFlowMatches() {
    when(flowService.listFlows()).thenReturn(List.of(flow("101", "MEDIUM")));

    ChatResponse response =
        chatService.answer(new ChatRequest("1", "Which products need a promotion?"));

    assertThat(response.answer()).isEqualTo("No analyzed products match the requested criteria.");
    assertThat(response.referencedData()).isEmpty();
  }

  private FlowDTO flow(String id, String type) {
    return new FlowDTO(
        id,
        "PROD001:FIL001",
        "Whole Milk 1L",
        type,
        "ANALYZED",
        "Analysis reason",
        new BigDecimal("5.14"),
        new BigDecimal("9.92"),
        0,
        3,
        Instant.parse("2026-08-28T12:00:00Z"));
  }
}
