package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.ProductDTO;
import com.quistock.ds_backend.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Produtos")
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "accessCookie")
@ApiResponse(
    responseCode = "401",
    description = "Access JWT ausente, expirado ou inválido.",
    content = @Content)
@ApiResponse(
    responseCode = "403",
    description = "Permissão insuficiente ou CSRF inválido em autenticação por cookie.",
    content = @Content)
@RestController
@RequestMapping("/products")
public class ProductController {

  private final ProductService productService;

  public ProductController(ProductService service) {
    this.productService = service;
  }

  @GetMapping
  @Operation(
      summary = "Listar produtos",
      description = "Consulta produtos consolidados por produto e filial no PostgreSQL.",
      parameters = {
        @Parameter(name = "branch", description = "Nome ou código ERP da filial."),
        @Parameter(name = "category", description = "Nome da categoria."),
        @Parameter(
            name = "status",
            description = "true: estoque positivo; false: estoque zerado. Omitir retorna ambos.")
      })
  @ApiResponse(responseCode = "200", description = "Operação concluída.")
  @ApiResponse(
      responseCode = "400",
      description =
          "Parâmetros ou corpo inválidos. Credenciais de autenticação ambíguas também retornam"
              + " 400.",
      content =
          @Content(
              schema = @Schema(implementation = com.quistock.ds_backend.model.dto.ErrorDTO.class)))
  @ApiResponse(
      responseCode = "503",
      description = "Dependência indisponível; no chat, também pode indicar IA não configurada.",
      content =
          @Content(
              schema = @Schema(implementation = com.quistock.ds_backend.model.dto.ErrorDTO.class)))
  public List<ProductDTO> listProducts(
      @RequestParam(name = "branch", required = false) String branch,
      @RequestParam(name = "category", required = false) String category,
      @RequestParam(name = "status", required = false) Boolean status) {
    return productService.listProducts(branch, category, status);
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Consultar produto",
      description = "Consulta um produto pelo identificador composto retornado na listagem.",
      parameters = {
        @Parameter(name = "id", description = "ID retornado por GET /products, incluindo a filial.")
      })
  @ApiResponse(responseCode = "200", description = "Operação concluída.")
  @ApiResponse(
      responseCode = "404",
      description = "Recurso não encontrado ou fora do escopo permitido.",
      content =
          @Content(
              schema = @Schema(implementation = com.quistock.ds_backend.model.dto.ErrorDTO.class)))
  @ApiResponse(
      responseCode = "503",
      description = "Dependência indisponível; no chat, também pode indicar IA não configurada.",
      content =
          @Content(
              schema = @Schema(implementation = com.quistock.ds_backend.model.dto.ErrorDTO.class)))
  public ProductDTO findProductById(@PathVariable("id") String id) {
    return productService.findProductById(id);
  }
}
