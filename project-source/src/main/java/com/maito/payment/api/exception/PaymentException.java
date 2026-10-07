package com.maito.payment.api.exception;

import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;

public class PaymentException extends BusinessException {
    public PaymentException(ErrorCode errorCode) {
        super(errorCode);
    }

    public PaymentException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
