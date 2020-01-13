package edu.cmu.oli.content.configuration;
import com.airhacks.porcupine.configuration.control.ExecutorConfigurator;
import com.airhacks.porcupine.execution.control.ExecutorConfiguration;
import com.airhacks.porcupine.execution.control.InstrumentedThreadPoolExecutor;
import com.airhacks.porcupine.execution.control.PipelineStore;
import com.airhacks.porcupine.execution.entity.Pipeline;
import com.airhacks.porcupine.execution.entity.Rejection;
import com.airhacks.porcupine.execution.entity.Statistics;
import edu.cmu.oli.content.logging.Logging;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;

@ApplicationScoped
public class ExecutorExposer {
    @Inject
    @Logging
    Logger log;

    @Inject
    Event<Rejection> rejections;
    @Inject
    PipelineStore ps;
    @Inject
    ExecutorConfigurator ec;

    public ExecutorExposer() {
    }

    public void onRejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        Pipeline pipeline = this.findPipeline(executor);
        pipeline.taskRejected();
        this.rejections.fire(new Rejection(pipeline.getStatistics(), r.getClass().getName()));
    }

    @Produces
    @DedicatedExecutor
    public ExecutorService exposeExecutorService(InjectionPoint ip) {
        String pipelineName = this.getPipelineName(ip);
        Pipeline existingPipeline = this.ps.get(pipelineName);
        if (existingPipeline != null) {
            log.info("Pipeline Old: "+ pipelineName);
            return existingPipeline.getExecutor();
        } else {
            log.info("Pipeline New: "+ pipelineName);
            ExecutorConfiguration config = this.ec.forPipeline(pipelineName);
            RejectedExecutionHandler rejectedExecutionHandler = config.getRejectedExecutionHandler();
            if (rejectedExecutionHandler == null) {
                rejectedExecutionHandler = this::onRejectedExecution;
            }

            InstrumentedThreadPoolExecutor threadPoolExecutor = this.createThreadPoolExecutor(config, rejectedExecutionHandler, pipelineName);
            this.ps.put(pipelineName, new Pipeline(pipelineName, threadPoolExecutor));
            return threadPoolExecutor;
        }
    }

    InstrumentedThreadPoolExecutor createThreadPoolExecutor(ExecutorConfiguration config, RejectedExecutionHandler rejectedExecutionHandler, String name) {
        int corePoolSize = config.getCorePoolSize();
        int keepAliveTime = config.getKeepAliveTime();
        int maxPoolSize = config.getMaxPoolSize();
        int queueCapacity = config.getQueueCapacity();
        Object queue;
        if (queueCapacity > 0) {
            queue = new ArrayBlockingQueue(queueCapacity);
        } else {
            queue = new SynchronousQueue();
        }


        InstrumentedThreadPoolExecutor threadPoolExecutor = new InstrumentedThreadPoolExecutor(corePoolSize, maxPoolSize, (long)keepAliveTime, TimeUnit.SECONDS, (BlockingQueue)queue, new DThreadFactory(name), rejectedExecutionHandler);
        return threadPoolExecutor;
    }

    @Produces
    @DedicatedExecutor
    public Statistics exposeStatistics(InjectionPoint ip) {
        String name = this.getPipelineName(ip);
        return this.ps.getStatistics(name);
    }

    @Produces
    public List<Statistics> getAllStatistics() {
        return this.ps.getAllStatistics();
    }

    String getPipelineName(InjectionPoint ip) {
        Annotated annotated = ip.getAnnotated();
        DedicatedExecutor dedicated = (DedicatedExecutor)annotated.getAnnotation(DedicatedExecutor.class);
        String name;
        if (dedicated != null && !"-".equals(dedicated.value())) {
            name = dedicated.value();
        } else {
            name = ip.getMember().getName();
        }

        return name;
    }

    Pipeline findPipeline(ThreadPoolExecutor executor) {
        return (Pipeline)this.ps.pipelines().stream().filter((p) -> {
            return p.manages(executor);
        }).findFirst().orElse((Pipeline)null);
    }

    private static class DThreadFactory implements ThreadFactory {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        DThreadFactory(String name) {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                    Thread.currentThread().getThreadGroup();
            namePrefix = "pool-" + name +
                    poolNumber.getAndIncrement() +
                    "-thread-";
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,
                    namePrefix + threadNumber.getAndIncrement(),
                    0);
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
}