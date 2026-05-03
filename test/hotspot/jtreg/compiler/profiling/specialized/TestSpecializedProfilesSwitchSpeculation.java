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
 * @summary Specialized profiles isolate switch statement profile data
 *          (MultiBranchData in MDO) per call site. When an inlined method
 *          contains a switch statement, specialized MDOs record per-site
 *          case frequencies. This enables C2 to trim dead switch arms
 *          per call site. This test verifies correctness of switch
 *          speculation and deoptimization with specialized profiles.
 *
 * @requires vm.flavor == "server" & vm.compMode == "Xmixed" & !vm.emulatedClient
 * @requires vm.compiler2.enabled
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+SpecializedMethodData
 *                   -XX:-BackgroundCompilation -XX:-UseOnStackReplacement
 *                   compiler.profiling.specialized.TestSpecializedProfilesSwitchSpeculation
 */

package compiler.profiling.specialized;

public class TestSpecializedProfilesSwitchSpeculation {

    static int inlinee(int mode) {
        switch (mode) {
            case 0:  return 10;
            case 1:  return 20;
            case 2:  return 30;
            default: return -1;
        }
    }

    static void testSwitchAlwaysOneCaseThenOther() {
        int sum = 0;
        for (int i = 0; i < 20_000; i++) {
            sum += inlinee(0);
        }
        if (sum != 10 * 20_000) {
            throw new RuntimeException("switch warmup: expected " + (10 * 20_000) + " got " + sum);
        }

        int case1 = inlinee(1);
        if (case1 != 20) {
            throw new RuntimeException("switch case 1: expected 20 got " + case1);
        }

        int case2 = inlinee(2);
        if (case2 != 30) {
            throw new RuntimeException("switch case 2: expected 30 got " + case2);
        }

        int defaultCase = inlinee(99);
        if (defaultCase != -1) {
            throw new RuntimeException("switch default: expected -1 got " + defaultCase);
        }
    }

    static void testSwitchDifferentContexts() {
        int sum0 = 0, sum1 = 0;
        for (int i = 0; i < 20_000; i++) {
            sum0 += inlinee(0);
            sum1 += inlinee(1);
        }
        if (sum0 != 10 * 20_000) {
            throw new RuntimeException("switch ctx 0: expected " + (10 * 20_000) + " got " + sum0);
        }
        if (sum1 != 20 * 20_000) {
            throw new RuntimeException("switch ctx 1: expected " + (20 * 20_000) + " got " + sum1);
        }

        int case2 = inlinee(2);
        if (case2 != 30) {
            throw new RuntimeException("switch cross case 2: expected 30 got " + case2);
        }

        int defaultCase = inlinee(-1);
        if (defaultCase != -1) {
            throw new RuntimeException("switch cross default: expected -1 got " + defaultCase);
        }
    }

    static void testSwitchMixedThenSpecific() {
        int sum = 0;
        int cnt0 = 0, cnt1 = 0, cnt2 = 0;
        for (int i = 0; i < 20_000; i++) {
            int m = i % 3;
            if (m == 0) cnt0++;
            else if (m == 1) cnt1++;
            else cnt2++;
            sum += inlinee(m);
        }
        int expected = 10 * cnt0 + 20 * cnt1 + 30 * cnt2;
        if (sum != expected) {
            throw new RuntimeException("switch mixed: expected " + expected + " got " + sum);
        }

        int specificCase = inlinee(0);
        if (specificCase != 10) {
            throw new RuntimeException("switch specific after mixed: expected 10 got " + specificCase);
        }
    }

    public static void main(String[] args) {
        testSwitchAlwaysOneCaseThenOther();
        testSwitchDifferentContexts();
        testSwitchMixedThenSpecific();
        System.out.println("All switch speculation tests passed.");
    }
}
