package dev.springdrop.kernel.tx;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs a unit of work inside a single transaction so that multi-table writes
 * (base, revision, and field tables) commit together or roll back together. Any
 * runtime exception thrown by the work rolls back the whole transaction.
 */
@Component
public class TransactionRunner {

    private final TransactionTemplate transactionTemplate;

    public TransactionRunner(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void run(Runnable work) {
        transactionTemplate.executeWithoutResult(status -> work.run());
    }

    public <T> T call(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }
}
