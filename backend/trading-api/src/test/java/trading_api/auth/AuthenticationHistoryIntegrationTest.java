package trading_api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import trading_api.analysis.Analysis;
import trading_api.analysis.AnalysisRepository;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationHistoryIntegrationTest {
    @Autowired MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired UserRepository users;
    @Autowired AnalysisRepository analyses;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    @BeforeEach
    void clean() { analyses.deleteAll(); users.deleteAll(); }

    @Test
    void test_register_success() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"register@example.com\",\"password\":\"secret6\",\"fullName\":\"Register User\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email", is("register@example.com")));
        User user = users.findByEmailIgnoreCase("register@example.com").orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches("secret6", user.getPasswordHash()));
        org.junit.jupiter.api.Assertions.assertNotEquals("secret6", user.getPasswordHash());
    }

    @Test
    void test_login_success() throws Exception {
        User user = user("login@example.com", "secret6");
        users.save(user);
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"login@example.com\",\"password\":\"secret6\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString()).andReturn().getResponse().getContentAsString();
        JsonNode token = objectMapper.readTree(body).get("accessToken");
        org.junit.jupiter.api.Assertions.assertEquals(user.getId().toString(), jwtService.parse(token.asText()).getSubject());
    }

    @Test
    void test_login_wrong_password() throws Exception {
        users.save(user("wrong@example.com", "secret6"));
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"wrong@example.com\",\"password\":\"incorrect\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void test_unauthorized_api() throws Exception {
        mockMvc.perform(get("/api/v1/analyses/my-history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void test_expired_token() throws Exception {
        User user = users.save(user("expired@example.com", "secret6"));
        String token = jwtService.generate(user, -1);
        mockMvc.perform(get("/api/v1/analyses/my-history").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message", is("Token has expired")));
    }

    @Test
    void test_data_isolation_user_a_cannot_access_user_b_data() throws Exception {
        User userA = users.save(user("a@example.com", "secret6"));
        User userB = users.save(user("b@example.com", "secret6"));
        Analysis analysisA = analysis(userA, "BTCUSDT");
        Analysis analysisB = analysis(userB, "ETHUSDT");
        String tokenA = login("a@example.com", "secret6");

        mockMvc.perform(get("/api/v1/analyses/my-history").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].symbol", is("BTCUSDT")));
        mockMvc.perform(get("/api/v1/analyses/" + analysisB.getId()).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
        org.junit.jupiter.api.Assertions.assertNotEquals(analysisA.getId(), analysisB.getId());
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }
    private User user(String email, String password) { User user = new User(); user.setEmail(email); user.setPasswordHash(passwordEncoder.encode(password)); return user; }
    private Analysis analysis(User user, String symbol) { Analysis analysis = new Analysis(); analysis.setUser(user); analysis.setSymbol(symbol); analysis.setTimeframe("1H"); analysis.setResultJson("{}"); return analyses.save(analysis); }
}