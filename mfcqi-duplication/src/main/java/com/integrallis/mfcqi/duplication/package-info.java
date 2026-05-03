/**
 * Code-duplication detection. Port of the Python reference's hash-based block detector: lines are
 * normalized (whitespace stripped, comments removed), then 3/4/5-line windows are hashed and
 * matched across files.
 */
package com.integrallis.mfcqi.duplication;
