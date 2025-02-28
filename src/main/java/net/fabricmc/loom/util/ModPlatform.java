/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2022 FabricMC
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

package net.fabricmc.loom.util;

import java.util.Locale;
import java.util.function.Supplier;

import org.gradle.api.GradleException;
import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.LoomGradleExtensionAPI;

public enum ModPlatform {
	FABRIC(false),
	FORGE(false),
	QUILT(true);

	boolean experimental;

	ModPlatform(boolean experimental) {
		this.experimental = experimental;
	}

	public boolean isExperimental() {
		return experimental;
	}

	public static void assertPlatform(Project project, ModPlatform platform) {
		assertPlatform(LoomGradleExtension.get(project), platform);
	}

	public static void assertPlatform(LoomGradleExtensionAPI extension, ModPlatform platform) {
		assertPlatform(extension, platform, () -> {
			String msg = "Loom is not running on %s.%nYou can switch to it by adding 'loom.platform = %s' to your gradle.properties";
			String name = platform.name().toLowerCase(Locale.ROOT);
			return msg.formatted(name, name);
		});
	}

	public static void assertPlatform(LoomGradleExtensionAPI extension, ModPlatform platform, Supplier<String> message) {
		if (!extension.getPlatforms().get().contains(platform)) {
			throw new GradleException(message.get());
		}
	}
}
