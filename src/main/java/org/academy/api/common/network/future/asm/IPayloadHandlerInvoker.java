package org.academy.api.common.network.future.asm;

import org.academy.api.common.network.future.RequestPayload;
import org.academy.api.common.network.future.ResponsePayload;

public interface IPayloadHandlerInvoker {
    ResponsePayload<?> invoke(RequestPayload<?, ?> requestPayload);
}