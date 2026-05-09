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
 * @summary Verify that programs produce correct results when specialized
 *          profiles (-XX:+SpecializedMethodData) are enabled. Tests various
 *          inlining and virtual dispatch scenarios to ensure the specialized
 *          method data does not cause incorrect optimizations.
 *
 * @requires vm.flavor == "server" & vm.compMode == "Xmixed" & !vm.emulatedClient
 * @requires vm.compiler2.enabled
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+SpecializedMethodData
 *                   -XX:-BackgroundCompilation
 *                   compiler.profiling.specialized.TestSpecializedProfilesCorrectness
 */

package compiler.profiling.specialized;

public class TestSpecializedProfilesCorrectness {

    static int passCount;

    interface I {
        int m();
    }

    static class IA implements I {
        public int m() { return 10; }
    }

    static class IB implements I {
        public int m() { return 20; }
    }

    static abstract class A {
        abstract int m();
    }

    static class AA extends A {
        int m() { return 1; }
    }

    static class AB extends A {
        int m() { return 2; }
    }

    static class AC extends A {
        int m() { return 3; }
    }

    static int virtualInlinee(A a) {
        return a.m();
    }

    static int interfaceInlinee(I i) {
        return i.m();
    }

    static int virtualCalleeAndArg(A a) {
        return a.m() + a.m();
    }

    static int nestedInlinee(A a) {
        return virtualInlinee(a);
    }

    static int checkcastInlinee(Object o) {
        if (o instanceof A) {
            return ((A) o).m();
        }
        return -1;
    }

    static void testMonomorphicSite() {
        AA aa = new AA();
        int expected = 1 * 20_000;
        int sum = 0;
        for (int i = 0; i < 20_000; i++) {
            sum += virtualInlinee(aa);
        }
        verify(sum == expected, "monomorphic site", sum, expected);
    }

    static void testBimorphicSite() {
        AA aa = new AA();
        AB ab = new AB();
        int expected = (1 + 2) * 10_000;
        int sum = 0;
        for (int i = 0; i < 20_000; i++) {
            sum += virtualInlinee(i % 2 == 0 ? aa : ab);
        }
        verify(sum == expected, "bimorphic site", sum, expected);
    }

    static void testMegamorphicSite() {
        AA aa = new AA();
        AB ab = new AB();
        AC ac = new AC();
        int sum = 0;
        int countA = 0, countB = 0, countC = 0;
        for (int i = 0; i < 20_000; i++) {
            A a;
            switch (i % 3) {
                case 0:  a = aa; countA++; break;
                case 1:  a = ab; countB++; break;
                default: a = ac; countC++; break;
            }
            sum += virtualInlinee(a);
        }
        int expected = 1 * countA + 2 * countB + 3 * countC;
        verify(sum == expected, "megamorphic site", sum, expected);
    }

    static void testSeparateMonomorphicSites() {
        AA aa = new AA();
        AB ab = new AB();
        AC ac = new AC();
        int expectedA = 1 * 20_000;
        int expectedB = 2 * 20_000;
        int expectedC = 3 * 20_000;

        int sumA = 0, sumB = 0, sumC = 0;
        for (int i = 0; i < 20_000; i++) {
            sumA += virtualInlinee(aa);
            sumB += virtualInlinee(ab);
            sumC += virtualInlinee(ac);
        }
        verify(sumA == expectedA, "separate monomorphic site A", sumA, expectedA);
        verify(sumB == expectedB, "separate monomorphic site B", sumB, expectedB);
        verify(sumC == expectedC, "separate monomorphic site C", sumC, expectedC);
    }

    static void testInterfaceDispatch() {
        IA ia = new IA();
        IB ib = new IB();
        int expectedA = 10 * 20_000;
        int expectedB = 20 * 20_000;

        int sumA = 0, sumB = 0;
        for (int i = 0; i < 20_000; i++) {
            sumA += interfaceInlinee(ia);
            sumB += interfaceInlinee(ib);
        }
        verify(sumA == expectedA, "interface site A", sumA, expectedA);
        verify(sumB == expectedB, "interface site B", sumB, expectedB);
    }

    static void testCalleeAndArgSame() {
        AA aa = new AA();
        int expected = 2 * 20_000;
        int sum = 0;
        for (int i = 0; i < 20_000; i++) {
            sum += virtualCalleeAndArg(aa);
        }
        verify(sum == expected, "callee and arg same", sum, expected);
    }

    static void testNestedInlining() {
        AA aa = new AA();
        int expected = 1 * 20_000;
        int sum = 0;
        for (int i = 0; i < 20_000; i++) {
            sum += nestedInlinee(aa);
        }
        verify(sum == expected, "nested inlining", sum, expected);
    }

    static void testCheckcastInlining() {
        AA aa = new AA();
        String s = "hello";
        int expectedObj = 1 * 10_000;
        int expectedStr = -1 * 10_000;
        int sumObj = 0, sumStr = 0;
        for (int i = 0; i < 20_000; i++) {
            if (i % 2 == 0) {
                sumObj += checkcastInlinee(aa);
            } else {
                sumStr += checkcastInlinee(s);
            }
        }
        verify(sumObj == expectedObj, "checkcast A", sumObj, expectedObj);
        verify(sumStr == expectedStr, "checkcast String", sumStr, expectedStr);
    }

    static void testMixedDispatch() {
        AA aa = new AA();
        AB ab = new AB();
        int expectedA = 1 * 20_000;
        int expectedB = 2 * 20_000;
        int sumA = 0, sumB = 0;
        for (int i = 0; i < 20_000; i++) {
            sumA += virtualInlinee(aa);
            sumB += virtualInlinee(ab);
        }
        verify(sumA == expectedA, "mixed dispatch A", sumA, expectedA);
        verify(sumB == expectedB, "mixed dispatch B", sumB, expectedB);
    }

    static void verify(boolean cond, String name, int actual, int expected) {
        if (cond) {
            passCount++;
        } else {
            throw new RuntimeException(
                name + ": expected " + expected + " but got " + actual);
        }
    }

    public static void main(String[] args) {
        testMonomorphicSite();
        testBimorphicSite();
        testMegamorphicSite();
        testSeparateMonomorphicSites();
        testInterfaceDispatch();
        testCalleeAndArgSame();
        testNestedInlining();
        testCheckcastInlining();
        testMixedDispatch();
        System.out.println("All " + passCount + " checks passed.");
    }
}
