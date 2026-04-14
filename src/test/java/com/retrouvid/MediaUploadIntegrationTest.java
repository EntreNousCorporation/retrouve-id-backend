package com.retrouvid;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaUploadIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private byte[] sampleJpeg() throws Exception {
        BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0, 0, 400, 300);
        g.setColor(Color.BLACK); g.drawString("CNI 123456789", 40, 250);
        g.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "jpg", out);
            return out.toByteArray();
        }
    }

    private String register() throws Exception {
        return TestAuthHelper.registerAndGetToken(mvc, om, "media@test.com", "password123", "M", "T");
    }

    @Test
    void uploadAndFetchPreviewAndOriginal() throws Exception {
        String token = register();
        MockMultipartFile file = new MockMultipartFile("file", "id.jpg", "image/jpeg", sampleJpeg());

        MvcResult up = mvc.perform(multipart("/api/v1/media/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andReturn();
        String id = om.readTree(up.getResponse().getContentAsString()).at("/data/id").asText();

        byte[] preview = mvc.perform(get("/api/v1/media/" + id + "/preview"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(preview.length).isGreaterThan(100);

        mvc.perform(get("/api/v1/media/" + id + "/original")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void uploadBase64_dataUri_succeeds() throws Exception {
        String token = TestAuthHelper.registerAndGetToken(
                mvc, om, "media-b64@test.com", "password123", "M", "B");
        String dataUri = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(sampleJpeg());

        MvcResult up = mvc.perform(post("/api/v1/media/upload-base64")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(
                                new java.util.HashMap<>(java.util.Map.of("base64", dataUri, "blur", false)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andReturn();
        String id = om.readTree(up.getResponse().getContentAsString()).at("/data/id").asText();

        byte[] preview = mvc.perform(get("/api/v1/media/" + id + "/preview"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(preview.length).isGreaterThan(100);
    }

    @Test
    void uploadBase64_invalidPayload_rejected() throws Exception {
        String token = TestAuthHelper.registerAndGetToken(
                mvc, om, "media-b64-bad@test.com", "password123", "M", "B");

        mvc.perform(post("/api/v1/media/upload-base64")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"base64\":\"data:image/gif;base64,AAAA\"}"))
                .andExpect(status().isBadRequest());
    }
}
