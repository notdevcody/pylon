package pl.tomgirl.pylon;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.logging.Level;

import org.lwjgl.sdl.SDLStdinc;
import org.lwjgl.system.MemoryStack;

public final class HrtfData {
    private static final String FILE = "SADIE II D1.mhr";
    private static boolean installed;

    private HrtfData() {}

    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        try (DirectoryStream<Path> sets = Files.newDirectoryStream(Paths.get(""),
                p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mhr"))) {
            if (sets.iterator().hasNext()) {
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            Path dir = Files.createTempDirectory("pylon-hrtf");
            String dirPath = dir.toAbsolutePath().toString();
            Charset charset = Charset.forName(System.getProperty("sun.jnu.encoding", "UTF-8"));
            if (!charset.newEncoder().canEncode(dirPath)) {
                Files.delete(dir);
                Pylon.LOG.warning("HRTF data path " + dirPath + " is not representable in " + charset + ", using built-in HRTF");
                return;
            }
            Path file = dir.resolve(FILE);
            dir.toFile().deleteOnExit();
            file.toFile().deleteOnExit();
            try (InputStream in = HrtfData.class.getResourceAsStream("/assets/pylon/hrtf/" + FILE)) {
                if (in == null) {
                    throw new IllegalStateException("Missing " + FILE);
                }
                Files.copy(in, file);
            }
            byte[] path = dirPath.getBytes(charset);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer value = stack.malloc(path.length + 1).put(path).put((byte) 0);
                value.flip();
                SDLStdinc.SDL_setenv_unsafe(stack.ASCII("ALSOFT_LOCAL_PATH"), value, 0);
            }
        } catch (Throwable t) {
            Pylon.LOG.log(Level.WARNING, "Failed to install HRTF data set", t);
        }
    }
}
