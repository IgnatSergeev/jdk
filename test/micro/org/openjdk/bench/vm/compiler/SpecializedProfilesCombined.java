/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR FILE HEADER.
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
 * or visit www.oracle.com if you need any additional information or
 * have any questions.
 */
package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Benchmarks for specialized method data (profiles) in combined type + branch
 * profile scenarios.
 *
 * <p>Combined scenario: the inlinee has both a virtual call (type profile)
 * and a conditional branch (branch profile). The hot loop is in the
 * benchmark method. Receivers are stored in an array of the interface type
 * to prevent C2 from using call-site type speculation. Classes are
 * non-final to prevent CHA-based devirtualization.
 *
 * <p>Without specialized profiles:
 * <ul>
 *   <li>Shared type profile accumulates all receiver types &rarr; megamorphic
 *       &rarr; C2 cannot inline the virtual call</li>
 *   <li>Shared branch profile shows both arms &rarr; C2 cannot use
 *       {@code unstable_if} &rarr; both code paths remain</li>
 * </ul>
 *
 * <p>With specialized profiles:
 * <ul>
 *   <li>Per-site type profile is monomorphic &rarr; C2 inlines the virtual
 *       call &rarr; further optimization of the inlined body</li>
 *   <li>Per-site branch profile shows one arm &rarr; C2 uses
 *       {@code unstable_if} &rarr; dead arm eliminated</li>
 * </ul>
 *
 * <p>To compare performance with and without specialized profiles, run with:
 * <pre>
 *   -XX:+UnlockDiagnosticVMOptions -XX:+SpecializedMethodData   (with)
 *   -XX:+UnlockDiagnosticVMOptions -XX:-SpecializedMethodData   (without)
 * </pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
public class SpecializedProfilesCombined {

    static final int SIZE = 1024;

    interface Data {
        int compute(int v);
    }

    static class AddData implements Data {
        public int compute(int v) { return v + 1; }
    }

    static class MulData implements Data {
        public int compute(int v) { return v * 31; }
    }

    static class XorData implements Data {
        public int compute(int v) { return v ^ 0x5555AAAA; }
    }

    static int combinedInlinee(Data d, int v) {
        int result = d.compute(v);
        if (v > 0) {
            return result;
        } else {
            return result * 31 + 7;
        }
    }

    Data[] receivers;
    int[] posData;
    int[] negData;
    int[] data;

    @Setup
    public void setup() {
        receivers = new Data[]{new AddData(), new MulData(), new XorData()};
        posData = new int[SIZE];
        negData = new int[SIZE];
        data = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            posData[i] = i + 1;
            negData[i] = -(i + 1);
            data[i] = i + 1;
        }
    }

    @Benchmark
    public int singleTypePositiveData() {
        int sum = 0;
        for (int i = 0; i < posData.length; i++) {
            sum += combinedInlinee(receivers[0], posData[i]);
        }
        return sum;
    }

    @Benchmark
    public int twoTypesSameData() {
        int sum = 0;
        for (int i = 0; i < data.length; i++) {
            sum += combinedInlinee(receivers[0], data[i]);
            sum += combinedInlinee(receivers[1], data[i]);
        }
        return sum;
    }

    @Benchmark
    public int twoTypesDifferentData() {
        int sum = 0;
        for (int i = 0; i < posData.length; i++) {
            sum += combinedInlinee(receivers[0], posData[i]);
            sum += combinedInlinee(receivers[1], negData[i]);
        }
        return sum;
    }

    @Benchmark
    public int threeTypesMixedData() {
        int sum = 0;
        for (int i = 0; i < posData.length; i++) {
            sum += combinedInlinee(receivers[0], posData[i]);
            sum += combinedInlinee(receivers[1], negData[i]);
            sum += combinedInlinee(receivers[2], posData[i]);
        }
        return sum;
    }
}
