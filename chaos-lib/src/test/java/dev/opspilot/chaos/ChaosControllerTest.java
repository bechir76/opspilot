package dev.opspilot.chaos;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ChaosControllerTest {

    private final ChaosState state = new ChaosState(code -> { });
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ChaosController(state)).build();

    @AfterEach
    void tearDown() {
        state.close();
    }

    @Test
    void setsLatencyAndReturnsStatus() throws Exception {
        mvc.perform(post("/chaos/latency").param("ms", "300"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latencyMs").value(300));

        mvc.perform(get("/chaos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latencyMs").value(300));
    }

    @Test
    void outOfRangeValueReturns400() throws Exception {
        mvc.perform(post("/chaos/errors").param("rate", "1.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("error rate must be between 0.0 and 1.0, got 1.5"));
    }

    @Test
    void missingOrMalformedParameterReturns400() throws Exception {
        mvc.perform(post("/chaos/cpu")).andExpect(status().isBadRequest());
        mvc.perform(post("/chaos/memory-leak").param("mbPerSecond", "lots")).andExpect(status().isBadRequest());
    }

    @Test
    void crashIsAccepted() throws Exception {
        mvc.perform(post("/chaos/crash")).andExpect(status().isAccepted());
    }
}
