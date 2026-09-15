package com.quistock.ds_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.quistock.ds_backend.exception.ErpIntegrationException;
import com.quistock.ds_backend.model.dto.ErpBatchDTO;
import com.quistock.ds_backend.model.dto.ProductDTO;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ProductServiceTest {

  private static final Clock TEST_CLOCK =
      Clock.fixed(Instant.parse("2026-08-28T12:00:00Z"), ZoneOffset.UTC);

  private MockRestServiceServer server;
  private ProductService productService;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://erp.test");
    server = MockRestServiceServer.bindTo(builder).build();
    RestClient restClient = builder.build();
    productService = new ProductService(restClient, "/products", TEST_CLOCK);
  }

  @Test
  void shouldCreateOneConsolidatedProductFromOneBatch() {
    ErpBatchDTO batch =
        batch(
            "1",
            "PROD001",
            "FIL001",
            "Santana Store",
            "2026-08-20",
            "2026-09-30",
            "71",
            "7.99",
            "5.20",
            "36",
            "150",
            "40",
            "5");

    ProductDTO product = productService.consolidateProducts(List.of(batch)).get(0);

    assertThat(product.id()).isEqualTo("PROD001:FIL001");
    assertThat(product.sku()).isEqualTo("PROD001");
    assertThat(product.name()).isEqualTo("Whole Milk 1L");
    assertThat(product.category()).isEqualTo("Dairy");
    assertThat(product.currentStock()).isEqualTo(71);
    assertThat(product.minimumStock()).isEqualTo(40);
    assertThat(product.sales7d()).isEqualTo(36);
    assertThat(product.sales30d()).isEqualTo(150);
    assertThat(product.expirationDays()).isEqualTo(33);
    assertThat(product.supplierLeadTime()).isEqualTo(5);
    assertThat(product.price()).isEqualByComparingTo("7.99");
    assertThat(product.cost()).isEqualByComparingTo("5.20");
    assertThat(product.lastRestock()).isEqualTo(Instant.parse("2026-08-20T00:00:00Z"));
    assertThat(product.status()).isTrue();
    assertThat(product.branch()).isEqualTo("Santana Store");
  }

  @Test
  void shouldSumBatchesForSameProductAndBranch() {
    List<ErpBatchDTO> batches =
        List.of(
            batch(
                "1",
                "PROD001",
                "FIL001",
                "Santana Store",
                "2026-08-20",
                "2026-09-30",
                50,
                7.99,
                5.20,
                10,
                100,
                40,
                5),
            batch(
                "2",
                "PROD001",
                "FIL001",
                "Santana Store",
                "2026-08-21",
                "2026-09-10",
                40,
                8.10,
                5.30,
                20,
                100,
                40,
                5),
            batch(
                "3",
                "PROD001",
                "FIL001",
                "Santana Store",
                "2026-08-22",
                "2026-09-20",
                30,
                8.20,
                5.40,
                20,
                0,
                40,
                5));

    ProductDTO product = productService.consolidateProducts(batches).get(0);

    assertThat(productService.consolidateProducts(batches)).hasSize(1);
    assertThat(product.currentStock()).isEqualTo(120);
    assertThat(product.sales7d()).isEqualTo(50);
    assertThat(product.sales30d()).isEqualTo(200);
    assertThat(product.minimumStock()).isEqualTo(40);
    assertThat(product.supplierLeadTime()).isEqualTo(5);
    assertThat(product.expirationDays()).isEqualTo(13);
  }

  @Test
  void shouldKeepDifferentBranchesSeparate() {
    List<ProductDTO> products =
        productService.consolidateProducts(
            List.of(
                batch(
                    "1",
                    "PROD001",
                    "FIL001",
                    "Santana Store",
                    "2026-08-20",
                    "2026-09-30",
                    50,
                    7.99,
                    5.20,
                    10,
                    20,
                    40,
                    5),
                batch(
                    "2",
                    "PROD001",
                    "FIL002",
                    "Downtown Store",
                    "2026-08-20",
                    "2026-09-30",
                    70,
                    7.99,
                    5.20,
                    10,
                    20,
                    40,
                    5)));

    assertThat(products).hasSize(2);
    assertThat(products)
        .extracting(ProductDTO::id)
        .containsExactly("PROD001:FIL001", "PROD001:FIL002");
    assertThat(products).extracting(ProductDTO::currentStock).containsExactly(50, 70);
  }

  @Test
  void shouldIgnoreBatchesWithoutStockWhenCalculatingExpiration() {
    ProductDTO product =
        productService
            .consolidateProducts(
                List.of(
                    batch(
                        "1",
                        "PROD001",
                        "FIL001",
                        "Santana Store",
                        "2026-08-20",
                        "2026-08-20",
                        0,
                        7.99,
                        5.20,
                        0,
                        0,
                        40,
                        5),
                    batch(
                        "2",
                        "PROD001",
                        "FIL001",
                        "Santana Store",
                        "2026-08-21",
                        "2026-09-20",
                        10,
                        7.99,
                        5.20,
                        1,
                        2,
                        40,
                        5)))
            .get(0);

    assertThat(product.expirationDays()).isEqualTo(23);
  }

  @Test
  void shouldUsePriceAndCostFromMostRecentBatch() {
    ProductDTO product =
        productService
            .consolidateProducts(
                List.of(
                    batch(
                        "1",
                        "PROD001",
                        "FIL001",
                        "Santana Store",
                        "2026-08-20",
                        "2026-09-30",
                        50,
                        7.99,
                        5.20,
                        10,
                        20,
                        40,
                        5),
                    batch(
                        "2",
                        "PROD001",
                        "FIL001",
                        "Santana Store",
                        "2026-08-25",
                        "2026-10-30",
                        40,
                        8.49,
                        5.80,
                        10,
                        20,
                        40,
                        5)))
            .get(0);

    assertThat(product.price()).isEqualByComparingTo("8.49");
    assertThat(product.cost()).isEqualByComparingTo("5.80");
    assertThat(product.lastRestock()).isEqualTo(Instant.parse("2026-08-25T00:00:00Z"));
  }

  @Test
  void shouldConvertUnixTimestampToExpirationDate() {
    long timestamp = Instant.parse("2026-09-15T00:00:00Z").getEpochSecond();
    ErpBatchDTO batch =
        batch(
            "1",
            "PROD001",
            "FIL001",
            "Santana Store",
            "2026-08-20",
            timestamp,
            10,
            7.99,
            5.20,
            1,
            2,
            40,
            5);

    ProductDTO product = productService.consolidateProducts(List.of(batch)).get(0);

    assertThat(product.expirationDays()).isEqualTo(18);
  }

  @Test
  void shouldApplyFiltersAfterConsolidation() {
    configureErpResponse(
        """
        [
          {
            "id": "1",
            "codigo_produto_erp": "PROD001",
            "nome_produto": "Whole Milk 1L",
            "categoria": "Dairy",
            "unidade_medida": "UN",
            "num_lote": "LT001",
            "data_validade": "2026-09-30",
            "quantidade": "50",
            "preco": "7.99",
            "custo": "5.20",
            "codigo_filial_erp": "FIL001",
            "filial": "Santana Store",
            "certificado_qualidade": true,
            "data_entrada": "2026-08-20",
            "vendas_7d": "10",
            "vendas_30d": "20",
            "estoque_minimo": "40",
            "lead_time_dias": "5"
          },
          {
            "id": "2",
            "codigo_produto_erp": "PROD001",
            "nome_produto": "Whole Milk 1L",
            "categoria": "Dairy",
            "unidade_medida": "UN",
            "num_lote": "LT002",
            "data_validade": "2026-10-30",
            "quantidade": "20",
            "preco": "8.19",
            "custo": "5.40",
            "codigo_filial_erp": "FIL001",
            "filial": "Santana Store",
            "certificado_qualidade": true,
            "data_entrada": "2026-08-21",
            "vendas_7d": "5",
            "vendas_30d": "10",
            "estoque_minimo": "40",
            "lead_time_dias": "5"
          },
          {
            "id": "3",
            "codigo_produto_erp": "PROD002",
            "nome_produto": "Soda",
            "categoria": "Beverages",
            "unidade_medida": "UN",
            "num_lote": "LT003",
            "data_validade": "2026-10-30",
            "quantidade": "0",
            "preco": "5.99",
            "custo": "3.20",
            "codigo_filial_erp": "FIL002",
            "filial": "Downtown Store",
            "certificado_qualidade": true,
            "data_entrada": "2026-08-22",
            "vendas_7d": "0",
            "vendas_30d": "0",
            "estoque_minimo": "20",
            "lead_time_dias": "3"
          }
        ]
        """);

    assertThat(productService.listProducts("Santana Store", null, null))
        .extracting(ProductDTO::id)
        .containsExactly("PROD001:FIL001");
    assertThat(productService.listProducts(null, "Beverages", null))
        .extracting(ProductDTO::id)
        .containsExactly("PROD002:FIL002");
    assertThat(productService.listProducts(null, null, false))
        .extracting(ProductDTO::id)
        .containsExactly("PROD002:FIL002");
  }

  @Test
  void shouldResolveDivergentConfigurationWithHighestValue() {
    ProductDTO product =
        productService
            .consolidateProducts(
                List.of(
                    batch(
                        "1",
                        "PROD001",
                        "FIL001",
                        "Santana Store",
                        "2026-08-20",
                        "2026-09-30",
                        50,
                        7.99,
                        5.20,
                        10,
                        20,
                        40,
                        5),
                    batch(
                        "2",
                        "PROD001",
                        "FIL001",
                        "Santana Store",
                        "2026-08-21",
                        "2026-09-30",
                        40,
                        7.99,
                        5.20,
                        10,
                        20,
                        50,
                        7)))
            .get(0);

    assertThat(product.minimumStock()).isEqualTo(50);
    assertThat(product.supplierLeadTime()).isEqualTo(7);
  }

  @Test
  void shouldConvertErpFailureToIntegrationException() {
    server
        .expect(requestTo("http://erp.test/products"))
        .andExpect(method(GET))
        .andRespond(withServerError());

    assertThatThrownBy(() -> productService.listProducts(null, null, null))
        .isInstanceOf(ErpIntegrationException.class)
        .hasMessage("Could not connect to the external ERP API.");
  }

  private void configureErpResponse(String response) {
    server
        .expect(ExpectedCount.times(3), requestTo("http://erp.test/products"))
        .andExpect(method(GET))
        .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
  }

  private ErpBatchDTO batch(
      String id, String erpProductCode, String erpBranchCode, String branch, Object... values) {
    Object entryDate = values[0];
    Object expirationDate = values[1];
    Object quantity = values[2];
    Object price = values[3];
    Object cost = values[4];
    Object sales7d = values[5];
    Object sales30d = values[6];
    Object minimumStock = values[7];
    Object leadTime = values[8];

    return new ErpBatchDTO(
        id,
        erpProductCode,
        "Whole Milk 1L",
        "Dairy",
        "UN",
        "LT" + id,
        expirationDate,
        quantity,
        price,
        cost,
        erpBranchCode,
        branch,
        true,
        entryDate,
        sales7d,
        sales30d,
        minimumStock,
        leadTime);
  }
}
