/**
 * Security SAST metric. Wraps SpotBugs core with the FindSecBugs plugin to surface security
 * findings, then aggregates them into a CVSS-weighted vulnerability density (CVSS points per line
 * of code) and exponentially decays the result into {@code [0,1]} — same shape as the Python
 * reference's Bandit-backed implementation.
 */
package com.integrallis.mfcqi.security;
