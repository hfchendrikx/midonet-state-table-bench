package org.midonet.benchmarks.mpi;

import java.util.Arrays;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class BlockDistributionTest {

    private void testCases(int procs, int itemsPerNode, int imbalance) {
        final int size = procs * itemsPerNode + imbalance;
        final int bigSize = (imbalance > 0)? procs * (itemsPerNode + 1): size;
        final Distribution dist[] = new BlockDistribution[procs];

        // First processes should get one more element
        for (int p = 0; p < imbalance; p++) {
            dist[p] = new BlockDistribution(size, procs, p);

            assertEquals(size, dist[p].getGlobalCount());
            assertEquals(itemsPerNode + 1, dist[p].getLocalCount());

            // array size is normalized
            assertEquals(bigSize, dist[p].getGlobalArraySize());
            assertEquals(bigSize / procs, dist[p].getLocalArraySize());

            // check other processes
            for (int other = 0; other < imbalance; other++) {
                assertEquals(itemsPerNode + 1, dist[p].getItemCount(other));
            }
            for (int other = imbalance; other < procs; other++) {
                assertEquals(itemsPerNode, dist[p].getItemCount(other));
            }

            // check indexes
            for (int i = 0; i < (itemsPerNode + 1); i++) {
                assertEquals(p * (itemsPerNode + 1) + i,
                             dist[p].getGlobalArrayIndex(i));
                assertEquals(p * (itemsPerNode + 1) + i,
                             dist[p].getGlobalOffset(i));
            }
        }

        // ending processes should get one less element
        for (int p = imbalance; p < procs; p++) {
            dist[p] = new BlockDistribution(size, procs, p);

            assertEquals(size, dist[p].getGlobalCount());
            assertEquals(itemsPerNode, dist[p].getLocalCount());

            // array size is normalized
            assertEquals(bigSize, dist[p].getGlobalArraySize());
            assertEquals(bigSize / procs, dist[p].getLocalArraySize());

            // check other processes
            for (int other = 0; other < imbalance; other++) {
                assertEquals(itemsPerNode + 1, dist[p].getItemCount(other));
            }
            for (int other = imbalance; other < procs; other++) {
                assertEquals(itemsPerNode, dist[p].getItemCount(other));
            }

            // check indexes
            for (int i = 0; i < itemsPerNode; i++) {
                assertEquals(p * (bigSize / procs) + i,
                             dist[p].getGlobalArrayIndex(i));
                assertEquals(p * itemsPerNode + imbalance + i,
                             dist[p].getGlobalOffset(i));
            }
        }
    }

    @Test
    public void testSingleParticipant() throws Exception {
        for (int size = 0; size < 100; size++) {
            testCases(1, size, 0);
        }
    }

    @Test
    public void testBalancedDistribution() throws Exception {
        testCases(4, 10, 0);
    }

    @Test
    public void testUnbalancedDistribution() throws Exception {
        testCases(4, 10, 2);
    }

    @Test
    public void testSmallDistribution() throws Exception {
        testCases(4, 0, 2);
    }

    @Test
    public void testAssortedDistributions() throws Exception {
        for (int size = 0; size < 100; size++) {
            for (int p = 1; p < 200; p++) {
                for (int imb = 0; imb < Math.min(size, imb); imb++) {
                    testCases(p, size, imb);
                }
            }

        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyProcessGroup() throws Exception {
        new BlockDistribution(100, 0, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidRank() throws Exception {
        new BlockDistribution(100, 10, 10);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGlobalArrayOutOfBounds() throws Exception {
        Distribution dist = new BlockDistribution(200, 10, 5);
        dist.getGlobalArrayIndex(20);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGlobalOffsetOutOfBounds() throws Exception {
        Distribution dist = new BlockDistribution(200, 10, 5);
        dist.getGlobalOffset(20);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testOtherOutOfBounds() throws Exception {
        Distribution dist = new BlockDistribution(200, 10, 5);
        dist.getItemCount(10);
    }

    @Test
    public void testCompactArray() throws Exception {
        // expanded array for 7 items among 5 processes
        Object[] expanded = Arrays.asList(1, 1, 1, 1, 1, 0, 1, 0, 1, 0).toArray();
        Object[] expected = Arrays.asList(1, 1, 1, 1, 1, 1, 1).toArray();
        Object[] compact = new Object[expected.length];
        Distribution dist = new BlockDistribution(7, 5, 0);
        dist.compactGlobalArray(expanded, compact);
        assertArrayEquals(expected, compact);
    }
}
