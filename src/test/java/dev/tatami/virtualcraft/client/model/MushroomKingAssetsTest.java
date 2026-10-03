package dev.tatami.virtualcraft.client.model;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Resource-only tests: no Minecraft window or saved world is opened. */
class MushroomKingAssetsTest {
    private static final String ROOT = "/assets/virtual_craft/";
    private static final String TEXTURES = ROOT + "textures/entity/mushroom_king/";

    @Test
    void staticModelKeepsItsAuthoredScaleAndFaceBudget() throws Exception {
        JsonArray bones = geometry("virtual_mushroom_king").getAsJsonArray("bones");
        assertEquals(19, bones.size());
        int cubes = 0;
        int faces = 0;
        double maxY = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        for (var bone : bones) {
            for (var element : bone.getAsJsonObject().getAsJsonArray("cubes")) {
                JsonObject cube = element.getAsJsonObject();
                cubes++;
                faces += cube.getAsJsonObject("uv").size();
                double y = cube.getAsJsonArray("origin").get(1).getAsDouble();
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y + cube.getAsJsonArray("size").get(1).getAsDouble());
            }
        }
        assertEquals(188, cubes);
        assertEquals(1011, faces);
        assertEquals(0.0, minY, 0.0001);
        assertEquals(103.0, maxY, 0.0001);
        assertEquals(6.4375, maxY / 16.0, 0.0001);
    }

    @Test
    void emissiveLayerOnlyDrawsTwoFrontFacesWithMatchingParents() throws Exception {
        Map<String, JsonObject> body = new HashMap<>();
        for (var bone : geometry("virtual_mushroom_king").getAsJsonArray("bones")) {
            JsonObject object = bone.getAsJsonObject();
            body.put(object.get("name").getAsString(), object);
        }
        int faceCount = 0;
        for (var bone : geometry("virtual_mushroom_king_eyes").getAsJsonArray("bones")) {
            JsonObject eyeBone = bone.getAsJsonObject();
            JsonObject bodyBone = body.get(eyeBone.get("name").getAsString());
            assertNotNull(bodyBone);
            for (String key : new String[]{"parent", "pivot", "rotation"}) {
                assertEquals(bodyBone.get(key), eyeBone.get(key), key);
            }
            for (var cube : eyeBone.getAsJsonArray("cubes")) {
                JsonObject uv = cube.getAsJsonObject().getAsJsonObject("uv");
                assertEquals(1, uv.size());
                assertTrue(uv.has("north"));
                faceCount++;
            }
        }
        assertEquals(2, faceCount);
        BufferedImage mask = png(TEXTURES + "virtual_mushroom_king_emissive.png");
        BufferedImage albedo = png(TEXTURES + "virtual_mushroom_king.png");
        assertEquals(128, mask.getWidth());
        assertEquals(128, mask.getHeight());
        assertEquals(128, albedo.getWidth());
        assertEquals(128, albedo.getHeight());
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                boolean iris = x >= 28 && x < 56 && y >= 54 && y < 82;
                assertEquals(iris ? 255 : 0, mask.getRGB(x, y) >>> 24);
                assertEquals(x < 112 && y < 110 ? 255 : 0, albedo.getRGB(x, y) >>> 24,
                        "Keep the user's original transparent border");
                if (iris) {
                    assertEquals(albedo.getRGB(x, y), mask.getRGB(x, y),
                            "Emission must use the same indexed iris pixels as the body");
                }
            }
        }
    }

    @Test
    void indexedTexturesAndUvsFollowOriginalMaterialIslands() throws Exception {
        for (String name : new String[]{"virtual_mushroom_king", "virtual_mushroom_king_emissive"}) {
            String path = TEXTURES + name + ".png";
            try (InputStream stream = getClass().getResourceAsStream(path)) {
                assertNotNull(stream);
                byte[] header = stream.readNBytes(26);
                assertEquals(26, header.length);
                assertEquals(8, header[24], "8-bit PNG");
                assertEquals(3, header[25], "PNG palette-indexed color type");
            }
            assertFalse(json(path + ".mcmeta").getAsJsonObject("texture").get("blur").getAsBoolean());
        }
        for (String name : new String[]{"virtual_mushroom_king", "virtual_mushroom_king_eyes"}) {
            for (var bone : geometry(name).getAsJsonArray("bones")) {
                for (var cube : bone.getAsJsonObject().getAsJsonArray("cubes")) {
                    for (var entry : cube.getAsJsonObject().getAsJsonObject("uv").entrySet()) {
                        JsonObject face = entry.getValue().getAsJsonObject();
                        double u0 = face.getAsJsonArray("uv").get(0).getAsDouble();
                        double v0 = face.getAsJsonArray("uv").get(1).getAsDouble();
                        double u1 = u0 + face.getAsJsonArray("uv_size").get(0).getAsDouble();
                        double v1 = v0 + face.getAsJsonArray("uv_size").get(1).getAsDouble();
                        int col = (int) Math.floor(Math.min(u0, u1) / 28);
                        double minV = Math.min(v0,v1);
                        int row = minV < 26 ? 0 : minV < 54 ? 1 : minV < 82 ? 2 : 3;
                        int[] rows = {0,26,54,82,110};
                        assertTrue(col >= 0 && col < 4 && row >= 0 && row < 4);
                        assertTrue(Math.min(u0,u1) >= col*28 && Math.max(u0,u1) <= (col+1)*28);
                        assertTrue(minV >= rows[row] && Math.max(v0,v1) <= rows[row+1]);
                    }
                }
            }
        }
    }

    @Test
    void noLegacyCombatAnimationsOrSoundsRemain() throws Exception {
        assertTrue(json(ROOT + "animations/virtual_mushroom_king.animation.json")
                .getAsJsonObject("animations").isEmpty());
        assertTrue(json(ROOT + "sounds.json").keySet().stream()
                .noneMatch(key -> key.startsWith("mushroom_king.")));
        for (String path : new String[]{"textures/gui/boss/mushroom_king_bar.png",
                "particles/mushroom_spore_glow.json", "textures/effect/mushroom_telegraph.png"}) {
            try (InputStream stream = getClass().getResourceAsStream(ROOT + path)) {
                assertNull(stream, path);
            }
        }
    }

    private JsonObject geometry(String name) throws Exception {
        return json(ROOT + "geo/" + name + ".geo.json")
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
    }

    private JsonObject json(String path) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }

    private BufferedImage png(String path) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image, path);
            return image;
        }
    }
}
