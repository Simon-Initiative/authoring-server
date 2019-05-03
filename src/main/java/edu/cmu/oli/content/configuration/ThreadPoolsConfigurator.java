package edu.cmu.oli.content.configuration;

import com.airhacks.porcupine.configuration.control.ExecutorConfigurator;
import com.airhacks.porcupine.execution.control.ExecutorConfiguration;

import javax.enterprise.inject.Specializes;

/**
 * @author Raphael Gachuhi
 */
@Specializes
public class ThreadPoolsConfigurator extends ExecutorConfigurator {
    @Override
    public ExecutorConfiguration forPipeline(String name) {
        if ("resourcesApiExecutor".equals(name)) {
            return new ExecutorConfiguration.Builder().corePoolSize(2).maxPoolSize(10).queueCapacity(200).build();
        }
        if ("svnExecutor".equals(name)) {
            return new ExecutorConfiguration.Builder().corePoolSize(2).maxPoolSize(10).queueCapacity(2000).build();
        }
        if ("resourceCreate".equals(name)) {
            return new ExecutorConfiguration.Builder().corePoolSize(1).maxPoolSize(1).queueCapacity(200).build();
        }
        if ("migrationExec".equals(name)) {
            return new ExecutorConfiguration.Builder().corePoolSize(1).maxPoolSize(2).queueCapacity(300).build();
        }
        if ("revMigrationExec".equals(name)) {
            return new ExecutorConfiguration.Builder().corePoolSize(1).maxPoolSize(2).queueCapacity(20000).build();
        }

        if ("versionBatchExec".equals(name)) {
            return new ExecutorConfiguration.Builder().corePoolSize(1).maxPoolSize(2).queueCapacity(20000).build();
        }

        return super.forPipeline(name);
    }
}
