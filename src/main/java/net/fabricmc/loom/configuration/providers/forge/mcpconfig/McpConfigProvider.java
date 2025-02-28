/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020-2022 FabricMC
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

package net.fabricmc.loom.configuration.providers.forge.mcpconfig;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.gradle.api.Project;

import net.fabricmc.loom.configuration.DependencyInfo;
import net.fabricmc.loom.configuration.providers.forge.DependencyProvider;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.ZipUtils;

public class McpConfigProvider extends DependencyProvider {
	private Path mcp;
	private Path configJson;
	private Path mappings;
	private McpConfigData data;

	public McpConfigProvider(Project project) {
		super(project);
	}

	@Override
	public void provide(DependencyInfo dependency) throws Exception {
		init(dependency.getDependency().getVersion());

		Path mcpZip = dependency.resolveFile().orElseThrow(() -> new RuntimeException("Could not resolve MCPConfig")).toPath();

		if (!Files.exists(mcp) || !Files.exists(configJson) || refreshDeps()) {
			Files.copy(mcpZip, mcp, StandardCopyOption.REPLACE_EXISTING);
			Files.write(configJson, ZipUtils.unpack(mcp, "config.json"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		}

		JsonObject json;

		try (Reader reader = Files.newBufferedReader(configJson)) {
			json = new Gson().fromJson(reader, JsonObject.class);
		}

		data = McpConfigData.fromJson(json);
	}

	private void init(String version) throws IOException {
		Path dir = getMinecraftProvider().dir("mcp/" + version).toPath();
		mcp = dir.resolve("mcp.zip");
		configJson = dir.resolve("mcp-config.json");
		mappings = dir.resolve("mcp-config-mappings.txt");

		if (refreshDeps()) {
			Files.deleteIfExists(mappings);
		}
	}

	public Path getMappings() {
		if (Files.notExists(mappings)) {
			try {
				Files.write(mappings, ZipUtils.unpack(getMcp(), getMappingsPath()), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			} catch (IOException e) {
				throw new IllegalStateException("Failed to find mappings '" + getMappingsPath() + "' in " + getMcp() + "!");
			}
		}

		return mappings;
	}

	public Path getMcp() {
		return mcp;
	}

	public boolean isOfficial() {
		return data.official();
	}

	public String getMappingsPath() {
		return data.mappingsPath();
	}

	@Override
	public String getTargetConfig() {
		return Constants.Configurations.MCP_CONFIG;
	}

	public McpConfigData getData() {
		return data;
	}
}
