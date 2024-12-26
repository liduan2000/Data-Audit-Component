package com.duan.transaction;

import org.springframework.transaction.support.TransactionSynchronization;

public class AuditTransactionSynchronization implements TransactionSynchronization {
    private final String txId;
    private final java.util.function.Consumer<String> commitCallback;
    private final java.util.function.Consumer<String> clearCallback;

    public AuditTransactionSynchronization(String txId,
                                           java.util.function.Consumer<String> commitCallback,
                                           java.util.function.Consumer<String> clearCallback) {
        this.txId = txId;
        this.commitCallback = commitCallback;
        this.clearCallback = clearCallback;
    }

    @Override
    public void afterCompletion(int status) {
        if (status == STATUS_COMMITTED) {
            commitCallback.accept(txId);
        }
        clearCallback.accept(txId);
    }
}
