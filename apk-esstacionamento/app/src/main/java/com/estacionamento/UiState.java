package com.estacionamento;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class UiState<T> {
    public enum Status { IDLE, LOADING, SUCCESS, ERROR }

    @NonNull private final Status status;
    @Nullable private final T data;
    @Nullable private final String message;

    private UiState(@NonNull Status status, @Nullable T data, @Nullable String message) {
        this.status = status;
        this.data = data;
        this.message = message;
    }

    @NonNull public Status getStatus() { return status; }
    @Nullable public T getData() { return data; }
    @Nullable public String getMessage() { return message; }

    public boolean isIdle() { return status == Status.IDLE; }
    public boolean isLoading() { return status == Status.LOADING; }
    public boolean isSuccess() { return status == Status.SUCCESS; }
    public boolean isError() { return status == Status.ERROR; }

    public static <T> UiState<T> idle() { return new UiState<>(Status.IDLE, null, null); }
    public static <T> UiState<T> loading() { return new UiState<>(Status.LOADING, null, null); }
    public static <T> UiState<T> success(@Nullable T data) { return new UiState<>(Status.SUCCESS, data, null); }
    public static <T> UiState<T> error(@NonNull String message) { return new UiState<>(Status.ERROR, null, message); }
}
