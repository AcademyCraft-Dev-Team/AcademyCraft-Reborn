package org.academy.api.common.network.future.asm;

import org.academy.api.common.network.future.IRequestPayload;
import org.academy.api.common.network.future.IResponsePayload;

public interface IPayloadHandlerInvoker {
    IResponsePayload invoke(IRequestPayload<?, ?> requestPayload);
}