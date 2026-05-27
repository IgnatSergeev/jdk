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
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * @test
 * @summary Verify that specialized profiles don`t trigger decompiles
 *          when call sites use different types on virtual call.
 *          and that triggered decompile affects only one site.
 *
 * @requires vm.flavor == "server" & vm.compMode == "Xmixed" & !vm.emulatedClient
 * @requires vm.compiler2.enabled
 *
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+UnlockExperimentalVMOptions -XX:+SpecializedMethodData
 *                   -XX:-BackgroundCompilation -XX:-UseOnStackReplacement
 *                   compiler.profiling.specialized.PerSiteSpeculateClassCheck
 */

package compiler.profiling.specialized;

import jdk.test.whitebox.WhiteBox;
import java.lang.reflect.Method;

public class PerSiteSpeculateClassCheck {

    static final WhiteBox WB = WhiteBox.getWhiteBox();
    static final int C2 = 4;

    static abstract class Base { abstract int m(); }
    static final class X extends Base { int m() { return 10; } }
    static final class Y extends Base { int m() { return 20; } }

    static int inlinee(Base b) { return b.m(); }
    static int site1(Base b) { return inlinee(b); }
    static int site2(Base b) { return inlinee(b); }

    public static void main(String[] args) throws Exception {
        Class<?> self = PerSiteSpeculateClassCheck.class;
        Method m1 = self.getDeclaredMethod("site1", Base.class);
        Method m2 = self.getDeclaredMethod("site2", Base.class);
        Method inl = self.getDeclaredMethod("inlinee", Base.class);

        int size = 100000;
        Base[] xData = new Base[size];
        Base[] yData = new Base[size];
        for (int i = 0; i < size; i++) {
            xData[i] = new X();
            yData[i] = new Y();
        }

        for (int i = 0; i < size; i++) {
            site1(xData[i]);
            site2(yData[i]);
        }
        WB.enqueueMethodForCompilation(m1, C2);
        WB.enqueueMethodForCompilation(m2, C2);
        if (!WB.isMethodCompiled(m1) || !WB.isMethodCompiled(m2)) {
            throw new RuntimeException("Methods not compiled by C2");
        }

        int dc1Before = WB.getMethodDecompileCount(m1);
        int dc2Before = WB.getMethodDecompileCount(m2);
        if (dc1Before != 0 || dc2Before != 0) {
            throw new RuntimeException("Unexpected method decompiles");
        }

        int deopt = site1(yData[0]);
        int nodeopt = site2(yData[0]);

        int dc1After = WB.getMethodDecompileCount(m1);
        int dc2After = WB.getMethodDecompileCount(m2);

        if (dc1Before == dc1After) {
            throw new RuntimeException("No expected decompile");
        }
        if (dc2Before != dc2After) {
            throw new RuntimeException("Unexpected decompile");
        }
    }
}
