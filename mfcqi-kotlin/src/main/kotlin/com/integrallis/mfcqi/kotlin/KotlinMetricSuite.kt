package com.integrallis.mfcqi.kotlin

import com.integrallis.mfcqi.core.Metric
import com.integrallis.mfcqi.kotlin.internal.KotlinAnalysis
import com.integrallis.mfcqi.kotlin.internal.KotlinFileAnalysis
import com.integrallis.mfcqi.kotlin.internal.KotlinNodeFact
import com.integrallis.mfcqi.kotlin.internal.KotlinSourceFiles
import com.integrallis.mfcqi.kotlin.internal.PmdKotlinAnalysis
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh
import net.sourceforge.pmd.cpd.CPDConfiguration
import net.sourceforge.pmd.cpd.CpdAnalysis
import net.sourceforge.pmd.lang.LanguageRegistry
import net.sourceforge.pmd.lang.kotlin.KotlinLanguageModule

internal abstract class KotlinDoubleMetric(
  private val metricName: String,
  private val metricWeight: Double,
) : Metric<Double>() {
  final override fun getName(): String = metricName

  final override fun getWeight(): Double = metricWeight

  override fun validateCodebase(codebase: Path): Boolean =
    Files.isDirectory(codebase) || Files.isRegularFile(codebase)

  protected fun analysis(codebase: Path): KotlinAnalysis = PmdKotlinAnalysis.analyze(codebase)
}

internal class KotlinCognitiveComplexity : KotlinDoubleMetric("Cognitive Complexity", 0.75) {
  override fun extract(codebase: Path): Double {
    val values =
      analysis(codebase).files.flatMap { file ->
        file.nodes("FunctionDeclaration").map { function ->
          val decisions = decisionNodes(file, function)
          decisions
            .sumOf { decision ->
              1 +
                decisions.count { parent ->
                  parent !== decision &&
                    parent.start <= decision.start &&
                    parent.end >= decision.end &&
                    (parent.start < decision.start || parent.end > decision.end)
                }
            }
            .toDouble()
        }
      }
    return values.averageOrZero()
  }

  override fun normalize(value: Double?): Double {
    val v = value ?: 0.0
    return when {
      v <= 5.0 -> 1.0 - v / 50.0
      v <= 10.0 -> 0.9 - (v - 5.0) / 25.0
      v <= 15.0 -> 0.7 - (v - 10.0) / 16.67
      v <= 25.0 -> 0.4 - (v - 15.0) / 50.0
      else -> max(0.0, 0.2 - (v - 25.0) / 125.0)
    }
  }
}

internal class KotlinHalsteadVolume : KotlinDoubleMetric("Halstead Volume", 0.65) {
  override fun extract(codebase: Path): Double =
    analysis(codebase).files.map(::halsteadVolume).filter { it > 0.0 }.averageOrZero()

  override fun normalize(value: Double?): Double {
    val v = value ?: return 1.0
    if (v <= 0.0) return 1.0
    if (v >= 25_000.0) return 0.0
    return (1.0 - tanh(v / 12_500.0)).coerceIn(0.0, 1.0)
  }
}

internal class KotlinMaintainabilityIndex : KotlinDoubleMetric("Maintainability Index", 0.5) {
  override fun extract(codebase: Path): Double {
    val values =
      analysis(codebase).files.map { file ->
        val sloc = sourceLines(file.source)
        val volume = halsteadVolume(file) / 5.0
        val complexity = totalComplexity(file)
        val comments = commentLines(file.source)
        mi(volume, complexity, sloc, if (sloc == 0) 0.0 else comments * 100.0 / sloc)
      }
    return if (values.isEmpty()) 100.0 else values.average()
  }

