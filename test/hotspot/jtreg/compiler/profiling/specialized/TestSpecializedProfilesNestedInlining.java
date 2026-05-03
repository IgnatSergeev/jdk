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
 * @summary Verify that specialized profiles work correctly with nested inlining:
 *          when a method is inlined, and within that inlined body another method
 *          is also inlined, the specialized MDO chain should be followed correctly.
 *          Each level of inlining should use its own specialized profile data,
 *          preserving per-call-site type information.
 *
 * @requires vm.flagless
 * @requires vm.flavor == "server" & vm.compMode == "Xmixed" & !vm.emulatedClient
 * @requires vm.compiler2.enabled
 *
 * @library /test/lib
 * @run driver compiler.profiling.specialized.TestSpecializedProfilesNestedInlining
 */

package compiler.profiling.specialized;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestSpecializedProfilesNestedInlining {

    static abstract class Base {
        abstract int m();
    }

    static final class P extends Base {
        int m() { return 1; }
    }

    static final class Q extends Base {
        int m() { return 2; }
    }

    static final class R extends Base {
        int m() { return 3; }
    }

    static int leaf(Base b) {
        return b.m();
    }

    static int middle(Base b) {
        return leaf(b) + leaf(b);
    }

    static class Runner {
        static int siteP() {
            int sum = 0;
            P p = new P();
            for (int i = 0; i < 20_000; i++) {
                sum += middle(p);
            }
            return sum;
        }

        static int siteQ() {
            int sum = 0;
            Q q = new Q();
            for (int i = 0; i < 20_000; i++) {
                sum += middle(q);
            }
            return sum;
        }

        static int siteR() {
            int sum = 0;
            R r = new R();
            for (int i = 0; i < 20_000; i++) {
                sum += middle(r);
            }
            return sum;
        }

        public static void main(String[] args) {
            int resultP = siteP();
            int resultQ = siteQ();
            int resultR = siteR();

            int expectedP = 2 * 1 * 20_000;
            int expectedQ = 2 * 2 * 20_000;
            int expectedR = 2 * 3 * 20_000;

            if (resultP != expectedP) {
                throw new RuntimeException("P: expected " + expectedP + " got " + resultP);
            }
            if (resultQ != expectedQ) {
                throw new RuntimeException("Q: expected " + expectedQ + " got " + resultQ);
            }
            if (resultR != expectedR) {
                throw new RuntimeException("R: expected " + expectedR + " got " + resultR);
            }
            System.out.println("Result P=" + resultP + " Q=" + resultQ + " R=" + resultR);
        }
    }

    static boolean hasVirtualCall(OutputAnalyzer output, String method) {
        return output.asLines().stream()
            .anyMatch(s -> s.contains(method) && s.contains("virtual call"));
    }

    public static void main(String[] args) throws Exception {
        String className = Runner.class.getName();

        ProcessBuilder pbWith = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+SpecializedMethodData",
            "-XX:+PrintInlining",
            "-XX:-BackgroundCompilation",
            "-XX:CompileCommand=quiet",
            "-XX:CompileCommand=compileonly,*Runner::site*",
            "-XX:CompileCommand=compileonly,*Runner::middle",
            "-XX:CompileCommand=compileonly,*Runner::leaf",
            className
        );
        OutputAnalyzer outWith = new OutputAnalyzer(pbWith.start());
        outWith.shouldHaveExitValue(0);

        ProcessBuilder pbWithout = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:-SpecializedMethodData",
            "-XX:+PrintInlining",
            "-XX:-BackgroundCompilation",
            "-XX:CompileCommand=quiet",
            "-XX:CompileCommand=compileonly,*Runner::site*",
            "-XX:CompileCommand=compileonly,*Runner::middle",
            "-XX:CompileCommand=compileonly,*Runner::leaf",
            className
        );
        OutputAnalyzer outWithout = new OutputAnalyzer(pbWithout.start());
        outWithout.shouldHaveExitValue(0);

        boolean virtualCallWithSpec = hasVirtualCall(outWith, "Base::m");
        boolean virtualCallWithoutSpec = hasVirtualCall(outWithout, "Base::m");

        if (virtualCallWithSpec && !virtualCallWithoutSpec) {
            throw new RuntimeException(
                "Specialized profiles did not improve nested inlining.\n" +
                "  With specialized profiles: virtual call = " + virtualCallWithSpec + "\n" +
                "  Without specialized profiles: virtual call = " + virtualCallWithoutSpec + "\n" +
                "--- Output with SpecializedMethodData ---\n" +
                outWith.getStdout() + "\n" +
                "--- Output without SpecializedMethodData ---\n" +
                outWithout.getStdout()
            );
        }

        System.out.println("TEST PASSED");
        System.out.println("  With specialized profiles: virtual call inlined = " + !virtualCallWithSpec);
        System.out.println("  Without specialized profiles: virtual call inlined = " + !virtualCallWithoutSpec);
    }
}
