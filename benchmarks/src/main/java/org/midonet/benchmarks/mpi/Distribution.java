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

public interface Distribution {
    // Total amount of elements to be distributed.
    int getGlobalCount();

    // Amount of elements to be processed locally.
    int getLocalCount();

    // Normalized size for local arrays of elements.
    int getLocalArraySize();

    // Normalized size for global arrays of elements.
    int getGlobalArraySize();

    // Get the global array index corresponding to the given local index.
    int getGlobalArrayIndex(int localIndex);

    // Get the local amount of elements assigned to a particular process.
    int getItemCount(int processRank);

    // Get the global offset in the original amount of elements (i.e. ignoring
    // array padding) corresponding to the given local offset.
    int getGlobalOffset(int localOffset);

    // Compact a global array, eliminating padding.
    // Note: destination array must be of the proper size.
    <T> void compactGlobalArray(T[] source, T[] dest);
}
