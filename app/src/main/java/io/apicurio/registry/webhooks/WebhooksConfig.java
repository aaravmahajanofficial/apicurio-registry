package io.apicurio.registry.webhooks;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_REST;

/**
 * Configuration for CloudEvents webhook notifications (experimental).
 * <p>
 * Webhooks are disabled by default ({@code apicurio.webhooks.enabled=false}). When enabled, only
 * PostgreSQL-backed SQL storage is operational; other storage variants return HTTP 409 on admin
 * endpoints. Property descriptions are indexed for the config reference via {@link Info}.
 */
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

    @ConfigProperty(name = "apicurio.webhooks.fanout.max-attempts", defaultValue = "10")
    @Info(category = CATEGORY_REST, description = "Maximum fanout attempts before a webhook_fanout row is abandoned", availableSince = "3.3.0", experimental = true)
    int fanoutMaxAttempts;

    @ConfigProperty(name = "apicurio.webhooks.fanout.reconcile-batch-size", defaultValue = "50")
    @Info(category = CATEGORY_REST, description = "Maximum webhook fanout rows reconciled per poll", availableSince = "3.3.0", experimental = true)
    int fanoutReconcileBatchSize;

    @ConfigProperty(name = "apicurio.webhooks.log.retention", defaultValue = "30d")
    @Info(category = CATEGORY_REST, description = "Retention period for webhook delivery audit logs", availableSince = "3.3.0", experimental = true)
    String logRetention;

    @ConfigProperty(name = "apicurio.webhooks.payload.max-bytes", defaultValue = "262144")
    @Info(category = CATEGORY_REST, description = "Maximum CloudEvent payload size in bytes before truncation", availableSince = "3.3.0", experimental = true)
    int payloadMaxBytes;

    @ConfigProperty(name = "apicurio.webhooks.violations.max-count", defaultValue = "20")
    @Info(category = CATEGORY_REST, description = "Maximum rule violations included in a rule.violated CloudEvent", availableSince = "3.3.0", experimental = true)
    int violationsMaxCount;

    /**
     * @return {@code true} when webhooks are enabled and the current storage supports them
     */
    public boolean isOperational() {
        return enabled && storage.supportsWebhooks();
    }

    /**
     * @return whether {@code http://} endpoint URLs are permitted at registration time
     */
    public boolean isAllowInsecureUrls() {
        return allowInsecureUrls;
    }

    /**
     * @return whether private/link-local IPs should be blocked for webhook URLs (Phase 2b)
     */
    public boolean isBlockPrivateIps() {
        return blockPrivateIps;
    }

    /**
     * @return whether {@code rule.violated} CloudEvents are emitted on rejected writes
     */
    public boolean isRuleViolationsEnabled() {
        return ruleViolationsEnabled;
    }

    /**
     * @return maximum subscriptions allowed registry-wide
     */
    public int getSubscriptionsMaxCount() {
        return subscriptionsMaxCount;
    }

    /**
     * @return maximum delivery attempts before dead-letter
     */
    public int getDeliveryMaxAttempts() {
        return deliveryMaxAttempts;
    }

    /**
     * @return initial retry delay for failed deliveries
     */
    public String getDeliveryInitialDelay() {
        return deliveryInitialDelay;
    }

    /**
     * @return maximum retry delay cap for failed deliveries
     */
    public String getDeliveryMaxDelay() {
        return deliveryMaxDelay;
    }

    /**
     * @return exponential backoff multiplier for delivery retries
     */
    public double getDeliveryBackoffMultiplier() {
        return deliveryBackoffMultiplier;
    }

    /**
     * @return number of deliveries claimed per worker poll
     */
    public int getDeliveryBatchSize() {
        return deliveryBatchSize;
    }

    /**
     * @return maximum concurrent HTTP deliveries per instance
     */
    public int getDeliveryConcurrency() {
        return deliveryConcurrency;
    }

    /**
     * @return per-subscription in-flight delivery cap
     */
    public int getDeliveryMaxInflightPerSubscription() {
        return deliveryMaxInflightPerSubscription;
    }

    /**
     * @return delivery worker poll interval
     */
    public String getDeliveryPollEvery() {
        return deliveryPollEvery;
    }

    /**
     * @return HTTP timeout for outbound webhook requests
     */
    public String getDeliveryHttpTimeout() {
        return deliveryHttpTimeout;
    }

    /**
     * @return duration after which stale in-progress deliveries are reclaimed
     */
    public String getDeliveryInProgressTimeout() {
        return deliveryInProgressTimeout;
    }

    /**
     * @return graceful shutdown drain timeout
     */
    public String getDeliveryShutdownTimeout() {
        return deliveryShutdownTimeout;
    }

    /**
     * @return consecutive failures before auto-disabling a subscription
     */
    public int getDeliveryAutoDisableThreshold() {
        return deliveryAutoDisableThreshold;
    }

    /**
     * @return fanout reconciler poll interval
     */
    public String getFanoutReconcileEvery() {
        return fanoutReconcileEvery;
    }

    /**
     * @return maximum fanout attempts before a {@code webhook_fanout} row is abandoned
     */
    public int getFanoutMaxAttempts() {
        return fanoutMaxAttempts;
    }

    /**
     * @return maximum fanout rows reconciled per poll
     */
    public int getFanoutReconcileBatchSize() {
        return fanoutReconcileBatchSize;
    }

    /**
     * @return delivery audit log retention period
     */
    public String getLogRetention() {
        return logRetention;
    }

    /**
     * @return maximum CloudEvent payload size before truncation
     */
    public int getPayloadMaxBytes() {
        return payloadMaxBytes;
    }

    /**
     * @return maximum rule violations included in a violation event payload
     */
    public int getViolationsMaxCount() {
        return violationsMaxCount;
    }
}