  override fun normalize(value: Double?): Double {
    val v = value ?: return 0.0
    return when {
      v <= 0.0 -> 0.0
      v >= 70.0 -> 0.85 + 0.15 * min(1.0, (v - 70.0) / 30.0)
      v >= 50.0 -> 0.70 + 0.15 * (v - 50.0) / 20.0
      v >= 30.0 -> 0.50 + 0.20 * (v - 30.0) / 20.0
      v >= 20.0 -> 0.25 + 0.25 * (v - 20.0) / 10.0
      else -> 0.25 * v / 20.0
    }
  }
}

internal class KotlinCodeDuplication : KotlinDoubleMetric("Code Duplication", 0.6) {
  override fun extract(codebase: Path): Double {
    val files = KotlinSourceFiles.find(codebase)
    if (files.isEmpty()) return 0.0
    val language = KotlinLanguageModule.getInstance()
    val config = CPDConfiguration(LanguageRegistry.singleton(language))
    config.minimumTileSize = 10
    config.setOnlyRecognizeLanguage(language)
    val duplicated = mutableSetOf<String>()
    CpdAnalysis.create(config).use { cpd ->
      files.forEach { cpd.files().addFile(it, language) }
      cpd.performAnalysis { report ->
        report.matches.forEach { match ->
          match.forEach { mark ->
            val location = mark.location
            for (line in location.startLine..location.endLine) {
              duplicated += "${location.fileId.absolutePath}:$line"
            }
          }
        }
      }
    }
    val total = files.sumOf { runCatching { Files.readAllLines(it).size }.getOrDefault(0) }
    return if (total == 0) 0.0 else duplicated.size * 100.0 / total
  }

  override fun normalize(value: Double?): Double {
    val v = value ?: return 1.0
    return when {
      v <= 0.0 -> 1.0
      v >= 50.0 -> 0.0
      v <= 5.0 -> 1.0 - v / 50.0
      v <= 15.0 -> 0.9 - (v - 5.0) / 10.0 * 0.4
      v <= 30.0 -> 0.5 - (v - 15.0) / 15.0 * 0.4
      else -> max(0.0, 0.1 - (v - 30.0) / 20.0 * 0.1)
    }
  }
}

internal class KotlinDocumentationCoverage : KotlinDoubleMetric("Documentation Coverage", 0.45) {
  override fun extract(codebase: Path): Double {
    var total = 0
    var documented = 0
    analysis(codebase).files.forEach { file ->
      total++
      if (file.source.trimStart().startsWith("/**")) documented++
      (file.nodes("ClassDeclaration") + file.nodes("FunctionDeclaration"))
        .filterNot { hasNonPublicModifier(it.text) }
        .forEach {
          total++
          if (hasKdoc(file.source, it.start)) documented++
        }
    }
    return if (total == 0) 0.0 else documented * 100.0 / total
  }

  override fun normalize(value: Double?): Double = ((value ?: 0.0) / 100.0).coerceIn(0.0, 1.0)
}

internal class KotlinSecurityMetric : KotlinDoubleMetric("security", 0.7) {
  override fun extract(codebase: Path): Double {
    val data = analysis(codebase)
    val findings =
      data.files.sumOf { file ->
        SECURITY_PATTERNS.count { pattern -> pattern.containsMatchIn(file.source) }
      }
    return if (data.sourceLines == 0) 0.0 else findings * 8.0 / data.sourceLines
  }

  override fun normalize(value: Double?): Double = exp(-((value ?: 0.0) / 0.03))
}

internal class KotlinCodeSmellDensity : KotlinDoubleMetric("Code Smell Density", 0.5) {
  override fun extract(codebase: Path): Double {
    val data = analysis(codebase)
    val smells =
      data.files.sumOf { file ->
        val functions = file.nodes("FunctionDeclaration")
        functions.count { lineCount(it.text) > 50 } +
          functions.count { kotlinFunctionComplexity(file, it) > 10 } +
          functions.count { parameterCount(it.text) > 5 } +
          file.nodes("ClassDeclaration").count { lineCount(it.text) > 500 }
      }
    return if (data.sourceLines == 0) 0.0 else smells * 1000.0 / data.sourceLines
  }

