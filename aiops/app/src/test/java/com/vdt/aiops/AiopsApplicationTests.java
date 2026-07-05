package com.vdt.aiops;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Full-context boot needs the live Docker + PostgreSQL environment (Docker client, Flyway, etc.).
// Disabled so the pure unit-test suite runs without infra; enable manually when the stack is up.
@Disabled("integration test — requires Docker + PostgreSQL running")
@SpringBootTest
class AiopsApplicationTests {

	@Test
	void contextLoads() {
	}

}
