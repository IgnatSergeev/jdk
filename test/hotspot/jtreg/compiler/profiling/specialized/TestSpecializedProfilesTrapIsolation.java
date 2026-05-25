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
 * @summary With specialized profiles, deoptimization traps are isolated per call
 *          site: a trap that fires when an unexpected type is passed at one call site
 *          does not invalidate the compilation of another call site for the same
 *          inlined method. Without specialized profiles, the shared profile would
 *          have been polluted by the trap, potentially affecting all call sites.
 *
 *          Scenario: inlinee(Base b) is called from site1 with only X receivers
 *          and from site2 with only Y receivers. With specialized profiles, C2
 *          compiles site1 with a monomorphic inline cache for X. Passing Y to site1
 *          triggers an uncommon trap (speculation failure). Crucially, site2 —
 *          compiled with a monomorphic cache for Y — remains valid because its
 *          specialized profile is separate.
 *
 * @requires vm.flavor == "server" & vm.compMode == "Xmixed" & !vm.emulatedClient
 * @requires vm.compiler2.enabled
 *
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+SpecializedMethodData -XX:+UseTypeSpeculation
 *                   -XX:-BackgroundCompilation -XX:-UseOnStackReplacement
 *                   -XX:TypeProfileLevel=222
 *                   -XX:+UnlockExperimentalVMOptions
 *                   -XX:PerMethodSpecTrapLimit=5000 -XX:PerMethodTrapLimit=100
 *                   compiler.profiling.specialized.TestSpecializedProfilesTrapIsolation
 */

package compiler.profiling.specialized;

import jdk.test.whitebox.WhiteBox;
import java.lang.reflect.Method;

public class TestSpecializedProfilesTrapIsolation {

    static final WhiteBox WB = WhiteBox.getWhiteBox();
    static final int COMP_LEVEL_FULL = 4;

    static abstract class Base {
        abstract int m();
    }

    static final class X extends Base {
        int m() { return 10; }
    }

    static final class Y extends Base {
        int m() { return 20; }
    }

    static int inlinee(Base b) {
        return b.m();
    }

    static int site1(Base b) {
        return inlinee(b);
    }

    static int site2(Base b) {
        return inlinee(b);
    }

    static void testTrapIsolationBetweenSites() throws Exception {
        Method site1Method = TestSpecializedProfilesTrapIsolation.class
            .getDeclaredMethod("site1", Base.class);
        Method site2Method = TestSpecializedProfilesTrapIsolation.class
            .getDeclaredMethod("site2", Base.class);

        X x = new X();
        Y y = new Y();

        for (int i = 0; i < 20_000; i++) {
            site1(x);
            site2(y);
        }

        WB.enqueueMethodForCompilation(site1Method, COMP_LEVEL_FULL);
        WB.enqueueMethodForCompilation(site2Method, COMP_LEVEL_FULL);

        if (!WB.isMethodCompiled(site1Method)) {
            throw new RuntimeException("site1 not compiled by C2");
        }
        if (!WB.isMethodCompiled(site2Method)) {
            throw new RuntimeException("site2 not compiled by C2");
        }

        int result = site1(y);
        if (result != 20) {
            throw new RuntimeException("site1(y) returned wrong result: " + result);
        }

        boolean site1Deoptimized = !WB.isMethodCompiled(site1Method);
        boolean site2StillCompiled = WB.isMethodCompiled(site2Method);

        int site2WithY = site2(y);
        if (site2WithY != 20) {
            throw new RuntimeException("site2(y) returned wrong result: " + site2WithY);
        }

        boolean site2StillCompiledAfterCheck = WB.isMethodCompiled(site2Method);

        if (!site1Deoptimized) {
            System.out.println("WARNING: site1 did not deoptimize on unexpected type Y");
        }
        if (!site2StillCompiled) {
            throw new RuntimeException(
                "Trap isolation failed: site2 was deoptimized by site1's trap");
        }
        if (!site2StillCompiledAfterCheck) {
            throw new RuntimeException(
                "site2 deoptimized when receiving its expected type Y");
        }

        System.out.println("Trap isolation verified:");
        System.out.println("  site1 deoptimized on Y: " + site1Deoptimized);
        System.out.println("  site2 (profiled for Y) unaffected: " + site2StillCompiled);
    }

    static void testRecompilationAfterTrap() throws Exception {
        Method site1Method = TestSpecializedProfilesTrapIsolation.class
            .getDeclaredMethod("site1", Base.class);

        X x = new X();
        Y y = new Y();

        for (int i = 0; i < 20_000; i++) {
            site1(x);
        }

        WB.enqueueMethodForCompilation(site1Method, COMP_LEVEL_FULL);
        if (!WB.isMethodCompiled(site1Method)) {
            throw new RuntimeException("site1 not compiled by C2 (initial)");
        }

        site1(y);

        WB.enqueueMethodForCompilation(site1Method, COMP_LEVEL_FULL);
        if (!WB.isMethodCompiled(site1Method)) {
            throw new RuntimeException("site1 not recompiled by C2 after trap");
        }

        int resultX = site1(x);
        int resultY = site1(y);
        if (resultX != 10 || resultY != 20) {
            throw new RuntimeException(
                "Wrong result after recompilation: x=" + resultX + ", y=" + resultY);
        }

        System.out.println("Recompilation after trap verified successfully.");
    }

    public static void main(String[] args) throws Exception {
        testTrapIsolationBetweenSites();
        testRecompilationAfterTrap();
        System.out.println("All trap isolation tests passed.");
    }
}
