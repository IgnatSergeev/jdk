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
 * @summary With specialized profiles, type speculation inside an inlined method
 *          is more precise: each call site gets its own speculative type based on
 *          the types it actually passes, rather than the merged types from all
 *          call sites. This test verifies correctness of type speculation with
 *          -XX:+SpecializedMethodData across different call-site contexts.
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
 *                   compiler.profiling.specialized.TestSpecializedProfilesTypeSpeculation
 */

package compiler.profiling.specialized;

import jdk.test.whitebox.WhiteBox;
import java.lang.reflect.Method;

public class TestSpecializedProfilesTypeSpeculation {

    static final WhiteBox WB = WhiteBox.getWhiteBox();
    static final int COMP_LEVEL_FULL = 4;

    static abstract class Shape {
        abstract int area();
    }

    static final class Circle extends Shape {
        int r;
        Circle(int r) { this.r = r; }
        int area() { return r * r * 3; }
    }

    static final class Square extends Shape {
        int s;
        Square(int s) { this.s = s; }
        int area() { return s * s; }
    }

    static final class Triangle extends Shape {
        int b, h;
        Triangle(int b, int h) { this.b = b; this.h = h; }
        int area() { return b * h / 2; }
    }

    static int classifyAndArea(Shape s) {
        if (s instanceof Circle) {
            return ((Circle) s).area();
        } else if (s instanceof Square) {
            return ((Square) s).area();
        }
        return -1;
    }

    static int siteCircle() {
        int sum = 0;
        Circle c = new Circle(3);
        for (int i = 0; i < 20_000; i++) {
            sum += classifyAndArea(c);
        }
        return sum;
    }

    static int siteSquare() {
        int sum = 0;
        Square sq = new Square(4);
        for (int i = 0; i < 20_000; i++) {
            sum += classifyAndArea(sq);
        }
        return sum;
    }

    static int siteTriangle() {
        int sum = 0;
        Triangle t = new Triangle(6, 4);
        for (int i = 0; i < 20_000; i++) {
            sum += classifyAndArea(t);
        }
        return sum;
    }

    static void testCorrectSpeculationPerSite() {
        int circleResult = siteCircle();
        int squareResult = siteSquare();

        int expectedCircle = 27 * 20_000;
        int expectedSquare = 16 * 20_000;

        if (circleResult != expectedCircle) {
            throw new RuntimeException("Circle site: expected " + expectedCircle +
                " but got " + circleResult);
        }
        if (squareResult != expectedSquare) {
            throw new RuntimeException("Square site: expected " + expectedSquare +
                " but got " + squareResult);
        }
    }

    static void testSpeculationWithCompilationControl() throws Exception {
        Method siteCircleMethod = TestSpecializedProfilesTypeSpeculation.class
            .getDeclaredMethod("siteCircle");
        Method siteSquareMethod = TestSpecializedProfilesTypeSpeculation.class
            .getDeclaredMethod("siteSquare");

        Circle c = new Circle(3);
        Square sq = new Square(4);

        for (int i = 0; i < 20_000; i++) {
            classifyAndArea(c);
        }

        WB.enqueueMethodForCompilation(siteCircleMethod, COMP_LEVEL_FULL);
        if (!WB.isMethodCompiled(siteCircleMethod)) {
            throw new RuntimeException("siteCircle not compiled by C2");
        }

        int circleArea = classifyAndArea(c);
        if (circleArea != 27) {
            throw new RuntimeException("classifyAndArea(Circle) = " + circleArea + ", expected 27");
        }

        for (int i = 0; i < 20_000; i++) {
            classifyAndArea(sq);
        }

        WB.enqueueMethodForCompilation(siteSquareMethod, COMP_LEVEL_FULL);
        if (!WB.isMethodCompiled(siteSquareMethod)) {
            throw new RuntimeException("siteSquare not compiled by C2");
        }

        int squareArea = classifyAndArea(sq);
        if (squareArea != 16) {
            throw new RuntimeException("classifyAndArea(Square) = " + squareArea + ", expected 16");
        }
    }

    static void testPolymorphicSiteWithSpecializedProfiles() {
        Circle c = new Circle(3);
        Square sq = new Square(4);
        Triangle t = new Triangle(6, 4);

        int sum = 0;
        int countC = 0, countSq = 0, countT = 0;
        for (int i = 0; i < 20_000; i++) {
            Shape s;
            switch (i % 3) {
                case 0:  s = c; countC++; break;
                case 1:  s = sq; countSq++; break;
                default: s = t; countT++; break;
            }
            sum += classifyAndArea(s);
        }

        int expected = 27 * countC + 16 * countSq + (-1) * countT;
        if (sum != expected) {
            throw new RuntimeException("Polymorphic site: expected " + expected +
                " but got " + sum);
        }
    }

    static void testTypeCheckFallbackCorrectness() {
        Circle c = new Circle(3);
        Square sq = new Square(4);

        int sumCircle = 0;
        for (int i = 0; i < 20_000; i++) {
            sumCircle += classifyAndArea(c);
        }

        int resultAfterFallback = classifyAndArea(sq);
        if (resultAfterFallback != 16) {
            throw new RuntimeException("After fallback: expected 16 but got " + resultAfterFallback);
        }

        int resultCircleAfter = classifyAndArea(c);
        if (resultCircleAfter != 27) {
            throw new RuntimeException("Circle after fallback: expected 27 but got " + resultCircleAfter);
        }
    }

    public static void main(String[] args) throws Exception {
        testCorrectSpeculationPerSite();
        testSpeculationWithCompilationControl();
        testPolymorphicSiteWithSpecializedProfiles();
        testTypeCheckFallbackCorrectness();
        System.out.println("All type speculation tests passed.");
    }
}
