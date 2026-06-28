package com.integrallis.mfcqi.kotlin.internal

import kotlinx.ast.common.ast.Ast

/** Cyclomatic-complexity counting over the kotlinx-ast tree. */
internal object KotlinComplexity {

  // Control-flow nodes whose presence adds an independent path.
  private val PRESENCE_RULES =
    setOf(
      "ifExpression",
      "whenEntry",
      "forStatement",
      "whileStatement",
      "doWhileStatement",
      "catchBlock"
    )

  // Operator-precedence nodes that ANTLR emits for EVERY expression in the cascade; they only add a
  // path when they actually apply the operator (i.e. have more than one child).
  private val OPERATOR_RULES = setOf("conjunction", "disjunction", "elvisExpression")

  /** Cyclomatic complexity (1 + decision points) for every function in the parsed file. */
  fun perFunction(fileAst: Ast): List<Int> {
    val result = mutableListOf<Int>()
    KotlinAst.walk(fileAst) { node ->
      if (node.description == "functionDeclaration") {
        result.add(1 + decisionsIn(node))
      }
    }
    return result
  }

  private fun decisionsIn(node: Ast): Int {
    var count = 0
    KotlinAst.walk(node) { n ->
      when {
        n.description in PRESENCE_RULES -> count++
        n.description in OPERATOR_RULES && KotlinAst.children(n).size > 1 -> count++
      }
    }
    return count
  }
}
