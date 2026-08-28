package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.ProdutoDTO;
import com.quistock.ds_backend.service.ProdutoService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/produtos")
public class ProdutoController {

  private final ProdutoService produtoService;

  public ProdutoController(ProdutoService service) {
    this.produtoService = service;
  }

  @GetMapping
  public List<ProdutoDTO> listarProdutos(
      @RequestParam(name = "filial", required = false) String filial,
      @RequestParam(name = "categoria", required = false) String categoria,
      @RequestParam(name = "status", required = false) Boolean status) {
    return produtoService.listarProdutos(filial, categoria, status);
  }
}
