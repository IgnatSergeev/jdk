/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need any additional information or
 * have any questions.
 */
package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Benchmarks for specialized method data (profiles) in switch profile
 * scenarios.
 *
 * <p>The inlinee is a thin method with a switch on the input value.
 * The hot loop is in the benchmark method. Data is constructed so each
 * element always hits the same switch case.
 *
 * <p>When the switch always hits one case at a given call site, specialized
 * profiles enable C2 to trim the dead cases via {@code unstable_if}. The
 * surviving case becomes the only code path, enabling simpler generated code.
 *
 * <p>Without specialized profiles, the shared MDO records all cases as
 * reachable when the inlinee is called from multiple sites with different
 * data. C2 must generate code for all switch cases.
 *
 * <p>To compare performance with and without specialized profiles, run with:
 * <pre>
 *   -XX:+UnlockDiagnosticVMOptions -XX:+SpecializedMethodData   (with)
 *   -XX:+UnlockDiagnosticVMOptions -XX:-SpecializedMethodData   (without)
 * </pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
public class SpecializedProfilesSwitch {

    static final int SIZE = 1024;

    static int switchInlinee(int v) {
        switch (v & 3) {
            case 0: return v;
            case 1: return v * 31 + 7;
            case 2: return v * v + 1;
            default: return -v;
        }
    }

    static int switchInlineeFiveCases(int v) {
        switch (v & 7) {
            case 0: return v;
            case 1: return v * 31 + 7;
            case 2: return v * v + 1;
            case 3: return v * 17 - 3;
            case 4: return v ^ 0x5555AAAA;
            default: return -v;
        }
    }

    int[] case0Data;
    int[] case1Data;
    int[] case2Data;
    int[] case4Data;

    @Setup
    public void setup() {
        case0Data = new int[SIZE];
        case1Data = new int[SIZE];
        case2Data = new int[SIZE];
        case4Data = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            case0Data[i] = (i + 1) * 4;
            case1Data[i] = (i + 1) * 4 + 1;
            case2Data[i] = (i + 1) * 4 + 2;
            case4Data[i] = (i + 1) * 8 + 4;
        }
    }

    @Benchmark
    public int case0Only() {
        int sum = 0;
        for (int i = 0; i < case0Data.length; i++) {
            sum += switchInlinee(case0Data[i]);
        }
        return sum;
    }

    @Benchmark
    public int case0AndCase1TwoSites() {
        int sum = 0;
        for (int i = 0; i < case0Data.length; i++) {
            sum += switchInlinee(case0Data[i]);
            sum += switchInlinee(case1Data[i]);
        }
        return sum;
    }

    @Benchmark
    public int allThreeCasesThreeSites() {
        int sum = 0;
        for (int i = 0; i < case0Data.length; i++) {
            sum += switchInlinee(case0Data[i]);
            sum += switchInlinee(case1Data[i]);
            sum += switchInlinee(case2Data[i]);
        }
        return sum;
    }

    @Benchmark
    public int case4OnlyFiveCases() {
        int sum = 0;
        for (int i = 0; i < case4Data.length; i++) {
            sum += switchInlineeFiveCases(case4Data[i]);
        }
        return sum;
    }

    @Benchmark
    public int case0AndCase4TwoSitesFiveCases() {
        int sum = 0;
        for (int i = 0; i < case0Data.length; i++) {
            sum += switchInlineeFiveCases(case0Data[i]);
            sum += switchInlineeFiveCases(case4Data[i]);
        }
        return sum;
    }

    @Benchmark
    public int case0Case1Case4ThreeSitesFiveCases() {
        int sum = 0;
        for (int i = 0; i < case0Data.length; i++) {
            sum += switchInlineeFiveCases(case0Data[i]);
            sum += switchInlineeFiveCases(case1Data[i]);
            sum += switchInlineeFiveCases(case4Data[i]);
        }
        return sum;
    }
}
