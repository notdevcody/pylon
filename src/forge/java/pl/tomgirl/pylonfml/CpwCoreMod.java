package pl.tomgirl.pylonfml;

import java.util.Map;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

@SuppressWarnings("unused")
@IFMLLoadingPlugin.TransformerExclusions({"pl.tomgirl.pylonfml.", "pl.tomgirl.pylon.RuntimeClassLoader"})
public final class CpwCoreMod implements IFMLLoadingPlugin {
    @Override
    public String[] getLibraryRequestClass() {
        return null;
    }

    @Override
    public String[] getASMTransformerClass() {
        return null;
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return CpwTweaker.class.getName();
    }

    @Override
    public void injectData(Map<String, Object> data) {}
}
