package com.integrallis.mfcqi.kotlin.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KotlinParserTest {

  @Test
  fun parsesASimpleFunctionIntoANonEmptyTree() {
    val ast = KotlinParser.parse("fun answer() = 42")
    assertThat(ast).isNotNull
    assertThat(ast.description).isNotBlank()
  }

  @Test
  fun parsesIdiomaticKotlinWithoutThrowing() {
    val code =
      """
        package demo
        data class P(val a: Int, var b: String = "x")
        fun P.tag(): String = if (a > 0) "pos" else "neg"
        """
        .trimIndent()
    val ast = KotlinParser.parse(code)
    assertThat(ast).isNotNull
  }

  @Test
  fun collectsExpectedRuleNodes() {
    val ast = KotlinParser.parse("fun f(x: Int) = if (x > 0 && x < 10) 1 else 2")
    val rules = mutableSetOf<String>()
    KotlinAst.walk(ast) { rules.add(it.description) }
    assertThat(rules).contains("ifExpression", "conjunction")
  }
}
