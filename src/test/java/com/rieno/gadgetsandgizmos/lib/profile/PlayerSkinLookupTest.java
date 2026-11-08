package com.rieno.gadgetsandgizmos.lib.profile;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Check profile decoding without depending on the live account service
class PlayerSkinLookupTest{
    @Test
    void readsWideAndSlimSkinsAndSecuresLegacyUrls(){
        for(boolean slim : new boolean[]{false, true}){
            GameProfile profile = profile("http://textures.minecraft.net/texture/test", slim);
            var res = PlayerSkinLookup.fromProfile(profile);
            assertTrue(res.found());
            assertEquals("Custom_Player", res.playerName());
            assertEquals("https://textures.minecraft.net/texture/test", res.skinUrl());
            assertEquals(slim, res.slim());
            assertSame(profile, res.profile());
            assertEquals("signature", res.profile().getProperties().get("textures").iterator().next().signature());
        }
    }

    @Test
    void rejectsUntrustedUrlsAndMalformedTextures(){
        for(String url : new String[]{"https://example.com/texture/test", "file:///texture/test",
                "https://textures.minecraft.net:8080/texture/test", "https://textures.minecraft.net/other"}){
            assertFalse(PlayerSkinLookup.fromProfile(profile(url, false)).found());
        }
        GameProfile invalid = new GameProfile(UUID.randomUUID(), "Custom_Player");
        invalid.getProperties().put("textures", new Property("textures", "not base64"));
        assertFalse(PlayerSkinLookup.fromProfile(invalid).found());
        assertFalse(PlayerSkinLookup.fromProfile(null).found());
    }

    @Test
    void invalidNamesFinishWithoutANetworkRequest(){
        for(String name : new String[]{"", "ab", "name with spaces", "abcdefghijklmnopq", "../player"}){
            assertEquals("", PlayerSkinLookup.normalizePlayerName(name));
            var future = PlayerSkinLookup.lookup(name);
            assertTrue(future.isDone());
            assertEquals(PlayerSkinLookup.Status.PLAYER_DOES_NOT_EXIST, future.join().status());
        }
        assertEquals("Custom_Player", PlayerSkinLookup.normalizePlayerName(" Custom_Player "));
    }

    private static GameProfile profile(String url, boolean slim){
        GameProfile profile = new GameProfile(UUID.randomUUID(), "Custom_Player");
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url
                + "\",\"metadata\":{\"model\":\"" + (slim ? "slim" : "default") + "\"}}}}";
        String value = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        profile.getProperties().put("textures", new Property("textures", value, "signature"));
        return profile;
    }
}
