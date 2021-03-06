package com.heliosapm.streams.metrics.processors.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.heliosapm.streams.metrics.ValueType;
import com.heliosapm.streams.metrics.processors.AbstractStreamedMetricProcessor;

/**
 * <p>Title: BeatsJSONToMetricTransformer</p>
 * <p>Description: MetricProcessor that accepts filebeat submitted json messages, converts to StreamedMetrics and publishes to the appropriate end point</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.streams.metrics.processors.impl.BeatsJSONToMetricTransformer</code></p>
 */
class BeatsJSONToMetricTransformer extends  AbstractStreamedMetricProcessor<String, JsonNode> {

	/**
	 * Creates a new BeatsJSONToMetricTransformer
	 * @param period The period of the context commit if the max number of forwards has not been met.
	 * @param maxForwards The maximum number of metrics to forward without a commit
	 * @param topicSink The topic sink for this processor
	 * @param sources The topic sources for this processor
	 */
	protected BeatsJSONToMetricTransformer(final long period, final int maxForwards, final String topicSink, final String[] sources) {
		super(ValueType.STRAIGHTTHROUGH, period, maxForwards, topicSink, sources);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.metrics.processors.AbstractStreamedMetricProcessor#doProcess(java.lang.Object, java.lang.Object)
	 */
	@Override
	protected boolean doProcess(final String key, final JsonNode value) {
		forward(key, value);
		return true;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.metrics.processors.AbstractStreamedMetricProcessor#punctuate(long)
	 */
	@Override
	public void punctuate(final long timestamp) {
		commit();
	}
	
}