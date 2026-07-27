package io.apicurio.registry.webhooks;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_REST;

@ApplicationScoped
public class WebhooksConfig {

    @Inject
    @Current
    RegistryStorage storage;

    @Getter
    @ConfigProperty(name = "apicurio.webhooks.enabled", defaultValue = "false")
    @Info(category = CATEGORY_REST, description = "Enable CloudEvents webhook notifications", availableSince = "3.3.0", experimental = true)
    boolean enabled;

    @ConfigProperty(name = "apicurio.webhooks.allow-insecure-urls", defaultValue = "false")
    @Info(category = CATEGORY_REST, description = "Allow http:// webhook endpoint URLs (development only)", availableSince = "3.3.0", experimental = true)
    boolean allowInsecureUrls;

    @ConfigProperty(name = "apicurio.webhooks.security.block-private-ips", defaultValue = "true")
    @Info(category = CATEGORY_REST, description = "Block webhook URLs that resolve to private or link-local IP addresses", availableSince = "3.3.0", experimental = true)
    boolean blockPrivateIps;

    @ConfigProperty(name = "apicurio.webhooks.rule-violations.enabled", defaultValue = "true")
    @Info(category = CATEGORY_REST, description = "Emit rule.violated CloudEvents when artifact writes are rejected by rules", availableSince = "3.3.0", experimental = true)
    boolean ruleViolationsEnabled;

    @ConfigProperty(name = "apicurio.webhooks.subscriptions.max-count", defaultValue = "100")
    @Info(category = CATEGORY_REST, description = "Maximum number of webhook subscriptions per registry instance", availableSince = "3.3.0", experimental = true)
    int subscriptionsMaxCount;

    @ConfigProperty(name = "apicurio.webhooks.delivery.max-attempts", defaultValue = "10")
    @Info(category = CATEGORY_REST, description = "Maximum delivery attempts before moving a webhook delivery to dead-letter status", availableSince = "3.3.0", experimental = true)
    int deliveryMaxAttempts;

    @ConfigProperty(name = "apicurio.webhooks.delivery.initial-delay", defaultValue = "1s")
    @Info(category = CATEGORY_REST, description = "Initial retry delay for failed webhook deliveries", availableSince = "3.3.0", experimental = true)
    String deliveryInitialDelay;

    @ConfigProperty(name = "apicurio.webhooks.delivery.max-delay", defaultValue = "5m")
    @Info(category = CATEGORY_REST, description = "Maximum retry delay for failed webhook deliveries", availableSince = "3.3.0", experimental = true)
    String deliveryMaxDelay;

    @ConfigProperty(name = "apicurio.webhooks.delivery.backoff-multiplier", defaultValue = "2.0")
    @Info(category = CATEGORY_REST, description = "Exponential backoff multiplier for webhook delivery retries", availableSince = "3.3.0", experimental = true)
    double deliveryBackoffMultiplier;

    @ConfigProperty(name = "apicurio.webhooks.delivery.batch-size", defaultValue = "50")
    @Info(category = CATEGORY_REST, description = "Number of webhook deliveries claimed per worker poll", availableSince = "3.3.0", experimental = true)
    int deliveryBatchSize;

    @ConfigProperty(name = "apicurio.webhooks.delivery.concurrency", defaultValue = "10")
    @Info(category = CATEGORY_REST, description = "Maximum concurrent webhook HTTP deliveries per registry instance", availableSince = "3.3.0", experimental = true)
    int deliveryConcurrency;

    @ConfigProperty(name = "apicurio.webhooks.delivery.max-inflight-per-subscription", defaultValue = "3")
    @Info(category = CATEGORY_REST, description = "Maximum in-flight webhook deliveries per subscription", availableSince = "3.3.0", experimental = true)
    int deliveryMaxInflightPerSubscription;

    @ConfigProperty(name = "apicurio.webhooks.delivery.poll-every", defaultValue = "2s")
    @Info(category = CATEGORY_REST, description = "How often the webhook delivery worker polls for pending deliveries", availableSince = "3.3.0", experimental = true)
    String deliveryPollEvery;

