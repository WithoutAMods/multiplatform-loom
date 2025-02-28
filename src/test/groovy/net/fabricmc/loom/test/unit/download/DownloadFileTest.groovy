/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

package net.fabricmc.loom.test.unit.download

import io.javalin.http.HttpCode
import net.fabricmc.loom.util.download.Download
import net.fabricmc.loom.util.download.DownloadException
import net.fabricmc.loom.util.download.DownloadExecutor
import net.fabricmc.loom.util.download.DownloadProgressListener

import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.time.Instant

class DownloadFileTest extends DownloadTest {
	def "File: Simple"() {
		setup:
			server.get("/simpleFile") {
				it.result("Hello World")
			}

			def output = new File(File.createTempDir(), "file.txt").toPath()

		when:
			def result = Download.create("$PATH/simpleFile").downloadPath(output)

		then:
			Files.readString(output) == "Hello World"
	}

	def "File: Not found"() {
		setup:
			server.get("/fileNotfound") {
				it.status(404)
			}

			def output = new File(File.createTempDir(), "file.txt").toPath()

		when:
			def result = Download.create("$PATH/stringNotFound").downloadPath(output)

		then:
			thrown DownloadException
	}

	def "Cache: Sha1"() {
		setup:
			int requestCount = 0

			server.get("/sha1.txt") {
				it.result("Hello World")
				requestCount ++
			}

			def output = new File(File.createTempDir(), "file.txt").toPath()

		when:
			for (i in 0..<2) {
				Download.create("$PATH/sha1.txt")
					.sha1("0a4d55a8d778e5022fab701977c5d840bbc486d0")
					.downloadPath(output)
			}

		then:
			requestCount == 1
	}

	def "Invalid Sha1"() {
		setup:
			server.get("/sha1.invalid") {
				it.result("Hello World")
			}

			def output = new File(File.createTempDir(), "file.txt").toPath()

		when:
			Download.create("$PATH/sha1.invalid")
				.sha1("d139cccf047a749691416ce385d3f168c1e28309")
				.downloadPath(output)

		then:
			// Ensure the file we downloaded with the wrong hash was deleted
			Files.notExists(output)
			thrown DownloadException
	}

	def "Offline"() {
		setup:
			int requestCount = 0

			server.get("/offline.txt") {
				it.result("Hello World")
				requestCount ++
			}

			def output = new File(File.createTempDir(), "offline.txt").toPath()

		when:
			Download.create("$PATH/offline.txt")
				.downloadPath(output)

			Download.create("$PATH/offline.txt")
				.offline()
				.downloadPath(output)

		then:
			requestCount == 1
	}

	def "Max Age"() {
		setup:
			int requestCount = 0

			server.get("/maxage.txt") {
				it.result("Hello World")
				requestCount ++
			}

			def output = new File(File.createTempDir(), "maxage.txt").toPath()

		when:
			Download.create("$PATH/maxage.txt")
				.maxAge(Duration.ofDays(1))
				.downloadPath(output)

			Download.create("$PATH/maxage.txt")
				.maxAge(Duration.ofDays(1))
				.downloadPath(output)

			def twoDaysAgo = Instant.now() - Duration.ofDays(2)
			Files.setLastModifiedTime(output, FileTime.from(twoDaysAgo))

			Download.create("$PATH/maxage.txt")
				.maxAge(Duration.ofDays(1))
				.downloadPath(output)

		then:
			requestCount == 2
	}

	def "ETag"() {
		setup:
			int requestCount = 0

			server.get("/etag") {
				def clientEtag = it.req.getHeader("If-None-Match")

				def result = "Hello world"
				def etag = result.hashCode().toString()
				it.header("ETag", etag)

				if (clientEtag == etag) {
					// Etag matches, no need to send the data.
					it.status(HttpCode.NOT_MODIFIED)
					return
				}

				it.result(result)
				requestCount ++
			}

		def output = new File(File.createTempDir(), "etag.txt").toPath()

		when:
			for (i in 0..<2) {
				Download.create("$PATH/etag")
					.etag(true)
					.downloadPath(output)
			}

		then:
			requestCount == 1
	}

	def "Progress: File"() {
		setup:
			server.get("/progressFile") {
				it.result("Hello World")
			}

			def output = new File(File.createTempDir(), "file.txt").toPath()
			def started, ended = false

		when:
			Download.create("$PATH/progressFile")
				.progress(new DownloadProgressListener() {
					@Override
					void onStart() {
						started = true
					}

					@Override
					void onProgress(long bytesTransferred, long contentLength) {
					}

					@Override
					void onEnd(boolean success) {
						ended = true
					}
				})
				.downloadPath(output)

		then:
			started
			ended
	}

	def "Progress: String"() {
		setup:
			server.get("/progressString") {
				it.result("Hello World")
			}

			def started, ended = false

		when:
			Download.create("$PATH/progressFile")
				.progress(new DownloadProgressListener() {
					@Override
					void onStart() {
						started = true
					}

					@Override
					void onProgress(long bytesTransferred, long contentLength) {
					}

					@Override
					void onEnd(boolean success) {
						ended = true
					}
				})
				.downloadString()

		then:
			started
			ended
	}

	def "File: Async"() {
		setup:
			server.get("/async1") {
				it.result("Hello World")
			}

			def dir = File.createTempDir().toPath()

		when:
			new DownloadExecutor(2).withCloseable {
				Download.create("$PATH/async1").downloadPathAsync(dir.resolve("1.txt"), it)
				Download.create("$PATH/async1").downloadPathAsync(dir.resolve("2.txt"), it)
				Download.create("$PATH/async1").downloadPathAsync(dir.resolve("3.txt"), it)
				Download.create("$PATH/async1").downloadPathAsync(dir.resolve("4.txt"), it)
			}

		then:
			Files.readString(dir.resolve("4.txt")) == "Hello World"
	}

	def "File: Async Error"() {
		setup:
			server.get("/async2") {
				it.result("Hello World")
			}

			def dir = File.createTempDir().toPath()

		when:
			new DownloadExecutor(2).withCloseable {
				Download.create("$PATH/async2").downloadPathAsync(dir.resolve("1.txt"), it)
				Download.create("$PATH/async2").downloadPathAsync(dir.resolve("2.txt"), it)
				Download.create("$PATH/async2").downloadPathAsync(dir.resolve("3.txt"), it)
				Download.create("$PATH/async2").downloadPathAsync(dir.resolve("4.txt"), it)

				Download.create("$PATH/asyncError").downloadPathAsync(dir.resolve("5.txt"), it)
				Download.create("$PATH/asyncError2").downloadPathAsync(dir.resolve("6.txt"), it)
			}

		then:
			thrown DownloadException
	}
}
