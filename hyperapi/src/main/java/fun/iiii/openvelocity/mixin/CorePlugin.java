package fun.iiii.openvelocity.mixin;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class CorePlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(final @NotNull String mixinPackage) {
    }

    @Override
    public @Nullable String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(final @NotNull String targetClassName, final @NotNull String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(final @NotNull Set<String> myTargets, final @NotNull Set<String> otherTargets) {
    }

    @Override
    public @Nullable List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(final @NotNull String targetClassName, final @NotNull ClassNode targetClass, final @NotNull String mixinClassName, final @NotNull IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(final @NotNull String targetClassName, final @NotNull ClassNode targetClass, final @NotNull String mixinClassName, final @NotNull IMixinInfo mixinInfo) {
    }
}