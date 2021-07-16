package kurenai.mybot.utils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RetryUtil {

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
        }
        throw new Exception("Over retry times.");
    }

    @FunctionalInterface
    public interface RetrySupplier<T> {
        T get() throws Exception;
    }

}
