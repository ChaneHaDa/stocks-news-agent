package com.newsagent.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public class NewsAgentApplicationTest {

    @Test
    void contextLoads() {
        // This test ensures that the Spring context loads successfully
        // with all the configurations and components we've created
    }
}