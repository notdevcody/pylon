package cpw.mods.fml.relauncher;

public interface IClassTransformer {
    byte[] transform(String name, String transformedName, byte[] bytes);
}
