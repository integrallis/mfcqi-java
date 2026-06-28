package com.integrallis.mfcqi.kotlin.internal

import kotlinx.ast.common.AstSource
import kotlinx.ast.common.ast.Ast
import kotlinx.ast.grammar.kotlin.target.antlr.kotlin.KotlinGrammarAntlrKotlinParser

/**
 * The single seam over the Kotlin parser. Everything else works against the returned [Ast] tree, so
 * swapping the backend (e.g. to a Kotlin-compiler PSI frontend for JVM-only high fidelity) is a
 * change confined to this object and [KotlinAst].
 */
internal object KotlinParser {

  /** Parse Kotlin [code] into an AST. [description] is a label used in parser diagnostics. */
  fun parse(code: String, description: String = "source"): Ast =
    KotlinGrammarAntlrKotlinParser.parseKotlinFile(AstSource.String(description, code))
}
