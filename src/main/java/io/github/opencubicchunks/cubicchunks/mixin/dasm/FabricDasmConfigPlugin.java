/*
 * Adapted from dasm's io.github.notstirred.dasm.mod.BaseConfigPlugin and neoforge.DasmConfigPlugin (dasm-neoforge 3.2.0, MIT licence,
 * Copyright (c) NotStirred): the mixin transformer comes from Mixin's own environment rather than ModLauncher's service, and the dasm
 * configs from each mod's fabric.mod.json ("custom": {"dasm": [...]}) rather than NeoForge's mod list.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions: The above copyright notice and this permission notice shall be included in all copies or substantial portions
 * of the Software. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED.
 */
package io.github.opencubicchunks.cubicchunks.mixin.dasm;

import static org.objectweb.asm.Opcodes.ACC_INTERFACE;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.gson.Gson;
import io.github.notstirred.dasm.annotation.AnnotationUtil;
import io.github.notstirred.dasm.api.provider.MappingsProvider;
import io.github.notstirred.dasm.exception.NoSuchTypeExists;
import io.github.notstirred.dasm.mod.DasmBootstrap;
import io.github.notstirred.dasm.mod.DasmConfig;
import io.github.notstirred.dasm.mod.DasmExtension;
import io.github.notstirred.dasm.util.CachingClassProvider;
import io.github.notstirred.dasm.util.ClassNodeProvider;
import io.github.notstirred.dasm.util.TypeUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.CustomValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/**
 * Runs dasm on Fabric. Mixin calls shouldApplyMixin for the three dummy mixins of {@code dasm-fabric.mixins.json} in order: the first two
 * apply (so the config is live), and on the third every class named in a dasm config that is not itself a mixin is added as a target of
 * the dummies, so Mixin visits it and dasm's extension can transform it.
 */
public class FabricDasmConfigPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("dasm");
    private static final String CONFIG_NAME = "dasm-fabric.mixins.json";

    private final ClassNodeProvider classNodeProvider = new CachingClassProvider(FabricDasmConfigPlugin::classBytes);
    private DasmExtension extension;
    private int invocations = 0;

    private static Optional<byte[]> classBytes(String className) {
        String resource = className.replace('.', '/') + ".class";
        try (InputStream in = MixinService.getService().getResourceAsStream(resource)) {
            if (in == null) {
                return Optional.empty();
            }
            return Optional.of(new DataInputStream(in).readAllBytes());
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("The specified resource '%s' was invalid or could not be read", resource), e);
        }
    }

    @Override public void onLoad(String mixinPackage) {
        this.extension = DasmBootstrap.init(MappingsProvider.IDENTITY, classNodeProvider);
    }

    private static List<DasmConfig> dasmConfigs() {
        Gson gson = new Gson();
        List<DasmConfig> configs = new ArrayList<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            CustomValue value = mod.getMetadata().getCustomValue("dasm");
            if (value == null || value.getType() != CustomValue.CvType.ARRAY) {
                continue;
            }
            for (CustomValue entry : value.getAsArray()) {
                String resource = entry.getAsString();
                try (InputStream in = MixinService.getService().getResourceAsStream(resource)) {
                    if (in == null) {
                        throw new IllegalArgumentException(String.format("The specified resource '%s' was invalid or could not be read", resource));
                    }
                    configs.add(gson.fromJson(new InputStreamReader(in), DasmConfig.class));
                } catch (IOException e) {
                    LOGGER.error("Failed to load dasm config {} of mod {}", resource, mod.getMetadata().getId(), e);
                }
            }
        }
        return configs;
    }

    /**
     * A dedicated server has no client classes, so a dasm class in a client package (transforming client code) is left out there, as the
     * mixin config's "client" list is; scanning it would look up its missing target and stop the server from starting.
     */
    private static boolean appliesHere(String dasmClass) {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT || !dasmClass.contains(".client.");
    }

    @Override public String getRefMapperConfig() {
        return null;
    }

    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        invocations++;
        if (invocations <= 2) {
            return true; // Must apply the first dummies
        } else if (invocations == 3) {
            DummyTargets dummyTargets = reflectIntoDummyTargets();
            Set<String> dasmTypes = new HashSet<>();
            for (DasmConfig dasmConfig : dasmConfigs()) {
                for (String dasmClass : dasmConfig.dasmClasses) {
                    if (!appliesHere(dasmClass)) {
                        continue;
                    }
                    this.extension.shouldApplyMixin(dasmClass);
                    dasmTypes.add(dasmClass);
                }
            }
            try {
                for (String dasmClass : dasmTypes) {
                    ClassNode classNode = classNodeProvider.classNode(Type.getObjectType(TypeUtil.classNameToInternalName(dasmClass)));
                    if (!AnnotationUtil.isAnnotationPresent(classNode.invisibleAnnotations, Mixin.class)) {
                        dummyTargets.add(dasmClass, (classNode.access & ACC_INTERFACE) != 0);
                    }
                }
            } catch (NoSuchTypeExists e) {
                throw new RuntimeException(e);
            }
            return false; // The last dummy itself is not needed
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static DummyTargets reflectIntoDummyTargets() {
        try {
            Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();

            Class<?> mixinTransformerClass = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer");
            Field processorField = mixinTransformerClass.getDeclaredField("processor");
            processorField.setAccessible(true);
            Object processor = processorField.get(transformer);

            Class<?> mixinProcessorClass = Class.forName("org.spongepowered.asm.mixin.transformer.MixinProcessor");
            Field pendingConfigsField = mixinProcessorClass.getDeclaredField("pendingConfigs");
            pendingConfigsField.setAccessible(true);
            List<IMixinConfig> pendingConfigs = (List<IMixinConfig>) pendingConfigsField.get(processor);

            IMixinConfig config = pendingConfigs.stream().filter(c -> c.getName().equals(CONFIG_NAME)).findFirst()
                    .orElseThrow(() -> new RuntimeException("Dasm failed to initialize, missing dasm mixin config " + CONFIG_NAME));

            Class<?> mixinConfigClass = Class.forName("org.spongepowered.asm.mixin.transformer.MixinConfig");
            Field pendingMixinsField = mixinConfigClass.getDeclaredField("pendingMixins");
            pendingMixinsField.setAccessible(true);
            List<IMixinInfo> pendingMixins = (List<IMixinInfo>) pendingMixinsField.get(config);

            Class<?> mixinInfoClass = Class.forName("org.spongepowered.asm.mixin.transformer.MixinInfo");
            Field declaredTargetsField = mixinInfoClass.getDeclaredField("declaredTargets");
            declaredTargetsField.setAccessible(true);
            List<Object> classTargets = (List<Object>) declaredTargetsField.get(pendingMixins.get(0));
            List<Object> interfaceTargets = (List<Object>) declaredTargetsField.get(pendingMixins.get(1));

            Class<?> declaredTargetClass = Class.forName("org.spongepowered.asm.mixin.transformer.MixinInfo$DeclaredTarget");
            Constructor<?> declaredTargetConstructor = declaredTargetClass.getDeclaredConstructor(String.class, boolean.class);
            declaredTargetConstructor.setAccessible(true);

            classTargets.clear();
            interfaceTargets.clear();
            return (target, isInterface) -> {
                try {
                    Object declaredTarget = declaredTargetConstructor.newInstance(target, false);
                    (isInterface ? interfaceTargets : classTargets).add(declaredTarget);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            };
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to initialise DASM", e);
        }
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override public List<String> getMixins() {
        return null;
    }

    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @FunctionalInterface
    interface DummyTargets {
        void add(String target, boolean isInterface);
    }
}
