package com.abv.hrerpisapi.scheduler;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulingProbe {

    private final Optional<TaskScheduler> taskScheduler;
    private final Optional<ScheduledAnnotationBeanPostProcessor> scheduledProcessor;

    @PostConstruct
    void init() {
        log.info("SchedulingProbe: TaskScheduler present? {}", taskScheduler.isPresent());
        log.info("SchedulingProbe: ScheduledAnnotationBeanPostProcessor present? {}", scheduledProcessor.isPresent());
    }
}