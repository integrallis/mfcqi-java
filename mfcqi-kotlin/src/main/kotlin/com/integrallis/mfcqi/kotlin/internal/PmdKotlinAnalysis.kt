package com.integrallis.mfcqi.kotlin.internal

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import net.sourceforge.pmd.lang.LanguageProcessorRegistry
import net.sourceforge.pmd.lang.ast.Node
import net.sourceforge.pmd.lang.ast.Parser
import net.sourceforge.pmd.lang.ast.SemanticErrorReporter
import net.sourceforge.pmd.lang.document.TextDocument
import net.sourceforge.pmd.lang.document.TextFile
import net.sourceforge.pmd.lang.kotlin.KotlinLanguageModule

internal data class KotlinNodeFact(
  val type: String,
  val text: String,
  val start: Int,
  val end: Int,
  val beginLine: Int,
)

internal data class KotlinFileAnalysis(
  val path: Path,
  val source: String,
  val nodes: List<KotlinNodeFact>,
) {
  fun nodes(type: String): List<KotlinNodeFact> = nodes.filter { it.type == type }

  fun within(container: KotlinNodeFact, type: String): List<KotlinNodeFact> =
    nodes.filter {
      (type.isEmpty() || it.type == type) && it.start > container.start && it.end <= container.end
    }
}

internal data class KotlinAnalysis(val files: List<KotlinFileAnalysis>) {
  val sourceLines: Int =
    files.sumOf { file ->
      file.source.lineSequence().count { line ->
        val value = line.trim()
        value.isNotEmpty() && !value.startsWith("//")
      }
    }
}

/**
 * PMD owns Kotlin lexing/parsing. This class only turns the typed PMD tree into immutable facts
 * that can safely be shared by MFCQI's concurrently executing metrics.
 */
internal object PmdKotlinAnalysis {
  private data class CacheKey(val path: Path, val fingerprint: Long)

  private val cache = ConcurrentHashMap<CacheKey, KotlinAnalysis>()

  fun analyze(codebase: Path): KotlinAnalysis {
    val root = codebase.toAbsolutePath().normalize()
    val files = KotlinSourceFiles.find(root)
    val key = CacheKey(root, fingerprint(files))
    cache.keys.removeIf { it.path == root && it != key }
    return cache.computeIfAbsent(key) { KotlinAnalysis(files.mapNotNull(::parse)) }
  }

  private fun fingerprint(files: List<Path>): Long =
    files.fold(1L) { hash, file ->
      runCatching {
          31L * hash +
            file.toAbsolutePath().normalize().hashCode() +
            Files.size(file) +
            Files.getLastModifiedTime(file).toMillis()
        }
        .getOrDefault(31L * hash + file.hashCode())
    }

  private fun parse(path: Path): KotlinFileAnalysis? =
    runCatching {
        val source = Files.readString(path, StandardCharsets.UTF_8)
        val language = KotlinLanguageModule.getInstance()
        val properties = language.newPropertyBundle()
        val processor = language.createProcessor(properties)
        LanguageProcessorRegistry.singleton(processor).use { registry ->
          TextDocument.create(
              TextFile.forPath(path, StandardCharsets.UTF_8, language.defaultVersion)
            )
            .use { document ->
              val task = Parser.ParserTask(document, SemanticErrorReporter.noop(), registry)
              val root = processor.services().parser.parse(task)
              KotlinFileAnalysis(path, source, flatten(root, source))
            }
        }
      }
      .getOrElse { throw IllegalStateException("PMD could not parse Kotlin source: $path", it) }

  private fun flatten(root: Node, source: String): List<KotlinNodeFact> {
    val facts = ArrayList<KotlinNodeFact>()

    fun visit(node: Node) {
      runCatching {
          val region = node.textRegion
          val start = region.startOffset.coerceIn(0, source.length)
          val end = region.endOffset.coerceIn(start, source.length)
          KotlinNodeFact(
            type = node.getXPathNodeName(),
            text = source.substring(start, end),
            start = start,
            end = end,
            beginLine = node.beginLine,
          )
        }
        .getOrNull()
        ?.let(facts::add)
      repeat(node.numChildren) { visit(node.getChild(it)) }
    }

    visit(root)
    return facts
  }
}
