import java.util.concurrent.CancellationException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.locks.StampedLock;


/*
StampedLock starwation demo based on the code
from https://www.javaspecialists.eu/archive/Issue321-StampedLock-ReadWriteLock-Dangers.html

This implementation uses optimistic locking wich allows to avoild writer's starwation

 */

public class SLWriteLockStarvationDemo {
    private final StampedLock rwlock;

    public SLWriteLockStarvationDemo(StampedLock rwlock) {
        this.rwlock = rwlock;
    }

    public static void main(String... args) {
        new SLWriteLockStarvationDemo(
                new StampedLock()
        ).run();
    }

    public void run() {
        if (checkForWriterStarvation(rwlock) > 1_000) {
            throw new AssertionError("Writer starvation occurred!!!");
        } else {
            System.out.println("No writer starvation");
        }
    }

    private long checkForWriterStarvation(StampedLock rwlock) {
        System.out.println("Checking " + rwlock.getClass());
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            scope.fork(() -> {
                System.out.println("Going to start readers ...");
                for (int i = 0; i < 10; i++) {
                    int reader = i;
                    scope.fork(() -> {
                        var stamp = rwlock.tryOptimisticRead();
                        System.out.println("Reader " + reader + " reading optimistically ");
                        if (!rwlock.validate(stamp)) {
                            stamp = rwlock.readLock();
                            try {
                                System.out.println("Reader " + reader +
                                        " is reading pessimistically...");
                                Thread.sleep(1000);
                            } finally {
                                rwlock.unlockRead(stamp);
                            }
                        }
                        System.out.println("Reader " + reader + "" +
                                " is done");
                        return true;
                    });
                    Thread.sleep(500);
                }
                return true;
            });
            Thread.sleep(1800);
            System.out.println("Going to try to write now ...");
            long time = System.nanoTime();
            var stamp = rwlock.writeLock();
            try {
                time = System.nanoTime() - time;
                time /= 1_000_000; // convert to ms
                System.out.printf(
                        "time to acquire write lock = %dms%n", time);
                System.out.println("Writer is writing ...");
                Thread.sleep(1000);
            } finally {
                rwlock.unlockWrite(stamp);
            }
            System.out.println("Writer is done");

            scope.join().throwIfFailed(IllegalStateException::new);
            return time;
        } catch (InterruptedException e) {
            throw new CancellationException("interrupted");
        }
    }

}