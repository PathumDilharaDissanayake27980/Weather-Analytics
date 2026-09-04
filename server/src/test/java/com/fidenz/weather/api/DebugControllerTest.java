package com.fidenz.weather.api;

import com.fidenz.weather.support.Fixtures;
import com.fidenz.weather.weather.WeatherCaches;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the cache debug endpoint the assignment asks for.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DebugControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WeatherCaches caches;

    @BeforeEach
    void resetCaches() {
        caches.clearAll();
    }

    @Test
    @DisplayName("reports both caches by name")
    void reportsBothCaches() throws Exception {
        mockMvc.perform(get("/api/debug/cache"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caches", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.caches[0].name").value("raw-weather"))
                .andExpect(jsonPath("$.caches[1].name").value("processed-dashboard"));
    }

    @Test
    @DisplayName("reports the configured TTL for each cache")
    void reportsTtl() throws Exception {
        mockMvc.perform(get("/api/debug/cache"))
                .andExpect(jsonPath("$.caches[0].ttlSeconds").value(300))
                .andExpect(jsonPath("$.caches[1].ttlSeconds").value(300));
    }

    @Test
    @DisplayName("starts empty with zeroed counters")
    void startsEmpty() throws Exception {
        mockMvc.perform(get("/api/debug/cache"))
                .andExpect(jsonPath("$.caches[0].size").value(0))
                .andExpect(jsonPath("$.caches[0].hits").value(0))
                .andExpect(jsonPath("$.caches[0].misses").value(0))
                .andExpect(jsonPath("$.caches[0].hitRate").value(0.0));
    }

    @Test
    @DisplayName("counts a MISS then a HIT and reports the resulting hit rate")
    void reflectsHitsAndMisses() throws Exception {
        caches.raw().get("1248991");                        // miss
        caches.raw().put("1248991", Fixtures.colombo());
        caches.raw().get("1248991");                        // hit
        caches.processed().get(WeatherCaches.DASHBOARD_KEY); // miss

        mockMvc.perform(get("/api/debug/cache"))
                .andExpect(jsonPath("$.caches[0].hits").value(1))
                .andExpect(jsonPath("$.caches[0].misses").value(1))
                .andExpect(jsonPath("$.caches[0].hitRate").value(0.5))
                .andExpect(jsonPath("$.caches[1].misses").value(1));
    }

    @Test
    @DisplayName("lists each live key with its age and remaining lifetime")
    void listsLiveEntries() throws Exception {
        caches.raw().put("1248991", Fixtures.colombo());

        mockMvc.perform(get("/api/debug/cache"))
                .andExpect(jsonPath("$.caches[0].entries", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.caches[0].entries[0].key").value("1248991"))
                .andExpect(jsonPath("$.caches[0].entries[0].ageSeconds").exists())
                .andExpect(jsonPath("$.caches[0].entries[0].expiresInSeconds").exists())
                .andExpect(jsonPath("$.caches[0].entries[0].expired").value(false));
    }

    @Test
    @DisplayName("includes the server time so entry ages can be interpreted")
    void includesServerTime() throws Exception {
        mockMvc.perform(get("/api/debug/cache"))
                .andExpect(jsonPath("$.serverTime").exists());
    }

    @Test
    @DisplayName("DELETE empties both caches and resets the counters")
    void deleteClearsCaches() throws Exception {
        caches.raw().put("1248991", Fixtures.colombo());
        caches.raw().get("1248991");

        mockMvc.perform(delete("/api/debug/cache"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/debug/cache"))
                .andExpect(jsonPath("$.caches[0].size").value(0))
                .andExpect(jsonPath("$.caches[0].hits").value(0))
                .andExpect(jsonPath("$.caches[0].misses").value(0));
    }
}
