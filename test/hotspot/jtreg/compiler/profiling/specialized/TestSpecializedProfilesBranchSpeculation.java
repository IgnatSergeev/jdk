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
 * @summary Specialized profiles isolate branch profile data (BranchData in MDO) per
 *          call site. When an inlined method has an `if` that always takes one arm at
 *          one call site but sometimes takes the other at another call site, specialized
 *          profiles record per-site branch frequencies. This enables C2 to make
 *          per-context branch speculations (unstable_if, unreached uncommon traps)
 *          that would be impossible with a shared MDO showing both arms as reachable.
 *          This test verifies correctness of those speculations and deoptimization
 *          when a speculated-dead branch is taken.
 *
 * @requires vm.flavor == "server" & vm.compMode == "Xmixed" & !vm.emulatedClient
 * @requires vm.compiler2.enabled
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+SpecializedMethodData
 *                   -XX:-BackgroundCompilation -XX:-UseOnStackReplacement
 *                   compiler.profiling.specialized.TestSpecializedProfilesBranchSpeculation
 */

package compiler.profiling.specialized;

public class TestSpecializedProfilesBranchSpeculation {

    static int inlinee(int value) {
        if (value > 0) {
            return value * 2;
        }
        return -value;
    }

    static int inlineeWithNullCheck(Object o, int flag) {
        if (o != null) {
            if (flag > 0) {
                return o.hashCode();
            }
            return 0;
        }
        return -1;
    }

    static void testBranchDifferentContexts() {
        int sumPositive = 0;
        int sumNegative = 0;
        for (int i = 0; i < 20_000; i++) {
            sumPositive += inlinee(1);
            sumNegative += inlinee(-1);
        }
        if (sumPositive != 2 * 20_000) {
            throw new RuntimeException("positive context: expected " + (2 * 20_000) + " got " + sumPositive);
        }
        if (sumNegative != 1 * 20_000) {
            throw new RuntimeException("negative context: expected " + 20_000 + " got " + sumNegative);
        }

        int crossPositive = inlinee(10);
        if (crossPositive != 20) {
            throw new RuntimeException("cross positive: expected 20 got " + crossPositive);
        }

        int crossNegative = inlinee(-10);
        if (crossNegative != 10) {
            throw new RuntimeException("cross negative: expected 10 got " + crossNegative);
        }

        int crossZero = inlinee(0);
        if (crossZero != 0) {
            throw new RuntimeException("cross zero: expected 0 got " + crossZero);
        }
    }

    static void testBranchAlwaysOneWayThenOther() {
        int sum = 0;
        for (int i = 0; i < 20_000; i++) {
            sum += inlinee(1);
        }
        if (sum != 2 * 20_000) {
            throw new RuntimeException("positive warmup: expected " + (2 * 20_000) + " got " + sum);
        }

        int negativeResult = inlinee(-5);
        if (negativeResult != 5) {
            throw new RuntimeException("negative after warmup: expected 5 got " + negativeResult);
        }
    }

    static void testNullCheckBranchCombined() {
        Object obj = new Object();
        int sum = 0;
        for (int i = 0; i < 20_000; i++) {
            sum += inlineeWithNullCheck(obj, 1);
        }

        int nullResult = inlineeWithNullCheck(null, 1);
        if (nullResult != -1) {
            throw new RuntimeException("null: expected -1 got " + nullResult);
        }

        int negativeFlag = inlineeWithNullCheck(obj, -1);
        if (negativeFlag != 0) {
            throw new RuntimeException("negative flag: expected 0 got " + negativeFlag);
        }

        int nullWithNegativeFlag = inlineeWithNullCheck(null, -1);
        if (nullWithNegativeFlag != -1) {
            throw new RuntimeException("null with negative flag: expected -1 got " + nullWithNegativeFlag);
        }
    }

    static void testBranchWithSameReceiverDifferentBranches() {
        for (int i = 0; i < 20_000; i++) {
            inlinee(i % 2 == 0 ? 1 : -1);
        }

        int posResult = inlinee(5);
        if (posResult != 10) {
            throw new RuntimeException("mixed->positive: expected 10 got " + posResult);
        }

        int negResult = inlinee(-3);
        if (negResult != 3) {
            throw new RuntimeException("mixed->negative: expected 3 got " + negResult);
        }

        int zeroResult = inlinee(0);
        if (zeroResult != 0) {
            throw new RuntimeException("mixed->zero: expected 0 got " + zeroResult);
        }
    }

    public static void main(String[] args) {
        testBranchDifferentContexts();
        testBranchAlwaysOneWayThenOther();
        testNullCheckBranchCombined();
        testBranchWithSameReceiverDifferentBranches();
        System.out.println("All branch speculation tests passed.");
    }
}