  override fun normalize(value: Double?): Double {
    val v = value ?: return 1.0
    return when {
      v <= 0.0 -> 1.0
      v >= 50.0 -> 0.0
      v <= 5.0 -> 1.0 - v / 5.0 * 0.2
      v <= 10.0 -> 0.8 - (v - 5.0) / 5.0 * 0.2
      v <= 20.0 -> 0.6 - (v - 10.0) / 10.0 * 0.3
      else -> max(0.0, 0.3 - (v - 20.0) / 30.0 * 0.3)
    }
  }
}

internal class KotlinRFCMetric : KotlinDoubleMetric("rfc", 0.65) {
  override fun extract(codebase: Path): Double {
    val values =
      analysis(codebase).files.flatMap { file ->
        file.nodes("ClassDeclaration").map { cls ->
          val methods = file.within(cls, "FunctionDeclaration").size
          val calls = file.within(cls, "CallSuffix").map { it.text }.toSet().size
          (methods + calls).toDouble()
        }
      }
    return values.averageOrZero()
  }

  override fun normalize(value: Double?): Double {
    val v = value ?: return 1.0
    return when {
      v <= 15.0 -> 1.0
      v <= 50.0 -> 1.0 - 0.25 * (v - 15.0) / 35.0
      v <= 100.0 -> 0.75 - 0.40 * (v - 50.0) / 50.0
      v <= 120.0 -> 0.35 - 0.35 * (v - 100.0) / 20.0
      else -> 0.0
    }
  }
}

internal class KotlinDITMetric : KotlinDoubleMetric("dit", 0.6) {
  override fun extract(codebase: Path): Double {
    val parents = mutableMapOf<String, String?>()
    analysis(codebase)
      .files
      .flatMap { it.nodes("ClassDeclaration") }
      .forEach { cls ->
        val match = CLASS_HEADER.find(cls.text) ?: return@forEach
        parents[match.groupValues[1]] =
          match.groupValues
            .getOrNull(2)
            ?.substringBefore('(')
            ?.substringBefore(',')
            ?.trim()
            ?.takeIf(String::isNotEmpty)
      }
    fun depth(name: String, seen: Set<String> = emptySet()): Int {
      if (name in seen) return 0
      val parent = parents[name] ?: return 0
      return 1 + if (parent in parents) depth(parent, seen + name) else 0
    }
    return parents.keys.maxOfOrNull(::depth)?.toDouble() ?: 0.0
  }

  override fun normalize(value: Double?): Double {
    val v = value ?: return 1.0
    return when {
      v <= 3.0 -> 1.0
      v <= 6.0 -> 1.0 - 0.30 * (v - 3.0) / 3.0
      v <= 10.0 -> 0.70 - 0.30 * (v - 6.0) / 4.0
      else -> max(0.0, 0.40 - 0.40 * (v - 10.0) / 5.0)
    }
  }
}

internal class KotlinMHFMetric : KotlinDoubleMetric("mhf", 0.55) {
  override fun extract(codebase: Path): Double {
    val methods = analysis(codebase).files.flatMap { it.nodes("FunctionDeclaration") }
    return if (methods.isEmpty()) 0.0
    else
      methods
        .count {
          PRIVATE_MODIFIER.containsMatchIn(it.text.substringBefore('{').substringBefore('='))
        }
        .toDouble() / methods.size
  }

  override fun normalize(value: Double?): Double = (value ?: 0.0).coerceIn(0.0, 1.0)
}

internal class KotlinCouplingBetweenObjects : KotlinDoubleMetric("Coupling Between Objects", 0.65) {
  override fun extract(codebase: Path): Double {
    val values =
      analysis(codebase).files.flatMap { file ->
        file.nodes("ClassDeclaration").map { cls ->
          val self = CLASS_HEADER.find(cls.text)?.groupValues?.get(1)
          file
            .within(cls, "SimpleUserType")
            .map { it.text.substringBefore('<').trim().substringAfterLast('.') }
            .filter { it.length > 1 && it != self && it !in BUILTIN_TYPES }
            .toSet()
            .size
            .toDouble()
        }
      }
    return values.averageOrZero()
  }

