package com.quistock.ds_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

@SpringBootTest
class DsBackendApplicationTests {

  @MockitoBean(name = "erpRestClient") RestClient erpRestClient;

  @Test
  void contextLoads() {}
}
