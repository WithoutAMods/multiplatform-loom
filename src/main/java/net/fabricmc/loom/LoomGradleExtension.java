/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2022 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.mercury.Mercury;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;

import net.fabricmc.loom.api.LoomGradleExtensionAPI;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.InstallerData;
import net.fabricmc.loom.configuration.LoomDependencyManager;
import net.fabricmc.loom.configuration.accesswidener.AccessWidenerFile;
import net.fabricmc.loom.configuration.processors.JarProcessorManager;
import net.fabricmc.loom.configuration.providers.forge.DependencyProviders;
import net.fabricmc.loom.configuration.providers.forge.ForgeProvider;
import net.fabricmc.loom.configuration.providers.forge.ForgeUniversalProvider;
import net.fabricmc.loom.configuration.providers.forge.ForgeUserdevProvider;
import net.fabricmc.loom.configuration.providers.forge.PatchProvider;
import net.fabricmc.loom.configuration.providers.forge.SrgProvider;
import net.fabricmc.loom.configuration.providers.forge.mcpconfig.McpConfigProvider;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProviderImpl;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.IntermediaryMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.NamedMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.SrgMinecraftProvider;
import net.fabricmc.loom.extension.LoomFiles;
import net.fabricmc.loom.extension.MixinExtension;
import net.fabricmc.loom.util.ModPlatform;
import net.fabricmc.loom.util.download.DownloadBuilder;

public interface LoomGradleExtension extends LoomGradleExtensionAPI {
	static LoomGradleExtension get(Project project) {
		return (LoomGradleExtension) project.getExtensions().getByName("loom");
	}

	LoomFiles getFiles();

	MappingSet getOrCreateSrcMappingCache(int id, Supplier<MappingSet> factory);

	Mercury getOrCreateSrcMercuryCache(int id, Supplier<Mercury> factory);

	ConfigurableFileCollection getUnmappedModCollection();

	void setInstallerData(InstallerData data);

	InstallerData getInstallerData();

	void setDependencyManager(LoomDependencyManager dependencyManager);

	LoomDependencyManager getDependencyManager();

	void setJarProcessorManager(JarProcessorManager jarProcessorManager);

	JarProcessorManager getJarProcessorManager();

	MinecraftProvider getMinecraftProvider();

	void setMinecraftProvider(MinecraftProvider minecraftProvider);

	MappingsProviderImpl getMappingsProvider();

	void setMappingsProvider(MappingsProviderImpl mappingsProvider);

	NamedMinecraftProvider<?> getNamedMinecraftProvider();

	IntermediaryMinecraftProvider<?> getIntermediaryMinecraftProvider();

	void setNamedMinecraftProvider(NamedMinecraftProvider<?> namedMinecraftProvider);

	void setIntermediaryMinecraftProvider(IntermediaryMinecraftProvider<?> intermediaryMinecraftProvider);

	SrgMinecraftProvider<?> getSrgMinecraftProvider();

	void setSrgMinecraftProvider(SrgMinecraftProvider<?> srgMinecraftProvider);

	default List<Path> getMinecraftJars(MappingsNamespace mappingsNamespace) {
		return switch (mappingsNamespace) {
		case NAMED -> getNamedMinecraftProvider().getMinecraftJars();
		case INTERMEDIARY -> getIntermediaryMinecraftProvider().getMinecraftJars();
		case OFFICIAL -> getMinecraftProvider().getMinecraftJars();
		case SRG -> {
			ModPlatform.assertPlatform(this, ModPlatform.FORGE, () -> "SRG jars are only available on Forge.");
			yield getSrgMinecraftProvider().getMinecraftJars();
		}
		};
	}

	FileCollection getMinecraftJarsCollection(MappingsNamespace mappingsNamespace);

	boolean isRootProject();

	@Override
	MixinExtension getMixin();

	List<AccessWidenerFile> getTransitiveAccessWideners();

	void addTransitiveAccessWideners(List<AccessWidenerFile> accessWidenerFiles);

	DownloadBuilder download(String url);

	boolean refreshDeps();

	void setRefreshDeps(boolean refreshDeps);

	// ===================
	//  Architectury Loom
	// ===================
	default PatchProvider getPatchProvider() {
		return getDependencyProviders().getProvider(PatchProvider.class);
	}

	default McpConfigProvider getMcpConfigProvider() {
		return getDependencyProviders().getProvider(McpConfigProvider.class);
	}

	default boolean isDataGenEnabled() {
		return isForge() && !getForge().getDataGenMods().isEmpty();
	}

	default boolean isForgeAndOfficial() {
		return isForge() && getMcpConfigProvider().isOfficial();
	}

	default boolean isForgeAndNotOfficial() {
		return isForge() && !getMcpConfigProvider().isOfficial();
	}

	DependencyProviders getDependencyProviders();

	void setDependencyProviders(DependencyProviders dependencyProviders);

	default SrgProvider getSrgProvider() {
		return getDependencyProviders().getProvider(SrgProvider.class);
	}

	default ForgeUniversalProvider getForgeUniversalProvider() {
		return getDependencyProviders().getProvider(ForgeUniversalProvider.class);
	}

	default ForgeUserdevProvider getForgeUserdevProvider() {
		return getDependencyProviders().getProvider(ForgeUserdevProvider.class);
	}

	default ForgeProvider getForgeProvider() {
		return getDependencyProviders().getProvider(ForgeProvider.class);
	}
}
