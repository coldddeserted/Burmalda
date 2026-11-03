package com.burmalda;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Тесты гоняем на БД в памяти, чтобы не трогать ./data */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:test;DB_CLOSE_DELAY=-1")
class BurmaldaApplicationTests {

    @Test
    void contextLoads() {
    }
}
