package mono.factories.d3engine;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import mono.factories.noises.CoordinateNoiseGenerator;
import mono.factories.noises.perlin.Perlin;
import mono.factories.noises.perlin.Perlin2D;
import mono.factories.noises.perlin.PerlinFactory;
import mono.factories.noises.perlin.PerlinFactoryStorage;

import java.util.*;

public class Main extends Application {
    private static final double WIDTH = 850, HEIGHT = 500;
    private static final double FOV = 70;
    private static final float Z_FAR = 1000.0F, Z_NEAR = 0.1F;

    private static Vec3F cameraPosition = new Vec3F(0, 5, -10);
    private static double yaw = 0, pitch = 0.0;
    private static final double MOVE_SPEED = 10.0; // единиц в секунду
    private static final double ROT_SPEED = 60.0; // градусов в секунду

    private static final List<TriangleInWorld> worldTriangles = new ArrayList<>();
    private final Set<KeyCode> activeKeys = new HashSet<>();

    private Canvas canvas;
    private GraphicsContext gc;
    private Label fpsAndCoordsLabel;

    private static Perlin p2d = null;
    //optimisation
    private static final double[] X_BUF = new double[3];
    private static final double[] Y_BUF = new double[3];

    @Override
    public void start(Stage stage) {
        p2d = Perlin2D.getFactory().build(42);// я сам не знаю что за хуйню я в вместо библиотеки написал

        Pane root = new Pane();
        canvas = new Canvas(WIDTH, HEIGHT);
        gc = canvas.getGraphicsContext2D();

        fpsAndCoordsLabel = new Label();
        fpsAndCoordsLabel.setTextFill(Color.WHITE);
        fpsAndCoordsLabel.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5); -fx-padding: 5px;");

        root.getChildren().addAll(canvas, fpsAndCoordsLabel);
        Scene scene = new Scene(root, WIDTH, HEIGHT);

        scene.setOnKeyPressed(e -> activeKeys.add(e.getCode()));
        scene.setOnKeyReleased(e -> activeKeys.remove(e.getCode()));

        stage.setTitle("3D Engine (JavaFX)");
        stage.setScene(scene);
        stage.show();

        // Генерация ландшафта при старте
        generateTerrain(200, 200, 0.077228f, 3.5f);

