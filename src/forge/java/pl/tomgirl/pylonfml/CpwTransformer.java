package pl.tomgirl.pylonfml;

import cpw.mods.fml.relauncher.IClassTransformer;
import pl.tomgirl.pylon.RuntimeClassLoader;
import pl.tomgirl.pylon.game.GameTransformer;

public final class CpwTransformer implements IClassTransformer {
    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        return bytes == null || RuntimeClassLoader.isRuntimeClass(name) ? bytes : GameTransformer.transform(bytes);
    }
}
