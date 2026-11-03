package com.burmalda;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Tests run on an in-memory database so ./data is untouched */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:test;DB_CLOSE_DELAY=-1")
class BurmaldaApplicationTests {

    @Test
    void contextLoads() {
    }
}
