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
        assertEquals(23, bones.size()); // Two eye pivots and two eyelid pivots.
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
        assertEquals(292, cubes); // Existing eyes regrouped; only two thin lids added.
        assertEquals(1315, faces);
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
    void everyRenderedFaceUsesSquareTexelsAtTheSameDensity() throws Exception {
        for (String name : new String[]{"virtual_mushroom_king", "virtual_mushroom_king_eyes"}) {
            for (var bone : geometry(name).getAsJsonArray("bones")) {
                for (var element : bone.getAsJsonObject().getAsJsonArray("cubes")) {
                    JsonObject cube = element.getAsJsonObject();
                    JsonArray size = cube.getAsJsonArray("size");
                    for (var entry : cube.getAsJsonObject("uv").entrySet()) {
                        String side = entry.getKey();
                        boolean horizontal = side.equals("up") || side.equals("down");
                        boolean lateral = side.equals("east") || side.equals("west");
                        double width = size.get(lateral ? 2 : 0).getAsDouble();
                        double height = size.get(horizontal ? 2 : 1).getAsDouble();
                        JsonArray uvSize = entry.getValue().getAsJsonObject().getAsJsonArray("uv_size");
                        assertEquals(width, Math.abs(uvSize.get(0).getAsDouble()), 0.00001,
                                name + " / " + side + " horizontal texture stretch");
                        assertEquals(height, Math.abs(uvSize.get(1).getAsDouble()), 0.00001,
                                name + " / " + side + " vertical texture stretch");
                    }
                }
            }
        }
    }

    @Test
    void geckoConversionKeepsOuterWallsAndRemovesOnlyInnerSplitWalls() throws Exception {
        int checked = 0;
        for (var element : geometry("virtual_mushroom_king").getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            if (!bone.get("name").getAsString().equals("hips")) continue;
            for (var cubeElement : bone.getAsJsonArray("cubes")) {
                JsonObject cube = cubeElement.getAsJsonObject();
                JsonArray size = cube.getAsJsonArray("size");
                JsonArray origin = cube.getAsJsonArray("origin");
                // The split pelvis spans native X=-17..7 and 7..17.
                if (size.get(1).getAsDouble() != 18 || size.get(2).getAsDouble() != 22) continue;
                double nativeMinX = -origin.get(0).getAsDouble()-size.get(0).getAsDouble();
                JsonObject faces = cube.getAsJsonObject("uv");
                if (Math.abs(nativeMinX+17) < 0.00001) {
                    assertTrue(faces.has("west"), "Missing outer left wall after Gecko conversion");
                    assertFalse(faces.has("east"), "Internal cut must not replace the exterior wall");
                    checked++;
                } else if (Math.abs(nativeMinX-7) < 0.00001) {
                    assertTrue(faces.has("east"), "Missing outer right wall after Gecko conversion");
                    assertFalse(faces.has("west"));
                    checked++;
                }
            }
        }
        assertEquals(2, checked);
    }

    @Test
    void geckoUvEndpointsMatchItsNonMirroredVertexOrder() throws Exception {
        // GeoQuad.build swaps the U endpoints, while VertexSet keeps face keys.
        // Our north/east/up/down source projections increase U; south/west
        // decrease U. The emitted signs must compensate exactly once.
        for (var bone : geometry("virtual_mushroom_king").getAsJsonArray("bones")) {
            for (var cube : bone.getAsJsonObject().getAsJsonArray("cubes")) {
                for (var entry : cube.getAsJsonObject().getAsJsonObject("uv").entrySet()) {
                    String side = entry.getKey();
                    JsonArray span = entry.getValue().getAsJsonObject().getAsJsonArray("uv_size");
                    boolean reverseU = side.equals("south") || side.equals("west");
                    assertEquals(reverseU, span.get(0).getAsDouble() > 0, side + " U orientation");
                    assertEquals(!side.equals("up"), span.get(1).getAsDouble() > 0, side + " V orientation");
                }
            }
        }
    }

    @Test
    void allFourHangingGillsAreAttachedWithoutChangingTheirDimensions() throws Exception {
        JsonArray crown = null;
        for (var element : geometry("virtual_mushroom_king").getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            if (bone.get("name").getAsString().equals("crown_cap")) crown = bone.getAsJsonArray("cubes");
        }
        assertNotNull(crown);
        int count = 0;
        for (var element : crown) {
            JsonObject cube = element.getAsJsonObject();
            JsonArray size = cube.getAsJsonArray("size");
            JsonArray origin = cube.getAsJsonArray("origin");
            double h = size.get(1).getAsDouble();
            if (size.get(0).getAsDouble() != 3 || size.get(2).getAsDouble() != 3
                    || (h != 6 && h != 8) || origin.get(2).getAsDouble() != -24) continue;
            count++;
            double top = origin.get(1).getAsDouble()+h;
            assertEquals(82.25, top, 0.00001, "Gill tip must penetrate the Y=82 underside");
            for (int ix : new int[]{0,1}) {
                for (int iz : new int[]{0,1}) {
                    double x = origin.get(0).getAsDouble()+3*ix;
                    double z = origin.get(2).getAsDouble()+3*iz;
                    boolean supported = false;
                    for (var supportElement : crown) {
                        JsonObject support = supportElement.getAsJsonObject();
                        if (support == cube) continue;
                        JsonArray p = support.getAsJsonArray("origin");
                        JsonArray s = support.getAsJsonArray("size");
                        if (x >= p.get(0).getAsDouble() && x <= p.get(0).getAsDouble()+s.get(0).getAsDouble()
                                && z >= p.get(2).getAsDouble() && z <= p.get(2).getAsDouble()+s.get(2).getAsDouble()
                                && top > p.get(1).getAsDouble() && top < p.get(1).getAsDouble()+s.get(1).getAsDouble()) {
                            supported = true;
                            break;
                        }
                    }
                    assertTrue(supported, "Each attachment corner must be inside solid cap");
                }
            }
        }
        assertEquals(4, count);
    }

    @Test
    void noLegacyCombatAnimationsOrSoundsRemain() throws Exception {
        assertEquals(18,json(ROOT + "animations/virtual_mushroom_king.animation.json")
                .getAsJsonObject("animations").size());
        assertTrue(json(ROOT + "sounds.json").keySet().stream()
                .noneMatch(key -> key.startsWith("mushroom_king.") && !key.equals("mushroom_king.dodge_success")));
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