  override fun normalize(value: Double?): Double {
    val v = value ?: return 1.0
    return when {
      v <= 0.0 -> 1.0
      v <= 9.0 -> 1.0 - v / 9.0 * 0.3
      v <= 20.0 -> 0.7 - (v - 9.0) / 11.0 * 0.3
      else -> max(0.0, 0.4 - (v - 20.0) / 20.0 * 0.4)
    }
  }
}

internal class KotlinLackOfCohesionOfMethods :
  KotlinDoubleMetric("Lack of Cohesion of Methods", 0.5) {
  override fun extract(codebase: Path): Double {
    val values =
      analysis(codebase).files.flatMap { file ->
        file.nodes("ClassDeclaration").map { cls -> lcom(file, cls) }
      }
    return values.averageOrZero()
  }

  override fun normalize(value: Double?): Double {
    val v = value ?: return 1.0
    return when {
      v <= 1.0 -> 1.0
      v <= 3.0 -> 1.0 - (v - 1.0) / 2.0 * 0.3
      v <= 6.0 -> 0.7 - (v - 3.0) / 3.0 * 0.3
      else -> max(0.0, 0.4 - (v - 6.0) / 6.0 * 0.4)
    }
  }
}

internal fun kotlinFunctionComplexity(
  file: KotlinFileAnalysis,
  function: KotlinNodeFact,
): Int = 1 + decisionNodes(file, function).size

private fun decisionNodes(
  file: KotlinFileAnalysis,
  function: KotlinNodeFact,
): List<KotlinNodeFact> =
  file.within(function, "").filter(::isDecision).distinctBy { Triple(it.type, it.start, it.end) }

private fun totalComplexity(file: KotlinFileAnalysis): Int =
  file.nodes("FunctionDeclaration").sumOf { kotlinFunctionComplexity(file, it) }

private fun isDecision(node: KotlinNodeFact): Boolean =
  when (node.type) {
    "IfExpression",
    "WhenEntry",
    "ForStatement",
    "WhileStatement",
    "DoWhileStatement",
    "CatchBlock" -> true
    "Elvis" -> node.text == "?:"
    else -> node.type.startsWith("T-") && (node.text == "&&" || node.text == "||")
  }

private fun halsteadVolume(file: KotlinFileAnalysis): Double {
  val operators =
    file.nodes.filter { it.type.startsWith("T-") && it.text in OPERATOR_TEXT }.map { it.text }
  val operands =
    file.nodes
      .filter {
        it.type.startsWith("T-") &&
          (it.type.contains("Identifier") ||
            it.type.contains("Literal") ||
            it.type.contains("StrText"))
      }
      .map { it.text }
      .filter { it.isNotBlank() }
  val length = operators.size + operands.size
  val vocabulary = operators.toSet().size + operands.toSet().size
  return if (length == 0 || vocabulary <= 1) 0.0 else length * log2(vocabulary.toDouble())
}

private fun mi(volume: Double, complexity: Int, sloc: Int, commentsPct: Double): Double {
  if (volume <= 0.0 || sloc <= 0) return 100.0
  val raw =
    171.0 - 5.2 * ln(volume) - 0.23 * complexity - 16.2 * ln(sloc.toDouble()) +
      50.0 * sin(sqrt(2.46 * Math.toRadians(commentsPct)))
  return (raw * 100.0 / 171.0).coerceIn(0.0, 100.0)
}

private fun sourceLines(source: String): Int =
  source.lineSequence().count {
    val line = it.trim()
    line.isNotEmpty() && !line.startsWith("//")
  }

