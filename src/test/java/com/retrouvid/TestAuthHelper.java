package com.retrouvid;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Helper pour les tests : register + OTP verify → access token en une ligne.
 * Requiert app.otp.expose-code=true (application-test.yml).
 */
final class TestAuthHelper {

    private TestAuthHelper() {}

    static String registerAndGetToken(MockMvc mvc, ObjectMapper om,
                                      String email, String password,
                                      String firstName, String lastName) throws Exception {
        String body = """
                {"email":"%s","password":"%s","firstName":"%s","lastName":"%s"}
                """.formatted(email, password, firstName, lastName);
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        MvcResult otpRes = mvc.perform(post("/api/v1/auth/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk()).andReturn();
        String code = om.readTree(otpRes.getResponse().getContentAsString())
                .at("/data/code").asText();

        MvcResult verifyRes = mvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(verifyRes.getResponse().getContentAsString())
                .at("/data/accessToken").asText();
    }
}
