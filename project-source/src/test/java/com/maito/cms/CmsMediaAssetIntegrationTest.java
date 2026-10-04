package com.maito.cms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CmsMediaAssetIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should serve hero banner WebP asset with 30-day Cache-Control header")
    void shouldServeHeroBannerAsset() throws Exception {
        mockMvc.perform(get("/assets/brands/mito_crunch/banners/hero_roasted_makhana.webp"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=2592000")));
    }

    @Test
    @DisplayName("Should serve Peri Peri product image asset")
    void shouldServePeriPeriProductAsset() throws Exception {
        mockMvc.perform(get("/assets/brands/mito_crunch/products/makhana_peri_peri.webp"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=2592000")));
    }

    @Test
    @DisplayName("Should serve Himalayan Salt product image asset")
    void shouldServeHimalayanSaltProductAsset() throws Exception {
        mockMvc.perform(get("/assets/brands/mito_crunch/products/makhana_himalayan_salt.webp"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=2592000")));
    }

    @Test
    @DisplayName("Should serve Mint Magic product image asset")
    void shouldServeMintMagicProductAsset() throws Exception {
        mockMvc.perform(get("/assets/brands/mito_crunch/products/makhana_mint_magic.webp"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=2592000")));
    }

    @Test
    @DisplayName("Should serve brand vector SVG logo")
    void shouldServeBrandSvgLogo() throws Exception {
        mockMvc.perform(get("/assets/brands/mito_crunch/brand/logo_crunch.svg"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should serve visual storefront index.html at root path /")
    void shouldServeStorefrontRoot() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
    }
}
