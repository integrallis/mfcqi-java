/**
 * Code-smell detection: production smells (god classes, long methods, feature envy, etc. — ported
 * from PyExamine) and test smells (assertion roulette, weak assertions, missing setup — ported from
 * the Python AST test smell detector). Both are adapted from Python AST patterns to JavaParser AST
 * patterns.
 *
 * <p>The aggregator de-duplicates by {@code (smellId, location)} and keeps the highest severity
 * when conflicts occur.
 */
package com.integrallis.mfcqi.smells;
