package com.quistock.ds_backend.service;

import com.quistock.ds_backend.model.dto.ChatReferencedDataDTO;
import com.quistock.ds_backend.model.dto.ChatRequest;
import com.quistock.ds_backend.model.dto.ChatResponse;
import com.quistock.ds_backend.model.dto.FlowDTO;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class ChatService {
  private static final String RESPONSIBLE_AGENT = "operations_agent";

  private final FlowService flowService;

  public ChatService(FlowService service) {
    this.flowService = service;
  }

  public ChatResponse answer(ChatRequest request) {
    List<FlowDTO> matchingFlows = selectFlows(request.message());
    List<ChatReferencedDataDTO> referencedData =
        matchingFlows.stream().map(this::toReferencedData).toList();
    return new ChatResponse(buildAnswer(matchingFlows), RESPONSIBLE_AGENT, referencedData);
  }

  private List<FlowDTO> selectFlows(String message) {
    String normalizedMessage = message.toLowerCase(Locale.ROOT);
    List<FlowDTO> flows = flowService.listFlows();
    if (containsAny(normalizedMessage, "promotion", "promote", "expiration", "expire")) {
      return flows.stream().filter(flow -> "LOW".equals(flow.flowType())).toList();
    }
    if (containsAny(normalizedMessage, "stockout", "out of stock", "replenish", "stock order")) {
      return flows.stream().filter(flow -> "HIGH".equals(flow.flowType())).toList();
    }
    if (containsAny(normalizedMessage, "monitor", "adequate stock")) {
      return flows.stream().filter(flow -> "MEDIUM".equals(flow.flowType())).toList();
    }
    return flows;
  }

  private boolean containsAny(String message, String... terms) {
    for (String term : terms) {
      if (message.contains(term)) {
        return true;
      }
    }
    return false;
  }

  private ChatReferencedDataDTO toReferencedData(FlowDTO flow) {
    return new ChatReferencedDataDTO(
        flow.productId(), flow.productName(), flow.flowType(), suggestedAction(flow.flowType()));
  }

  private String suggestedAction(String flowType) {
    return switch (flowType) {
      case "HIGH" -> "STOCK_ORDER";
      case "MEDIUM" -> "MONITOR";
      case "LOW" -> "PROMOTION";
      default -> "MONITOR";
    };
  }

  private String buildAnswer(List<FlowDTO> flows) {
    if (flows.isEmpty()) {
      return "No analyzed products match the requested criteria.";
    }
    FlowDTO firstFlow = flows.get(0);
    return switch (firstFlow.flowType()) {
      case "LOW" ->
          "There are products at risk of expiration. The main suggestion is to create a promotion for "
              + firstFlow.productName()
              + ".";
      case "HIGH" ->
          "There are products at risk of stockout. The main suggestion is to create a stock order for "
              + firstFlow.productName()
              + ".";
      case "MEDIUM" ->
          "There are products with adequate stock. The main suggestion is to monitor "
              + firstFlow.productName()
              + ".";
      default -> "There are analyzed product flows that require review.";
    };
  }
}
