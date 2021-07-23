package kurenai.mybot.utils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RetryUtil {

    private static final long SLEEP     = 200L;
    private static final long SLEEP_MAX = 3000L;

    public static <T> T retry(int retryTimes, RetrySupplier<T> supplier) throws Exception {
        int retryCount = 0;
        while (retryCount <= retryTimes) {
            try {
                return supplier.get();
            } catch (Exception e) {
                if (retryCount >= retryTimes) throw e;
                log.warn("retry for exception: {}", e.getMessage());
                retryCount++;
            }
            Thread.sleep(Math.min(SLEEP * retryCount * retryCount * retryCount, SLEEP_MAX));
        }
        throw new Exception("Over retry times.");
    }

    @FunctionalInterface
    public interface RetrySupplier<T> {
        T get() throws Exception;
    }

}
