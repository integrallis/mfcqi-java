package com.integrallis.mfcqi.kotlin.internal

import kotlinx.ast.common.ast.Ast
import kotlinx.ast.common.ast.AstNode

/** Small helpers for traversing the kotlinx-ast tree, isolating its node API in one place. */
internal object KotlinAst {

  /** Depth-first pre-order visit of [node] and all descendants. */
  fun walk(node: Ast, visit: (Ast) -> Unit) {
    visit(node)
    for (child in children(node)) {
      walk(child, visit)
    }
  }

  /** The direct children of [node], or an empty list if it is a leaf. */
  fun children(node: Ast): List<Ast> = (node as? AstNode)?.children ?: emptyList()
}
