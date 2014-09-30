/*
 * Copyright 2014 Midokura SARL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.midonet.benchmarks.mpi;

public class BlockDistribution implements Distribution {
    private final int globalCount;
    private final int localCount;
    private final int procSize;
    private final int procRank;

    private final int globalArraySize;
    private final int localArraySize;
    private final int imbalance;

    public BlockDistribution(int globalCount, int processes, int localRank) {
        if (processes <= 0) throw
            new IllegalArgumentException("empty process group");
        if (localRank < 0 || localRank >= processes) throw
            new IllegalArgumentException("rank beyond process group size");
        this.globalCount = globalCount;
        this.procSize = processes;
        this.procRank = localRank;

        int partial = this.globalCount / this.procSize;
        int rest = this.globalCount % this.procSize;
        this.localCount = (this.procRank < rest)? partial + 1: partial;
        this.localArraySize = (rest != 0)? partial + 1: partial;
        this.globalArraySize = this.localArraySize * this.procSize;
        this.imbalance = rest;
    }

    @Override
    public int getGlobalCount() {
        return globalCount;
    }

    @Override
    public int getLocalCount() {
        return localCount;
    }

    @Override
    public int getLocalArraySize() {
        return localArraySize;
    }

    @Override
    public int getGlobalArraySize() {
        return globalArraySize;
    }

    @Override
    public int getGlobalArrayIndex(int localIndex) {
        if (localIndex < 0 || localIndex >= localArraySize) throw
            new IndexOutOfBoundsException("beyond local array size");
        return procRank * localArraySize + localIndex;
    }

    @Override
    public int getItemCount(int processRank) {
        if (processRank >= procSize) throw
            new IndexOutOfBoundsException("rank beyond process group size");
        if (imbalance == 0 || processRank < imbalance)
            return localArraySize;
        return localArraySize - 1;
    }

    @Override
    public int getGlobalOffset(int localOffset) {
        if (localOffset < 0 || localOffset >= localCount) {
            throw new IndexOutOfBoundsException("beyond local work size");
        }
        int offset = procRank * localCount + localOffset;
        if (procRank >= imbalance)
            offset += imbalance;
        return offset;
    }

    @Override
    public <T> void compactGlobalArray(T[] source, T[] dest) {
        int ptr = 0;
        for (int proc = 0; proc < procSize; proc++) {
            int b = proc * localArraySize;
            int items = getItemCount(proc);
            for (int j = 0; j < items; j++) {
                dest[ptr] = source[b + j];
                ptr += 1;
            }
        }
    }
}