        startRenderLoop();
    }

    // Временная заглушка для шума Перлина (замените своей библиотекой)
    private static float getPerlinNoise(float x, float z) {
        // Пример: return (float) YourPerlinLibrary.noise(x * 0.1, z * 0.1);
        return (float) p2d.get(x, z) + 0.35f;// generator.get(x, z) -> [-0.5, 0.5]; [-0.15, 0.85]
    }

    private void generateTerrain(int mapWidth, int mapDepth, float scale, float heightMultiplier) {
        worldTriangles.clear();

        for (int z = 0; z < mapDepth - 1; z++) {
            for (int x = 0; x < mapWidth - 1; x++) {
                float x0 = (x - mapWidth / 2.0f) * scale;
                float z0 = (z - mapDepth / 2.0f) * scale;
                float x1 = x0 + scale;
                float z1 = z0 + scale;

                float y00 = getPerlinNoise(x0, z0) * heightMultiplier;
                float y10 = getPerlinNoise(x1, z0) * heightMultiplier;
                float y01 = getPerlinNoise(x0, z1) * heightMultiplier;
                float y11 = getPerlinNoise(x1, z1) * heightMultiplier;

                Vec3F v00 = new Vec3F(x0, y00, z0);
                Vec3F v10 = new Vec3F(x1, y10, z0);
                Vec3F v01 = new Vec3F(x0, y01, z1);
                Vec3F v11 = new Vec3F(x1, y11, z1);

                int color00 = getColorByHeight(y00);
                int color10 = getColorByHeight(y10);
                int color01 = getColorByHeight(y01);
                int color11 = getColorByHeight(y11);

                // Два треугольника на каждую ячейку сетки
                worldTriangles.add(new TriangleInWorld(v00, color00, v01, color01, v10, color10));
                worldTriangles.add(new TriangleInWorld(v10, color10, v01, color01, v11, color11));
            }
        }
    }

    private int getColorByHeight(float y) {
        if (y < -0.5f) return 0xFF0000FF; // Вода (синий)
        if (y < 1.0f)  return 0xFF228B22; // Трава (зеленый)
        if (y < 2.5f)  return 0xFF8B4513; // Горы/Земля (коричневый)
        return 0xFFFFFFFF;                // Снег (белый)
    }

    private void startRenderLoop() {
        new AnimationTimer() {
            private long lastTime = System.nanoTime();

            @Override
            public void handle(long now) {
                double deltaTime = (now - lastTime) / 1e9;
                lastTime = now;

                updateInput(deltaTime);
                render();

                double fps = 1.0 / Math.max(deltaTime, 0.0001);
                fpsAndCoordsLabel.setText(String.format("FPS: %.1f | Pos: %s | Yaw: %.1f | Pitch: %.1f",
                        fps, cameraPosition, yaw, pitch));
            }
        }.start();
    }

    private void updateInput(double dt) {
        float moveDist = (float) (MOVE_SPEED * dt);
        float rotDist = (float) (ROT_SPEED * dt);

        if (activeKeys.contains(KeyCode.W)) {
            Vec3F dir = GeomCalculating.getRotateMat(yaw, pitch).multiply(new Vec3F(0, 0, 1)).normalize();
            cameraPosition = cameraPosition.add(dir.multiply(moveDist));
        }
        if (activeKeys.contains(KeyCode.S)) {
            Vec3F dir = GeomCalculating.getRotateMat(yaw, pitch).multiply(new Vec3F(0, 0, -1)).normalize();
            cameraPosition = cameraPosition.add(dir.multiply(moveDist));
        }
        if (activeKeys.contains(KeyCode.A)) {
            Vec3F dir = GeomCalculating.getRotateMat(yaw - 90, 0).multiply(new Vec3F(0, 0, 1)).normalize();
            cameraPosition = cameraPosition.add(dir.multiply(moveDist));
        }
        if (activeKeys.contains(KeyCode.D)) {
            Vec3F dir = GeomCalculating.getRotateMat(yaw + 90, 0).multiply(new Vec3F(0, 0, 1)).normalize();
            cameraPosition = cameraPosition.add(dir.multiply(moveDist));
        }
        if (activeKeys.contains(KeyCode.SPACE)) {
            cameraPosition = cameraPosition.add(new Vec3F(0, moveDist, 0));
        }
        if (activeKeys.contains(KeyCode.SHIFT)) {
            cameraPosition = cameraPosition.add(new Vec3F(0, -moveDist, 0));
        }

        if (activeKeys.contains(KeyCode.RIGHT)) yaw = (yaw + rotDist) % 360;
        if (activeKeys.contains(KeyCode.LEFT)) yaw = (yaw - rotDist + 360) % 360;
        if (activeKeys.contains(KeyCode.DOWN)) pitch = Math.min(90, pitch + rotDist);
        if (activeKeys.contains(KeyCode.UP)) pitch = Math.max(-90, pitch - rotDist);
    }

    public void render() {
        gc.setFill(Color.DARKGRAY);
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        Mat3F combinedMat = getMatMinecraftToCameraMat(Math.toRadians(-yaw), Math.toRadians(-pitch));
        Mat4F projMat = GeomCalculating.getProjMat();

        List<TransformedTriangle> rasterQueue = new ArrayList<>();

        for (TriangleInWorld tri : worldTriangles) {
            // Перевод вершин в систему координат камеры
            Vec3F v0Cam = combinedMat.multiply(tri.v.sub(cameraPosition));
            Vec3F v1Cam = combinedMat.multiply(tri.v1.sub(cameraPosition));
            Vec3F v2Cam = combinedMat.multiply(tri.v2.sub(cameraPosition));

            // Клиппинг относительно ближней плоскости (Z_NEAR)
            List<TriangleCamSpace> clippedTriangles = clipTriangleAgainstNearPlane(
                    new TriangleCamSpace(v0Cam, tri.color, v1Cam, tri.color1, v2Cam, tri.color2)
            );

            // Проецирование отсеченных треугольников
            for (TriangleCamSpace clipped : clippedTriangles) {
                Vec4F a = project(clipped.v0, projMat);
                Vec4F b = project(clipped.v1, projMat);
                Vec4F c = project(clipped.v2, projMat);

                Triangle t2d = getTriangle(a, b, c, clipped.c0, clipped.c1, clipped.c2);
                float avgZ = (clipped.v0.z() + clipped.v1.z() + clipped.v2.z()) / 3.0f;
                rasterQueue.add(new TransformedTriangle(t2d, avgZ));
            }
        }

        // Сортировка алгоритмом художника (от дальних к ближним)
        rasterQueue.sort((t1, t2) -> Float.compare(t2.avgZ, t1.avgZ));

        for (TransformedTriangle tt : rasterQueue) {
            drawTriangle(gc, tt.triangle);
        }
    }

    private static Vec4F project(Vec3F cameraBindVec, Mat4F projMat) {
        Vec4F projectedVec = projMat.multiply(new Vec4F(cameraBindVec.x(), cameraBindVec.y(), cameraBindVec.z(), 1));
        if (projectedVec.w() != 0) {
            projectedVec = projectedVec.multiply(1 / projectedVec.w());
        }
        return projectedVec;
    }

    // Функция отсечения полигонов по ближней плоскости Z_NEAR
    private static List<TriangleCamSpace> clipTriangleAgainstNearPlane(TriangleCamSpace tri) {
        List<TriangleCamSpace> result = new ArrayList<>();

        Vec3F[] verts = {tri.v0, tri.v1, tri.v2};
        int[] colors = {tri.c0, tri.c1, tri.c2};

        List<Integer> insideIndices = new ArrayList<>();
        List<Integer> outsideIndices = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            if (verts[i].z() >= Z_NEAR) {
                insideIndices.add(i);
            } else {
                outsideIndices.add(i);
            }
        }

        if (insideIndices.size() == 3) {
            // Треугольник полностью впереди
            result.add(tri);
        } else if (insideIndices.size() == 1) {
            // 1 вершина спереди, 2 сзади -> создаем 1 уменьшенный треугольник
            int i0 = insideIndices.get(0);
            int o0 = outsideIndices.get(0);
            int o1 = outsideIndices.get(1);

            float t0 = (Z_NEAR - verts[i0].z()) / (verts[o0].z() - verts[i0].z());
            float t1 = (Z_NEAR - verts[i0].z()) / (verts[o1].z() - verts[i0].z());

            Vec3F newV1 = interpolate(verts[i0], verts[o0], t0);
            Vec3F newV2 = interpolate(verts[i0], verts[o1], t1);

            result.add(new TriangleCamSpace(verts[i0], colors[i0], newV1, colors[o0], newV2, colors[o1]));
        } else if (insideIndices.size() == 2) {
            // 2 вершины спереди, 1 сзади -> разбиваем на 2 треугольника (четырехугольник)
            int i0 = insideIndices.get(0);
            int i1 = insideIndices.get(1);
            int o0 = outsideIndices.get(0);

            float t0 = (Z_NEAR - verts[i0].z()) / (verts[o0].z() - verts[i0].z());
            float t1 = (Z_NEAR - verts[i1].z()) / (verts[o0].z() - verts[i1].z());

            Vec3F newV0 = interpolate(verts[i0], verts[o0], t0);
            Vec3F newV1 = interpolate(verts[i1], verts[o0], t1);

            result.add(new TriangleCamSpace(verts[i0], colors[i0], verts[i1], colors[i1], newV0, colors[o0]));
            result.add(new TriangleCamSpace(verts[i1], colors[i1], newV1, colors[o0], newV0, colors[o0]));
        }
        // Если insideIndices.size() == 0, треугольник целиком позади и игнорируется

        return result;
    }

    private static Vec3F interpolate(Vec3F a, Vec3F b, float t) {
        return new Vec3F(
                a.x() + t * (b.x() - a.x()),
                a.y() + t * (b.y() - a.y()),
                a.z() + t * (b.z() - a.z())
        );
    }

    private static void drawTriangle(GraphicsContext gc, Triangle tri) {
        int a = (((tri.color >> 24) & 0xFF) + ((tri.color1 >> 24) & 0xFF) + ((tri.color2 >> 24) & 0xFF)) / 3;
        int r = (((tri.color >> 16) & 0xFF) + ((tri.color1 >> 16) & 0xFF) + ((tri.color2 >> 16) & 0xFF)) / 3;
        int g = (((tri.color >> 8) & 0xFF) + ((tri.color1 >> 8) & 0xFF) + ((tri.color2 >> 8) & 0xFF)) / 3;
        int b = ((tri.color & 0xFF) + (tri.color1 & 0xFF) + (tri.color2 & 0xFF)) / 3;

        Color color = Color.rgb(r, g, b, Math.max(0.0, Math.min(1.0, a / 255.0)));

        X_BUF[0] = tri.x;  X_BUF[1] = tri.x1; X_BUF[2] = tri.x2;
        Y_BUF[0] = tri.y;  Y_BUF[1] = tri.y1; Y_BUF[2] = tri.y2;

        gc.setFill(color);
        gc.fillPolygon(X_BUF, Y_BUF, 3);
    }

    private static Triangle getTriangle(Vec4F a, Vec4F b, Vec4F c, int color, int color1, int color2) {
        return new Triangle(
                (int) ((a.x + 1.0) * 0.5 * WIDTH), (int) ((1.0 - a.y) * 0.5 * HEIGHT), (int) a.z, color,
                (int) ((b.x + 1.0) * 0.5 * WIDTH), (int) ((1.0 - b.y) * 0.5 * HEIGHT), (int) b.z, color1,
                (int) ((c.x + 1.0) * 0.5 * WIDTH), (int) ((1.0 - c.y) * 0.5 * HEIGHT), (int) c.z, color2
        );
    }

    private static Mat3F getMatMinecraftToCameraMat(double yawRad, double pitchRad) {
        return Mat3F.rotateX((float) pitchRad).multiply(Mat3F.rotateY((float) yawRad));
    }

    public static void main(String[] args) {
        launch(args);
    }

    // --- Вспомогательные классы и геометрия ---

    private record TransformedTriangle(Triangle triangle, float avgZ) {}

    private record TriangleCamSpace(Vec3F v0, int c0, Vec3F v1, int c1, Vec3F v2, int c2) {}

    public static final class GeomCalculating {
        public static Mat4F getProjMat() {
            float aspect = (float) (WIDTH / HEIGHT);
            float tanHalfFov = (float) Math.tan(Math.toRadians(FOV / 2.0));
            return new Mat4F(
                    1.0f / (aspect * tanHalfFov), 0, 0, 0,
                    0, 1.0f / tanHalfFov, 0, 0,
                    0, 0, Z_FAR / (Z_FAR - Z_NEAR), -Z_FAR * Z_NEAR / (Z_FAR - Z_NEAR),
                    0, 0, 1, 0
            );
        }

        public static Mat3F getRotateMat(double yawDeg, double pitchDeg) {
            return Mat3F.rotateY((float) Math.toRadians(yawDeg))
                    .multiply(Mat3F.rotateX((float) Math.toRadians(pitchDeg)));
        }
    }

    public record TriangleInWorld(Vec3F v, int color, Vec3F v1, int color1, Vec3F v2, int color2) {}

    public record Triangle(int x, int y, int z, int color, int x1, int y1, int z1, int color1, int x2, int y2, int z2, int color2) {}

    public record Vec3F(float x, float y, float z) {
        public Vec3F add(Vec3F v) { return new Vec3F(x + v.x, y + v.y, z + v.z); }
        public Vec3F sub(Vec3F v) { return new Vec3F(x - v.x, y - v.y, z - v.z); }
        public Vec3F multiply(float s) { return new Vec3F(x * s, y * s, z * s); }
        public float dot(Vec3F v) { return x * v.x + y * v.y + z * v.z; }
        public float length() { return (float) Math.sqrt(x * x + y * y + z * z); }
        public Vec3F normalize() {
            float len = length();
            return len == 0 ? this : multiply(1 / len);
        }
    }

    public record Vec4F(float x, float y, float z, float w) {
        public Vec4F multiply(float s) { return new Vec4F(x * s, y * s, z * s, w * s); }
    }

    public record Mat3F(float m00, float m01, float m02, float m10, float m11, float m12, float m20, float m21, float m22) {
        public Vec3F multiply(Vec3F v) {
            return new Vec3F(
                    v.x * m00 + v.y * m01 + v.z * m02,
                    v.x * m10 + v.y * m11 + v.z * m12,
                    v.x * m20 + v.y * m21 + v.z * m22
            );
        }

        public Mat3F multiply(Mat3F o) {
            return new Mat3F(
                    m00*o.m00 + m01*o.m10 + m02*o.m20, m00*o.m01 + m01*o.m11 + m02*o.m21, m00*o.m02 + m01*o.m12 + m02*o.m22,
                    m10*o.m00 + m11*o.m10 + m12*o.m20, m10*o.m01 + m11*o.m11 + m12*o.m21, m10*o.m02 + m11*o.m12 + m12*o.m22,
                    m20*o.m00 + m21*o.m10 + m22*o.m20, m20*o.m01 + m21*o.m11 + m22*o.m21, m20*o.m02 + m21*o.m12 + m22*o.m22
            );
        }

        public static Mat3F rotateX(float angleRad) {
            float c = (float) Math.cos(angleRad), s = (float) Math.sin(angleRad);
            return new Mat3F(1, 0, 0, 0, c, -s, 0, s, c);
        }

        public static Mat3F rotateY(float angleRad) {
            float c = (float) Math.cos(angleRad), s = (float) Math.sin(angleRad);
            return new Mat3F(c, 0, s, 0, 1, 0, -s, 0, c);
        }
    }

    public record Mat4F(float m00, float m01, float m02, float m03,
                        float m10, float m11, float m12, float m13,
                        float m20, float m21, float m22, float m23,
                        float m30, float m31, float m32, float m33) {

        public Vec4F multiply(Vec4F v) {
            return new Vec4F(
                    v.x*m00 + v.y*m01 + v.z*m02 + v.w*m03,
                    v.x*m10 + v.y*m11 + v.z*m12 + v.w*m13,
                    v.x*m20 + v.y*m21 + v.z*m22 + v.w*m23,
                    v.x*m30 + v.y*m31 + v.z*m32 + v.w*m33
            );
        }
    }
}