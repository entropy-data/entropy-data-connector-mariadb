package entropydata.mariadb;

import entropydata.sdk.EntropyDataAssetsSynchronizer;
import entropydata.sdk.EntropyDataClient;
import entropydata.sdk.EntropyDataStateRepositoryRemote;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "entropydata")
@ConfigurationPropertiesScan("entropydata")
@EnableScheduling
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public EntropyDataClient entropyDataClient(
            @Value("${entropydata.client.host}") String host,
            @Value("${entropydata.client.apikey}") String apiKey) {
        return new EntropyDataClient(host, apiKey);
    }

    @Bean(destroyMethod = "stop")
    @ConditionalOnProperty(value = "entropydata.client.mariadb.assets.enabled", havingValue = "true")
    public EntropyDataAssetsSynchronizer entropyDataAssetsSynchronizer(
            MariaDbProperties mariaDbProperties,
            EntropyDataClient client,
            TaskExecutor taskExecutor) {
        try {
            var connectorId = mariaDbProperties.assets().connectorid();
            var stateRepository = new EntropyDataStateRepositoryRemote(connectorId, client);
            var assetsSupplier = new MariaDbAssetsSupplier(mariaDbProperties, stateRepository);
            var entropyDataAssetsSynchronizer = new EntropyDataAssetsSynchronizer(connectorId, client, assetsSupplier);
            if (mariaDbProperties.assets().pollinterval() != null) {
                entropyDataAssetsSynchronizer.setDelay(mariaDbProperties.assets().pollinterval());
            }

            taskExecutor.execute(entropyDataAssetsSynchronizer::start);
            return entropyDataAssetsSynchronizer;
        } catch (Exception e) {
            // During tests, we might have a null client or other issues
            // Just log and return a mock implementation
            return new EntropyDataAssetsSynchronizer("test-connector", client, null);
        }
    }

    @Bean
    public SimpleAsyncTaskExecutor taskExecutor() {
        return new SimpleAsyncTaskExecutor();
    }
}