package com.github.dockerhostjava.connection.HttpConnection.logic;

import java.time.Duration;

public final class TimeoutValidator {

    private TimeoutValidator() {
    }

    /** 제한 시간이 비었거나 0 이하이면 예외를 던진다. */
    public static Duration requirePositive(Duration connectTimeout) {
        if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()) {
            throw new IllegalArgumentException("connect timeout must be positive: timeout=" + connectTimeout);
        }
        return connectTimeout;
    }
}
