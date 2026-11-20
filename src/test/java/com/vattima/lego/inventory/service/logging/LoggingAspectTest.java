package com.vattima.lego.inventory.service.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoggingAspectTest {
    private final LoggingAspect loggingAspect = new LoggingAspect();

    @Test
    void nullArgumentsDoNotPreventTheInterceptedCallFromReturning() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        Object returnValue = new Object();

        when(joinPoint.proceed()).thenReturn(returnValue);
        when(joinPoint.getArgs()).thenReturn(new Object[]{8569, null});
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("createListingCreateSyncRequest");
        when(joinPoint.getTarget()).thenReturn(new Object());

        assertThat(loggingAspect.logAround(joinPoint)).isSameAs(returnValue);
        verify(joinPoint).proceed();
    }
}
