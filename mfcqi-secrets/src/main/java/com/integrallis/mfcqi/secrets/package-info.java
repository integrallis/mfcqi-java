/**
 * Secret detection — Java port of Python {@code detect-secrets}. Combines a curated regex catalog
 * (AWS keys, GitHub tokens, private-key headers, JWTs, etc.) with a Shannon-entropy filter for
 * generic high-entropy strings. No external service required.
 */
package com.integrallis.mfcqi.secrets;
