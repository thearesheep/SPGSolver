package com.JPGSolver;

import com.google.common.primitives.Ints;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.array.TIntArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;


public class AsyncSolver implements Solver {

    protected Graph G;
    int[] tmpMap;
    int[] check;
    //ArrayList<Thread> threads;
    ExecutorService executor;

    int min = Integer.MAX_VALUE;
    int max = Integer.MIN_VALUE;
    TIntArrayList avg;

    public AsyncSolver(){
        executor = Executors.newFixedThreadPool(4);
        //threads = new ArrayList<>();
        avg = new TIntArrayList();
    }

    @Override
    public int[][] win(Graph G) {
        this.G = G;
        this.tmpMap = new int[G.length()];
        this.check = new int[G.length()];
        BitSet removed = new BitSet(G.length());
        int[][] res =  win_improved(G, removed);
        //System.out.println("Threads: " + threads.size());
        System.out.println("Min: " + min);
        System.out.println("Max: " + max);
        System.out.println("Avg: " + (avg.sum()/avg.size()));
        executor.shutdown();
        return res;
    }

    protected class asyncAttr implements Callable<TIntArrayList> {

        int node;
        BitSet removed;
        int i;

        public asyncAttr(int node, BitSet removed, int i){
            this.node = node;
            this.removed = removed;
            this.i = i;
        }

        public TIntArrayList call() {
            //if (!threads.contains(Thread.currentThread())) threads.add(Thread.currentThread());
            final TIntIterator iter = G.incomingEdgesOf(node).iterator();
            TIntArrayList A = new TIntArrayList();
            while (iter.hasNext()) {
                int v0 = iter.next();
                if (!removed.get(v0)) {
                    boolean flag = G.getPlayerOf(v0) == i;
                    if (tmpMap[v0] == 0) {
                        if (flag) {
                            A.add(v0);
                            tmpMap[v0] = 1;
                        } else {
                            int adjCounter = 0;
                            TIntIterator it = G.outgoingEdgesOf(v0).iterator();
                            while (it.hasNext()) {
                                if (!removed.get(it.next())) {
                                    adjCounter += 1;
                                }
                            }
                            tmpMap[v0] = adjCounter;
                            if (adjCounter == 1) {
                                A.add(v0);
                            }
                        }
                    } else if (!flag && tmpMap[v0] > 1) {
                        tmpMap[v0] -= 1;
                        if (tmpMap[v0] == 1) {
                            A.add(v0);
                        }
                    }
                }
            }
            return A;
        }
    }

    protected TIntArrayList Attr(TIntArrayList A, int i, BitSet removed) {
        Arrays.parallelSetAll(tmpMap, x -> 0);
        Arrays.parallelSetAll(check, x -> 0);
        TIntIterator it = A.iterator();
        while (it.hasNext()) {
            tmpMap[it.next()] = 1;
        }

        int index = 0;
        ArrayList<FutureTask<TIntArrayList>> tasks = new ArrayList<>();
        while (index < A.size()) {
            while (index < A.size()) {
                tasks.add(new FutureTask<>(new asyncAttr(A.get(index), removed, i)));
                index += 1;
            }

            int size = tasks.size();
            if (size < min) min = size;
            if (size > max) max = size;
            avg.add(size);

            try {
                for (FutureTask<TIntArrayList> task : tasks) executor.execute(task);
                for (FutureTask<TIntArrayList> task : tasks) MyTrove.addAllEx(A, task.get(), check); //if (!A.containsAll(task.get())) A.addAll(task.get());
            } catch (Exception e) {
                System.out.println("Error!");
            }
        }
        it = A.iterator();
        while (it.hasNext()) {
            removed.set(it.next());
        }
        return A;
    }

    private int[][] win_improved(Graph G, BitSet removed) {
        final int[][] W = {new int[0], new int[0]};
        final int d = G.maxPriority(removed);
        if (d > -1) {
            TIntArrayList U = G.getNodesWithPriority(d, removed);
            final int p = d % 2;
            final int j = 1 - p;
            int[][] W1;
            BitSet removed1 = (BitSet)removed.clone();
            final TIntArrayList A = Attr(U, p, removed1);
            W1 = win_improved(G, removed1);
            if (W1[j].length == 0) {
                W[p] = Ints.concat(W1[p], A.toArray());
            } else {
                BitSet removed2 = (BitSet)removed.clone();
                final TIntArrayList B = Attr(new TIntArrayList(W1[j]), j, removed2);
                W1 = win_improved(G, removed2);
                W[p] = W1[p];
                W[j] = Ints.concat(W1[j], B.toArray());
            }
        }
        return W;
    }
}
