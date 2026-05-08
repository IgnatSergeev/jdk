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
 * Benchmarks for specialized method data (profiles) in branch profile
 * scenarios.
 *
 * <p>The inlinee is a thin method with a branch on the input value.
 * The hot loop is in the benchmark method, not the inlinee, to prevent
 * OSR compilation of the inlinee as a standalone method. This ensures C1
 * inlines the inlinee and creates specialized MDOs for each call site.
 *
 * <p>Each arm performs different computation:
 * <ul>
 *   <li>if-arm: simple return value</li>
 *   <li>else-arm: more expensive computation (multiply + add)</li>
 * </ul>
 *
 * <p>When the branch always takes one arm at a given call site, specialized
 * profiles enable C2's {@code unstable_if} to replace the dead arm with an
 * uncommon trap. The surviving arm's code becomes the only path, enabling
 * simpler generated code and better optimization.
 *
 * <p>Without specialized profiles, the shared MDO records both arms as
 * reachable when the inlinee is called from multiple sites with different
 * data. C2 must keep both arms.
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
public class SpecializedProfilesBranch {

    static final int SIZE = 1024;

    static int branchInlinee(int v) {
        if (v > 0) {
            return v;
        } else {
            return v * 31 + 7;
        }
    }

    static int branchInlineeTwoBranches(int v) {
        if (v > 0) {
            return v;
        } else if (v < -50) {
            return v * 17 - 3;
        } else {
            return v * 31 + 7;
        }
    }

    int[] posData;
    int[] negData;

    @Setup
    public void setup() {
        posData = new int[SIZE];
        negData = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            posData[i] = i + 1;
            negData[i] = -(i + 1);
        }
    }

    @Benchmark
    public int positiveOnly() {
        int sum = 0;
        for (int i = 0; i < posData.length; i++) {
            sum += branchInlinee(posData[i]);
        }
        return sum;
    }

    @Benchmark
    public int negativeOnly() {
        int sum = 0;
        for (int i = 0; i < negData.length; i++) {
            sum += branchInlinee(negData[i]);
        }
        return sum;
    }

    @Benchmark
    public int positiveAndNegativeTwoSites() {
        int sum = 0;
        for (int i = 0; i < posData.length; i++) {
            sum += branchInlinee(posData[i]);
            sum += branchInlinee(negData[i]);
        }
        return sum;
    }

    @Benchmark
    public int positiveAndNegativeThreeSites() {
        int sum = 0;
        for (int i = 0; i < posData.length; i++) {
            sum += branchInlinee(posData[i]);
            sum += branchInlinee(posData[i]);
            sum += branchInlinee(negData[i]);
        }
        return sum;
    }

    @Benchmark
    public int positiveOnlyTwoBranches() {
        int sum = 0;
        for (int i = 0; i < posData.length; i++) {
            sum += branchInlineeTwoBranches(posData[i]);
        }
        return sum;
    }

    @Benchmark
    public int negativeOnlyTwoBranches() {
        int sum = 0;
        for (int i = 0; i < negData.length; i++) {
            sum += branchInlineeTwoBranches(negData[i]);
        }
        return sum;
    }

    @Benchmark
    public int mixedTwoBranchesTwoSites() {
        int sum = 0;
        for (int i = 0; i < posData.length; i++) {
            sum += branchInlineeTwoBranches(posData[i]);
            sum += branchInlineeTwoBranches(negData[i]);
        }
        return sum;
    }

    @Benchmark
    public int mixedTwoBranchesThreeSites() {
        int sum = 0;
        for (int i = 0; i < posData.length; i++) {
            sum += branchInlineeTwoBranches(posData[i]);
            sum += branchInlineeTwoBranches(posData[i]);
            sum += branchInlineeTwoBranches(negData[i]);
        }
        return sum;
    }
}
