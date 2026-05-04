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
 * @summary Combined scenario: an inlined method has both virtual calls (type
 *          profile) and conditional branches (branch profile) whose behavior
 *          differs across call sites. Specialized profiles isolate both profile
 *          types per call site, enabling C2 to make contextual optimizations.
 *          This test verifies correctness of the combined type+branch
 *          speculation with -XX:+SpecializedMethodData.
 *
 * @requires vm.flavor == "server" & vm.compMode == "Xmixed" & !vm.emulatedClient
 * @requires vm.compiler2.enabled
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+SpecializedMethodData
 *                   -XX:-BackgroundCompilation -XX:-UseOnStackReplacement
 *                   -XX:+UseTypeSpeculation -XX:TypeProfileLevel=222
 *                   compiler.profiling.specialized.TestSpecializedProfilesCombinedContext
 */

package compiler.profiling.specialized;

public class TestSpecializedProfilesCombinedContext {

    interface Data {
        int value();
    }

    static final class StringData implements Data {
        String s;
        StringData(String s) { this.s = s; }
        public int value() { return s.length(); }
    }

    static final class IntData implements Data {
        int n;
        IntData(int n) { this.n = n; }
        public int value() { return n * n; }
    }

    static final class DoubleData implements Data {
        double d;
        DoubleData(double d) { this.d = d; }
        public int value() { return (int) d; }
    }

    static final int MODE_VALIDATE = 0;
    static final int MODE_COMPUTE = 1;
    static final int MODE_FALLBACK = 2;

    static int processData(Data data, int mode) {
        if (mode == MODE_VALIDATE) {
            return data.value();
        } else if (mode == MODE_COMPUTE) {
            return data.value() * 2;
        }
        return -1;
    }

    static void testStringValidate() {
        int sum = 0;
        StringData sd = new StringData("abc");
        for (int i = 0; i < 20_000; i++) {
            sum += processData(sd, MODE_VALIDATE);
        }
        if (sum != 3 * 20_000) {
            throw new RuntimeException("String validate: expected " + (3 * 20_000) + " got " + sum);
        }

        int crossCompute = processData(sd, MODE_COMPUTE);
        if (crossCompute != 6) {
            throw new RuntimeException("String compute after validate: expected 6 got " + crossCompute);
        }
    }

    static void testIntCompute() {
        int sum = 0;
        IntData id = new IntData(5);
        for (int i = 0; i < 20_000; i++) {
            sum += processData(id, MODE_COMPUTE);
        }
        if (sum != 50 * 20_000) {
            throw new RuntimeException("Int compute: expected " + (50 * 20_000) + " got " + sum);
        }

        int crossValidate = processData(id, MODE_VALIDATE);
        if (crossValidate != 25) {
            throw new RuntimeException("Int validate after compute: expected 25 got " + crossValidate);
        }
    }

    static void testDoubleFallback() {
        int sum = 0;
        DoubleData dd = new DoubleData(7.0);
        for (int i = 0; i < 20_000; i++) {
            sum += processData(dd, MODE_FALLBACK);
        }
        if (sum != -1 * 20_000) {
            throw new RuntimeException("Double fallback: expected " + (-1 * 20_000) + " got " + sum);
        }

        int crossValidate = processData(dd, MODE_VALIDATE);
        if (crossValidate != 7) {
            throw new RuntimeException("Double validate after fallback: expected 7 got " + crossValidate);
        }
    }

    static void testMixedContext() {
        Data[] data = { new StringData("x"), new IntData(3), new DoubleData(2.0) };
        int[] modes = { MODE_VALIDATE, MODE_COMPUTE, MODE_FALLBACK };

        int sum = 0;
        int cntV = 0, cntC = 0, cntF = 0;
        for (int i = 0; i < 20_000; i++) {
            int idx = i % 3;
            if (idx == 0) cntV++;
            else if (idx == 1) cntC++;
            else cntF++;
            sum += processData(data[idx], modes[idx]);
        }

        int expected = 1 * cntV + 18 * cntC + (-1) * cntF;
        if (sum != expected) {
            throw new RuntimeException("Mixed: expected " + expected + " got " + sum);
        }
    }

    static void testCrossTypeAndMode() {
        StringData sd = new StringData("hi");
        IntData id = new IntData(4);
        int sum = 0;
        for (int i = 0; i < 20_000; i++) {
            sum += processData(sd, MODE_VALIDATE);
            sum += processData(id, MODE_COMPUTE);
        }
        if (sum != (2 + 32) * 20_000) {
            throw new RuntimeException("Cross type+mode: expected " + ((2 + 32) * 20_000) + " got " + sum);
        }

        int swapValidate = processData(id, MODE_VALIDATE);
        if (swapValidate != 16) {
            throw new RuntimeException("Int validate after cross: expected 16 got " + swapValidate);
        }

        int swapCompute = processData(sd, MODE_COMPUTE);
        if (swapCompute != 4) {
            throw new RuntimeException("String compute after cross: expected 4 got " + swapCompute);
        }

        int fallback = processData(sd, MODE_FALLBACK);
        if (fallback != -1) {
            throw new RuntimeException("String fallback: expected -1 got " + fallback);
        }
    }

    public static void main(String[] args) {
        testStringValidate();
        testIntCompute();
        testDoubleFallback();
        testMixedContext();
        testCrossTypeAndMode();
        System.out.println("All combined context tests passed.");
    }
}
