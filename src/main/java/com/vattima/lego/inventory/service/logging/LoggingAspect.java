package com.vattima.lego.inventory.service.logging;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Aspect
@Component
@Slf4j
public class LoggingAspect {

    @Around("@annotation(LogExecution)")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        Object returnValue = joinPoint.proceed();

        stopWatch.stop();
        Object[] args = joinPoint.getArgs();
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String parameters = (String) ((Stream) Arrays.stream(args).sequential()).map(Object::toString).collect(Collectors.joining());
        log.info("Method: {}.{} with parameters: {} took [{}]ms", className, methodName, parameters, stopWatch.getTotalTimeMillis());

        return returnValue;
    }
}