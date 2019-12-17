package Analysis;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FixedThreadPoolAnalysis {

    public static void main(String[] args) {
        int n = 1000000000;
        long startTime = System.nanoTime();
        test.singleThread(n);
        long endTime = System.nanoTime();
        System.out.println("单线程计算 10 次 1 + 2 + ... + " + n + " 的时间消耗: " + (endTime - startTime) + "ns");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        startTime = System.nanoTime();
        test.multiThread(n);
        endTime = System.nanoTime();
        System.out.println("多线程计算 10 次 1 + 2 + ... + " + n + " 的时间消耗: " + (endTime - startTime) + "ns");
    }

    private static FixedThreadPoolAnalysis test = new FixedThreadPoolAnalysis();

    private void singleThread(int n) {
        long sum = 0L;
        for (int k = 0; k < 10; k++) {
            for (int i = 1; i <= n; i++)
                sum += i;
        }
        System.out.println(Thread.currentThread().getName() + " -> " + sum);
    }

    private void multiThread(int n) {
        int cpus = Runtime.getRuntime().availableProcessors();
        ExecutorService fixedThreadPool = Executors.newFixedThreadPool(cpus + 1);
        int interval = n / (cpus + 1);
        for (int k = 0; k < 10; k++) {
            for (int i = 0; i < cpus; i++) {
                int left = i * interval + 1;
                int right = (i + 1) * interval;
                Runnable task = new Run(left, right);
                fixedThreadPool.execute(task);
            }
        }
        fixedThreadPool.shutdown();
    }

    class Run implements Runnable{
        private int left;
        private int right;
        Run(int left, int right) {
            this.left = left;
            this.right = right;
        }
        @Override
        public void run() {
            long sum = 0L;
            for (int i = left; i <= right; i++) {
                sum += i;
            }
            System.out.println(Thread.currentThread().getName() + " -> " + sum);
        }
    }
}
