package org.labkey.api.usageMetrics;

import java.util.Map;

/**
 * Created by Tony on 2/14/2017.
 *
 * Modules can report their own usage metrics by registering a subclass with UsageMetricsService at startup.
 */
public abstract class UsageMetricsProvider
{
    public abstract Map<String, Object> getUsageMetrics();
}
