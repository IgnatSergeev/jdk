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
 * @summary Specialized profiles prevent type pollution: when the same method is
 *          inlined at multiple call sites that each pass a different receiver type,
 *          specialized profiles keep each site's virtual call monomorphic instead of
 *          letting the shared method data become megamorphic (3+ receiver types).
 *          With -XX:+SpecializedMethodData, C2 inlines the virtual call at each site;
 *          without it, C2 falls back to a virtual dispatch.
 *
 * @requires vm.flagless
 * @requires vm.flavor == "server" & vm.compMode == "Xmixed" & !vm.emulatedClient
 * @requires vm.compiler2.enabled
 *
 * @library /test/lib
 * @run driver compiler.profiling.specialized.TestSpecializedProfilesMegamorphicInlining
 */

package compiler.profiling.specialized;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestSpecializedProfilesMegamorphicInlining {

    static abstract class Base {
        abstract int m();
    }

    static final class A extends Base {
        int m() { return 1; }
    }

    static final class B extends Base {
        int m() { return 2; }
    }

    static final class C extends Base {
        int m() { return 3; }
    }

    static int inlinee(Base b) {
        return b.m();
    }

    static class Runner {
        static int siteA() {
            int sum = 0;
            A a = new A();
            for (int i = 0; i < 20_000; i++) {
                sum += inlinee(a);
            }
            return sum;
        }

        static int siteB() {
            int sum = 0;
            B b = new B();
            for (int i = 0; i < 20_000; i++) {
                sum += inlinee(b);
            }
            return sum;
        }

        static int siteC() {
            int sum = 0;
            C c = new C();
            for (int i = 0; i < 20_000; i++) {
                sum += inlinee(c);
            }
            return sum;
        }

        public static void main(String[] args) {
            int result = siteA() + siteB() + siteC();
            if (result != 20_000 * (1 + 2 + 3)) {
                throw new RuntimeException("Wrong result: " + result);
            }
            System.out.println("Result: " + result);
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
            "-XX:CompileCommand=compileonly,*Runner::inlinee",
            className
        );
        OutputAnalyzer outWith = new OutputAnalyzer(pbWith.start());
        outWith.shouldHaveExitValue(0);

        boolean virtualCallWithSpec = hasVirtualCall(outWith, "Base::m");

        ProcessBuilder pbWithout = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:-SpecializedMethodData",
            "-XX:+PrintInlining",
            "-XX:-BackgroundCompilation",
            "-XX:CompileCommand=quiet",
            "-XX:CompileCommand=compileonly,*Runner::site*",
            "-XX:CompileCommand=compileonly,*Runner::inlinee",
            className
        );
        OutputAnalyzer outWithout = new OutputAnalyzer(pbWithout.start());
        outWithout.shouldHaveExitValue(0);

        boolean virtualCallWithoutSpec = hasVirtualCall(outWithout, "Base::m");

        if (virtualCallWithSpec && !virtualCallWithoutSpec) {
            throw new RuntimeException(
                "Specialized profiles did not improve inlining.\n" +
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