private fun commentLines(source: String): Int =
  source.lineSequence().count {
    val line = it.trim()
    line.startsWith("//") || line.startsWith("/*") || line.startsWith("*")
  }

private fun hasKdoc(source: String, offset: Int): Boolean =
  KDOC_BEFORE_DECLARATION.containsMatchIn(source.substring(0, offset))

private fun hasNonPublicModifier(text: String): Boolean =
  NON_PUBLIC_MODIFIER.containsMatchIn(text.substringBefore('{').substringBefore('='))

private fun parameterCount(text: String): Int {
  val params = text.substringAfter('(', "").substringBefore(')', "")
  if (params.isBlank()) return 0
  return params.count { it == ',' } + 1
}

private fun lineCount(text: String): Int = text.lineSequence().count()

private fun lcom(file: KotlinFileAnalysis, cls: KotlinNodeFact): Double {
  val properties =
    file
      .within(cls, "PropertyDeclaration")
      .mapNotNull { PROPERTY_NAME.find(it.text)?.groupValues?.get(1) }
      .toSet()
  val methods = file.within(cls, "FunctionDeclaration")
  if (methods.size <= 1) return 0.0
  val touched =
    methods.associateWith { method ->
      properties
        .filter { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(method.text) }
        .toSet()
    }
  val unseen = methods.toMutableSet()
  var components = 0
  while (unseen.isNotEmpty()) {
    components++
    val queue = ArrayDeque<KotlinNodeFact>()
    queue += unseen.first()
    while (queue.isNotEmpty()) {
      val current = queue.removeFirst()
      if (!unseen.remove(current)) continue
      unseen
        .filter { other ->
          touched.getValue(current).intersect(touched.getValue(other)).isNotEmpty()
        }
        .forEach(queue::add)
    }
  }
  return components.toDouble()
}

private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()

private val CLASS_HEADER =
  Regex("""\b(?:class|interface)\s+([A-Za-z_]\w*)(?:[^{:]*:\s*([A-Za-z_][\w.<>, ]*))?""")
private val PROPERTY_NAME = Regex("""\b(?:val|var)\s+([A-Za-z_]\w*)""")
private val PRIVATE_MODIFIER = Regex("""\bprivate\b""")
private val NON_PUBLIC_MODIFIER = Regex("""\b(?:private|protected|internal)\b""")
private val KDOC_BEFORE_DECLARATION = Regex("""(?s)/\*\*(?:(?!\*/).)*\*/\s*$""")
private val SECURITY_PATTERNS =
  listOf(
    Regex("""Runtime\s*\.\s*getRuntime\s*\(\s*\)\s*\.\s*exec\s*\("""),
    Regex("""ProcessBuilder\s*\("""),
    Regex("""createStatement\s*\(\s*\)\s*\.\s*execute(?:Query|Update)?\s*\("""),
    Regex("""MessageDigest\s*\.\s*getInstance\s*\(\s*["'](?:MD5|SHA-?1)["']"""),
    Regex("""TrustAll|ALLOW_ALL_HOSTNAME_VERIFIER""", RegexOption.IGNORE_CASE),
  )
private val BUILTIN_TYPES =
  setOf(
    "Any",
    "Boolean",
    "Byte",
    "Char",
    "Double",
    "Float",
    "Int",
    "Long",
    "Nothing",
    "Short",
    "String",
    "Unit",
    "List",
    "Map",
    "Set",
    "MutableList",
    "MutableMap",
    "MutableSet",
  )
private val OPERATOR_TEXT =
  setOf(
    "+",
    "-",
    "*",
    "/",
    "%",
    "=",
    "+=",
    "-=",
    "*=",
    "/=",
    "%=",
    "==",
    "===",
    "!=",
    "!==",
    "<",
    ">",
    "<=",
    ">=",
    "&&",
    "||",
    "?:",
    "++",
    "--",
    "in",
    "!in",
    "is",
    "!is",
  )
