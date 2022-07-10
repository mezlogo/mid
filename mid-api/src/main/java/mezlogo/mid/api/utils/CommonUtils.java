package mezlogo.mid.api.utils;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class CommonUtils {
    public static CompletableFuture<Void> allOf(List<CompletableFuture<Void>> futures) {
        var result = new CompletableFuture<Void>();
        var countDown = new AtomicInteger(futures.size());

        futures.forEach(future -> {
            future.thenAccept(it -> {
                countDown.decrementAndGet();
                if (!result.isDone() && 0 == countDown.get()) {
                    result.complete(null);
                }
            });
            future.exceptionally(it -> {
                result.completeExceptionally(it);
                return null;
            });
        });

        return result;
    }
}
