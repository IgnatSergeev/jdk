/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
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
 * or visit https://www.oracle.com if you need any additional information or
 * have any questions.
 */

/**
 * @test
 * @summary Verify that branch speculation with specialized profiles produces
 *          correct results, including when deoptimization occurs. An inlined
 *          method has an `if` that is always taken one way at one call site.
 *          With specialized profiles, C2 may place an uncommon trap on the
 *          never-taken arm. When the other arm is later taken (from a different
 *          call or after recompilation), the program must still produce correct
 *          results via deoptimization and re-execution in the interpreter.
 *
 * @requires vm.flavor == "server" & vm.compMode == "Xmixed" & !vm.emulatedClient
 * @requires vm.compiler2.enabled
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+SpecializedMethodData
 *                   -XX:-BackgroundCompilation -XX:-UseOnStackReplacement
 *                   compiler.profiling.specialized.TestSpecializedProfilesBranchCorrectness
 */

package compiler.profiling.specialized;

public class TestSpecializedProfilesBranchCorrectness {

    static int inlineeWithBranch(int value) {
        if (value > 0) {
            return value * 2;
        }
        return -value;
    }

    static int inlineeWithNullCheck(Object o) {
        if (o != null) {
            return o.hashCode();
        }
        return -1;
    }

    static int inlineeWithSwitch(int mode) {
        switch (mode) {
            case 0:  return 10;
            case 1:  return 20;
            case 2:  return 30;
            default: return -1;
        }
    }

    static void testBranchAlwaysTrueThenFalse() {
        int sum = 0;
        for (int i = 0; i < 20_000; i++) {
            sum += inlineeWithBranch(1);
        }
        if (sum != 2 * 20_000) {
            throw new RuntimeException("warmup: expected " + (2 * 20_000) + " got " + sum);
        }

        int negativeResult = inlineeWithBranch(-5);
        if (negativeResult != 5) {
            throw new RuntimeException("negative: expected 5 got " + negativeResult);
        }

        int zeroResult = inlineeWithBranch(0);
        if (zeroResult != 0) {
            throw new RuntimeException("zero: expected 0 got " + zeroResult);
        }
    }

    static void testBranchAlwaysFalseThenTrue() {
        int sum = 0;
        for (int i = 0; i < 20_000; i++) {
            sum += inlineeWithBranch(-1);
        }
        if (sum != 1 * 20_000) {
            throw new RuntimeException("warmup: expected " + 20_000 + " got " + sum);
        }

        int positiveResult = inlineeWithBranch(7);
        if (positiveResult != 14) {
            throw new RuntimeException("positive: expected 14 got " + positiveResult);
        }
    }

    static void testBranchDifferentContexts() {
        int sumPositive = 0;
        int sumNegative = 0;
        for (int i = 0; i < 20_000; i++) {
            sumPositive += inlineeWithBranch(1);
            sumNegative += inlineeWithBranch(-1);
        }
        if (sumPositive != 2 * 20_000) {
            throw new RuntimeException("positive context: expected " + (2 * 20_000) + " got " + sumPositive);
        }
        if (sumNegative != 1 * 20_000) {
            throw new RuntimeException("negative context: expected " + 20_000 + " got " + sumNegative);
        }

        int crossPositive = inlineeWithBranch(10);
        if (crossPositive != 20) {
            throw new RuntimeException("cross positive: expected 20 got " + crossPositive);
        }

        int crossNegative = inlineeWithBranch(-10);
        if (crossNegative != 10) {
            throw new RuntimeException("cross negative: expected 10 got " + crossNegative);
        }
    }

    static void testNullCheckAlwaysNonNullThenNull() {
        Object obj = new Object();
        int sum = 0;
        for (int i = 0; i < 20_000; i++) {
            sum += inlineeWithNullCheck(obj);
        }

        int nullResult = inlineeWithNullCheck(null);
        if (nullResult != -1) {
            throw new RuntimeException("null: expected -1 got " + nullResult);
        }

        int nonNullResult = inlineeWithNullCheck(obj);
        if (nonNullResult == -1) {
            throw new RuntimeException("non-null after null: got -1");
        }
    }

    static void testSwitchAlwaysOneCaseThenOther() {
        int sum = 0;
        for (int i = 0; i < 20_000; i++) {
            sum += inlineeWithSwitch(0);
        }
        if (sum != 10 * 20_000) {
            throw new RuntimeException("switch warmup: expected " + (10 * 20_000) + " got " + sum);
        }

        int case1 = inlineeWithSwitch(1);
        if (case1 != 20) {
            throw new RuntimeException("switch case 1: expected 20 got " + case1);
        }

        int case2 = inlineeWithSwitch(2);
        if (case2 != 30) {
            throw new RuntimeException("switch case 2: expected 30 got " + case2);
        }

        int defaultCase = inlineeWithSwitch(99);
        if (defaultCase != -1) {
            throw new RuntimeException("switch default: expected -1 got " + defaultCase);
        }
    }

    static void testSwitchDifferentContexts() {
        int sum0 = 0, sum1 = 0;
        for (int i = 0; i < 20_000; i++) {
            sum0 += inlineeWithSwitch(0);
            sum1 += inlineeWithSwitch(1);
        }
        if (sum0 != 10 * 20_000) {
            throw new RuntimeException("switch ctx 0: expected " + (10 * 20_000) + " got " + sum0);
        }
        if (sum1 != 20 * 20_000) {
            throw new RuntimeException("switch ctx 1: expected " + (20 * 20_000) + " got " + sum1);
        }

        int case2 = inlineeWithSwitch(2);
        if (case2 != 30) {
            throw new RuntimeException("switch cross case 2: expected 30 got " + case2);
        }
    }

    public static void main(String[] args) {
        testBranchAlwaysTrueThenFalse();
        testBranchAlwaysFalseThenTrue();
        testBranchDifferentContexts();
        testNullCheckAlwaysNonNullThenNull();
        testSwitchAlwaysOneCaseThenOther();
        testSwitchDifferentContexts();
        System.out.println("All branch correctness tests passed.");
    }
}