    @ConfigProperty(name = "apicurio.webhooks.delivery.http-timeout", defaultValue = "15s")
    @Info(category = CATEGORY_REST, description = "HTTP timeout for webhook delivery requests", availableSince = "3.3.0", experimental = true)
    String deliveryHttpTimeout;

    @ConfigProperty(name = "apicurio.webhooks.delivery.in-progress-timeout", defaultValue = "5m")
    @Info(category = CATEGORY_REST, description = "Duration after which in-progress webhook deliveries are reclaimed", availableSince = "3.3.0", experimental = true)
    String deliveryInProgressTimeout;

    @ConfigProperty(name = "apicurio.webhooks.delivery.shutdown-timeout", defaultValue = "30s")
    @Info(category = CATEGORY_REST, description = "Grace period to drain in-flight webhook deliveries on shutdown", availableSince = "3.3.0", experimental = true)
    String deliveryShutdownTimeout;

    @ConfigProperty(name = "apicurio.webhooks.delivery.auto-disable-threshold", defaultValue = "50")
    @Info(category = CATEGORY_REST, description = "Consecutive delivery failures before auto-disabling a subscription", availableSince = "3.3.0", experimental = true)
    int deliveryAutoDisableThreshold;

    @ConfigProperty(name = "apicurio.webhooks.fanout.reconcile-every", defaultValue = "30s")
    @Info(category = CATEGORY_REST, description = "How often the webhook fanout reconciler polls for pending fanouts", availableSince = "3.3.0", experimental = true)
    String fanoutReconcileEvery;

    @ConfigProperty(name = "apicurio.webhooks.log.retention", defaultValue = "30d")
    @Info(category = CATEGORY_REST, description = "Retention period for webhook delivery audit logs", availableSince = "3.3.0", experimental = true)
    String logRetention;

    @ConfigProperty(name = "apicurio.webhooks.payload.max-bytes", defaultValue = "262144")
    @Info(category = CATEGORY_REST, description = "Maximum CloudEvent payload size in bytes before truncation", availableSince = "3.3.0", experimental = true)
    int payloadMaxBytes;

    @ConfigProperty(name = "apicurio.webhooks.violations.max-count", defaultValue = "20")
    @Info(category = CATEGORY_REST, description = "Maximum rule violations included in a rule.violated CloudEvent", availableSince = "3.3.0", experimental = true)
    int violationsMaxCount;

    public boolean isOperational() {
        return enabled && storage.supportsWebhooks();
    }

    public boolean isAllowInsecureUrls() {
        return allowInsecureUrls;
    }

    public boolean isBlockPrivateIps() {
        return blockPrivateIps;
    }

    public boolean isRuleViolationsEnabled() {
        return ruleViolationsEnabled;
    }

    public int getSubscriptionsMaxCount() {
        return subscriptionsMaxCount;
    }

    public int getDeliveryMaxAttempts() {
        return deliveryMaxAttempts;
    }

    public String getDeliveryInitialDelay() {
        return deliveryInitialDelay;
    }

    public String getDeliveryMaxDelay() {
        return deliveryMaxDelay;
    }

    public double getDeliveryBackoffMultiplier() {
        return deliveryBackoffMultiplier;
    }

    public int getDeliveryBatchSize() {
        return deliveryBatchSize;
    }

    public int getDeliveryConcurrency() {
        return deliveryConcurrency;
    }

    public int getDeliveryMaxInflightPerSubscription() {
        return deliveryMaxInflightPerSubscription;
    }

    public String getDeliveryPollEvery() {
        return deliveryPollEvery;
    }

    public String getDeliveryHttpTimeout() {
        return deliveryHttpTimeout;
    }

    public String getDeliveryInProgressTimeout() {
        return deliveryInProgressTimeout;
    }

    public String getDeliveryShutdownTimeout() {
        return deliveryShutdownTimeout;
    }

    public int getDeliveryAutoDisableThreshold() {
        return deliveryAutoDisableThreshold;
    }

    public String getFanoutReconcileEvery() {
        return fanoutReconcileEvery;
    }

    public String getLogRetention() {
        return logRetention;
    }

    public int getPayloadMaxBytes() {
        return payloadMaxBytes;
    }

    public int getViolationsMaxCount() {
        return violationsMaxCount;
    }
}
