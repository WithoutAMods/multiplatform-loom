/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020-2021 FabricMC
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

package net.fabricmc.loom.configuration;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.collect.ImmutableMap;
import groovy.util.Node;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublishingExtension;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.DeprecationHelper;
import net.fabricmc.loom.util.GroovyXmlUtil;
import net.fabricmc.loom.util.gradle.GradleUtils;

public final class MavenPublication {
	// ImmutableMap is needed since it guarantees ordering
	// (compile must go before runtime, or otherwise dependencies might get the "weaker" runtime scope).
	private static final Map<String, String> CONFIGURATION_TO_SCOPE = ImmutableMap.of(
			JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME, "compile",
			JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME, "runtime"
	);
	private static final Set<Publication> EXCLUDED_PUBLICATIONS = Collections.newSetFromMap(new WeakHashMap<>());

	private MavenPublication() {
	}

	public static void configure(Project project) {
		GradleUtils.afterSuccessfulEvaluation(project, () -> {
			AtomicBoolean reportedDeprecation = new AtomicBoolean(false);

			CONFIGURATION_TO_SCOPE.forEach((configurationName, scope) -> {
				Configuration config = project.getConfigurations().getByName(configurationName);

				// add modsCompile to maven-publish
				PublishingExtension mavenPublish = project.getExtensions().findByType(PublishingExtension.class);

				if (mavenPublish != null) {
					project.getLogger().info("Processing maven publication for project [" + project.getName() + "] of " + configurationName);
					processEntry(project, scope, config, mavenPublish, reportedDeprecation);
				}
			});
		});
	}

	private static boolean hasSoftwareComponent(Publication publication) {
		try {
			Method getComponent = publication.getClass().getMethod("getComponent");
			return getComponent.invoke(publication) != null;
		} catch (ReflectiveOperationException e) {
			// our hacks have broken!
			return false;
		}
	}

	// TODO: Remove this in Loom 0.12
	private static void processEntry(Project project, String scope, Configuration config, PublishingExtension mavenPublish, AtomicBoolean reportedDeprecation) {
		mavenPublish.publications((publications) -> {
			for (Publication publication : publications) {
				if (!(publication instanceof org.gradle.api.publish.maven.MavenPublication mavenPublication)) {
					continue;
				}

				if (hasSoftwareComponent(publication) || EXCLUDED_PUBLICATIONS.contains(publication)) {
					continue;
				} else if (!reportedDeprecation.get() && !LoomGradleExtension.get(project).isForge()) {
					DeprecationHelper deprecationHelper = LoomGradleExtension.get(project).getDeprecationHelper();
					deprecationHelper.warn("Loom is applying dependency data manually to publications instead of using a software component (from(components[\"java\"])). This is deprecated and will be removed in Loom 0.13.");
					reportedDeprecation.set(true);
				}

				project.getLogger().info("Processing maven publication [" + publication.getName() + "]");
				mavenPublication.pom((pom) -> pom.withXml((xml) -> {
					Node dependencies = GroovyXmlUtil.getOrCreateNode(xml.asNode(), "dependencies");
					Set<String> foundArtifacts = new HashSet<>();

					GroovyXmlUtil.childrenNodesStream(dependencies).filter((n) -> "dependency".equals(n.name())).forEach((n) -> {
						Optional<Node> groupId = GroovyXmlUtil.getNode(n, "groupId");
						Optional<Node> artifactId = GroovyXmlUtil.getNode(n, "artifactId");

						if (groupId.isPresent() && artifactId.isPresent()) {
							foundArtifacts.add(groupId.get().text() + ":" + artifactId.get().text());
						}
					});

					for (Dependency dependency : config.getAllDependencies()) {
						if (foundArtifacts.contains(dependency.getGroup() + ":" + dependency.getName())) {
							project.getLogger().info("Found inserted artifact " + dependency.getGroup() + ":" + dependency.getName());
							continue;
						}

						project.getLogger().info("Inserting artifact " + dependency.getGroup() + ":" + dependency.getName());

						Node depNode = dependencies.appendNode("dependency");
						depNode.appendNode("groupId", dependency.getGroup());
						depNode.appendNode("artifactId", dependency.getName());
						depNode.appendNode("version", dependency.getVersion());
						depNode.appendNode("scope", scope);

						if (!(dependency instanceof ModuleDependency)) {
							continue;
						}

						final Set<ExcludeRule> exclusions = ((ModuleDependency) dependency).getExcludeRules();

						if (exclusions.isEmpty()) {
							continue;
						}

						Node exclusionsNode = depNode.appendNode("exclusions");

						for (ExcludeRule rule : exclusions) {
							Node exclusionNode = exclusionsNode.appendNode("exclusion");
							exclusionNode.appendNode("groupId", rule.getGroup() == null ? "*" : rule.getGroup());
							exclusionNode.appendNode("artifactId", rule.getModule() == null ? "*" : rule.getModule());
						}
					}
				}));
			}
		});
	}

	public static void excludePublication(Publication publication) {
		EXCLUDED_PUBLICATIONS.add(publication);
	}
}
